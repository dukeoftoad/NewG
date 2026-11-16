package com.toadgallery.app.ui.components.people

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.toadgallery.app.data.ai.FaceCropBounds
import com.toadgallery.app.data.ai.FaceCropper
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.ui.components.GalleryMediaImage

/**
 * Compose presentation for the local Ente-inspired face crop cache.
 * The cropper handles background decode, EXIF orientation, memory/disk cache,
 * and returns null when the source cannot be cropped so the gallery image is
 * used as a safe fallback.
 */
@Composable
fun FaceThumbnail(
  item: GalleryMediaItem,
  bounds: FaceCropBounds?,
  sourceRevision: Long = 0L,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val parsedUri = remember(item.uriString) { item.uriString?.let(Uri::parse) }
  val cachedImmediate =
    remember(parsedUri, bounds, sourceRevision) {
      FaceCropper.peekCachedBitmap(parsedUri, bounds, sourceRevision)
    }

  val bitmap by
    produceState<Bitmap?>(initialValue = cachedImmediate, parsedUri, bounds, sourceRevision) {
      if (value == null && parsedUri != null) {
        value = runCatching { FaceCropper.cropBitmap(context, parsedUri, bounds, sourceRevision) }.getOrNull()
      }
    }

  val activeBitmap = bitmap
  if (activeBitmap != null && !activeBitmap.isRecycled) {
    FaceThumbnailBitmap(
      bitmap = activeBitmap,
      contentDescription = "Detected face in ${item.title}",
      modifier = modifier,
    )
  } else {
    GalleryMediaImage(item = item, thumbnailSizePx = 256, modifier = modifier)
  }
}

/**
 * Displays a bitmap without taking ownership of its lifetime. Compose may keep
 * recorded draw commands after this node leaves composition, so the producer
 * must not recycle the bitmap from a DisposableEffect/onDispose callback.
 * Once no longer referenced by Compose or its producer, the bitmap is reclaimed
 * normally by the runtime.
 */
@Composable
internal fun FaceThumbnailBitmap(
  bitmap: Bitmap,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
  Image(
    bitmap = imageBitmap,
    contentDescription = contentDescription,
    contentScale = ContentScale.Crop,
    modifier = modifier,
  )
}
