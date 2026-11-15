package com.example.ui.components.people

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.exifinterface.media.ExifInterface
import com.example.data.ai.FaceCropBounds
import com.example.data.model.GalleryMediaItem
import com.example.ui.components.GalleryMediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android port of Ente's face-thumbnail cache/generator presentation path.
 * Decodes only the local media URI, applies EXIF orientation before cropping,
 * and gracefully falls back to the source image when face metadata is unavailable.
 */
@Composable
fun FaceThumbnail(
  item: GalleryMediaItem,
  bounds: FaceCropBounds?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val bitmap by produceState<Bitmap?>(initialValue = null, item.uriString, bounds) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        val uri = item.uriString?.let(Uri::parse) ?: return@runCatching null
        val resolver = context.contentResolver
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dimensions) }
        if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(dimensions.outWidth, dimensions.outHeight) / sample > 1600) {
          sample *= 2
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply {
              inSampleSize = sample
              inPreferredConfig = Bitmap.Config.ARGB_8888
            },
          )
        } ?: return@runCatching null

        val orientation = runCatching {
          resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_NORMAL,
            )
          } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
          ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
          ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
          ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
          }
          ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
          }
        }
        val oriented = if (!matrix.isIdentity) {
          Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
          }
        } else decoded

        val face = bounds ?: return@runCatching oriented
        val left = face.left.coerceIn(0f, 1f)
        val top = face.top.coerceIn(0f, 1f)
        val right = face.right.coerceIn(0f, 1f)
        val bottom = face.bottom.coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return@runCatching oriented

        // A little context around the face produces a more useful avatar crop.
        val padX = (right - left) * 0.45f
        val padY = (bottom - top) * 0.45f
        val x0 = ((left - padX).coerceAtLeast(0f) * oriented.width)
          .toInt().coerceIn(0, oriented.width - 1)
        val y0 = ((top - padY).coerceAtLeast(0f) * oriented.height)
          .toInt().coerceIn(0, oriented.height - 1)
        val x1 = ((right + padX).coerceAtMost(1f) * oriented.width)
          .toInt().coerceIn(x0 + 1, oriented.width)
        val y1 = ((bottom + padY).coerceAtMost(1f) * oriented.height)
          .toInt().coerceIn(y0 + 1, oriented.height)
        Bitmap.createBitmap(oriented, x0, y0, x1 - x0, y1 - y0).also {
          if (it !== oriented) oriented.recycle()
        }
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
