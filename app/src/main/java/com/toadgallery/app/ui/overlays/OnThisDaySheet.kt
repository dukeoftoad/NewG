package com.toadgallery.app.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.toadgallery.app.data.memories.OnThisDay
import com.toadgallery.app.data.memories.WeekMemories
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.ui.MotionPolicy
import com.toadgallery.app.ui.components.MediaThumbnailCard
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.theme.ToadMarkTokens

/**
 * "See all" surface for memories (v2.3.0, tabbed in v3.0.0): the full
 * collection in a grid instead of the card's single-row shelf. v3.0.0 adds
 * the Week tab — the ±3-day recall window from [WeekMemories] — next to the
 * original day-exact On This Day tab. Tapping a thumbnail closes the sheet
 * and opens the lightbox via the same [onOpenLightbox] path the grid uses,
 * so selection/favorites behavior stays out of the memories view on purpose.
 */
@Composable
fun OnThisDaySheet(
  items: List<GalleryMediaItem>,
  onOpenLightbox: (Long) -> Unit,
  onDismiss: () -> Unit,
  weekItems: List<GalleryMediaItem> = emptyList(),
) {
  BackHandler(onBack = onDismiss)
  val extended = LocalToadColors.current

  var tab by remember { mutableStateOf(if (items.isNotEmpty()) 0 else 1) }

  // v2.4.0: fade+scale entrance — dialog windows otherwise snap open.
  val entrance = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    if (MotionPolicy.isRobolectric) entrance.snapTo(1f)
    else entrance.animateTo(1f, tween(MotionPolicy.Medium))
  }

  // The tab's live list; the grid is a pure function of (tab, items).
  val shown = if (tab == 0) items else weekItems

  Dialog(
    onDismissRequest = onDismiss,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.94f)
          .widthIn(max = 560.dp)
          .padding(vertical = 28.dp)
          .windowInsetsPadding(WindowInsets.systemBars)
          .graphicsLayer {
            alpha = entrance.value
            val s = 0.94f + 0.06f * entrance.value
            scaleX = s
            scaleY = s
          },
      shape = RoundedCornerShape(ToadMarkTokens.RadiusSheet),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().testTag("memories_sheet_header"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Memories", style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                if (tab == 0)
                  "${items.size} memor${if (items.size == 1) "y" else "ies"} · ${OnThisDay.yearSpan(items)}"
                else
                  "${weekItems.size} this week · ${WeekMemories.windowLabel()}",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close memories view")
          }
        }

        // v3.0.0: Day / Week tabs. A tab with no memories is disabled rather
        // than hidden so the surface stays stable — the day tab exists even
        // when today has no day-exact matches.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MemoriesTab(
            label = "On this day",
            selected = tab == 0,
            enabled = items.isNotEmpty(),
            onClick = { tab = 0 },
            modifier = Modifier.testTag("memories_tab_day"),
          )
          MemoriesTab(
            label = "This week",
            selected = tab == 1,
            enabled = weekItems.isNotEmpty(),
            onClick = { tab = 1 },
            modifier = Modifier.testTag("memories_tab_week"),
          )
        }

        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          contentPadding = PaddingValues(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("memories_grid"),
        ) {
          items(items = shown, key = { it.id }) { item ->
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

/** Compact pill tab used by the Memories sheet's Day/Week switch. */
@Composable
private fun MemoriesTab(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(ToadMarkTokens.RadiusControl),
    color =
      when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
      },
    contentColor =
      when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      },
    border =
      if (selected) null
      else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = modifier,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
  }
}
