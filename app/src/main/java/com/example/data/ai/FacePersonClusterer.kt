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
  ): List<FacePersonGroup> {
    val clusters = mutableListOf<Cluster>()
    rows.sortedBy { it.timestamp }.forEach { row ->
      val rawJson = row.faceDataJson ?: return@forEach
      val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@forEach
      val faces = json.optJSONArray("faces") ?: return@forEach
      for (index in 0 until faces.length()) {
        if (FaceReference(row.id, index) in excludedFaces) continue
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
    return clusters.filter { it.samples.map(Sample::photoId).distinct().size >= MIN_PHOTOS }
      .map { group ->
        val ids = group.samples.map(Sample::photoId).distinct().sortedDescending()
        FacePersonGroup(
          group.id, ids, group.samples.size, ids.first(),
          clusterIds = listOf(group.id),
          faces = group.samples.map { FaceReference(it.photoId, it.faceIndex) },
        )
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
