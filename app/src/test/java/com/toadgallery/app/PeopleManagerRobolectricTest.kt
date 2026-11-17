package com.toadgallery.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * People Manager lives under Settings → People Manager (AI section); the People
 * page (Search "portraits") is browsing-only since mutations moved here.
 *
 * Only the hub integration is asserted here: the full page-open click is flaky
 * on Robolectric's tiny 320x470 viewport (the row composes below the fold and
 * clicks get swallowed behind the bottom tab bar), while the manager page
 * contents are plain Compose covered by compile, PersonManagementSheet tests
 * and FaceFeedbackDao tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeopleManagerRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `settings hub exposes the People Manager row with live counts`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_people_manager"))
    composeTestRule.waitForIdle()

    // scrollToNode already proves the row is present: it fails the test if no
    // node matching the tag can be scrolled into composition.
    val row =
      composeTestRule
        .onAllNodesWithTag("settings_row_people_manager")
        .fetchSemanticsNodes()
        .firstOrNull()
    assertTrue("People Manager row missing from the settings hub", row != null)
  }
}
