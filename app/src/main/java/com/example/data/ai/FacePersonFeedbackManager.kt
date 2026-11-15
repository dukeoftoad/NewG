package com.example.data.ai

import com.example.data.local.FacePersonEntity
import com.example.data.local.GalleryDao
import org.json.JSONObject

/**
 * Coordinates Ente-style local person feedback.
 *
 * The DAO owns the @Transaction boundaries so a correction and its undo record
 * are committed together. This class gives the repository one feature-specific
 * entry point for assign/reject/restore/undo and person metadata changes.
 */
class FacePersonFeedbackManager(
  private val dao: GalleryDao,
) {
  suspend fun assignFace(photoId: Long, faceIndex: Int, personClusterId: String) {
    require(photoId > 0L) { "photoId must be positive" }
    require(faceIndex >= 0) { "faceIndex must not be negative" }
    require(personClusterId.isNotBlank()) { "personClusterId must not be blank" }
    dao.assignFaceWithHistory(
      photoId = photoId,
      faceIndex = faceIndex,
      personClusterId = personClusterId,
      faceId = faceId(photoId, faceIndex),
      changedAt = System.currentTimeMillis(),
    )
  }

  suspend fun rejectFace(photoId: Long, faceIndex: Int) {
    require(photoId > 0L && faceIndex >= 0)
    dao.excludeFaceWithHistory(
      photoId = photoId,
      faceIndex = faceIndex,
      changedAt = System.currentTimeMillis(),
      faceId = faceId(photoId, faceIndex),
    )
  }

  suspend fun restoreFace(photoId: Long, faceIndex: Int) {
    require(photoId > 0L && faceIndex >= 0)
    dao.restoreFaceWithHistory(
      photoId = photoId,
      faceIndex = faceIndex,
      changedAt = System.currentTimeMillis(),
      faceId = faceId(photoId, faceIndex),
    )
  }

  suspend fun undoLastCorrection(): Boolean = dao.undoLatestFaceCorrection()

  suspend fun setPersonName(clusterId: String, name: String?) {
    require(clusterId.isNotBlank())
    val existing = dao.getFacePerson(clusterId) ?: FacePersonEntity(clusterId = clusterId)
    dao.upsertFacePerson(
      existing.copy(name = name?.trim()?.takeIf(String::isNotEmpty), updatedAt = System.currentTimeMillis())
    )
  }

  suspend fun setPersonIgnored(clusterId: String, ignored: Boolean) {
    require(clusterId.isNotBlank())
    val existing = dao.getFacePerson(clusterId) ?: FacePersonEntity(clusterId = clusterId)
    dao.upsertFacePerson(existing.copy(isIgnored = ignored, updatedAt = System.currentTimeMillis()))
  }

  suspend fun setPersonCover(clusterId: String, photoId: Long, faceIndex: Int) {
    require(clusterId.isNotBlank() && photoId > 0L && faceIndex >= 0)
    val existing = dao.getFacePerson(clusterId) ?: FacePersonEntity(clusterId = clusterId)
    dao.upsertFacePerson(
      existing.copy(
        coverPhotoId = photoId,
        coverFaceIndex = faceIndex,
        coverFaceId = faceId(photoId, faceIndex),
        updatedAt = System.currentTimeMillis(),
      )
    )
  }


  private suspend fun faceId(photoId: Long, faceIndex: Int): String? =
    runCatching {
      val json = dao.getMediaById(photoId)?.faceDataJson ?: return@runCatching null
      val faces = JSONObject(json).optJSONArray("faces") ?: return@runCatching null
      faces.optJSONObject(faceIndex)?.optString("faceId")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
