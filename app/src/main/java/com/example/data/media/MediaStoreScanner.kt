package com.example.data.media

import android.content.ContentUris
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.MediaItemEntity
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreScanner(private val context: Context) {

  fun scanDeviceMedia(existingMediaStoreIds: Set<Long>, limit: Int = 250): List<MediaItemEntity> {
    if (MediaPermissionHelper.checkAccessLevel(context) == MediaAccessLevel.NONE) {
      return emptyList()
    }

    val collected = mutableListOf<MediaItemEntity>()
    val allowOriginals = MediaPermissionHelper.hasMediaLocationPermission(context)

    collected +=
      queryCollection(
        collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        isVideo = false,
        existingIds = existingMediaStoreIds,
        allowOriginals = allowOriginals,
        limit = limit,
      )
    val remaining = (limit - collected.size).coerceAtLeast(0)
    if (remaining > 0) {
      collected +=
        queryCollection(
          collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          isVideo = true,
          existingIds = existingMediaStoreIds,
          allowOriginals = allowOriginals,
          limit = remaining,
        )
    }

    return collected.sortedByDescending { it.timestamp }
  }

  private fun queryCollection(
    collectionUri: Uri,
    isVideo: Boolean,
    existingIds: Set<Long>,
    allowOriginals: Boolean,
    limit: Int,
  ): List<MediaItemEntity> {
    val results = mutableListOf<MediaItemEntity>()
    val projection =
      buildList {
          add(MediaStore.MediaColumns._ID)
          add(MediaStore.MediaColumns.DISPLAY_NAME)
          add(MediaStore.MediaColumns.MIME_TYPE)
          add(MediaStore.MediaColumns.DATE_ADDED)
          add(MediaStore.MediaColumns.DATE_TAKEN)
          add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
          add(MediaStore.MediaColumns.WIDTH)
          add(MediaStore.MediaColumns.HEIGHT)
          if (isVideo) {
            add(MediaStore.Video.VideoColumns.DURATION)
          }
        }
        .toTypedArray()

    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val weekFmt = SimpleDateFormat("'Week' w, yyyy", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    runCatching {
      context.contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val durCol =
          if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

        while (cursor.moveToNext() && results.size < limit) {
          val mediaId = cursor.getLong(idCol)
          if (mediaId in existingIds) continue

          val contentUri = ContentUris.withAppendedId(collectionUri, mediaId)
          val displayName =
            if (nameCol >= 0) cursor.getString(nameCol) ?: "Media $mediaId" else "Media $mediaId"
          val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
          val bucketRaw =
            if (bucketCol >= 0) cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } else null
          val rawTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
          val rawAdded = if (addedCol >= 0) cursor.getLong(addedCol) * 1000L else 0L
          val timestamp =
            when {
              rawTaken > 0L -> rawTaken
              rawAdded > 0L -> rawAdded
              else -> System.currentTimeMillis()
            }

          val w = if (widthCol >= 0) cursor.getInt(widthCol) else 0
          val h = if (heightCol >= 0) cursor.getInt(heightCol) else 0
          val aspectRatio =
            if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.55f, 1.5f)
            else if (isVideo) 1.33f else 0.85f

          val durationMs = if (durCol >= 0) cursor.getLong(durCol) else 0L
          val durationLabel = if (isVideo) formatDuration(durationMs) else null

          val isGif = mimeType == "image/gif"
          val bucketName = bucketRaw ?: defaultBucket(isVideo, displayName)
          val isScreenshot =
            bucketName.contains("screenshot", ignoreCase = true) ||
              displayName.contains("screenshot", ignoreCase = true)

          val category =
            when {
              isVideo -> MediaCategory.VIDEOS
              isGif -> MediaCategory.GIFS
              isScreenshot -> MediaCategory.SCREENSHOTS
              else -> MediaCategory.CAMERA
            }
          val mediaType =
            when {
              isVideo -> MediaType.VIDEO
              isGif -> MediaType.GIF
              isScreenshot -> MediaType.SCREENSHOT
              else -> MediaType.PHOTO
            }

          var exifSummary =
            buildString {
              append(if (isVideo) "Device Video" else "Device Camera")
              if (w > 0 && h > 0) append(" • ${w}×${h}")
            }
          var hasGps = false
          var locationName = bucketName
          var mapX = 0.50f
          var mapY = 0.50f

          if (!isVideo && !isGif) {
            runCatching {
              val exifUri =
                if (allowOriginals && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  MediaStore.setRequireOriginal(contentUri)
                } else {
                  contentUri
                }
              context.contentResolver.openInputStream(exifUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val parts =
                  listOfNotNull(
                    model?.takeIf { it.isNotBlank() },
                    aperture?.takeIf { it.isNotBlank() }?.let { "f/$it" },
                    iso?.takeIf { it.isNotBlank() }?.let { "ISO $it" },
                  )
                if (parts.isNotEmpty()) {
                  exifSummary = parts.joinToString(" • ")
                }
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                  hasGps = true
                  val latDir = if (latLong[0] >= 0f) "N" else "S"
                  val lonDir = if (latLong[1] >= 0f) "E" else "W"
                  locationName =
                    String.format(
                      Locale.US,
                      "%.2f°%s, %.2f°%s",
                      kotlin.math.abs(latLong[0]),
                      latDir,
                      kotlin.math.abs(latLong[1]),
                      lonDir,
                    )
                  mapX = ((latLong[1] + 180f) / 360f).coerceIn(0.14f, 0.86f)
                  mapY = (1f - (latLong[0] + 90f) / 180f).coerceIn(0.14f, 0.86f)
                }
              }
            }
          }

          val date = Date(timestamp)
          results +=
            MediaItemEntity(
              title = displayName.substringBeforeLast('.').ifBlank { displayName },
              uriString = contentUri.toString(),
              mediaStoreId = mediaId,
              bucketName = bucketName,
              category = category.name,
              mediaType = mediaType.name,
              durationLabel = durationLabel,
              dateLabel = dateFmt.format(date),
              weekGroup = weekFmt.format(date),
              monthGroup = monthFmt.format(date),
              timestamp = timestamp,
              locationName = locationName,
              hasGps = hasGps,
              mapX = mapX,
              mapY = mapY,
              aspectRatio = aspectRatio,
              tagsCsv = "device, ${bucketName.lowercase()}, ${category.label.lowercase()}",
              ocrText = if (isScreenshot) displayName else "",
              exifSummary = exifSummary,
            )
        }
      }
    }

    return results
  }

  private fun defaultBucket(isVideo: Boolean, name: String): String =
    when {
      name.contains("screenshot", ignoreCase = true) -> "Screenshots"
      isVideo -> "Movies"
      else -> "Camera"
    }

  private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(1L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
  }
}
