package com.example.data.ai

import com.example.data.local.MediaItemEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePersonClustererTest {

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

  private fun media(id: Long, timestamp: Long, embeddings: List<FloatArray>): MediaItemEntity {
    val faces = JSONArray()
    embeddings.forEach { vector ->
      val values = JSONArray()
      vector.forEach { values.put(it.toDouble()) }
      faces.put(
        JSONObject()
          .put("embedding", values)
          .put("box", JSONArray().put(0.1).put(0.2).put(0.6).put(0.8))
      )
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
