package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toadgallery.app.ui.overlays.IntroOverlay
import com.toadgallery.app.ui.theme.ToadGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3.0.0 — the first-run intro composable: pages advance, Skip finishes
 * immediately, Start finishes from the last page. Pure createComposeRule
 * (no activity, no DataStore) — the once-per-install gating lives in
 * UserPreferences.introDone and is covered by the repository's loader
 * default (false) and the Robolectric gate in the shell.
 */
@RunWith(AndroidJUnit4::class)
class IntroOverlayTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `pages advance and finish from the last page`() {
    var finished = 0
    composeRule.setContent { ToadGalleryTheme { IntroOverlay(onFinish = { finished++ }) } }

    composeRule.onNodeWithTag("intro_overlay").assertIsDisplayed()
    composeRule.onNodeWithText("Your photos, your pond").assertIsDisplayed()

    composeRule.onNodeWithTag("intro_next").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Smart, and private").assertIsDisplayed()

    composeRule.onNodeWithTag("intro_next").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Memories & a hidden vault").assertIsDisplayed()

    assertEquals(0, finished)
    composeRule.onNodeWithTag("intro_next").performClick()
    composeRule.waitForIdle()
    assertEquals(1, finished)
  }

  @Test
  fun `skip finishes immediately from the first page`() {
    var finished = 0
    composeRule.setContent { ToadGalleryTheme { IntroOverlay(onFinish = { finished++ }) } }

    composeRule.onNodeWithTag("intro_skip").performClick()
    composeRule.waitForIdle()
    assertEquals(1, finished)
  }
}
