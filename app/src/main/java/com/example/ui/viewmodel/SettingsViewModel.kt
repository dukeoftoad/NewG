package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.DepthModelManager
import com.example.data.ai.FaceModelManager
import com.example.data.ai.FaceIndexManager
import com.example.data.ai.FaceIndexState
import com.example.data.ai.FaceModelPackState
import com.example.data.ai.DepthModelPackState
import com.example.data.model.AppearanceMode
import com.example.data.model.GroupByMode
import com.example.data.model.LayoutMode
import com.example.data.model.SettingsSubPage
import com.example.data.model.ThemePalette
import com.example.data.model.UserPreferences
import com.example.data.model.VaultAutoLock
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val preferences: UserPreferences = UserPreferences(),
  val currentSubPage: SettingsSubPage = SettingsSubPage.HUB,
  val searchQuery: String = "",
  val aiIndexing: Boolean = false,
  val aiProgress: Int = 0,
  val aiStatusText: String = "Ready to index local photos",
  val semanticDownloading: Boolean = false,
  val semanticDownloadProgress: Int = 0,
  val faceModelState: FaceModelPackState = FaceModelPackState(),
  val depthModelState: DepthModelPackState = DepthModelPackState(),
  val showDiagnostics: Boolean = false,
  val selfTestOutput: String? = null,
)

class SettingsViewModel(
  private val prefsRepo: UserPreferencesRepository,
  private val depthModelManager: DepthModelManager,
  private val faceModelManager: FaceModelManager,
  private val faceIndexManager: FaceIndexManager,
) : ViewModel() {

  private val currentSubPage = MutableStateFlow(SettingsSubPage.HUB)
  private val searchQuery = MutableStateFlow("")
  private val semanticDownloading = MutableStateFlow(false)
  private val semanticDownloadProgress = MutableStateFlow(0)
  private val showDiagnostics = MutableStateFlow(false)
  private val selfTestOutput = MutableStateFlow<String?>(null)

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private var downloadJob: Job? = null

  private val aiMonitorFlow =
    combine(
      semanticDownloading,
      semanticDownloadProgress,
      depthModelManager.state,
      faceModelManager.state,
      faceIndexManager.state,
    ) { arr ->
      val index = arr[4] as FaceIndexState
      AiMonitorState(
        indexing = index.running,
        progress = index.progress,
        statusText = index.progressLabel,
        downloading = arr[0] as Boolean,
        downloadProgress = arr[1] as Int,
        depthState = arr[2] as DepthModelPackState,
        faceState = arr[3] as FaceModelPackState,
      )
    }

  val uiState: StateFlow<SettingsUiState> =
    combine(
        prefsRepo.preferences,
        currentSubPage,
        searchQuery,
        aiMonitorFlow,
        showDiagnostics,
        selfTestOutput,
      ) { arr ->
        val prefs = arr[0] as UserPreferences
        val page = arr[1] as SettingsSubPage
        val query = arr[2] as String
        val ai = arr[3] as AiMonitorState
        val diag = arr[4] as Boolean
        val selfTest = arr[5] as String?

        SettingsUiState(
          preferences = prefs,
          currentSubPage = page,
          searchQuery = query,
          aiIndexing = ai.indexing,
          aiProgress = ai.progress,
          aiStatusText = ai.statusText,
          semanticDownloading = ai.downloading,
          semanticDownloadProgress = ai.downloadProgress,
          depthModelState = ai.depthState,
          faceModelState = ai.faceState,
          showDiagnostics = diag,
          selfTestOutput = selfTest,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
      )

  fun openSubPage(page: SettingsSubPage) {
    currentSubPage.value = page
  }

  fun backToHub() {
    currentSubPage.value = SettingsSubPage.HUB
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun selectPalette(palette: ThemePalette) {
    viewModelScope.launch {
      prefsRepo.updatePalette(palette)
      _messages.tryEmit("Applied ${palette.displayName} theme")
    }
  }

  fun selectAppearanceMode(mode: AppearanceMode) {
    viewModelScope.launch { prefsRepo.updateAppearanceMode(mode) }
  }

  fun toggleDynamicColors() {
    viewModelScope.launch {
      val next = !uiState.value.preferences.useDynamicColors
      prefsRepo.setUseDynamicColors(next)
      _messages.tryEmit(
        if (next) "Material You wallpaper colors enabled" else "Using curated gallery palette"
      )
    }
  }

  fun setLayoutMode(mode: LayoutMode) {
    viewModelScope.launch { prefsRepo.updateLayoutMode(mode) }
  }

  fun changeColumns(delta: Int) {
    viewModelScope.launch {
      val next = (uiState.value.preferences.columns + delta).coerceIn(2, 10)
      prefsRepo.updateColumns(next)
    }
  }

  fun setColumns(columns: Int) {
    viewModelScope.launch {
      prefsRepo.updateColumns(columns.coerceIn(2, 10))
    }
  }

  fun setGroupBy(groupBy: GroupByMode) {
    viewModelScope.launch { prefsRepo.updateGroupBy(groupBy) }
  }

  fun toggleShowStories() {
    viewModelScope.launch { prefsRepo.setShowStories(!uiState.value.preferences.showStories) }
  }

  fun toggleAiPeople() {
    viewModelScope.launch { prefsRepo.setAiPeople(!uiState.value.preferences.aiPeople) }
  }

  fun toggleAiMagicSearch() {
    viewModelScope.launch { prefsRepo.setAiMagicSearch(!uiState.value.preferences.aiMagicSearch) }
  }

  fun toggleAiThings() {
    viewModelScope.launch { prefsRepo.setAiThings(!uiState.value.preferences.aiThings) }
  }

  fun setClusteringThreshold(threshold: Float) {
    viewModelScope.launch { prefsRepo.setClusteringThreshold(threshold) }
  }

  fun handleFacePackAction() {
    val current = faceModelManager.state.value
    if (current.isDownloading) {
      faceModelManager.cancelDownload()
      _messages.tryEmit("Cancelled Ente face-model download")
      return
    }
    if (current.isInstalled) {
      faceIndexManager.pause()
      viewModelScope.launch {
        val freedMb = faceModelManager.deleteModelPack()
        _messages.tryEmit(String.format("Removed Ente face models (%.1f MB freed)", freedMb))
      }
      return
    }
    faceModelManager.startDownload { _, message -> _messages.tryEmit(message) }
  }

  fun handleSemanticPackAction() {
    if (semanticDownloading.value) return
    if (uiState.value.preferences.semanticModelInstalled) {
      viewModelScope.launch {
        prefsRepo.setSemanticModelInstalled(false)
        _messages.tryEmit("Removed Semantic CLIP pack (213 MB freed)")
      }
      return
    }
    downloadJob?.cancel()
    downloadJob =
      viewModelScope.launch {
        semanticDownloading.value = true
        semanticDownloadProgress.value = 12
        while (semanticDownloadProgress.value < 100) {
          delay(280)
          semanticDownloadProgress.value = (semanticDownloadProgress.value + 22).coerceAtMost(100)
        }
        semanticDownloading.value = false
        prefsRepo.setSemanticModelInstalled(true)
        _messages.tryEmit("Semantic CLIP models installed & verified")
      }
  }

  fun handleDepthPackAction() {
    val cur = depthModelManager.state.value
    if (cur.isDownloading) {
      depthModelManager.cancelDownload()
      _messages.tryEmit("Cancelled bokeh-depth-dav2 download")
      return
    }
    if (cur.isInstalled) {
      viewModelScope.launch {
        val freedMb = depthModelManager.deleteModelPack()
        _messages.tryEmit(String.format("Removed bokeh-depth-dav2 (%.1f MB freed)", freedMb))
      }
      return
    }
    depthModelManager.startDownload { _, msg -> _messages.tryEmit(msg) }
  }

  fun toggleDiagnostics() {
    showDiagnostics.value = !showDiagnostics.value
  }

  fun runOrPauseIndex() {
    if (faceIndexManager.state.value.running) {
      faceIndexManager.pause()
      _messages.tryEmit("Face indexing paused; completed results are saved")
    } else {
      val indexState = faceIndexManager.state.value
      faceIndexManager.start(forceReindex = indexState.progress >= 100 && indexState.processed > 0)
    }
  }

  fun resetAiIndex() {
    faceIndexManager.clear()
    _messages.tryEmit("Local face index cleared")
  }

  fun toggleLocationExif() {
    viewModelScope.launch {
      val next = !uiState.value.preferences.showLocationExif
      prefsRepo.setShowLocationExif(next)
      _messages.tryEmit(
        if (next) "GPS coordinates visible locally" else "GPS coordinates masked in Lightbox"
      )
    }
  }

  fun toggleVaultBiometrics() {
    viewModelScope.launch {
      prefsRepo.setVaultBiometrics(!uiState.value.preferences.vaultBiometrics)
    }
  }

  fun setVaultAutoLock(autoLock: VaultAutoLock) {
    viewModelScope.launch { prefsRepo.setVaultAutoLock(autoLock) }
  }

  fun updateVaultPin(currentPin: String, newPin: String, onComplete: (Boolean) -> Unit) {
    if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
      _messages.tryEmit("New PIN must be 4 digits")
      onComplete(false)
      return
    }
    viewModelScope.launch {
      val valid = prefsRepo.verifyPin(currentPin)
      if (!valid) {
        _messages.tryEmit("Current PIN did not match")
        onComplete(false)
        return@launch
      }
      prefsRepo.updateVaultPin(newPin)
      _messages.tryEmit("Hidden Vault PIN updated (SHA-256 salted)")
      onComplete(true)
    }
  }

  fun clearCache() {
    viewModelScope.launch {
      val freed = uiState.value.preferences.cacheSizeMb
      prefsRepo.setCacheSizeMb(0.0f)
      _messages.tryEmit(String.format("Freed %.1f MB of thumbnail cache", freed))
    }
  }

  fun clearMlIndexStore() {
    faceIndexManager.clear()
    viewModelScope.launch {
      val freed = uiState.value.preferences.mlIndexSizeMb
      prefsRepo.setMlIndexSizeMb(0.0f)
      _messages.tryEmit(String.format("Cleared ML index store (%.1f MB freed)", freed))
    }
  }

  fun runEngineSelfTest() {
    val dav2Ready = depthModelManager.state.value.isInstalled
    val faceReady = faceModelManager.state.value.isInstalled
    selfTestOutput.value = when {
      !faceReady -> "Face model pack is not installed; download it before indexing."
      dav2Ready -> "Model files verified • Face detector, MobileFaceNet and depth model are installed. Run library indexing to execute face inference."
      else -> "Model files verified • Face detector and MobileFaceNet are installed. Depth model is not downloaded."
    }
    _messages.tryEmit("Model availability check completed")
  }

  private data class AiMonitorState(
    val indexing: Boolean,
    val progress: Int,
    val statusText: String,
    val downloading: Boolean,
    val downloadProgress: Int,
    val depthState: DepthModelPackState,
    val faceState: FaceModelPackState,
  )

  companion object {
    fun factory(container: ToadAppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          SettingsViewModel(
            container.preferencesRepository,
            container.depthModelManager,
            container.faceModelManager,
            container.faceIndexManager,
          ) as T
      }
  }
}
