package com.toadgallery.app.data.trash

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [TrashPurgeWorker] as a single periodic job for the app lifetime.
 *
 * Called once from [com.toadgallery.app.ToadGalleryApplication.onCreate] (see
 * PR #2 wiring). KEEP is the right policy: if the app is re-installed or the
 * device is rebooted, WorkManager re-enqueues the same unique-named periodic
 * request rather than stacking duplicates — the [ExistingPeriodicWorkPolicy.KEEP]
 * means a previously-scheduled instance is preserved when present, so a
 * repeated Application start (e.g. process death and restart) is a no-op.
 *
 * Constraints: needs no network (purge is a local DB op); runs only when idle
 * so it never competes with foreground UI; requires battery-not-low so a
 * near-dead phone doesn't burn the last drops on housekeeping. The 24h
 * interval is WorkManager's allowed minimum and the 30-day retention window
 * has plenty of slack, so this cadence is more than enough.
 */
object TrashPurgeScheduler {

  fun schedule(context: Context) {
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
      .setRequiresBatteryNotLow(true)
      .setRequiresDeviceIdle(true)
      .build()

    val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(
      1, TimeUnit.DAYS,
    ).setConstraints(constraints).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      TrashPurgeWorker.WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }
}
