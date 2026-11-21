package com.toadgallery.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toadgallery.app.ui.components.GalleryMediaImage
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.theme.ToadMarkTokens
import com.toadgallery.app.ui.viewmodel.GalleryUiState
import com.toadgallery.app.ui.viewmodel.MapDisplayMode
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun PlacesMapContent(
  uiState: GalleryUiState,
  onSelectMode: (MapDisplayMode) -> Unit,
  onSelectPin: (Long) -> Unit,
  onOpenLightbox: (Long) -> Unit,
  onNotifyMissingGps: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val geoItems = uiState.geoItems
  val selectedItem =
    remember(geoItems, uiState.selectedMapPinId) {
      geoItems.firstOrNull { it.id == uiState.selectedMapPinId } ?: geoItems.firstOrNull()
    }

  // Compute spatial cluster counts once per geoItems change
  val pinClusterCounts =
    remember(geoItems) {
      geoItems.associate { anchor ->
        val nearby =
          geoItems.count { other ->
            hypot((anchor.mapX - other.mapX).toDouble(), (anchor.mapY - other.mapY).toDouble()) <=
              0.09
          }
        anchor.id to nearby.coerceAtLeast(1)
      }
    }

  val pulseAnim = rememberInfiniteTransition(label = "pinPulse")
  val pulseScale by
    pulseAnim.animateFloat(
      initialValue = 1.0f,
      targetValue = 1.38f,
      animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
      label = "pulseScale",
    )
  val pulseAlpha by
    pulseAnim.animateFloat(
      initialValue = 0.55f,
      targetValue = 0.0f,
      animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
      label = "pulseAlpha",
    )

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Sub-Header + Pins/Heatmap Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Places Map",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "${geoItems.size} geotagged memories • Local EXIF only",
          style = MaterialTheme.typography.bodySmall,
          color = extended.textMuted,
        )
      }

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extended.borderSubtle),
      ) {
        Row(modifier = Modifier.padding(4.dp)) {
          MapDisplayMode.entries.forEach { mode ->
            val selected = uiState.mapMode == mode
            Surface(
              onClick = { onSelectMode(mode) },
              modifier = Modifier.testTag("map_mode_${mode.name.lowercase()}"),
              shape = CircleShape,
              color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            ) {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
              )
            }
          }
        }
      }
    }

    // Interactive Vector Map Canvas
    Surface(
      modifier = Modifier.fillMaxWidth().weight(1f),
      shape = RoundedCornerShape(ToadMarkTokens.RadiusSheet),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val dimColor = extended.textDim

        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height

          val lakePath =
            Path().apply {
              moveTo(w * 0.37f, h * 0.42f)
              quadraticTo(w * 0.49f, h * 0.39f, w * 0.54f, h * 0.50f)
              quadraticTo(w * 0.50f, h * 0.61f, w * 0.39f, h * 0.57f)
              quadraticTo(w * 0.32f, h * 0.51f, w * 0.37f, h * 0.42f)
              close()
            }
          drawPath(path = lakePath, color = primaryColor.copy(alpha = 0.18f))

          val roadStroke = Stroke(width = 6f)
          drawLine(
            color = primaryColor.copy(alpha = 0.28f),
            start = Offset(0f, h * 0.28f),
            end = Offset(w, h * 0.35f),
            strokeWidth = roadStroke.width,
          )
          drawLine(
            color = primaryColor.copy(alpha = 0.28f),
            start = Offset(0f, h * 0.68f),
            end = Offset(w, h * 0.61f),
            strokeWidth = roadStroke.width,
          )
          drawLine(
            color = primaryColor.copy(alpha = 0.28f),
            start = Offset(w * 0.28f, 0f),
            end = Offset(w * 0.36f, h),
            strokeWidth = roadStroke.width,
          )
          drawLine(
            color = primaryColor.copy(alpha = 0.28f),
            start = Offset(w * 0.69f, 0f),
            end = Offset(w * 0.63f, h),
            strokeWidth = roadStroke.width,
          )

          drawLine(
            color = dimColor.copy(alpha = 0.35f),
            start = Offset(w * 0.10f, h * 0.08f),
            end = Offset(w * 0.90f, h * 0.88f),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
          )

          if (uiState.mapMode == MapDisplayMode.HEATMAP) {
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors =
                    listOf(
                      Color(0x99EF4444),
                      Color(0x66F59E0B),
                      Color.Transparent,
                    ),
                  center = Offset(w * 0.46f, h * 0.52f),
                  radius = w * 0.26f,
                ),
              radius = w * 0.26f,
              center = Offset(w * 0.46f, h * 0.52f),
            )
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors =
                    listOf(
                      Color(0x8810B981),
                      Color(0x5538BDF8),
                      Color.Transparent,
                    ),
                  center = Offset(w * 0.62f, h * 0.62f),
                  radius = w * 0.22f,
                ),
              radius = w * 0.22f,
              center = Offset(w * 0.62f, h * 0.62f),
            )
          }
        }

        Text(
          text = "JUBILEE HILLS",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = extended.textDim.copy(alpha = 0.7f),
          modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 54.dp),
        )
        Text(
          text = "SECUNDERABAD",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = extended.textDim.copy(alpha = 0.7f),
          modifier = Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 88.dp),
        )
        Text(
          text = "OLD CITY",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = extended.textDim.copy(alpha = 0.7f),
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
        )

        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        geoItems.forEach { item ->
          val isSelected = selectedItem?.id == item.id
          val pinSize = if (isSelected) 54.dp else 46.dp
          val clusterCount = pinClusterCounts[item.id] ?: 1

          Box(
            modifier =
              Modifier.offset {
                  IntOffset(
                    x = (boxW * item.mapX - 27.dp.toPx()).roundToInt(),
                    y = (boxH * item.mapY - 27.dp.toPx()).roundToInt(),
                  )
                }
                .size(pinSize)
                .testTag("map_pin_${item.id}")
                .clickable { onSelectPin(item.id) },
            contentAlignment = Alignment.Center,
          ) {
            if (isSelected) {
              // Defer pulse scale & alpha reads to graphicsLayer so map never relayouts at 60fps
              Box(
                modifier =
                  Modifier.size(52.dp)
                    .graphicsLayer {
                      scaleX = pulseScale
                      scaleY = pulseScale
                      alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
              )
            }

            Box(
              modifier =
                Modifier.size(pinSize)
                  .clip(RoundedCornerShape(16.dp))
                  .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else extended.surfaceHighest
                  )
                  .border(
                    width = 2.dp,
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    shape = RoundedCornerShape(16.dp),
                  )
                  .padding(2.dp)
            ) {
              GalleryMediaImage(
                item = item,
                thumbnailSizePx = 160,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
              )
            }

            Box(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .offset(x = 4.dp, y = (-4).dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary)
                  .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text(
                text = "$clusterCount",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                  ),
                color = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }

        Surface(
          onClick = onNotifyMissingGps,
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(12.dp)
              .testTag("map_gps_summary_pill"),
          shape = CircleShape,
          color = extended.glassSurface,
          border = BorderStroke(1.dp, extended.borderSubtle),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Explore,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = "${geoItems.size} Geotagged",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    if (selectedItem != null) {
      Surface(
        onClick = { onOpenLightbox(selectedItem.id) },
        modifier = Modifier.fillMaxWidth().testTag("map_selected_memory_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extended.borderStrong),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          GalleryMediaImage(
            item = selectedItem,
            thumbnailSizePx = 220,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = selectedItem.locationName.uppercase(),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = selectedItem.title,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${selectedItem.dateLabel} • Tap to inspect EXIF",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.OpenInFull,
              contentDescription = "Inspect photo",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    } else {
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}
