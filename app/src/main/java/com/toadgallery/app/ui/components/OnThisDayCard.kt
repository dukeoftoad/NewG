package com.toadgallery.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.memories.OnThisDay
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.ui.theme.LocalToadColors

/**
 * "On this day" memories card (v2.2.0 E2): a horizontally scrollable shelf of
 * media captured on today's month+day in earlier years, rendered at the top of
 * the photos grid. Hidden entirely when there is nothing to show.
 */
@Composable
fun OnThisDayCard(
  items: List<GalleryMediaItem>,
  onOpenLightbox: (Long) -> Unit,
  onSeeAll: () -> Unit = {},
) {
  if (items.isEmpty()) return
  val extended = LocalToadColors.current
  val years = OnThisDay.yearSpan(items)

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = extended.surfaceHigh,
    border = BorderStroke(1.dp, extended.borderStrong),
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .testTag("on_this_day_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.CalendarMonth,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
          Text("On this day", style = MaterialTheme.typography.titleSmall)
          Text(
            text =
              if (years.isBlank()) "${items.size} memories"
              else "${items.size} memor${if (items.size == 1) "y" else "ies"} from $years",
            style = MaterialTheme.typography.bodySmall,
            color = extended.textMuted,
          )
        }
        TextButton(onClick = onSeeAll, modifier = Modifier.testTag("on_this_day_see_all")) {
          Text("See all")
        }
      }

      LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(items.size, key = { items[it].id }) { idx ->
          val item = items[idx]
          GalleryMediaImage(
            item = item,
            modifier =
              Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenLightbox(item.id) },
          )
        }
      }
    }
  }
}
