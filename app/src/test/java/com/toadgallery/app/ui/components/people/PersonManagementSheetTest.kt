package com.toadgallery.app.ui.components.people

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.toadgallery.app.data.ai.FacePersonGroup
import com.toadgallery.app.data.ai.FaceReference
import com.toadgallery.app.data.model.AppearanceMode
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.TonePreset
import com.toadgallery.app.ui.screens.SearchScreen
import com.toadgallery.app.ui.theme.ToadGalleryTheme
import com.toadgallery.app.ui.viewmodel.GalleryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonManagementSheetTest {
  @get:Rule
  val compose = createComposeRule()

  private val alice = FacePersonGroup(
    id = "alice",
    photoIds = listOf(1L, 2L),
    faceCount = 2,
    coverPhotoId = 1L,
    coverFaceIndex = 0,
    name = "Alice",
    clusterIds = listOf("alice"),
    faces = listOf(FaceReference(1L, 0), FaceReference(2L, 0)),
  )

  private val bob = FacePersonGroup(
    id = "bob",
    photoIds = listOf(3L, 4L),
    faceCount = 2,
    coverPhotoId = 3L,
    coverFaceIndex = 0,
    name = "Bob",
    clusterIds = listOf("bob"),
    faces = listOf(FaceReference(3L, 0), FaceReference(4L, 0)),
  )

  private fun media(id: Long) = GalleryMediaItem(
    id = id,
    title = "Photo $id",
    uriString = null,
    drawableResName = null,
    category = MediaCategory.CAMERA,
    mediaType = MediaType.PHOTO,
    durationLabel = null,
    dateLabel = "Today",
    weekGroup = "This week",
    monthGroup = "This month",
    timestamp = id,
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

  @Test
  fun removingFaceThumbnailDoesNotRecycleBitmapUsedByCompose() {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    val isVisible = mutableStateOf(true)
    compose.setContent {
      if (isVisible.value) {
        FaceThumbnailBitmap(
          bitmap = bitmap,
          contentDescription = "Lifecycle face thumbnail",
        )
      }
    }

    compose.onNodeWithContentDescription("Lifecycle face thumbnail").assertIsDisplayed()
    compose.runOnIdle { isVisible.value = false }
    compose.waitForIdle()

    // Bitmap ownership stays with its producer; Compose may still retain a
    // recorded drawing layer after the node leaves the composition.
    assertFalse(bitmap.isRecycled)
  }

  @Test
  fun editorCanChooseAndPersistAnExactFaceAsCustomCover() {
    var selected: Triple<String, Long, Int>? = null
    compose.setContent {
      ToadGalleryTheme(appearanceMode = AppearanceMode.DARK) {
        PersonManagementSheet(
          group = alice,
          uiState = GalleryUiState(
            peopleGroups = listOf(alice, bob),
            visibleMedia = listOf(media(1L), media(2L), media(3L), media(4L)),
          ),
          onDismiss = {},
          onNamePerson = { _, _ -> },
          onExcludeFace = { _, _ -> },
          onSetPersonCover = { group, photoId, faceIndex ->
            selected = Triple(group.id, photoId, faceIndex)
          },
          onSetPersonIgnored = { _, _ -> },
          onAssignFace = { _, _, _ -> },
          onUndoFaceCorrection = {},
        )
      }
    }

    compose.onNodeWithText("Use as cover").performScrollTo().assertIsDisplayed().performClick()
    compose.runOnIdle {
      assertEquals(Triple("alice", 2L, 0), selected)
    }
  }

  @Test
  fun editorExposesPerFaceAssignmentActions() {
    compose.setContent {
      ToadGalleryTheme {
        PersonManagementSheet(
          group = alice,
          uiState = GalleryUiState(
            peopleGroups = listOf(alice, bob),
            visibleMedia = listOf(media(1L), media(2L), media(3L), media(4L)),
          ),
          onDismiss = {},
          onNamePerson = { _, _ -> },
          onExcludeFace = { _, _ -> },
          onSetPersonCover = { _, _, _ -> },
          onSetPersonIgnored = { _, _ -> },
          onAssignFace = { _, _, _ -> },
          onUndoFaceCorrection = {},
        )
      }
    }

    // Each detected face exposes its own assignment entry point. The
    // deterministic suggestion ordering is covered by FacePersonClustererTest;
    // Robolectric does not reliably expose Compose DropdownMenu popup semantics.
    compose.onAllNodesWithText("Assign")[0].performScrollTo().assertIsDisplayed()
  }

  @Test
  fun editorIgnoreAndUndoActionsReachTheirCallbacks() {
    var ignored: Pair<String, Boolean>? = null
    var undoCount = 0
    compose.setContent {
      ToadGalleryTheme {
        PersonManagementSheet(
          group = alice,
          uiState = GalleryUiState(),
          onDismiss = {},
          onNamePerson = { _, _ -> },
          onExcludeFace = { _, _ -> },
          onSetPersonCover = { _, _, _ -> },
          onSetPersonIgnored = { group, value -> ignored = group.id to value },
          onAssignFace = { _, _, _ -> },
          onUndoFaceCorrection = { undoCount++ },
        )
      }
    }

    compose.onNodeWithText("Ignore").performClick()
    compose.runOnIdle { assertEquals("alice" to true, ignored) }

    compose.onNodeWithText("Undo").performClick()
    compose.runOnIdle { assertEquals(1, undoCount) }
  }

  @Test
  fun ignoredPeopleCanBeRestoredFromPeopleSection() {
    var restored: Pair<String, Boolean>? = null
    compose.setContent {
      ToadGalleryTheme {
        SearchScreen(
          uiState = GalleryUiState(
            searchQuery = "portraits",
            ignoredPeopleGroups = listOf(alice),
          ),
          isWideLayout = false,
          onSelectSearchMode = {},
          onQueryChange = {},
          onToggleLocationFilter = {},
          onSelectTypeFilter = {},
          onSelectMapMode = {},
          onSelectMapPin = {},
          onNotifyMissingGps = {},
          onOpenLightbox = {},
          onSetPersonIgnored = { group, value -> restored = group.id to value },
        )
      }
    }

    compose
      .onNodeWithTag("search_results_list")
      .performScrollToNode(hasText("Unignore"))
    compose.onNodeWithText("Unignore").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals("alice" to false, restored) }
  }
}
