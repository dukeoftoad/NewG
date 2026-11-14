package com.example.data.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.example.data.ai.DepthMapResult
import com.example.data.local.SampleMediaCatalog
import com.example.data.model.BokehShape
import com.example.data.model.CropParams
import com.example.data.model.DepthPreviewMode
import com.example.data.model.FilterLook
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MarkupStroke
import com.example.data.model.MarkupToolType
import com.example.data.model.PhotoEditRecipe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoEditorRenderer {

  suspend fun loadSourceBitmap(
    context: Context,
    item: GalleryMediaItem,
    maxDimension: Int = 1280,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      runCatching {
          val uriStr = item.uriString
          val raw =
            if (!uriStr.isNullOrBlank()) {
              val uri = Uri.parse(uriStr)
              decodeBoundedFromUri(context, uri, maxDimension)
            } else {
              val resId = SampleMediaCatalog.resolveDrawableRes(item.drawableResName)
              decodeBoundedFromRes(context, resId, maxDimension)
            }
          raw?.let { ensureMutablePixelBacked(it, recycleInput = true) }
        }
        .getOrNull()
    }

  private fun ensureMutablePixelBacked(bmp: Bitmap, recycleInput: Boolean): Bitmap {
    val hasPixels = runCatching {
      if (bmp.width > 0 && bmp.height > 0) {
        bmp.getPixels(IntArray(1), 0, 1, 0, 0, 1, 1)
      }
      true
    }.getOrDefault(false)
    if (hasPixels && bmp.config == Bitmap.Config.ARGB_8888 && bmp.isMutable) {
      return bmp
    }
    val w = bmp.width.coerceAtLeast(1)
    val h = bmp.height.coerceAtLeast(1)
    val backed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    runCatching { Canvas(backed).drawBitmap(bmp, 0f, 0f, null) }
    if (recycleInput && !bmp.isRecycled) bmp.recycle()
    return backed
  }

  private fun decodeBoundedFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDim)
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, decodeOpts)
    }
  }

  private fun decodeBoundedFromRes(context: Context, resId: Int, maxDim: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, resId, opts)
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDim)
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeResource(context.resources, resId, decodeOpts)
  }

  private fun computeInSampleSize(w: Int, h: Int, maxDim: Int): Int {
    if (w <= 0 || h <= 0) return 1
    var sample = 1
    while (max(w, h) / sample > maxDim * 2) {
      sample *= 2
    }
    return sample
  }

  /** Returns an independent bitmap with orientation transforms but without cropping. */
  fun applyTransformOnly(source: Bitmap, crop: CropParams): Bitmap {
    val turns = ((crop.rotationQuarterTurns % 4) + 4) % 4
    if (turns == 0 && !crop.flipHorizontal && !crop.flipVertical && abs(crop.straightenDegrees) <= 0.1f) {
      return source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val matrix = Matrix()
    val cx = source.width / 2f
    val cy = source.height / 2f
    if (crop.flipHorizontal || crop.flipVertical) {
      matrix.postScale(if (crop.flipHorizontal) -1f else 1f, if (crop.flipVertical) -1f else 1f, cx, cy)
    }
    if (turns != 0) matrix.postRotate(turns * 90f, cx, cy)
    if (abs(crop.straightenDegrees) > 0.1f) matrix.postRotate(crop.straightenDegrees, cx, cy)
    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (abs(crop.straightenDegrees) <= 0.1f) return transformed

    val rad = abs(crop.straightenDegrees) * PI.toFloat() / 180f
    val origW = if (turns % 2 == 1) source.height.toFloat() else source.width.toFloat()
    val origH = if (turns % 2 == 1) source.width.toFloat() else source.height.toFloat()
    val scale =
      max(
        (origW * cos(rad) + origH * sin(rad)) / origW,
        (origW * sin(rad) + origH * cos(rad)) / origH,
      )
    val innerW = (origW / scale).roundToInt().coerceIn(8, transformed.width)
    val innerH = (origH / scale).roundToInt().coerceIn(8, transformed.height)
    val leftX = ((transformed.width - innerW) / 2).coerceIn(0, transformed.width - innerW)
    val topY = ((transformed.height - innerH) / 2).coerceIn(0, transformed.height - innerH)
    val trimmed = Bitmap.createBitmap(transformed, leftX, topY, innerW, innerH)
    transformed.recycle()
    return trimmed
  }

  fun applyGeometryOnly(source: Bitmap, crop: CropParams): Bitmap {
    val working = applyTransformOnly(source, crop)
    val cropLeft = (crop.left.coerceIn(0f, 0.92f) * working.width).roundToInt().coerceIn(0, working.width - 4)
    val cropTop = (crop.top.coerceIn(0f, 0.92f) * working.height).roundToInt().coerceIn(0, working.height - 4)
    val cropRight =
      (crop.right.coerceIn(crop.left + 0.08f, 1f) * working.width)
        .roundToInt()
        .coerceIn(cropLeft + 4, working.width)
    val cropBottom =
      (crop.bottom.coerceIn(crop.top + 0.08f, 1f) * working.height)
        .roundToInt()
        .coerceIn(cropTop + 4, working.height)

    if (cropLeft == 0 && cropTop == 0 && cropRight == working.width && cropBottom == working.height) {
      return if (working === source) working.copy(Bitmap.Config.ARGB_8888, true) else working
    }

    val cropped = Bitmap.createBitmap(working, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
    if (working !== source) working.recycle()
    return cropped
  }

  suspend fun renderFullPreview(
    source: Bitmap,
    recipe: PhotoEditRecipe,
    depthMap: DepthMapResult?,
    includeCrop: Boolean = true,
  ): Bitmap =
    withContext(Dispatchers.Default) {
      var canvasBmp =
        if (includeCrop) applyGeometryOnly(source, recipe.crop)
        else applyTransformOnly(source, recipe.crop)

      canvasBmp = ensureMutablePixelBacked(canvasBmp, recycleInput = canvasBmp !== source)

      // 1. Depth Anything V2 Bokeh, Color Pop, Relight, Haze & Depth View
      if (recipe.bokeh.enabled && depthMap != null) {
        val next = applyBokehAndDepthEffects(canvasBmp, recipe, depthMap)
        if (next !== canvasBmp) canvasBmp.recycle()
        canvasBmp = next
      }

      // 2. Color Matrix (Light + Color + Filter Look + Highlights/Shadows)
      if (!recipe.adjust.isDefault || recipe.filterLook != FilterLook.NATURAL) {
        applyToneAndFilter(canvasBmp, recipe)
      }

      // 3. Sharpen
      if (recipe.adjust.sharpen > 0.02f) {
        applySharpen(canvasBmp, recipe.adjust.sharpen)
      }

      // 4. Privacy Mosaic & Vector Markup & Text
      if (recipe.strokes.isNotEmpty() || recipe.textItems.isNotEmpty()) {
        applyMarkupAndPrivacy(canvasBmp, recipe)
      }

      // 5. Vignette & Film Grain
      if (recipe.adjust.vignette > 0.02f) {
        applyVignette(canvasBmp, recipe.adjust.vignette)
      }
      if (recipe.filmGrain > 0.02f) {
        applyFilmGrain(canvasBmp, recipe.filmGrain)
      }

      canvasBmp
    }

  private fun applyBokehAndDepthEffects(
    bitmap: Bitmap,
    recipe: PhotoEditRecipe,
    depthMap: DepthMapResult,
  ): Bitmap {
    val bokeh = recipe.bokeh
    val w = bitmap.width
    val h = bitmap.height
    val count = w * h
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    if (bokeh.depthPreviewMode != DepthPreviewMode.OFF) {
      val out = IntArray(count)
      for (y in 0 until h) {
        val ny = y.toFloat() / (h - 1).coerceAtLeast(1)
        for (x in 0 until w) {
          val nx = x.toFloat() / (w - 1).coerceAtLeast(1)
          val d = depthMap.sampleAt(nx, ny)
          out[y * w + x] =
            if (bokeh.depthPreviewMode == DepthPreviewMode.GRAYSCALE) {
              val v = (d * 255f).roundToInt().coerceIn(0, 255)
              Color.rgb(v, v, v)
            } else {
              heatmapColor(d)
            }
        }
      }
      val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      result.setPixels(out, 0, w, 0, 0, w, h)
      return result
    }

    val blurRadius =
      when {
        bokeh.aperture <= 0.01f -> 0
        bokeh.bokehShape == BokehShape.CREAMY -> (bokeh.aperture * 16f).roundToInt().coerceIn(1, 18)
        bokeh.bokehShape == BokehShape.HEXAGON -> (bokeh.aperture * 12f).roundToInt().coerceIn(1, 14)
        else -> (bokeh.aperture * 14f).roundToInt().coerceIn(1, 16)
      }

    val blurredPixels =
      if (blurRadius > 0) {
        computeBoxBlurPixels(pixels, w, h, blurRadius, boostHighlights = bokeh.bokehShape != BokehShape.CREAMY)
      } else {
        pixels
      }

    val out = IntArray(count)
    val focalD = bokeh.focusDepth.coerceIn(0f, 1f)
    val range = bokeh.focusRange.coerceIn(0.05f, 0.55f)
    val hasPop = bokeh.foregroundColorPop > 0.01f
    val hasFog = bokeh.depthFog > 0.01f
    val hasRelight = abs(bokeh.subjectRelight) > 0.01f
    val hasBgDim = bokeh.backgroundDim > 0.01f

    for (y in 0 until h) {
      val ny = y.toFloat() / (h - 1).coerceAtLeast(1)
      for (x in 0 until w) {
        val idx = y * w + x
        val nx = x.toFloat() / (w - 1).coerceAtLeast(1)
        val d = depthMap.sampleAt(nx, ny)
        val delta = abs(d - focalD)
        val blurWeight = ((delta - range * 0.45f) / (range * 1.4f + 0.08f)).coerceIn(0f, 1f)

        val sharp = pixels[idx]
        val blur = blurredPixels[idx]

        var r = Color.red(sharp) * (1f - blurWeight) + Color.red(blur) * blurWeight
        var g = Color.green(sharp) * (1f - blurWeight) + Color.green(blur) * blurWeight
        var b = Color.blue(sharp) * (1f - blurWeight) + Color.blue(blur) * blurWeight

        // Foreground Color Pop (keeps focal plane color, desaturates background)
        if (hasPop && blurWeight > 0.01f) {
          val luma = 0.299f * r + 0.587f * g + 0.114f * b
          val desatAmount = (blurWeight * bokeh.foregroundColorPop).coerceIn(0f, 1f)
          r = r + (luma - r) * desatAmount
          g = g + (luma - g) * desatAmount
          b = b + (luma - b) * desatAmount
        }

        // Subject Relight (brightens/sculpts focal plane)
        if (hasRelight) {
          val fgWeight = 1f - blurWeight
          val gain = 1f + bokeh.subjectRelight * 0.35f * fgWeight
          r *= gain
          g *= gain
          b *= gain
        }

        // Background Dimming
        if (hasBgDim && blurWeight > 0.01f) {
          val dim = 1f - bokeh.backgroundDim * 0.62f * blurWeight
          r *= dim
          g *= dim
          b *= dim
        }

        // Atmospheric Depth Fog (cool mist weighted by far depth)
        if (hasFog) {
          val farWeight = ((1f - d) * 0.85f * bokeh.depthFog).coerceIn(0f, 0.78f)
          r = r * (1f - farWeight) + 216f * farWeight
          g = g * (1f - farWeight) + 230f * farWeight
          b = b * (1f - farWeight) + 240f * farWeight
        }

        out[idx] =
          Color.rgb(
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255),
          )
      }
    }

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(out, 0, w, 0, 0, w, h)
    return result
  }

  private fun computeBoxBlurPixels(
    src: IntArray,
    w: Int,
    h: Int,
    radius: Int,
    boostHighlights: Boolean,
  ): IntArray {
    // Downsample 2x for fast bokeh kernel sweep + 2 box passes
    val dw = (w / 2).coerceAtLeast(16)
    val dh = (h / 2).coerceAtLeast(16)
    val small = IntArray(dw * dh)
    for (y in 0 until dh) {
      val sy = (y * h / dh).coerceIn(0, h - 1)
      for (x in 0 until dw) {
        val sx = (x * w / dw).coerceIn(0, w - 1)
        small[y * dw + x] = src[sy * w + sx]
      }
    }

    val r = (radius / 2).coerceIn(1, 10)
    val horiz = IntArray(dw * dh)
    val vert = IntArray(dw * dh)

    for (y in 0 until dh) {
      val rowOff = y * dw
      for (x in 0 until dw) {
        var sr = 0f
        var sg = 0f
        var sb = 0f
        var sw = 0f
        val x0 = (x - r).coerceAtLeast(0)
        val x1 = (x + r).coerceAtMost(dw - 1)
        for (kx in x0..x1) {
          val c = small[rowOff + kx]
          val cr = Color.red(c)
          val cg = Color.green(c)
          val cb = Color.blue(c)
          val weight = if (boostHighlights && (cr + cg + cb) > 610) 2.2f else 1f
          sr += cr * weight
          sg += cg * weight
          sb += cb * weight
          sw += weight
        }
        horiz[rowOff + x] =
          Color.rgb(
            (sr / sw).roundToInt().coerceIn(0, 255),
            (sg / sw).roundToInt().coerceIn(0, 255),
            (sb / sw).roundToInt().coerceIn(0, 255),
          )
      }
    }

    for (x in 0 until dw) {
      for (y in 0 until dh) {
        var sr = 0f
        var sg = 0f
        var sb = 0f
        var sw = 0f
        val y0 = (y - r).coerceAtLeast(0)
        val y1 = (y + r).coerceAtMost(dh - 1)
        for (ky in y0..y1) {
          val c = horiz[ky * dw + x]
          val cr = Color.red(c)
          val cg = Color.green(c)
          val cb = Color.blue(c)
          val weight = if (boostHighlights && (cr + cg + cb) > 610) 1.6f else 1f
          sr += cr * weight
          sg += cg * weight
          sb += cb * weight
          sw += weight
        }
        vert[y * dw + x] =
          Color.rgb(
            (sr / sw).roundToInt().coerceIn(0, 255),
            (sg / sw).roundToInt().coerceIn(0, 255),
            (sb / sw).roundToInt().coerceIn(0, 255),
          )
      }
    }

    // Upsample smoothly with bilinear interpolation
    val out = IntArray(w * h)
    for (y in 0 until h) {
      val fy = y.toFloat() * (dh - 1) / (h - 1).coerceAtLeast(1)
      val y0 = fy.toInt().coerceIn(0, dh - 1)
      val y1 = (y0 + 1).coerceAtMost(dh - 1)
      val ty = fy - y0
      for (x in 0 until w) {
        val fx = x.toFloat() * (dw - 1) / (w - 1).coerceAtLeast(1)
        val x0 = fx.toInt().coerceIn(0, dw - 1)
        val x1 = (x0 + 1).coerceAtMost(dw - 1)
        val tx = fx - x0

        val c00 = vert[y0 * dw + x0]
        val c10 = vert[y0 * dw + x1]
        val c01 = vert[y1 * dw + x0]
        val c11 = vert[y1 * dw + x1]

        val cr =
          bilerp(Color.red(c00), Color.red(c10), Color.red(c01), Color.red(c11), tx, ty)
        val cg =
          bilerp(Color.green(c00), Color.green(c10), Color.green(c01), Color.green(c11), tx, ty)
        val cb =
          bilerp(Color.blue(c00), Color.blue(c10), Color.blue(c01), Color.blue(c11), tx, ty)
        out[y * w + x] = Color.rgb(cr, cg, cb)
      }
    }
    return out
  }

  private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
    val top = c00 + (c10 - c00) * tx
    val bot = c01 + (c11 - c01) * tx
    return (top + (bot - top) * ty).roundToInt().coerceIn(0, 255)
  }

  private fun heatmapColor(d: Float): Int {
    val t = d.coerceIn(0f, 1f)
    // Dark indigo (far) -> Teal -> Emerald -> Amber -> Bright Cream (near)
    return when {
      t < 0.25f -> lerpColor(0xFF1E1B4B.toInt(), 0xFF0E7490.toInt(), t / 0.25f)
      t < 0.50f -> lerpColor(0xFF0E7490.toInt(), 0xFF10B981.toInt(), (t - 0.25f) / 0.25f)
      t < 0.78f -> lerpColor(0xFF10B981.toInt(), 0xFFF59E0B.toInt(), (t - 0.50f) / 0.28f)
      else -> lerpColor(0xFFF59E0B.toInt(), 0xFFFEF3C7.toInt(), (t - 0.78f) / 0.22f)
    }
  }

  private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * k).roundToInt()
    val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * k).roundToInt()
    val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).roundToInt()
    return Color.rgb(r, g, bl)
  }

  private fun applyToneAndFilter(bitmap: Bitmap, recipe: PhotoEditRecipe) {
    val adj = recipe.adjust
    val cm = ColorMatrix()

    // Exposure + Brightness
    val expScale = 1f + adj.exposure * 0.55f
    val brightOff = adj.brightness * 65f + adj.fade * 24f
    val contrastFactor = (1f + adj.contrast * 0.6f) * (1f - adj.fade * 0.16f)
    val contrastTranslate = (-0.5f * contrastFactor + 0.5f) * 255f

    val toneMat =
      ColorMatrix(
        floatArrayOf(
          expScale * contrastFactor, 0f, 0f, 0f, brightOff + contrastTranslate,
          0f, expScale * contrastFactor, 0f, 0f, brightOff + contrastTranslate,
          0f, 0f, expScale * contrastFactor, 0f, brightOff + contrastTranslate,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    cm.postConcat(toneMat)

    // Saturation + Vibrancy
    val sat = (1f + adj.saturation + adj.vibrancy * 0.45f).coerceIn(0f, 2.2f)
    cm.postConcat(ColorMatrix().apply { setSaturation(sat) })

    // Warmth & Tint
    if (abs(adj.warmth) > 0.01f || abs(adj.tint) > 0.01f) {
      val rGain = 1f + adj.warmth * 0.18f + adj.tint * 0.06f
      val gGain = 1f - adj.tint * 0.14f
      val bGain = 1f - adj.warmth * 0.18f + adj.tint * 0.06f
      cm.postConcat(
        ColorMatrix(
          floatArrayOf(
            rGain, 0f, 0f, 0f, adj.warmth * 12f,
            0f, gGain, 0f, 0f, -adj.tint * 8f,
            0f, 0f, bGain, 0f, -adj.warmth * 10f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
    }

    // Filter Look blended by intensity
    if (recipe.filterLook != FilterLook.NATURAL && recipe.filterIntensity > 0.01f) {
      val filterArr = filterMatrixArray(recipe.filterLook)
      val identityArr =
        floatArrayOf(
          1f, 0f, 0f, 0f, 0f,
          0f, 1f, 0f, 0f, 0f,
          0f, 0f, 1f, 0f, 0f,
          0f, 0f, 0f, 1f, 0f,
        )
      val t = recipe.filterIntensity.coerceIn(0f, 1f)
      val blended = FloatArray(20) { i -> identityArr[i] + (filterArr[i] - identityArr[i]) * t }
      cm.postConcat(ColorMatrix(blended))
    }

    val m = cm.array
    val count = bitmap.width * bitmap.height
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    val hasCurve = abs(adj.highlights) > 0.02f || abs(adj.shadows) > 0.02f
    val hlShift = adj.highlights * 42f
    val shShift = adj.shadows * 48f

    for (i in 0 until count) {
      val c = pixels[i]
      val r0 = Color.red(c).toFloat()
      val g0 = Color.green(c).toFloat()
      val b0 = Color.blue(c).toFloat()

      var r = m[0] * r0 + m[1] * g0 + m[2] * b0 + m[4]
      var g = m[5] * r0 + m[6] * g0 + m[7] * b0 + m[9]
      var b = m[10] * r0 + m[11] * g0 + m[12] * b0 + m[14]

      if (hasCurve) {
        val rc = r.coerceIn(0f, 255f)
        val gc = g.coerceIn(0f, 255f)
        val bc = b.coerceIn(0f, 255f)
        val luma = (0.299f * rc + 0.587f * gc + 0.114f * bc) / 255f
        val shadowMask = (1f - luma) * (1f - luma)
        val highMask = luma * luma
        val delta = shShift * shadowMask + hlShift * highMask
        r = rc + delta
        g = gc + delta
        b = bc + delta
      }

      pixels[i] =
        Color.rgb(
          r.roundToInt().coerceIn(0, 255),
          g.roundToInt().coerceIn(0, 255),
          b.roundToInt().coerceIn(0, 255),
        )
    }
    bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }

  private fun filterMatrixArray(look: FilterLook): FloatArray =
    when (look) {
      FilterLook.NATURAL ->
        floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
      FilterLook.BOTANICAL_VIVID ->
        ColorMatrix().apply { setSaturation(1.42f) }.array
      FilterLook.WARM_FILM ->
        floatArrayOf(
          1.12f, 0.06f, 0f, 0f, 12f,
          0.02f, 1.04f, 0f, 0f, 5f,
          0f, 0f, 0.84f, 0f, -8f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.MONOCHROME -> ColorMatrix().apply { setSaturation(0f) }.array
      FilterLook.TEAL_ORANGE ->
        floatArrayOf(
          1.16f, -0.04f, -0.04f, 0f, 6f,
          0f, 1.02f, 0.08f, 0f, 2f,
          -0.06f, 0.10f, 1.12f, 0f, 4f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.NOIR_CONTRAST ->
        floatArrayOf(
          0.42f, 0.82f, 0.16f, 0f, -38f,
          0.42f, 0.82f, 0.16f, 0f, -38f,
          0.42f, 0.82f, 0.16f, 0f, -38f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.GOLDEN_HOUR ->
        floatArrayOf(
          1.18f, 0.08f, 0f, 0f, 14f,
          0.04f, 1.05f, 0f, 0f, 4f,
          0f, 0f, 0.78f, 0f, -10f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.CYBER_MINT ->
        floatArrayOf(
          0.88f, 0.04f, 0.08f, 0f, -6f,
          0.04f, 1.16f, 0.06f, 0f, 8f,
          0.02f, 0.12f, 1.14f, 0f, 6f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.MATTE_FADE ->
        floatArrayOf(
          0.92f, 0.04f, 0f, 0f, 18f,
          0f, 0.90f, 0.02f, 0f, 16f,
          0f, 0.04f, 0.92f, 0f, 22f,
          0f, 0f, 0f, 1f, 0f,
        )
      FilterLook.PORTRA_SOFT ->
        floatArrayOf(
          1.06f, 0.04f, 0.02f, 0f, 8f,
          0.02f, 1.03f, 0.02f, 0f, 6f,
          0f, 0.02f, 0.96f, 0f, 6f,
          0f, 0f, 0f, 1f, 0f,
        )
    }

  private fun applySharpen(bitmap: Bitmap, amount: Float) {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 4 || h < 4) return
    val src = IntArray(w * h)
    val dst = src.copyOf()
    bitmap.getPixels(src, 0, w, 0, 0, w, h)
    val strength = amount.coerceIn(0f, 1f) * 1.15f

    for (y in 1 until h - 1) {
      val row = y * w
      for (x in 1 until w - 1) {
        val c = src[row + x]
        val n = src[row - w + x]
        val s = src[row + w + x]
        val wPx = src[row + x - 1]
        val ePx = src[row + x + 1]

        val avgR = (Color.red(n) + Color.red(s) + Color.red(wPx) + Color.red(ePx)) * 0.25f
        val avgG = (Color.green(n) + Color.green(s) + Color.green(wPx) + Color.green(ePx)) * 0.25f
        val avgB = (Color.blue(n) + Color.blue(s) + Color.blue(wPx) + Color.blue(ePx)) * 0.25f

        val r = Color.red(c) + (Color.red(c) - avgR) * strength
        val g = Color.green(c) + (Color.green(c) - avgG) * strength
        val b = Color.blue(c) + (Color.blue(c) - avgB) * strength
        dst[row + x] =
          Color.rgb(
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255),
          )
      }
    }
    bitmap.setPixels(dst, 0, w, 0, 0, w, h)
  }

  private fun applyVignette(bitmap: Bitmap, amount: Float) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val radius = max(w, h) * 0.72f
    val edgeAlpha = (amount.coerceIn(0f, 1f) * 210f).roundToInt()
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader =
          RadialGradient(
            w / 2f,
            h / 2f,
            radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((edgeAlpha * 0.35f).roundToInt(), 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0)),
            floatArrayOf(0.45f, 0.76f, 1f),
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRect(0f, 0f, w, h, paint)
  }

  private fun applyFilmGrain(bitmap: Bitmap, amount: Float) {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val maxNoise = (amount.coerceIn(0f, 1f) * 34f).roundToInt()
    if (maxNoise <= 0) return

    var seed = 0x517CC1B7
    for (i in pixels.indices) {
      seed = seed * 1664525 + 1013904223
      val noise = (((seed shr 24) and 0xFF) - 128) * maxNoise / 128
      val c = pixels[i]
      pixels[i] =
        Color.rgb(
          (Color.red(c) + noise).coerceIn(0, 255),
          (Color.green(c) + noise).coerceIn(0, 255),
          (Color.blue(c) + noise).coerceIn(0, 255),
        )
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
  }

  private fun applyMarkupAndPrivacy(bitmap: Bitmap, recipe: PhotoEditRecipe) {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val minDim = min(w, h)

    // Apply pixelate privacy mosaic strokes directly onto bitmap pixels first
    val privacyStrokes = recipe.strokes.filter { it.tool == MarkupToolType.PRIVACY_BLUR }
    if (privacyStrokes.isNotEmpty()) {
      applyPrivacyMosaicStrokes(bitmap, privacyStrokes)
    }

    val canvas = Canvas(bitmap)
    recipe.strokes.forEach { stroke ->
      if (stroke.tool == MarkupToolType.PRIVACY_BLUR || stroke.points.isEmpty()) return@forEach
      val strokePx = (stroke.strokeWidthNorm * minDim).coerceIn(3f, 52f)
      val baseColor = stroke.colorHex.toInt()
      val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeCap = Paint.Cap.ROUND
          strokeJoin = Paint.Join.ROUND
          strokeWidth =
            if (stroke.tool == MarkupToolType.HIGHLIGHTER) strokePx * 2.2f else strokePx
          color =
            if (stroke.tool == MarkupToolType.HIGHLIGHTER) {
              Color.argb(105, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            } else {
              baseColor
            }
        }

      when (stroke.tool) {
        MarkupToolType.PEN,
        MarkupToolType.HIGHLIGHTER -> {
          val path = Path()
          stroke.points.forEachIndexed { idx, pt ->
            val px = pt.x * w
            val py = pt.y * h
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
          }
          canvas.drawPath(path, paint)
        }
        MarkupToolType.RECT -> {
          val p0 = stroke.points.first()
          val p1 = stroke.points.last()
          val rect =
            RectF(
              min(p0.x, p1.x) * w,
              min(p0.y, p1.y) * h,
              max(p0.x, p1.x) * w,
              max(p0.y, p1.y) * h,
            )
          canvas.drawRoundRect(rect, 12f, 12f, paint)
        }
        MarkupToolType.CIRCLE -> {
          val p0 = stroke.points.first()
          val p1 = stroke.points.last()
          val rect =
            RectF(
              min(p0.x, p1.x) * w,
              min(p0.y, p1.y) * h,
              max(p0.x, p1.x) * w,
              max(p0.y, p1.y) * h,
            )
          canvas.drawOval(rect, paint)
        }
        MarkupToolType.ARROW -> {
          val p0 = stroke.points.first()
          val p1 = stroke.points.last()
          val sx = p0.x * w
          val sy = p0.y * h
          val ex = p1.x * w
          val ey = p1.y * h
          canvas.drawLine(sx, sy, ex, ey, paint)
          val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
          val headLen = max(strokePx * 3.2f, minDim * 0.04f)
          val a1 = angle - Math.toRadians(28.0)
          val a2 = angle + Math.toRadians(28.0)
          canvas.drawLine(
            ex,
            ey,
            (ex - headLen * cos(a1)).toFloat(),
            (ey - headLen * sin(a1)).toFloat(),
            paint,
          )
          canvas.drawLine(
            ex,
            ey,
            (ex - headLen * cos(a2)).toFloat(),
            (ey - headLen * sin(a2)).toFloat(),
            paint,
          )
        }
        MarkupToolType.PRIVACY_BLUR,
        MarkupToolType.TEXT -> Unit
      }
    }

    // Draw text overlays
    recipe.textItems.forEach { item ->
      val cx = item.x.coerceIn(0.05f, 0.95f) * w
      val cy = item.y.coerceIn(0.05f, 0.95f) * h
      val textSizePx = (minDim * 0.058f * item.scale).coerceIn(22f, 120f)
      val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = item.colorHex.toInt()
          textSize = textSizePx
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
          textAlign = Paint.Align.CENTER
        }
      val measuredW = textPaint.measureText(item.text)
      val fm = textPaint.fontMetrics
      val boxH = fm.descent - fm.ascent
      if (item.hasPillBackdrop) {
        val bgPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 10, 18, 14)
            style = Paint.Style.FILL
          }
        val padH = textSizePx * 0.48f
        val padV = textSizePx * 0.24f
        val rect =
          RectF(
            cx - measuredW / 2f - padH,
            cy - boxH / 2f - padV,
            cx + measuredW / 2f + padH,
            cy + boxH / 2f + padV,
          )
        canvas.drawRoundRect(rect, 99f, 99f, bgPaint)
      }
      val baseline = cy - (fm.ascent + fm.descent) / 2f
      canvas.drawText(item.text, cx, baseline, textPaint)
    }
  }

  private fun applyPrivacyMosaicStrokes(bitmap: Bitmap, strokes: List<MarkupStroke>) {
    val w = bitmap.width
    val h = bitmap.height
    val block = max(10, min(w, h) / 48)
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = pixels.copyOf()

    strokes.forEach { stroke ->
      val radiusPx = (stroke.strokeWidthNorm * min(w, h) * 1.6f).coerceIn(14f, 72f)
      stroke.points.forEach { pt ->
        val cx = (pt.x * w).roundToInt()
        val cy = (pt.y * h).roundToInt()
        val rInt = radiusPx.roundToInt()
        val xStart = (cx - rInt).coerceAtLeast(0)
        val xEnd = (cx + rInt).coerceAtMost(w - 1)
        val yStart = (cy - rInt).coerceAtLeast(0)
        val yEnd = (cy + rInt).coerceAtMost(h - 1)
        for (y in yStart..yEnd) {
          val snapY = ((y / block) * block + block / 2).coerceIn(0, h - 1)
          for (x in xStart..xEnd) {
            if (hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radiusPx) {
              val snapX = ((x / block) * block + block / 2).coerceIn(0, w - 1)
              out[y * w + x] = pixels[snapY * w + snapX]
            }
          }
        }
      }
    }
    bitmap.setPixels(out, 0, w, 0, 0, w, h)
  }
}
