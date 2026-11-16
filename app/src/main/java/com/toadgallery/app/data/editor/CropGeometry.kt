package com.toadgallery.app.data.editor

import com.toadgallery.app.data.model.CropParams

/**
 * Pure geometry helpers that keep the crop rectangle anchored to the same
 * visual region when the underlying transform changes. Rotating or flipping
 * changes the image the rect is normalized against, so without remapping the
 * rect the user's selected region silently jumps to different pixels. These
 * helpers remap the rect coordinates with the exact inverse transform.
 */

private fun remapRect(
  crop: CropParams,
  remapPoint: (Float, Float) -> Pair<Float, Float>,
): CropParams {
  val (x1, y1) = remapPoint(crop.left, crop.top)
  val (x2, y2) = remapPoint(crop.right, crop.bottom)
  return crop.copy(
    left = minOf(x1, x2),
    top = minOf(y1, y2),
    right = maxOf(x1, x2),
    bottom = maxOf(y1, y2),
  )
}

/**
 * Remaps the crop rect for a RELATIVE quarter-turn of the image content,
 * matching [CropParams.rotationQuarterTurns] deltas (+1 = 90° clockwise,
 * +2 = 180°, +3 = 90° counter-clockwise) as rendered by
 * [PhotoEditorRenderer.applyTransformOnly] (Android postRotate is clockwise).
 */
fun CropParams.withQuarterTurnDelta(delta: Int): CropParams =
  when (((delta % 4) + 4) % 4) {
    1 -> remapRect(this) { x, y -> (1f - y) to x }
    2 -> remapRect(this) { x, y -> (1f - x) to (1f - y) }
    3 -> remapRect(this) { x, y -> y to (1f - x) }
    else -> this
  }

/** Mirrors the crop rect horizontally, matching flipHorizontal rendering. */
fun CropParams.withHorizontalFlipApplied(): CropParams =
  remapRect(this) { x, y -> (1f - x) to y }

/** Mirrors the crop rect vertically, matching flipVertical rendering. */
fun CropParams.withVerticalFlipApplied(): CropParams =
  remapRect(this) { x, y -> x to (1f - y) }
