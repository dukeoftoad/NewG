package com.example.data.ai

import com.example.data.local.MediaItemEntity
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

  /** Rank many unassigned candidates while reusing cached parsed face embeddings. */
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
    val vectorsByReference = HashMap<FaceReference, FloatArray>(references.size)
    references.forEach { reference ->
      val row = rowsById[reference.photoId] ?: return@forEach
      val parsed = FacePersonClusterer.parsePhotoFaces(row) ?: return@forEach
      val entry = parsed.entries.firstOrNull { it.faceIndex == reference.faceIndex }
      val vec = entry?.normalizedVector
      if (vec != null) {
        vectorsByReference[reference] = vec
      }
    }

    // Precompute person means and samples once across all candidates
    val preparedPeople =
      people.mapNotNull { person ->
        val personVectors = person.faces.distinct().mapNotNull(vectorsByReference::get)
        if (personVectors.isEmpty()) return@mapNotNull null
        val personMean = normalizedMean(personVectors) ?: return@mapNotNull null
        val personSample = evenlySample(personVectors, sampleSize)
        PreparedPerson(person.id, personMean, personSample)
      }

    if (preparedPeople.isEmpty()) return emptyMap()

    return candidates.associate { candidate ->
      candidate.id to
        rankOne(
          candidate = candidate,
          preparedPeople = preparedPeople,
          vectorsByReference = vectorsByReference,
          sampleSize = sampleSize,
          maxMeanDistance = maxMeanDistance,
          maxMedianDistance = maxMedianDistance,
        )
    }
  }

  private data class PreparedPerson(
    val id: String,
    val mean: FloatArray,
    val sample: List<FloatArray>,
  )

  private fun rankOne(
    candidate: FacePersonGroup,
    preparedPeople: List<PreparedPerson>,
    vectorsByReference: Map<FaceReference, FloatArray>,
    sampleSize: Int,
    maxMeanDistance: Float,
    maxMedianDistance: Float,
  ): List<FacePersonSuggestion> {
    val candidateVectors = candidate.faces.distinct().mapNotNull(vectorsByReference::get)
    if (candidateVectors.isEmpty()) return emptyList()
    val candidateMean = normalizedMean(candidateVectors) ?: return emptyList()
    val candidateSample = evenlySample(candidateVectors, sampleSize)

    return preparedPeople
      .asSequence()
      .filter { person -> person.id != candidate.id }
      .mapNotNull { person ->
        val meanDistance = cosineDistance(candidateMean, person.mean)
        if (meanDistance >= maxMeanDistance) return@mapNotNull null

        val distances = FloatArray(candidateSample.size * person.sample.size)
        var k = 0
        for (i in candidateSample.indices) {
          val cVec = candidateSample[i]
          for (j in person.sample.indices) {
            distances[k++] = cosineDistance(cVec, person.sample[j])
          }
        }
        if (k == 0) return@mapNotNull null
        distances.sort()
        // Match Ente's upper-middle median (sorted[length ~/ 2]).
        val medianDistance = distances[distances.size / 2]
        if (medianDistance >= maxMedianDistance) return@mapNotNull null
        FacePersonSuggestion(person.id, meanDistance, medianDistance)
      }
      .sortedWith(compareBy<FacePersonSuggestion> { it.medianDistance }.thenBy { it.meanDistance })
      .toList()
  }

  private fun normalizedMean(vectors: List<FloatArray>): FloatArray? {
    if (vectors.isEmpty()) return null
    val mean = FloatArray(EMBEDDING_SIZE)
    val invSize = 1f / vectors.size
    for (vIdx in vectors.indices) {
      val vector = vectors[vIdx]
      for (index in 0 until EMBEDDING_SIZE) {
        mean[index] += vector[index] * invSize
      }
    }
    var sumSq = 0.0
    for (index in 0 until EMBEDDING_SIZE) {
      val v = mean[index].toDouble()
      sumSq += v * v
    }
    val norm = sqrt(sumSq).toFloat()
    if (!norm.isFinite() || norm <= 1e-8f) return null
    for (index in 0 until EMBEDDING_SIZE) mean[index] /= norm
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

/** Put ranked matches first while retaining every remaining person as a fallback. */
fun orderFaceAssignmentTargets(
  people: List<FacePersonGroup>,
  suggestions: List<FacePersonSuggestion>,
): List<FacePersonGroup> {
  val peopleById = people.associateBy { it.id }
  val suggested = suggestions.mapNotNull { peopleById[it.personId] }.distinctBy { it.id }
  val suggestedIds = suggested.mapTo(mutableSetOf()) { it.id }
  return suggested + people.filterNot { it.id in suggestedIds }
}
