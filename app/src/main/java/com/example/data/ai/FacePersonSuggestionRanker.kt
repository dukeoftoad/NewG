package com.example.data.ai

import com.example.data.local.MediaItemEntity
import org.json.JSONObject
import kotlin.math.sqrt

data class FacePersonSuggestion(
  val personId: String,
  val meanDistance: Float,
  val medianDistance: Float,
)

/**
 * Local-only adaptation of Ente's two-stage person suggestion flow:
 * centroid/mean-distance filtering followed by sampled pairwise median distance.
 *
 * Ente samples randomly from persisted cluster embeddings. NewG samples deterministically
 * and evenly across each local face list so suggestions remain reproducible on-device.
 */
object FacePersonSuggestionRanker {
  fun rank(
    candidate: FacePersonGroup,
    people: List<FacePersonGroup>,
    rows: List<MediaItemEntity>,
    sampleSize: Int = 20,
    maxMeanDistance: Float = 0.55f,
    maxMedianDistance: Float = 0.55f,
  ): List<FacePersonSuggestion> =
    rankAll(listOf(candidate), people, rows, sampleSize, maxMeanDistance, maxMedianDistance)
      .getOrDefault(candidate.id, emptyList())

  /** Rank many unassigned candidates while decoding each indexed face embedding only once. */
  fun rankAll(
    candidates: List<FacePersonGroup>,
    people: List<FacePersonGroup>,
    rows: List<MediaItemEntity>,
    sampleSize: Int = 20,
    maxMeanDistance: Float = 0.55f,
    maxMedianDistance: Float = 0.55f,
  ): Map<String, List<FacePersonSuggestion>> {
    if (sampleSize <= 0 || candidates.isEmpty() || people.isEmpty()) return emptyMap()
    val rowsById = rows.associateBy { it.id }
    val references = (candidates + people).flatMap { it.faces }.distinct()
    val vectorsByReference = references.mapNotNull { reference ->
      val row = rowsById[reference.photoId] ?: return@mapNotNull null
      vectorFor(row, reference.faceIndex)?.let { reference to it }
    }.toMap()

    return candidates.associate { candidate ->
      candidate.id to rankOne(
        candidate,
        people,
        vectorsByReference,
        sampleSize,
        maxMeanDistance,
        maxMedianDistance,
      )
    }
  }

  private fun rankOne(
    candidate: FacePersonGroup,
    people: List<FacePersonGroup>,
    vectorsByReference: Map<FaceReference, FloatArray>,
    sampleSize: Int,
    maxMeanDistance: Float,
    maxMedianDistance: Float,
  ): List<FacePersonSuggestion> {
    val candidateVectors = candidate.faces.distinct().mapNotNull(vectorsByReference::get)
    if (candidateVectors.isEmpty()) return emptyList()
    val candidateMean = normalizedMean(candidateVectors) ?: return emptyList()
    val candidateSample = evenlySample(candidateVectors, sampleSize)

    return people.asSequence()
      .filter { person -> person.id != candidate.id }
      .mapNotNull { person ->
        val personVectors = person.faces.distinct().mapNotNull(vectorsByReference::get)
        if (personVectors.isEmpty()) return@mapNotNull null
        val personMean = normalizedMean(personVectors) ?: return@mapNotNull null
        val meanDistance = cosineDistance(candidateMean, personMean)
        if (meanDistance >= maxMeanDistance) return@mapNotNull null

        val personSample = evenlySample(personVectors, sampleSize)
        val distances = buildList(candidateSample.size * personSample.size) {
          candidateSample.forEach { candidateVector ->
            personSample.forEach { personVector ->
              add(cosineDistance(candidateVector, personVector))
            }
          }
        }.sorted()
        if (distances.isEmpty()) return@mapNotNull null
        // Match Ente's upper-middle median (sorted[length ~/ 2]).
        val medianDistance = distances[distances.size / 2]
        if (medianDistance >= maxMedianDistance) return@mapNotNull null
        FacePersonSuggestion(person.id, meanDistance, medianDistance)
      }
      .sortedWith(compareBy<FacePersonSuggestion> { it.medianDistance }.thenBy { it.meanDistance })
      .toList()
  }

  private fun vectorFor(row: MediaItemEntity, faceIndex: Int): FloatArray? {
    val json = row.faceDataJson ?: return null
    return runCatching {
      val face = JSONObject(json).optJSONArray("faces")?.optJSONObject(faceIndex)
        ?: return@runCatching null
      val values = face.optJSONArray("embedding") ?: return@runCatching null
      if (values.length() != EMBEDDING_SIZE) return@runCatching null
      val vector = FloatArray(EMBEDDING_SIZE) { index ->
        values.optDouble(index, Double.NaN).toFloat()
      }
      if (vector.any { !it.isFinite() }) return@runCatching null
      val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
      if (!norm.isFinite() || norm <= 1e-8f) return@runCatching null
      for (index in vector.indices) vector[index] /= norm
      vector
    }.getOrNull()
  }

  private fun normalizedMean(vectors: List<FloatArray>): FloatArray? {
    if (vectors.isEmpty()) return null
    val mean = FloatArray(EMBEDDING_SIZE)
    vectors.forEach { vector ->
      for (index in mean.indices) mean[index] += vector[index] / vectors.size
    }
    val norm = sqrt(mean.sumOf { (it * it).toDouble() }).toFloat()
    if (!norm.isFinite() || norm <= 1e-8f) return null
    for (index in mean.indices) mean[index] /= norm
    return mean
  }

  private fun evenlySample(vectors: List<FloatArray>, limit: Int): List<FloatArray> {
    if (vectors.size <= limit) return vectors
    return List(limit) { index -> vectors[(index * vectors.size) / limit] }
  }

  private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
    var similarity = 0f
    for (index in a.indices) similarity += a[index] * b[index]
    return (1f - similarity).coerceIn(0f, 2f)
  }

  private const val EMBEDDING_SIZE = 192
}
