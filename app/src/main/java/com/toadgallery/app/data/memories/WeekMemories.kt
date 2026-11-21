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
        if (!cal.withinDaysOf(center, WINDOW_DAYS)) return@mapNotNull null
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
   * True when this calendar falls within [days] calendar days of [other]
   * (inclusive), compared on the YEAR field after a day-level diff, so a
   * Dec 30 photo can surface in a window centered on Jan 2. Day-level
   * arithmetic (add/roll) is used rather than raw millis: with DST and
   * timezone offsets, 3*24h in millis is not reliably 3 calendar days.
   */
  private fun Calendar.withinDaysOf(other: Calendar, days: Int): Boolean {
    var a = (this.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
    var b = (other.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
    if (a.after(b)) { val t = a; a = b; b = t }
    var diff = 0
    while (a.before(b)) {
      a.add(Calendar.DAY_OF_YEAR, 1)
      diff++
      if (diff > days) return false
    }
    return diff <= days
  }
}
