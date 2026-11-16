package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewerCompactLayoutRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `lightbox opens edge-to-edge, toggles chrome on tap, and opens EXIF sheet on swipe up`() {
    composeTestRule.waitForIdle()

    // Wait for initial Room media seeding on Photos tab and scroll to media_card_1
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule
            .onNodeWithTag("photos_timeline_list")
            .performScrollToNode(hasTestTag("media_card_1"))
          true
        }
        .getOrDefault(false)
    }

    composeTestRule.onNodeWithTag("media_card_1").performClick()
    composeTestRule.waitForIdle()

    // Edge-to-edge lightbox opens with top & bottom overlay chrome, and NO permanently docked EXIF box
    composeTestRule.onNodeWithTag("lightbox_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lightbox_top_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lightbox_bottom_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lightbox_info_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lightbox_details_pill").assertDoesNotExist()
    composeTestRule.onNodeWithTag("lightbox_prev_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("lightbox_next_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("lightbox_exif_sheet").assertDoesNotExist()
    composeTestRule.onNodeWithText("Camera / Source").assertDoesNotExist()

    // Single tap toggles overlay chrome off
    composeTestRule.onNodeWithTag("lightbox_canvas").performTouchInput { click() }
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      composeTestRule.onAllNodesWithTag("lightbox_bottom_bar").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("lightbox_top_bar").assertDoesNotExist()

    // Single tap toggles overlay chrome back on
    composeTestRule.onNodeWithTag("lightbox_canvas").performTouchInput { click() }
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      composeTestRule.onAllNodesWithTag("lightbox_bottom_bar").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("lightbox_top_bar").assertIsDisplayed()

    // Swipe up on the photo canvas at 1x zoom reveals the on-demand EXIF & Tone sheet
    composeTestRule.onNodeWithTag("lightbox_canvas").performTouchInput { swipeUp() }
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      composeTestRule.onAllNodesWithTag("lightbox_exif_sheet").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("tone_preset_warm_film").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Camera / Source").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Location").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Folder & Tags").performScrollTo().assertIsDisplayed()
  }
}
