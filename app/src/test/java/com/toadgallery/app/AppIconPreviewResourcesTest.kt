package com.toadgallery.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
      // v2.0.0 crashed because painterResource rejects the resource type that
      // resolution lands on here (adaptive-icon XML on API 26+); ContextCompat
      // must hand back a drawable that draws. Robolectric may map the adaptive
      // XML to its own drawable type, so only the load+draw path is asserted,
      // not the concrete class.
      val drawable =
        checkNotNull(ContextCompat.getDrawable(context, resId)) { "no drawable for ${icon.name}" }
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
      .performScrollToNode(hasTestTag("settings_row_appearance"))
    composeTestRule.onNodeWithTag("settings_row_appearance").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_appearance_list").assertExists()
    // Tiles sit below the mode + palette cards; LazyColumn composes lazily, so
    // scroll to the last tile row before asserting every tile exists.
    composeTestRule
      .onNodeWithTag("settings_appearance_list")
      .performScrollToNode(hasTestTag("app_icon_tile_${AppIcon.CAMERA.name}"))
    composeTestRule.waitForIdle()
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
