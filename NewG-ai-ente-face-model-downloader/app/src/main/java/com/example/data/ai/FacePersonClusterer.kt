package com.example.data.ai

import android.util.LruCache
import com.example.data.local.MediaItemEntity
import org.json.JSONObject
import kotlin.math.sqrt

data class FaceReference(val photoId: Long, val faceIndex: Int)
data class FaceCropBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class FacePersonGroup(
  val id: String,
  val photoIds: List<Long>,
  val faceCount: Int,
  val coverPhotoId: Long,
  val coverFaceIndex: Int? = null,
  val name: String? = null,
  val clusterIds: List<String> = listOf(id),
  val faces: List<FaceReference> = emptyList(),
  val faceBounds: Map<FaceReference, FaceCropBounds> = emptyMap(),
)

internal data class ParsedFaceEntry(
  val faceIndex: Int,
  val faceId: String?,
  val bounds: FaceCropBounds?,
  /** L2-normalized 192-D embedding or null if absent/invalid. */
  val normalizedVector: FloatArray?,
)

internal data class ParsedPhotoFaces(
  val photoId: Long,
  val entries: List<ParsedFaceEntry>,
  val indexByFaceId: Map<String, Int>,
)

object FacePersonClusterer {
  private data class Sample(
    val photoId: Long,
    val faceIndex: Int,
    val vector: FloatArray,
    val bounds: FaceCropBounds?,
  )

  private data class Cluster(
    val id: String,
    val samples: MutableList<Sample>,
    var centroid: FloatArray,
    val photoIds: MutableSet<Long>,
  )

  // Thread-safe LRU cache of parsed face records so clustering, suggestion ranking,
  // and ID resolution do not repeatedly parse 192-float JSON arrays on every state emission.
  private val parsedFaceCache = LruCache<String, ParsedPhotoFaces>(512)

  internal fun parsePhotoFaces(row: MediaItemEntity): ParsedPhotoFaces? {
    val rawJson = row.faceDataJson ?: return null
    if (rawJson.isBlank()) return null
    val cacheKey = "${row.id}:${row.faceIndexedAt ?: 0L}:${rawJson.hashCode()}"
    synchronized(parsedFaceCache) {
      parsedFaceCache.get(cacheKey)?.let { return it }
    }

    val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
    val faces = json.optJSONArray("faces") ?: return null
    val len = faces.length()
    if (len == 0) {
      val empty = ParsedPhotoFaces(row.id, emptyList(), emptyMap())
      synchronized(parsedFaceCache) { parsedFaceCache.put(cacheKey, empty) }
      return empty
    }

    val entries = ArrayList<ParsedFaceEntry>(len)
    val idMap = HashMap<String, Int>(len)
    for (index in 0 until len) {
      val faceObject = faces.optJSONObject(index) ?: continue
      val faceId = faceObject.optString("faceId").takeIf { it.isNotBlank() }
      if (faceId != null && !idMap.containsKey(faceId)) {
        idMap[faceId] = index
      }

      val box = faceObject.optJSONArray("box")
      val bounds =
        if (box != null && box.length() >= 4) {
          val left = box.optDouble(0, Double.NaN).toFloat()
          val top = box.optDouble(1, Double.NaN).toFloat()
          val right = box.optDouble(2, Double.NaN).toFloat()
          val bottom = box.optDouble(3, Double.NaN).toFloat()
          if (
            left.isFinite() &&
              top.isFinite() &&
              right.isFinite() &&
              bottom.isFinite() &&
              right > left &&
              bottom > top
          ) {
            FaceCropBounds(
              left.coerceIn(0f, 1f),
              top.coerceIn(0f, 1f),
              right.coerceIn(0f, 1f),
              bottom.coerceIn(0f, 1f),
            )
          } else null
        } else null

      val values = faceObject.optJSONArray("embedding")
      var normalizedVector: FloatArray? = null
      if (values != null && values.length() == EMBEDDING_SIZE) {
        val vector = FloatArray(EMBEDDING_SIZE)
        var valid = true
        var sumSq = 0.0
        for (i in 0 until EMBEDDING_SIZE) {
          val v = values.optDouble(i, Double.NaN).toFloat()
          if (!v.isFinite()) {
            valid = false
            break
          }
          vector[i] = v
          sumSq += (v * v).toDouble()
        }
        if (valid) {
          val norm = sqrt(sumSq).toFloat()
          if (norm.isFinite() && norm > 1e-8f) {
            for (i in 0 until EMBEDDING_SIZE) {
              vector[i] /= norm
            }
            normalizedVector = vector
          }
        }
      }

      entries.add(
        ParsedFaceEntry(
          faceIndex = index,
          faceId = faceId,
          bounds = bounds,
          normalizedVector = normalizedVector,
        )
      )
    }

    val parsed = ParsedPhotoFaces(row.id, entries, idMap)
    synchronized(parsedFaceCache) {
      parsedFaceCache.put(cacheKey, parsed)
    }
    return parsed
  }

  /** Chronological cosine-distance clustering. A photo contributes at most one face per group. */
  fun cluster(
    rows: List<MediaItemEntity>,
    distanceThreshold: Float,
    excludedFaces: Set<FaceReference> = emptySet(),
    manualAssignments: Map<FaceReference, String> = emptyMap(),
    minimumPhotos: Int = MIN_PHOTOS,
  ): List<FacePersonGroup> {
    val clusters = mutableListOf<Cluster>()
    val rejectedFaceGroups = mutableListOf<FacePersonGroup>()
    val parsedByPhotoId = HashMap<Long, ParsedPhotoFaces>(rows.size)
    val matchThreshold = distanceThreshold.coerceIn(0.05f, 0.8f)

    rows.sortedBy { it.timestamp }.forEach { row ->
      val parsed = parsePhotoFaces(row) ?: return@forEach
      parsedByPhotoId[row.id] = parsed
      for (entry in parsed.entries) {
        val index = entry.faceIndex
        val reference = FaceReference(row.id, index)
        val bounds = entry.bounds

        // Ente turns a rejected face into its own cluster instead of deleting it.
        // Keep it visible as an unassigned correction candidate so it can be assigned elsewhere.
        if (reference in excludedFaces) {
          val rejectedId = "rejected-${row.id}-${entry.faceId ?: index}"
          rejectedFaceGroups +=
            FacePersonGroup(
              id = rejectedId,
              photoIds = listOf(row.id),
              faceCount = 1,
              coverPhotoId = row.id,
              coverFaceIndex = index,
              clusterIds = listOf(rejectedId),
              faces = listOf(reference),
              faceBounds = bounds?.let { mapOf(reference to it) } ?: emptyMap(),
            )
          continue
        }
        if (reference in manualAssignments) continue

        val vector = entry.normalizedVector ?: continue
        val stableFaceId = entry.faceId

        // Rank candidate groups using both centroid distance and the median
        // distance to member faces.
        var bestGroup: Cluster? = null
        var bestScore = Float.POSITIVE_INFINITY
        for (i in 0 until clusters.size) {
          val group = clusters[i]
          if (row.id in group.photoIds) continue
          val score =
            FaceClusterRanker.score(
              query = vector,
              centroid = group.centroid,
              memberCount = group.samples.size,
              memberAt = { memberIndex -> group.samples[memberIndex].vector },
            )
          if (score <= matchThreshold && score < bestScore) {
            bestScore = score
            bestGroup = group
          }
        }

        val sample = Sample(row.id, index, vector, bounds)
        if (bestGroup == null) {
          clusters +=
            Cluster(
              "person-${row.id}-${stableFaceId ?: index}",
              mutableListOf(sample),
              vector.copyOf(),
              mutableSetOf(row.id),
            )
        } else {
          bestGroup.samples += sample
          bestGroup.photoIds += row.id
          val count = bestGroup.samples.size.toFloat()
          val wPrev = (count - 1f) / count
          val wNew = 1f / count
          val mean = bestGroup.centroid
          var sumSq = 0.0
          for (i in 0 until EMBEDDING_SIZE) {
            val v = mean[i] * wPrev + vector[i] * wNew
            mean[i] = v
            sumSq += (v * v).toDouble()
          }
          val meanNorm = sqrt(sumSq).toFloat().coerceAtLeast(1e-8f)
          for (i in 0 until EMBEDDING_SIZE) {
            mean[i] /= meanNorm
          }
        }
      }
    }

    val minRequired = minimumPhotos.coerceAtLeast(1)
    val automaticGroups =
      clusters
        .filter { it.photoIds.size >= minRequired }
        .map { group ->
          val ids = group.photoIds.sortedDescending()
          val firstId = ids.first()
          FacePersonGroup(
            id = group.id,
            photoIds = ids,
            faceCount = group.samples.size,
            coverPhotoId = firstId,
            clusterIds = listOf(group.id),
            coverFaceIndex = group.samples.firstOrNull { it.photoId == firstId }?.faceIndex,
            faces = group.samples.map { FaceReference(it.photoId, it.faceIndex) },
            faceBounds =
              group.samples
                .mapNotNull { sample ->
                  sample.bounds?.let { FaceReference(sample.photoId, sample.faceIndex) to it }
                }
                .toMap(),
          )
        }

    // Ente's PersonService keeps explicit cluster/person assignments separate from
    // automatic similarity clustering.
    val manualGroups =
      manualAssignments.entries
        .groupBy({ it.value }, { it.key })
        .mapNotNull { (personClusterId, references) ->
          val photoIds = references.map(FaceReference::photoId).distinct().sortedDescending()
          if (photoIds.isEmpty()) null
          else {
            val firstId = photoIds.first()
            FacePersonGroup(
              id = personClusterId,
              photoIds = photoIds,
              faceCount = references.size,
              coverPhotoId = firstId,
              clusterIds = listOf(personClusterId),
              coverFaceIndex = references.firstOrNull { it.photoId == firstId }?.faceIndex,
              faces = references,
              faceBounds =
                references
                  .mapNotNull { reference ->
                    val parsed = parsedByPhotoId[reference.photoId] ?: return@mapNotNull null
                    val entry =
                      parsed.entries.firstOrNull { it.faceIndex == reference.faceIndex }
                        ?: return@mapNotNull null
                    entry.bounds?.let { reference to it }
                  }
                  .toMap(),
            )
          }
        }

    return (automaticGroups + manualGroups + rejectedFaceGroups)
      .groupBy(FacePersonGroup::id)
      .values
      .map { samePerson ->
        if (samePerson.size == 1) samePerson.single()
        else {
          val first = samePerson.first()
          val photoIds = samePerson.flatMap(FacePersonGroup::photoIds).distinct().sortedDescending()
          first.copy(
            photoIds = photoIds,
            faceCount = samePerson.sumOf(FacePersonGroup::faceCount),
            coverPhotoId = first.coverPhotoId.takeIf { it in photoIds } ?: photoIds.first(),
            coverFaceIndex = first.coverFaceIndex,
            clusterIds = samePerson.flatMap(FacePersonGroup::clusterIds).distinct(),
            faces = samePerson.flatMap(FacePersonGroup::faces).distinct(),
            faceBounds = samePerson.flatMap { it.faceBounds.entries }.associate { it.toPair() },
          )
        }
      }
      .sortedWith(compareByDescending<FacePersonGroup> { it.photoIds.size }.thenBy { it.id })
  }

  /** Resolve a persisted stable face ID to its current detector array index. */
  fun faceIndexForId(row: MediaItemEntity, faceId: String): Int? =
    parsePhotoFaces(row)?.indexByFaceId?.get(faceId)

  private const val EMBEDDING_SIZE = 192
  private const val MIN_PHOTOS = 2
}

/**
 * Ente-inspired candidate ranking: combine the cluster centroid with the median
 * query-to-member distance. The median reduces sensitivity to outlier face crops.
 */
internal object FaceClusterRanker {
  fun score(query: FloatArray, centroid: FloatArray, members: List<FloatArray>): Float =
    score(query, centroid, members.size) { index -> members[index] }

  fun score(
    query: FloatArray,
    centroid: FloatArray,
    memberCount: Int,
    memberAt: (Int) -> FloatArray,
  ): Float {
    require(query.size == centroid.size && memberCount >= 0)
    val centroidDistance = cosineDistance(query, centroid)
    if (memberCount == 0) return centroidDistance
    val sampleCount = minOf(memberCount, MAX_MEDIAN_SAMPLES)
    val distances =
      FloatArray(sampleCount) { sampleIndex ->
        val memberIndex =
          if (memberCount <= MAX_MEDIAN_SAMPLES) {
            sampleIndex
          } else {
            sampleIndex * (memberCount - 1) / (sampleCount - 1)
          }
        val member = memberAt(memberIndex)
        require(member.size == query.size)
        cosineDistance(query, member)
      }
    distances.sort()
    val middle = distances.size / 2
    val medianDistance =
      if (distances.size % 2 == 0) {
        (distances[middle - 1] + distances[middle]) / 2f
      } else {
        distances[middle]
      }
    return medianDistance * 0.7f + centroidDistance * 0.3f
  }

  private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
    var similarity = 0f
    for (i in a.indices) similarity += a[i] * b[i]
    return (1f - similarity).coerceIn(0f, 2f)
  }

  private const val MAX_MEDIAN_SAMPLES = 16
}
