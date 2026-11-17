package com.toadgallery.app.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.toadgallery.app.data.media.VideoPlaybackPolicy
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.TonePreset
import com.toadgallery.app.ui.components.GalleryMediaImage
import com.toadgallery.app.ui.theme.LocalToadColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
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
  onShare: () -> Unit,
  onHideToVault: () -> Unit,
  onMoveToTrash: () -> Unit,
  onSelectTonePreset: (TonePreset) -> Unit,
  onOpenEditor: () -> Unit,
  onRevertToOriginal: () -> Unit,
) {
  var showChrome by remember { mutableStateOf(true) }
  var showExifSheet by remember { mutableStateOf(false) }
  var scale by remember(item.id) { mutableFloatStateOf(1f) }
  var panOffset by remember(item.id) { mutableStateOf(Offset.Zero) }

  val animatedScale by
    animateFloatAsState(
      targetValue = scale,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "lightboxScale",
    )
  val animatedPan by
    animateOffsetAsState(
      targetValue = panOffset,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "lightboxPan",
    )

  BackHandler(enabled = !showExifSheet, onBack = onClose)

  val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black)
        .testTag("lightbox_overlay")
  ) {
    // Full-Bleed Edge-to-Edge Media Canvas
    Box(
      modifier =
        Modifier.fillMaxSize()
          .testTag("lightbox_canvas")
          .pointerInput(item.id, hasPrevious, hasNext) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)
              var zoom = 1f
              var pan = Offset.Zero
              var pastTouchSlop = false
              var dragAccum = Offset.Zero
              var isMultiTouch = false
              val wasZoomedAtStart = scale > 1.02f

              do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                  val pressedCount = event.changes.count { it.pressed }
                  if (pressedCount > 1) isMultiTouch = true

                  val zoomChange = event.calculateZoom()
                  val panChange = event.calculatePan()
                  dragAccum += panChange

                  if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                      pastTouchSlop = true
                    }
                  }

                  if (pastTouchSlop) {
                    if (isMultiTouch || wasZoomedAtStart || scale > 1.02f || zoomChange != 1f) {
                      val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                      scale = newScale
                      if (newScale > 1.01f) {
                        val maxOffset = (newScale - 1f) * 380f
                        panOffset =
                          Offset(
                            x = (panOffset.x + panChange.x).coerceIn(-maxOffset, maxOffset),
                            y = (panOffset.y + panChange.y).coerceIn(-maxOffset, maxOffset),
                          )
                      } else {
                        panOffset = Offset.Zero
                      }
                    }
                    event.changes.forEach {
                      if (it.positionChanged()) it.consume()
                    }
                  }
                }
              } while (!canceled && event.changes.any { it.pressed })

              if (pastTouchSlop && !isMultiTouch && !wasZoomedAtStart && scale <= 1.02f) {
                val absX = abs(dragAccum.x)
                val absY = abs(dragAccum.y)
                if (absY > absX && dragAccum.y < -80f) {
                  showExifSheet = true
                } else if (absX > absY) {
                  if (dragAccum.x > 100f && hasPrevious) onPrevious()
                  else if (dragAccum.x < -100f && hasNext) onNext()
                }
              }
            }
          }
          .pointerInput(item.id) {
            detectTapGestures(
              onTap = { showChrome = !showChrome },
              onDoubleTap = {
                if (scale > 1.05f) {
                  scale = 1f
                  panOffset = Offset.Zero
                } else {
                  scale = 2.5f
                }
              },
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      if (VideoPlaybackPolicy.isDirectlyPlayable(item)) {
        // Direct video playback: Media3 ExoPlayer with its own seek/controls.
        // Pinch-zoom stays photo-only; swipes still navigate between items.
        LightboxVideoPlayer(
          uriString = VideoPlaybackPolicy.playbackUri(item).orEmpty(),
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        GalleryMediaImage(
          item = item,
          contentScale = ContentScale.Fit,
          thumbnailSizePx = null,
          heroKey = "hero-media-${item.id}",
          modifier =
            Modifier.fillMaxSize().graphicsLayer {
              scaleX = animatedScale
              scaleY = animatedScale
              translationX = animatedPan.x
              translationY = animatedPan.y
            },
        )
      }
    }

    // Overlaid Compact Top Bar with Gradient Scrim
    AnimatedVisibility(
      visible = showChrome,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.62f),
                0.75f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Transparent,
              )
            )
            .testTag("lightbox_top_bar"),
        contentAlignment = Alignment.TopCenter,
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 760.dp)
              .padding(top = topPad + 2.dp, bottom = 18.dp, start = 8.dp, end = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("lightbox_close_button")
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.36f)),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Close viewer",
              tint = Color.White,
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
            color = Color.Transparent,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
          ) {
            Column(
              modifier = Modifier.padding(vertical = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val zoomLabel =
                if (scale > 1.05f) String.format(Locale.US, " • %.1fx (Tap to reset)", scale)
                else if (item.hasNonDestructiveEdits) " • Edited"
                else ""
              val videoLabel =
                if (item.mediaType == MediaType.VIDEO && !item.durationLabel.isNullOrBlank()) {
                  " • ${item.durationLabel}"
                } else {
                  ""
                }
              Text(
                text = "${item.dateLabel}$videoLabel$zoomLabel",
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (scale > 1.05f || item.hasNonDestructiveEdits) Color(0xFF6EE7B7)
                  else Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = { showExifSheet = true },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("lightbox_info_button")
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.36f)),
            ) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Photo EXIF and details",
                tint = Color.White,
              )
            }

            IconButton(
              onClick = onToggleFavorite,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("lightbox_favorite_button")
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.36f)),
            ) {
              Icon(
                imageVector =
                  if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription =
                  if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (item.isFavorite) Color(0xFFFB7185) else Color.White,
              )
            }
          }
        }
      }
    }

    // Overlaid Slim Bottom Action Strip
    AnimatedVisibility(
      visible = showChrome,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to Color.Black.copy(alpha = 0.36f),
                1f to Color.Black.copy(alpha = 0.72f),
              )
            ),
        contentAlignment = Alignment.BottomCenter,
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 760.dp)
              .padding(top = 18.dp, bottom = bottomPad + 6.dp, start = 12.dp, end = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("lightbox_bottom_bar"),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.56f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = onShare,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("lightbox_share_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Share,
                  contentDescription = "Share photo",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = onOpenEditor,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("lightbox_edit_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = "Open Photo Editor",
                  tint =
                    if (item.hasNonDestructiveEdits || item.tonePreset != TonePreset.NATURAL)
                      Color(0xFF6EE7B7)
                    else Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = onMoveToTrash,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("lightbox_delete_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = "Move to Recycle Bin",
                  tint = Color(0xFFFCA5A5),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showExifSheet) {
    PhotoExifBottomSheet(
      item = item,
      showLocationExif = showLocationExif,
      onSelectTonePreset = onSelectTonePreset,
      onOpenEditor = {
        showExifSheet = false
        onOpenEditor()
      },
      onRevertToOriginal = {
        showExifSheet = false
        onRevertToOriginal()
      },
      onShare = {
        showExifSheet = false
        onShare()
      },
      onToggleFavorite = onToggleFavorite,
      onHideToVault = {
        showExifSheet = false
        onHideToVault()
      },
      onMoveToTrash = {
        showExifSheet = false
        onMoveToTrash()
      },
      onDismiss = { showExifSheet = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoExifBottomSheet(
  item: GalleryMediaItem,
  showLocationExif: Boolean,
  onSelectTonePreset: (TonePreset) -> Unit,
  onOpenEditor: () -> Unit,
  onRevertToOriginal: () -> Unit,
  onShare: () -> Unit,
  onToggleFavorite: () -> Unit,
  onHideToVault: () -> Unit,
  onMoveToTrash: () -> Unit,
  onDismiss: () -> Unit,
) {
  val extended = LocalToadColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 20.dp)
          .testTag("lightbox_exif_sheet"),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Title + Type Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              buildString {
                append(item.dateLabel)
                append(" • ")
                append(aspectRatioLabel(item.aspectRatio))
                if (!item.durationLabel.isNullOrBlank()) {
                  append(" • ")
                  append(item.durationLabel)
                }
              },
            style = MaterialTheme.typography.bodySmall,
            color = extended.textMuted,
          )
        }
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = item.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      // Non-Destructive Tone Presets
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "TONE PRESET",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(TonePreset.entries) { preset ->
            val selected = item.tonePreset == preset
            Surface(
              onClick = { onSelectTonePreset(preset) },
              modifier = Modifier.testTag("tone_preset_${preset.name.lowercase()}"),
              shape = CircleShape,
              color =
                if (selected) MaterialTheme.colorScheme.primary
                else extended.surfaceHigh,
              border =
                BorderStroke(
                  1.dp,
                  if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
                ),
            ) {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      // Pro Luminance Histogram Bar
      LuminanceHistogramCard(item = item)

      HorizontalDivider(color = extended.borderSubtle)

      // EXIF Metadata Rows
      ExifDetailRow(
        icon = Icons.Outlined.CameraAlt,
        label = "Camera / Source",
        value = item.exifSummary,
        monospace = true,
      )

      ExifDetailRow(
        icon = Icons.Outlined.Event,
        label = "Captured",
        value = "${item.dateLabel} (${item.weekGroup})",
      )

      ExifDetailRow(
        icon = Icons.Outlined.LocationOn,
        label = "Location",
        value =
          if (!showLocationExif) "GPS Hidden by Privacy Setting"
          else if (item.hasGps) item.locationName
          else "${item.locationName} (No GPS tag)",
        highlight = showLocationExif && item.hasGps,
      )

      ExifDetailRow(
        icon = Icons.Outlined.FolderOpen,
        label = "Folder & Tags",
        value =
          if (item.tags.isEmpty()) item.bucketName
          else "${item.bucketName} • ${item.tags.joinToString(", ")}",
      )

      if (item.ocrText.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = extended.surfaceHigh,
          border = BorderStroke(1.dp, extended.borderSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.TextSnippet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "On-Device OCR Text",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text = item.ocrText,
              style =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                ),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // Quick Actions Row
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ExifQuickActionChip(
          icon = Icons.Filled.Edit,
          label = "Editor",
          bg = MaterialTheme.colorScheme.primaryContainer,
          fg = MaterialTheme.colorScheme.onPrimaryContainer,
          onClick = onOpenEditor,
          modifier = Modifier.weight(1f).testTag("exif_open_editor_chip"),
        )
        ExifQuickActionChip(
          icon = Icons.Filled.Share,
          label = "Share",
          bg = extended.surfaceHigh,
          fg = MaterialTheme.colorScheme.onSurface,
          onClick = onShare,
          modifier = Modifier.weight(1f),
        )
        ExifQuickActionChip(
          icon = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
          label = if (item.isFavorite) "Liked" else "Like",
          bg = extended.surfaceHigh,
          fg = if (item.isFavorite) Color(0xFFFB7185) else MaterialTheme.colorScheme.onSurface,
          onClick = onToggleFavorite,
          modifier = Modifier.weight(1f),
        )
        ExifQuickActionChip(
          icon = Icons.Filled.Lock,
          label = "Hide",
          bg = extended.surfaceHigh,
          fg = MaterialTheme.colorScheme.onSurface,
          onClick = onHideToVault,
          modifier = Modifier.weight(1f),
        )
        ExifQuickActionChip(
          icon = Icons.Outlined.DeleteOutline,
          label = "Delete",
          bg = extended.dangerContainer,
          fg = extended.onDangerContainer,
          onClick = onMoveToTrash,
          modifier = Modifier.weight(1f),
        )
      }

      if (item.hasNonDestructiveEdits) {
        Surface(
          onClick = onRevertToOriginal,
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("exif_revert_original_btn"),
          shape = RoundedCornerShape(14.dp),
          color = extended.surfaceHigh,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
          Text(
            text = "Non-destructive edit active • Tap to Revert to Original",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun LuminanceHistogramCard(item: GalleryMediaItem) {
  val extended = LocalToadColors.current
  val primaryColor = MaterialTheme.colorScheme.primary
  val bins =
    remember(item.id, item.tonePreset, item.hasNonDestructiveEdits) {
      val seed = (item.id * 31L + item.tonePreset.ordinal * 17L).toInt()
      FloatArray(28) { idx ->
        val norm = idx / 27f
        val bell = sin(norm * Math.PI).toFloat()
        val harmonic = 0.22f * sin((norm * 4.5 + (seed % 7)) * Math.PI).toFloat()
        (0.18f + 0.68f * bell + abs(harmonic)).coerceIn(0.12f, 1f)
      }
    }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = extended.surfaceHigh,
    border = BorderStroke(1.dp, extended.borderSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "TONE DISTRIBUTION",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = extended.textMuted,
        )
        Text(
          text = if (item.hasNonDestructiveEdits) "Edited Curve" else item.tonePreset.label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = primaryColor,
        )
      }
      Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val barCount = bins.size
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        for (i in 0 until barCount) {
          val h = size.height * bins[i]
          drawRoundRect(
            color = primaryColor.copy(alpha = 0.35f + 0.55f * bins[i]),
            topLeft = Offset(i * (barWidth + gap), size.height - h),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(3f, 3f),
          )
        }
      }
    }
  }
}

@Composable
private fun ExifDetailRow(
  icon: ImageVector,
  label: String,
  value: String,
  monospace: Boolean = false,
  highlight: Boolean = false,
) {
  val extended = LocalToadColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (highlight) MaterialTheme.colorScheme.primary else extended.textMuted,
      modifier = Modifier.padding(top = 2.dp).size(18.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = extended.textMuted,
      )
      Text(
        text = value,
        style =
          if (monospace) {
            MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
            )
          } else {
            MaterialTheme.typography.bodyMedium
          },
        color =
          if (highlight) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun ExifQuickActionChip(
  icon: ImageVector,
  label: String,
  bg: Color,
  fg: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize(),
    shape = RoundedCornerShape(14.dp),
    color = bg,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun aspectRatioLabel(aspectRatio: Float): String =
  when {
    aspectRatio >= 1.7f -> "16:9 Wide"
    aspectRatio >= 1.25f -> "4:3 Landscape"
    aspectRatio in 0.92f..1.08f -> "1:1 Square"
    else -> "3:4 Portrait"
  }

/**
 * Full-bleed video playback inside the lightbox: Media3 ExoPlayer wired to a
 * PlayerView with native seek/controls. Audio focus is handled so music
 * pauses during playback, and the player is released when the item changes
 * or the lightbox closes.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LightboxVideoPlayer(uriString: String, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val player =
    remember(uriString) {
      ExoPlayer.Builder(context)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
          /* handleAudioFocus = */ true,
        )
        .build()
        .apply {
          setMediaItem(ExoMediaItem.fromUri(uriString))
          prepare()
          playWhenReady = true
        }
    }
  DisposableEffect(uriString) { onDispose { player.release() } }

  AndroidView(
    factory = { ctx ->
      PlayerView(ctx).apply {
        this.player = player
        useController = true
        setShowPreviousButton(false)
        setShowNextButton(false)
      }
    },
    update = { view -> if (view.player !== player) view.player = player },
    modifier = modifier,
  )
}
