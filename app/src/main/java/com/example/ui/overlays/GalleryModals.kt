package com.example.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.SampleMediaCatalog
import com.example.data.model.GalleryMediaItem
import com.example.data.model.StorySlide
import com.example.data.model.TonePreset
import com.example.ui.components.GalleryMediaImage
import com.example.ui.theme.LocalToadColors
import java.util.Locale

@Composable
fun LightboxOverlay(
  item: GalleryMediaItem,
  hasPrevious: Boolean,
  hasNext: Boolean,
  showLocationExif: Boolean,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onClose: () -> Unit,
  onToggleFavorite: () -> Unit,
  onHideToVault: () -> Unit,
  onMoveToTrash: () -> Unit,
  onSelectTonePreset: (TonePreset) -> Unit,
) {
  BackHandler(onBack = onClose)
  var showEditorRow by remember { mutableStateOf(false) }
  var scale by remember(item.id) { mutableFloatStateOf(1f) }
  var panOffset by remember(item.id) { mutableStateOf(Offset.Zero) }
  var swipeAccum by remember(item.id) { mutableFloatStateOf(0f) }

  val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.96f))
        .padding(top = topPad + 8.dp, bottom = bottomPad + 12.dp, start = 16.dp, end = 16.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onClose,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("lightbox_close_button")
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.12f)),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close viewer",
            tint = Color.White,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = item.dateLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
          )
          Text(
            text = if (showLocationExif) item.locationName else "GPS Hidden by Privacy Setting",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6EE7B7),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        IconButton(
          onClick = onToggleFavorite,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("lightbox_favorite_button")
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.12f)),
        ) {
          Icon(
            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (item.isFavorite) Color(0xFFFB7185) else Color.White,
          )
        }
      }

      // Main Pinch-to-Zoom & Double-Tap Preview Area
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(22.dp))
              .pointerInput(item.id) {
                detectTapGestures(
                  onDoubleTap = {
                    if (scale > 1.05f) {
                      scale = 1f
                      panOffset = Offset.Zero
                    } else {
                      scale = 2.5f
                    }
                  }
                )
              }
              .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                  val newScale = (scale * zoom).coerceIn(1f, 4f)
                  scale = newScale
                  if (newScale > 1.01f) {
                    val maxOffset = (newScale - 1f) * 320f
                    panOffset =
                      Offset(
                        x = (panOffset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                        y = (panOffset.y + pan.y).coerceIn(-maxOffset, maxOffset),
                      )
                  } else {
                    panOffset = Offset.Zero
                  }
                }
              }
              .pointerInput(item.id, scale <= 1.02f) {
                if (scale <= 1.02f) {
                  detectHorizontalDragGestures(
                    onDragEnd = {
                      if (swipeAccum > 120f && hasPrevious) onPrevious()
                      else if (swipeAccum < -120f && hasNext) onNext()
                      swipeAccum = 0f
                    },
                    onDragCancel = { swipeAccum = 0f },
                    onHorizontalDrag = { _, dragAmount -> swipeAccum += dragAmount },
                  )
                }
              },
          contentAlignment = Alignment.Center,
        ) {
          GalleryMediaImage(
            item = item,
            contentScale = ContentScale.Fit,
            modifier =
              Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panOffset.x
                translationY = panOffset.y
              },
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("lightbox_prev_button")
                .clip(CircleShape)
                .background(
                  if (hasPrevious) Color.White.copy(alpha = 0.14f)
                  else Color.White.copy(alpha = 0.04f)
                ),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Previous photo",
              tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
            )
          }

          Surface(
            onClick = {
              if (scale > 1.05f) {
                scale = 1f
                panOffset = Offset.Zero
              }
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
          ) {
            val zoomNote =
              if (scale > 1.05f) String.format(Locale.US, " • %.1fx (Tap to reset)", scale)
              else ""
            Text(
              text = "${item.title}$zoomNote",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFD1FAE5),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
          }

          IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("lightbox_next_button")
                .clip(CircleShape)
                .background(
                  if (hasNext) Color.White.copy(alpha = 0.14f)
                  else Color.White.copy(alpha = 0.04f)
                ),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Next photo",
              tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
            )
          }
        }
      }

      // EXIF & Non-Destructive Action Sheet
      Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              "Folder & Tags",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.65f),
            )
            Text(
              text =
                if (item.ocrText.isNotBlank()) "${item.bucketName} • ${item.ocrText.take(36)}"
                else "${item.bucketName} • ${item.tags.joinToString(", ")}",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF6EE7B7),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(start = 12.dp),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              "Camera / Source",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.65f),
            )
            Text(
              text = item.exifSummary,
              style =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                ),
              color = Color.White.copy(alpha = 0.92f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(start = 12.dp),
            )
          }

          AnimatedVisibility(visible = showEditorRow) {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 2.dp),
            ) {
              items(TonePreset.entries) { preset ->
                val selected = item.tonePreset == preset
                Surface(
                  onClick = { onSelectTonePreset(preset) },
                  modifier = Modifier.testTag("tone_preset_${preset.name.lowercase()}"),
                  shape = CircleShape,
                  color =
                    if (selected) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color(0xFF032619) else Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LightboxActionChip(
              icon = Icons.Filled.Lock,
              label = "Hide",
              bg = Color.White.copy(alpha = 0.12f),
              fg = Color.White,
              onClick = onHideToVault,
              testTag = "lightbox_hide_button",
              modifier = Modifier.weight(1f),
            )
            LightboxActionChip(
              icon = Icons.Filled.Tune,
              label = if (showEditorRow) "Done" else "Edit",
              bg =
                if (showEditorRow) Color(0xFF6EE7B7).copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.12f),
              fg = if (showEditorRow) Color(0xFF6EE7B7) else Color.White,
              onClick = { showEditorRow = !showEditorRow },
              testTag = "lightbox_edit_button",
              modifier = Modifier.weight(1f),
            )
            LightboxActionChip(
              icon = Icons.Filled.Delete,
              label = "Delete",
              bg = Color(0xFFEF4444).copy(alpha = 0.22f),
              fg = Color(0xFFFCA5A5),
              onClick = onMoveToTrash,
              testTag = "lightbox_delete_button",
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LightboxActionChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  bg: Color,
  fg: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
    shape = RoundedCornerShape(14.dp),
    color = bg,
  ) {
    Row(
      modifier = Modifier.padding(vertical = 11.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.size(6.dp))
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
  }
}

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
        }
      }
    }
  }
}
