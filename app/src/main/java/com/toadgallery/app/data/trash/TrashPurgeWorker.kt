package com.toadgallery.app.data.trash

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.toadgallery.app.data.local.ToadGalleryDatabase
import com.toadgallery.app.data.repository.GalleryRepository
import java.util.concurrent.TimeUnit

/**
 * Enforces the Recycle Bin's 30-day auto-delete promise.
 *
 * The Recycle Bin dialog (ui/overlays/GalleryModals.kt) tells the user
 * "Auto-deletes in 30 days". Without this worker that promise was hollow:
 * trashed rows lived in the Room DB forever, only cleared by an explicit
 * "Empty bin". This worker is the missing enforcement arm.
 *
 * Schedule: a periodic [androidx.work.PeriodicWorkRequest] at ~24h interval
 * (see [TrashPurgeScheduler]); WorkManager defers to system batching, so the
 * actual cadence is "roughly daily while the device is idle". This is fine —
 * the 30-day window has slack, and the cutoff is "<=", so a slightly late run
 * still deletes everything due.
 *
 * What it deletes: rows with [com.toadgallery.app.data.local.MediaItemEntity.isTrashed]
 * = true AND trashedAt <= now - 30 days. Rows with NULL trashedAt are
 * intentionally skipped (a data-integrity artefact — see
 * [com.toadgallery.app.data.local.GalleryDao.purgeTrashedOlderThan]); the user
 * resolves those via "Empty bin".
 *
 * What it does NOT delete: the underlying media files. Trash is a two-part
 * operation (our isTrashed flag + MediaStore.createTrashRequest); the file
 * itself is owned by MediaStore's own trash, which the OS clears. This worker
 * only cleans our metadata rows once their retention window expires.
 */
class TrashPurgeWorker(
  appContext: Context,
  params: WorkerParameters,
  /**
   * How to obtain the [GalleryRepository]. Production uses the real one built
   * from applicationContext; tests inject a fake built on an in-memory Room DB.
   */
  private val repositoryProvider: (Context) -> GalleryRepository = ::defaultRepository,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val now = System.currentTimeMillis()
    val cutoff = now - RETENTION_MILLIS
    val repo = repositoryProvider(applicationContext)
    return try {
      val purged = repo.purgeTrashedOlderThan(cutoff)
      if (purged > 0) {
        Log.i(TAG, "purged $purged trashed rows older than ${RETENTION_DAYS} days (cutoff=$cutoff)")
      } else {
        Log.i(TAG, "no trashed rows older than ${RETENTION_DAYS} days; nothing to purge")
      }
      Result.success()
    } catch (t: Throwable) {
      Log.w(TAG, "purge pass failed; will retry on next schedule", t)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "TrashPurge"
    const val RETENTION_DAYS = 30L
    val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(RETENTION_DAYS)

    /**
     * "N days left" label for a trashed row, or null when the row has no
     * timestamp (legacy artifact — the user resolves it via Empty bin).
     * Rounds the remaining window UP, so an item trashed just now reads
     * "30 days left" and the final day reads "1 day left", then "Deletes
     * today" once the window has passed.
     */
    fun daysLeftLabel(trashedAt: Long?, now: Long = System.currentTimeMillis()): String? {
      if (trashedAt == null) return null
      val elapsed = (now - trashedAt).coerceAtLeast(0L)
      val remaining = RETENTION_MILLIS - elapsed
      if (remaining <= 0L) return "Deletes today"
      val days = (remaining + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)
      return if (days == 1L) "1 day left" else "$days days left"
    }
    /**
     * The "about to delete" notice window — items within this many days of
     * the 30-day purge get a heads-up banner in the Recycle Bin so the user
     * can restore them before they're gone.
     */
    const val GRACE_DAYS = 7L
    val GRACE_MILLIS = TimeUnit.DAYS.toMillis(GRACE_DAYS)

    /** Unique name under which the periodic worker is enqueued. */
    const val WORK_NAME = "toadgallery_trash_purge"

    /**
     * The production repository lookup: a fresh [GalleryRepository] on the
     * app's singleton Room DB. Cheap — the DB itself is cached behind
     * [ToadGalleryDatabase.getInstance], and the repo is a thin wrapper.
     */
    private fun defaultRepository(context: Context): GalleryRepository =
      GalleryRepository(
        context.applicationContext,
        ToadGalleryDatabase.getInstance(context.applicationContext).galleryDao(),
      )
  }
}
