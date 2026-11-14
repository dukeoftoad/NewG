package com.example.data.repository

import android.content.Context
import com.example.data.local.AlbumEntity
import com.example.data.local.GalleryDao
import com.example.data.local.SampleMediaCatalog
import com.example.data.media.MediaStoreScanner
import com.example.data.model.GalleryMediaItem
import com.example.data.model.TonePreset
import com.example.data.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GalleryRepository(
  private val context: Context,
  private val dao: GalleryDao,
  val vaultCryptoManager: VaultCryptoManager = VaultCryptoManager(context),
  private val mediaStoreScanner: MediaStoreScanner = MediaStoreScanner(context),
) {

  val visibleMedia: Flow<List<GalleryMediaItem>> =
    dao.observeVisibleMedia().map { list -> list.map { it.toDomain() } }

  val vaultMedia: Flow<List<GalleryMediaItem>> =
    dao.observeVaultMedia().map { list -> list.map { it.toDomain() } }

  val trashedMedia: Flow<List<GalleryMediaItem>> =
    dao.observeTrashedMedia().map { list -> list.map { it.toDomain() } }

  val customAlbums: Flow<List<AlbumEntity>> = dao.observeAlbums()

  suspend fun seedInitialDataIfEmpty() =
    withContext(Dispatchers.IO) {
      if (dao.countAllMedia() == 0) {
        dao.insertMedia(SampleMediaCatalog.initialMediaItems())
      }
      // Ensure pre-seeded hidden items also have real AES-GCM encrypted vault files on disk
      val vaultRows = dao.getVaultMediaOnce()
      vaultRows.forEach { entity ->
        if (entity.encryptedVaultFile.isNullOrBlank()) {
          val bytes = vaultCryptoManager.readSourceBytes(entity)
          if (bytes != null) {
            val encPath = vaultCryptoManager.encryptMediaBytes(entity.id, bytes)
            dao.updateVaultState(entity.id, hidden = true, encryptedPath = encPath)
          }
        }
      }
    }

  suspend fun syncDeviceMediaStore(): Int =
    withContext(Dispatchers.IO) {
      val existingIds = dao.getExistingMediaStoreIds().toSet()
      val discovered = mediaStoreScanner.scanDeviceMedia(existingIds)
      if (discovered.isNotEmpty()) {
        dao.insertMedia(discovered)
      }
      discovered.size
    }

  suspend fun toggleFavorite(id: Long, favorite: Boolean) {
    dao.setFavorite(id, favorite)
  }

  suspend fun setFavoriteBatch(ids: List<Long>, favorite: Boolean) {
    if (ids.isEmpty()) return
    dao.setFavoriteBatch(ids, favorite)
  }

  suspend fun hideToVaultEncrypted(ids: List<Long>): Int =
    withContext(Dispatchers.IO) {
      if (ids.isEmpty()) return@withContext 0
      val entities = dao.getMediaByIds(ids)
      var hiddenCount = 0
      entities.forEach { entity ->
        val rawBytes = vaultCryptoManager.readSourceBytes(entity)
        val encPath =
          if (rawBytes != null) {
            vaultCryptoManager.encryptMediaBytes(entity.id, rawBytes)
          } else {
            entity.encryptedVaultFile
          }
        dao.updateVaultState(id = entity.id, hidden = true, encryptedPath = encPath)
        hiddenCount++
      }
      hiddenCount
    }

  suspend fun unhideFromVaultEncrypted(id: Long) =
    withContext(Dispatchers.IO) {
      val entity = dao.getMediaById(id) ?: return@withContext
      vaultCryptoManager.deleteEncryptedFile(entity.encryptedVaultFile)
      dao.updateVaultState(id = id, hidden = false, encryptedPath = null)
    }

  suspend fun deletePermanentlyFromVault(id: Long) =
    withContext(Dispatchers.IO) {
      val entity = dao.getMediaById(id) ?: return@withContext
      vaultCryptoManager.deleteEncryptedFile(entity.encryptedVaultFile)
      dao.deletePermanently(id)
    }

  suspend fun decryptVaultPreviews(items: List<GalleryMediaItem>): Map<Long, ByteArray> =
    withContext(Dispatchers.IO) {
      val map = LinkedHashMap<Long, ByteArray>(items.size)
      items.forEach { item ->
        val encPath = item.encryptedVaultFile
        val decrypted =
          if (!encPath.isNullOrBlank()) {
            vaultCryptoManager.decryptMediaBytes(encPath)
          } else {
            null
          }
        if (decrypted != null) {
          map[item.id] = decrypted
        } else {
          // Lazily encrypt & recover if missing
          val entity = dao.getMediaById(item.id) ?: return@forEach
          val raw = vaultCryptoManager.readSourceBytes(entity) ?: return@forEach
          val newPath = vaultCryptoManager.encryptMediaBytes(entity.id, raw)
          dao.updateVaultState(entity.id, hidden = true, encryptedPath = newPath)
          map[item.id] = raw
        }
      }
      map
    }

  suspend fun setTrashed(id: Long, trashed: Boolean) {
    dao.setTrashed(id, trashed)
  }

  suspend fun setTrashedBatch(ids: List<Long>, trashed: Boolean) {
    if (ids.isEmpty()) return
    dao.setTrashedBatch(ids, trashed)
  }

  suspend fun assignToAlbumBatch(ids: List<Long>, albumId: Long?) {
    if (ids.isEmpty()) return
    dao.assignToAlbumBatch(ids, albumId)
  }

  suspend fun moveToFolderBatch(ids: List<Long>, bucketName: String) {
    val clean = bucketName.trim()
    if (ids.isEmpty() || clean.isEmpty()) return
    dao.moveToFolderBatch(ids, clean)
  }

  suspend fun createAlbumAndAssignBatch(albumName: String, ids: List<Long>): Long {
    val newId = createAlbum(albumName)
    if (ids.isNotEmpty()) {
      dao.assignToAlbumBatch(ids, newId)
    }
    return newId
  }

  suspend fun setTonePreset(id: Long, preset: TonePreset) {
    dao.setTonePreset(id, preset.name)
  }

  suspend fun emptyRecycleBin() {
    dao.emptyRecycleBin()
  }

  suspend fun createAlbum(name: String): Long =
    dao.insertAlbum(
      AlbumEntity(
        name = name.trim(),
        coverDrawableName = "sample_forest_trail",
      )
    )
}
