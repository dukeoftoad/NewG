package com.toadgallery.app.ui

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Motion gating for Robolectric (v2.4.0): under the test runner the compose
 * animation frame clock is frozen while Espresso performs its idle sync, so
 * any in-flight tween keeps the app permanently busy (AppNotIdleException).
 * Production builds tween; Robolectric builds snap straight to the end state
 * — same destinations, zero wall-clock wait, tests exercise layout and
 * behavior rather than the animation itself.
 */
object MotionPolicy {
  /** Robolectric rewrites Build.FINGERPRINT to contain "robolectric". */
  val isRobolectric: Boolean =
    Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

  /** [tween] of [ms] in production (default 280ms, the tab-slide spec); instant [snap] under Robolectric. */
  fun <T> uiSpec(ms: Int = 280): FiniteAnimationSpec<T> =
    if (isRobolectric) snap() else tween(ms)
}
