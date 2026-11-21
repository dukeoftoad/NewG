package com.toadgallery.app.ui

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.toadgallery.app.ui.theme.ToadMarkTokens

/**
 * Motion gating for Robolectric (v2.4.0): under the test runner the compose
 * animation frame clock is frozen while Espresso performs its idle sync, so
 * any in-flight tween keeps the app permanently busy (AppNotIdleException).
 * Production builds tween; Robolectric builds snap straight to the end state
 * — same destinations, zero wall-clock wait, tests exercise layout and
 * behavior rather than the animation itself.
 *
 * v2.5.0: durations now come from the ToadMark token scale
 * ([ToadMarkTokens]) instead of call-site constants, so the gallery, the
 * future Flutter surfaces and the other toad apps share one motion
 * character: fast 150 / medium 250 / slow 400 ms (from toadPLAYER's
 * AppTokens, re-expressed).
 */
object MotionPolicy {
  /** Robolectric rewrites Build.FINGERPRINT to contain "robolectric". */
  val isRobolectric: Boolean =
    Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

  /** ToadMark motion scale (ms): chips and small feedback. */
  val Fast = ToadMarkTokens.MotionFast

  /** ToadMark motion scale (ms): panels, nav transitions, content changes. */
  val Medium = ToadMarkTokens.MotionMedium

  /** ToadMark motion scale (ms): full-screen surfaces. */
  val Slow = ToadMarkTokens.MotionSlow

  /**
   * [tween] in production, instant [snap] under Robolectric. Defaults to the
   * ToadMark medium spec (250 ms), which replaces the old tab-slide 280 ms.
   */
  fun <T> uiSpec(ms: Int = Medium): FiniteAnimationSpec<T> =
    if (isRobolectric) snap() else tween(ms)

  /**
   * Sheet/dialog entrance: the ToadMark medium spec. Replaces the previous
   * call-site 200 ms tween behind the same Robolectric gating.
   */
  fun <T> entranceSpec(): FiniteAnimationSpec<T> = uiSpec(Medium)

  /**
   * Lightbox entrance/exit: in on the medium spec, out on the fast spec
   * (exits are always quicker than entrances). Replaces the previous
   * 220/180 ms pair behind the same Robolectric gating.
   */
  fun <T> lightboxInSpec(): FiniteAnimationSpec<T> = uiSpec(Medium)
  fun <T> lightboxOutSpec(): FiniteAnimationSpec<T> = uiSpec(Fast)
}
