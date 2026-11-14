package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

  @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isTrashed = 0 AND mediaType = 'PHOTO' AND uriString IS NOT NULL AND (:force = 1 OR faceIndexedAt IS NULL) ORDER BY timestamp ASC")
  suspend fun getPhotosForFaceIndex(force: Boolean): List<MediaItemEntity>

  @Query("UPDATE media_items SET faceDataJson = :faceDataJson, faceIndexedAt = :indexedAt WHERE id = :id")
  suspend fun updateFaceIndex(id: Long, faceDataJson: String, indexedAt: Long)

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

  @Query("SELECT * FROM albums ORDER BY createdAt DESC")
  fun observeAlbums(): Flow<List<AlbumEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlbum(album: AlbumEntity): Long
}
