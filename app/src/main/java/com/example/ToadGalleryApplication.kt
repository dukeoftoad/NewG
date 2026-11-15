package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class ToadGalleryApplication : Application(), ImageLoaderFactory {
  override fun newImageLoader(): ImageLoader =
    ImageLoader.Builder(this)
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.25)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(File(cacheDir, "coil_image_cache"))
          .maxSizeBytes(150L * 1024L * 1024L)
          .build()
      }
      .bitmapConfig(Bitmap.Config.ARGB_8888)
      .allowHardware(true)
      .respectCacheHeaders(false)
      .crossfade(120)
      .build()
}
