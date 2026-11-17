package com.toadgallery.app.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pulls a still frame out of a video via [MediaMetadataRetriever] — the
 * "extract frame" action (v2.2.0 E1). Needs real device codecs, so the
 * extraction itself has no JVM test; the label math is pure and the save
 * path is covered by repository tests.
 */
class VideoFrameExtractor(private val context: Context) {

  /**
   * Returns the frame at [positionMs] (closest sync frame — fast and
   * deterministic; exact-frame decoding is not worth the battery cost here).
   * Falls back to the first frame when the position yields none (corrupt
   * tail / seeking edge).
   *
   * @throws IllegalStateException when the video has no decodable frames.
   */
  suspend fun extractFrame(inputUri: String, positionMs: Long): Bitmap =
    withContext(Dispatchers.IO) {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, Uri.parse(inputUri))
        retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          ?: retriever.getFrameAtTime(0L)
          ?: throw IllegalStateException("video has no decodable frames: $inputUri")
      } finally {
        retriever.release()
      }
    }

  companion object {
    /**
     * "Frame at mm:ss" label in the same %02d:%02d shape the scanner and
     * [VideoEditPlan] use, so provenance stays readable in the grid.
     */
    fun frameLabel(positionMs: Long): String {
      val totalSec = (positionMs / 1000L).coerceAtLeast(0L)
      return String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
    }
  }
}
