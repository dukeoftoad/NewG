package com.toadgallery.app.data.editor

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-wide LRU for decoded, EXIF-oriented editor source bitmaps.
 *
 * Opening the editor previously re-decoded a bounded bitmap from disk every
 * single time (MediaStore IO + EXIF pass). Keeping the last few sources hot
 * makes editor re-open instant, keeps the "compare original" hold preview
 * allocation-free, and avoids repeated decodes when bouncing between the
 * viewer and the editor on the same photo.
 *
 * Evicted bitmaps are deliberately NOT recycled: Compose may still hold an
 * ImageBitmap wrapper that is about to be drawn, and recycling a drawn
 * bitmap crashes. Dropping the reference lets the GC reclaim safely.
 */
object EditorSourceBitmapCache {
  private const val MAX_ENTRIES = 4

  private val cache =
    object : LruCache<String, Bitmap>(MAX_ENTRIES) {
      override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

  fun cacheKey(itemId: Long, sourceUriOrRes: String?, maxDimension: Int): String =
    "$itemId:${sourceUriOrRes.orEmpty()}:$maxDimension"

  operator fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

  fun put(key: String, bitmap: Bitmap) {
    synchronized(cache) { cache.put(key, bitmap) }
  }

  fun clear() {
    synchronized(cache) { cache.evictAll() }
  }
}
