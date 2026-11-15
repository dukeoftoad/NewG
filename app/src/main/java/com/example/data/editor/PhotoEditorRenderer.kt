package com.example.data.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
    val hasPixels =
      runCatching {
          if (bmp.width > 0 && bmp.height > 0) {
            bmp.getPixels(IntArray(1), 0, 1, 0, 0, 1, 1)
          }
          true
        }
        .getOrDefault(false)
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
    val resolver = context.contentResolver
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDim)
    val decodeOpts =
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded =
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: return null

    // Apply EXIF orientation so rotated camera captures open upright in the editor
    val orientation =
      runCatching {
          resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          } ?: ExifInterface.ORIENTATION_NORMAL
        }
        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.setRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.setRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      else -> return decoded
    }
    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
    return oriented
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
      val invH = 1f / (h - 1).coerceAtLeast(1)
      val invW = 1f / (w - 1).coerceAtLeast(1)
      val isGray = bokeh.depthPreviewMode == DepthPreviewMode.GRAYSCALE
      for (y in 0 until h) {
        val ny = y * invH
        val rowOff = y * w
        for (x in 0 until w) {
          val nx = x * invW
          val d = depthMap.sampleAt(nx, ny)
          out[rowOff + x] =
            if (isGray) {
              val v = (d * 255f + 0.5f).toInt().coerceIn(0, 255)
              (0xFF shl 24) or (v shl 16) or (v shl 8) or v
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
    val rangeHalf = range * 0.45f
    val invRangeSpan = 1f / (range * 1.4f + 0.08f)
    val hasPop = bokeh.foregroundColorPop > 0.01f
    val hasFog = bokeh.depthFog > 0.01f
    val hasRelight = abs(bokeh.subjectRelight) > 0.01f
    val hasBgDim = bokeh.backgroundDim > 0.01f
    val invH = 1f / (h - 1).coerceAtLeast(1)
    val invW = 1f / (w - 1).coerceAtLeast(1)

    for (y in 0 until h) {
      val ny = y * invH
      val rowOff = y * w
      for (x in 0 until w) {
        val idx = rowOff + x
        val nx = x * invW
        val d = depthMap.sampleAt(nx, ny)
        val delta = abs(d - focalD)
        val blurWeight = ((delta - rangeHalf) * invRangeSpan).coerceIn(0f, 1f)
        val fgWeight = 1f - blurWeight

        val sharp = pixels[idx]
        val blur = blurredPixels[idx]

        val sr = (sharp ushr 16) and 0xFF
        val sg = (sharp ushr 8) and 0xFF
        val sb = sharp and 0xFF

        val br = (blur ushr 16) and 0xFF
        val bg = (blur ushr 8) and 0xFF
        val bb = blur and 0xFF

        var r = sr * fgWeight + br * blurWeight
        var g = sg * fgWeight + bg * blurWeight
        var b = sb * fgWeight + bb * blurWeight

        // Foreground Color Pop (keeps focal plane color, desaturates background)
        if (hasPop && blurWeight > 0.01f) {
          val luma = 0.299f * r + 0.587f * g + 0.114f * b
          val desatAmount = (blurWeight * bokeh.foregroundColorPop).coerceIn(0f, 1f)
          r += (luma - r) * desatAmount
          g += (luma - g) * desatAmount
          b += (luma - b) * desatAmount
        }

        // Subject Relight (brightens/sculpts focal plane)
        if (hasRelight) {
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
          val invFar = 1f - farWeight
          r = r * invFar + 216f * farWeight
          g = g * invFar + 230f * farWeight
          b = b * invFar + 240f * farWeight
        }

        val ri = (r + 0.5f).toInt().coerceIn(0, 255)
        val gi = (g + 0.5f).toInt().coerceIn(0, 255)
        val bi = (b + 0.5f).toInt().coerceIn(0, 255)
        out[idx] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
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
      val srcRow = sy * w
      val dstRow = y * dw
      for (x in 0 until dw) {
        val sx = (x * w / dw).coerceIn(0, w - 1)
        small[dstRow + x] = src[srcRow + sx]
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
          val cr = (c ushr 16) and 0xFF
          val cg = (c ushr 8) and 0xFF
          val cb = c and 0xFF
          val weight = if (boostHighlights && (cr + cg + cb) > 610) 2.2f else 1f
          sr += cr * weight
          sg += cg * weight
          sb += cb * weight
          sw += weight
        }
        val invW = 1f / sw
        val ri = (sr * invW + 0.5f).toInt().coerceIn(0, 255)
        val gi = (sg * invW + 0.5f).toInt().coerceIn(0, 255)
        val bi = (sb * invW + 0.5f).toInt().coerceIn(0, 255)
        horiz[rowOff + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
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
          val cr = (c ushr 16) and 0xFF
          val cg = (c ushr 8) and 0xFF
          val cb = c and 0xFF
          val weight = if (boostHighlights && (cr + cg + cb) > 610) 1.6f else 1f
          sr += cr * weight
          sg += cg * weight
          sb += cb * weight
          sw += weight
        }
        val invW = 1f / sw
        val ri = (sr * invW + 0.5f).toInt().coerceIn(0, 255)
        val gi = (sg * invW + 0.5f).toInt().coerceIn(0, 255)
        val bi = (sb * invW + 0.5f).toInt().coerceIn(0, 255)
        vert[y * dw + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
      }
    }

    // Upsample smoothly with bilinear interpolation
    val out = IntArray(w * h)
    val scaleY = (dh - 1).toFloat() / (h - 1).coerceAtLeast(1)
    val scaleX = (dw - 1).toFloat() / (w - 1).coerceAtLeast(1)
    for (y in 0 until h) {
      val fy = y * scaleY
      val y0 = fy.toInt().coerceIn(0, dh - 1)
      val y1 = (y0 + 1).coerceAtMost(dh - 1)
      val ty = fy - y0
      val row0 = y0 * dw
      val row1 = y1 * dw
      val outRow = y * w
      for (x in 0 until w) {
        val fx = x * scaleX
        val x0 = fx.toInt().coerceIn(0, dw - 1)
        val x1 = (x0 + 1).coerceAtMost(dw - 1)
        val tx = fx - x0

        val c00 = vert[row0 + x0]
        val c10 = vert[row0 + x1]
        val c01 = vert[row1 + x0]
        val c11 = vert[row1 + x1]

        val cr =
          bilerp(
            (c00 ushr 16) and 0xFF,
            (c10 ushr 16) and 0xFF,
            (c01 ushr 16) and 0xFF,
            (c11 ushr 16) and 0xFF,
            tx,
            ty,
          )
        val cg =
          bilerp(
            (c00 ushr 8) and 0xFF,
            (c10 ushr 8) and 0xFF,
            (c01 ushr 8) and 0xFF,
            (c11 ushr 8) and 0xFF,
            tx,
            ty,
          )
        val cb = bilerp(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, tx, ty)
        out[outRow + x] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
      }
    }
    return out
  }

  private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
    val top = c00 + (c10 - c00) * tx
    val bot = c01 + (c11 - c01) * tx
    return (top + (bot - top) * ty + 0.5f).toInt().coerceIn(0, 255)
  }

  private fun heatmapColor(d: Float): Int {
    val t = d.coerceIn(0f, 1f)
    return when {
      t < 0.25f -> lerpColor(0xFF1E1B4B.toInt(), 0xFF0E7490.toInt(), t / 0.25f)
      t < 0.50f -> lerpColor(0xFF0E7490.toInt(), 0xFF10B981.toInt(), (t - 0.25f) / 0.25f)
      t < 0.78f -> lerpColor(0xFF10B981.toInt(), 0xFFF59E0B.toInt(), (t - 0.50f) / 0.28f)
      else -> lerpColor(0xFFF59E0B.toInt(), 0xFFFEF3C7.toInt(), (t - 0.78f) / 0.22f)
    }
  }

  private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val ar = (a ushr 16) and 0xFF
    val ag = (a ushr 8) and 0xFF
    val ab = a and 0xFF
    val br = (b ushr 16) and 0xFF
    val bg = (b ushr 8) and 0xFF
    val bb = b and 0xFF
    val r = (ar + (br - ar) * k + 0.5f).toInt().coerceIn(0, 255)
    val g = (ag + (bg - ag) * k + 0.5f).toInt().coerceIn(0, 255)
    val bl = (ab + (bb - ab) * k + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
  }

  private fun applyToneAndFilter(bitmap: Bitmap, recipe: PhotoEditRecipe) {
    val adj = recipe.adjust
    val cm = ColorMatrix()

    // Exposure + Brightness + Contrast + Fade
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

    val hasCurve = abs(adj.highlights) > 0.02f || abs(adj.shadows) > 0.02f

    // Fast path: apply ColorMatrix via native Skia Canvas when no non-linear highlight/shadow curve is active
    if (!hasCurve) {
      val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
      Canvas(bitmap).drawBitmap(bitmap, 0f, 0f, paint)
      return
    }

    val m = cm.array
    val m0 = m[0]; val m1 = m[1]; val m2 = m[2]; val m4 = m[4]
    val m5 = m[5]; val m6 = m[6]; val m7 = m[7]; val m9 = m[9]
    val m10 = m[10]; val m11 = m[11]; val m12 = m[12]; val m14 = m[14]

    // Precompute 256-entry integer LUT for shadow/highlight luma curve
    val hlShift = adj.highlights * 42f
    val shShift = adj.shadows * 48f
    val curveDeltaLut =
      IntArray(256) { lumaInt ->
        val luma = lumaInt / 255f
        val shadowMask = (1f - luma) * (1f - luma)
        val highMask = luma * luma
        (shShift * shadowMask + hlShift * highMask).roundToInt()
      }

    val count = bitmap.width * bitmap.height
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    for (i in 0 until count) {
      val c = pixels[i]
      val r0 = ((c ushr 16) and 0xFF).toFloat()
      val g0 = ((c ushr 8) and 0xFF).toFloat()
      val b0 = (c and 0xFF).toFloat()

      val rc = (m0 * r0 + m1 * g0 + m2 * b0 + m4).roundToInt().coerceIn(0, 255)
      val gc = (m5 * r0 + m6 * g0 + m7 * b0 + m9).roundToInt().coerceIn(0, 255)
      val bc = (m10 * r0 + m11 * g0 + m12 * b0 + m14).roundToInt().coerceIn(0, 255)

      val lumaIdx = ((77 * rc + 150 * gc + 29 * bc) ushr 8).coerceIn(0, 255)
      val delta = curveDeltaLut[lumaIdx]
      val r = (rc + delta).coerceIn(0, 255)
      val g = (gc + delta).coerceIn(0, 255)
      val b = (bc + delta).coerceIn(0, 255)

      pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
    bitmap.getPixels(src, 0, w, 0, 0, w, h)
    val dst = src.copyOf()
    val strengthFixed = (amount.coerceIn(0f, 1f) * 1.15f * 256f).roundToInt()

    for (y in 1 until h - 1) {
      val row = y * w
      for (x in 1 until w - 1) {
        val c = src[row + x]
        val n = src[row - w + x]
        val s = src[row + w + x]
        val wPx = src[row + x - 1]
        val ePx = src[row + x + 1]

        val cr = (c ushr 16) and 0xFF
        val cg = (c ushr 8) and 0xFF
        val cb = c and 0xFF

        val avgR = (((n ushr 16) and 0xFF) + ((s ushr 16) and 0xFF) + ((wPx ushr 16) and 0xFF) + ((ePx ushr 16) and 0xFF)) ushr 2
        val avgG = (((n ushr 8) and 0xFF) + ((s ushr 8) and 0xFF) + ((wPx ushr 8) and 0xFF) + ((ePx ushr 8) and 0xFF)) ushr 2
        val avgB = ((n and 0xFF) + (s and 0xFF) + (wPx and 0xFF) + (ePx and 0xFF)) ushr 2

        val r = (cr + (((cr - avgR) * strengthFixed) shr 8)).coerceIn(0, 255)
        val g = (cg + (((cg - avgG) * strengthFixed) shr 8)).coerceIn(0, 255)
        val b = (cb + (((cb - avgB) * strengthFixed) shr 8)).coerceIn(0, 255)
        dst[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
            intArrayOf(
              Color.TRANSPARENT,
              Color.argb((edgeAlpha * 0.35f).roundToInt(), 0, 0, 0),
              Color.argb(edgeAlpha, 0, 0, 0),
            ),
            floatArrayOf(0.45f, 0.76f, 1f),
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRect(0f, 0f, w, h, paint)
  }

  private fun applyFilmGrain(bitmap: Bitmap, amount: Float) {
    val w = bitmap.width
    val h = bitmap.height
    val maxNoise = (amount.coerceIn(0f, 1f) * 34f).roundToInt()
    if (maxNoise <= 0) return

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    var seed = 0x517CC1B7
    for (i in pixels.indices) {
      seed = seed * 1664525 + 1013904223
      val noise = (((seed ushr 24) and 0xFF) - 128) * maxNoise shr 7
      val c = pixels[i]
      val r = (((c ushr 16) and 0xFF) + noise).coerceIn(0, 255)
      val g = (((c ushr 8) and 0xFF) + noise).coerceIn(0, 255)
      val b = ((c and 0xFF) + noise).coerceIn(0, 255)
      pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
      val rSq = radiusPx * radiusPx
      val rInt = radiusPx.roundToInt()
      stroke.points.forEach { pt ->
        val cx = (pt.x * w).roundToInt()
        val cy = (pt.y * h).roundToInt()
        val xStart = (cx - rInt).coerceAtLeast(0)
        val xEnd = (cx + rInt).coerceAtMost(w - 1)
        val yStart = (cy - rInt).coerceAtLeast(0)
        val yEnd = (cy + rInt).coerceAtMost(h - 1)
        for (y in yStart..yEnd) {
          val dy = (y - cy).toFloat()
          val dySq = dy * dy
          val snapY = ((y / block) * block + block / 2).coerceIn(0, h - 1)
          val snapRow = snapY * w
          val outRow = y * w
          for (x in xStart..xEnd) {
            val dx = (x - cx).toFloat()
            if (dx * dx + dySq <= rSq) {
              val snapX = ((x / block) * block + block / 2).coerceIn(0, w - 1)
              out[outRow + x] = pixels[snapRow + snapX]
            }
          }
        }
      }
    }
    bitmap.setPixels(out, 0, w, 0, 0, w, h)
  }
}
