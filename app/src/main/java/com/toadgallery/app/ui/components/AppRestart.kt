package com.toadgallery.app.ui.components

import android.content.Context
import android.content.Intent

/**
 * Hard-restarts the app: relaunches the launcher intent into a fresh task and
 * then kills the process, so the next thing the user sees is a brand-new app
 * instance. Used after a launcher-icon switch — the activity-alias change is
 * applied by PackageManager immediately, but the running task keeps the old
 * icon in its recents snapshot until the process restarts.
 *
 * startActivity is a synchronous binder call, so the launch request reaches the
 * system before exit(0) runs; the new task then starts in a fresh process.
 */
fun restartApp(context: Context) {
  val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
  if (launch != null) {
    launch.addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TASK or
        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    )
    context.startActivity(launch)
  }
  Runtime.getRuntime().exit(0)
}
