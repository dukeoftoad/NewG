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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
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

    // The Robolectric window is tiny (320x470): performScrollToNode stops as
    // soon as the row is COMPOSED, which can leave it below the fold or behind
    // the bottom tab bar, where clicks are swallowed — and lazy extent growth
    // means swipes can overshoot and decompose the row entirely. Nudge the
    // list both ways until the row center sits in the clickable band.
    val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.height
    val bandTop = rootHeight * 0.30f
    val bandBottom = rootHeight * 0.80f
    var centered = false
    var attempts = 0
    while (attempts < 12 && !centered) {
      val row =
        composeTestRule
          .onAllNodesWithTag("settings_row_people_manager")
          .fetchSemanticsNodes()
          .firstOrNull()
      centered =
        row != null &&
          row.boundsInRoot.center.y >= bandTop &&
          row.boundsInRoot.center.y <= bandBottom
      if (centered) break
      composeTestRule.onNodeWithTag("settings_hub_list").performTouchInput {
        if (row == null || row.boundsInRoot.center.y < bandTop) swipeDown() else swipeUp()
      }
      composeTestRule.waitForIdle()
      attempts++
    }
    assertTrue("People Manager row never reached the clickable band", centered)
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
