package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AlbumEntity
import com.example.data.local.SampleMediaCatalog
import com.example.data.media.MediaAccessLevel
import com.example.data.media.MediaPermissionHelper
import com.example.data.media.MediaShareHelper
import com.example.data.model.AlbumFolder
import com.example.data.model.GalleryMediaItem
import com.example.data.model.LayoutMode
import com.example.data.model.MediaCategory
import com.example.data.model.MediaType
import com.example.data.model.SearchTabMode
import com.example.data.model.StorySlide
import com.example.data.model.TimelineFolderFilter
import com.example.data.model.TonePreset
import com.example.data.model.UserPreferences
import com.example.data.model.VaultAutoLock
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MapDisplayMode(val label: String) {
  PINS("Pins"),
  HEATMAP("Heatmap"),
}

data class GalleryUiState(
  val preferences: UserPreferences = UserPreferences(),
  val mediaAccessLevel: MediaAccessLevel = MediaAccessLevel.NONE,
  val isSyncingMedia: Boolean = false,
  val permissionBannerDismissed: Boolean = false,
  val activeCategory: MediaCategory = MediaCategory.ALL,
  val activeFolderFilter: TimelineFolderFilter? = null,
  val visibleMedia: List<GalleryMediaItem> = emptyList(),
  val filteredTimelineMedia: List<GalleryMediaItem> = emptyList(),
  val customAlbums: List<AlbumEntity> = emptyList(),
  val folderDestinations: List<String> = listOf("Camera", "Travel", "Screenshots", "Downloads"),
  val stories: List<StorySlide> = SampleMediaCatalog.stories,
  val folderAlbums: List<AlbumFolder> = emptyList(),
  val vaultMedia: List<GalleryMediaItem> = emptyList(),
  val vaultDecryptedPreviews: Map<Long, ByteArray> = emptyMap(),
  val trashedMedia: List<GalleryMediaItem> = emptyList(),
  val selectedMediaIds: Set<Long> = emptySet(),
  val isSelectionMode: Boolean = false,
  val showBatchMoveDialog: Boolean = false,
  val showBatchDeleteConfirmDialog: Boolean = false,
  val isPreparingShare: Boolean = false,
  val searchTabMode: SearchTabMode = SearchTabMode.MAGIC_SEARCH,
  val searchQuery: String = "",
  val searchOnlyLocation: Boolean = false,
  val searchTypeFilter: MediaType? = null,
  val searchResults: List<GalleryMediaItem> = emptyList(),
  val geoItems: List<GalleryMediaItem> = emptyList(),
  val mapMode: MapDisplayMode = MapDisplayMode.PINS,
  val selectedMapPinId: Long? = null,
  val activeLightboxId: Long? = null,
  val activeStoryIndex: Int? = null,
  val showVaultDialog: Boolean = false,
  val isVaultUnlocked: Boolean = false,
  val showRecycleBinSheet: Boolean = false,
)

class GalleryViewModel(
  private val appContext: Context,
  private val galleryRepo: GalleryRepository,
  private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {

  private val mediaShareHelper = MediaShareHelper(appContext)
  private val mediaAccessLevel =
    MutableStateFlow(MediaPermissionHelper.checkAccessLevel(appContext))
  private val isSyncingMedia = MutableStateFlow(false)
  private val permissionBannerDismissed = MutableStateFlow(false)
  private val activeCategory = MutableStateFlow(MediaCategory.ALL)
  private val activeFolderFilter = MutableStateFlow<TimelineFolderFilter?>(null)

  private val selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
  private val explicitSelectionMode = MutableStateFlow(false)
  private val showBatchMoveDialog = MutableStateFlow(false)
  private val showBatchDeleteConfirmDialog = MutableStateFlow(false)
  private val isPreparingShare = MutableStateFlow(false)

  private val searchTabMode = MutableStateFlow(SearchTabMode.MAGIC_SEARCH)
  private val searchQuery = MutableStateFlow("")
  private val searchOnlyLocation = MutableStateFlow(false)
  private val searchTypeFilter = MutableStateFlow<MediaType?>(null)
  private val mapMode = MutableStateFlow(MapDisplayMode.PINS)
  private val selectedMapPinId = MutableStateFlow<Long?>(null)
  private val activeLightboxId = MutableStateFlow<Long?>(null)
  private val activeStoryIndex = MutableStateFlow<Int?>(null)
  private val showVaultDialog = MutableStateFlow(false)
  private val isVaultUnlocked = MutableStateFlow(false)
  private val vaultDecryptedPreviews = MutableStateFlow<Map<Long, ByteArray>>(emptyMap())
  private val showRecycleBinSheet = MutableStateFlow(false)

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 10)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private val syncControlsFlow =
    combine(
      mediaAccessLevel,
      isSyncingMedia,
      permissionBannerDismissed,
      activeCategory,
      activeFolderFilter,
    ) { access, syncing, dismissed, cat, folder ->
      SyncAndFilterControls(access, syncing, dismissed, cat, folder)
    }

  private val selectionControlsFlow =
    combine(
      selectedMediaIds,
      explicitSelectionMode,
      showBatchMoveDialog,
      showBatchDeleteConfirmDialog,
      isPreparingShare,
    ) { ids, explicit, moveDlg, deleteDlg, sharing ->
      SelectionControls(
        selectedIds = ids,
        isSelectionMode = explicit || ids.isNotEmpty(),
        showMoveDialog = moveDlg,
        showDeleteConfirm = deleteDlg,
        isPreparingShare = sharing,
      )
    }

  private val searchControlsFlow =
    combine(
      searchTabMode,
      searchQuery,
      searchOnlyLocation,
      searchTypeFilter,
      mapMode,
    ) { tabMode, query, onlyLoc, typeFilter, mMode ->
      SearchControls(tabMode, query, onlyLoc, typeFilter, mMode)
    }

  private val modalControlsFlow =
    combine(
      selectedMapPinId,
      activeLightboxId,
      activeStoryIndex,
      showVaultDialog,
      isVaultUnlocked,
      vaultDecryptedPreviews,
      showRecycleBinSheet,
    ) { arr ->
      @Suppress("UNCHECKED_CAST")
      ModalControls(
        selectedPinId = arr[0] as Long?,
        lightboxId = arr[1] as Long?,
        storyIdx = arr[2] as Int?,
        vaultDialog = arr[3] as Boolean,
        vaultUnlocked = arr[4] as Boolean,
        vaultPreviews = arr[5] as Map<Long, ByteArray>,
        recycleSheet = arr[6] as Boolean,
      )
    }

  private val mediaBundleFlow =
    combine(
      galleryRepo.visibleMedia,
      galleryRepo.vaultMedia,
      galleryRepo.trashedMedia,
      galleryRepo.customAlbums,
    ) { visible, vault, trashed, albums ->
      MediaBundle(visible, vault, trashed, albums)
    }

  private val uiControlsBundleFlow =
    combine(syncControlsFlow, selectionControlsFlow, searchControlsFlow, modalControlsFlow) {
      syncCtrl,
      selCtrl,
      searchCtrl,
      modals ->
      UiControlsBundle(syncCtrl, selCtrl, searchCtrl, modals)
    }

  val uiState: StateFlow<GalleryUiState> =
    combine(prefsRepo.preferences, mediaBundleFlow, uiControlsBundleFlow) { prefs, media, ctrls ->
        val syncCtrl = ctrls.sync
        val selCtrl = ctrls.selection
        val searchCtrl = ctrls.search
        val modals = ctrls.modals

        val filteredTimeline =
          media.visible.filter { item ->
            val folderOk =
              when (val f = syncCtrl.folderFilter) {
                null -> true
                TimelineFolderFilter.Favorites -> item.isFavorite
                is TimelineFolderFilter.DeviceBucket ->
                  item.bucketName.equals(f.bucketName, ignoreCase = true)
                is TimelineFolderFilter.CustomAlbum -> item.albumId == f.albumId
              }
            val screenshotOk =
              if (syncCtrl.folderFilter != null) true
              else !(prefs.hideScreenshots && item.category == MediaCategory.SCREENSHOTS)
            val categoryOk =
              if (syncCtrl.category == MediaCategory.ALL) true
              else item.category == syncCtrl.category

            folderOk && screenshotOk && categoryOk
          }

        val q = searchCtrl.query.trim().lowercase()
        val searchMatches =
          media.visible.filter { item ->
            val locOk = !searchCtrl.onlyLoc || item.hasGps
            val typeOk = searchCtrl.typeFilter == null || item.mediaType == searchCtrl.typeFilter
            val queryOk =
              q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.locationName.lowercase().contains(q) ||
                item.bucketName.lowercase().contains(q) ||
                item.ocrText.lowercase().contains(q) ||
                item.tags.any { it.lowercase().contains(q) }
            locOk && typeOk && queryOk
          }

        val geo = media.visible.filter { it.hasGps }
        val resolvedPinId =
          modals.selectedPinId?.takeIf { id -> geo.any { it.id == id } } ?: geo.firstOrNull()?.id

        val favoritesList = media.visible.filter { it.isFavorite }
        val favoritesFolder =
          AlbumFolder(
            id = -100L,
            name = "Favorites",
            itemCount = favoritesList.size,
            coverUriString = favoritesList.firstOrNull()?.uriString,
            coverDrawableName = favoritesList.firstOrNull()?.drawableResName ?: "sample_espresso",
            filterCategory = null,
            isFavoritesAlbum = true,
          )

        val deviceBucketFolders =
          media.visible
            .groupBy { it.bucketName.ifBlank { "Camera" } }
            .entries
            .sortedByDescending { it.value.size }
            .mapIndexed { index, (bucket, itemsInBucket) ->
              val cover = itemsInBucket.firstOrNull()
              AlbumFolder(
                id = -(index + 1).toLong(),
                name = bucket,
                itemCount = itemsInBucket.size,
                coverUriString = cover?.uriString,
                coverDrawableName = cover?.drawableResName ?: "sample_forest_trail",
                filterCategory = null,
                filterBucketName = bucket,
              )
            }

        val userFolders =
          media.customAlbums.map { album ->
            val albumItems = media.visible.filter { it.albumId == album.id }
            val cover = albumItems.firstOrNull()
            AlbumFolder(
              id = album.id,
              name = album.name,
              itemCount = albumItems.size,
              coverUriString = cover?.uriString ?: album.coverUriString,
              coverDrawableName = cover?.drawableResName ?: album.coverDrawableName ?: "sample_lake_sunset",
              filterCategory = null,
              customAlbumId = album.id,
            )
          }

        val validSelection = selCtrl.selectedIds.filter { id -> media.visible.any { it.id == id } }.toSet()
        val folderDestSet = linkedSetOf<String>()
        deviceBucketFolders.forEach { folderDestSet.add(it.name) }
        listOf("Camera", "Travel", "Screenshots", "Downloads").forEach { folderDestSet.add(it) }

        GalleryUiState(
          preferences = prefs,
          mediaAccessLevel = syncCtrl.accessLevel,
          isSyncingMedia = syncCtrl.isSyncing,
          permissionBannerDismissed = syncCtrl.bannerDismissed,
          activeCategory = syncCtrl.category,
          activeFolderFilter = syncCtrl.folderFilter,
          visibleMedia = media.visible,
          filteredTimelineMedia = filteredTimeline,
          customAlbums = media.customAlbums,
          folderDestinations = folderDestSet.toList(),
          folderAlbums = listOf(favoritesFolder) + deviceBucketFolders + userFolders,
          vaultMedia = media.vault,
          vaultDecryptedPreviews = modals.vaultPreviews,
          trashedMedia = media.trashed,
          selectedMediaIds = validSelection,
          isSelectionMode = selCtrl.isSelectionMode && (validSelection.isNotEmpty() || explicitSelectionMode.value),
          showBatchMoveDialog = selCtrl.showMoveDialog,
          showBatchDeleteConfirmDialog = selCtrl.showDeleteConfirm,
          isPreparingShare = selCtrl.isPreparingShare,
          searchTabMode = searchCtrl.tabMode,
          searchQuery = searchCtrl.query,
          searchOnlyLocation = searchCtrl.onlyLoc,
          searchTypeFilter = searchCtrl.typeFilter,
          searchResults = searchMatches,
          geoItems = geo,
          mapMode = searchCtrl.mapMode,
          selectedMapPinId = resolvedPinId,
          activeLightboxId = modals.lightboxId,
          activeStoryIndex = modals.storyIdx,
          showVaultDialog = modals.vaultDialog,
          isVaultUnlocked = modals.vaultUnlocked,
          showRecycleBinSheet = modals.recycleSheet,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState(mediaAccessLevel = MediaPermissionHelper.checkAccessLevel(appContext)),
      )

  init {
    viewModelScope.launch {
      prefsRepo.ensurePinInitialized()
      galleryRepo.seedInitialDataIfEmpty()
      refreshMediaPermissionsAndSync(silent = true)
    }
    viewModelScope.launch {
      galleryRepo.vaultMedia.collect { vaultItems ->
        if (isVaultUnlocked.value) {
          vaultDecryptedPreviews.value = galleryRepo.decryptVaultPreviews(vaultItems)
        }
      }
    }
  }

  fun refreshMediaPermissionsAndSync(silent: Boolean = false) {
    val level = MediaPermissionHelper.checkAccessLevel(appContext)
    mediaAccessLevel.value = level
    if (level == MediaAccessLevel.NONE) {
      if (!silent) emitMessage("Media permission required to scan device library")
      return
    }
    viewModelScope.launch {
      isSyncingMedia.value = true
      val synced = galleryRepo.syncDeviceMediaStore()
      prefsRepo.setLastMediaSyncCount(synced)
      isSyncingMedia.value = false
      if (!silent) {
        emitMessage(
          if (synced > 0) "Synced $synced items from device MediaStore"
          else "Device library is up to date"
        )
      }
    }
  }

  fun dismissPermissionBanner() {
    permissionBannerDismissed.value = true
  }

  fun setPhotoCategory(category: MediaCategory) {
    activeCategory.value = category
  }

  fun openFolderFilter(filter: TimelineFolderFilter?) {
    activeFolderFilter.value = filter
    activeCategory.value = MediaCategory.ALL
    clearSelection()
  }

  fun clearFolderFilter() {
    activeFolderFilter.value = null
  }

  fun toggleHideScreenshots() {
    viewModelScope.launch {
      val next = !uiState.value.preferences.hideScreenshots
      prefsRepo.setHideScreenshots(next)
      emitMessage(
        if (next) "Screenshots hidden from timeline" else "Showing all screenshots"
      )
    }
  }

  fun toggleQuickLayout() {
    viewModelScope.launch {
      val next =
        if (uiState.value.preferences.layoutMode == LayoutMode.COLLAGE) LayoutMode.GRID
        else LayoutMode.COLLAGE
      prefsRepo.updateLayoutMode(next)
      emitMessage("Switched to ${next.label} layout")
    }
  }

  fun resetPhotoFilters() {
    activeCategory.value = MediaCategory.ALL
    activeFolderFilter.value = null
    viewModelScope.launch { prefsRepo.setHideScreenshots(false) }
  }

  // Multi-Select Mode
  fun enterSelectionMode(initialItemId: Long? = null) {
    explicitSelectionMode.value = true
    if (initialItemId != null) {
      selectedMediaIds.value = selectedMediaIds.value + initialItemId
    }
  }

  fun toggleMediaSelection(id: Long) {
    val current = selectedMediaIds.value
    val next = if (id in current) current - id else current + id
    selectedMediaIds.value = next
    if (next.isEmpty()) {
      explicitSelectionMode.value = false
    }
  }

  fun toggleSectionSelection(sectionIds: List<Long>) {
    if (sectionIds.isEmpty()) return
    explicitSelectionMode.value = true
    val current = selectedMediaIds.value
    val allSelected = sectionIds.all { it in current }
    selectedMediaIds.value =
      if (allSelected) current - sectionIds.toSet() else current + sectionIds.toSet()
  }

  fun toggleSelectAllVisible() {
    val allIds = uiState.value.filteredTimelineMedia.map { it.id }.toSet()
    if (allIds.isEmpty()) return
    explicitSelectionMode.value = true
    val current = selectedMediaIds.value
    val allSelected = allIds.all { it in current }
    selectedMediaIds.value = if (allSelected) emptySet() else allIds
  }

  fun clearSelection() {
    selectedMediaIds.value = emptySet()
    explicitSelectionMode.value = false
    showBatchMoveDialog.value = false
    showBatchDeleteConfirmDialog.value = false
  }

  fun openBatchMoveDialog() {
    if (selectedMediaIds.value.isNotEmpty()) {
      showBatchMoveDialog.value = true
    } else {
      emitMessage("Select at least one item to move")
    }
  }

  fun closeBatchMoveDialog() {
    showBatchMoveDialog.value = false
  }

  fun openBatchDeleteConfirmDialog() {
    if (selectedMediaIds.value.isNotEmpty()) {
      showBatchDeleteConfirmDialog.value = true
    } else {
      emitMessage("Select at least one item to delete")
    }
  }

  fun closeBatchDeleteConfirmDialog() {
    showBatchDeleteConfirmDialog.value = false
  }

  fun confirmBatchDeleteToTrash() {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) {
      showBatchDeleteConfirmDialog.value = false
      return
    }
    viewModelScope.launch {
      galleryRepo.setTrashedBatch(ids, true)
      clearSelection()
      emitMessage("Moved ${ids.size} ${if (ids.size == 1) "item" else "items"} to Recycle Bin")
    }
  }

  fun batchToggleFavorite() {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) return
    val visibleMap = uiState.value.visibleMedia.associateBy { it.id }
    val allAlreadyFav = ids.all { visibleMap[it]?.isFavorite == true }
    val targetFav = !allAlreadyFav
    viewModelScope.launch {
      galleryRepo.setFavoriteBatch(ids, targetFav)
      clearSelection()
      emitMessage(
        if (targetFav) "Added ${ids.size} items to Favorites"
        else "Removed ${ids.size} items from Favorites"
      )
    }
  }

  fun batchHideToVault() {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.hideToVaultEncrypted(ids)
      clearSelection()
      emitMessage("Encrypted & moved $count items to Hidden Vault")
    }
  }

  fun batchMoveToAlbum(albumId: Long, albumName: String) {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
      galleryRepo.assignToAlbumBatch(ids, albumId)
      clearSelection()
      emitMessage("Moved ${ids.size} items to album \"$albumName\"")
    }
  }

  fun batchCreateAlbumAndMove(albumName: String) {
    val clean = albumName.trim()
    val ids = selectedMediaIds.value.toList()
    if (clean.isEmpty() || ids.isEmpty()) return
    viewModelScope.launch {
      galleryRepo.createAlbumAndAssignBatch(clean, ids)
      clearSelection()
      emitMessage("Created album \"$clean\" and moved ${ids.size} items")
    }
  }

  fun batchMoveToFolder(bucketName: String) {
    val clean = bucketName.trim()
    val ids = selectedMediaIds.value.toList()
    if (clean.isEmpty() || ids.isEmpty()) return
    viewModelScope.launch {
      galleryRepo.moveToFolderBatch(ids, clean)
      clearSelection()
      emitMessage("Moved ${ids.size} items to folder \"$clean\"")
    }
  }

  fun batchShareSelected(onLaunchIntent: (Intent) -> Unit) {
    val ids = selectedMediaIds.value
    if (ids.isEmpty() || isPreparingShare.value) return
    val selectedItems = uiState.value.visibleMedia.filter { it.id in ids }
    if (selectedItems.isEmpty()) return

    viewModelScope.launch {
      isPreparingShare.value = true
      val payload = mediaShareHelper.buildShareIntent(selectedItems)
      isPreparingShare.value = false
      if (payload == null) {
        emitMessage("Unable to prepare selected media for sharing")
        return@launch
      }
      onLaunchIntent(payload.chooserIntent)
      clearSelection()
      emitMessage("Ready to share ${payload.itemCount} ${if (payload.itemCount == 1) "item" else "items"}")
    }
  }

  // Search & Places Map Mode
  fun setSearchTabMode(mode: SearchTabMode) {
    searchTabMode.value = mode
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun toggleSearchOnlyLocation() {
    searchOnlyLocation.value = !searchOnlyLocation.value
  }

  fun setSearchTypeFilter(type: MediaType?) {
    searchTypeFilter.value = type
  }

  fun openSmartCollection(name: String) {
    if (name == "Places") {
      searchTabMode.value = SearchTabMode.PLACES_MAP
      return
    }
    searchTabMode.value = SearchTabMode.MAGIC_SEARCH
    searchOnlyLocation.value = false
    searchTypeFilter.value = null
    searchQuery.value =
      when (name) {
        "People" -> "portraits"
        "Things" -> "dogs"
        "Receipts & Docs" -> "receipt"
        else -> ""
      }
  }

  fun setMapMode(mode: MapDisplayMode) {
    mapMode.value = mode
  }

  fun selectMapPin(id: Long) {
    selectedMapPinId.value = id
  }

  fun openLightbox(id: Long) {
    activeLightboxId.value = id
  }

  fun closeLightbox() {
    activeLightboxId.value = null
  }

  fun stepLightbox(delta: Int) {
    val currentId = activeLightboxId.value ?: return
    val list =
      uiState.value.filteredTimelineMedia.takeIf { list -> list.any { it.id == currentId } }
        ?: uiState.value.visibleMedia
    if (list.isEmpty()) return
    val idx = list.indexOfFirst { it.id == currentId }
    if (idx < 0) return
    val nextIdx = (idx + delta).coerceIn(0, list.lastIndex)
    activeLightboxId.value = list[nextIdx].id
  }

  fun toggleFavorite(item: GalleryMediaItem) {
    viewModelScope.launch {
      val next = !item.isFavorite
      galleryRepo.toggleFavorite(item.id, next)
      emitMessage(if (next) "Added to Favorites" else "Removed from Favorites")
    }
  }

  fun hideToVault(id: Long) {
    viewModelScope.launch {
      galleryRepo.hideToVaultEncrypted(listOf(id))
      activeLightboxId.value = null
      emitMessage("Encrypted with AES-256 & moved to Hidden Vault")
    }
  }

  fun unhideFromVault(id: Long) {
    viewModelScope.launch {
      galleryRepo.unhideFromVaultEncrypted(id)
      emitMessage("Decrypted & restored to main timeline")
    }
  }

  fun deletePermanentlyFromVault(id: Long) {
    viewModelScope.launch {
      galleryRepo.deletePermanentlyFromVault(id)
      emitMessage("Encrypted vault file deleted permanently")
    }
  }

  fun moveToTrash(id: Long) {
    viewModelScope.launch {
      galleryRepo.setTrashed(id, true)
      activeLightboxId.value = null
      emitMessage("Moved to Recycle Bin")
    }
  }

  fun restoreFromTrash(id: Long) {
    viewModelScope.launch {
      galleryRepo.setTrashed(id, false)
      emitMessage("Restored item from Recycle Bin")
    }
  }

  fun emptyRecycleBin() {
    viewModelScope.launch {
      galleryRepo.emptyRecycleBin()
      emitMessage("Recycle Bin emptied")
    }
  }

  fun setTonePreset(id: Long, preset: TonePreset) {
    viewModelScope.launch {
      galleryRepo.setTonePreset(id, preset)
      emitMessage("Applied ${preset.label} tone preset")
    }
  }

  fun openStory(index: Int) {
    activeStoryIndex.value = index.coerceIn(0, SampleMediaCatalog.stories.lastIndex)
  }

  fun closeStory() {
    activeStoryIndex.value = null
  }

  fun nextStorySlide() {
    val current = activeStoryIndex.value ?: return
    activeStoryIndex.value = (current + 1) % SampleMediaCatalog.stories.size
  }

  fun openVaultDialog() {
    val prefs = uiState.value.preferences
    val withinGraceWindow =
      prefs.vaultAutoLock != VaultAutoLock.IMMEDIATELY &&
        prefs.vaultLastUnlockedAt > 0L &&
        (System.currentTimeMillis() - prefs.vaultLastUnlockedAt) <= prefs.vaultAutoLock.timeoutMs

    if (withinGraceWindow) {
      isVaultUnlocked.value = true
      viewModelScope.launch {
        vaultDecryptedPreviews.value = galleryRepo.decryptVaultPreviews(uiState.value.vaultMedia)
      }
    } else {
      isVaultUnlocked.value = false
      vaultDecryptedPreviews.value = emptyMap()
    }
    showVaultDialog.value = true
  }

  fun closeVaultDialog() {
    showVaultDialog.value = false
    if (uiState.value.preferences.vaultAutoLock == VaultAutoLock.IMMEDIATELY) {
      isVaultUnlocked.value = false
      vaultDecryptedPreviews.value = emptyMap()
    }
  }

  fun lockVaultNow() {
    isVaultUnlocked.value = false
    vaultDecryptedPreviews.value = emptyMap()
    viewModelScope.launch { prefsRepo.clearVaultUnlockSession() }
    emitMessage("Hidden Vault locked")
  }

  fun verifyVaultPinAsync(enteredPin: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val ok = prefsRepo.verifyPin(enteredPin)
      if (ok) {
        prefsRepo.recordVaultUnlocked()
        isVaultUnlocked.value = true
        vaultDecryptedPreviews.value = galleryRepo.decryptVaultPreviews(uiState.value.vaultMedia)
        emitMessage("Hidden Vault unlocked • AES-256 decrypted")
      } else {
        emitMessage("Incorrect Vault PIN")
      }
      onResult(ok)
    }
  }

  fun unlockVaultBiometric() {
    viewModelScope.launch {
      prefsRepo.recordVaultUnlocked()
      isVaultUnlocked.value = true
      vaultDecryptedPreviews.value = galleryRepo.decryptVaultPreviews(uiState.value.vaultMedia)
      emitMessage("Hidden Vault unlocked via Biometrics")
    }
  }

  fun openRecycleBin() {
    showRecycleBinSheet.value = true
  }

  fun closeRecycleBin() {
    showRecycleBinSheet.value = false
  }

  fun createAlbum(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      galleryRepo.createAlbum(name)
      emitMessage("Created album \"$name\"")
    }
  }

  fun showMessage(msg: String) {
    emitMessage(msg)
  }

  private fun emitMessage(msg: String) {
    _messages.tryEmit(msg)
  }

  private data class SyncAndFilterControls(
    val accessLevel: MediaAccessLevel,
    val isSyncing: Boolean,
    val bannerDismissed: Boolean,
    val category: MediaCategory,
    val folderFilter: TimelineFolderFilter?,
  )

  private data class SelectionControls(
    val selectedIds: Set<Long>,
    val isSelectionMode: Boolean,
    val showMoveDialog: Boolean,
    val showDeleteConfirm: Boolean,
    val isPreparingShare: Boolean,
  )

  private data class SearchControls(
    val tabMode: SearchTabMode,
    val query: String,
    val onlyLoc: Boolean,
    val typeFilter: MediaType?,
    val mapMode: MapDisplayMode,
  )

  private data class ModalControls(
    val selectedPinId: Long?,
    val lightboxId: Long?,
    val storyIdx: Int?,
    val vaultDialog: Boolean,
    val vaultUnlocked: Boolean,
    val vaultPreviews: Map<Long, ByteArray>,
    val recycleSheet: Boolean,
  )

  private data class MediaBundle(
    val visible: List<GalleryMediaItem>,
    val vault: List<GalleryMediaItem>,
    val trashed: List<GalleryMediaItem>,
    val customAlbums: List<AlbumEntity>,
  )

  private data class UiControlsBundle(
    val sync: SyncAndFilterControls,
    val selection: SelectionControls,
    val search: SearchControls,
    val modals: ModalControls,
  )

  companion object {
    fun factory(container: ToadAppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          GalleryViewModel(
            container.appContext,
            container.galleryRepository,
            container.preferencesRepository,
          ) as T
      }
  }
}
