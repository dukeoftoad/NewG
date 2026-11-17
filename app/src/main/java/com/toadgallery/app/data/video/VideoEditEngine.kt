package com.toadgallery.app.data.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Executes a validated [VideoEditPlan] with androidx.media3 Transformer:
 * writes a re-encoded MP4 into the app-owned `edited_media` directory. The
 * caller ([com.toadgallery.app.data.repository.GalleryRepository.saveEditedVideoCopy])
 * owns MediaStore export and item insertion; this class only produces the file.
 *
 * Trim uses [MediaItem.ClippingConfiguration]; mute uses
 * [EditedMediaItem.Builder.setRemoveAudio]; speed uses
 * [EditedMediaItem.Builder.setSpeed] — media3 re-times video AND audio with
 * pitch preserved from one call. Transformer construction and callbacks run
 * on the application main Looper (the API requires a Looper thread), while
 * the caller suspends — so the export never blocks the UI thread, it just
 * lives on it.
 *
 * The transform itself needs real device codecs (MediaCodec) and therefore has
 * no JVM unit test; everything about a plan is validated in VideoEditPlan, and
 * the save path is covered by repository tests.
 */
@OptIn(UnstableApi::class)
class VideoEditEngine(private val context: Context) {

  /**
   * Runs the plan against [inputUri] and returns the finished MP4.
   * Output lives in `filesDir/edited_media/[outputFileName]`.
   *
   * @throws VideoEditException when the transformer reports an error.
   */
  suspend fun execute(
    plan: VideoEditPlan,
    inputUri: String,
    outputFileName: String,
  ): File =
    withContext(Dispatchers.Main) {
      val outDir = File(context.filesDir, "edited_media").apply { mkdirs() }
      val outFile = File(outDir, outputFileName)

      val mediaItem =
        MediaItem.Builder()
          .setUri(inputUri)
          .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
              .setStartPositionMs(plan.trimStartMs)
              .apply { plan.trimEndMs?.let { setEndPositionMs(it) } }
              .build(),
          )
          .build()

      val edited =
        EditedMediaItem.Builder(mediaItem)
          .setRemoveAudio(plan.removeAudio)
          .apply {
            if (plan.changesSpeed) {
              setSpeed(constantSpeedProvider(plan.speed))
            }
          }
          .build()

      suspendCancellableCoroutine { cont ->
        val listener =
          object : Transformer.Listener {
            override fun onCompleted(
              composition: androidx.media3.transformer.Composition,
              exportResult: ExportResult,
            ) {
              if (cont.isActive) cont.resume(outFile)
            }

            override fun onError(
              composition: androidx.media3.transformer.Composition,
              exportResult: ExportResult,
              exception: ExportException,
            ) {
              if (cont.isActive) cont.resumeWithException(VideoEditException("transform failed", exception))
            }
          }
        val transformer = Transformer.Builder(context).addListener(listener).build()
        cont.invokeOnCancellation { runCatching { transformer.cancel() } }
        transformer.start(edited, outFile.absolutePath)
      }
    }

  private fun constantSpeedProvider(speed: Float): SpeedProvider =
    object : SpeedProvider {
      override fun getSpeed(timeUs: Long): Float = speed
      override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

  class VideoEditException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
}
