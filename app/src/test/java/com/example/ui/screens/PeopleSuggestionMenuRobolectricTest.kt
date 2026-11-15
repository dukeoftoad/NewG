package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.ai.FacePersonGroup
import com.example.data.ai.FacePersonSuggestion
import com.example.data.ai.FaceReference
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import com.example.data.model.TonePreset
import com.example.ui.theme.ToadGalleryTheme
import com.example.ui.viewmodel.GalleryUiState
import com.example.ui.viewmodel.MapDisplayMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeopleSuggestionMenuRobolectricTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun assignmentMenuShowsRankedSuggestionsBeforeOtherPeople() {
    val candidate = FacePersonGroup(
      id = "unassigned-face",
      photoIds = listOf(1L),
      faceCount = 1,
      coverPhotoId = 1L,
      faces = listOf(FaceReference(1L, 0)),
    )
    val alice = FacePersonGroup(
      id = "person-alice",
      photoIds = listOf(2L),
      faceCount = 1,
      coverPhotoId = 2L,
      name = "Alice",
    )
    val bob = FacePersonGroup(
      id = "person-bob",
      photoIds = listOf(3L),
      faceCount = 1,
      coverPhotoId = 3L,
      name = "Bob",
    )
    val photo = GalleryMediaItem(
      id = 1L,
      title = "Face photo",
      uriString = null,
      drawableResName = "sample_lake_sunset",
      category = MediaCategory.CAMERA,
      mediaType = MediaType.PHOTO,
      durationLabel = null,
      dateLabel = "",
      weekGroup = "",
      monthGroup = "",
      timestamp = 1L,
      locationName = "",
      hasGps = false,
      mapX = 0f,
      mapY = 0f,
      aspectRatio = 1f,
      isFavorite = false,
      isHidden = false,
      isTrashed = false,
      tags = emptyList(),
      ocrText = "",
      exifSummary = "",
      tonePreset = TonePreset.NATURAL,
      albumId = null,
    )
    val state = GalleryUiState(
      visibleMedia = listOf(photo),
      searchQuery = "portraits",
      peopleGroups = listOf(alice, bob),
      unassignedFaceGroups = listOf(candidate),
      facePersonSuggestions = mapOf(
        candidate.id to listOf(FacePersonSuggestion("person-alice", 0.1f, 0.12f))
      ),
    )

    composeRule.setContent {
      ToadGalleryTheme {
        SearchScreen(
          uiState = state,
          isWideLayout = false,
          onSelectSearchMode = {},
          onQueryChange = {},
          onToggleLocationFilter = {},
          onSelectTypeFilter = {},
          onSelectMapMode = { _: MapDisplayMode -> },
          onSelectMapPin = {},
          onNotifyMissingGps = {},
          onOpenLightbox = {},
        )
      }
    }

    composeRule.onNodeWithText("Assign").performClick()
    composeRule.onNodeWithText("SUGGESTED MATCHES").assertIsDisplayed()
    composeRule.onNodeWithText("Alice · Suggested").assertIsDisplayed()
    composeRule.onNodeWithText("Bob").assertIsDisplayed()
  }
}
