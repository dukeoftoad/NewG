package com.toadgallery.app

import com.toadgallery.app.data.video.TrimTimelineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.3.0 — visual trim timeline math. Pure JVM: the composable only renders
 * this state, so these tests carry the guarantees (clamping, ordering,
 * minimum window, probe distribution).
 */
class TrimTimelineStateTest {

  private val duration = 60_000L

  @Test
  fun `construction rejects invalid durations and ranges`() {
    assertThrows(IllegalArgumentException::class.java) { TrimTimelineState(durationMs = 0L) }
    assertThrows(IllegalArgumentException::class.java) {
      TrimTimelineState(durationMs = duration, startMs = 5_000L, endMs = 5_000L)
    }
    assertThrows(IllegalArgumentException::class.java) {
      TrimTimelineState(durationMs = duration, startMs = -1L)
    }
    assertThrows(IllegalArgumentException::class.java) {
      TrimTimelineState(durationMs = duration, endMs = duration + 1)
    }
  }

  @Test
  fun `withRange clamps and reorders unordered input`() {
    val base = TrimTimelineState(duration)
    val flipped = base.withRange(20_000L, 10_000L)
    assertEquals(10_000L, flipped.startMs)
    assertEquals(20_000L, flipped.endMs)

    val clamped = base.withRange(-5_000L, 999_999L)
    assertEquals(0L, clamped.startMs)
    assertEquals(duration, clamped.endMs)
  }

  @Test
  fun `minimum window is enforced at the start anchor`() {
    val state = TrimTimelineState(duration).withRange(0L, 200L)
    assertEquals(0L, state.startMs)
    assertEquals(TrimTimelineState.DEFAULT_MIN_WINDOW_MS, state.endMs)
  }

  @Test
  fun `minimum window is enforced at the end anchor`() {
    val state = TrimTimelineState(duration).withRange(59_800L, duration)
    assertEquals(duration - TrimTimelineState.DEFAULT_MIN_WINDOW_MS, state.startMs)
    assertEquals(duration, state.endMs)
  }

  @Test
  fun `minimum window is enforced mid-video by extending right`() {
    val state = TrimTimelineState(duration).withRange(30_000L, 30_200L)
    assertEquals(30_000L, state.startMs)
    assertEquals(31_000L, state.endMs)
  }

  @Test
  fun `mid-video window near the end falls back left`() {
    val state = TrimTimelineState(duration).withRange(59_000L, 59_500L)
    assertEquals(duration - TrimTimelineState.DEFAULT_MIN_WINDOW_MS, state.startMs)
    assertEquals(duration, state.endMs)
  }

  @Test
  fun `videos shorter than the minimum window select everything`() {
    val short = TrimTimelineState(600L).withRange(0L, 300L)
    assertEquals(0L, short.startMs)
    assertEquals(600L, short.endMs)
    assertTrue(short.isFullRange)
  }

  @Test
  fun `probe positions spread inside the video and never land on EOF`() {
    val state = TrimTimelineState(80_000L)
    val probes = state.probePositionsMs(8)
    assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L, 50_000L, 60_000L, 70_000L), probes)
    assertTrue("never at EOF", probes.all { it < 80_000L })
  }

  @Test
  fun `window and full-range flags track the state`() {
    val full = TrimTimelineState(duration)
    assertTrue(full.isFullRange)
    assertEquals(duration, full.windowMs)

    val trimmed = full.withRange(5_000L, 15_000L)
    assertFalse(trimmed.isFullRange)
    assertEquals(10_000L, trimmed.windowMs)
  }

  @Test
  fun `formatClock renders compact readout labels`() {
    assertEquals("0:00", TrimTimelineState.formatClock(0L))
    assertEquals("0:04", TrimTimelineState.formatClock(4_000L))
    assertEquals("1:02", TrimTimelineState.formatClock(62_000L))
    assertEquals("10:00", TrimTimelineState.formatClock(600_000L))
    assertEquals("0:00", TrimTimelineState.formatClock(-500L))
  }
}
