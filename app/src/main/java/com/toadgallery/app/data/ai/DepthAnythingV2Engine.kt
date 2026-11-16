package com.toadgallery.app.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DepthMapResult(
  val width: Int,
  val height: Int,
  /** Row-major relative inverse depth normalized to [0f..1f] (1f = closest subject, 0f = farthest background). */
  val normalizedDepth: FloatArray,
  val inferenceTimeMs: Long,
) {
  fun sampleAt(normX: Float, normY: Float): Float {
    if (width <= 0 || height <= 0 || normalizedDepth.isEmpty()) return 0.5f
    val px = (normX.coerceIn(0f, 1f) * (width - 1)).roundToInt().coerceIn(0, width - 1)
    val py = (normY.coerceIn(0f, 1f) * (height - 1)).roundToInt().coerceIn(0, height - 1)
    return normalizedDepth[py * width + px]
  }
}

class DepthAnythingV2Engine {
  private val sessionMutex = Mutex()
  private var ortEnv: OrtEnvironment? = null
  private var ortSession: OrtSession? = null
  private var loadedModelPath: String? = null
  private var loadedModelModified: Long = -1L

  suspend fun estimateDepth(source: Bitmap, modelFile: File): Result<DepthMapResult> =
    withContext(Dispatchers.Default) {
      runCatching {
        if (!modelFile.exists() || modelFile.length() <= 0L) {
          throw IllegalStateException("bokeh-depth-dav2 model file is not downloaded")
        }

        val startMs = System.currentTimeMillis()
        val (targetW, targetH) = computeVitInputDimensions(source.width, source.height)
        val scaled =
          if (source.width == targetW && source.height == targetH) source
          else Bitmap.createScaledBitmap(source, targetW, targetH, true)

        val inputBuffer = prepareImageNetTensorBuffer(scaled, targetW, targetH)
        if (scaled !== source) scaled.recycle()

        sessionMutex.withLock {
          val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
          val session = ensureSessionLocked(env, modelFile)
          val inputName = session.inputNames.firstOrNull() ?: "pixel_values"
          val shape = longArrayOf(1L, 3L, targetH.toLong(), targetW.toLong())

          OnnxTensor.createTensor(env, inputBuffer, shape).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { outputs ->
              val firstOutput =
                outputs.firstOrNull()?.value as? OnnxTensor
                  ?: throw IllegalStateException("Missing predicted_depth output tensor")
              val outShape = firstOutput.info.shape
              val outH =
                when (outShape.size) {
                  4 -> outShape[2].toInt()
                  3 -> outShape[1].toInt()
                  2 -> outShape[0].toInt()
                  else -> targetH
                }
              val outW =
                when (outShape.size) {
                  4 -> outShape[3].toInt()
                  3 -> outShape[2].toInt()
                  2 -> outShape[1].toInt()
                  else -> targetW
                }
              val rawFloats = firstOutput.floatBuffer
              val total = outW * outH
              val raw = FloatArray(total)
              rawFloats.get(raw, 0, minOf(total, rawFloats.remaining()))

              val normalized = normalizeDepthPlane(raw)
              val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
              DepthMapResult(
                width = outW,
                height = outH,
                normalizedDepth = normalized,
                inferenceTimeMs = elapsed,
              )
            }
          }
        }
      }
    }

  private fun ensureSessionLocked(env: OrtEnvironment, modelFile: File): OrtSession {
    val path = modelFile.absolutePath
    val mod = modelFile.lastModified()
    val existing = ortSession
    if (existing != null && loadedModelPath == path && loadedModelModified == mod) {
      return existing
    }
    existing?.close()
    val opts =
      OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2))
      }
    return env.createSession(path, opts).also {
      ortSession = it
      loadedModelPath = path
      loadedModelModified = mod
    }
  }

  fun close() {
    runCatching { ortSession?.close() }
    ortSession = null
    loadedModelPath = null
  }

  companion object {
    private const val PATCH_SIZE = 14
    private const val MAX_VIT_DIM = 392 // Fast mobile latency while honoring ViT-S 14px patch grid
    private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    internal fun computeVitInputDimensions(srcW: Int, srcH: Int): Pair<Int, Int> {
      val safeW = srcW.coerceAtLeast(1)
      val safeH = srcH.coerceAtLeast(1)
      val scale = MAX_VIT_DIM.toFloat() / max(safeW, safeH).toFloat()
      val rawW = (safeW * scale).roundToInt().coerceAtLeast(PATCH_SIZE * 8)
      val rawH = (safeH * scale).roundToInt().coerceAtLeast(PATCH_SIZE * 8)
      val snappedW = ((rawW + PATCH_SIZE / 2) / PATCH_SIZE) * PATCH_SIZE
      val snappedH = ((rawH + PATCH_SIZE / 2) / PATCH_SIZE) * PATCH_SIZE
      return snappedW.coerceAtLeast(PATCH_SIZE * 8) to snappedH.coerceAtLeast(PATCH_SIZE * 8)
    }

    internal fun prepareImageNetTensorBuffer(
      bitmap: Bitmap,
      width: Int,
      height: Int,
    ): FloatBuffer {
      val pixels = IntArray(width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      val planeSize = width * height
      val floatArray = FloatArray(3 * planeSize)

      for (i in 0 until planeSize) {
        val c = pixels[i]
        val r = (Color.red(c) / 255f - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
        val g = (Color.green(c) / 255f - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
        val b = (Color.blue(c) / 255f - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
        floatArray[i] = r
        floatArray[planeSize + i] = g
        floatArray[2 * planeSize + i] = b
      }
      return FloatBuffer.wrap(floatArray)
    }

    internal fun normalizeDepthPlane(raw: FloatArray): FloatArray {
      if (raw.isEmpty()) return raw
      var minV = Float.POSITIVE_INFINITY
      var maxV = Float.NEGATIVE_INFINITY
      for (v in raw) {
        if (!v.isNaN() && !v.isInfinite()) {
          if (v < minV) minV = v
          if (v > maxV) maxV = v
        }
      }
      val span = (maxV - minV).takeIf { it > 1e-6f } ?: 1f
      val out = FloatArray(raw.size)
      for (i in raw.indices) {
        val v = raw[i]
        out[i] = if (v.isNaN() || v.isInfinite()) 0.5f else ((v - minV) / span).coerceIn(0f, 1f)
      }
      return out
    }
  }
}
