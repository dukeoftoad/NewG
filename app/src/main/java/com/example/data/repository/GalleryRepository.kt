package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.AlbumEntity
import com.example.data.local.GalleryDao
import com.example.data.local.FacePersonEntity
import com.example.data.local.FaceExclusionEntity
import com.example.data.local.MediaItemEntity
import com.example.data.local.SampleMediaCatalog
import com.example.data.media.MediaStoreScanner
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import com.example.data.model.PhotoEditRecipe
import com.example.data.model.TonePreset
import com.example.data.security.VaultCryptoManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

  val faceIndexedMedia: Flow<List<MediaItemEntity>> = dao.observeFaceIndexedMedia()

  val facePeople: Flow<List<FacePersonEntity>> = dao.observeFacePeople()

  val faceExclusions: Flow<List<FaceExclusionEntity>> = dao.observeFaceExclusions()

  suspend fun excludeFace(photoId: Long, faceIndex: Int) {
    dao.upsertFaceExclusion(FaceExclusionEntity(photoId, faceIndex))
  }

  suspend fun restoreFace(photoId: Long, faceIndex: Int) {
    dao.deleteFaceExclusion(photoId, faceIndex)
  }

  suspend fun saveFacePerson(person: FacePersonEntity) {
    val existing = dao.getFacePerson(person.clusterId)
    dao.upsertFacePerson(
      existing?.copy(name = person.name, updatedAt = person.updatedAt) ?: person
    )
  }

  suspend fun removeFacePersonLabel(clusterId: String) = dao.deleteFacePerson(clusterId)

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

  suspend fun unhideBatchFromVaultEncrypted(ids: List<Long>): Int =
    withContext(Dispatchers.IO) {
      if (ids.isEmpty()) return@withContext 0
      val entities = dao.getMediaByIds(ids)
      entities.forEach { entity ->
        vaultCryptoManager.deleteEncryptedFile(entity.encryptedVaultFile)
      }
      dao.unhideBatch(ids)
      entities.size
    }

  suspend fun deletePermanentlyFromVault(id: Long) =
    withContext(Dispatchers.IO) {
      val entity = dao.getMediaById(id) ?: return@withContext
      vaultCryptoManager.deleteEncryptedFile(entity.encryptedVaultFile)
      dao.deletePermanently(id)
    }

  suspend fun deletePermanentlyBatchFromVault(ids: List<Long>): Int =
    withContext(Dispatchers.IO) {
      if (ids.isEmpty()) return@withContext 0
      val entities = dao.getMediaByIds(ids)
      entities.forEach { entity ->
        vaultCryptoManager.deleteEncryptedFile(entity.encryptedVaultFile)
      }
      dao.deletePermanentlyBatch(ids)
      entities.size
    }

  suspend fun importExternalUrisToVault(uris: List<Uri>): Int =
    withContext(Dispatchers.IO) {
      if (uris.isEmpty()) return@withContext 0
      var imported = 0
      val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
      val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
      val weekDayFmt = SimpleDateFormat("MMM d", Locale.US)
      val yearFmt = SimpleDateFormat("yyyy", Locale.US)

      uris.forEach { uri ->
        val bytes =
          runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
        if (bytes == null || bytes.isEmpty()) return@forEach

        val mime = context.contentResolver.getType(uri).orEmpty()
        val displayName = resolveDisplayName(uri) ?: "Vault Import"
        val nowMs = System.currentTimeMillis()
        val nowDate = Date(nowMs)
        val cal = Calendar.getInstance().apply { time = nowDate }
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val weekStart = weekDayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val weekEnd = weekDayFmt.format(cal.time)
        val weekGroup = "$weekStart – $weekEnd, ${yearFmt.format(nowDate)}"

        val bounds =
          BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { opts ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
          }
        val aspectRatio =
          if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            (bounds.outWidth.toFloat() / bounds.outHeight.toFloat()).coerceIn(0.55f, 1.8f)
          } else {
            1.0f
          }

        val mediaType =
          when {
            mime.startsWith("video/") -> MediaType.VIDEO
            mime == "image/gif" -> MediaType.GIF
            else -> MediaType.PHOTO
          }
        val category =
          when (mediaType) {
            MediaType.VIDEO -> MediaCategory.VIDEOS
            MediaType.GIF -> MediaCategory.GIFS
            MediaType.PHOTO,
            MediaType.SCREENSHOT -> MediaCategory.CAMERA
          }

        val entity =
          MediaItemEntity(
            title = displayName.substringBeforeLast('.').ifBlank { "Vault Import" },
            uriString = null,
            drawableResName = "sample_lake_sunset",
            bucketName = "Vault Import",
            category = category.name,
            mediaType = mediaType.name,
            dateLabel = dayFmt.format(nowDate),
            weekGroup = weekGroup,
            monthGroup = monthFmt.format(nowDate),
            timestamp = nowMs,
            locationName = "Hidden Vault",
            hasGps = false,
            mapX = 0f,
            mapY = 0f,
            aspectRatio = aspectRatio,
            isHidden = true,
            tagsCsv = "vault, imported, private",
            ocrText = "",
            exifSummary = "Imported to AES-256-GCM Vault",
          )

        val newId = dao.insertSingleMedia(entity)
        val encPath = vaultCryptoManager.encryptMediaBytes(newId, bytes)
        dao.updateVaultState(id = newId, hidden = true, encryptedPath = encPath)
        imported++
      }
      imported
    }

  private fun resolveDisplayName(uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
          ?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (idx >= 0) cursor.getString(idx) else null
            } else null
          }
      }
      .getOrNull()

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

  suspend fun saveEditedCopy(
    sourceItem: GalleryMediaItem,
    renderedBitmap: Bitmap,
    recipe: PhotoEditRecipe,
  ): Long =
    withContext(Dispatchers.IO) {
      val outDir = File(context.filesDir, "edited_media").apply { mkdirs() }
      val fileName = "toad_edit_${sourceItem.id}_${System.currentTimeMillis()}.jpg"
      val outFile = File(outDir, fileName)
      FileOutputStream(outFile).use { fos ->
        renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)
      }

      // Best-effort export to scoped Pictures/toadGALLERY on Android 10+ (zero broad storage permissions)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
          val values =
            ContentValues().apply {
              put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
              put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
              put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/toadGALLERY",
              )
              put(MediaStore.Images.Media.IS_PENDING, 1)
            }
          val uri =
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
          if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
              renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
          }
        }
      }

      val nowMs = System.currentTimeMillis()
      val nowDate = Date(nowMs)
      val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
      val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
      val weekDayFmt = SimpleDateFormat("MMM d", Locale.US)
      val yearFmt = SimpleDateFormat("yyyy", Locale.US)
      val cal = Calendar.getInstance().apply { time = nowDate }
      cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
      val weekStart = weekDayFmt.format(cal.time)
      cal.add(Calendar.DAY_OF_WEEK, 6)
      val weekEnd = weekDayFmt.format(cal.time)

      val aspect =
        (renderedBitmap.width.toFloat() / renderedBitmap.height.coerceAtLeast(1).toFloat())
          .coerceIn(0.45f, 2.2f)
      val cleanBaseTitle = sourceItem.title.removeSuffix(" (Edited)").take(40)
      val entity =
        MediaItemEntity(
          title = "$cleanBaseTitle (Edited)",
          uriString = Uri.fromFile(outFile).toString(),
          drawableResName = sourceItem.drawableResName,
          bucketName = sourceItem.bucketName,
          category = MediaCategory.CAMERA.name,
          mediaType = MediaType.PHOTO.name,
          dateLabel = dayFmt.format(nowDate),
          weekGroup = "$weekStart – $weekEnd, ${yearFmt.format(nowDate)}",
          monthGroup = monthFmt.format(nowDate),
          timestamp = nowMs,
          locationName = sourceItem.locationName,
          hasGps = sourceItem.hasGps,
          mapX = sourceItem.mapX,
          mapY = sourceItem.mapY,
          aspectRatio = aspect,
          isFavorite = false,
          tagsCsv = (sourceItem.tags + "edited").distinct().joinToString(", "),
          ocrText = sourceItem.ocrText,
          exifSummary = "${sourceItem.exifSummary} • Edited Copy",
          tonePreset = TonePreset.NATURAL.name,
          albumId = sourceItem.albumId,
          editRecipeJson = recipe.toJsonString(),
        )
      dao.insertSingleMedia(entity)
    }

  suspend fun applyNonDestructiveEdit(
    sourceItem: GalleryMediaItem,
    renderedBitmap: Bitmap,
    recipe: PhotoEditRecipe,
  ) =
    withContext(Dispatchers.IO) {
      val outDir = File(context.filesDir, "edited_media").apply { mkdirs() }
      val outFile = File(outDir, "nd_${sourceItem.id}_${System.currentTimeMillis()}.jpg")
      FileOutputStream(outFile).use { fos ->
        renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
      }
      deleteOwnedEditFile(sourceItem.editedUriString)
      dao.updateNonDestructiveEdit(
        id = sourceItem.id,
        editedUri = Uri.fromFile(outFile).toString(),
        recipeJson = recipe.toJsonString(),
      )
    }

  suspend fun revertNonDestructiveEdit(sourceItem: GalleryMediaItem) =
    withContext(Dispatchers.IO) {
      deleteOwnedEditFile(sourceItem.editedUriString)
      dao.updateNonDestructiveEdit(id = sourceItem.id, editedUri = null, recipeJson = null)
    }

  private fun deleteOwnedEditFile(uriString: String?) {
    if (uriString.isNullOrBlank()) return
    runCatching {
      val path = Uri.parse(uriString).path ?: return@runCatching
      val ownedDir = File(context.filesDir, "edited_media").canonicalPath
      val target = File(path)
      if (target.canonicalPath.startsWith(ownedDir)) {
        target.delete()
      }
    }
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
