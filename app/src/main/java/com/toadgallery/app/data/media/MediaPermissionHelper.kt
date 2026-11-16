package com.toadgallery.app.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccessLevel(val label: String) {
  NONE("Permission Required"),
  PARTIAL("Selected Photos Only"),
  FULL("Full Library Granted"),
}

object MediaPermissionHelper {

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

  private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
