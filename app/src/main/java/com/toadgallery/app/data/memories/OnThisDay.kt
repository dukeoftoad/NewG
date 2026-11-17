package com.toadgallery.app.data.memories

import com.toadgallery.app.data.model.GalleryMediaItem
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * "On this day" (v2.2.0 E2): media captured on today's month+day in an
 * EARLIER year. Pure and timezone-correct — the comparison uses the device's
 * calendar for both "today" and each item's capture time, so a photo taken
 * locally on Nov 17 surfaces on Nov 17 wherever the user is now.
 *
 * Input should be the library's visible media (the flows already exclude
 * hidden and trashed rows). Output is sorted newest-first.
 */
object OnThisDay {

  fun pick(
    items: List<GalleryMediaItem>,
    today: Calendar = Calendar.getInstance(),
  ): List<GalleryMediaItem> {
    val month = today.get(Calendar.MONTH)
    val day = today.get(Calendar.DAY_OF_MONTH)
    val year = today.get(Calendar.YEAR)

    // Leap-day forgiveness: Feb 29 captures would otherwise be invisible in
    // three of four years. On the last day of February in a NON-leap year,
    // surface them alongside Feb 28 media.
    val dayKeys = mutableListOf(month to day)
    val isLeap = (today as? GregorianCalendar ?: GregorianCalendar().apply { time = today.time }).isLeapYear(year)
    if (!isLeap && month == Calendar.FEBRUARY && day == 28) {
      dayKeys += Calendar.FEBRUARY to 29
    }

    return items
      .mapNotNull { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
        val key = cal.get(Calendar.MONTH) to cal.get(Calendar.DAY_OF_MONTH)
        val earlierYear = cal.get(Calendar.YEAR) < year
        if (earlierYear && dayKeys.any { it == key }) cal to item else null
      }
      .sortedByDescending { (cal, _) -> cal.timeInMillis }
      .map { (_, item) -> item }
  }

  /** "2019–2024" style year span for the card subtitle; "" when empty. */
  fun yearSpan(items: List<GalleryMediaItem>, today: Calendar = Calendar.getInstance()): String {
    if (items.isEmpty()) return ""
    val years =
      items.map { item ->
        Calendar.getInstance().apply { timeInMillis = item.timestamp }.get(Calendar.YEAR)
      }.distinct().sorted()
    return if (years.size == 1) "${years.first()}" else "${years.first()}–${years.last()}"
  }
}
