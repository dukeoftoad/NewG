package com.toadgallery.app.data.memories

import com.toadgallery.app.data.model.GalleryMediaItem
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * "This week in past years" (v3.0.0): the week-around-today counterpart of
 * [OnThisDay]. Photos captured within ±3 days of today's month-day in an
 * EARLIER year — the "a whole week of that trip three years ago" recall the
 * day-exact matcher misses.
 *
 * Same timezone-correct approach as OnThisDay: the device's calendar defines
 * both "today" and each item's capture time, so a photo taken locally on
 * Nov 15 surfaces in the week window wherever the user is now.
 *
 * Output excludes the current year entirely (strictly earlier years only)
 * and is sorted newest-first, matching OnThisDay's contract.
 */
object WeekMemories {

  /** Half-width of the recall window in days. ±3 keeps the window at 7 days. */
  const val WINDOW_DAYS = 3

  fun pick(
    items: List<GalleryMediaItem>,
    today: Calendar = Calendar.getInstance(),
  ): List<GalleryMediaItem> {
    val center = today.normalized()
    val currentYear = center.get(Calendar.YEAR)

    return items
      .mapNotNull { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }.normalized()
        if (cal.get(Calendar.YEAR) >= currentYear) return@mapNotNull null
        if (!cal.seasonallyWithinDaysOf(center, WINDOW_DAYS)) return@mapNotNull null
        cal to item
      }
      .sortedByDescending { (cal, _) -> cal.timeInMillis }
      .map { (_, item) -> item }
  }

  /** "Nov 14 – 20" style window label; "" when empty. */
  fun windowLabel(today: Calendar = Calendar.getInstance()): String {
    val center = today.normalized()
    val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
    val start = (center.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -WINDOW_DAYS) }
    val end = (center.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, WINDOW_DAYS) }
    return "${fmt.format(start.time)} – ${fmt.format(end.time)}"
  }

  /** "2019–2024" style year span for a subtitle; "" when empty. */
  fun yearSpan(items: List<GalleryMediaItem>): String {
    if (items.isEmpty()) return ""
    val years =
      items
        .map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) }
        .distinct()
        .sorted()
    return if (years.size == 1) "${years.first()}" else "${years.first()}–${years.last()}"
  }

  // --- date helpers (private, kept tiny) ---

  private fun Calendar.normalized(): Calendar =
    (this.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, 12)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
  }

  /**
   * True when this calendar's (month, day) falls within [days] days of
   * [other]'s (month, day) in the SAME year — the seasonal distance, so a
   * Nov 18 2023 photo is 3 "days" from a Nov 21 today, and a Dec 30 capture
   * is 3 days from a Jan 2 today (wrap across the year seam).
   *
   * Distance is seam-aware day-of-year arithmetic on a year-collapsed copy:
   * min(|doyA − doyB|, yearLen − |doyA − doyB|). The seam term is what makes
   * Dec 30 ↔ Jan 2 come out as 3 instead of 363. Day-of-year values come
   * from Calendar (not raw millis) so DST/timezone offsets never leak in.
   *
   * Leap-day forgiveness, same rule as OnThisDay: a Feb 29 capture is
   * compared as Feb 28 when the collapsed year is not a leap year.
   */
  private fun Calendar.seasonallyWithinDaysOf(other: Calendar, days: Int): Boolean {
    val year = other.get(Calendar.YEAR)

    fun leapAwareDayOfYear(c: Calendar): Int {
      val month = c.get(Calendar.MONTH)
      var day = c.get(Calendar.DAY_OF_MONTH)
      if (month == Calendar.FEBRUARY && day == 29) {
        val isLeap =
          (c as? GregorianCalendar ?: GregorianCalendar()).isLeapYear(year)
        if (!isLeap) day = 28
      }
      val collapsed =
        Calendar.getInstance(timeZone).apply {
          clear()
          set(year, month, day, 12, 0, 0)
        }
      return collapsed.get(Calendar.DAY_OF_YEAR)
    }

    val doyA = leapAwareDayOfYear(this)
    val doyB = leapAwareDayOfYear(other)
    val direct = kotlin.math.abs(doyA - doyB)
    val yearLen =
      if (GregorianCalendar().isLeapYear(year)) 366 else 365
    val seam = yearLen - direct
    return minOf(direct, seam) <= days
  }
}
