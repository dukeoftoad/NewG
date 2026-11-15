package com.example.data.ai

import com.example.data.local.MediaItemEntity
import org.json.JSONObject
import kotlin.math.sqrt

data class FaceReference(val photoId: Long, val faceIndex: Int)

data class FacePersonGroup(
  val id: String,
  val photoIds: List<Long>,
  val faceCount: Int,
  val coverPhotoId: Long,
  val name: String? = null,
  val clusterIds: List<String> = listOf(id),
  val faces: List<FaceReference> = emptyList(),
)

object FacePersonClusterer {
  private data class Sample(val photoId: Long, val faceIndex: Int, val vector: FloatArray)
  private data class Cluster(val id: String, val samples: MutableList<Sample>, var centroid: FloatArray)

  /** Chronological cosine-distance clustering. A photo contributes at most one face per group. */
  fun cluster(
    rows: List<MediaItemEntity>,
    distanceThreshold: Float,
    excludedFaces: Set<FaceReference> = emptySet(),
    manualAssignments: Map<FaceReference, String> = emptyMap(),
  ): List<FacePersonGroup> {
    val clusters = mutableListOf<Cluster>()
    rows.sortedBy { it.timestamp }.forEach { row ->
      val rawJson = row.faceDataJson ?: return@forEach
      val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@forEach
      val faces = json.optJSONArray("faces") ?: return@forEach
      for (index in 0 until faces.length()) {
        val reference = FaceReference(row.id, index)
        if (reference in excludedFaces || reference in manualAssignments) continue
        val values = faces.optJSONObject(index)?.optJSONArray("embedding") ?: continue
        if (values.length() != EMBEDDING_SIZE) continue
        val vector = FloatArray(EMBEDDING_SIZE) { i -> values.optDouble(i, Double.NaN).toFloat() }
        if (vector.any { !it.isFinite() }) continue
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm <= 1e-8f || !norm.isFinite()) continue
        for (i in vector.indices) vector[i] /= norm
        val match = clusters.asSequence()
          .filter { group -> group.samples.none { it.photoId == row.id } }
          .map { group -> group to cosineDistance(vector, group.centroid) }
          .filter { it.second <= distanceThreshold.coerceIn(0.05f, 0.8f) }
          .minByOrNull { it.second }
        val sample = Sample(row.id, index, vector)
        if (match == null) {
          clusters += Cluster("person-${row.id}-$index", mutableListOf(sample), vector.copyOf())
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
      .filter { it.samples.map(Sample::photoId).distinct().size >= MIN_PHOTOS }
      .map { group ->
        val ids = group.samples.map(Sample::photoId).distinct().sortedDescending()
        FacePersonGroup(
          group.id, ids, group.samples.size, ids.first(),
          clusterIds = listOf(group.id),
          faces = group.samples.map { FaceReference(it.photoId, it.faceIndex) },
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
          faces = references,
        )
      }

    return (automaticGroups + manualGroups)
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
            clusterIds = samePerson.flatMap(FacePersonGroup::clusterIds).distinct(),
            faces = samePerson.flatMap(FacePersonGroup::faces).distinct(),
          )
        }
      }
      .sortedWith(compareByDescending<FacePersonGroup> { it.photoIds.size }.thenBy { it.id })
  }

  private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
    var similarity = 0f
    for (i in a.indices) similarity += a[i] * b[i]
    return (1f - similarity).coerceIn(0f, 2f)
  }

  private const val EMBEDDING_SIZE = 192
  private const val MIN_PHOTOS = 2
}
