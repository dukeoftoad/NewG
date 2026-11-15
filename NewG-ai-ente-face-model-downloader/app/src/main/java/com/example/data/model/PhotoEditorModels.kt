package com.example.data.model

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

enum class EditorToolTab(val label: String) {
  ADJUST("Adjust"),
  FILTERS("Filters"),
  CROP("Crop"),
  BOKEH("Bokeh"),
  MARKUP("Markup"),
}

enum class AdjustParam(
  val label: String,
  val min: Float,
  val max: Float,
  val defaultValue: Float,
) {
  EXPOSURE("Exposure", -1f, 1f, 0f),
  BRIGHTNESS("Brightness", -1f, 1f, 0f),
  CONTRAST("Contrast", -1f, 1f, 0f),
  SATURATION("Saturation", -1f, 1f, 0f),
  VIBRANCY("Vibrancy", -1f, 1f, 0f),
  WARMTH("Warmth", -1f, 1f, 0f),
  TINT("Tint", -1f, 1f, 0f),
  HIGHLIGHTS("Highlights", -1f, 1f, 0f),
  SHADOWS("Shadows", -1f, 1f, 0f),
  SHARPEN("Sharpen", 0f, 1f, 0f),
  VIGNETTE("Vignette", 0f, 1f, 0f),
  FADE("Fade", 0f, 1f, 0f),
}

@Immutable
data class AdjustParams(
  val exposure: Float = 0f,
  val brightness: Float = 0f,
  val contrast: Float = 0f,
  val saturation: Float = 0f,
  val vibrancy: Float = 0f,
  val warmth: Float = 0f,
  val tint: Float = 0f,
  val highlights: Float = 0f,
  val shadows: Float = 0f,
  val sharpen: Float = 0f,
  val vignette: Float = 0f,
  val fade: Float = 0f,
) {
  fun valueOf(param: AdjustParam): Float =
    when (param) {
      AdjustParam.EXPOSURE -> exposure
      AdjustParam.BRIGHTNESS -> brightness
      AdjustParam.CONTRAST -> contrast
      AdjustParam.SATURATION -> saturation
      AdjustParam.VIBRANCY -> vibrancy
      AdjustParam.WARMTH -> warmth
      AdjustParam.TINT -> tint
      AdjustParam.HIGHLIGHTS -> highlights
      AdjustParam.SHADOWS -> shadows
      AdjustParam.SHARPEN -> sharpen
      AdjustParam.VIGNETTE -> vignette
      AdjustParam.FADE -> fade
    }

  fun withParam(param: AdjustParam, value: Float): AdjustParams {
    val clamped = value.coerceIn(param.min, param.max)
    return when (param) {
      AdjustParam.EXPOSURE -> copy(exposure = clamped)
      AdjustParam.BRIGHTNESS -> copy(brightness = clamped)
      AdjustParam.CONTRAST -> copy(contrast = clamped)
      AdjustParam.SATURATION -> copy(saturation = clamped)
      AdjustParam.VIBRANCY -> copy(vibrancy = clamped)
      AdjustParam.WARMTH -> copy(warmth = clamped)
      AdjustParam.TINT -> copy(tint = clamped)
      AdjustParam.HIGHLIGHTS -> copy(highlights = clamped)
      AdjustParam.SHADOWS -> copy(shadows = clamped)
      AdjustParam.SHARPEN -> copy(sharpen = clamped)
      AdjustParam.VIGNETTE -> copy(vignette = clamped)
      AdjustParam.FADE -> copy(fade = clamped)
    }
  }

  val isDefault: Boolean
    get() = AdjustParam.entries.all { kotlin.math.abs(valueOf(it) - it.defaultValue) < 0.001f }
}

enum class FilterLook(val label: String) {
  NATURAL("Natural"),
  BOTANICAL_VIVID("Botanical"),
  WARM_FILM("Warm Film"),
  MONOCHROME("B&W Mono"),
  TEAL_ORANGE("Teal & Amber"),
  NOIR_CONTRAST("Noir"),
  GOLDEN_HOUR("Golden Hour"),
  CYBER_MINT("Cyber Mint"),
  MATTE_FADE("Matte Fade"),
  PORTRA_SOFT("Portra Soft"),
}

enum class AspectRatioPreset(val label: String, val ratio: Float?) {
  FREE("Free", null),
  ORIGINAL("Original", null),
  SQUARE("1:1", 1f),
  FOUR_THREE("4:3", 4f / 3f),
  THREE_FOUR("3:4", 3f / 4f),
  SIXTEEN_NINE("16:9", 16f / 9f),
  NINE_SIXTEEN("9:16", 9f / 16f),
  FOUR_FIVE("4:5", 4f / 5f),
}

@Immutable
data class CropParams(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 1f,
  val bottom: Float = 1f,
  val aspectPreset: AspectRatioPreset = AspectRatioPreset.FREE,
  val straightenDegrees: Float = 0f,
  val rotationQuarterTurns: Int = 0,
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
) {
  val isDefault: Boolean
    get() =
      left <= 0.002f &&
        top <= 0.002f &&
        right >= 0.998f &&
        bottom >= 0.998f &&
        kotlin.math.abs(straightenDegrees) < 0.1f &&
        rotationQuarterTurns % 4 == 0 &&
        !flipHorizontal &&
        !flipVertical
}

enum class BokehShape(val label: String) {
  CIRCULAR("Circular"),
  CREAMY("Creamy"),
  HEXAGON("Hexagon"),
}

enum class DepthPreviewMode(val label: String) {
  OFF("Photo"),
  HEATMAP("Depth Heat"),
  GRAYSCALE("Depth B&W"),
}

@Immutable
data class BokehParams(
  val enabled: Boolean = false,
  val focusX: Float = 0.5f,
  val focusY: Float = 0.45f,
  val focusDepth: Float = 0.7f,
  val aperture: Float = 0.58f,
  val focusRange: Float = 0.22f,
  val bokehShape: BokehShape = BokehShape.CIRCULAR,
  val foregroundColorPop: Float = 0f,
  val depthFog: Float = 0f,
  val subjectRelight: Float = 0f,
  val backgroundDim: Float = 0f,
  val depthPreviewMode: DepthPreviewMode = DepthPreviewMode.OFF,
) {
  val fStopLabel: String
    get() {
      val f = 16f - aperture.coerceIn(0f, 1f) * 14.6f
      return String.format(java.util.Locale.US, "f/%.1f", f)
    }

  val isActive: Boolean
    get() =
      enabled &&
        (aperture > 0.01f ||
          foregroundColorPop > 0.01f ||
          depthFog > 0.01f ||
          kotlin.math.abs(subjectRelight) > 0.01f ||
          backgroundDim > 0.01f ||
          depthPreviewMode != DepthPreviewMode.OFF)
}

enum class MarkupToolType(val label: String) {
  PEN("Pen"),
  HIGHLIGHTER("Highlighter"),
  PRIVACY_BLUR("Privacy Blur"),
  ARROW("Arrow"),
  RECT("Rect"),
  CIRCLE("Circle"),
  TEXT("Text"),
}

@Immutable
data class NormalizedPoint(val x: Float, val y: Float)

@Immutable
data class MarkupStroke(
  val id: Long,
  val tool: MarkupToolType,
  val colorHex: Long,
  val strokeWidthNorm: Float,
  val points: List<NormalizedPoint>,
)

@Immutable
data class MarkupTextItem(
  val id: Long,
  val text: String,
  val x: Float,
  val y: Float,
  val colorHex: Long,
  val hasPillBackdrop: Boolean = true,
  val scale: Float = 1f,
)

@Immutable
data class PhotoEditRecipe(
  val adjust: AdjustParams = AdjustParams(),
  val filterLook: FilterLook = FilterLook.NATURAL,
  val filterIntensity: Float = 0.85f,
  val filmGrain: Float = 0f,
  val crop: CropParams = CropParams(),
  val bokeh: BokehParams = BokehParams(),
  val strokes: List<MarkupStroke> = emptyList(),
  val textItems: List<MarkupTextItem> = emptyList(),
) {
  val isIdentity: Boolean
    get() =
      adjust.isDefault &&
        filterLook == FilterLook.NATURAL &&
        filmGrain <= 0.005f &&
        crop.isDefault &&
        !bokeh.isActive &&
        strokes.isEmpty() &&
        textItems.isEmpty()

  fun toJsonString(): String {
    val root = JSONObject()
    root.put(
      "adjust",
      JSONObject().apply {
        put("exposure", adjust.exposure.toDouble())
        put("brightness", adjust.brightness.toDouble())
        put("contrast", adjust.contrast.toDouble())
        put("saturation", adjust.saturation.toDouble())
        put("vibrancy", adjust.vibrancy.toDouble())
        put("warmth", adjust.warmth.toDouble())
        put("tint", adjust.tint.toDouble())
        put("highlights", adjust.highlights.toDouble())
        put("shadows", adjust.shadows.toDouble())
        put("sharpen", adjust.sharpen.toDouble())
        put("vignette", adjust.vignette.toDouble())
        put("fade", adjust.fade.toDouble())
      },
    )
    root.put("filterLook", filterLook.name)
    root.put("filterIntensity", filterIntensity.toDouble())
    root.put("filmGrain", filmGrain.toDouble())
    root.put(
      "crop",
      JSONObject().apply {
        put("left", crop.left.toDouble())
        put("top", crop.top.toDouble())
        put("right", crop.right.toDouble())
        put("bottom", crop.bottom.toDouble())
        put("aspectPreset", crop.aspectPreset.name)
        put("straightenDegrees", crop.straightenDegrees.toDouble())
        put("rotationQuarterTurns", crop.rotationQuarterTurns)
        put("flipHorizontal", crop.flipHorizontal)
        put("flipVertical", crop.flipVertical)
      },
    )
    root.put(
      "bokeh",
      JSONObject().apply {
        put("enabled", bokeh.enabled)
        put("focusX", bokeh.focusX.toDouble())
        put("focusY", bokeh.focusY.toDouble())
        put("focusDepth", bokeh.focusDepth.toDouble())
        put("aperture", bokeh.aperture.toDouble())
        put("focusRange", bokeh.focusRange.toDouble())
        put("bokehShape", bokeh.bokehShape.name)
        put("foregroundColorPop", bokeh.foregroundColorPop.toDouble())
        put("depthFog", bokeh.depthFog.toDouble())
        put("subjectRelight", bokeh.subjectRelight.toDouble())
        put("backgroundDim", bokeh.backgroundDim.toDouble())
        put("depthPreviewMode", bokeh.depthPreviewMode.name)
      },
    )
    root.put(
      "strokes",
      JSONArray().apply {
        strokes.forEach { s ->
          put(
            JSONObject().apply {
              put("id", s.id)
              put("tool", s.tool.name)
              put("colorHex", s.colorHex)
              put("strokeWidthNorm", s.strokeWidthNorm.toDouble())
              put(
                "points",
                JSONArray().apply {
                  s.points.forEach { pt ->
                    put(
                      JSONObject().apply {
                        put("x", pt.x.toDouble())
                        put("y", pt.y.toDouble())
                      }
                    )
                  }
                },
              )
            }
          )
        }
      },
    )
    root.put(
      "textItems",
      JSONArray().apply {
        textItems.forEach { t ->
          put(
            JSONObject().apply {
              put("id", t.id)
              put("text", t.text)
              put("x", t.x.toDouble())
              put("y", t.y.toDouble())
              put("colorHex", t.colorHex)
              put("hasPillBackdrop", t.hasPillBackdrop)
              put("scale", t.scale.toDouble())
            }
          )
        }
      },
    )
    return root.toString()
  }

  companion object {
    fun fromJson(json: String?): PhotoEditRecipe {
      if (json.isNullOrBlank()) return PhotoEditRecipe()
      return runCatching {
          val root = JSONObject(json)
          val adjObj = root.optJSONObject("adjust")
          val adjust =
            if (adjObj != null) {
              AdjustParams(
                exposure = adjObj.optDouble("exposure", 0.0).toFloat(),
                brightness = adjObj.optDouble("brightness", 0.0).toFloat(),
                contrast = adjObj.optDouble("contrast", 0.0).toFloat(),
                saturation = adjObj.optDouble("saturation", 0.0).toFloat(),
                vibrancy = adjObj.optDouble("vibrancy", 0.0).toFloat(),
                warmth = adjObj.optDouble("warmth", 0.0).toFloat(),
                tint = adjObj.optDouble("tint", 0.0).toFloat(),
                highlights = adjObj.optDouble("highlights", 0.0).toFloat(),
                shadows = adjObj.optDouble("shadows", 0.0).toFloat(),
                sharpen = adjObj.optDouble("sharpen", 0.0).toFloat(),
                vignette = adjObj.optDouble("vignette", 0.0).toFloat(),
                fade = adjObj.optDouble("fade", 0.0).toFloat(),
              )
            } else AdjustParams()

          val filterLook =
            runCatching { FilterLook.valueOf(root.optString("filterLook")) }
              .getOrDefault(FilterLook.NATURAL)
          val filterIntensity = root.optDouble("filterIntensity", 0.85).toFloat()
          val filmGrain = root.optDouble("filmGrain", 0.0).toFloat()

          val cropObj = root.optJSONObject("crop")
          val crop =
            if (cropObj != null) {
              CropParams(
                left = cropObj.optDouble("left", 0.0).toFloat(),
                top = cropObj.optDouble("top", 0.0).toFloat(),
                right = cropObj.optDouble("right", 1.0).toFloat(),
                bottom = cropObj.optDouble("bottom", 1.0).toFloat(),
                aspectPreset =
                  runCatching { AspectRatioPreset.valueOf(cropObj.optString("aspectPreset")) }
                    .getOrDefault(AspectRatioPreset.FREE),
                straightenDegrees = cropObj.optDouble("straightenDegrees", 0.0).toFloat(),
                rotationQuarterTurns = cropObj.optInt("rotationQuarterTurns", 0),
                flipHorizontal = cropObj.optBoolean("flipHorizontal", false),
                flipVertical = cropObj.optBoolean("flipVertical", false),
              )
            } else CropParams()

          val bokehObj = root.optJSONObject("bokeh")
          val bokeh =
            if (bokehObj != null) {
              BokehParams(
                enabled = bokehObj.optBoolean("enabled", false),
                focusX = bokehObj.optDouble("focusX", 0.5).toFloat(),
                focusY = bokehObj.optDouble("focusY", 0.45).toFloat(),
                focusDepth = bokehObj.optDouble("focusDepth", 0.7).toFloat(),
                aperture = bokehObj.optDouble("aperture", 0.58).toFloat(),
                focusRange = bokehObj.optDouble("focusRange", 0.22).toFloat(),
                bokehShape =
                  runCatching { BokehShape.valueOf(bokehObj.optString("bokehShape")) }
                    .getOrDefault(BokehShape.CIRCULAR),
                foregroundColorPop = bokehObj.optDouble("foregroundColorPop", 0.0).toFloat(),
                depthFog = bokehObj.optDouble("depthFog", 0.0).toFloat(),
                subjectRelight = bokehObj.optDouble("subjectRelight", 0.0).toFloat(),
                backgroundDim = bokehObj.optDouble("backgroundDim", 0.0).toFloat(),
                depthPreviewMode =
                  runCatching { DepthPreviewMode.valueOf(bokehObj.optString("depthPreviewMode")) }
                    .getOrDefault(DepthPreviewMode.OFF),
              )
            } else BokehParams()

          val strokesArr = root.optJSONArray("strokes")
          val strokes =
            if (strokesArr != null) {
              buildList {
                for (i in 0 until strokesArr.length()) {
                  val sObj = strokesArr.optJSONObject(i) ?: continue
                  val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                  val pts = buildList {
                    for (j in 0 until ptsArr.length()) {
                      val p = ptsArr.optJSONObject(j) ?: continue
                      add(
                        NormalizedPoint(
                          p.optDouble("x", 0.0).toFloat(),
                          p.optDouble("y", 0.0).toFloat(),
                        )
                      )
                    }
                  }
                  add(
                    MarkupStroke(
                      id = sObj.optLong("id", i.toLong()),
                      tool =
                        runCatching { MarkupToolType.valueOf(sObj.optString("tool")) }
                          .getOrDefault(MarkupToolType.PEN),
                      colorHex = sObj.optLong("colorHex", 0xFF6EE7B7),
                      strokeWidthNorm = sObj.optDouble("strokeWidthNorm", 0.012).toFloat(),
                      points = pts,
                    )
                  )
                }
              }
            } else emptyList()

          val textArr = root.optJSONArray("textItems")
          val textItems =
            if (textArr != null) {
              buildList {
                for (i in 0 until textArr.length()) {
                  val tObj = textArr.optJSONObject(i) ?: continue
                  add(
                    MarkupTextItem(
                      id = tObj.optLong("id", i.toLong()),
                      text = tObj.optString("text"),
                      x = tObj.optDouble("x", 0.5).toFloat(),
                      y = tObj.optDouble("y", 0.5).toFloat(),
                      colorHex = tObj.optLong("colorHex", 0xFFFFFFFF),
                      hasPillBackdrop = tObj.optBoolean("hasPillBackdrop", true),
                      scale = tObj.optDouble("scale", 1.0).toFloat(),
                    )
                  )
                }
              }
            } else emptyList()

          PhotoEditRecipe(
            adjust = adjust,
            filterLook = filterLook,
            filterIntensity = filterIntensity,
            filmGrain = filmGrain,
            crop = crop,
            bokeh = bokeh,
            strokes = strokes,
            textItems = textItems,
          )
        }
        .getOrDefault(PhotoEditRecipe())
    }
  }
}
