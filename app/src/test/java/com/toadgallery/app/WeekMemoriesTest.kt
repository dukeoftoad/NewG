package com.toadgallery.app

import com.toadgallery.app.data.memories.WeekMemories
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.0.0 — "This week in past years" matching: within ±3 calendar days of
 * today's date, strictly earlier year, newest first. Pure JVM, fixed
 * timezone, deterministic on any CI machine. Companion to OnThisDayTest
 * (same fixtures, same conventions).
 */
class WeekMemoriesTest {

  private fun calendar(
    year: Int,
    month0: Int,
    day: Int,
  ): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(year, month0, day, 12, 0, 0)
    }

  /** Fixed "today": 2026-11-21 (the release date of v3.0.0). */
  private fun today(): Calendar = calendar(2026, Calendar.NOVEMBER, 21)

  private fun item(
    id: Long,
    captured: Calendar,
    title: String = "p$id",
  ): GalleryMediaItem =
    GalleryMediaItem(
      id = id,
      title = title,
      uriString = null,
      drawableResName = null,
      category = MediaCategory.CAMERA,
      mediaType = MediaType.PHOTO,
      durationLabel = null,
      dateLabel = "",
      weekGroup = "",
      monthGroup = "",
      timestamp = captured.timeInMillis,
      locationName = "",
      hasGps = false,
      mapX = 0f,
      mapY = 0f,
      aspectRatio = 1f,
      isFavorite = false,
      isHidden = false,
      isTrashed = false,
      tags = emptyList(),
      ocrText = "",
      exifSummary = "",
    )

  @Test
  fun `day-exact and near-week captures match`() {
    val items =
      listOf(
        item(1, calendar(2023, Calendar.NOVEMBER, 21)), // exact day, 3 years back — matches
        item(2, calendar(2023, Calendar.NOVEMBER, 18)), // -3 days — matches
        item(3, calendar(2021, Calendar.NOVEMBER, 24)), // +3 days — matches
        item(4, calendar(2020, Calendar.DECEMBER, 21)), // a month later — no match
      )
    val picked = WeekMemories.pick(items, today())
    assertEquals(listOf(1L, 2L, 3L), picked.map { it.id })
  }

  @Test
  fun `boundary is inclusive at three days`() {
    val items =
      listOf(
        item(1, calendar(2025, Calendar.NOVEMBER, 18)), // exactly -3 — matches
        item(2, calendar(2025, Calendar.NOVEMBER, 24)), // exactly +3 — matches
        item(3, calendar(2025, Calendar.NOVEMBER, 17)), // -4 — no match
        item(4, calendar(2025, Calendar.NOVEMBER, 25)), // +4 — no match
      )
    val picked = WeekMemories.pick(items, today())
    assertEquals(listOf(1L, 2L), picked.map { it.id })
  }

  @Test
  fun `current-year captures never surface`() {
    val items =
      listOf(
        item(1, calendar(2026, Calendar.NOVEMBER, 21)),
        item(2, calendar(2026, Calendar.NOVEMBER, 18)),
      )
    assertTrue(WeekMemories.pick(items, today()).isEmpty())
  }

  @Test
  fun `newest first across mixed years`() {
    val items =
      listOf(
        item(1, calendar(2019, Calendar.NOVEMBER, 20)),
        item(2, calendar(2024, Calendar.NOVEMBER, 19)),
        item(3, calendar(2021, Calendar.NOVEMBER, 23)),
      )
    val picked = WeekMemories.pick(items, today())
    assertEquals(listOf(2L, 3L, 1L), picked.map { it.id })
  }

  @Test
  fun `year boundary wraps the window`() {
    // Window centered on Jan 2 reaches back into the previous year's Dec 30.
    val newYearToday = calendar(2026, Calendar.JANUARY, 2)
    val items =
      listOf(
        item(1, calendar(2023, Calendar.DECEMBER, 30)), // -3 across the boundary — matches
        item(2, calendar(2023, Calendar.DECEMBER, 28)), // -5 — no match
        item(3, calendar(2024, Calendar.JANUARY, 5)), // +3 — matches
      )
    val picked = WeekMemories.pick(items, newYearToday)
    assertEquals(listOf(3L, 1L), picked.map { it.id })
  }

  @Test
  fun `leap day capture surfaces in non-leap year window`() {
    // Feb 29 2024 capture; "today" is Feb 27 2026 (non-leap). Distance is 2
    // calendar days via Feb 28 -> Feb 29 in the item's own leap year.
    val today = calendar(2026, Calendar.FEBRUARY, 27)
    val items = listOf(item(1, calendar(2024, Calendar.FEBRUARY, 29)))
    val picked = WeekMemories.pick(items, today)
    assertEquals(listOf(1L), picked.map { it.id })
  }

  @Test
  fun `window label uses month-day span`() {
    val label = WeekMemories.windowLabel(today())
    assertEquals("Nov 18 – 24", label)
  }

  @Test
  fun `year span collapses single year`() {
    val items = listOf(item(1, calendar(2022, Calendar.NOVEMBER, 19)))
    assertEquals("2022", WeekMemories.yearSpan(items))
    val multi = listOf(item(1, calendar(2019, Calendar.NOVEMBER, 19)), item(2, calendar(2024, Calendar.NOVEMBER, 20)))
    assertEquals("2019–2024", WeekMemories.yearSpan(multi))
  }
}
