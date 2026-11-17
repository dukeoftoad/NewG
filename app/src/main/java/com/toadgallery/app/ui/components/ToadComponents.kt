package com.toadgallery.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toadgallery.app.data.local.SampleMediaCatalog
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.TonePreset
import com.toadgallery.app.ui.heroSharedElement
import com.toadgallery.app.ui.theme.LocalToadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VividToneFilter: ColorFilter by lazy {
  ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.38f) })
}

private val WarmFilmToneFilter: ColorFilter by lazy {
  ColorFilter.colorMatrix(
    ColorMatrix(
      floatArrayOf(
        1.12f, 0.05f, 0f, 0f, 10f,
        0.02f, 1.04f, 0f, 0f, 4f,
        0f, 0f, 0.86f, 0f, -6f,
        0f, 0f, 0f, 1f, 0f,
      )
    )
  )
}

private val MonochromeToneFilter: ColorFilter by lazy {
  ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
}

private val vaultBitmapCache = LruCache<String, ImageBitmap>(48)

@Composable
fun ToadBrandIcon(
  modifier: Modifier = Modifier,
) {
  val containerColor = MaterialTheme.colorScheme.primary
  val silhouetteColor = MaterialTheme.colorScheme.onPrimary

  Box(
    modifier =
      modifier
        .size(32.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(containerColor)
        .semantics { contentDescription = "toadGALLERY brand mark" }
        .testTag("toad_brand_icon"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(22.dp)) {
      val w = size.width
      val h = size.height

      // Raised left and right toad eye ridges
      drawCircle(
        color = silhouetteColor,
        radius = w * 0.16f,
        center = Offset(w * 0.30f, h * 0.32f),
      )
      drawCircle(
        color = silhouetteColor,
        radius = w * 0.16f,
        center = Offset(w * 0.70f, h * 0.32f),
      )

      // Wide rounded toad head & body silhouette
      drawOval(
        color = silhouetteColor,
        topLeft = Offset(w * 0.08f, h * 0.32f),
        size = Size(w * 0.84f, h * 0.56f),
      )

      // Subtle toad hind legs tucked at the bottom corners
      drawRoundRect(
        color = silhouetteColor,
        topLeft = Offset(w * 0.05f, h * 0.64f),
        size = Size(w * 0.24f, h * 0.26f),
        cornerRadius = CornerRadius(w * 0.12f, h * 0.12f),
      )
      drawRoundRect(
        color = silhouetteColor,
        topLeft = Offset(w * 0.71f, h * 0.64f),
        size = Size(w * 0.24f, h * 0.26f),
        cornerRadius = CornerRadius(w * 0.12f, h * 0.12f),
      )

      // Cutout eye pupils using badge container color
      drawCircle(
        color = containerColor,
        radius = w * 0.068f,
        center = Offset(w * 0.30f, h * 0.32f),
      )
      drawCircle(
        color = containerColor,
        radius = w * 0.068f,
        center = Offset(w * 0.70f, h * 0.32f),
      )

      // Gentle toad smile line
      drawArc(
        color = containerColor,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.26f, h * 0.42f),
        size = Size(w * 0.48f, h * 0.24f),
        style = Stroke(width = w * 0.065f, cap = StrokeCap.Round),
      )
    }
  }
}

fun Modifier.pinchToZoomGridColumns(
  currentColumns: Int,
  minColumns: Int = 2,
  maxColumns: Int = 10,
  onColumnsChange: (Int) -> Unit,
  onPinchActiveChange: (Boolean) -> Unit = {},
): Modifier = composed {
  val latestColumns by rememberUpdatedState(currentColumns)
  val latestOnColumnsChange by rememberUpdatedState(onColumnsChange)
  val latestOnPinchActive by rememberUpdatedState(onPinchActiveChange)
  val haptics = LocalHapticFeedback.current

  pointerInput(minColumns, maxColumns) {
    val stepThreshold = 1.16f
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false)
      var cumulativeZoom = 1f
      var trackedColumns = latestColumns.coerceIn(minColumns, maxColumns)
      var pinchActive = false

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressedCount = event.changes.count { it.pressed }
        if (pressedCount == 0) {
          if (pinchActive) {
            latestOnPinchActive(false)
          }
          break
        }
        if (pressedCount >= 2) {
          if (!pinchActive) {
            pinchActive = true
            trackedColumns = latestColumns.coerceIn(minColumns, maxColumns)
            cumulativeZoom = 1f
            latestOnPinchActive(true)
          }
          val zoomChange = event.calculateZoom()
          if (zoomChange != 1f && !zoomChange.isNaN()) {
            cumulativeZoom *= zoomChange
            while (cumulativeZoom >= stepThreshold) {
              val next = (trackedColumns - 1).coerceIn(minColumns, maxColumns)
              if (next != trackedColumns) {
                trackedColumns = next
                latestOnColumnsChange(next)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
              cumulativeZoom /= stepThreshold
            }
            while (cumulativeZoom <= 1f / stepThreshold) {
              val next = (trackedColumns + 1).coerceIn(minColumns, maxColumns)
              if (next != trackedColumns) {
                trackedColumns = next
                latestOnColumnsChange(next)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
              cumulativeZoom *= stepThreshold
            }
          }
          event.changes.forEach { it.consume() }
        } else if (pinchActive) {
          // Consume remaining single-finger lift transition after pinch so scroll does not jump
          event.changes.forEach { it.consume() }
        }
      }
    }
  }
}

@Composable
fun ToadSwitch(
  checked: Boolean,
  onCheckedChange: () -> Unit,
  contentDesc: String,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val trackColor by
    animateColorAsState(
      targetValue =
        if (checked) MaterialTheme.colorScheme.primary else extended.surfaceHighest,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "switchTrack",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (checked) MaterialTheme.colorScheme.primary else extended.borderStrong,
      label = "switchBorder",
    )
  val thumbOffset by
    animateDpAsState(
      targetValue = if (checked) 22.dp else 4.dp,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
      label = "switchOffset",
    )
  val thumbSize by
    animateDpAsState(
      targetValue = if (checked) 20.dp else 16.dp,
      label = "switchSize",
    )

  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .semantics { contentDescription = contentDesc }
        .clip(CircleShape)
        .clickable(role = Role.Switch, onClick = onCheckedChange)
        .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.size(width = 48.dp, height = 28.dp)
          .clip(CircleShape)
          .background(trackColor)
          .border(1.5.dp, borderColor, CircleShape),
      contentAlignment = Alignment.CenterStart,
    ) {
      Box(
        modifier =
          Modifier.offset(x = thumbOffset)
            .size(thumbSize)
            .clip(CircleShape)
            .background(
              if (checked) MaterialTheme.colorScheme.onPrimary else extended.textMuted
            )
      )
    }
  }
}

fun toneColorFilter(preset: TonePreset): ColorFilter? =
  when (preset) {
    TonePreset.NATURAL -> null
    TonePreset.BOTANICAL_VIVID -> VividToneFilter
    TonePreset.WARM_FILM -> WarmFilmToneFilter
    TonePreset.MONOCHROME -> MonochromeToneFilter
  }

@Composable
fun GalleryMediaImage(
  item: GalleryMediaItem,
  contentScale: ContentScale = ContentScale.Crop,
  thumbnailSizePx: Int? = 384,
  modifier: Modifier = Modifier,
  heroKey: String? = null,
) {
  val context = LocalContext.current
  val hasNonDestructiveBitmap = !item.editedUriString.isNullOrBlank()
  val filter = remember(hasNonDestructiveBitmap, item.tonePreset) {
    if (hasNonDestructiveBitmap) null else toneColorFilter(item.tonePreset)
  }
  val effectiveUri = item.editedUriString?.takeIf { it.isNotBlank() } ?: item.uriString
  if (!effectiveUri.isNullOrBlank()) {
    val request = remember(context, effectiveUri, thumbnailSizePx) {
      ImageRequest.Builder(context)
        .data(effectiveUri)
        .apply {
          if (thumbnailSizePx != null) {
            size(thumbnailSizePx)
            crossfade(false)
          } else {
            crossfade(120)
          }
        }
        .allowHardware(true)
        .build()
    }
    AsyncImage(
      model = request,
      contentDescription = item.title,
      contentScale = contentScale,
      colorFilter = filter,
      modifier = modifier.heroSharedElement(heroKey),
    )
  } else {
    Image(
      painter = painterResource(SampleMediaCatalog.resolveDrawableRes(item.drawableResName)),
      contentDescription = item.title,
      contentScale = contentScale,
      colorFilter = filter,
      modifier = modifier.heroSharedElement(heroKey),
    )
  }
}

@Composable
fun DecryptedVaultImage(
  item: GalleryMediaItem,
  decryptedBytes: ByteArray?,
  contentScale: ContentScale = ContentScale.Crop,
  maxDimensionPx: Int = 512,
  modifier: Modifier = Modifier,
) {
  val cacheKey = remember(item.id, decryptedBytes?.size, maxDimensionPx) {
    if (decryptedBytes == null) null else "${item.id}:${decryptedBytes.size}:$maxDimensionPx"
  }
  val cachedImmediate = remember(cacheKey) {
    cacheKey?.let { synchronized(vaultBitmapCache) { vaultBitmapCache.get(it) } }
  }
  val bitmap by
    produceState<ImageBitmap?>(
      initialValue = cachedImmediate,
      cacheKey,
      decryptedBytes,
      maxDimensionPx,
    ) {
      if (value == null && decryptedBytes != null && cacheKey != null) {
        value =
          withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, bounds)
                var sample = 1
                val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
                if (maxEdge > maxDimensionPx && maxDimensionPx > 0) {
                  while (maxEdge / sample > maxDimensionPx * 2) sample *= 2
                }
                BitmapFactory.decodeByteArray(
                    decryptedBytes,
                    0,
                    decryptedBytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                  )
                  ?.asImageBitmap()
                  ?.also { decoded ->
                    synchronized(vaultBitmapCache) { vaultBitmapCache.put(cacheKey, decoded) }
                  }
              }
              .getOrNull()
          }
      }
    }
  val activeBitmap = bitmap
  if (activeBitmap != null) {
    Image(
      bitmap = activeBitmap,
      contentDescription = item.title,
      contentScale = contentScale,
      colorFilter = toneColorFilter(item.tonePreset),
      modifier = modifier,
    )
  } else if (!item.drawableResName.isNullOrBlank() || !item.uriString.isNullOrBlank()) {
    GalleryMediaImage(item = item, contentScale = contentScale, modifier = modifier)
  } else {
    Box(
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = item.title,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailCard(
  item: GalleryMediaItem,
  forceSquare: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  onDoubleClick: (() -> Unit)? = null,
  isSelectionMode: Boolean = false,
  isSelected: Boolean = false,
  compactMode: Boolean = false,
  heroKey: String? = null,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val ratio = if (forceSquare) 1f else item.aspectRatio.coerceIn(0.62f, 1.45f)
  val cornerRadius = if (compactMode) 6.dp else 12.dp
  val scale by
    animateFloatAsState(
      targetValue = if (isSelectionMode && isSelected) 0.92f else 1f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "selectScale",
    )

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(ratio)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .testTag("media_card_${item.id}")
        .clip(RoundedCornerShape(cornerRadius))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
          onDoubleClick = onDoubleClick,
        ),
    shape = RoundedCornerShape(cornerRadius),
    color = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width =
          if (isSelected) (if (compactMode) 2.dp else 2.5.dp)
          else (if (compactMode) 0.5.dp else 1.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
      ),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      GalleryMediaImage(
        item = item,
        thumbnailSizePx = if (compactMode) 220 else 384,
        heroKey = heroKey,
        modifier = Modifier.fillMaxSize(),
      )

      if (isSelected) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        )
      }

      if (isSelectionMode) {
        Box(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(if (compactMode) 3.dp else 6.dp)
              .size(if (compactMode) 16.dp else 22.dp)
              .clip(CircleShape)
              .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.50f)
              )
              .border(
                width = if (compactMode) 1.dp else 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                shape = CircleShape,
              ),
          contentAlignment = Alignment.Center,
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = "Selected",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(if (compactMode) 10.dp else 14.dp),
            )
          }
        }
      } else if (!compactMode && item.mediaType == MediaType.SCREENSHOT) {
        Box(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(5.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color.Black.copy(alpha = 0.68f))
              .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = "OCR",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White,
          )
        }
      } else if (!compactMode && item.hasNonDestructiveEdits) {
        Box(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(5.dp)
              .size(20.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.58f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoFixHigh,
            contentDescription = "Edited",
            tint = Color(0xFF6EE7B7),
            modifier = Modifier.size(11.dp),
          )
        }
      }

      if (!compactMode && item.isFavorite) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(5.dp)
              .size(20.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.52f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorited",
            tint = Color(0xFFFB7185),
            modifier = Modifier.size(12.dp),
          )
        }
      }

      if (!compactMode && item.mediaType == MediaType.VIDEO && !item.durationLabel.isNullOrBlank()) {
        Row(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(5.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color.Black.copy(alpha = 0.74f))
              .padding(horizontal = 5.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp),
          )
          Text(
            text = item.durationLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White,
          )
        }
      }

      if (!compactMode && item.mediaType == MediaType.GIF) {
        Box(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(5.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color.Black.copy(alpha = 0.78f))
              .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = "GIF",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
              ),
            color = Color(0xFF6EE7B7),
          )
        }
      }
    }
  }
}

@Composable
fun StorageBreakdownBar(
  faceModelInstalled: Boolean,
  semanticModelInstalled: Boolean,
  depthModelInstalled: Boolean = false,
  mlIndexMb: Float,
  cacheMb: Float,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val modelsMb =
    (if (faceModelInstalled) 35.9f else 0f) +
      (if (semanticModelInstalled) 213.0f else 0f) +
      (if (depthModelInstalled) 27.3f else 0f)
  val total = (14.2f + modelsMb + mlIndexMb + cacheMb).coerceAtLeast(1f)

  val dbWeight = (14.2f / total).coerceAtLeast(0.06f)
  val modelWeight = (modelsMb / total).coerceAtLeast(0.03f)
  val indexWeight = (mlIndexMb / total).coerceAtLeast(0.03f)
  val cacheWeight = (cacheMb / total).coerceAtLeast(0.03f)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(CircleShape)
        .background(extended.surfaceHighest)
        .padding(2.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(
      modifier =
        Modifier.weight(dbWeight)
          .fillMaxHeight()
          .clip(RoundedCornerShape(topStart = 99.dp, bottomStart = 99.dp))
          .background(Color(0xFF38BDF8))
    )
    Box(
      modifier =
        Modifier.weight(modelWeight)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.primary)
    )
    Box(
      modifier =
        Modifier.weight(indexWeight)
          .fillMaxHeight()
          .background(Color(0xFFA78BFA))
    )
    Box(
      modifier =
        Modifier.weight(cacheWeight)
          .fillMaxHeight()
          .clip(RoundedCornerShape(topEnd = 99.dp, bottomEnd = 99.dp))
          .background(Color(0xFFFBBF24))
    )
  }
}

@Composable
fun SectionHeading(
  title: String,
  trailing: String? = null,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (!trailing.isNullOrBlank()) {
      Text(
        text = trailing,
        style = MaterialTheme.typography.bodySmall,
        color = extended.textDim,
      )
    }
  }
}

@Composable
fun HeroGradientCard(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val extended = LocalToadColors.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(1.dp, extended.borderStrong),
    color = Color.Transparent,
  ) {
    Box(
      modifier =
        Modifier.background(
          Brush.linearGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.primaryContainer,
                extended.surfaceHigh,
              )
          )
        )
    ) {
      Column(modifier = Modifier.padding(16.dp)) { content() }
    }
  }
}
