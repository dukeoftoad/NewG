package com.toadgallery.app

import androidx.room.Room
import com.toadgallery.app.data.ai.FacePersonClusterer
import com.toadgallery.app.data.ai.FacePersonGroup
import com.toadgallery.app.data.ai.FacePersonSuggestionRanker
import com.toadgallery.app.data.ai.FacePersonFeedbackManager
import com.toadgallery.app.data.ai.FaceReference
import com.toadgallery.app.data.ai.orderFaceAssignmentTargets
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ente-parity verification for `person_gallery_suggestion.dart` (parity row 35)
 * and the data layer of `pick_person_avatar.dart` (parity row 32).
 *
 * Suggestion side: the two-stage mean/median cosine gate of
 * [FacePersonSuggestionRanker] — near faces suggest, distant faces do not,
 * results order by median then mean, a candidate never suggests itself, and
 * [orderFaceAssignmentTargets] surfaces suggested people first.
 *
 * Avatar side: a persisted cover resolves to the face's CURRENT index via its
 * stable face ID after re-indexing, and resolves to null when the face was
 * removed from the payload (the precondition for the UI's fall-back to the
 * first available face). Rejection is bound to the face's stable ID, so a
 * rejected face does not come back as a suggestion after detector reordering.
 */
class FacePersonSuggestionParityTest {
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

  /** Unit vector along `axis` (e_i), the cosine anchor for fixtures. */
  private fun unitVector(axis: Int, tilt: Float = 0f): FloatArray {
    val v = FloatArray(192)
    v[axis] = 1f
    if (tilt != 0f) v[(axis + 1) % 192] = tilt
    return v
  }

  private fun faceJson(faceId: String, vector: FloatArray): String =
    JSONObject()
      .put(
        "faces",
        JSONArray()
          .put(
            JSONObject()
              .put("faceIndex", 0)
              .put("faceId", faceId)
              .put("embedding", JSONArray(vector.toList())),
          ),
      )
      .toString()

  private suspend fun insertFacePhoto(photoIdHint: String, faceId: String, vector: FloatArray): MediaItemEntity {
    val title = "sug-$photoIdHint"
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
          faceDataJson = faceJson(faceId, vector),
        ),
      ),
    )
    return dao.observeVisibleMedia().first().single { it.title == title }
  }

  private fun group(id: String, vararg refs: FaceReference): FacePersonGroup =
    FacePersonGroup(
      id = id,
      photoIds = refs.map { it.photoId },
      faceCount = refs.size,
      coverPhotoId = refs.first().photoId,
      faces = refs.toList(),
    )

  @Test
  fun nearIdenticalCandidateSuggestsItsPerson() = runBlocking {
    val personPhoto = insertFacePhoto("p", "p-face", unitVector(0))
    val candPhoto = insertFacePhoto("c", "c-face", unitVector(0, tilt = 0.01f))
    val rows = listOf(personPhoto, candPhoto)

    val suggestions =
      FacePersonSuggestionRanker.rankAll(
        candidates = listOf(group("cand", FaceReference(candPhoto.id, 0))),
        people = listOf(group("person-p", FaceReference(personPhoto.id, 0))),
        rows = rows,
      )

    val forCandidate = suggestions["cand"].orEmpty()
    assertEquals("near face suggests the matching person", listOf("person-p"), forCandidate.map { it.personId })
    assertTrue("mean distance is tiny", forCandidate.single().meanDistance < 0.1f)
    assertTrue("median distance is tiny", forCandidate.single().medianDistance < 0.1f)
  }

  @Test
  fun distantCandidateIsNotSuggested() = runBlocking {
    val personPhoto = insertFacePhoto("p", "p-face", unitVector(0))
    val candPhoto = insertFacePhoto("c", "c-face", unitVector(1))
    val rows = listOf(personPhoto, candPhoto)

    val suggestions =
      FacePersonSuggestionRanker.rankAll(
        candidates = listOf(group("cand", FaceReference(candPhoto.id, 0))),
        people = listOf(group("person-p", FaceReference(personPhoto.id, 0))),
        rows = rows,
      )

    assertTrue(
      "orthogonal faces exceed the 0.55 mean gate — no suggestion",
      suggestions["cand"].isNullOrEmpty(),
    )
  }

  @Test
  fun multipleSuggestionsOrderByMedianThenMean() = runBlocking {
    val p1 = insertFacePhoto("p1", "p1-face", unitVector(0))
    val p2 = insertFacePhoto("p2", "p2-face", unitVector(0, tilt = 0.3f))
    val p3 = insertFacePhoto("p3", "p3-face", unitVector(0, tilt = 0.6f))
    val cand = insertFacePhoto("c", "c-face", unitVector(0))
    val rows = listOf(p1, p2, p3, cand)

    val suggestions =
      FacePersonSuggestionRanker.rankAll(
        candidates = listOf(group("cand", FaceReference(cand.id, 0))),
        people = listOf(
          group("person-p1", FaceReference(p1.id, 0)),
          group("person-p2", FaceReference(p2.id, 0)),
          group("person-p3", FaceReference(p3.id, 0)),
        ),
        rows = rows,
      )

    assertEquals(
      "closest person first (median, then mean tiebreak)",
      listOf("person-p1", "person-p2", "person-p3"),
      suggestions["cand"].orEmpty().map { it.personId },
    )
  }

  @Test
  fun candidateNeverSuggestsItself() = runBlocking {
    val photo = insertFacePhoto("p", "p-face", unitVector(0))
    val rows = listOf(photo)

    val suggestions =
      FacePersonSuggestionRanker.rankAll(
        candidates = listOf(group("cand", FaceReference(photo.id, 0))),
        people = listOf(group("cand", FaceReference(photo.id, 0))),
        rows = rows,
      )

    assertTrue("self is filtered out", suggestions["cand"].isNullOrEmpty())
  }

  @Test
  fun assignmentTargetsPutSuggestedPeopleFirst() {
    val people = listOf(group("p1", FaceReference(1L, 0)), group("p2", FaceReference(2L, 0)), group("p3", FaceReference(3L, 0)))
    val targets = orderFaceAssignmentTargets(people, listOf(com.toadgallery.app.data.ai.FacePersonSuggestion("p3", 0.1f, 0.1f)))
    assertEquals("suggested person first, remaining order retained", listOf("p3", "p1", "p2"), targets.map { it.id })
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePersonAvatarIdentityParityTest {
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

  private suspend fun insertTwoFacePhoto(title: String = "avatar-photo"): Long {
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

  @Test
  fun coverFaceIdResolvesToNewIndexAfterReorder() = runBlocking {
    val photoId = insertTwoFacePhoto()
    feedback.setPersonCover("cluster-x", photoId, 0)
    assertEquals("cover initially at index 0", 0, dao.getFacePerson("cluster-x")!!.coverFaceIndex)

    val reindexed =
      """{"faces":[
         {"faceIndex":0,"faceId":"stable-2"},
         {"faceIndex":1,"faceId":"stable-1"}]}"""
    dao.updateFaceIndexAndReconcile(photoId, reindexed, System.currentTimeMillis())

    val row = dao.getMediaById(photoId)!!
    assertEquals(
      "stable cover face ID resolves to the face's new detector index",
      1,
      FacePersonClusterer.faceIndexForId(row, "stable-1"),
    )
  }

  @Test
  fun coverFaceIdReturnsNullWhenFaceRemovedFromPayload() = runBlocking {
    val photoId = insertTwoFacePhoto()
    feedback.setPersonCover("cluster-x", photoId, 0)

    // The avatar source face disappears from the payload entirely.
    val reindexed = """{"faces":[{"faceIndex":0,"faceId":"stable-2"}]}"""
    dao.updateFaceIndexAndReconcile(photoId, reindexed, System.currentTimeMillis())

    val row = dao.getMediaById(photoId)!!
    assertNull(
      "removed face resolves to null — the UI falls back to the first available face",
      FacePersonClusterer.faceIndexForId(row, "stable-1"),
    )
    assertEquals(
      "remaining face still resolves",
      0,
      FacePersonClusterer.faceIndexForId(row, "stable-2"),
    )
  }

  @Test
  fun rejectedFaceStaysRejectedAcrossReindex() = runBlocking {
    val photoId = insertTwoFacePhoto()
    feedback.rejectFace(photoId, 0)
    assertTrue("rejected at index 0", dao.isFaceExcluded(photoId, 0))

    val reindexed =
      """{"faces":[
         {"faceIndex":0,"faceId":"stable-2"},
         {"faceIndex":1,"faceId":"stable-1"}]}"""
    dao.updateFaceIndexAndReconcile(photoId, reindexed, System.currentTimeMillis())

    assertTrue(
      "rejection follows the stable face ID to its new index — it cannot come back as a suggestion",
      dao.isFaceExcluded(photoId, 1),
    )
    assertTrue("old index no longer holds the rejection", !dao.isFaceExcluded(photoId, 0))
  }
}
