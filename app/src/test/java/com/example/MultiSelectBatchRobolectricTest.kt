package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.media.MediaShareHelper
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import com.example.data.model.TonePreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiSelectBatchRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `media share helper creates FileProvider and MediaStore URIs for single and multiple items`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val helper = MediaShareHelper(context)

      val sampleItem =
        GalleryMediaItem(
          id = 101L,
          title = "Golden Hour Lakefront",
          uriString = null,
          drawableResName = "sample_lake_sunset",
          category = MediaCategory.CAMERA,
          mediaType = MediaType.PHOTO,
          durationLabel = null,
          dateLabel = "12 Nov 2026",
          weekGroup = "Nov 8 – 14, 2026",
          monthGroup = "November 2026",
          timestamp = 1794599900000L,
          locationName = "Hussain Sagar",
          hasGps = true,
          mapX = 0.46f,
          mapY = 0.52f,
          aspectRatio = 0.75f,
          isFavorite = true,
          isHidden = false,
          isTrashed = false,
          tags = listOf("sunset"),
          ocrText = "",
          exifSummary = "Pixel 9 Pro",
          tonePreset = TonePreset.NATURAL,
          albumId = null,
        )

      val singlePayload = helper.buildShareIntent(listOf(sampleItem))
      assertNotNull(singlePayload)
      assertEquals(Intent.ACTION_SEND, singlePayload!!.rawShareIntent.action)
      assertEquals(1, singlePayload.uris.size)
      assertTrue(singlePayload.uris.first().toString().contains("fileprovider"))

      val deviceItem =
        sampleItem.copy(
          id = 102L,
          title = "Device Photo",
          uriString = "content://media/external/images/media/42",
          drawableResName = null,
        )

      val multiPayload = helper.buildShareIntent(listOf(sampleItem, deviceItem))
      assertNotNull(multiPayload)
      assertEquals(Intent.ACTION_SEND_MULTIPLE, multiPayload!!.rawShareIntent.action)
      assertEquals(2, multiPayload.uris.size)
      assertEquals("content://media/external/images/media/42", multiPayload.uris[1].toString())
      assertEquals(2, multiPayload.rawShareIntent.clipData?.itemCount)
    }

  @Test
  fun `grid multi-select displays 5 actions, supports move destinations, and confirms batch delete`() {
    composeTestRule.waitForIdle()

    // Enter multi-select mode from Photos More menu (0 selected -> bottom bar stays hidden)
    composeTestRule.onNodeWithTag("photos_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_multi_select").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("0 selected").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_action_bar").assertDoesNotExist()

    // Wait for Room initial media seeding to enable Select All
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("selection_select_all_button").assertIsEnabled()
          true
        }
        .getOrDefault(false)
    }

    // Select All -> bottom action bar appears with Share, Move, Favorite, Hide, and Delete
    composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("9 selected").assertIsDisplayed()
    composeTestRule.onNodeWithText("Deselect All").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_action_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_share_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_move_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_favorite_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_hide_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_delete_button").assertIsDisplayed()

    // Deselect All -> bottom action bar disappears again
    composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("0 selected").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_action_bar").assertDoesNotExist()

    // Use section quick-select pill for November 2026 (5 items) -> bottom action bar reappears
    composeTestRule
      .onNodeWithTag("photos_timeline_list")
      .performScrollToNode(hasTestTag("section_select_november_2026"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("section_select_november_2026").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("5 selected").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_action_bar").assertIsDisplayed()

    // Open Batch Move Destination dialog and create a new album + move
    composeTestRule.onNodeWithTag("batch_move_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("batch_move_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("move_dest_vault").assertIsDisplayed()
    composeTestRule.onNodeWithTag("move_dest_folder_travel").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("move_new_album_input").performScrollTo().performTextInput("Roadtrip")
    composeTestRule.onNodeWithTag("move_create_album_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
          composeTestRule.onNodeWithTag("batch_move_dialog").assertDoesNotExist()
          true
        }
        .getOrDefault(false)
    }

    // Re-enter selection via More menu, select remaining items, and test Delete Confirmation dialog
    composeTestRule.onNodeWithTag("photos_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_multi_select").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
    composeTestRule.waitForIdle()

    // Tap Delete -> opens confirmation dialog
    composeTestRule.onNodeWithTag("batch_delete_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("batch_delete_confirm_dialog").assertIsDisplayed()

    // Cancel preserves selection
    composeTestRule.onNodeWithTag("cancel_batch_delete_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("batch_delete_confirm_dialog").assertDoesNotExist()
    composeTestRule.onNodeWithTag("batch_action_bar").assertIsDisplayed()

    // Confirm delete moves selected items to Recycle Bin
    composeTestRule.onNodeWithTag("batch_delete_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_batch_delete_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
          composeTestRule.onNodeWithTag("batch_action_bar").assertDoesNotExist()
          true
        }
        .getOrDefault(false)
    }
    composeTestRule
      .onNodeWithTag("photos_timeline_list")
      .performScrollToNode(hasText("No media matches this filter"))
    composeTestRule.onNodeWithText("No media matches this filter").assertIsDisplayed()
  }
}
