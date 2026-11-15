package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local face-crop cache adapted from Ente's face_thumbnail_cache.dart and
 * face_thumbnail_generator.dart. Crop bytes are cached in memory and in the
 * app cache directory; no face image is uploaded or shared.
 */
object FaceCropper {
  private const val CACHE_VERSION = 2
  private const val JPEG_QUALITY = 90
  private const val MAX_DECODE_DIMENSION = 2048
  private const val MAX_DISK_CACHE_BYTES = 32L * 1024L * 1024L

  private val memoryCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
  }

  suspend fun crop(
    context: Context,
    uri: Uri,
    bounds: FaceCropBounds?,
    sourceRevision: Long = 0L,
  ): ByteArray? = withContext(Dispatchers.IO) {
    val key = cacheKey(uri, bounds, sourceRevision)
    synchronized(memoryCache) { memoryCache.get(key) }?.let { return@withContext it }

    val cacheDir = File(context.cacheDir, "ente-face-crops-v$CACHE_VERSION")
    val cacheFile = File(cacheDir, "$key.jpg")
    if (cacheFile.isFile && cacheFile.length() > 0L) {
      runCatching { cacheFile.readBytes() }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { bytes ->
          synchronized(memoryCache) { memoryCache.put(key, bytes) }
          return@withContext bytes
        }
    } else if (cacheFile.exists()) {
      cacheFile.delete()
    }

    val bytes = runCatching { decodeAndCrop(context, uri, bounds) }.getOrNull()
      ?: return@withContext null
    synchronized(memoryCache) { memoryCache.put(key, bytes) }
    runCatching {
      cacheDir.mkdirs()
      cacheFile.writeBytes(bytes)
      trimDiskCache(cacheDir)
    }
    bytes
  }

  private fun decodeAndCrop(context: Context, uri: Uri, bounds: FaceCropBounds?): ByteArray? {
    val resolver = context.contentResolver
    val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dimensions) }
    if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null

    var sample = 1
    while (maxOf(dimensions.outWidth, dimensions.outHeight) / sample > MAX_DECODE_DIMENSION) {
      sample *= 2
    }
    val decoded = resolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(
        stream,
        null,
        BitmapFactory.Options().apply {
          inSampleSize = sample
          inPreferredConfig = Bitmap.Config.ARGB_8888
        },
      )
    } ?: return null

    val orientation = runCatching {
      resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
      ) } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
    }
    val oriented = if (!matrix.isIdentity) {
      Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
        if (it !== decoded) decoded.recycle()
      }
    } else decoded

    val output = if (bounds == null) {
      oriented
    } else {
      val left = bounds.left.coerceIn(0f, 1f)
      val top = bounds.top.coerceIn(0f, 1f)
      val right = bounds.right.coerceIn(0f, 1f)
      val bottom = bounds.bottom.coerceIn(0f, 1f)
      if (right <= left || bottom <= top) {
        oriented
      } else {
        val padX = (right - left) * 0.45f
        val padY = (bottom - top) * 0.45f
        val x0 = ((left - padX).coerceAtLeast(0f) * oriented.width).toInt().coerceIn(0, oriented.width - 1)
        val y0 = ((top - padY).coerceAtLeast(0f) * oriented.height).toInt().coerceIn(0, oriented.height - 1)
        val x1 = ((right + padX).coerceAtMost(1f) * oriented.width).toInt().coerceIn(x0 + 1, oriented.width)
        val y1 = ((bottom + padY).coerceAtMost(1f) * oriented.height).toInt().coerceIn(y0 + 1, oriented.height)
        Bitmap.createBitmap(oriented, x0, y0, x1 - x0, y1 - y0).also {
          if (it !== oriented) oriented.recycle()
        }
      }
    }

    return try {
      ByteArrayOutputStream().use { stream ->
        if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) null
        else stream.toByteArray()
      }
    } finally {
      output.recycle()
    }
  }

  private fun cacheKey(uri: Uri, bounds: FaceCropBounds?, sourceRevision: Long): String {
    val value = buildString {
      append(uri.toString()).append('|').append(CACHE_VERSION).append('|').append(sourceRevision)
      if (bounds == null) append("|full")
      else append('|').append(bounds.left).append('|').append(bounds.top)
        .append('|').append(bounds.right).append('|').append(bounds.bottom)
    }
    return MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }

  private fun trimDiskCache(directory: File) {
    val files = directory.listFiles()?.filter { it.isFile } ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_DISK_CACHE_BYTES) return
    files.sortedBy { it.lastModified() }.forEach { file ->
      if (total <= MAX_DISK_CACHE_BYTES) return
      val length = file.length()
      if (file.delete()) total -= length
    }
  }
}
