package com.toadgallery.app

import android.app.Application
import android.graphics.Bitmap
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.toadgallery.app.data.local.ToadGalleryDatabase
import com.toadgallery.app.data.preferences.UserPreferencesRepository
import com.toadgallery.app.data.trash.TrashPurgeScheduler
import com.toadgallery.app.domain.PackageManagerAppIconSwitcher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Implements [Configuration.Provider] so WorkManager initializes on demand via
 * the Application rather than the androidx.startup manifest initializer (which
 * we remove in AndroidManifest). On-demand init is the Robolectric-friendly
 * path: unit tests that touch WorkManager get a real singleton built from this
 * Configuration, without needing the manifest startup provider to have run.
 */
class ToadGalleryApplication :
  Application(),
  ImageLoaderFactory,
  Configuration.Provider {

  override val workManagerConfiguration: Configuration =
    Configuration.Builder()
      .setMinimumLoggingLevel(android.util.Log.INFO)
      .build()


  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    // Re-apply the saved launcher icon: enable the saved alias and disable the
    // others, so a fresh install or a restored backup shows the right icon even
    // before the user opens Settings → Appearance & Timeline → App Icon.
    // (Same startup pattern as toadFILES' ToadApp.)
    appScope.launch(Dispatchers.IO) {
      val icon =
        UserPreferencesRepository(this@ToadGalleryApplication).preferences.first().appIcon
      PackageManagerAppIconSwitcher(this@ToadGalleryApplication).apply(icon)
    }
    // Schedule the 30-day Recycle Bin auto-purge (v2.1.0). KEEP policy means
    // re-scheduling on every Application start is a no-op once WorkManager has
    // the unique-named periodic request; reboot / reinstall survive. The worker
    // itself only deletes trashed rows older than 30 days.
    TrashPurgeScheduler.schedule(this)

    // E3 (v2.1.0): one-time eager backfill of legacy v8 null-faceId feedback
    // rows against each photo's current face payload, so feedback on photos
    // that are never re-indexed still gains stable face identities. Gated by
    // a preference flag (exactly once per install); rows that cannot be
    // derived from their payload stay on the saved-index fallback (safe skip).
    appScope.launch(Dispatchers.IO) {
      val prefs = UserPreferencesRepository(this@ToadGalleryApplication)
      if (!prefs.preferences.first().legacyFaceIdBackfillDone) {
        runCatching {
          val dao = ToadGalleryDatabase.getInstance(this@ToadGalleryApplication).galleryDao()
          val backfilled = dao.backfillLegacyFaceIds()
          android.util.Log.i("FaceBackfill", "legacy face-ID backfill updated $backfilled rows")
        }
        prefs.setLegacyFaceIdBackfillDone()
      }
    }
  }

  override fun newImageLoader(): ImageLoader =
    ImageLoader.Builder(this)
      .memoryCache {
        MemoryCache.Builder(this)
          // App-wide cache layering: Room holds metadata, Coil holds pixels.
          // 30% of app memory with strong references keeps grid fling, the
          // vault grid and editor re-opens on decoded bitmaps without
          // re-decoding from MediaStore on every swipe back.
          .maxSizePercent(0.30)
          .strongReferencesEnabled(true)
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
