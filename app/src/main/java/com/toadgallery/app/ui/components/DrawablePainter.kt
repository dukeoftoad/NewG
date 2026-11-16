package com.toadgallery.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter

/**
 * Painter that draws any [Drawable]: bitmaps, VectorDrawables, and — crucially —
 * `<adaptive-icon>` launcher XMLs, which painterResource rejects with
 * IllegalArgumentException ("Only VectorDrawables and rasterized asset types…").
 * On API 26+ an @mipmap launcher icon resolves to its mipmap-anydpi-v26 adaptive
 * XML, so icon previews must load through the framework's own loader
 * (ContextCompat.getDrawable) and render here instead of via painterResource.
 *
 * Launcher icons are static, so no invalidation plumbing is needed; bounds are
 * re-applied on every draw so the drawable scales to the measured box.
 */
class DrawablePainter(private val drawable: Drawable) : Painter() {
  override val intrinsicSize: Size
    get() {
      val width = drawable.intrinsicWidth
      val height = drawable.intrinsicHeight
      return if (width > 0 && height > 0) Size(width.toFloat(), height.toFloat())
      else Size.Unspecified
    }

  override fun DrawScope.onDraw() {
    drawIntoCanvas { canvas ->
      drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
      drawable.draw(canvas.nativeCanvas)
    }
  }
}
