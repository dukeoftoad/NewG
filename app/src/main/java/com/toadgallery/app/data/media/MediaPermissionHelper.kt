package com.toadgallery.app.data.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class MediaAccessLevel(val label: String) {
  NONE("Permission Required"),
  PARTIAL("Selected Photos Only"),
  FULL("Full Library Granted"),
}

object MediaPermissionHelper {

  const val MANAGE_MEDIA_PERMISSION = "android.permission.MANAGE_MEDIA"

  fun requiredMediaPermissions(): Array<String> =
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(
          Manifest.permission.READ_MEDIA_IMAGES,
          Manifest.permission.READ_MEDIA_VIDEO,
          Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
          Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(
          Manifest.permission.READ_MEDIA_IMAGES,
          Manifest.permission.READ_MEDIA_VIDEO,
          Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        arrayOf(
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
      else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

  fun checkAccessLevel(context: Context): MediaAccessLevel {
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        val images = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
        val videos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
        val partial =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        when {
          images || videos -> MediaAccessLevel.FULL
          partial -> MediaAccessLevel.PARTIAL
          else -> MediaAccessLevel.NONE
        }
      }
      else -> {
        if (isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
          MediaAccessLevel.FULL
        } else {
          MediaAccessLevel.NONE
        }
      }
    }
  }

  fun hasMediaLocationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      isGranted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
    } else {
      true
    }

  /**
   * "Media management" is the Android 11+ special app-op permission
   * (android.permission.MANAGE_MEDIA). With it granted, MediaStore write
   * requests issued via PendingIntent — trash/delete/favorite — execute
   * SILENTLY, instead of popping a system confirmation dialog for every
   * batch operation. On pre-Android 11 builds writes go through legacy
   * storage, so the concept does not exist and we report granted.
   */
  fun isMediaManagementSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

  fun hasMediaManagementPermission(context: Context): Boolean =
    if (isMediaManagementSupported()) {
      isGranted(context, MANAGE_MEDIA_PERMISSION)
    } else {
      true
    }

  /**
   * Opens the system "Media management" panel for this app. Android 12+
   * uses the dedicated request screen; Android 11 falls back to the app
   * details page where the toggle lives. Returns null when unsupported.
   */
  fun mediaManagementRequestIntent(context: Context): Intent? =
    when {
      !isMediaManagementSupported() -> null
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        Intent(
            Settings.ACTION_REQUEST_MANAGE_MEDIA,
            Uri.parse("package:${context.packageName}"),
          )
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      else ->
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
          )
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
