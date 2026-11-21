@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.toadgallery.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Scopes for the grid→lightbox shared-element hero animation (v2.4.0).
 *
 * [LocalSharedTransitionScope] is provided once by the SharedTransitionLayout
 * wrapping the app shell; [LocalNavAnimatedVisibilityScope] by the Photos
 * NavHost destination (the grid scene); [LocalOverlayAnimatedVisibilityScope]
 * by the lightbox AnimatedVisibility (the overlay scene). An element
 * registered via [heroSharedElement] animates between scenes when both expose
 * the same key — and silently degrades to no animation whenever a scope is
 * missing (e.g. lightbox opened from Search, where the grid carries no key).
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalOverlayAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Registers the composable under [key] as a shared element when both scopes
 * are available; a no-op otherwise, so call sites stay hero-agnostic and the
 * caller never needs the experimental API itself.
 *
 * v3.0.1: the OVERLAY scene (the lightbox) carries the shared element only
 * while its enter/exit transition is actually running. At rest the match
 * partner — the grid thumbnail, which stays composed underneath the lightbox —
 * can pin this element at the thumbnail's bounds: a full-screen photo rendered
 * inside a ~345px grid cell (founder device screenshot, v3.0.0). Dropping the
 * modifier once the transition settles makes the resting lightbox a plain
 * full-screen ContentScale.Fit image, and lightbox swipes (new item id → no
 * running transition → no match) can never re-pin it. The morph itself still
 * plays during the transition: at enter start currentState(HIDDEN) differs
 * from targetState(VISIBLE), so the element registers from the first frame.
 */
@Composable
fun Modifier.heroSharedElement(key: String?): Modifier {
  if (key.isNullOrBlank()) return this
  // Robolectric: the frozen frame clock makes any shared transition keep the
  // app permanently busy during Espresso's idle sync — skip the hero there.
  if (MotionPolicy.isRobolectric) return this
  val shared = LocalSharedTransitionScope.current ?: return this
  val overlayScope = LocalOverlayAnimatedVisibilityScope.current
  val animated = overlayScope ?: LocalNavAnimatedVisibilityScope.current ?: return this
  if (overlayScope != null) {
    val transition = overlayScope.transition
    val morphing = transition.isRunning || transition.currentState != transition.targetState
    if (!morphing) return this
  }
  return with(shared) {
    this@heroSharedElement.sharedElement(
      rememberSharedContentState(key = key),
      animatedVisibilityScope = animated,
    )
  }
}
