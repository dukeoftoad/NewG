package com.toadgallery.app

import com.toadgallery.app.data.editor.withHorizontalFlipApplied
import com.toadgallery.app.data.editor.withQuarterTurnDelta
import com.toadgallery.app.data.editor.withVerticalFlipApplied
import com.toadgallery.app.data.model.CropParams
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure geometry checks for the crop-rect remapping that keeps the selected
 * region anchored to the same visual pixels when the image transform changes.
 */
class CropGeometryTest {

  private val rect = CropParams(left = 0.2f, top = 0.3f, right = 0.8f, bottom = 0.9f)

  @Test
  fun `quarter turn clockwise remaps rect to the same visual region`() {
    val turned = rect.withQuarterTurnDelta(1)
    // (x, y) -> (1 - y, x): left'=1-bottom, top'=left, right'=1-top, bottom'=right
    assertEquals(0.1f, turned.left, 1e-4f)
    assertEquals(0.2f, turned.top, 1e-4f)
    assertEquals(0.7f, turned.right, 1e-4f)
    assertEquals(0.8f, turned.bottom, 1e-4f)
  }

  @Test
  fun `counter-clockwise turn is the exact inverse of clockwise`() {
    val roundTrip = rect.withQuarterTurnDelta(1).withQuarterTurnDelta(3)
    assertEquals(rect.left, roundTrip.left, 1e-4f)
    assertEquals(rect.top, roundTrip.top, 1e-4f)
    assertEquals(rect.right, roundTrip.right, 1e-4f)
    assertEquals(rect.bottom, roundTrip.bottom, 1e-4f)
  }

  @Test
  fun `half turns mirror the rect through the center`() {
    val turned = rect.withQuarterTurnDelta(2)
    assertEquals(1f - rect.right, turned.left, 1e-4f)
    assertEquals(1f - rect.bottom, turned.top, 1e-4f)
    assertEquals(1f - rect.left, turned.right, 1e-4f)
    assertEquals(1f - rect.top, turned.bottom, 1e-4f)
  }

  @Test
  fun `horizontal flip mirrors an off-center rect`() {
    val offCenter = CropParams(left = 0.1f, top = 0.2f, right = 0.7f, bottom = 0.8f)
    val flipped = offCenter.withHorizontalFlipApplied()
    assertEquals(0.3f, flipped.left, 1e-4f) // 1 - right
    assertEquals(0.9f, flipped.right, 1e-4f) // 1 - left
    assertEquals(offCenter.top, flipped.top, 1e-4f)
    assertEquals(offCenter.bottom, flipped.bottom, 1e-4f)
  }

  @Test
  fun `vertical flip mirrors top and bottom only`() {
    val offCenter = CropParams(left = 0.1f, top = 0.2f, right = 0.7f, bottom = 0.8f)
    val flipped = offCenter.withVerticalFlipApplied()
    assertEquals(0.2f, flipped.top, 1e-4f) // 1 - bottom
    assertEquals(0.8f, flipped.bottom, 1e-4f) // 1 - top
    assertEquals(offCenter.left, flipped.left, 1e-4f)
    assertEquals(offCenter.right, flipped.right, 1e-4f)
  }

  @Test
  fun `rect stays normalized after every transform`() {
    listOf(0, 1, 2, 3).forEach { delta ->
      val turned = rect.withQuarterTurnDelta(delta)
      assertEquals(turned.left, turned.left.coerceIn(0f, 1f), 1e-6f)
      assertEquals(turned.right, turned.right.coerceIn(0f, 1f), 1e-6f)
      assertEquals(turned.top, turned.top.coerceIn(0f, 1f), 1e-6f)
      assertEquals(turned.bottom, turned.bottom.coerceIn(0f, 1f), 1e-6f)
      org.junit.Assert.assertTrue(turned.left < turned.right)
      org.junit.Assert.assertTrue(turned.top < turned.bottom)
    }
  }
}
