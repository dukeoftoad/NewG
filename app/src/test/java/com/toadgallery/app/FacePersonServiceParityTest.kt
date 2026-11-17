package com.toadgallery.app

import androidx.room.Room
import com.toadgallery.app.data.ai.FacePersonFeedbackManager
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ente-parity verification for `person_service.dart` (parity row 25) and
 * `save_or_edit_person.dart` (parity row 33) — previously "Partial".
 *
 * Covers the person CRUD surface the way Ente's person service models it:
 * create (unknown cluster -> record created), update/rename (identity-stable,
 * membership untouched), name validation (trim, blank -> null), duplicate
 * names allowed at the data layer (merge is projection-level, see
 * FacePersonMergeParityTest), cover create/overwrite/rename interaction, and
 * reconciliation after new detections (name + cover survive a re-index that
 * reorders faces).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePersonServiceParityTest {
  private lateinit var database: ToadGalleryDatabase
  private lateinit var dao: GalleryDao
  private lateinit var feedback: FacePersonFeedbackManager

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      RuntimeEnvironment.getApplication(),
      ToadGalleryDatabase::class.java,
    ).allowMainThreadQueries().build()
    dao = database.galleryDao()
    feedback = FacePersonFeedbackManager(dao)
  }

  @After
  fun tearDown() {
    database.close()
  }

  /** Photo with two stable-ID faces (array position = detector index). */
  private suspend fun insertTwoFacePhoto(title: String = "svc-photo"): Long {
    val json =
      """{"faces":[
         {"faceIndex":0,"faceId":"stable-1"},
         {"faceIndex":1,"faceId":"stable-2"}]}"""
    dao.insertMedia(
      listOf(
        MediaItemEntity(
          title = title,
          category = "CAMERA",
          mediaType = "PHOTO",
          dateLabel = "2026-01-01",
          weekGroup = "2026-W01",
          monthGroup = "2026-01",
          timestamp = 0L,
          locationName = "",
          hasGps = false,
          mapX = 0f,
          mapY = 0f,
          aspectRatio = 1f,
          tagsCsv = "",
          ocrText = "",
          exifSummary = "",
          faceDataJson = json,
        ),
      ),
    )
    return dao.observeVisibleMedia().first().single { it.title == title }.id
  }

  // --- save_or_edit_person (row 33): create/update/validation/stability ---

  @Test
  fun setPersonNameCreatesRecordForUnknownClusterAndTrims() = runBlocking {
    assertNull(dao.getFacePerson("cluster-x"))

    feedback.setPersonName("cluster-x", "  Alice  ")

    val person = dao.getFacePerson("cluster-x")
    assertNotNull("unknown cluster gets a record (create path)", person)
    assertEquals("name is trimmed", "Alice", person!!.name)
    assertEquals("clusterId is the identity", "cluster-x", person.clusterId)
  }

  @Test
  fun setPersonNameBlankClearsNameButKeepsRecord() = runBlocking {
    feedback.setPersonName("cluster-x", "Alice")
    feedback.setPersonName("cluster-x", "   ")

    val person = dao.getFacePerson("cluster-x")
    assertNotNull("record survives a blank rename", person)
    assertNull("blank name is stored as null", person!!.name)
  }

  @Test
  fun renamePreservesClusterIdentityAndMembership() = runBlocking {
    val photoId = insertTwoFacePhoto()
    feedback.assignFace(photoId, 0, "cluster-x")

    feedback.setPersonName("cluster-x", "Alice")
    feedback.setPersonName("cluster-x", "Alicia")

    val person = dao.getFacePerson("cluster-x")!!
    assertEquals("clusterId never changes on rename", "cluster-x", person.clusterId)
    assertEquals("latest name wins", "Alicia", person.name)

    val assignment = dao.getFaceAssignmentsForPhoto(photoId).single()
    assertEquals("membership untouched by rename", "cluster-x", assignment.personClusterId)
    assertEquals("assignment face identity untouched", "stable-1", assignment.faceId)
  }

  @Test
  fun duplicateNamesAcrossClustersAreAllowedAtDataLayer() = runBlocking {
    feedback.setPersonName("cluster-a", "Twin")
    feedback.setPersonName("cluster-b", "Twin")

    assertEquals("two records exist", 2, dao.observeFacePeople().first().size)
    assertEquals("Twin", dao.getFacePerson("cluster-a")!!.name)
    assertEquals("Twin", dao.getFacePerson("cluster-b")!!.name)
    // Merge happens at projection level (FacePersonMergeParityTest); the data
    // layer never deduplicates or rejects duplicate names.
  }

  // --- person_service (row 25): cover create/overwrite + rename interaction ---

  @Test
  fun coverUpdatePersistsAndSurvivesRenameAndOverwrite() = runBlocking {
    val photoId = insertTwoFacePhoto()

    feedback.setPersonName("cluster-x", "Ivy")
    feedback.setPersonCover("cluster-x", photoId, 0)

    var person = dao.getFacePerson("cluster-x")!!
    assertEquals(photoId, person.coverPhotoId)
    assertEquals(0, person.coverFaceIndex)
    assertEquals("cover carries the stable face identity", "stable-1", person.coverFaceId)

    feedback.setPersonName("cluster-x", "Ivy II")
    person = dao.getFacePerson("cluster-x")!!
    assertEquals("cover survives rename", photoId, person.coverPhotoId)
    assertEquals("Ivy II", person.name)

    feedback.setPersonCover("cluster-x", photoId, 1)
    person = dao.getFacePerson("cluster-x")!!
    assertEquals("overwrite replaces the index", 1, person.coverFaceIndex)
    assertEquals("overwrite replaces the identity", "stable-2", person.coverFaceId)
  }

  // --- person_service (row 25): reconciliation after new detections ---

  @Test
  fun nameAndCoverSurviveReindexReconciliationWithReorderedFaces() = runBlocking {
    val photoId = insertTwoFacePhoto()
    feedback.assignFace(photoId, 0, "cluster-x")
    feedback.setPersonName("cluster-x", "Ivy")
    feedback.setPersonCover("cluster-x", photoId, 0)

    // "New detections arrive": same stable identities, reversed detector order.
    val reindexed =
      """{"faces":[
         {"faceIndex":0,"faceId":"stable-2"},
         {"faceIndex":1,"faceId":"stable-1"}]}"""
    dao.updateFaceIndexAndReconcile(photoId, reindexed, System.currentTimeMillis())

    val person = dao.getFacePerson("cluster-x")!!
    assertEquals("name survives re-index", "Ivy", person.name)
    assertEquals("cover photo survives re-index", photoId, person.coverPhotoId)
    assertEquals("cover face identity unchanged", "stable-1", person.coverFaceId)
    assertEquals("cover index remapped to the face's new position", 1, person.coverFaceIndex)

    val assignment = dao.getFaceAssignmentsForPhoto(photoId).single()
    assertEquals("assignment follows the stable identity", 1, assignment.faceIndex)
    assertEquals("stable id preserved on the assignment", "stable-1", assignment.faceId)
    assertEquals("cluster unchanged", "cluster-x", assignment.personClusterId)
    assertTrue("no duplicate rows created", dao.getFaceAssignmentsForPhoto(photoId).size == 1)
  }
}
