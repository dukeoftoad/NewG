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

  @Query("DELETE FROM face_exclusions WHERE photoId = :photoId AND faceId = :faceId")
  suspend fun deleteFaceExclusionByFaceId(photoId: Long, faceId: String)

  @Query("SELECT * FROM face_assignments")
  fun observeFaceAssignments(): Flow<List<FaceAssignmentEntity>>

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId AND faceIndex = :faceIndex LIMIT 1")
  suspend fun getFaceAssignment(photoId: Long, faceIndex: Int): FaceAssignmentEntity?

  @Query("SELECT * FROM face_assignments WHERE photoId = :photoId AND faceId = :faceId LIMIT 1")
  suspend fun getFaceAssignmentByFaceId(photoId: Long, faceId: String): FaceAssignmentEntity?

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
    faceId: String?,
    changedAt: Long,
  ) {
    val previous = faceId?.let { getFaceAssignmentByFaceId(photoId, it) }
      ?: getFaceAssignment(photoId, faceIndex)
    val wasExcluded = if (faceId != null) {
      isFaceExcludedByFaceId(photoId, faceId) || isFaceExcluded(photoId, faceIndex)
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
  suspend fun excludeFaceWithHistory(photoId: Long, faceIndex: Int, faceId: String?, changedAt: Long) {
    val previous = faceId?.let { getFaceAssignmentByFaceId(photoId, it) }
      ?: getFaceAssignment(photoId, faceIndex)
    val wasExcluded = if (faceId != null) {
      isFaceExcludedByFaceId(photoId, faceId) || isFaceExcluded(photoId, faceIndex)
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
  suspend fun restoreFaceWithHistory(photoId: Long, faceIndex: Int, changedAt: Long) {
    val previous = getFaceAssignment(photoId, faceIndex)
    insertFaceCorrectionHistory(
      FaceCorrectionHistoryEntity(
        photoId = photoId,
        faceIndex = faceIndex,
        previousPersonClusterId = previous?.personClusterId,
        nextPersonClusterId = previous?.personClusterId,
        wasExcluded = isFaceExcluded(photoId, faceIndex),
        isExcluded = false,
        createdAt = changedAt,
      )
    )
    deleteFaceExclusion(photoId, faceIndex)
  }

  /** Undo the latest correction as one database transaction. */
  @Transaction
  suspend fun undoLatestFaceCorrection(): Boolean {
    val entry = getLatestUndoableFaceCorrection() ?: return false
    if (entry.previousPersonClusterId == null) {
      deleteFaceAssignment(entry.photoId, entry.faceIndex)
    } else {
      upsertFaceAssignment(
        FaceAssignmentEntity(
          entry.photoId,
          entry.faceIndex,
          entry.previousPersonClusterId,
        )
      )
    }
    if (entry.wasExcluded) {
      upsertFaceExclusion(FaceExclusionEntity(entry.photoId, entry.faceIndex))
    } else {
      deleteFaceExclusion(entry.photoId, entry.faceIndex)
    }
    markFaceCorrectionUndone(entry.id, System.currentTimeMillis())
    return true
  }

  @Query("SELECT * FROM albums ORDER BY createdAt DESC")
  fun observeAlbums(): Flow<List<AlbumEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlbum(album: AlbumEntity): Long
}
