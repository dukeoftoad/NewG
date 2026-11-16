package com.toadgallery.app

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.data.media.MediaAccessLevel
import com.toadgallery.app.data.media.MediaPermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Media management" (MANAGE_MEDIA) helper + access-level detection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaPermissionHelperTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun `media management is supported on android 11 and up`() {
    assertTrue(MediaPermissionHelper.isMediaManagementSupported())
  }

  @Test
  fun `media management starts ungranted and is detectable`() {
    // The special app-op is not granted by default in a fresh Robolectric env.
    assertFalse(MediaPermissionHelper.hasMediaManagementPermission(context))
  }

  @Test
  fun `request intent targets the dedicated manage media panel`() {
    val intent = MediaPermissionHelper.mediaManagementRequestIntent(context)
    assertNotNull(intent)
    assertEquals(Settings.ACTION_REQUEST_MANAGE_MEDIA, intent!!.action)
    assertEquals("package:${context.packageName}", intent.data.toString())
    val flags = intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    assertEquals(android.content.Intent.FLAG_ACTIVITY_NEW_TASK, flags)
  }

  @Test
  fun `fresh environment reports no media access`() {
    assertEquals(MediaAccessLevel.NONE, MediaPermissionHelper.checkAccessLevel(context))
  }

  @Test
  fun `required permissions include partial access on android 14+`() {
    val required = MediaPermissionHelper.requiredMediaPermissions()
    assertTrue(required.contains(android.Manifest.permission.READ_MEDIA_IMAGES))
    assertTrue(required.contains(android.Manifest.permission.READ_MEDIA_VIDEO))
    assertTrue(
      required.contains(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    )
  }
}
