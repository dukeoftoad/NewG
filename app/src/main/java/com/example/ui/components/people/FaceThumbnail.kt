package com.example.ui.components.people

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.data.ai.FaceCropBounds
import com.example.data.ai.FaceCropper
import com.example.data.model.GalleryMediaItem
import com.example.ui.components.GalleryMediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
  val bitmap by produceState<Bitmap?>(initialValue = null, item.uriString, bounds, sourceRevision) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        val uri = item.uriString?.let(Uri::parse) ?: return@runCatching null
        val cropBytes = FaceCropper.crop(context, uri, bounds, sourceRevision) ?: return@runCatching null
        BitmapFactory.decodeByteArray(cropBytes, 0, cropBytes.size)
      }.getOrNull()
    }
  }

  DisposableEffect(bitmap) {
    onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
  }

  if (bitmap != null && !bitmap!!.isRecycled) {
    Image(
      bitmap = bitmap!!.asImageBitmap(),
      contentDescription = "Detected face in ${item.title}",
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  } else {
    GalleryMediaImage(item = item, modifier = modifier)
  }
}
