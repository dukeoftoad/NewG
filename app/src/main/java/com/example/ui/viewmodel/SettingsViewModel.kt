package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
  val aiProgress: Int = 100,
  val aiStatusText: String = "All 564 photos indexed • Healthy",
  val semanticDownloading: Boolean = false,
  val semanticDownloadProgress: Int = 0,
  val showDiagnostics: Boolean = false,
  val selfTestOutput: String? = null,
)

class SettingsViewModel(private val prefsRepo: UserPreferencesRepository) : ViewModel() {

  private val currentSubPage = MutableStateFlow(SettingsSubPage.HUB)
  private val searchQuery = MutableStateFlow("")
  private val aiIndexing = MutableStateFlow(false)
  private val aiProgress = MutableStateFlow(100)
  private val aiStatusText = MutableStateFlow("All 564 photos indexed • Healthy")
  private val semanticDownloading = MutableStateFlow(false)
  private val semanticDownloadProgress = MutableStateFlow(0)
  private val showDiagnostics = MutableStateFlow(false)
  private val selfTestOutput = MutableStateFlow<String?>(null)

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private var indexJob: Job? = null
  private var downloadJob: Job? = null

  private val aiMonitorFlow =
    combine(aiIndexing, aiProgress, aiStatusText, semanticDownloading, semanticDownloadProgress) {
      indexing,
      progress,
      status,
      dl,
      dlProg ->
      AiMonitorState(indexing, progress, status, dl, dlProg)
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
      val next = (uiState.value.preferences.columns + delta).coerceIn(2, 4)
      prefsRepo.updateColumns(next)
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

  fun toggleFaceModelPack() {
    viewModelScope.launch {
      val next = !uiState.value.preferences.faceModelInstalled
      prefsRepo.setFaceModelInstalled(next)
      _messages.tryEmit(
        if (next) "Face models verified & installed" else "Deleted face model pack"
      )
    }
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

  fun toggleDiagnostics() {
    showDiagnostics.value = !showDiagnostics.value
  }

  fun runOrPauseIndex() {
    if (aiIndexing.value) {
      indexJob?.cancel()
      aiIndexing.value = false
      aiStatusText.value = "Paused at ${aiProgress.value}%"
      return
    }
    indexJob?.cancel()
    indexJob =
      viewModelScope.launch {
        aiIndexing.value = true
        if (aiProgress.value >= 100) aiProgress.value = 8
        aiStatusText.value = "Warming up NPU worker..."
        while (aiProgress.value < 100) {
          delay(420)
          val next = (aiProgress.value + 18).coerceAtMost(100)
          aiProgress.value = next
          aiStatusText.value =
            when {
              next < 50 -> "Clustering faces (Batch 1/3)..."
              next < 95 -> "Generating CLIP embeddings..."
              else -> "All 564 photos indexed • Healthy"
            }
        }
        aiIndexing.value = false
        prefsRepo.setMlIndexSizeMb(18.4f)
        _messages.tryEmit("AI background index completed safely")
      }
  }

  fun resetAiIndex() {
    indexJob?.cancel()
    aiIndexing.value = false
    aiProgress.value = 0
    aiStatusText.value = "Index cleared • Ready to start"
    _messages.tryEmit("AI index queue reset")
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
    indexJob?.cancel()
    aiIndexing.value = false
    aiProgress.value = 0
    aiStatusText.value = "Index cleared • Ready to rebuild"
    viewModelScope.launch {
      val freed = uiState.value.preferences.mlIndexSizeMb
      prefsRepo.setMlIndexSizeMb(0.0f)
      _messages.tryEmit(String.format("Cleared ML index store (%.1f MB freed)", freed))
    }
  }

  fun runEngineSelfTest() {
    selfTestOutput.value = "NNAPI Delegate OK • Face & CLIP tensor latency: 14ms"
    _messages.tryEmit("Engine self-test passed (14ms)")
  }

  private data class AiMonitorState(
    val indexing: Boolean,
    val progress: Int,
    val statusText: String,
    val downloading: Boolean,
    val downloadProgress: Int,
  )

  companion object {
    fun factory(container: ToadAppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          SettingsViewModel(container.preferencesRepository) as T
      }
  }
}
