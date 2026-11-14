package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.local.SampleMediaCatalog
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaType
import com.example.data.model.TonePreset
import com.example.ui.theme.LocalToadColors

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
    TonePreset.BOTANICAL_VIVID -> {
      val matrix = ColorMatrix().apply { setToSaturation(1.38f) }
      ColorFilter.colorMatrix(matrix)
    }
    TonePreset.WARM_FILM -> {
      val matrix =
        ColorMatrix(
          floatArrayOf(
            1.12f, 0.05f, 0f, 0f, 10f,
            0.02f, 1.04f, 0f, 0f, 4f,
            0f, 0f, 0.86f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      ColorFilter.colorMatrix(matrix)
    }
    TonePreset.MONOCHROME -> {
      val matrix = ColorMatrix().apply { setToSaturation(0f) }
      ColorFilter.colorMatrix(matrix)
    }
  }

@Composable
fun GalleryMediaImage(
  item: GalleryMediaItem,
  contentScale: ContentScale = ContentScale.Crop,
  modifier: Modifier = Modifier,
) {
  val filter = toneColorFilter(item.tonePreset)
  if (!item.uriString.isNullOrBlank()) {
    AsyncImage(
      model =
        ImageRequest.Builder(LocalContext.current)
          .data(item.uriString)
          .crossfade(true)
          .build(),
      contentDescription = item.title,
      contentScale = contentScale,
      colorFilter = filter,
      modifier = modifier,
    )
  } else {
    Image(
      painter = painterResource(SampleMediaCatalog.resolveDrawableRes(item.drawableResName)),
      contentDescription = item.title,
      contentScale = contentScale,
      colorFilter = filter,
      modifier = modifier,
    )
  }
}

@Composable
fun DecryptedVaultImage(
  item: GalleryMediaItem,
  decryptedBytes: ByteArray?,
  contentScale: ContentScale = ContentScale.Crop,
  modifier: Modifier = Modifier,
) {
  val bitmap =
    remember(decryptedBytes) {
      decryptedBytes?.let { bytes ->
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
          .getOrNull()
      }
    }
  if (bitmap != null) {
    Image(
      bitmap = bitmap,
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
  isSelectionMode: Boolean = false,
  isSelected: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val ratio = if (forceSquare) 1f else item.aspectRatio.coerceIn(0.62f, 1.45f)
  val scale by
    animateFloatAsState(
      targetValue = if (isSelected) 0.92f else 1f,
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
        .clip(RoundedCornerShape(18.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (isSelected) 2.5.dp else 1.dp,
        color = if (isSelected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
      ),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      GalleryMediaImage(item = item, modifier = Modifier.fillMaxSize())

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
              .padding(7.dp)
              .size(22.dp)
              .clip(CircleShape)
              .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.50f)
              )
              .border(
                width = 1.5.dp,
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
              modifier = Modifier.size(14.dp),
            )
          }
        }
      } else if (item.mediaType == MediaType.SCREENSHOT) {
        Box(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(6.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color.Black.copy(alpha = 0.68f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "OCR",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White,
          )
        }
      }

      if (item.isFavorite) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(6.dp)
              .size(22.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.52f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorited",
            tint = Color(0xFFFB7185),
            modifier = Modifier.size(13.dp),
          )
        }
      }

      if (item.mediaType == MediaType.VIDEO && !item.durationLabel.isNullOrBlank()) {
        Row(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(6.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color.Black.copy(alpha = 0.74f))
              .padding(horizontal = 6.dp, vertical = 3.dp),
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

      if (item.mediaType == MediaType.GIF) {
        Box(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(6.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color.Black.copy(alpha = 0.78f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
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
  mlIndexMb: Float,
  cacheMb: Float,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val modelsMb =
    (if (faceModelInstalled) 37.6f else 0f) + (if (semanticModelInstalled) 213.0f else 0f)
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
