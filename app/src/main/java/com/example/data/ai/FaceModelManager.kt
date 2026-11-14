package com.example.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.data.preferences.UserPreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
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

enum class FaceModelStatus {
  NOT_INSTALLED,
  DOWNLOADING,
  INSTALLED,
  ERROR,
}

@Immutable
data class FaceModelPackState(
  val status: FaceModelStatus = FaceModelStatus.NOT_INSTALLED,
  val progressPercent: Int = 0,
  val downloadedMb: Float = 0f,
  val totalMb: Float = FaceModelManager.EXPECTED_SIZE_MB,
  val installedSizeMb: Float = 0f,
  val errorMessage: String? = null,
) {
  val isInstalled: Boolean get() = status == FaceModelStatus.INSTALLED
  val isDownloading: Boolean get() = status == FaceModelStatus.DOWNLOADING

  val progressLabel: String
    get() = when (status) {
      FaceModelStatus.NOT_INSTALLED -> String.format(
        Locale.US, "~%.1f MB • Ente face models",
        FaceModelManager.EXPECTED_SIZE_MB,
      )
      FaceModelStatus.DOWNLOADING -> String.format(
        Locale.US, "Downloading %.1f / %.1f MB (%d%%)",
        downloadedMb, totalMb, progressPercent,
      )
      FaceModelStatus.INSTALLED -> String.format(
        Locale.US, "Installed and verified (%.1f MB)", installedSizeMb,
      )
      FaceModelStatus.ERROR -> errorMessage ?: "Download failed • Tap to retry"
    }
}

class FaceModelManager(
  context: Context,
  private val prefsRepo: UserPreferencesRepository,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val client = OkHttpClient.Builder()
    .connectTimeout(25, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val modelDir = File(appContext.filesDir, "models/$MODEL_ID")
  val detectorFile: File get() = File(modelDir, DETECTOR_FILE_NAME)
  val embedderFile: File get() = File(modelDir, EMBEDDER_FILE_NAME)

  private val _state = MutableStateFlow(FaceModelPackState())
  val state: StateFlow<FaceModelPackState> = _state.asStateFlow()

  private var activeDownloadJob: Job? = null
  @Volatile private var activeCall: Call? = null

  init {
    scope.launch { reconcileWithDisk() }
  }

  suspend fun reconcileWithDisk() {
    withContext(Dispatchers.IO) {
      val detectorValid = verifyGitBlob(detectorFile, DETECTOR_SIZE, DETECTOR_GIT_BLOB_SHA)
      val embedderValid = verifyGitBlob(embedderFile, EMBEDDER_SIZE, EMBEDDER_GIT_BLOB_SHA)
      val valid = detectorValid && embedderValid
      if (!valid) {
        if (detectorFile.exists() && !detectorValid) detectorFile.delete()
        if (embedderFile.exists() && !embedderValid) embedderFile.delete()
      }
      val installedBytes = if (valid) detectorFile.length() + embedderFile.length() else 0L
      if (!_state.value.isDownloading) {
        _state.value = FaceModelPackState(
          status = if (valid) FaceModelStatus.INSTALLED else FaceModelStatus.NOT_INSTALLED,
          progressPercent = if (valid) 100 else 0,
          downloadedMb = installedBytes / (1024f * 1024f),
          installedSizeMb = installedBytes / (1024f * 1024f),
        )
      }
      prefsRepo.setFaceModelInstalled(valid)
    }
  }

  fun startDownload(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
    if (_state.value.isDownloading) return
    activeDownloadJob?.cancel()
    activeDownloadJob = scope.launch {
      _state.value = FaceModelPackState(status = FaceModelStatus.DOWNLOADING)
      try {
        modelDir.mkdirs()
        downloadOne(
          url = DETECTOR_URL,
          destination = detectorFile,
          expectedBytes = DETECTOR_SIZE,
          expectedGitBlobSha = DETECTOR_GIT_BLOB_SHA,
          alreadyDownloaded = 0L,
        )
        downloadOne(
          url = EMBEDDER_URL,
          destination = embedderFile,
          expectedBytes = EMBEDDER_SIZE,
          expectedGitBlobSha = EMBEDDER_GIT_BLOB_SHA,
          alreadyDownloaded = DETECTOR_SIZE,
        )

        val totalBytes = detectorFile.length() + embedderFile.length()
        prefsRepo.setFaceModelInstalled(true)
        _state.value = FaceModelPackState(
          status = FaceModelStatus.INSTALLED,
          progressPercent = 100,
          downloadedMb = totalBytes / (1024f * 1024f),
          installedSizeMb = totalBytes / (1024f * 1024f),
        )
        onResult(true, "Ente face detector and MobileFaceNet installed and verified")
      } catch (cancelled: CancellationException) {
        File(modelDir, "$DETECTOR_FILE_NAME.part").delete()
        File(modelDir, "$EMBEDDER_FILE_NAME.part").delete()
        _state.value = FaceModelPackState()
        throw cancelled
      } catch (error: Throwable) {
        File(modelDir, "$DETECTOR_FILE_NAME.part").delete()
        File(modelDir, "$EMBEDDER_FILE_NAME.part").delete()
        prefsRepo.setFaceModelInstalled(false)
        val message = error.message?.take(100) ?: "Unable to download Ente face models"
        _state.value = FaceModelPackState(
          status = FaceModelStatus.ERROR,
          errorMessage = message,
        )
        onResult(false, message)
      } finally {
        activeCall = null
      }
    }
  }

  private suspend fun downloadOne(
    url: String,
    destination: File,
    expectedBytes: Long,
    expectedGitBlobSha: String,
    alreadyDownloaded: Long,
  ) = withContext(Dispatchers.IO) {
    val partFile = File(modelDir, "${destination.name}.part")
    partFile.delete()
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "toadGALLERY-Android/0.5")
      .build()
    val call = client.newCall(request)
    activeCall = call
    call.execute().use { response ->
      if (!response.isSuccessful) {
        throw IllegalStateException("HTTP ${response.code} downloading ${destination.name}")
      }
      val body = response.body ?: throw IllegalStateException("Empty model response")
      val buffer = ByteArray(32 * 1024)
      var downloaded = 0L
      var lastUiEmitMs = 0L
      body.byteStream().use { input ->
        FileOutputStream(partFile).use { output ->
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            downloaded += count
            val now = System.currentTimeMillis()
            if (now - lastUiEmitMs >= 120L || downloaded == expectedBytes) {
              lastUiEmitMs = now
              val total = (alreadyDownloaded + downloaded).coerceAtMost(EXPECTED_TOTAL_BYTES)
              _state.value = _state.value.copy(
                status = FaceModelStatus.DOWNLOADING,
                progressPercent = ((total * 100L) / EXPECTED_TOTAL_BYTES).toInt().coerceIn(1, 99),
                downloadedMb = total / (1024f * 1024f),
              )
            }
          }
        }
      }
    }
    if (partFile.length() != expectedBytes) {
      partFile.delete()
      throw IllegalStateException("${destination.name} size mismatch")
    }
    if (!verifyGitBlob(partFile, expectedBytes, expectedGitBlobSha)) {
      partFile.delete()
      throw IllegalStateException("${destination.name} integrity check failed")
    }
    if (destination.exists()) destination.delete()
    if (!partFile.renameTo(destination)) {
      partFile.copyTo(destination, overwrite = true)
      partFile.delete()
    }
  }

  private fun verifyGitBlob(file: File, expectedBytes: Long, expectedSha: String): Boolean {
    if (!file.isFile || file.length() != expectedBytes) return false
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.US_ASCII))
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == expectedSha
  }

  fun cancelDownload() {
    activeCall?.cancel()
    activeDownloadJob?.cancel()
    activeCall = null
    _state.value = FaceModelPackState()
  }

  suspend fun deleteModelPack(): Float = withContext(Dispatchers.IO) {
    cancelDownload()
    val bytes = (if (detectorFile.exists()) detectorFile.length() else 0L) +
      (if (embedderFile.exists()) embedderFile.length() else 0L)
    detectorFile.delete()
    embedderFile.delete()
    File(modelDir, "$DETECTOR_FILE_NAME.part").delete()
    File(modelDir, "$EMBEDDER_FILE_NAME.part").delete()
    modelDir.delete()
    prefsRepo.setFaceModelInstalled(false)
    _state.value = FaceModelPackState()
    bytes / (1024f * 1024f)
  }

  companion object {
    const val MODEL_ID = "ente-face-recognition"
    const val DETECTOR_FILE_NAME = "yolov5s_face_640_640.onnx"
    const val EMBEDDER_FILE_NAME = "mobilefacenet_opset15.onnx"
    const val EXPECTED_SIZE_MB = 35.9f
    private const val EXPECTED_TOTAL_BYTES = 37_656_477L

    // Pinned to FlutterFace commit dbcbc2b; hashes are Git blob object IDs from that tree.
    private const val SOURCE_COMMIT = "$sourceCommit"
    private const val DETECTOR_SIZE = 32_369_479L
    private const val EMBEDDER_SIZE = 5_286_998L
    private const val DETECTOR_GIT_BLOB_SHA = "11c42e4909997c9dab9eb318ba919fe10110ee16"
    private const val EMBEDDER_GIT_BLOB_SHA = "d5468a5e0df5209ed088db9d6eef4a7eb36c97f2"
    private const val DETECTOR_URL =
      "https://raw.githubusercontent.com/laurenspriem/flutterface/$SOURCE_COMMIT/assets/models/$DETECTOR_FILE_NAME"
    private const val EMBEDDER_URL =
      "https://raw.githubusercontent.com/laurenspriem/flutterface/$SOURCE_COMMIT/assets/models/$EMBEDDER_FILE_NAME"
  }
}
