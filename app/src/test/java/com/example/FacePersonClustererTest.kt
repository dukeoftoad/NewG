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
  fun doesNotExposeAClusterFoundInOnlyOnePhoto() {
    val rows = listOf(
      media(id = 1L, timestamp = 1L, embeddings = listOf(vector(0), vector(0))),
    )

    assertTrue(FacePersonClusterer.cluster(rows, distanceThreshold = 0.24f).isEmpty())
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
      faces.put(JSONObject().put("embedding", values))
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
