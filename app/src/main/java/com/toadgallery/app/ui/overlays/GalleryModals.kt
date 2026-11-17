package com.toadgallery.app.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toadgallery.app.data.local.SampleMediaCatalog
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.StorySlide
import com.toadgallery.app.ui.components.GalleryMediaImage
import com.toadgallery.app.ui.theme.LocalToadColors

@Composable
fun StoryViewerOverlay(
  stories: List<StorySlide>,
  activeIndex: Int,
  onAdvance: () -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)
  val story = stories.getOrNull(activeIndex) ?: return
  val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black)
        .padding(top = topPad + 10.dp, bottom = bottomPad + 16.dp, start = 16.dp, end = 16.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).align(Alignment.Center),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        stories.forEachIndexed { idx, _ ->
          Box(
            modifier =
              Modifier.weight(1f)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                  if (idx <= activeIndex) Color(0xFF6EE7B7)
                  else Color.White.copy(alpha = 0.28f)
                )
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = story.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
          )
          Text(
            text = story.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
          )
        }
        IconButton(
          onClick = onClose,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("story_close_button")
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.16f)),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close story",
            tint = Color.White,
          )
        }
      }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onAdvance)
            .testTag("story_advance_surface")
      ) {
        Image(
          painter =
            painterResource(SampleMediaCatalog.resolveDrawableRes(story.drawableResName)),
          contentDescription = story.caption,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  0.5f to Color.Transparent,
                  1.0f to Color.Black.copy(alpha = 0.90f),
                )
              )
        )
        Column(
          modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = story.caption,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
          )
          Text(
            text = "Tap anywhere to advance",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
          )
        }
      }
    }
  }
}

@Composable
fun RecycleBinDialog(
  trashedItems: List<GalleryMediaItem>,
  onRestoreItem: (Long) -> Unit,
  onEmptyBin: () -> Unit,
  onPurgeExpired: () -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)
  val extended = LocalToadColors.current

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.92f).widthIn(max = 500.dp).padding(vertical = 24.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Recycle Bin", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${trashedItems.size} deleted items • Auto-deletes in 30 days",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(extended.surfaceHigh),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close Recycle Bin")
          }
        }

        if (trashedItems.isEmpty()) {
          Text(
            text = "Recycle Bin is empty.",
            style = MaterialTheme.typography.bodyMedium,
            color = extended.textMuted,
            modifier = Modifier.padding(vertical = 20.dp),
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(trashedItems, key = { it.id }) { item ->
              Surface(shape = RoundedCornerShape(16.dp), color = extended.surfaceHigh) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  GalleryMediaImage(
                    item = item,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      item.title,
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      item.dateLabel,
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                  }
                  TextButton(
                    onClick = { onRestoreItem(item.id) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.RestoreFromTrash,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Restore")
                  }
                }
              }
            }
          }

          Button(
            onClick = onEmptyBin,
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("empty_recycle_bin_btn"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = extended.dangerContainer,
                contentColor = extended.onDangerContainer,
              ),
          ) {
            Text("Empty Recycle Bin")
          }
          // Manual trigger for the 30-day auto-purge (TrashPurgeWorker runs it
          // daily; this lets the user force a pass without waiting). Kept as a
          // quiet secondary action — it does NOT touch fresh items, so it's safe
          // to surface next to the destructive Empty button.
          TextButton(
            onClick = onPurgeExpired,
            modifier =
              Modifier.fillMaxWidth().testTag("purge_expired_btn"),
          ) {
            Text("Purge expired (30+ days)")
          }
        }
      }
    }
  }
}
