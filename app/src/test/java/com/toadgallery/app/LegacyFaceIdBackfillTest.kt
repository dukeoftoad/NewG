package com.toadgallery.app

import androidx.room.Room
import com.toadgallery.app.data.local.FaceAssignmentEntity
import com.toadgallery.app.data.local.FaceExclusionEntity
import com.toadgallery.app.data.local.FacePersonEntity
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
 * E3 (v2.1.0) — the safe backfill/recovery policy for legacy v8 feedback rows
 * with null faceId (ENTE-PEOPLE-PARITY.md row 24, "remains to be completed").
 *
 * The lazy path only backfills when a photo is re-indexed. [GalleryDao.backfillLegacyFaceIds]
 * is the eager one-time pass: it derives stable face IDs from each photo's
 * CURRENT payload (the same deterministic model/photo/box/embedding signature
 * the indexer uses), backfills null-faceId assignment/exclusion/history/cover
 * rows, and re-keys automatic v8 `person-<photoId>-<index>` cluster IDs to the
 * stable face-based ID — for photos that may never be re-indexed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegacyFaceIdBackfillTest {
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

  /** Deterministic per-face embedding — distinct faces must derive distinct IDs. */
  private fun embedding(faceIndex: Int): FloatArray =
    FloatArray(192) { ((it + faceIndex * 3) % 7) * 0.01f }

  /** v8-style payload: box + embedding, NO faceId fields. */
  private fun v8Payload(withEmbedding: Boolean = true): String {
    val faces = JSONArray()
    repeat(2) { index ->
      val face = JSONObject().put("faceIndex", index).put("box", JSONArray(listOf(0.1, 0.2, 0.5, 0.6)))
      if (withEmbedding) face.put("embedding", JSONArray(embedding(index).toList()))
      faces.put(face)
    }
    return JSONObject().put("faces", faces).toString()
  }

  private suspend fun insertPhoto(title: String, payload: String): Long {
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
          faceDataJson = payload,
        ),
      ),
    )
    return dao.observeVisibleMedia().first().single { it.title == title }.id
  }

  @Test
  fun eagerBackfillDerivesStableIdsFromCurrentPayload() = runBlocking {
    val photoId = insertPhoto("legacy", v8Payload())
    val payloadBefore = dao.getMediaById(photoId)!!.faceDataJson

    dao.upsertFaceAssignment(FaceAssignmentEntity(photoId = photoId, faceIndex = 0, personClusterId = "person-$photoId-0"))
    dao.upsertFaceExclusion(FaceExclusionEntity(photoId = photoId, faceIndex = 1))
    dao.upsertFacePerson(
      FacePersonEntity(
        clusterId = "person-$photoId-0",
        name = "Legacy Person",
        coverPhotoId = photoId,
        coverFaceIndex = 0,
        coverFaceId = null,
      ),
    )

    val backfilled = dao.backfillLegacyFaceIds()

    assertTrue("assignment + exclusion + cover backfilled", backfilled >= 3)

    val assignment = dao.getFaceAssignmentsForPhoto(photoId).single()
    val stableFace0 = assignment.faceId
    assertNotNull("assignment gains a stable face ID", stableFace0)

    val exclusion = dao.getFaceExclusionsForPhoto(photoId).single()
    assertNotNull("exclusion gains a stable face ID", exclusion.faceId)
    assertTrue("different faces derive different IDs", exclusion.faceId != stableFace0)

    assertNull("v8 cluster id is gone", dao.getFacePerson("person-$photoId-0"))
    val rekeyed = dao.getFacePerson("person-$photoId-$stableFace0")
    assertNotNull("person record re-keyed to the stable face-based ID", rekeyed)
    assertEquals("name survives the re-key", "Legacy Person", rekeyed!!.name)
    assertEquals("cover photo survives", photoId, rekeyed.coverPhotoId)
    assertEquals("cover gains the same stable ID as the face it points at", stableFace0, rekeyed.coverFaceId)

    assertEquals("payload is never modified by the backfill", payloadBefore, dao.getMediaById(photoId)!!.faceDataJson)
  }

  @Test
  fun backfillIsIdempotent() = runBlocking {
    val photoId = insertPhoto("legacy2", v8Payload())
    dao.upsertFaceAssignment(FaceAssignmentEntity(photoId = photoId, faceIndex = 0, personClusterId = "person-$photoId-0"))

    val first = dao.backfillLegacyFaceIds()
    assertTrue(first >= 1)
    val afterFirst = dao.getFaceAssignmentsForPhoto(photoId).single().faceId

    val second = dao.backfillLegacyFaceIds()
    assertEquals("second pass has nothing left to do", 0, second)
    assertEquals("identity unchanged after the second pass", afterFirst, dao.getFaceAssignmentsForPhoto(photoId).single().faceId)
    assertEquals("no duplicate rows", 1, dao.getFaceAssignmentsForPhoto(photoId).size)
  }

  @Test
  fun underivablePayloadRowsAreLeftUntouched() = runBlocking {
    // Box only, no embedding: the deterministic signature cannot be derived,
    // so the row keeps null faceId and the runtime saved-index fallback.
    val photoId = insertPhoto("opaque", v8Payload(withEmbedding = false))
    dao.upsertFaceAssignment(FaceAssignmentEntity(photoId = photoId, faceIndex = 0, personClusterId = "person-$photoId-0"))
    dao.upsertFacePerson(
      FacePersonEntity(clusterId = "person-$photoId-0", name = "Untouched Person", coverPhotoId = photoId, coverFaceIndex = 0),
    )

    val backfilled = dao.backfillLegacyFaceIds()
    assertEquals("nothing backfilled for an underivable payload", 0, backfilled)
    assertNull("row keeps the null faceId (index fallback)", dao.getFaceAssignmentsForPhoto(photoId).single().faceId)
    val person = dao.getFacePerson("person-$photoId-0")
    assertNotNull("v8 person record survives untouched", person)
    assertEquals("Untouched Person", person!!.name)
  }
}
