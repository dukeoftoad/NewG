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
