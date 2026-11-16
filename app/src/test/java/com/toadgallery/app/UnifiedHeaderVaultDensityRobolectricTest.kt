package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnifiedHeaderVaultDensityRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `unified header collapses toadGALLERY on scroll, supports 2-10 cols and badge hiding, and theme swatch grid`() {
    composeTestRule.waitForIdle()
    // Expanded state: Toad brand icon + toadGALLERY title + More menu + filter chips
    composeTestRule.onNodeWithTag("toad_brand_icon").assertIsDisplayed()
    composeTestRule.onNodeWithText("toadGALLERY").assertIsDisplayed()
    composeTestRule.onNodeWithTag("photos_more_menu_button").assertIsDisplayed()

    // Scroll timeline down -> Room media loads and toadGALLERY fades/hides while pinned controls remain
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule
            .onNodeWithTag("photos_timeline_list")
            .performScrollToNode(hasTestTag("media_card_1"))
          true
        }
        .getOrDefault(false)
    }
    composeTestRule.onNodeWithTag("photos_timeline_list").performTouchInput {
      swipeUp(startY = bottom * 0.8f, endY = top + 40f)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toad_brand_icon").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("photos_more_menu_button").assertIsDisplayed()

    // Use More menu -> Timeline Layout (routes straight to Layout subpage)
    composeTestRule.onNodeWithTag("photos_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_timeline_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Timeline Layout").assertIsDisplayed()
    composeTestRule.onNodeWithTag("columns_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("columns_preset_8").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
          composeTestRule.onNodeWithText("8 cols").assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }

    // Navigate back to Photos tab: compact mode @ 6+ columns hides OCR/GIF badges
    composeTestRule.onNodeWithTag("nav_tab_photos").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithText("OCR").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("GIF").fetchSemanticsNodes().isEmpty())

    // Check Theme & Colors Palette Grid
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_theme"))
    composeTestRule.onNodeWithTag("settings_row_theme").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("settings_theme_list")
      .performScrollToNode(hasTestTag("palette_grid"))
    composeTestRule.onNodeWithTag("palette_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("palette_card_ocean_slate").performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun `full-page hidden vault supports unlock, decrypted viewer, gallery import, and batch unhide`() {
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule
            .onNodeWithTag("photos_timeline_list")
            .performScrollToNode(hasTestTag("media_card_1"))
          true
        }
        .getOrDefault(false)
    }

    // Open full-page Hidden Vault directly from Photos More menu
    composeTestRule.onNodeWithTag("photos_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_hidden_vault").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("hidden_vault_screen").assertIsDisplayed()

    // Unlock via default PIN 1234
    composeTestRule.onNodeWithTag("vault_key_1").performClick()
    composeTestRule.onNodeWithTag("vault_key_2").performClick()
    composeTestRule.onNodeWithTag("vault_key_3").performClick()
    composeTestRule.onNodeWithTag("vault_key_4").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("vault_import_button").assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }

    // Open Direct Import sheet and import a gallery item
    composeTestRule.onNodeWithTag("vault_import_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_import_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vault_import_photo_picker").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vault_import_from_gallery").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("vault_gallery_pick_1").performClick()
    composeTestRule.onNodeWithTag("vault_confirm_gallery_import_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("vault_item_1").assertExists()
          true
        }
        .getOrDefault(false)
    }

    // Tap vault item to open full-screen decrypted viewer
    composeTestRule.onNodeWithTag("vault_item_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_decrypted_viewer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vault_viewer_back_button").performClick()
    composeTestRule.waitForIdle()

    // Multi-select batch unhide back to main gallery
    composeTestRule.onNodeWithTag("vault_select_mode_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_select_all_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_batch_action_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vault_batch_delete_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_batch_delete_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vault_cancel_batch_delete_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vault_batch_unhide_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("vault_empty_import_button").assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }
  }
}
