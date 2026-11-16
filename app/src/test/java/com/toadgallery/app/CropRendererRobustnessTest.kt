package com.toadgallery.app

import android.graphics.Bitmap
import com.toadgallery.app.data.editor.PhotoEditorRenderer
import com.toadgallery.app.data.model.CropParams
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test: legacy or corrupted saved recipes could carry out-of-range
 * or swapped crop edges. The old renderer coerced the RAW crop.left into the
 * right bound (crop.right.coerceIn(crop.left + 0.08f, 1f)) and threw
 * IllegalArgumentException whenever left exceeded 0.92, crashing the editor
 * on open. The normalized implementation must clamp safely instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CropRendererRobustnessTest {

  @Test
  fun `out of range legacy crop rect does not crash applyGeometryOnly`() {
    val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
    val out =
      PhotoEditorRenderer.applyGeometryOnly(
        source,
        CropParams(left = 0.95f, top = 0.9f, right = 0.5f, bottom = 0.4f),
      )
    assertTrue("cropped bitmap must stay usable", out.width >= 1 && out.height >= 1)
    out.recycle()
    source.recycle()
  }

  @Test
  fun `standard crop still produces the expected pixel rect`() {
    val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
    val out =
      PhotoEditorRenderer.applyGeometryOnly(
        source,
        CropParams(left = 0.25f, top = 0f, right = 0.75f, bottom = 1f),
      )
    assertTrue(out.width == 60)
    assertTrue(out.height == 80)
    out.recycle()
    source.recycle()
  }
}
