package com.toadgallery.app.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.memories.OnThisDay
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.ui.components.MediaThumbnailCard
import com.toadgallery.app.ui.theme.LocalToadColors

/**
 * "See all" surface for the On-This-Day memories card (v2.3.0): the full
 * memories collection in a grid instead of the card's single-row shelf.
 * Tapping a thumbnail closes the sheet and opens the lightbox via the same
 * [onOpenLightbox] path the grid uses, so selection/favorites behavior stays
 * out of the memories view on purpose.
 */
@Composable
fun OnThisDaySheet(
  items: List<GalleryMediaItem>,
  onOpenLightbox: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  BackHandler(onBack = onDismiss)
  val extended = LocalToadColors.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).padding(vertical = 28.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("On this day", style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                "${items.size} memor${if (items.size == 1) "y" else "ies"} · ${OnThisDay.yearSpan(items)}",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close memories view")
          }
        }

        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          contentPadding = PaddingValues(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        ) {
          items(items = items, key = { it.id }) { item ->
            MediaThumbnailCard(
              item = item,
              forceSquare = true,
              onClick = {
                onDismiss()
                onOpenLightbox(item.id)
              },
            )
          }
        }
      }
    }
  }
}
