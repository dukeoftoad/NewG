package com.example.data.ai

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

object FacePersonClusterer {
  private data class Sample(val photoId: Long, val faceIndex: Int, val vector: FloatArray, val bounds: FaceCropBounds?)
  private data class Cluster(val id: String, val samples: MutableList<Sample>, var centroid: FloatArray)

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
    rows.sortedBy { it.timestamp }.forEach { row ->
      val rawJson = row.faceDataJson ?: return@forEach
      val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@forEach
      val faces = json.optJSONArray("faces") ?: return@forEach
      for (index in 0 until faces.length()) {
        val reference = FaceReference(row.id, index)
        val faceObject = faces.optJSONObject(index) ?: continue
        val box = faceObject.optJSONArray("box")
        val bounds = if (box != null && box.length() >= 4) {
          val left = box.optDouble(0, Double.NaN).toFloat()
          val top = box.optDouble(1, Double.NaN).toFloat()
          val right = box.optDouble(2, Double.NaN).toFloat()
          val bottom = box.optDouble(3, Double.NaN).toFloat()
          if (listOf(left, top, right, bottom).all { it.isFinite() } && right > left && bottom > top) {
            FaceCropBounds(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
          } else null
        } else null

        // Ente turns a rejected face into its own cluster instead of deleting it.
        // Keep it visible as an unassigned correction candidate so it can be assigned elsewhere.
        if (reference in excludedFaces) {
          val rejectedFaceId = faceObject.optString("faceId").takeIf { it.isNotBlank() }
          val rejectedId = "rejected-${row.id}-${rejectedFaceId ?: index}"
          rejectedFaceGroups += FacePersonGroup(
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
        val values = faceObject.optJSONArray("embedding") ?: continue
        if (values.length() != EMBEDDING_SIZE) continue
        val vector = FloatArray(EMBEDDING_SIZE) { i -> values.optDouble(i, Double.NaN).toFloat() }
        if (vector.any { !it.isFinite() }) continue
        val stableFaceId = faceObject.optString("faceId").takeIf { it.isNotBlank() }
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm <= 1e-8f || !norm.isFinite()) continue
        for (i in vector.indices) vector[i] /= norm
        val match = clusters.asSequence()
          .filter { group -> group.samples.none { it.photoId == row.id } }
          .map { group -> group to cosineDistance(vector, group.centroid) }
          .filter { it.second <= distanceThreshold.coerceIn(0.05f, 0.8f) }
          .minByOrNull { it.second }
        val sample = Sample(row.id, index, vector, bounds)
        if (match == null) {
          clusters += Cluster("person-${row.id}-${stableFaceId ?: index}", mutableListOf(sample), vector.copyOf())
        } else {
          val group = match.first
          group.samples += sample
          val count = group.samples.size.toFloat()
          val mean = FloatArray(EMBEDDING_SIZE) { i ->
            group.centroid[i] * ((count - 1f) / count) + vector[i] / count
          }
          val meanNorm = sqrt(mean.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-8f)
          for (i in mean.indices) mean[i] /= meanNorm
          group.centroid = mean
        }
      }
    }
    val automaticGroups = clusters
      .filter { it.samples.map(Sample::photoId).distinct().size >= minimumPhotos.coerceAtLeast(1) }
      .map { group ->
        val ids = group.samples.map(Sample::photoId).distinct().sortedDescending()
        FacePersonGroup(
          group.id, ids, group.samples.size, ids.first(),
          clusterIds = listOf(group.id),
          coverFaceIndex = group.samples.firstOrNull { it.photoId == ids.first() }?.faceIndex,
          faces = group.samples.map { FaceReference(it.photoId, it.faceIndex) },
          faceBounds = group.samples.mapNotNull { sample ->
            sample.bounds?.let { FaceReference(sample.photoId, sample.faceIndex) to it }
          }.toMap(),
        )
      }

    // Ente's PersonService keeps explicit cluster/person assignments separate from
    // automatic similarity clustering. Assigned faces are therefore excluded above
    // and reattached to their chosen stable person here, so later reclustering cannot
    // silently move a manually corrected face back to an unrelated person.
    val manualGroups = manualAssignments.entries
      .groupBy({ it.value }, { it.key })
      .mapNotNull { (personClusterId, references) ->
        val photoIds = references.map(FaceReference::photoId).distinct().sortedDescending()
        if (photoIds.isEmpty()) null
        else FacePersonGroup(
          id = personClusterId,
          photoIds = photoIds,
          faceCount = references.size,
          coverPhotoId = photoIds.first(),
          clusterIds = listOf(personClusterId),
          coverFaceIndex = references.firstOrNull { it.photoId == photoIds.first() }?.faceIndex,
          faces = references,
          faceBounds = references.mapNotNull { reference ->
            val row = rows.firstOrNull { it.id == reference.photoId } ?: return@mapNotNull null
            val faceJson = row.faceDataJson ?: return@mapNotNull null
            val face = runCatching {
              JSONObject(faceJson).optJSONArray("faces")?.optJSONObject(reference.faceIndex)
            }.getOrNull()
            val box = face?.optJSONArray("box") ?: return@mapNotNull null
            if (box.length() < 4) return@mapNotNull null
            val bounds = FaceCropBounds(
              box.optDouble(0, 0.0).toFloat(), box.optDouble(1, 0.0).toFloat(),
              box.optDouble(2, 1.0).toFloat(), box.optDouble(3, 1.0).toFloat(),
            )
            reference to bounds
          }.toMap(),
        )
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
  fun faceIndexForId(row: MediaItemEntity, faceId: String): Int? {
    val faces = runCatching {
      row.faceDataJson?.let { JSONObject(it).optJSONArray("faces") }
    }.getOrNull() ?: return null
    return (0 until faces.length()).firstOrNull { index ->
      faces.optJSONObject(index)?.optString("faceId") == faceId
    }
  }

  private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
    var similarity = 0f
    for (i in a.indices) similarity += a[i] * b[i]
    return (1f - similarity).coerceIn(0f, 2f)
  }

  private const val EMBEDDING_SIZE = 192
  private const val MIN_PHOTOS = 2
}
