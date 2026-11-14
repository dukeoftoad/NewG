package com.example.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FaceBox(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
  val confidence: Float,
  val landmarks: List<Pair<Float, Float>>,
)

data class FaceAnalysisResult(
  val faces: List<FaceBox>,
  /** L2-normalized 192-dimensional vectors, in the same order as [faces]. */
  val embeddings: List<FloatArray>,
  val inferenceTimeMs: Long,
)

/**
 * Local ONNX inference for the pinned Ente FlutterFace demo models.
 *
 * The detector contract is [1,3,640,640] -> [1,25200,16], while MobileFaceNet
 * consumes aligned NHWC RGB crops [N,112,112,3] and returns 192 values per face.
 * All image processing and inference happen in-process; no network calls are made.
 */
class FaceRecognitionEngine {
  private val mutex = Mutex()
  private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
  private var detectorSession: OrtSession? = null
  private var embedderSession: OrtSession? = null
  private var detectorStamp: Long = -1L
  private var embedderStamp: Long = -1L

  suspend fun analyze(bitmap: Bitmap, detectorFile: File, embedderFile: File): Result<FaceAnalysisResult> =
    withContext(Dispatchers.Default) {
      runCatching {
        require(detectorFile.isFile && detectorFile.length() > 0L) { "Face detector model is not installed" }
        require(embedderFile.isFile && embedderFile.length() > 0L) { "Face embedding model is not installed" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Image is empty" }

        mutex.withLock {
          val started = System.nanoTime()
          val detector = ensureSession(detectorFile, isDetector = true)
          val detections = detect(bitmap, detector)
          val embeddings = if (detections.isEmpty()) emptyList() else {
            val embedder = ensureSession(embedderFile, isDetector = false)
            detections.map { face -> embed(bitmap, face, embedder) }
          }
          FaceAnalysisResult(
            faces = detections,
            embeddings = embeddings,
            inferenceTimeMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L),
          )
        }
      }
    }

  private fun ensureSession(file: File, isDetector: Boolean): OrtSession {
    val stamp = file.lastModified()
    val current = if (isDetector) detectorSession else embedderSession
    val currentStamp = if (isDetector) detectorStamp else embedderStamp
    if (current != null && currentStamp == stamp) return current
    current?.close()
    val options = OrtSession.SessionOptions().apply {
      setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
      setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
    }
    val created = environment.createSession(file.absolutePath, options)
    if (isDetector) {
      detectorSession = created
      detectorStamp = stamp
    } else {
      embedderSession = created
      embedderStamp = stamp
    }
    return created
  }

  private fun detect(source: Bitmap, session: OrtSession): List<FaceBox> {
    val scale = min(INPUT_SIZE.toFloat() / source.width, INPUT_SIZE.toFloat() / source.height)
    val resizedW = (source.width * scale).toInt().coerceAtLeast(1)
    val resizedH = (source.height * scale).toInt().coerceAtLeast(1)
    val padX = (INPUT_SIZE - resizedW) / 2f
    val padY = (INPUT_SIZE - resizedH) / 2f
    val inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(inputBitmap)
    canvas.drawColor(LETTERBOX_COLOR)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
      source,
      null,
      android.graphics.RectF(padX, padY, padX + resizedW, padY + resizedH),
      paint,
    )

    val plane = INPUT_SIZE * INPUT_SIZE
    val pixels = IntArray(plane)
    inputBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
    inputBitmap.recycle()
    val data = FloatArray(3 * plane)
    for (i in pixels.indices) {
      val color = pixels[i]
      data[i] = Color.red(color) / 255f
      data[plane + i] = Color.green(color) / 255f
      data[2 * plane + i] = Color.blue(color) / 255f
    }

    val inputName = session.inputNames.firstOrNull() ?: "input"
    OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { tensor ->
      session.run(mapOf(inputName to tensor)).use { outputs ->
        val output = outputs.firstOrNull()?.value as? OnnxTensor
          ?: error("YOLOv5Face returned no tensor")
        val shape = output.info.shape
        require(shape.size == 3 && shape[0] == 1L && shape[2] >= 15L) {
          "Unexpected YOLOv5Face output shape: ${shape.contentToString()}"
        }
        val rows = shape[1].toInt()
        val columns = shape[2].toInt()
        val buffer = output.floatBuffer
        val raw = FloatArray(rows * columns)
        buffer.get(raw, 0, min(raw.size, buffer.remaining() + buffer.position()))
        val candidates = ArrayList<FaceBox>()
        for (row in 0 until rows) {
          val offset = row * columns
          val score = raw[offset + 4]
          if (!score.isFinite() || score < DETECTION_THRESHOLD) continue
          val cx = raw[offset]
          val cy = raw[offset + 1]
          val width = raw[offset + 2]
          val height = raw[offset + 3]
          if (!cx.isFinite() || !cy.isFinite() || !width.isFinite() || !height.isFinite()) continue
          val left = ((cx - width / 2f - padX) / resizedW).coerceIn(0f, 1f)
          val top = ((cy - height / 2f - padY) / resizedH).coerceIn(0f, 1f)
          val right = ((cx + width / 2f - padX) / resizedW).coerceIn(0f, 1f)
          val bottom = ((cy + height / 2f - padY) / resizedH).coerceIn(0f, 1f)
          if (right - left < MIN_FACE_EDGE || bottom - top < MIN_FACE_EDGE) continue
          val points = (0 until 5).map { point ->
            val x = ((raw[offset + 5 + point * 2] - padX) / resizedW).coerceIn(0f, 1f)
            val y = ((raw[offset + 6 + point * 2] - padY) / resizedH).coerceIn(0f, 1f)
            x to y
          }
          candidates += FaceBox(left, top, right, bottom, score.coerceIn(0f, 1f), points)
        }
        return nonMaximumSuppression(candidates)
      }
    }
  }

  private fun embed(source: Bitmap, face: FaceBox, session: OrtSession): FloatArray {
    val crop = alignFace(source, face)
    val pixels = IntArray(EMBED_SIZE * EMBED_SIZE)
    crop.getPixels(pixels, 0, EMBED_SIZE, 0, 0, EMBED_SIZE, EMBED_SIZE)
    crop.recycle()
    // FlutterFace's MobileFaceNet input is NHWC RGB, normalized to [-1, 1].
    val input = FloatArray(EMBED_SIZE * EMBED_SIZE * 3)
    for (i in pixels.indices) {
      val color = pixels[i]
      input[i * 3] = (Color.red(color) - 127.5f) / 127.5f
      input[i * 3 + 1] = (Color.green(color) - 127.5f) / 127.5f
      input[i * 3 + 2] = (Color.blue(color) - 127.5f) / 127.5f
    }
    val inputName = session.inputNames.firstOrNull() ?: "img_inputs"
    OnnxTensor.createTensor(
      environment,
      FloatBuffer.wrap(input),
      longArrayOf(1, EMBED_SIZE.toLong(), EMBED_SIZE.toLong(), 3),
    ).use { tensor ->
      session.run(mapOf(inputName to tensor)).use { outputs ->
        val output = outputs.firstOrNull()?.value as? OnnxTensor
          ?: error("MobileFaceNet returned no tensor")
        val shape = output.info.shape
        require(shape.lastOrNull() == EMBEDDING_SIZE.toLong()) {
          "Unexpected MobileFaceNet output shape: ${shape.contentToString()}"
        }
        val buffer = output.floatBuffer
        val vector = FloatArray(EMBEDDING_SIZE)
        buffer.get(vector, 0, min(vector.size, buffer.remaining() + buffer.position()))
        var normSquared = 0f
        vector.forEach { normSquared += it * it }
        val norm = sqrt(normSquared)
        require(norm.isFinite() && norm > 1e-8f) { "MobileFaceNet produced an invalid embedding" }
        for (i in vector.indices) vector[i] /= norm
        vector
      }
    }
  }

  /**
   * Align eyes and nose to the first three ArcFace reference landmarks. This affine
   * three-point transform is stable on Android Canvas and avoids stretching the face.
   */
  private fun alignFace(source: Bitmap, face: FaceBox): Bitmap {
    val target = Bitmap.createBitmap(EMBED_SIZE, EMBED_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(target)
    canvas.drawColor(Color.BLACK)

    // FlutterFace's five detector landmarks are ordered left eye, right eye, nose,
    // left mouth corner, right mouth corner. Map them to the ArcFace 112px template
    // with a least-squares similarity transform (uniform scale + rotation + translation).
    val reference = arrayOf(
      38.2946f to 51.6963f,
      73.5318f to 51.5014f,
      56.0252f to 71.7366f,
      41.5493f to 92.3655f,
      70.7299f to 92.2041f,
    )
    if (face.landmarks.size != reference.size) {
      drawFallbackFaceCrop(canvas, source, face)
      return target
    }

    val sourcePoints = face.landmarks.map { it.first * source.width to it.second * source.height }
    val sourceMeanX = sourcePoints.map { it.first }.average().toFloat()
    val sourceMeanY = sourcePoints.map { it.second }.average().toFloat()
    val targetMeanX = reference.map { it.first }.average().toFloat()
    val targetMeanY = reference.map { it.second }.average().toFloat()

    var denominator = 0f
    var dot = 0f
    var cross = 0f
    for (i in sourcePoints.indices) {
      val sx = sourcePoints[i].first - sourceMeanX
      val sy = sourcePoints[i].second - sourceMeanY
      val dx = reference[i].first - targetMeanX
      val dy = reference[i].second - targetMeanY
      denominator += sx * sx + sy * sy
      dot += sx * dx + sy * dy
      cross += sx * dy - sy * dx
    }
    if (denominator <= 1e-6f) {
      drawFallbackFaceCrop(canvas, source, face)
      return target
    }

    val a = dot / denominator
    val b = cross / denominator
    val translateX = targetMeanX - (a * sourceMeanX - b * sourceMeanY)
    val translateY = targetMeanY - (b * sourceMeanX + a * sourceMeanY)
    val matrix = Matrix().apply {
      setValues(floatArrayOf(a, -b, translateX, b, a, translateY, 0f, 0f, 1f))
    }
    canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return target
  }

  private fun drawFallbackFaceCrop(canvas: Canvas, source: Bitmap, face: FaceBox) {
    val left = (face.left * source.width).toInt().coerceIn(0, source.width - 1)
    val top = (face.top * source.height).toInt().coerceIn(0, source.height - 1)
    val right = (face.right * source.width).toInt().coerceIn(left + 1, source.width)
    val bottom = (face.bottom * source.height).toInt().coerceIn(top + 1, source.height)
    val fallback = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    canvas.drawBitmap(
      fallback,
      null,
      android.graphics.Rect(0, 0, EMBED_SIZE, EMBED_SIZE),
      Paint(Paint.FILTER_BITMAP_FLAG),
    )
    fallback.recycle()
  }

  private fun nonMaximumSuppression(input: List<FaceBox>): List<FaceBox> {
    val remaining = input.sortedByDescending { it.confidence }.toMutableList()
    val selected = ArrayList<FaceBox>()
    while (remaining.isNotEmpty() && selected.size < MAX_FACES_PER_IMAGE) {
      val best = remaining.removeAt(0)
      selected += best
      val iterator = remaining.iterator()
      while (iterator.hasNext()) {
        if (intersectionOverUnion(best, iterator.next()) > IOU_THRESHOLD) iterator.remove()
      }
    }
    return selected
  }

  private fun intersectionOverUnion(a: FaceBox, b: FaceBox): Float {
    val left = max(a.left, b.left)
    val top = max(a.top, b.top)
    val right = min(a.right, b.right)
    val bottom = min(a.bottom, b.bottom)
    val intersection = max(0f, right - left) * max(0f, bottom - top)
    val areaA = (a.right - a.left) * (a.bottom - a.top)
    val areaB = (b.right - b.left) * (b.bottom - b.top)
    val union = areaA + areaB - intersection
    return if (union <= 0f) 0f else intersection / union
  }

  fun close() {
    runCatching { detectorSession?.close() }
    runCatching { embedderSession?.close() }
    detectorSession = null
    embedderSession = null
    detectorStamp = -1L
    embedderStamp = -1L
  }

  companion object {
    private const val INPUT_SIZE = 640
    private const val EMBED_SIZE = 112
    private const val EMBEDDING_SIZE = 192
    private const val DETECTION_THRESHOLD = 0.8f
    private const val IOU_THRESHOLD = 0.4f
    private const val MIN_FACE_EDGE = 0.025f
    private const val MAX_FACES_PER_IMAGE = 40
    private const val LETTERBOX_COLOR = 0xFF727272.toInt()
  }
}
