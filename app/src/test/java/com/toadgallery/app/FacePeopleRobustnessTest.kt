package com.toadgallery.app.data.ai

import com.toadgallery.app.data.local.MediaItemEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePeopleRobustnessTest {
  @Test fun identicalRankerVectorsHaveZeroDistance() {
    assertEquals(0f, FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), listOf(v2(1f, 0f))), 0.0001f)
  }
  @Test fun orthogonalRankerVectorsHaveUnitDistance() {
    assertEquals(1f, FaceClusterRanker.score(v2(1f, 0f), v2(0f, 1f), emptyList()), 0.0001f)
  }
  @Test fun oppositeRankerVectorsClampDistanceToTwo() {
    assertEquals(2f, FaceClusterRanker.score(v2(1f, 0f), v2(-1f, 0f), emptyList()), 0.0001f)
  }
  @Test fun rankerFallsBackToCentroidForEmptyCluster() {
    assertEquals(1f, FaceClusterRanker.score(v2(1f, 0f), v2(0f, 1f), emptyList()), 0.0001f)
  }
  @Test fun rankerUsesMedianForOddMemberCount() {
    val score = FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), listOf(v2(1f, 0f), v2(0f, 1f), v2(0f, 1f)))
    assertEquals(0.7f, score, 0.0001f)
  }
  @Test fun rankerAveragesMiddleDistancesForEvenMemberCount() {
    val score = FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), listOf(v2(1f, 0f), v2(0f, 1f)))
    assertEquals(0.35f, score, 0.0001f)
  }
  @Test fun rankerCapsMemberSamplingAtSixteen() {
    val visited = mutableListOf<Int>()
    FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), 100) { index ->
      visited += index
      v2(1f, 0f)
    }
    assertEquals(16, visited.size)
    assertEquals(0, visited.first())
    assertEquals(99, visited.last())
  }
  @Test fun rankerSamplingIsDeterministic() {
    fun sample() = buildList {
      FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), 80) { index ->
        add(index); v2(1f, 0f)
      }
    }
    assertEquals(sample(), sample())
  }
  @Test fun rankerWeightsMedianAtSeventyPercent() {
    val score = FaceClusterRanker.score(v2(1f, 0f), v2(0f, 1f), listOf(v2(1f, 0f), v2(1f, 0f), v2(1f, 0f)))
    assertEquals(0.3f, score, 0.0001f)
  }
  @Test fun rankerRejectsMismatchedCentroidDimensions() {
    assertThrows(IllegalArgumentException::class.java) {
      FaceClusterRanker.score(v2(1f, 0f), floatArrayOf(1f), emptyList())
    }
  }
  @Test fun rankerRejectsMismatchedMemberDimensions() {
    assertThrows(IllegalArgumentException::class.java) {
      FaceClusterRanker.score(v2(1f, 0f), v2(1f, 0f), listOf(floatArrayOf(1f)))
    }
  }
  @Test fun clustererSkipsMalformedJson() {
    assertTrue(FacePersonClusterer.cluster(listOf(media(1, 1, "{broken")), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsMissingFacesArray() {
    assertTrue(FacePersonClusterer.cluster(listOf(media(1, 1, """{"version":2}""")), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsNullFaceEntries() {
    assertTrue(FacePersonClusterer.cluster(listOf(media(1, 1, """{"faces":[null]}""")), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsMissingEmbeddings() {
    val row = media(1, 1, """{"faces":[{"box":[0.1,0.2,0.6,0.8]}]}""")
    assertTrue(FacePersonClusterer.cluster(listOf(row), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsWrongEmbeddingLength() {
    val row = media(1, 1, """{"faces":[{"embedding":[1,0,0]}]}""")
    assertTrue(FacePersonClusterer.cluster(listOf(row), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsNonFiniteEmbeddingValues() {
    // org.json rejects non-finite numeric values at construction time. A string
    // value exercises the clusterer's optDouble conversion and finite-value guard.
    val values = JSONArray().apply { repeat(192) { put(if (it == 0) "NaN" else 0.0) } }
    val json = JSONObject().put("faces", JSONArray().put(JSONObject().put("embedding", values))).toString()
    assertTrue(FacePersonClusterer.cluster(listOf(media(1, 1, json)), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererSkipsZeroMagnitudeEmbeddings() {
    assertTrue(FacePersonClusterer.cluster(listOf(media(1, 1, faceJson(listOf(FloatArray(192))))), 0.2f, minimumPhotos = 1).isEmpty())
  }
  @Test fun clustererProcessesRowsChronologically() {
    val rows = listOf(media(3, 3, faceJson(listOf(axis(0)))), media(1, 1, faceJson(listOf(axis(0)))), media(2, 2, faceJson(listOf(axis(0)))))
    val group = FacePersonClusterer.cluster(rows, 0.2f).single()
    assertEquals("person-1-0", group.id)
    assertEquals(listOf(3L, 2L, 1L), group.photoIds)
  }
  @Test fun clustererDoesNotPutTwoFacesFromOnePhotoInSameGroup() {
    val rows = listOf(media(1, 1, faceJson(listOf(axis(0), axis(0)))), media(2, 2, faceJson(listOf(axis(0), axis(0)))))
    val groups = FacePersonClusterer.cluster(rows, 0.2f)
    assertEquals(2, groups.size)
    assertTrue(groups.all { it.photoIds == listOf(2L, 1L) })
    assertTrue(groups.all { it.faces.map(FaceReference::photoId).distinct().size == 2 })
  }
  @Test fun minimumPhotoCountIsClampedToOne() {
    val row = media(1, 1, faceJson(listOf(axis(0))))
    assertEquals(1, FacePersonClusterer.cluster(listOf(row), 0.2f, minimumPhotos = 0).size)
  }
  @Test fun minimumPhotoCountHidesUndersizedGroups() {
    val rows = listOf(media(1, 1, faceJson(listOf(axis(0)))), media(2, 2, faceJson(listOf(axis(0)))))
    assertTrue(FacePersonClusterer.cluster(rows, 0.2f, minimumPhotos = 3).isEmpty())
  }
  @Test fun excludedFaceRemainsReviewableWithoutEmbedding() {
    val row = media(7, 7, """{"faces":[{"faceId":"stable-x","box":[0,0,1,1]}]}""")
    val group = FacePersonClusterer.cluster(listOf(row), 0.2f, excludedFaces = setOf(FaceReference(7, 0)), minimumPhotos = 1).single()
    assertEquals("rejected-7-stable-x", group.id)
    assertEquals(FaceReference(7, 0), group.faces.single())
  }
  @Test fun manualAssignmentKeepsSinglePhotoPersonVisible() {
    val row = media(8, 8, faceJson(listOf(axis(0))))
    val groups = FacePersonClusterer.cluster(listOf(row), 0.2f, manualAssignments = mapOf(FaceReference(8, 0) to "person-kept"))
    assertEquals("person-kept", groups.single().id)
    assertEquals(listOf(8L), groups.single().photoIds)
  }
  @Test fun manualAssignmentCombinesFacesAcrossPhotos() {
    val rows = listOf(media(8, 8, faceJson(listOf(axis(0)))), media(9, 9, faceJson(listOf(axis(1)))))
    val groups = FacePersonClusterer.cluster(rows, 0.2f, manualAssignments = mapOf(FaceReference(8, 0) to "manual", FaceReference(9, 0) to "manual"))
    assertEquals(1, groups.size)
    assertEquals("manual", groups.single().id)
    assertEquals(2, groups.single().faceCount)
  }
  @Test fun invalidFaceBoundsAreOmitted() {
    val row = media(10, 10, faceJson(listOf(axis(0)), box = listOf(0.5, 0.5, 0.2, 0.8)))
    assertTrue(FacePersonClusterer.cluster(listOf(row), 0.2f, minimumPhotos = 1).single().faceBounds.isEmpty())
  }
  @Test fun faceBoundsAreClampedToNormalizedCoordinates() {
    val row = media(11, 11, faceJson(listOf(axis(0)), box = listOf(-0.2, 0.1, 1.4, 1.2)))
    val bounds = FacePersonClusterer.cluster(listOf(row), 0.2f, minimumPhotos = 1).single().faceBounds.values.single()
    assertEquals(FaceCropBounds(0f, 0.1f, 1f, 1f), bounds)
  }
  @Test fun stableFaceLookupReturnsCurrentIndex() {
    val row = media(12, 12, faceJson(listOf(axis(0), axis(1)), listOf("a", "b")))
    assertEquals(1, FacePersonClusterer.faceIndexForId(row, "b"))
  }
  @Test fun stableFaceLookupReturnsNullForUnknownId() {
    val row = media(12, 12, faceJson(listOf(axis(0)), listOf("a")))
    assertNull(FacePersonClusterer.faceIndexForId(row, "missing"))
  }
  @Test fun stableFaceLookupReturnsNullForMalformedPayload() {
    assertNull(FacePersonClusterer.faceIndexForId(media(12, 12, "{"), "face"))
  }
  @Test fun assignmentOrderingIgnoresUnknownSuggestionIds() {
    val ordered = orderFaceAssignmentTargets(listOf(group("alice")), listOf(FacePersonSuggestion("missing", 0f, 0f)))
    assertEquals(listOf("alice"), ordered.map { it.id })
  }
  @Test fun assignmentOrderingDeduplicatesSuggestions() {
    val ordered = orderFaceAssignmentTargets(listOf(group("alice"), group("bob")), listOf(FacePersonSuggestion("bob", 0.1f, 0.1f), FacePersonSuggestion("bob", 0.2f, 0.2f)))
    assertEquals(listOf("bob", "alice"), ordered.map { it.id })
  }
  @Test fun assignmentOrderingKeepsFallbackPeople() {
    val ordered = orderFaceAssignmentTargets(listOf(group("a"), group("b"), group("c")), listOf(FacePersonSuggestion("c", 0f, 0f)))
    assertEquals(listOf("c", "a", "b"), ordered.map { it.id })
  }
  @Test fun clusterOutputIsSortedBySize() {
    val rows = listOf(media(1, 1, faceJson(listOf(axis(0), axis(1)))), media(2, 2, faceJson(listOf(axis(0), axis(1)))), media(3, 3, faceJson(listOf(axis(0)))))
    val groups = FacePersonClusterer.cluster(rows, 0.2f, minimumPhotos = 1)
    assertEquals(2, groups.size)
    assertTrue(groups.first().photoIds.size >= groups.last().photoIds.size)
  }
  @Test fun thresholdBelowMinimumDoesNotJoinOrthogonalFaces() {
    val rows = listOf(media(1, 1, faceJson(listOf(axis(0)))), media(2, 2, faceJson(listOf(axis(1)))))
    assertEquals(2, FacePersonClusterer.cluster(rows, -1f, minimumPhotos = 1).size)
  }
  @Test fun thresholdAboveMaximumDoesNotJoinOrthogonalFaces() {
    val rows = listOf(media(1, 1, faceJson(listOf(axis(0)))), media(2, 2, faceJson(listOf(axis(1)))))
    assertEquals(2, FacePersonClusterer.cluster(rows, 4f, minimumPhotos = 1).size)
  }

  private fun group(id: String) = FacePersonGroup(id, listOf(1L), 1, 1L)
  private fun v2(x: Float, y: Float) = floatArrayOf(x, y)
  private fun axis(index: Int) = FloatArray(192).also { it[index] = 1f }
  private fun faceJson(embeddings: List<FloatArray>, ids: List<String?>? = null, box: List<Double> = listOf(0.1, 0.2, 0.6, 0.8)): String {
    val faces = JSONArray()
    embeddings.forEachIndexed { index, embedding ->
      val values = JSONArray()
      embedding.forEach { values.put(it.toDouble()) }
      val face = JSONObject().put("embedding", values).put("box", JSONArray(box))
      ids?.getOrNull(index)?.let { face.put("faceId", it) }
      faces.put(face)
    }
    return JSONObject().put("faces", faces).toString()
  }
  private fun media(id: Long, timestamp: Long, json: String) = MediaItemEntity(
    id = id, title = "Photo $id", category = "CAMERA", mediaType = "PHOTO",
    dateLabel = "", weekGroup = "", monthGroup = "", timestamp = timestamp,
    locationName = "", hasGps = false, mapX = 0f, mapY = 0f, aspectRatio = 1f,
    tagsCsv = "", ocrText = "", exifSummary = "", faceDataJson = json,
  )
}
