package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Dao
interface GalleryDao {
  @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isTrashed = 0 ORDER BY timestamp DESC")
  fun observeVisibleMedia(): Flow<List<MediaItemEntity>>

  @Query("SELECT * FROM media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY timestamp DESC")
  fun observeVaultMedia(): Flow<List<MediaItemEntity>>

  @Query("SELECT * FROM media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY timestamp DESC")
  suspend fun getVaultMediaOnce(): List<MediaItemEntity>

  @Query("SELECT * FROM media_items WHERE isTrashed = 1 ORDER BY timestamp DESC")
  fun observeTrashedMedia(): Flow<List<MediaItemEntity>>

  @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
  suspend fun getMediaById(id: Long): MediaItemEntity?

  @Query("SELECT * FROM media_items WHERE id IN (:ids)")
  suspend fun getMediaByIds(ids: List<Long>): List<MediaItemEntity>

  @Query("SELECT mediaStoreId FROM media_items WHERE mediaStoreId IS NOT NULL")
  suspend fun getExistingMediaStoreIds(): List<Long>

  @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isTrashed = 0 AND mediaType IN ('PHOTO', 'SCREENSHOT') AND uriString IS NOT NULL AND (:force = 1 OR faceIndexedAt IS NULL) ORDER BY timestamp ASC")
  suspend fun getPhotosForFaceIndex(force: Boolean): List<MediaItemEntity>

  @Query("UPDATE media_items SET faceDataJson = :faceDataJson, faceIndexedAt = :indexedAt WHERE id = :id")
  suspend fun updateFaceIndex(id: Long, faceDataJson: String, indexedAt: Long)

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId")
  suspend fun getFaceAssignmentsForPhoto(photoId: Long): List<FaceAssignmentEntity>

  @Query("SELECT * FROM face_assignments WHERE personClusterId = :personClusterId")
  suspend fun getFaceAssignmentsForPersonCluster(personClusterId: String): List<FaceAssignmentEntity>

  @Query("DELETE FROM face_assignments WHERE photoId = :photoId")
  suspend fun deleteFaceAssignmentsForPhoto(photoId: Long)

  @Query("SELECT * FROM face_exclusions WHERE photoId = :photoId")
  suspend fun getFaceExclusionsForPhoto(photoId: Long): List<FaceExclusionEntity>

  @Query("DELETE FROM face_exclusions WHERE photoId = :photoId")
  suspend fun deleteFaceExclusionsForPhoto(photoId: Long)

  @Query("SELECT * FROM face_correction_history WHERE photoId = :photoId")
  suspend fun getFaceCorrectionHistoryForPhoto(photoId: Long): List<FaceCorrectionHistoryEntity>

  @Query("SELECT * FROM face_correction_history WHERE previousPersonClusterId = :personClusterId OR nextPersonClusterId = :personClusterId")
  suspend fun getFaceCorrectionHistoryForPersonCluster(personClusterId: String): List<FaceCorrectionHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFaceCorrectionHistory(entry: FaceCorrectionHistoryEntity)

  @Query("SELECT * FROM face_people WHERE coverPhotoId = :photoId AND coverFaceIndex IS NOT NULL")
  suspend fun getPeopleWithCoverForPhoto(photoId: Long): List<FacePersonEntity>

  @Transaction
  suspend fun updateFaceIndexAndReconcile(id: Long, faceDataJson: String, indexedAt: Long) {
    // Backfill legacy index-only feedback against the OLD face payload before replacing it.
    // The stable ID is derived with the same model/photo/box/embedding signature as the indexer.
    val previousJson = getMediaById(id)?.faceDataJson
    val previousRoot = runCatching { previousJson?.let(::JSONObject) }.getOrNull()
    val previousFaces = previousRoot?.optJSONArray("faces")
    if (previousRoot != null && previousFaces != null) {
      fun legacyId(index: Int): String? = previousFaces.optJSONObject(index)?.let { face ->
        stableFaceIdFromLegacyPayload(id, previousRoot, face)
      }
      getFaceAssignmentsForPhoto(id).filter { it.faceId == null }.forEach { row ->
        legacyId(row.faceIndex)?.let { upsertFaceAssignment(row.copy(faceId = it)) }
      }
      getFaceExclusionsForPhoto(id).filter { it.faceId == null }.forEach { row ->
        legacyId(row.faceIndex)?.let { upsertFaceExclusion(row.copy(faceId = it)) }
      }
      getFaceCorrectionHistoryForPhoto(id).filter { it.faceId == null }.forEach { row ->
        legacyId(row.faceIndex)?.let { upsertFaceCorrectionHistory(row.copy(faceId = it)) }
      }
      getPeopleWithCoverForPhoto(id).filter { it.coverFaceId == null }.forEach { person ->
        val coverIndex = person.coverFaceIndex ?: return@forEach
        legacyId(coverIndex)?.let { upsertFacePerson(person.copy(coverFaceId = it)) }
      }
      // Automatic v8 person IDs ended in the detector index. Re-key saved names,
      // covers, assignments and history to the stable face-based group ID.
      for (oldIndex in 0 until previousFaces.length()) {
        val stableId = legacyId(oldIndex) ?: continue
        val oldClusterId = "person-$id-$oldIndex"
        val newClusterId = "person-$id-$stableId"
        if (oldClusterId == newClusterId) continue
        val oldPerson = getFacePerson(oldClusterId)
        if (oldPerson != null) {
          val existingPerson = getFacePerson(newClusterId)
          val oldRecordIsNewer = existingPerson == null || oldPerson.updatedAt > existingPerson.updatedAt
          val preferred = if (oldRecordIsNewer) oldPerson.copy(clusterId = newClusterId) else existingPerson!!
          val fallback = if (oldRecordIsNewer) existingPerson else oldPerson
          val migratedPerson = preferred.copy(
            name = preferred.name ?: fallback?.name,
            coverPhotoId = preferred.coverPhotoId ?: fallback?.coverPhotoId,
            coverFaceIndex = preferred.coverFaceIndex ?: fallback?.coverFaceIndex,
            coverFaceId = preferred.coverFaceId ?: fallback?.coverFaceId,
            updatedAt = maxOf(preferred.updatedAt, fallback?.updatedAt ?: preferred.updatedAt),
          )
          deleteFacePerson(oldClusterId)
          upsertFacePerson(migratedPerson)
        }
        // Assignments/history can reference an unnamed cluster with no face_people row.
        getFaceAssignmentsForPersonCluster(oldClusterId).forEach { assignment ->
          upsertFaceAssignment(assignment.copy(personClusterId = newClusterId))
        }
        getFaceCorrectionHistoryForPersonCluster(oldClusterId).forEach { entry ->
          upsertFaceCorrectionHistory(
            entry.copy(
              previousPersonClusterId = entry.previousPersonClusterId?.let {
                if (it == oldClusterId) newClusterId else it
              },
              nextPersonClusterId = entry.nextPersonClusterId?.let {
                if (it == oldClusterId) newClusterId else it
              },
            )
          )
        }
      }
    }
    updateFaceIndex(id, faceDataJson, indexedAt)
    val faces = runCatching { JSONObject(faceDataJson).optJSONArray("faces") }.getOrNull()
      ?: return
    // An empty or legacy face payload cannot safely reconcile stable identities.
    if (faces.length() == 0 || (0 until faces.length()).any { index ->
        faces.optJSONObject(index)?.optString("faceId").isNullOrBlank()
      }) return
    // A null faceId means the old payload did not contain enough information to
    // identify this face. Keep that feedback dormant at a negative sentinel index
    // instead of reusing a detector position that may now belong to somebody else.
    fun unresolvedIndex(oldIndex: Int): Int = if (oldIndex >= 0) -oldIndex - 1 else oldIndex
    fun resolve(faceId: String?): Int? {
      val stableId = faceId?.takeIf { it.isNotBlank() } ?: return null
      return (0 until faces.length()).firstOrNull { index ->
        faces.optJSONObject(index)?.optString("faceId") == stableId
      }
    }
    val assignments = getFaceAssignmentsForPhoto(id)
      .map { row -> row.copy(faceIndex = resolve(row.faceId) ?: unresolvedIndex(row.faceIndex)) }
      .sortedByDescending { it.faceId != null }
      .distinctBy { it.faceIndex }
    val exclusions = getFaceExclusionsForPhoto(id)
      .map { row -> row.copy(faceIndex = resolve(row.faceId) ?: unresolvedIndex(row.faceIndex)) }
      .sortedByDescending { it.faceId != null }
      .distinctBy { it.faceIndex }
    val corrections = getFaceCorrectionHistoryForPhoto(id)
      .map { row -> row.copy(faceIndex = resolve(row.faceId) ?: unresolvedIndex(row.faceIndex)) }
    val people = getPeopleWithCoverForPhoto(id).map { person ->
      val oldIndex = person.coverFaceIndex ?: return@map person
      person.copy(coverFaceIndex = resolve(person.coverFaceId) ?: unresolvedIndex(oldIndex))
    }
    deleteFaceAssignmentsForPhoto(id)
    deleteFaceExclusionsForPhoto(id)
    assignments.forEach { upsertFaceAssignment(it) }
    exclusions.forEach { upsertFaceExclusion(it) }
    corrections.forEach { upsertFaceCorrectionHistory(it) }
    people.forEach { upsertFacePerson(it) }
  }

  @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isTrashed = 0 AND faceDataJson IS NOT NULL ORDER BY timestamp ASC")
  fun observeFaceIndexedMedia(): Flow<List<MediaItemEntity>>

  @Query("UPDATE media_items SET faceDataJson = NULL, faceIndexedAt = NULL")
  suspend fun clearFaceIndex()

  @Query("SELECT COUNT(*) FROM media_items")
  suspend fun countAllMedia(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMedia(items: List<MediaItemEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSingleMedia(item: MediaItemEntity): Long

  @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("UPDATE media_items SET isFavorite = :favorite WHERE id IN (:ids)")
  suspend fun setFavoriteBatch(ids: List<Long>, favorite: Boolean)

  @Query("UPDATE media_items SET isHidden = :hidden, encryptedVaultFile = :encryptedPath WHERE id = :id")
  suspend fun updateVaultState(id: Long, hidden: Boolean, encryptedPath: String?)

  @Query("UPDATE media_items SET isHidden = 0, encryptedVaultFile = NULL WHERE id IN (:ids)")
  suspend fun unhideBatch(ids: List<Long>)

  @Query("UPDATE media_items SET isTrashed = :trashed WHERE id = :id")
  suspend fun setTrashed(id: Long, trashed: Boolean)

  @Query("UPDATE media_items SET isTrashed = :trashed WHERE id IN (:ids)")
  suspend fun setTrashedBatch(ids: List<Long>, trashed: Boolean)

  @Query("UPDATE media_items SET tonePreset = :preset WHERE id = :id")
  suspend fun setTonePreset(id: Long, preset: String)

  @Query("UPDATE media_items SET editedUriString = :editedUri, editRecipeJson = :recipeJson WHERE id = :id")
  suspend fun updateNonDestructiveEdit(id: Long, editedUri: String?, recipeJson: String?)

  @Query("UPDATE media_items SET albumId = :albumId WHERE id IN (:ids)")
  suspend fun assignToAlbumBatch(ids: List<Long>, albumId: Long?)

  @Query("UPDATE media_items SET bucketName = :bucketName WHERE id IN (:ids)")
  suspend fun moveToFolderBatch(ids: List<Long>, bucketName: String)

  @Query("DELETE FROM media_items WHERE isTrashed = 1")
  suspend fun emptyRecycleBin()

  @Query("DELETE FROM media_items WHERE id = :id")
  suspend fun deletePermanently(id: Long)

  @Query("DELETE FROM media_items WHERE id IN (:ids)")
  suspend fun deletePermanentlyBatch(ids: List<Long>)

  @Query("DELETE FROM face_exclusions")
  suspend fun clearFaceExclusions()

  @Query("DELETE FROM face_assignments")
  suspend fun clearFaceAssignments()

  @Query("DELETE FROM face_correction_history")
  suspend fun clearFaceCorrectionHistory()

  @Query("DELETE FROM face_people")
  suspend fun clearFacePeople()

  /** Clear all face-derived data and user corrections atomically with the index reset. */
  @Transaction
  suspend fun clearFaceIndexAndFeedback() {
    clearFaceIndex()
    clearFaceExclusions()
    clearFaceAssignments()
    clearFaceCorrectionHistory()
    clearFacePeople()
  }

  @Query("SELECT * FROM face_exclusions")
  fun observeFaceExclusions(): Flow<List<FaceExclusionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFaceExclusion(exclusion: FaceExclusionEntity)

  @Query("DELETE FROM face_exclusions WHERE photoId = :photoId AND faceIndex = :faceIndex")
  suspend fun deleteFaceExclusion(photoId: Long, faceIndex: Int)

  @Query("SELECT EXISTS(SELECT 1 FROM face_exclusions WHERE photoId = :photoId AND faceId = :faceId)")
  suspend fun isFaceExcludedByFaceId(photoId: Long, faceId: String): Boolean

  @Query("SELECT EXISTS(SELECT 1 FROM face_exclusions WHERE photoId = :photoId AND faceIndex = :faceIndex AND faceId IS NULL)")
  suspend fun isLegacyFaceExcluded(photoId: Long, faceIndex: Int): Boolean

  @Query("DELETE FROM face_exclusions WHERE photoId = :photoId AND faceId = :faceId")
  suspend fun deleteFaceExclusionByFaceId(photoId: Long, faceId: String)

  @Query("SELECT * FROM face_assignments")
  fun observeFaceAssignments(): Flow<List<FaceAssignmentEntity>>

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId AND faceIndex = :faceIndex LIMIT 1")
  suspend fun getFaceAssignment(photoId: Long, faceIndex: Int): FaceAssignmentEntity?

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId AND faceId = :faceId LIMIT 1")
  suspend fun getFaceAssignmentByFaceId(photoId: Long, faceId: String): FaceAssignmentEntity?

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId AND faceIndex = :faceIndex AND faceId IS NULL LIMIT 1")
  suspend fun getLegacyFaceAssignment(photoId: Long, faceIndex: Int): FaceAssignmentEntity?

  @Query("DELETE FROM face_assignments WHERE photoId = :photoId AND faceId = :faceId")
  suspend fun deleteFaceAssignmentByFaceId(photoId: Long, faceId: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFaceAssignment(assignment: FaceAssignmentEntity)

  @Query("DELETE FROM face_assignments WHERE photoId = :photoId AND faceIndex = :faceIndex")
  suspend fun deleteFaceAssignment(photoId: Long, faceIndex: Int)

  @Insert
  suspend fun insertFaceCorrectionHistory(entry: FaceCorrectionHistoryEntity): Long

  @Query("SELECT * FROM face_correction_history ORDER BY createdAt DESC LIMIT :limit")
  fun observeFaceCorrectionHistory(limit: Int = 50): Flow<List<FaceCorrectionHistoryEntity>>

  @Query("SELECT * FROM face_correction_history WHERE undoneAt IS NULL ORDER BY id DESC LIMIT 1")
  suspend fun getLatestUndoableFaceCorrection(): FaceCorrectionHistoryEntity?

  @Query("UPDATE face_correction_history SET undoneAt = :undoneAt WHERE id = :id")
  suspend fun markFaceCorrectionUndone(id: Long, undoneAt: Long)

  @Query("SELECT * FROM face_people ORDER BY name COLLATE NOCASE ASC")
  fun observeFacePeople(): Flow<List<FacePersonEntity>>

  @Query("SELECT * FROM face_people WHERE clusterId = :clusterId LIMIT 1")
  suspend fun getFacePerson(clusterId: String): FacePersonEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFacePerson(person: FacePersonEntity)

  @Query("DELETE FROM face_people WHERE clusterId = :clusterId")
  suspend fun deleteFacePerson(clusterId: String)


  @Query("SELECT EXISTS(SELECT 1 FROM face_exclusions WHERE photoId = :photoId AND faceIndex = :faceIndex)")
  suspend fun isFaceExcluded(photoId: Long, faceIndex: Int): Boolean

  /** Atomically persist Ente-style face assignment feedback and its undo record. */
  @Transaction
  suspend fun assignFaceWithHistory(
    photoId: Long,
    faceIndex: Int,
    personClusterId: String,
    changedAt: Long,
    faceId: String? = null,
  ) {
    val previous = if (faceId != null) {
      getFaceAssignmentByFaceId(photoId, faceId) ?: getLegacyFaceAssignment(photoId, faceIndex)
    } else getFaceAssignment(photoId, faceIndex)
    val wasExcluded = if (faceId != null) {
      isFaceExcludedByFaceId(photoId, faceId) || isLegacyFaceExcluded(photoId, faceIndex)
    } else isFaceExcluded(photoId, faceIndex)
    insertFaceCorrectionHistory(
      FaceCorrectionHistoryEntity(
        photoId = photoId,
        faceIndex = faceIndex,
        faceId = faceId,
        previousPersonClusterId = previous?.personClusterId,
        nextPersonClusterId = personClusterId,
        wasExcluded = wasExcluded,
        isExcluded = false,
        createdAt = changedAt,
      )
    )
    if (faceId != null) {
      deleteFaceAssignmentByFaceId(photoId, faceId)
      deleteFaceExclusionByFaceId(photoId, faceId)
    }
    deleteFaceExclusion(photoId, faceIndex)
    upsertFaceAssignment(
      FaceAssignmentEntity(
        photoId = photoId,
        faceIndex = faceIndex,
        personClusterId = personClusterId,
        faceId = faceId,
        updatedAt = changedAt,
      )
    )
  }

  /** Atomically reject a face from a person without deleting the source photo. */
  @Transaction
  suspend fun excludeFaceWithHistory(photoId: Long, faceIndex: Int, changedAt: Long, faceId: String? = null) {
    val previous = if (faceId != null) {
      getFaceAssignmentByFaceId(photoId, faceId) ?: getLegacyFaceAssignment(photoId, faceIndex)
    } else getFaceAssignment(photoId, faceIndex)
    val wasExcluded = if (faceId != null) {
      isFaceExcludedByFaceId(photoId, faceId) || isLegacyFaceExcluded(photoId, faceIndex)
    } else isFaceExcluded(photoId, faceIndex)
    insertFaceCorrectionHistory(
      FaceCorrectionHistoryEntity(
        photoId = photoId,
        faceIndex = faceIndex,
        faceId = faceId,
        previousPersonClusterId = previous?.personClusterId,
        nextPersonClusterId = null,
        wasExcluded = wasExcluded,
        isExcluded = true,
        createdAt = changedAt,
      )
    )
    if (faceId != null) {
      deleteFaceAssignmentByFaceId(photoId, faceId)
      deleteFaceExclusionByFaceId(photoId, faceId)
    }
    deleteFaceAssignment(photoId, faceIndex)
    upsertFaceExclusion(FaceExclusionEntity(photoId, faceIndex, faceId, changedAt))
  }

  /** Restore an excluded face to automatic clustering and keep the action undoable. */
  @Transaction
  suspend fun restoreFaceWithHistory(photoId: Long, faceIndex: Int, changedAt: Long, faceId: String? = null) {
    val previous = if (faceId != null) {
      getFaceAssignmentByFaceId(photoId, faceId) ?: getLegacyFaceAssignment(photoId, faceIndex)
    } else getFaceAssignment(photoId, faceIndex)
    insertFaceCorrectionHistory(
      FaceCorrectionHistoryEntity(
        photoId = photoId,
        faceIndex = faceIndex,
        faceId = faceId,
        previousPersonClusterId = previous?.personClusterId,
        nextPersonClusterId = previous?.personClusterId,
        wasExcluded = if (faceId != null) isFaceExcludedByFaceId(photoId, faceId) || isLegacyFaceExcluded(photoId, faceIndex) else isFaceExcluded(photoId, faceIndex),
        isExcluded = false,
        createdAt = changedAt,
      )
    )
    if (faceId != null) deleteFaceExclusionByFaceId(photoId, faceId)
    deleteFaceExclusion(photoId, faceIndex)
  }

  /** Undo the latest correction as one database transaction. */
  @Transaction
  suspend fun undoLatestFaceCorrection(): Boolean {
    val entry = getLatestUndoableFaceCorrection() ?: return false
    val currentFaceIndex = if (entry.faceId == null) {
      entry.faceIndex
    } else {
      val json = getMediaById(entry.photoId)?.faceDataJson
      val faces = runCatching { json?.let { JSONObject(it).optJSONArray("faces") } }.getOrNull()
        ?: return false
      (0 until faces.length()).firstOrNull { index ->
        faces.optJSONObject(index)?.optString("faceId") == entry.faceId
      } ?: return false
    }
    // Unresolved legacy rows are retained for manual recovery, never replayed
    // against a potentially unrelated detector index.
    if (currentFaceIndex < 0) return false
    if (entry.faceId != null) {
      deleteFaceAssignmentByFaceId(entry.photoId, entry.faceId)
      deleteFaceExclusionByFaceId(entry.photoId, entry.faceId)
    }
    if (entry.previousPersonClusterId == null) {
      deleteFaceAssignment(entry.photoId, currentFaceIndex)
    } else {
      upsertFaceAssignment(
        FaceAssignmentEntity(
          photoId = entry.photoId,
          faceIndex = currentFaceIndex,
          personClusterId = entry.previousPersonClusterId,
          faceId = entry.faceId,
        )
      )
    }
    if (entry.wasExcluded) {
      upsertFaceExclusion(FaceExclusionEntity(entry.photoId, currentFaceIndex, entry.faceId))
    } else {
      deleteFaceExclusion(entry.photoId, currentFaceIndex)
    }
    markFaceCorrectionUndone(entry.id, System.currentTimeMillis())
    return true
  }

  @Query("SELECT * FROM albums ORDER BY createdAt DESC")
  fun observeAlbums(): Flow<List<AlbumEntity>>

  private fun stableFaceIdFromLegacyPayload(
    photoId: Long,
    root: JSONObject,
    face: JSONObject,
  ): String? {
    val box = face.optJSONArray("box") ?: return null
    val embedding = face.optJSONArray("embedding") ?: return null
    if (box.length() < 4 || embedding.length() != 192) return null
    val coordinates = (0 until 4).map { box.optDouble(it, Double.NaN).toFloat() }
    val vector = (0 until embedding.length()).map { embedding.optDouble(it, Double.NaN).toFloat() }
    if (coordinates.any { !it.isFinite() } || vector.any { !it.isFinite() }) return null
    val modelId = root.optString("model", "ente-face-recognition").ifBlank { "ente-face-recognition" }
    val signature = buildString {
      append(modelId).append('|').append(photoId)
      coordinates.forEach { append('|').append(kotlin.math.round(it * 100f).toInt()) }
      vector.forEach { append('|').append(kotlin.math.round(it * 1000f).toInt()) }
    }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
      .digest(signature.toByteArray(Charsets.UTF_8))
    return "face-" + digest.take(16).joinToString("") { "%02x".format(it) }
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlbum(album: AlbumEntity): Long
}
