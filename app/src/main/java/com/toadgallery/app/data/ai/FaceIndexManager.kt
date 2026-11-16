package com.toadgallery.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

data class FaceIndexState(
  val running: Boolean = false,
  val progress: Int = 0,
  val processed: Int = 0,
  val total: Int = 0,
  val facesFound: Int = 0,
  val failures: Int = 0,
  val status: String = "Ready to index local photos",
) {
  val progressLabel: String
    get() = if (running) "$processed / $total photos • $facesFound faces"
    else status
}

/**
 * Incremental, on-device face indexer. Only regular gallery photos already represented in
 * Room are considered; videos, Hidden Vault items, and trashed items are excluded.
 */
class FaceIndexManager(
  context: Context,
  private val dao: GalleryDao,
  private val modelManager: FaceModelManager,
  private val engine: FaceRecognitionEngine,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _state = MutableStateFlow(FaceIndexState())
  val state: StateFlow<FaceIndexState> = _state.asStateFlow()
  private var indexJob: Job? = null

  fun start(forceReindex: Boolean = false) {
    if (_state.value.running) return
    indexJob?.cancel()
    indexJob = scope.launch {
      if (!modelManager.state.value.isInstalled) {
        _state.value = FaceIndexState(status = "Download and verify the face model pack first")
        return@launch
      }
      val photos = dao.getPhotosForFaceIndex(force = forceReindex)
      if (photos.isEmpty()) {
        _state.value = FaceIndexState(progress = 100, status = "No eligible local photos to index")
        return@launch
      }
      var processed = 0
      var facesFound = 0
      var failures = 0
      _state.value = FaceIndexState(running = true, total = photos.size, status = "Preparing on-device face index")
      try {
        for (photo in photos) {
          kotlinx.coroutines.currentCoroutineContext().ensureActive()
          try {
            val bitmap = decodePhoto(photo)
            if (bitmap == null) {
              failures++
              dao.updateFaceIndexAndReconcile(photo.id, EMPTY_FACE_RESULT, System.currentTimeMillis())
            } else {
              val result = try {
                engine.analyze(bitmap, modelManager.detectorFile, modelManager.embedderFile).getOrThrow()
              } finally {
                bitmap.recycle()
              }
              dao.updateFaceIndexAndReconcile(photo.id, encodeResult(photo.id, result), System.currentTimeMillis())
              facesFound += result.faces.size
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            failures++
            // Mark unreadable/unsupported files as attempted to avoid an endless retry loop.
            dao.updateFaceIndexAndReconcile(photo.id, EMPTY_FACE_RESULT, System.currentTimeMillis())
          }
          processed++
          _state.value = FaceIndexState(
            running = true,
            progress = (processed * 100 / photos.size).coerceIn(0, 100),
            processed = processed,
            total = photos.size,
            facesFound = facesFound,
            failures = failures,
            status = "Analyzing photos on this device",
          )
        }
        _state.value = FaceIndexState(
          progress = 100,
          processed = processed,
          total = photos.size,
          facesFound = facesFound,
          failures = failures,
          status = "Face index ready • $facesFound faces in $processed photos",
        )
      } catch (cancelled: CancellationException) {
        _state.value = FaceIndexState(
          progress = if (photos.isEmpty()) 0 else processed * 100 / photos.size,
          processed = processed,
          total = photos.size,
          facesFound = facesFound,
          failures = failures,
          status = "Paused • $processed photos saved; resume to continue",
        )
        throw cancelled
      } finally {
        engine.close()
      }
    }
  }

  fun pause() {
    indexJob?.cancel()
  }

  suspend fun pauseAndWait() {
    indexJob?.cancelAndJoin()
    engine.close()
  }

  fun clear() {
    scope.launch {
      indexJob?.cancelAndJoin()
      dao.clearFaceIndexAndFeedback()
      _state.value = FaceIndexState(status = "Face index cleared")
    }
  }

  private suspend fun decodePhoto(item: MediaItemEntity): Bitmap? = withContext(Dispatchers.IO) {
    val rawUri = item.uriString ?: return@withContext null
    val uri = Uri.parse(rawUri)
    val resolver = appContext.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { input ->
      BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      })
    } ?: return@withContext null
    val orientation = runCatching {
      resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
      ) } ?: ExifInterface.ORIENTATION_NORMAL
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
      else -> return@withContext decoded
    }
    val corrected = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (corrected !== decoded) decoded.recycle()
    corrected
  }

  private fun encodeResult(photoId: Long, result: FaceAnalysisResult): String {
    val faces = JSONArray()
    result.faces.forEachIndexed { index, face ->
      val box = JSONArray().put(face.left).put(face.top).put(face.right).put(face.bottom)
      val landmarks = JSONArray()
      face.landmarks.forEach { point -> landmarks.put(JSONArray().put(point.first).put(point.second)) }
      val embedding = JSONArray()
      val vector = result.embeddings[index]
      vector.forEach { embedding.put(it.toDouble()) }
      faces.put(JSONObject()
        .put("faceId", stableFaceId(photoId, face, vector))
        .put("confidence", face.confidence.toDouble())
        .put("box", box)
        .put("landmarks", landmarks)
        .put("embedding", embedding))
    }
    return JSONObject()
      .put("version", 2)
      .put("model", FaceModelManager.MODEL_ID)
      .put("inferenceMs", result.inferenceTimeMs)
      .put("faces", faces)
      .toString()
  }

  private fun stableFaceId(photoId: Long, face: FaceBox, embedding: FloatArray): String {
    // Quantization makes the identifier resilient to insignificant float noise and
    // detector output ordering while the photo id + box disambiguate repeated faces.
    val signature = buildString {
      append(FaceModelManager.MODEL_ID).append('|').append(photoId)
      listOf(face.left, face.top, face.right, face.bottom).forEach {
        append('|').append(kotlin.math.round(it * 100f).toInt())
      }
      embedding.forEach { append('|').append(kotlin.math.round(it * 1000f).toInt()) }
    }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
      .digest(signature.toByteArray(Charsets.UTF_8))
    return "face-" + digest.take(16).joinToString("") { "%02x".format(it) }
  }

  companion object {
    private const val MAX_DECODE_EDGE = 1600
    private const val EMPTY_FACE_RESULT = """{"version":1,"faces":[]}"""
  }
}
