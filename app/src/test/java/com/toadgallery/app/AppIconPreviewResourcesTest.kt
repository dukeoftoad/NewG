package com.toadgallery.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.domain.AppIcon
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the v2.0.0 crash (IllegalArgumentException: "Only VectorDrawables
 * and rasterized asset types are supported"): the App Icon settings page called
 * painterResource on launcher mipmaps, which resolve to <adaptive-icon> XMLs on API 26+.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppIconPreviewResourcesTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `every launcher icon variant has a mipmap`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppIcon.entries.forEach { icon ->
      val resId = context.resources.getIdentifier(icon.mipmapName, "mipmap", context.packageName)
      assertTrue("missing mipmap for ${icon.name}", resId != 0)
    }
  }

  @Test
  fun `launcher mipmaps resolve to adaptive icons and draw without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppIcon.entries.forEach { icon ->
      val resId = context.resources.getIdentifier(icon.mipmapName, "mipmap", context.packageName)
      val drawable = ContextCompat.getDrawable(context, resId)
      assertNotNull("no drawable for ${icon.name}", drawable)
      // On 26+ resource resolution must land on the adaptive XML — the exact
      // configuration that crashed painterResource in v2.0.0.
      assertTrue(
        "expected AdaptiveIconDrawable for ${icon.name}, got ${drawable!!::class.java.simpleName}",
        drawable is AdaptiveIconDrawable,
      )
      // The same draw the preview tile performs: render into a bitmap, no throw.
      drawable.setBounds(0, 0, 128, 128)
      drawable.draw(Canvas(Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)))
    }
  }

  @Test
  fun `app icon settings page renders all tiles without crashing`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_app_icon"))
    composeTestRule.onNodeWithTag("settings_row_app_icon").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_app_icon_list").assertExists()
    // One preview tile per selectable icon.
    composeTestRule
      .onAllNodesWithTag("app_icon_tile_${AppIcon.CLASSIC_TOAD.name}", useUnmergedTree = true)
      .assertCountEquals(1)
    AppIcon.entries.forEach { icon ->
      composeTestRule
        .onNodeWithTag("app_icon_tile_${icon.name}", useUnmergedTree = true)
        .assertExists()
    }
  }
}
