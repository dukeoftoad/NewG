package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.topCenter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * People Manager lives under Settings → People Manager; the People page
 * (Search "portraits") is browsing-only since mutations moved here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeopleManagerRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `people manager opens from settings hub and renders its list`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_people_manager"))
    // The tiny Robolectric window leaves the row half-clipped at the viewport
    // edge after scrollToNode, where a center-click lands off-window and gets
    // swallowed — click its visible top edge instead.
    composeTestRule
      .onNodeWithTag("settings_row_people_manager")
      .performTouchInput { click(topCenter) }

    // The sub page swaps in via AnimatedContent — wait for the router to land.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("people_manager_list").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("people_manager_list").assertExists()

    // Fresh install has no face index yet — the empty state must render.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithText("No people detected yet").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("No people detected yet").assertIsDisplayed()
  }
}
