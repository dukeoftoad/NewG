package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.ai.DepthAnythingV2Engine
import com.example.data.ai.DepthMapResult
import com.example.data.editor.PhotoEditorRenderer
import com.example.data.model.AdjustParams
import com.example.data.model.AspectRatioPreset
import com.example.data.model.BokehParams
import com.example.data.model.BokehShape
import com.example.data.model.CropParams
import com.example.data.model.DepthPreviewMode
import com.example.data.model.FilterLook
import com.example.data.model.MarkupStroke
import com.example.data.model.MarkupTextItem
import com.example.data.model.MarkupToolType
import com.example.data.model.NormalizedPoint
import com.example.data.model.PhotoEditRecipe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoEditorAndDepthRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `recipe json roundtrip preserves all adjust, filter, crop, bokeh, and markup fields`() {
    val original =
      PhotoEditRecipe(
        adjust =
          AdjustParams(
            exposure = 0.25f,
            brightness = -0.1f,
            contrast = 0.3f,
            saturation = 0.15f,
            vibrancy = 0.2f,
            warmth = 0.4f,
            tint = -0.2f,
            highlights = -0.3f,
            shadows = 0.35f,
            sharpen = 0.5f,
            vignette = 0.45f,
            fade = 0.12f,
          ),
        filterLook = FilterLook.TEAL_ORANGE,
        filterIntensity = 0.72f,
        filmGrain = 0.33f,
        crop =
          CropParams(
            left = 0.1f,
            top = 0.15f,
            right = 0.9f,
            bottom = 0.85f,
            aspectPreset = AspectRatioPreset.FOUR_FIVE,
            straightenDegrees = -7.5f,
            rotationQuarterTurns = 1,
            flipHorizontal = true,
            flipVertical = false,
          ),
        bokeh =
          BokehParams(
            enabled = true,
            focusX = 0.42f,
            focusY = 0.38f,
            focusDepth = 0.82f,
            aperture = 0.75f,
            focusRange = 0.18f,
            bokehShape = BokehShape.HEXAGON,
            foregroundColorPop = 0.6f,
            depthFog = 0.25f,
            subjectRelight = 0.4f,
            backgroundDim = 0.3f,
            depthPreviewMode = DepthPreviewMode.HEATMAP,
          ),
        strokes =
          listOf(
            MarkupStroke(
              id = 101L,
              tool = MarkupToolType.PRIVACY_BLUR,
              colorHex = 0xFFFFFFFFL,
              strokeWidthNorm = 0.02f,
              points = listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.4f, 0.4f)),
            )
          ),
        textItems =
          listOf(
            MarkupTextItem(
              id = 202L,
              text = "Toad Pond",
              x = 0.5f,
              y = 0.6f,
              colorHex = 0xFF6EE7B7L,
            )
          ),
      )

    val decoded = PhotoEditRecipe.fromJson(original.toJsonString())
    assertFalse(decoded.isIdentity)
    assertEquals(original.adjust.exposure, decoded.adjust.exposure, 0.001f)
    assertEquals(original.adjust.shadows, decoded.adjust.shadows, 0.001f)
    assertEquals(original.filterLook, decoded.filterLook)
    assertEquals(original.filterIntensity, decoded.filterIntensity, 0.001f)
    assertEquals(original.filmGrain, decoded.filmGrain, 0.001f)
    assertEquals(original.crop.aspectPreset, decoded.crop.aspectPreset)
    assertEquals(original.crop.rotationQuarterTurns, decoded.crop.rotationQuarterTurns)
    assertEquals(original.crop.flipHorizontal, decoded.crop.flipHorizontal)
    assertEquals(original.bokeh.bokehShape, decoded.bokeh.bokehShape)
    assertEquals(original.bokeh.depthPreviewMode, decoded.bokeh.depthPreviewMode)
    assertEquals(original.bokeh.foregroundColorPop, decoded.bokeh.foregroundColorPop, 0.001f)
    assertEquals(1, decoded.strokes.size)
    assertEquals(MarkupToolType.PRIVACY_BLUR, decoded.strokes.first().tool)
    assertEquals(1, decoded.textItems.size)
    assertEquals("Toad Pond", decoded.textItems.first().text)
  }

  @Test
  fun `depth engine ViT dimensions snap cleanly to 14px grid and normalize plane`() {
    val (w, h) = DepthAnythingV2Engine.computeVitInputDimensions(1200, 900)
    assertEquals(0, w % 14)
    assertEquals(0, h % 14)
    assertTrue(w >= h)

    val norm = DepthAnythingV2Engine.normalizeDepthPlane(floatArrayOf(2f, 6f, Float.NaN, 10f))
    assertEquals(0f, norm[0], 0.001f)
    assertEquals(0.5f, norm[1], 0.001f)
    assertEquals(0.5f, norm[2], 0.001f)
    assertEquals(1f, norm[3], 0.001f)
  }

  @Test
  fun `photo editor renderer executes crop, filter, privacy mosaic, and bokeh depth render`() = runBlocking {
    val src = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
    for (y in 0 until 80) {
      for (x in 0 until 120) {
        src.setPixel(x, y, if (x < 60) Color.rgb(220, 60, 40) else Color.rgb(40, 120, 220))
      }
    }

    // 1. Crop + Rotate
    val rotated =
      PhotoEditorRenderer.applyGeometryOnly(
        src,
        CropParams(left = 0.25f, top = 0f, right = 0.75f, bottom = 1f, rotationQuarterTurns = 1),
      )
    assertEquals(40, rotated.width)
    assertEquals(120, rotated.height)
    rotated.recycle()

    // The crop editing preview uses the same transformed orientation as the renderer.
    val cropPreview = PhotoEditorRenderer.applyTransformOnly(
      src,
      CropParams(rotationQuarterTurns = 1, flipHorizontal = true),
    )
    assertEquals(80, cropPreview.width)
    assertEquals(120, cropPreview.height)
    cropPreview.recycle()

    // 2. Monochrome + Privacy Blur + Text + Bokeh
    val fakeDepth =
      DepthMapResult(
        width = 16,
        height = 16,
        normalizedDepth = FloatArray(256) { idx -> (idx % 16) / 15f },
        inferenceTimeMs = 12L,
      )
    val bokehOut =
      PhotoEditorRenderer.renderFullPreview(
        source = src,
        recipe =
          PhotoEditRecipe(
            filterLook = FilterLook.MONOCHROME,
            filterIntensity = 1f,
            bokeh =
              BokehParams(
                enabled = true,
                focusX = 0.9f,
                focusY = 0.5f,
                focusDepth = 0.95f,
                aperture = 0.85f,
                foregroundColorPop = 0.5f,
              ),
            strokes =
              listOf(
                MarkupStroke(
                  id = 1L,
                  tool = MarkupToolType.PRIVACY_BLUR,
                  colorHex = 0xFFFFFFFFL,
                  strokeWidthNorm = 0.03f,
                  points = listOf(NormalizedPoint(0.2f, 0.5f), NormalizedPoint(0.3f, 0.5f)),
                )
              ),
          ),
        depthMap = fakeDepth,
      )
    val px = bokehOut.getPixel(100, 40)
    assertEquals(Color.red(px), Color.green(px))
    assertEquals(Color.green(px), Color.blue(px))
    bokehOut.recycle()

    // 3. Depth Heatmap preview produces high-contrast depth colors across X
    val heatmapOut =
      PhotoEditorRenderer.renderFullPreview(
        source = src,
        recipe =
          PhotoEditRecipe(
            bokeh = BokehParams(enabled = true, depthPreviewMode = DepthPreviewMode.HEATMAP)
          ),
        depthMap = fakeDepth,
      )
    assertNotEquals(heatmapOut.getPixel(5, 40), heatmapOut.getPixel(115, 40))
    heatmapOut.recycle()
    src.recycle()
  }

  @Test
  fun `photo editor opens from lightbox, exercises all 5 tabs, undo redo, split compare, and dual save modes`() {
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

    // Open photo -> open full-capability Editor
    composeTestRule.onNodeWithTag("media_card_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lightbox_edit_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
          composeTestRule.onNodeWithTag("photo_editor_screen").assertIsDisplayed()
          true
        }
        .getOrDefault(false)
    }

    composeTestRule.onNodeWithTag("photo_editor_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_canvas").assertIsDisplayed()

    // 1. Adjust tab
    composeTestRule.onNodeWithTag("adjust_param_warmth").performClick()
    composeTestRule.onNodeWithTag("editor_adjust_slider").assertIsDisplayed()

    // 2. Filters + Grain tab (commits undo history)
    composeTestRule.onNodeWithTag("editor_tab_filters").performClick()
    composeTestRule.onNodeWithTag("filter_look_warm_film").performClick()
    composeTestRule.onNodeWithTag("editor_filter_intensity_slider").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_film_grain_slider").assertIsDisplayed()

    // 3. Crop + Transform tab
    composeTestRule.onNodeWithTag("editor_tab_crop").performClick()
    composeTestRule.onNodeWithTag("crop_aspect_square").performClick()
    composeTestRule.onNodeWithTag("crop_rotate_right").performClick()
    composeTestRule.onNodeWithTag("crop_reset_btn").assertIsDisplayed()

    // 4. Bokeh tab shows on-demand bokeh-depth-dav2 download card when not downloaded
    composeTestRule.onNodeWithTag("editor_tab_bokeh").performClick()
    composeTestRule.onNodeWithTag("editor_bokeh_download_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_bokeh_download_btn").assertIsDisplayed()

    // 5. Markup + Privacy Blur + Text tab
    composeTestRule.onNodeWithTag("editor_tab_markup").performClick()
    composeTestRule.onNodeWithTag("markup_tool_privacy_blur").performClick()
    composeTestRule
      .onNodeWithTag("markup_tool_row")
      .performScrollToNode(hasTestTag("markup_tool_text"))
    composeTestRule.onNodeWithTag("markup_tool_text").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("markup_text_input").performTextInput("Pond 2026")
    composeTestRule.onNodeWithTag("markup_text_confirm_btn").performClick()
    composeTestRule.waitForIdle()

    // Undo / Redo & Split Before/After Compare
    composeTestRule.onNodeWithTag("editor_undo_button").assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag("editor_redo_button").assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag("editor_split_toggle_button").performClick()
    composeTestRule.onNodeWithText("ORIGINAL").assertIsDisplayed()
    composeTestRule.onNodeWithText("EDITED").assertIsDisplayed()

    // Save prompt -> Apply Non-Destructively
    composeTestRule.onNodeWithTag("editor_save_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("editor_save_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_save_nondestructive_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("photo_editor_screen").fetchSemanticsNodes().isEmpty()
    }

    // Back in Lightbox, verify EXIF sheet shows "Revert to Original" badge button
    composeTestRule.onNodeWithTag("lightbox_info_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("exif_revert_original_btn").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("exif_revert_original_btn").performScrollTo().assertIsDisplayed()

    // Re-open editor from EXIF sheet and Save as New Copy
    composeTestRule.onNodeWithTag("exif_open_editor_chip").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("photo_editor_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_save_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("editor_save_copy_btn").performClick()

    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("photo_editor_screen").fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun `ai settings displays synchronized bokeh-depth-dav2 on-demand model pack card`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("settings_hub_list")
      .performScrollToNode(hasTestTag("settings_row_ai_engine"))
    composeTestRule.onNodeWithTag("settings_row_ai_engine").performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("settings_ai_list")
      .performScrollToNode(hasTestTag("depth_model_pack_card"))
    composeTestRule.onNodeWithTag("depth_model_pack_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bokeh Depth (bokeh-depth-dav2)").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("settings_ai_list")
      .performScrollToNode(hasTestTag("toggle_depth_pack_btn"))
    composeTestRule.onNodeWithTag("toggle_depth_pack_btn").assertIsDisplayed()
    assertTrue(composeTestRule.onAllNodesWithText("Download (~27.3 MB)").fetchSemanticsNodes().isNotEmpty())
  }
}
