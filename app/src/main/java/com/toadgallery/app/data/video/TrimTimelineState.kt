package com.toadgallery.app.data.video

import java.util.Locale

/**
 * Pure math for the visual trim timeline (v2.3.0): a clamped start/end
 * range with a minimum window, plus evenly-spaced frame-probe positions
 * for the thumbnail strip. UI-free so every guarantee is JVM-testable —
 * the composable in ui/overlays only renders this state and feeds
 * [VideoEditPlan] with the final range.
 *
 * Invariants after [withRange]:
 *  - 0 <= startMs < endMs <= durationMs (strictly ordered, never inverted);
 *  - the window is never shorter than [minWindowMs] unless the whole video
 *    is shorter, in which case the window is the whole video;
 *  - ranges are expanded toward the nearest boundary, never silently
 *    clipped to something shorter than requested.
 */
data class TrimTimelineState(
  val durationMs: Long,
  val startMs: Long = 0L,
  val endMs: Long = durationMs,
  val minWindowMs: Long = DEFAULT_MIN_WINDOW_MS,
) {
  init {
    require(durationMs > 0L) { "durationMs must be positive, was $durationMs" }
    require(minWindowMs > 0L) { "minWindowMs must be positive" }
    require(startMs >= 0L && endMs <= durationMs && startMs < endMs) {
      "invalid initial range [$startMs, $endMs] for duration $durationMs"
    }
  }

  /** Current selected window length. */
  val windowMs: Long
    get() = endMs - startMs

  /** True when the whole video is selected (nothing to trim). */
  val isFullRange: Boolean
    get() = startMs == 0L && endMs == durationMs

  /**
   * Returns a new state with the given (unordered) range applied: values are
   * clamped into the video, re-ordered, and the minimum window is enforced
   * by expanding toward the nearest boundary (video start, video end, or
   * whichever side has room in the middle).
   */
  fun withRange(firstMs: Long, secondMs: Long): TrimTimelineState {
    val lo = firstMs.coerceIn(0L, durationMs)
    val hi = secondMs.coerceIn(0L, durationMs)
    val (start, end) = if (lo <= hi) lo to hi else hi to lo

    if (end - start >= minWindowMs) return copy(startMs = start, endMs = end)

    // Window too small: expand to minWindow when the video allows it.
    if (durationMs <= minWindowMs) return copy(startMs = 0L, endMs = durationMs)

    val expanded =
      when {
        start == 0L -> 0L to minWindowMs
        end == durationMs -> (durationMs - minWindowMs) to durationMs
        // Mid-video anchor: prefer keeping the left edge, extend right.
        else -> {
          val rightExtended = start + minWindowMs
          if (rightExtended <= durationMs) start to rightExtended
          else (durationMs - minWindowMs) to durationMs
        }
      }
    return copy(startMs = expanded.first, endMs = expanded.second)
  }

  /**
   * Evenly-spaced probe positions for a thumbnail strip of [count] frames:
   * `durationMs * i / count`, which spreads samples inside the video and
   * never lands exactly on EOF (where getFrameAtTime can return null).
   */
  fun probePositionsMs(count: Int): List<Long> {
    require(count >= 2) { "strip needs at least 2 frames, was $count" }
    return (0 until count).map { i -> durationMs * i / count }
  }

  companion object {
    /** One second — the smallest trim window the engine accepts usefully. */
    const val DEFAULT_MIN_WINDOW_MS = 1_000L

    /** Compact "0:04" / "1:02" clock label for the timeline readout. */
    fun formatClock(ms: Long): String {
      val totalSec = (ms / 1000L).coerceAtLeast(0L)
      return String.format(Locale.US, "%d:%02d", totalSec / 60L, totalSec % 60L)
    }
  }
}
