package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToadGalleryRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `navigate 4 tabs, search-map mode switch, multi-select, and encrypted vault unlock`() {
    composeTestRule.waitForIdle()

    // 4-tab bar check (Map tab removed)
    composeTestRule.onNodeWithTag("nav_tab_photos").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_albums").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_search").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_map").assertDoesNotExist()

    // Photos tab unified header: Toad icon + toadGALLERY + More menu (no standalone Select/Refresh buttons)
    composeTestRule.onNodeWithTag("toad_brand_icon").assertIsDisplayed()
    composeTestRule.onNodeWithText("toadGALLERY").assertIsDisplayed()
    composeTestRule.onNodeWithTag("select_mode_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sync_mediastore_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("photos_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_refresh_library").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_clean_feed").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_multi_select").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("batch_action_bar").assertDoesNotExist()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("selection_select_all_button").assertIsEnabled()
          true
        }
        .getOrDefault(false)
    }
    composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("batch_action_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("selection_clear_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("batch_action_bar").assertDoesNotExist()
          true
        }
        .getOrDefault(false)
    }

    // Navigate to Albums tab and unlock Hidden Vault using default PIN 1234
    composeTestRule.onNodeWithTag("nav_tab_albums").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("new_album_button").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("albums_list")
      .performScrollToNode(hasTestTag("albums_hidden_vault_button"))
    composeTestRule.onNodeWithTag("albums_hidden_vault_button").performClick()
    // The vault visibility is delivered through the combined ViewModel state flow;
    // wait for the overlay itself instead of assuming waitForIdle drains background work.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("hidden_vault_screen").assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }

    composeTestRule.onNodeWithTag("vault_key_1").performClick()
    composeTestRule.onNodeWithTag("vault_key_2").performClick()
    composeTestRule.onNodeWithTag("vault_key_3").performClick()
    composeTestRule.onNodeWithTag("vault_key_4").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
          composeTestRule
            .onNodeWithText("Decrypted in session • Tap an item to inspect or unhide")
            .assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }
    composeTestRule.onNodeWithTag("vault_close_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate to Search tab and switch between Magic Search and Places Map
    composeTestRule.onNodeWithTag("nav_tab_search").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("magic_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_mode_map").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_mode_pins").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_gps_summary_pill").assertIsDisplayed()

    // Navigate to Settings hub, verify Media Permissions row (no Photo Picker), and drill into Theme
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_media_permissions"))
    composeTestRule.onNodeWithTag("settings_row_media_permissions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("switch_photo_picker").assertDoesNotExist()

    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_theme"))
    composeTestRule.onNodeWithTag("settings_row_theme").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Theme & Colors").assertIsDisplayed()
  }
}
