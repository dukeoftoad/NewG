package com.example.data.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.SampleMediaCatalog
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaType
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SharePayload(
  val chooserIntent: Intent,
  val rawShareIntent: Intent,
  val uris: List<Uri>,
  val itemCount: Int,
)

class MediaShareHelper(private val context: Context) {

  suspend fun buildShareIntent(items: List<GalleryMediaItem>): SharePayload? =
    withContext(Dispatchers.IO) {
      if (items.isEmpty()) return@withContext null

      val shareDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
      pruneOldCacheFiles(shareDir)

      val uris = ArrayList<Uri>(items.size)
      val mimeTypes = mutableSetOf<String>()

      items.forEach { item ->
        val resolvedUri = resolveItemUri(item, shareDir)
        if (resolvedUri != null) {
          uris.add(resolvedUri)
          mimeTypes.add(mimeTypeFor(item))
        }
      }

      if (uris.isEmpty()) return@withContext null

      val combinedMime =
        when {
          mimeTypes.size == 1 -> mimeTypes.first()
          mimeTypes.all { it.startsWith("image/") } -> "image/*"
          mimeTypes.all { it.startsWith("video/") } -> "video/*"
          else -> "*/*"
        }

      val shareIntent =
        if (uris.size == 1) {
          Intent(Intent.ACTION_SEND).apply {
            type = combinedMime
            putExtra(Intent.EXTRA_STREAM, uris.first())
            putExtra(Intent.EXTRA_SUBJECT, items.first().title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(items.first().title, uris.first())
          }
        } else {
          Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = combinedMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Shared ${uris.size} items from toadGALLERY")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val clip = ClipData.newRawUri(items.first().title, uris.first())
            for (i in 1 until uris.size) {
              clip.addItem(ClipData.Item(uris[i]))
            }
            clipData = clip
          }
        }

      val title =
        if (uris.size == 1) "Share \"${items.first().title}\""
        else "Share ${uris.size} items"
      val chooser =
        Intent.createChooser(shareIntent, title).apply {
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

      SharePayload(
        chooserIntent = chooser,
        rawShareIntent = shareIntent,
        uris = uris,
        itemCount = uris.size,
      )
    }

  private fun resolveItemUri(item: GalleryMediaItem, shareDir: File): Uri? {
    if (!item.uriString.isNullOrBlank()) {
      return runCatching { Uri.parse(item.uriString) }.getOrNull()
    }
    return exportSampleDrawableToFileProvider(item, shareDir)
  }

  private fun exportSampleDrawableToFileProvider(
    item: GalleryMediaItem,
    shareDir: File,
  ): Uri? =
    runCatching {
      val resId = SampleMediaCatalog.resolveDrawableRes(item.drawableResName)
      val safeSlug =
        item.title
          .lowercase()
          .replace(Regex("[^a-z0-9]+"), "_")
          .trim('_')
          .take(28)
          .ifBlank { "media" }
      val outFile = File(shareDir, "toad_${item.id}_$safeSlug.png")

      val copiedRaw =
        runCatching {
          context.resources.openRawResource(resId).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
          }
          outFile.length() > 0L
        }
          .getOrDefault(false)

      if (!copiedRaw) {
        val bmp = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        FileOutputStream(outFile).use { output ->
          bmp.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
      }

      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }
      .getOrNull()

  private fun mimeTypeFor(item: GalleryMediaItem): String =
    when (item.mediaType) {
      MediaType.VIDEO -> "video/*"
      MediaType.GIF -> "image/gif"
      MediaType.PHOTO,
      MediaType.SCREENSHOT -> "image/png"
    }

  private fun pruneOldCacheFiles(shareDir: File) {
    val cutoff = System.currentTimeMillis() - 3_600_000L
    shareDir.listFiles()?.forEach { file ->
      if (file.lastModified() < cutoff) {
        runCatching { file.delete() }
      }
    }
  }
}
