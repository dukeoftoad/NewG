package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    composeTestRule.waitForIdle()

    // The Robolectric window is tiny (320x470) and performScrollToNode stops as
    // soon as the row is COMPOSED — it can still sit below the fold (or behind
    // the bottom tab bar), where clicks are swallowed. Swipe until the row is
    // fully inside the visible list area, then click its center.
    val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.height
    val visibleBottom = rootHeight * 0.82f
    val visibleTop = rootHeight * 0.27f
    var attempts = 0
    while (attempts < 10) {
      val row =
        composeTestRule
          .onAllNodesWithTag("settings_row_people_manager")
          .fetchSemanticsNodes()
          .firstOrNull()
      val onScreen =
        row != null &&
          row.boundsInRoot.top >= visibleTop &&
          row.boundsInRoot.bottom <= visibleBottom
      if (onScreen) break
      composeTestRule.onNodeWithTag("settings_hub_list").performTouchInput { swipeUp() }
      composeTestRule.waitForIdle()
      attempts++
    }
    composeTestRule
      .onNodeWithTag("settings_row_people_manager")
      .performTouchInput { click(center) }

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
