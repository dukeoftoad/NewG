package com.example.data.ai

import com.example.data.local.MediaItemEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePersonClustererTest {

  @Test
  fun assignmentTargetsPutRankedPeopleFirstAndKeepOtherPeopleAsFallbacks() {
    val alice = FacePersonGroup("alice", listOf(1L), 1, 1L, name = "Alice")
    val bob = FacePersonGroup("bob", listOf(2L), 1, 2L, name = "Bob")
    val carol = FacePersonGroup("carol", listOf(3L), 1, 3L, name = "Carol")

    val ordered = orderFaceAssignmentTargets(
      people = listOf(alice, bob, carol),
      suggestions = listOf(
        FacePersonSuggestion("carol", 0.08f, 0.12f),
        FacePersonSuggestion("alice", 0.10f, 0.18f),
        FacePersonSuggestion("missing", 0.01f, 0.02f),
      ),
    )

    assertEquals(listOf("carol", "alice", "bob"), ordered.map { it.id })
  }



  @Test
  fun resolvesStableFaceIdentityWhenDetectorOrderingChanges() {
    val original = media(
      id = 20L,
      timestamp = 1L,
      embeddings = listOf(vector(0), vector(1)),
      faceIds = listOf("face-a", "face-b"),
    )
    val reordered = media(
      id = 20L,
      timestamp = 1L,
      embeddings = listOf(vector(1), vector(0)),
      faceIds = listOf("face-b", "face-a"),
    )
    val nextPhoto = media(
      id = 21L,
      timestamp = 2L,
      embeddings = listOf(vector(0), vector(1)),
      faceIds = listOf("face-c", "face-d"),
    )

    assertEquals(0, FacePersonClusterer.faceIndexForId(original, "face-a"))
    assertEquals(1, FacePersonClusterer.faceIndexForId(reordered, "face-a"))
    assertEquals(
      FacePersonClusterer.cluster(listOf(original, nextPhoto), 0.24f).map { it.id }.toSet(),
      FacePersonClusterer.cluster(listOf(reordered, nextPhoto), 0.24f).map { it.id }.toSet(),
    )
  }

  @Test
  fun legacyIndexOnlyFeedbackIsIgnoredOnceStableFaceIdsAreAvailable() {
    val legacyPayload = media(
      id = 22L,
      timestamp = 1L,
      embeddings = listOf(vector(0), vector(1)),
    )
    val stablePayload = media(
      id = 22L,
      timestamp = 1L,
      embeddings = listOf(vector(1), vector(0)),
      faceIds = listOf("face-b", "face-a"),
    )

    assertEquals(1, FacePersonClusterer.faceIndexForLegacyIndex(legacyPayload, 1))
    assertNull(FacePersonClusterer.faceIndexForLegacyIndex(stablePayload, 0))
    assertNull(FacePersonClusterer.faceIndexForLegacyIndex(stablePayload, -1))
  }

  @Test
  fun rejectedFaceCandidateIdSurvivesDetectorOrderingChanges() {
    val original = media(
      id = 30L,
      timestamp = 1L,
      embeddings = listOf(vector(0), vector(1)),
      faceIds = listOf("face-a", "face-b"),
    )
    val reordered = media(
      id = 30L,
      timestamp = 1L,
      embeddings = listOf(vector(1), vector(0)),
      faceIds = listOf("face-b", "face-a"),
    )

    val before = FacePersonClusterer.cluster(
      listOf(original),
      distanceThreshold = 0.24f,
      excludedFaces = setOf(FaceReference(30L, 0)),
      minimumPhotos = 1,
    ).single { it.id.startsWith("rejected-") }
    val after = FacePersonClusterer.cluster(
      listOf(reordered),
      distanceThreshold = 0.24f,
      excludedFaces = setOf(FaceReference(30L, 1)),
      minimumPhotos = 1,
    ).single { it.id.startsWith("rejected-") }

    assertEquals(before.id, after.id)
    assertEquals("rejected-30-face-a", after.id)
    assertEquals(FaceReference(30L, 1), after.faces.single())
  }

  @Test
  fun groupsMatchingFacesAcrossDifferentPhotos() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0))),
      media(id = 2L, timestamp = 2L, embeddings = listOf(vector(0))),
    )

    val groups = FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f)

    assertEquals(1, groups.size)
    assertEquals(listOf(2L, 1L), groups.single().photoIds)
    assertEquals(2, groups.single().faceCount)
  }

  @Test
  fun excludesOnlyTheSelectedFaceFromACluster() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0))),
      media(id = 2L, timestamp = 2L, embeddings = listOf(vector(0))),
      media(id = 3L, timestamp = 3L, embeddings = listOf(vector(0))),
    )

    val groups = FacePersonClusterer.cluster(
      rows,
      distanceThreshold = 0.24f,
      excludedFaces = setOf(FaceReference(photoId = 2L, faceIndex = 0)),
    )

    val remainingPerson = groups.single { it.id != "rejected-2-0" }
    val rejectedCandidate = groups.single { it.id == "rejected-2-0" }
    assertEquals(2, groups.size)
    assertEquals(listOf(3L, 1L), remainingPerson.photoIds)
    assertEquals(
      setOf(FaceReference(1L, 0), FaceReference(3L, 0)),
      remainingPerson.faces.toSet(),
    )
    assertEquals(listOf(2L), rejectedCandidate.photoIds)
    assertEquals(FaceReference(2L, 0), rejectedCandidate.faces.single())
  }


  @Test
  fun manualFaceAssignmentOverridesSimilarityClusterAndPersistsPersonIdentity() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0))),
      media(id = 2L, timestamp = 2L, embeddings = listOf(vector(0))),
      media(id = 3L, timestamp = 3L, embeddings = listOf(vector(1))),
      media(id = 4L, timestamp = 4L, embeddings = listOf(vector(1))),
    )

    val groups = FacePersonClusterer.cluster(
      rows,
      distanceThreshold = 0.24f,
      manualAssignments = mapOf(FaceReference(1L, 0) to "person-3-0"),
    )

    assertEquals(1, groups.size)
    assertEquals("person-3-0", groups.single().id)
    assertEquals(listOf(4L, 3L, 1L), groups.single().photoIds)
    assertEquals(3, groups.single().faceCount)
    assertTrue(FaceReference(1L, 0) in groups.single().faces)
  }

  @Test
  fun keepsFaceBoundsAndCoverFaceIdentityForPeopleThumbnails() {
    val rows = listOf(
      media(id = 11L, timestamp = 1L, embeddings = listOf(vector(0))),
      media(id = 12L, timestamp = 2L, embeddings = listOf(vector(0))),
    )

    val group = FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f).single()

    assertEquals(12L, group.coverPhotoId)
    assertEquals(0, group.coverFaceIndex)
    assertEquals(
      FaceCropBounds(0.1f, 0.2f, 0.6f, 0.8f),
      group.faceBounds[FaceReference(12L, 0)],
    )
  }

  @Test
  fun doesNotExposeAClusterFoundInOnlyOnePhoto() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0), vector(0))),
    )

    assertTrue(FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f).isEmpty())
  }

  @Test
  fun canReturnSinglePhotoFaceCandidatesForPeopleReview() {
    val rows = listOf(media(id = 5L, timestamp = 5L, embeddings = listOf(vector(0))))

    assertTrue(FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f).isEmpty())
    val candidates = FacePersonClusterer.cluster(
      rows,
      distanceThreshold = 0.24f,
      minimumPhotos = 1,
    )
    assertEquals(1, candidates.size)
    assertEquals(listOf(5L), candidates.single().photoIds)
    assertEquals(FaceReference(5L, 0), candidates.single().faces.single())
  }

  @Test
  fun keepsClearlyDifferentPeopleInSeparateGroups() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0), vector(1))),
      media(id = 2L, timestamp = 2L, embeddings = listOf(vector(0), vector(1))),
    )

    val groups = FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f)

    assertEquals(2, groups.size)
    assertTrue(groups.all { it.photoIds.size == 2 })
    assertTrue(groups.all { it.faceCount == 2 })
  }

  @Test
  fun clusterRankingUsesMedianMemberDistanceToResistOutlierCentroids() {
    val query = floatArrayOf(1f, 0f)
    val centroidOnlyWinner = FaceClusterRanker.score(
      query = query,
      centroid = floatArrayOf(1f, 0f),
      members = listOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f), floatArrayOf(1f, 0f)),
    )
    val consistentGroup = FaceClusterRanker.score(
      query = query,
      centroid = floatArrayOf(0.8f, 0.6f),
      members = listOf(floatArrayOf(0.8f, 0.6f), floatArrayOf(0.8f, 0.6f), floatArrayOf(0.8f, 0.6f)),
    )

    assertTrue(consistentGroup < centroidOnlyWinner)
  }

  @Test
  fun personSuggestionsUseMeanFilteringAndMedianPairwiseRanking() {
    val candidateRow = media(30L, 3L, listOf(vector(0)))
    val matchingRow = media(31L, 1L, listOf(vector(0)))
    val unrelatedRow = media(32L, 2L, listOf(vector(1)))
    val nearVector = FloatArray(192).also {
      it[0] = 0.9f
      it[1] = 0.4358899f
    }
    val nearRow = media(33L, 2L, listOf(nearVector))
    val candidate = FacePersonGroup(
      id = "candidate",
      photoIds = listOf(30L),
      faceCount = 1,
      coverPhotoId = 30L,
      faces = listOf(FaceReference(30L, 0)),
    )
    val matchingPerson = FacePersonGroup(
      id = "matching-person",
      photoIds = listOf(31L),
      faceCount = 1,
      coverPhotoId = 31L,
      faces = listOf(FaceReference(31L, 0)),
    )
    val unrelatedPerson = FacePersonGroup(
      id = "unrelated-person",
      photoIds = listOf(32L),
      faceCount = 1,
      coverPhotoId = 32L,
      faces = listOf(FaceReference(32L, 0)),
    )
    val nearPerson = FacePersonGroup(
      id = "near-person",
      photoIds = listOf(33L),
      faceCount = 1,
      coverPhotoId = 33L,
      faces = listOf(FaceReference(33L, 0)),
    )

    val suggestions = FacePersonSuggestionRanker.rank(
      candidate,
      listOf(unrelatedPerson, nearPerson, matchingPerson),
      listOf(candidateRow, matchingRow, unrelatedRow, nearRow),
    )

    assertEquals(listOf("matching-person", "near-person"), suggestions.map { it.personId })
    assertEquals(0f, suggestions.first().meanDistance, 0.0001f)
    assertEquals(0f, suggestions.first().medianDistance, 0.0001f)
    assertTrue(suggestions[1].medianDistance > suggestions[0].medianDistance)
  }

  @Test
  fun medianSuggestionStageRejectsAClusterWithInconsistentFaceEmbeddings() {
    val candidateRow = media(40L, 3L, listOf(vector(0), vector(1)))
    val personRow = media(41L, 1L, listOf(vector(0), vector(1)))
    val candidate = FacePersonGroup(
      id = "candidate",
      photoIds = listOf(40L),
      faceCount = 2,
      coverPhotoId = 40L,
      faces = listOf(FaceReference(40L, 0), FaceReference(40L, 1)),
    )
    val person = FacePersonGroup(
      id = "person",
      photoIds = listOf(41L),
      faceCount = 2,
      coverPhotoId = 41L,
      faces = listOf(FaceReference(41L, 0), FaceReference(41L, 1)),
    )

    val suggestions = FacePersonSuggestionRanker.rank(candidate, listOf(person), listOf(candidateRow, personRow))

    // The centroids match, but cross-face distances have a high median.
    assertTrue(suggestions.isEmpty())
  }

  private fun media(
    id: Long,
    timestamp: Long,
    embeddings: List<FloatArray>,
    faceIds: List<String?>? = null,
  ): MediaItemEntity {
    val faces = JSONArray()
    embeddings.forEachIndexed { index, vector ->
      val values = JSONArray()
      vector.forEach { values.put(it.toDouble()) }
      val face = JSONObject()
        .put("embedding", values)
        .put("box", JSONArray().put(0.1).put(0.2).put(0.6).put(0.8))
      faceIds?.getOrNull(index)?.let { face.put("faceId", it) }
      faces.put(face)
    }
    return MediaItemEntity(
      id = id,
      title = "Photo $id",
      category = "CAMERA",
      mediaType = "PHOTO",
      dateLabel = "",
      weekGroup = "",
      monthGroup = "",
      timestamp = timestamp,
      locationName = "",
      hasGps = false,
      mapX = 0f,
      mapY = 0f,
      aspectRatio = 1f,
      tagsCsv = "",
      ocrText = "",
      exifSummary = "",
      faceDataJson = JSONObject().put("faces", faces).toString(),
    )
  }

  private fun vector(axis: Int): FloatArray =
    FloatArray(192).also { it[axis] = 1f }
}
