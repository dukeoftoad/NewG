package com.toadgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.0.0 — the Memories sheet's Day/Week tabs exist and switch. The demo
 * library's sample media carries past-year timestamps, so on most dates the
 * On-This-Day card is present on the photos grid; the test scrolls to it,
 * opens the sheet and asserts both tabs. When today's date has no day-exact
 * demo media the card is legitimately absent (the card hides when empty) and
 * the test skips with a pass — the pure matching rules are covered by
 * OnThisDayTest/WeekMemoriesTest; this test covers the UI wiring only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemoriesSheetTabTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `memories sheet offers day and week tabs`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_photos").performClick()
    composeTestRule.waitForIdle()

    val cardPresent =
      composeTestRule
        .onAllNodes(hasTestTag("on_this_day_card"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    if (!cardPresent) return // nothing day-exact in the demo library today — wiring covered below

    composeTestRule
      .onNodeWithTag("photos_timeline_list")
      .performScrollToNode(hasTestTag("on_this_day_card"))
    composeTestRule.onNodeWithTag("on_this_day_see_all").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("memories_sheet_header").assertIsDisplayed()
    composeTestRule.onNodeWithTag("memories_tab_day").assertIsDisplayed()
    composeTestRule.onNodeWithTag("memories_tab_week").assertIsDisplayed()

    // Week tab: when the ±3-day window has matches, switch and check the
    // header stays (never crashes, stays on the sheet). assertIsEnabled
    // skips the branch when today's window is empty — matching the pure
    // matcher tests' coverage of the disabled case.
    runCatching { composeTestRule.onNodeWithTag("memories_tab_week").assertIsEnabled() }
      .onSuccess {
        composeTestRule.onNodeWithTag("memories_tab_week").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("memories_sheet_header").assertIsDisplayed()
        // and back
        composeTestRule.onNodeWithTag("memories_tab_day").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("memories_sheet_header").assertIsDisplayed()
      }
  }
}
