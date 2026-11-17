package com.toadgallery.app.data.video

import java.util.Locale

/**
 * The pure, testable plan for one video edit export. No Android dependencies —
 * validation and duration math live here so the [VideoEditEngine] only has to
 * execute a plan that has already been proven valid.
 *
 * v2.2.0 scope (founder-approved): trim, mute, and speed changes. This is a
 * deliberately smaller surface than Ente's editor — no overlays, no track
 * composition, no filters on video.
 *
 * @param trimStartMs keep frames from this time; 0 keeps the start.
 * @param trimEndMs keep frames up to this time; null keeps the source end.
 * @param removeAudio true exports a silent video (the "mute" action).
 * @param speed 1.0 = normal; [MIN_SPEED]..[MAX_SPEED] for slow-mo / fast-mo.
 */
data class VideoEditPlan(
  val trimStartMs: Long = 0L,
  val trimEndMs: Long? = null,
  val removeAudio: Boolean = false,
  val speed: Float = 1.0f,
) {
  init {
    require(trimStartMs >= 0L) { "trimStartMs must not be negative" }
    require(trimEndMs == null || trimEndMs > trimStartMs) {
      "trimEndMs must be after trimStartMs"
    }
    require(speed in MIN_SPEED..MAX_SPEED) {
      "speed must be within $MIN_SPEED..$MAX_SPEED, was $speed"
    }
  }

  /** True when the export re-times the video (needs speed effects). */
  val changesSpeed: Boolean
    get() = speed != 1.0f

  /** True when the plan would produce a byte-identical passthrough. */
  val isNoOp: Boolean
    get() = !changesSpeed && !removeAudio && trimStartMs == 0L && trimEndMs == null

  /**
   * Output duration in ms for a source of [sourceDurationMs]: the clipped
   * window divided by the speed factor. Never less than 1 ms (matches the
   * scanner's min-1-second label rounding; the label formatter floors again).
   */
  fun outputDurationMs(sourceDurationMs: Long): Long {
    val end = trimEndMs ?: sourceDurationMs
    val clipped = (end - trimStartMs).coerceAtLeast(1L)
    return (clipped / speed).toLong().coerceAtLeast(1L)
  }

  /**
   * The Recycle-Row duration label for the edited video, in the same
   * "%02d:%02d" shape [com.toadgallery.app.data.media.MediaStoreScanner] uses
   * for originals, so edited copies do not render differently in the grid.
   */
  fun outputDurationLabel(sourceDurationMs: Long): String {
    val totalSec = (outputDurationMs(sourceDurationMs) / 1000L).coerceAtLeast(1L)
    return String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
  }

  companion object {
    /** Guard rails: 4x fast-mo, quarter-speed slow-mo. */
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4.0f
  }
}
