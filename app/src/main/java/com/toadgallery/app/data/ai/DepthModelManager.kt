package com.toadgallery.app.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.toadgallery.app.data.preferences.UserPreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class DepthModelStatus {
  NOT_INSTALLED,
  DOWNLOADING,
  INSTALLED,
  ERROR,
}

@Immutable
data class DepthModelPackState(
  val modelId: String = DepthModelManager.MODEL_ID,
  val displayName: String = "Depth Anything V2 Small (int8 ONNX)",
  val fileName: String = DepthModelManager.MODEL_FILE_NAME,
  val expectedSizeMb: Float = DepthModelManager.EXPECTED_SIZE_MB,
  val status: DepthModelStatus = DepthModelStatus.NOT_INSTALLED,
  val progressPercent: Int = 0,
  val downloadedMb: Float = 0f,
  val installedSizeMb: Float = 0f,
  val errorMessage: String? = null,
) {
  val isInstalled: Boolean
    get() = status == DepthModelStatus.INSTALLED

  val isDownloading: Boolean
    get() = status == DepthModelStatus.DOWNLOADING

  val progressLabel: String
    get() =
      when (status) {
        DepthModelStatus.NOT_INSTALLED ->
          String.format(Locale.US, "~%.1f MB • On-demand download", expectedSizeMb)
        DepthModelStatus.DOWNLOADING ->
          String.format(
            Locale.US,
            "Downloading %.1f / %.1f MB (%d%%)",
            downloadedMb,
            expectedSizeMb,
            progressPercent,
          )
        DepthModelStatus.INSTALLED ->
          String.format(
            Locale.US,
            "Installed (%.1f MB) • Ready for Bokeh",
            if (installedSizeMb > 0f) installedSizeMb else expectedSizeMb,
          )
        DepthModelStatus.ERROR -> errorMessage ?: "Download failed • Tap to retry"
      }
}

class DepthModelManager(
  context: Context,
  private val prefsRepo: UserPreferencesRepository,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val modelDir = File(appContext.filesDir, "models/$MODEL_ID")
  val modelFile: File
    get() = File(modelDir, MODEL_FILE_NAME)

  private val _state = MutableStateFlow(initialPackState())
  val state: StateFlow<DepthModelPackState> = _state.asStateFlow()

  private var activeDownloadJob: Job? = null
  @Volatile private var activeCall: Call? = null

  init {
    scope.launch { reconcileWithDisk() }
  }

  private fun initialPackState(): DepthModelPackState {
    val file = modelFile
    val exists = file.exists() && file.length() > MIN_VALID_BYTES
    val sizeMb = if (exists) file.length() / (1024f * 1024f) else 0f
    return DepthModelPackState(
      status = if (exists) DepthModelStatus.INSTALLED else DepthModelStatus.NOT_INSTALLED,
      progressPercent = if (exists) 100 else 0,
      installedSizeMb = sizeMb,
    )
  }

  suspend fun reconcileWithDisk() {
    withContext(Dispatchers.IO) {
      val file = modelFile
      val valid = file.exists() && file.length() > MIN_VALID_BYTES
      val sizeMb = if (valid) file.length() / (1024f * 1024f) else 0f
      if (_state.value.status != DepthModelStatus.DOWNLOADING) {
        _state.value =
          _state.value.copy(
            status = if (valid) DepthModelStatus.INSTALLED else DepthModelStatus.NOT_INSTALLED,
            progressPercent = if (valid) 100 else 0,
            installedSizeMb = sizeMb,
            errorMessage = null,
          )
      }
      prefsRepo.setDepthModelInstalled(valid)
    }
  }

  fun startDownload(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
    if (_state.value.isDownloading) return
    val existing = modelFile
    if (existing.exists() && existing.length() > MIN_VALID_BYTES) {
      scope.launch {
        reconcileWithDisk()
        onResult(true, "bokeh-depth-dav2 is already installed")
      }
      return
    }

    activeDownloadJob?.cancel()
    activeDownloadJob =
      scope.launch {
        _state.value =
          _state.value.copy(
            status = DepthModelStatus.DOWNLOADING,
            progressPercent = 0,
            downloadedMb = 0f,
            errorMessage = null,
          )

        val partFile = File(modelDir, "$MODEL_FILE_NAME.part")
        try {
          modelDir.mkdirs()
          if (partFile.exists()) partFile.delete()

          val request =
            Request.Builder()
              .url(DOWNLOAD_URL)
              .header("User-Agent", "toadGALLERY-Android/0.5")
              .build()

          val call = client.newCall(request)
          activeCall = call
          call.execute().use { response ->
            if (!response.isSuccessful) {
              throw IllegalStateException("HTTP ${response.code} downloading model")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: EXPECTED_BYTES
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastUiEmitMs = 0L

            body.byteStream().use { input ->
              FileOutputStream(partFile).use { output ->
                while (true) {
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  downloaded += read
                  val now = System.currentTimeMillis()
                  if (now - lastUiEmitMs >= 120L || downloaded >= totalBytes) {
                    lastUiEmitMs = now
                    val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(1, 99)
                    _state.value =
                      _state.value.copy(
                        status = DepthModelStatus.DOWNLOADING,
                        progressPercent = pct,
                        downloadedMb = downloaded / (1024f * 1024f),
                        expectedSizeMb = totalBytes / (1024f * 1024f),
                      )
                  }
                }
              }
            }
          }

          if (partFile.length() < MIN_VALID_BYTES) {
            partFile.delete()
            throw IllegalStateException("Downloaded ONNX file was incomplete")
          }

          val finalFile = modelFile
          if (finalFile.exists()) finalFile.delete()
          if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
          }

          val sizeMb = finalFile.length() / (1024f * 1024f)
          prefsRepo.setDepthModelInstalled(true)
          _state.value =
            _state.value.copy(
              status = DepthModelStatus.INSTALLED,
              progressPercent = 100,
              downloadedMb = sizeMb,
              installedSizeMb = sizeMb,
              errorMessage = null,
            )
          onResult(true, "Installed bokeh-depth-dav2 (Depth Anything V2 Small int8)")
        } catch (ce: CancellationException) {
          partFile.delete()
          _state.value =
            _state.value.copy(
              status = DepthModelStatus.NOT_INSTALLED,
              progressPercent = 0,
              downloadedMb = 0f,
              errorMessage = null,
            )
          throw ce
        } catch (t: Throwable) {
          partFile.delete()
          prefsRepo.setDepthModelInstalled(false)
          val msg = t.message?.take(80) ?: "Network error downloading ONNX model"
          _state.value =
            _state.value.copy(
              status = DepthModelStatus.ERROR,
              progressPercent = 0,
              downloadedMb = 0f,
              errorMessage = msg,
            )
          onResult(false, msg)
        } finally {
          activeCall = null
        }
      }
  }

  fun cancelDownload() {
    activeCall?.cancel()
    activeDownloadJob?.cancel()
    activeCall = null
    _state.value =
      _state.value.copy(
        status = DepthModelStatus.NOT_INSTALLED,
        progressPercent = 0,
        downloadedMb = 0f,
        errorMessage = null,
      )
  }

  suspend fun deleteModelPack(): Float =
    withContext(Dispatchers.IO) {
      cancelDownload()
      val file = modelFile
      val freedMb = if (file.exists()) file.length() / (1024f * 1024f) else EXPECTED_SIZE_MB
      runCatching {
        file.delete()
        File(modelDir, "$MODEL_FILE_NAME.part").delete()
      }
      prefsRepo.setDepthModelInstalled(false)
      _state.value =
        DepthModelPackState(
          status = DepthModelStatus.NOT_INSTALLED,
          progressPercent = 0,
          downloadedMb = 0f,
          installedSizeMb = 0f,
        )
      freedMb
    }

  companion object {
    const val MODEL_ID = "bokeh-depth-dav2"
    const val MODEL_FILE_NAME = "model_int8.onnx"
    const val EXPECTED_SIZE_MB = 27.3f
    private const val EXPECTED_BYTES = 28_626_124L
    private const val MIN_VALID_BYTES = 512 * 1024L
    const val DOWNLOAD_URL =
      "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model_int8.onnx"
  }
}
