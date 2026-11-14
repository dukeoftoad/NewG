package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import com.example.data.model.TonePreset

@Entity(tableName = "media_items")
data class MediaItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val uriString: String? = null,
  val drawableResName: String? = null,
  val mediaStoreId: Long? = null,
  val bucketName: String = "Camera",
  val encryptedVaultFile: String? = null,
  val category: String,
  val mediaType: String,
  val durationLabel: String? = null,
  val dateLabel: String,
  val weekGroup: String,
  val monthGroup: String,
  val timestamp: Long,
  val locationName: String,
  val hasGps: Boolean,
  val mapX: Float,
  val mapY: Float,
  val aspectRatio: Float,
  val isFavorite: Boolean = false,
  val isHidden: Boolean = false,
  val isTrashed: Boolean = false,
  val tagsCsv: String,
  val ocrText: String,
  val exifSummary: String,
  val tonePreset: String = TonePreset.NATURAL.name,
  val albumId: Long? = null,
) {
  fun toDomain(): GalleryMediaItem =
    GalleryMediaItem(
      id = id,
      title = title,
      uriString = uriString,
      drawableResName = drawableResName,
      mediaStoreId = mediaStoreId,
      bucketName = bucketName,
      encryptedVaultFile = encryptedVaultFile,
      category = runCatching { MediaCategory.valueOf(category) }.getOrDefault(MediaCategory.CAMERA),
      mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.PHOTO),
      durationLabel = durationLabel,
      dateLabel = dateLabel,
      weekGroup = weekGroup,
      monthGroup = monthGroup,
      timestamp = timestamp,
      locationName = locationName,
      hasGps = hasGps,
      mapX = mapX,
      mapY = mapY,
      aspectRatio = aspectRatio,
      isFavorite = isFavorite,
      isHidden = isHidden,
      isTrashed = isTrashed,
      tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
      ocrText = ocrText,
      exifSummary = exifSummary,
      tonePreset = runCatching { TonePreset.valueOf(tonePreset) }.getOrDefault(TonePreset.NATURAL),
      albumId = albumId,
    )
}
