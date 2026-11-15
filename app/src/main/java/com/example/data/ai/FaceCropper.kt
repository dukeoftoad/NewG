package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Local face-crop cache adapted from Ente's face_thumbnail_cache.dart and
 * face_thumbnail_generator.dart. Crop bytes and decoded bitmaps are cached in
 * memory and in the app cache directory; no face image is uploaded or shared.
 */
object FaceCropper {
  private const val CACHE_VERSION = 2
  private const val JPEG_QUALITY = 88
  private const val MAX_DECODE_DIMENSION = 1600
  private const val MAX_CROP_OUTPUT_PX = 320
  private const val MAX_DISK_CACHE_BYTES = 32L * 1024L * 1024L
  private const val DISK_TRIM_INTERVAL = 24

  private val decodeSemaphore = Semaphore(4)
  private val writeCounter = AtomicInteger(0)

  private val memoryCache =
    object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
      override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

  private val bitmapCache =
    object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

  /** Fast synchronous lookup for Compose to avoid initial blank frame when scrolling cached faces. */
  fun peekCachedBitmap(
    uri: Uri?,
    bounds: FaceCropBounds?,
    sourceRevision: Long = 0L,
  ): Bitmap? {
    if (uri == null) return null
    val key = cacheKey(uri, bounds, sourceRevision)
    val cached = synchronized(bitmapCache) { bitmapCache.get(key) }
    return if (cached != null && !cached.isRecycled) cached else null
  }

  suspend fun cropBitmap(
    context: Context,
    uri: Uri,
    bounds: FaceCropBounds?,
    sourceRevision: Long = 0L,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = cacheKey(uri, bounds, sourceRevision)
      synchronized(bitmapCache) {
        val existing = bitmapCache.get(key)
        if (existing != null && !existing.isRecycled) return@withContext existing
      }

      val bytes = crop(context, uri, bounds, sourceRevision) ?: return@withContext null
      val decoded =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
          ?: return@withContext null
      synchronized(bitmapCache) { bitmapCache.put(key, decoded) }
      decoded
    }

  suspend fun crop(
    context: Context,
    uri: Uri,
    bounds: FaceCropBounds?,
    sourceRevision: Long = 0L,
  ): ByteArray? =
    withContext(Dispatchers.IO) {
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

      // Bound concurrent heavy image decodes so scrolling a large People list stays smooth
      val bytes =
        decodeSemaphore.withPermit {
          synchronized(memoryCache) { memoryCache.get(key) }?.let { return@withPermit it }
          runCatching { decodeAndCrop(context, uri, bounds) }.getOrNull()
        } ?: return@withContext null

      synchronized(memoryCache) { memoryCache.put(key, bytes) }
      runCatching {
        cacheDir.mkdirs()
        cacheFile.writeBytes(bytes)
        if (writeCounter.incrementAndGet() % DISK_TRIM_INTERVAL == 0) {
          trimDiskCache(cacheDir)
        }
      }
      bytes
    }

  fun clearCache(context: Context): Float {
    synchronized(memoryCache) { memoryCache.evictAll() }
    synchronized(bitmapCache) { bitmapCache.evictAll() }
    val cacheDir = File(context.cacheDir, "ente-face-crops-v$CACHE_VERSION")
    val files = cacheDir.listFiles()?.filter { it.isFile }.orEmpty()
    val bytes = files.sumOf { it.length() }
    files.forEach { runCatching { it.delete() } }
    return bytes / (1024f * 1024f)
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
    val decoded =
      resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(
          stream,
          null,
          BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
          },
        )
      } ?: return null

    val orientation =
      runCatching {
          resolver.openInputStream(uri)?.use {
            ExifInterface(it)
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          } ?: ExifInterface.ORIENTATION_NORMAL
        }
        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.setRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.setRotate(270f)
        matrix.postScale(-1f, -1f)
      }
    }
    val oriented =
      if (!matrix.isIdentity) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
          if (it !== decoded) decoded.recycle()
        }
      } else decoded

    val cropped =
      if (bounds == null) {
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
          val x0 =
            ((left - padX).coerceAtLeast(0f) * oriented.width)
              .toInt()
              .coerceIn(0, oriented.width - 1)
          val y0 =
            ((top - padY).coerceAtLeast(0f) * oriented.height)
              .toInt()
              .coerceIn(0, oriented.height - 1)
          val x1 =
            ((right + padX).coerceAtMost(1f) * oriented.width)
              .toInt()
              .coerceIn(x0 + 1, oriented.width)
          val y1 =
            ((bottom + padY).coerceAtMost(1f) * oriented.height)
              .toInt()
              .coerceIn(y0 + 1, oriented.height)
          Bitmap.createBitmap(oriented, x0, y0, x1 - x0, y1 - y0).also {
            if (it !== oriented) oriented.recycle()
          }
        }
      }

    // Downscale oversized face crops to thumbnail dimensions so memory/disk cache stays compact
    val maxEdge = maxOf(cropped.width, cropped.height)
    val output =
      if (maxEdge > MAX_CROP_OUTPUT_PX) {
        val scale = MAX_CROP_OUTPUT_PX.toFloat() / maxEdge.toFloat()
        val targetW = (cropped.width * scale).toInt().coerceAtLeast(1)
        val targetH = (cropped.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(cropped, targetW, targetH, true).also {
          if (it !== cropped) cropped.recycle()
        }
      } else {
        cropped
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
      else
        append('|')
          .append(bounds.left)
          .append('|')
          .append(bounds.top)
          .append('|')
          .append(bounds.right)
          .append('|')
          .append(bounds.bottom)
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
