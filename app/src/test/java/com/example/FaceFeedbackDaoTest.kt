package com.example.data.local

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceFeedbackDaoTest {
  private lateinit var database: ToadGalleryDatabase
  private lateinit var dao: GalleryDao

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      RuntimeEnvironment.getApplication(),
      ToadGalleryDatabase::class.java,
    ).allowMainThreadQueries().build()
    dao = database.galleryDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun assignmentUndoRestoresPreviousAssignment() = runBlocking {
    dao.assignFaceWithHistory(42L, 1, "person-a", 100L)
    dao.assignFaceWithHistory(42L, 1, "person-b", 200L)

    assertEquals("person-b", dao.getFaceAssignment(42L, 1)?.personClusterId)
    assertTrue(dao.undoLatestFaceCorrection())
    assertEquals("person-a", dao.getFaceAssignment(42L, 1)?.personClusterId)
    assertTrue(dao.undoLatestFaceCorrection())
    assertNull(dao.getFaceAssignment(42L, 1))
  }

  @Test
  fun assignmentAfterExclusionClearsExclusionAndUndoRestoresIt() = runBlocking {
    dao.excludeFaceWithHistory(7L, 0, 100L)
    assertTrue(dao.isFaceExcluded(7L, 0))

    dao.assignFaceWithHistory(7L, 0, "person-target", 200L)
    assertFalse(dao.isFaceExcluded(7L, 0))
    assertEquals("person-target", dao.getFaceAssignment(7L, 0)?.personClusterId)

    assertTrue(dao.undoLatestFaceCorrection())
    assertTrue(dao.isFaceExcluded(7L, 0))
    assertNull(dao.getFaceAssignment(7L, 0))
  }

  @Test
  fun restoringExcludedFaceCanBeUndone() = runBlocking {
    dao.excludeFaceWithHistory(9L, 2, 100L)
    dao.restoreFaceWithHistory(9L, 2, 200L)
    assertFalse(dao.isFaceExcluded(9L, 2))

    assertTrue(dao.undoLatestFaceCorrection())
    assertTrue(dao.isFaceExcluded(9L, 2))
    assertTrue(dao.undoLatestFaceCorrection())
    assertFalse(dao.isFaceExcluded(9L, 2))
  }

  @Test
  fun assignmentsAndUndoFollowStableFaceIdAfterDetectorReordering() = runBlocking {
    dao.insertSingleMedia(
      MediaItemEntity(
        id = 91L,
        title = "Face identity fixture",
        category = "CAMERA",
        mediaType = "PHOTO",
        dateLabel = "",
        weekGroup = "",
        monthGroup = "",
        timestamp = 1L,
        locationName = "",
        hasGps = false,
        mapX = 0f,
        mapY = 0f,
        aspectRatio = 1f,
        tagsCsv = "",
        ocrText = "",
        exifSummary = "",
        faceDataJson = faceJson("face-a", "face-b"),
      )
    )
    dao.assignFaceWithHistory(
      photoId = 91L,
      faceIndex = 0,
      personClusterId = "person-before",
      changedAt = 100L,
      faceId = "face-a",
    )

    dao.updateFaceIndexAndReconcile(91L, faceJson("face-b", "face-a"), 200L)
    dao.assignFaceWithHistory(
      photoId = 91L,
      faceIndex = 1,
      personClusterId = "person-after",
      changedAt = 300L,
      faceId = "face-a",
    )

    assertNull(dao.getFaceAssignment(91L, 0))
    assertEquals("person-after", dao.getFaceAssignment(91L, 1)?.personClusterId)
    assertTrue(dao.undoLatestFaceCorrection())
    assertNull(dao.getFaceAssignment(91L, 0))
    assertEquals("person-before", dao.getFaceAssignment(91L, 1)?.personClusterId)
    assertEquals("face-a", dao.getFaceAssignment(91L, 1)?.faceId)
  }

  @Test
  fun exclusionsAndUndoResolveTheCurrentFaceIndexAfterReordering() = runBlocking {
    dao.insertSingleMedia(
      MediaItemEntity(
        id = 92L,
        title = "Face exclusion fixture",
        category = "CAMERA",
        mediaType = "PHOTO",
        dateLabel = "",
        weekGroup = "",
        monthGroup = "",
        timestamp = 1L,
        locationName = "",
        hasGps = false,
        mapX = 0f,
        mapY = 0f,
        aspectRatio = 1f,
        tagsCsv = "",
        ocrText = "",
        exifSummary = "",
        faceDataJson = faceJson("face-a", "face-b"),
      )
    )
    dao.excludeFaceWithHistory(92L, 0, 100L, "face-a")
    dao.updateFaceIndexAndReconcile(92L, faceJson("face-b", "face-a"), 200L)
    assertTrue(dao.isFaceExcludedByFaceId(92L, "face-a"))

    dao.restoreFaceWithHistory(92L, 1, 300L, "face-a")
    assertFalse(dao.isFaceExcludedByFaceId(92L, "face-a"))
    assertTrue(dao.undoLatestFaceCorrection())
    assertTrue(dao.isFaceExcludedByFaceId(92L, "face-a"))
  }

  @Test
  fun reindexReconcilesMultipleAssignmentsWithoutOverwritingAnotherFace() = runBlocking {
    dao.insertSingleMedia(
      MediaItemEntity(
        id = 93L,
        title = "Two assigned faces",
        category = "CAMERA",
        mediaType = "PHOTO",
        dateLabel = "",
        weekGroup = "",
        monthGroup = "",
        timestamp = 1L,
        locationName = "",
        hasGps = false,
        mapX = 0f,
        mapY = 0f,
        aspectRatio = 1f,
        tagsCsv = "",
        ocrText = "",
        exifSummary = "",
        faceDataJson = faceJson("face-a", "face-b"),
      )
    )
    dao.assignFaceWithHistory(93L, 0, "person-a", 100L, "face-a")
    dao.assignFaceWithHistory(93L, 1, "person-b", 110L, "face-b")

    dao.updateFaceIndexAndReconcile(93L, faceJson("face-b", "face-a"), 200L)

    assertNull(dao.getFaceAssignment(93L, 2))
    assertEquals("person-b", dao.getFaceAssignment(93L, 0)?.personClusterId)
    assertEquals("face-b", dao.getFaceAssignment(93L, 0)?.faceId)
    assertEquals("person-a", dao.getFaceAssignment(93L, 1)?.personClusterId)
    assertEquals("face-a", dao.getFaceAssignment(93L, 1)?.faceId)
  }

  @Test
  fun correctionHistoryAndCustomCoverFollowStableFaceAfterReindex() = runBlocking {
    dao.insertSingleMedia(
      MediaItemEntity(
        id = 94L,
        title = "Cover identity fixture",
        category = "CAMERA",
        mediaType = "PHOTO",
        dateLabel = "",
        weekGroup = "",
        monthGroup = "",
        timestamp = 1L,
        locationName = "",
        hasGps = false,
        mapX = 0f,
        mapY = 0f,
        aspectRatio = 1f,
        tagsCsv = "",
        ocrText = "",
        exifSummary = "",
        faceDataJson = faceJson("face-a", "face-b"),
      )
    )
    dao.insertFaceCorrectionHistory(
      FaceCorrectionHistoryEntity(
        photoId = 94L,
        faceIndex = 0,
        faceId = "face-a",
        previousPersonClusterId = "person-old",
        nextPersonClusterId = "person-new",
        createdAt = 100L,
      )
    )
    dao.upsertFacePerson(
      FacePersonEntity(
        clusterId = "person-new",
        name = "Alice",
        coverPhotoId = 94L,
        coverFaceIndex = 0,
        coverFaceId = "face-a",
      )
    )

    dao.updateFaceIndexAndReconcile(94L, faceJson("face-b", "face-a"), 200L)

    assertEquals(1, dao.getFaceCorrectionHistoryForPhoto(94L).single().faceIndex)
    val person = dao.getFacePerson("person-new")
    assertEquals(1, person?.coverFaceIndex)
    assertEquals("face-a", person?.coverFaceId)
  }

  @Test
  fun ignoreAndUnignorePersonMetadataPersistsWithoutLosingNameOrCover() = runBlocking {
    dao.upsertFacePerson(
      FacePersonEntity(
        clusterId = "person-ignore",
        name = "Bob",
        coverPhotoId = 12L,
        coverFaceIndex = 2,
        coverFaceId = "face-bob",
        isIgnored = true,
      )
    )

    val ignored = dao.getFacePerson("person-ignore")
    assertTrue(ignored?.isIgnored == true)
    dao.upsertFacePerson(ignored!!.copy(isIgnored = false, updatedAt = 200L))

    val restored = dao.getFacePerson("person-ignore")
    assertFalse(restored?.isIgnored == true)
    assertEquals("Bob", restored?.name)
    assertEquals(12L, restored?.coverPhotoId)
    assertEquals("face-bob", restored?.coverFaceId)
  }

  private fun faceJson(vararg ids: String): String {
    val faces = JSONArray()
    ids.forEach { id -> faces.put(JSONObject().put("faceId", id)) }
    return JSONObject().put("version", 2).put("faces", faces).toString()
  }

  @Test
  fun undoReturnsFalseWhenHistoryIsEmptyOrAlreadyUndone() = runBlocking {
    assertFalse(dao.undoLatestFaceCorrection())
    dao.assignFaceWithHistory(1L, 0, "person-a", 100L)
    assertTrue(dao.undoLatestFaceCorrection())
    assertFalse(dao.undoLatestFaceCorrection())
  }
  @Test
  fun clearingFaceIndexAlsoClearsAllFaceFeedbackAndPersonMetadata() = runBlocking {
    dao.assignFaceWithHistory(42L, 1, "person-a", 100L)
    dao.excludeFaceWithHistory(43L, 0, 110L)
    dao.upsertFacePerson(
      FacePersonEntity(
        clusterId = "person-a",
        name = "Alice",
        coverPhotoId = 42L,
        coverFaceIndex = 1,
        isIgnored = true,
      )
    )

    dao.clearFaceIndexAndFeedback()

    assertNull(dao.getFaceAssignment(42L, 1))
    assertFalse(dao.isFaceExcluded(43L, 0))
    assertNull(dao.getFacePerson("person-a"))
    assertNull(dao.getLatestUndoableFaceCorrection())
  }

}
