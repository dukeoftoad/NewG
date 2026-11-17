package com.toadgallery.app

import com.toadgallery.app.data.memories.OnThisDay
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.2.0 E2 — "On this day" matching: same month+day, strictly earlier year,
 * newest first. Pure JVM (java.util.Calendar), fixed timezone so the test is
 * deterministic on any CI machine.
 */
class OnThisDayTest {

  private fun calendar(
    year: Int,
    month0: Int,
    day: Int,
  ): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(year, month0, day, 12, 0, 0)
    }

  /** Fixed "today": 2026-11-17 (the release date of v2.2.0). */
  private fun today(): Calendar = calendar(2026, Calendar.NOVEMBER, 17)

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
      tonePreset = com.toadgallery.app.data.model.TonePreset.NATURAL,
      albumId = null,
    )

  @Test
  fun `matches same month-day from earlier years only`() {
    val media =
      listOf(
        item(1, calendar(2024, Calendar.NOVEMBER, 17)), // match
        item(2, calendar(2025, Calendar.NOVEMBER, 17)), // match
        item(3, today()), // this year — excluded
        item(4, calendar(2025, Calendar.NOVEMBER, 16)), // wrong day
        item(5, calendar(2025, Calendar.DECEMBER, 17)), // wrong month
        item(6, calendar(2026, Calendar.NOVEMBER, 18)), // future
      )

    val picked = OnThisDay.pick(media, today())

    assertEquals(listOf(2L, 1L), picked.map { it.id })
  }

  @Test
  fun `newest year first on ties`() {
    val media =
      listOf(
        item(1, calendar(2020, Calendar.NOVEMBER, 17, )),
        item(2, calendar(2025, Calendar.NOVEMBER, 17)),
        item(3, calendar(2022, Calendar.NOVEMBER, 17)),
      )
    assertEquals(listOf(2L, 3L, 1L), OnThisDay.pick(media, today()).map { it.id })
  }

  @Test
  fun `empty library or nothing matching yields empty`() {
    assertTrue(OnThisDay.pick(emptyList(), today()).isEmpty())
    assertTrue(OnThisDay.pick(listOf(item(1, calendar(2025, Calendar.MARCH, 3))), today()).isEmpty())
  }

  @Test
  fun `yearSpan collapses a single year and spans a range`() {
    val media =
      listOf(
        item(1, calendar(2025, Calendar.NOVEMBER, 17)),
        item(2, calendar(2019, Calendar.NOVEMBER, 17)),
        item(3, calendar(2025, Calendar.NOVEMBER, 17, )),
      )
    assertEquals("2019–2025", OnThisDay.yearSpan(media, today()))
    assertEquals(
      "2025",
      OnThisDay.yearSpan(listOf(item(1, calendar(2025, Calendar.NOVEMBER, 17))), today()),
    )
    assertEquals("", OnThisDay.yearSpan(emptyList(), today()))
  }

  @Test
  fun `leap-day media surfaces on Feb 28 of non-leap years`() {
    // Feb 29 2024: on a non-leap year it must surface on Feb 28 (the last
    // day of February — the anniversary spirit) and stay quiet on Mar 1.
    val leapItem = item(1, calendar(2024, Calendar.FEBRUARY, 29))

    val feb28 = OnThisDay.pick(listOf(leapItem), calendar(2026, Calendar.FEBRUARY, 28))
    assertEquals("surfaces on Feb 28 of a non-leap year", listOf(1L), feb28.map { it.id })

    val mar1 = OnThisDay.pick(listOf(leapItem), calendar(2026, Calendar.MARCH, 1))
    assertTrue("no double-surfacing on Mar 1", mar1.isEmpty())

    val realLeapFeb29 = OnThisDay.pick(listOf(leapItem), calendar(2028, Calendar.FEBRUARY, 29))
    assertEquals("matches on the real day in leap years", listOf(1L), realLeapFeb29.map { it.id })
  }
}
