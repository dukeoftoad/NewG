package com.toadgallery.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.toadgallery.app.data.ai.DepthAnythingV2Engine
import com.toadgallery.app.data.ai.DepthMapResult
import com.toadgallery.app.data.ai.DepthModelManager
import com.toadgallery.app.data.ai.DepthModelPackState
import com.toadgallery.app.data.ai.FacePersonClusterer
import com.toadgallery.app.data.memories.OnThisDay
import com.toadgallery.app.data.memories.WeekMemories
import com.toadgallery.app.data.ai.FacePersonGroup
import com.toadgallery.app.data.ai.FacePersonSuggestion
import com.toadgallery.app.data.ai.FacePersonSuggestionRanker
import com.toadgallery.app.data.ai.FaceReference
import com.toadgallery.app.data.editor.PhotoEditorRenderer
import com.toadgallery.app.data.local.AlbumEntity
import com.toadgallery.app.data.local.FaceAssignmentEntity
import com.toadgallery.app.data.local.FaceCorrectionHistoryEntity
import com.toadgallery.app.data.local.FacePersonEntity
import com.toadgallery.app.data.local.SampleMediaCatalog
import com.toadgallery.app.data.media.MediaAccessLevel
import com.toadgallery.app.data.media.MediaPermissionHelper
import com.toadgallery.app.data.media.MediaShareHelper
import com.toadgallery.app.data.model.AlbumFolder
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.LayoutMode
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.PhotoEditRecipe
import com.toadgallery.app.data.model.SearchTabMode
import com.toadgallery.app.data.model.StorySlide
import com.toadgallery.app.data.model.TimelineFolderFilter
import com.toadgallery.app.data.model.TonePreset
import com.toadgallery.app.data.model.UserPreferences
import com.toadgallery.app.data.model.VaultAutoLock
import com.toadgallery.app.data.preferences.UserPreferencesRepository
import com.toadgallery.app.data.repository.GalleryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MapDisplayMode(val label: String) {
  PINS("Pins"),
  HEATMAP("Heatmap"),
}

data class GalleryUiState(
  val preferences: UserPreferences = UserPreferences(),
  val mediaAccessLevel: MediaAccessLevel = MediaAccessLevel.NONE,
  val isSyncingMedia: Boolean = false,
  val permissionBannerDismissed: Boolean = false,
  val mediaManagementPrompt: Boolean = false,
  val activeCategory: MediaCategory = MediaCategory.ALL,
  val activeFolderFilter: TimelineFolderFilter? = null,
  val visibleMedia: List<GalleryMediaItem> = emptyList(),
  val filteredTimelineMedia: List<GalleryMediaItem> = emptyList(),
  val customAlbums: List<AlbumEntity> = emptyList(),
  val peopleGroups: List<FacePersonGroup> = emptyList(),
  val unassignedFaceGroups: List<FacePersonGroup> = emptyList(),
  val facePersonSuggestions: Map<String, List<FacePersonSuggestion>> = emptyMap(),
  val ignoredPeopleGroups: List<FacePersonGroup> = emptyList(),
  val faceCorrectionHistory: List<FaceCorrectionHistoryEntity> = emptyList(),
  val faceIndexRevisions: Map<Long, Long> = emptyMap(),
  val folderDestinations: List<String> = listOf("Camera", "Travel", "Screenshots", "Downloads"),
  val stories: List<StorySlide> = SampleMediaCatalog.stories,
  val folderAlbums: List<AlbumFolder> = emptyList(),
  val vaultMedia: List<GalleryMediaItem> = emptyList(),
  val vaultDecryptedPreviews: Map<Long, ByteArray> = emptyMap(),
  val trashedMedia: List<GalleryMediaItem> = emptyList(),
  val trashedExpiringSoonCount: Int = 0,
  val onThisDay: List<GalleryMediaItem> = emptyList(),
  val weekMemories: List<GalleryMediaItem> = emptyList(),
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
  val activeEditorId: Long? = null,
  val activeStoryIndex: Int? = null,
  val showVaultDialog: Boolean = false,
  val isVaultUnlocked: Boolean = false,
  val showRecycleBinSheet: Boolean = false,
  val depthModelState: DepthModelPackState = DepthModelPackState(),
  val isSavingEdit: Boolean = false,
)

class GalleryViewModel(
  private val appContext: Context,
  private val galleryRepo: GalleryRepository,
  private val prefsRepo: UserPreferencesRepository,
  private val depthModelManager: DepthModelManager,
  private val depthEngine: DepthAnythingV2Engine,
) : ViewModel() {

  private val mediaShareHelper = MediaShareHelper(appContext)
  private val mediaAccessLevel =
    MutableStateFlow(MediaPermissionHelper.checkAccessLevel(appContext))
  private val isSyncingMedia = MutableStateFlow(false)
  private var syncJob: Job? = null
  private var lastSilentSyncAtMs: Long = 0L
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
  private val activeLightboxId = MutableStateFlow(null as Long?)
  private val activeEditorId = MutableStateFlow(null as Long?)
  private val isSavingEdit = MutableStateFlow(false)
  private val activeStoryIndex = MutableStateFlow(null as Int?)
  private val showVaultDialog = MutableStateFlow(false)
  private val isVaultUnlocked = MutableStateFlow(false)
  private val vaultDecryptedPreviews = MutableStateFlow<Map<Long, ByteArray>>(emptyMap())
  private val showRecycleBinSheet = MutableStateFlow(false)
  private val showMediaManagementPrompt = MutableStateFlow(false)

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
      activeEditorId,
      activeStoryIndex,
      showVaultDialog,
      isVaultUnlocked,
      vaultDecryptedPreviews,
      showRecycleBinSheet,
      showMediaManagementPrompt,
      depthModelManager.state,
      isSavingEdit,
    ) { arr ->
      @Suppress("UNCHECKED_CAST")
      ModalControls(
        selectedPinId = arr[0] as Long?,
        lightboxId = arr[1] as Long?,
        editorId = arr[2] as Long?,
        storyIdx = arr[3] as Int?,
        vaultDialog = arr[4] as Boolean,
        vaultUnlocked = arr[5] as Boolean,
        vaultPreviews = arr[6] as Map<Long, ByteArray>,
        recycleSheet = arr[7] as Boolean,
        mediaManagementPrompt = arr[8] as Boolean,
        depthState = arr[9] as DepthModelPackState,
        savingEdit = arr[10] as Boolean,
      )
    }

  // Conflate rapid faceIndexedMedia emissions while keeping user feedback flows
  // (naming, assigning, excluding, ignoring, undoing) instantaneous with zero sample() lag.
  private val facePeopleBundleFlow =
    combine(
      galleryRepo.faceIndexedMedia.conflate(),
      galleryRepo.facePeople,
      galleryRepo.faceExclusions,
      galleryRepo.faceAssignments,
      galleryRepo.faceCorrectionHistory,
    ) { faceRows, facePeople, exclusions, assignments, history ->
      FacePeopleBundle(faceRows, facePeople, exclusions, assignments, history)
    }.conflate()

  private val facePeopleGroupsFlow =
    combine(facePeopleBundleFlow, prefsRepo.preferences) { bundle, prefs ->
        withContext(Dispatchers.Default) {
          val personRecords = bundle.facePeople.associateBy { it.clusterId }
          val rowsById = bundle.faceRows.associateBy { it.id }
          val manuallyAssignedPersonIds = bundle.faceAssignments.mapTo(HashSet()) { it.personClusterId }
          val excludedFaceReferences =
            bundle.faceExclusions
              .mapNotNull { exclusion ->
                val row = rowsById[exclusion.photoId] ?: return@mapNotNull null
                val currentIndex =
                  if (exclusion.faceId != null) {
                    FacePersonClusterer.faceIndexForId(row, exclusion.faceId)
                  } else exclusion.faceIndex
                currentIndex?.let { FaceReference(exclusion.photoId, it) }
              }
              .toSet()
          val manualFaceAssignments =
            bundle.faceAssignments
              .mapNotNull { assignment ->
                val row = rowsById[assignment.photoId] ?: return@mapNotNull null
                val currentIndex =
                  if (assignment.faceId != null) {
                    FacePersonClusterer.faceIndexForId(row, assignment.faceId)
                  } else assignment.faceIndex
                currentIndex?.let { FaceReference(assignment.photoId, it) to assignment.personClusterId }
              }
              .toMap()
          val allDetectedGroups =
            FacePersonClusterer.cluster(
                rows = bundle.faceRows,
                distanceThreshold = prefs.clusteringThreshold,
                excludedFaces = excludedFaceReferences,
                manualAssignments = manualFaceAssignments,
                minimumPhotos = 1,
              )
              .map { group ->
                val record = personRecords[group.id]
                val savedCoverIsInGroup = record?.coverPhotoId?.let { it in group.photoIds } == true
                val stableCoverIndex =
                  if (savedCoverIsInGroup && record?.coverFaceId != null) {
                    rowsById[record.coverPhotoId!!]?.let { row ->
                      FacePersonClusterer.faceIndexForId(row, record.coverFaceId!!)
                    }
                  } else null
                val coverFaceIndex =
                  when {
                    stableCoverIndex != null -> stableCoverIndex
                    record?.coverFaceId == null && savedCoverIsInGroup ->
                      record.coverFaceIndex ?: group.coverFaceIndex
                    else -> group.coverFaceIndex
                  }
                group.copy(
                  name = record?.name,
                  coverPhotoId =
                    if (savedCoverIsInGroup) record!!.coverPhotoId!! else group.coverPhotoId,
                  coverFaceIndex = coverFaceIndex,
                )
              }
          val detectedGroups =
            allDetectedGroups.filter { group ->
              group.photoIds.size >= 2 ||
                group.id in manuallyAssignedPersonIds ||
                personRecords.containsKey(group.id)
            }
          val unassignedFaceGroups =
            allDetectedGroups.filter { group ->
              group.photoIds.size == 1 &&
                group.id !in manuallyAssignedPersonIds &&
                personRecords[group.id] == null
            }
          val ignoredPeopleGroups =
            detectedGroups.filter { personRecords[it.id]?.isIgnored == true }
          val peopleGroups =
            detectedGroups
              .filter { personRecords[it.id]?.isIgnored != true }
              .let(FacePersonClusterer::mergeSameNameGroups)

          val suggestions =
            if (unassignedFaceGroups.isNotEmpty() && peopleGroups.isNotEmpty()) {
              FacePersonSuggestionRanker.rankAll(
                candidates = unassignedFaceGroups,
                people = peopleGroups,
                rows = bundle.faceRows,
              )
            } else {
              emptyMap()
            }

          val revisions = bundle.faceRows.associate { it.id to (it.faceIndexedAt ?: 0L) }
          FacePeopleGroupsBundle(
            peopleGroups = peopleGroups,
            unassignedFaceGroups = unassignedFaceGroups,
            facePersonSuggestions = suggestions,
            ignoredPeopleGroups = ignoredPeopleGroups,
            faceCorrectionHistory = bundle.faceCorrectionHistory,
            faceIndexRevisions = revisions,
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FacePeopleGroupsBundle(),
      )

  private val mediaBundleFlow =
    combine(
      galleryRepo.visibleMedia,
      galleryRepo.vaultMedia,
      galleryRepo.trashedMedia,
      galleryRepo.customAlbums,
      galleryRepo.trashedExpiringSoonCount(),
    ) { visible, vault, trashed, albums, expiringSoon ->
      MediaBundle(visible, vault, trashed, albums, expiringSoon, OnThisDay.pick(visible), WeekMemories.pick(visible))
    }

  private val baseUiState: StateFlow<GalleryUiState> =
    combine(prefsRepo.preferences, mediaBundleFlow, syncControlsFlow, searchControlsFlow) {
        prefs,
        media,
        syncCtrl,
        searchCtrl ->
        withContext(Dispatchers.Default) {
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

          val itemsByAlbumId = media.visible.filter { it.albumId != null }.groupBy { it.albumId }
          val userFolders =
            media.customAlbums.map { album ->
              val albumItems = itemsByAlbumId[album.id].orEmpty()
              val cover = albumItems.firstOrNull()
              AlbumFolder(
                id = album.id,
                name = album.name,
                itemCount = albumItems.size,
                coverUriString = cover?.uriString ?: album.coverUriString,
                coverDrawableName =
                  cover?.drawableResName ?: album.coverDrawableName ?: "sample_lake_sunset",
                filterCategory = null,
                customAlbumId = album.id,
              )
            }

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
            peopleGroups = emptyList(),
            unassignedFaceGroups = emptyList(),
            facePersonSuggestions = emptyMap(),
            ignoredPeopleGroups = emptyList(),
            faceCorrectionHistory = emptyList(),
            faceIndexRevisions = emptyMap(),
            folderDestinations = folderDestSet.toList(),
            folderAlbums = listOf(favoritesFolder) + deviceBucketFolders + userFolders,
            vaultMedia = media.vault,
            vaultDecryptedPreviews = emptyMap(),
            trashedMedia = media.trashed,
            trashedExpiringSoonCount = media.expiringSoonCount,
            onThisDay = media.onThisDay,
            weekMemories = media.weekMemories,
            selectedMediaIds = emptySet(),
            isSelectionMode = false,
            showBatchMoveDialog = false,
            showBatchDeleteConfirmDialog = false,
            isPreparingShare = false,
            searchTabMode = searchCtrl.tabMode,
            searchQuery = searchCtrl.query,
            searchOnlyLocation = searchCtrl.onlyLoc,
            searchTypeFilter = searchCtrl.typeFilter,
            searchResults = searchMatches,
            geoItems = geo,
            mapMode = searchCtrl.mapMode,
            selectedMapPinId = geo.firstOrNull()?.id,
            activeLightboxId = null,
            activeEditorId = null,
            activeStoryIndex = null,
            showVaultDialog = false,
            isVaultUnlocked = false,
            showRecycleBinSheet = false,
            depthModelState = DepthModelPackState(),
            isSavingEdit = false,
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue =
          GalleryUiState(mediaAccessLevel = MediaPermissionHelper.checkAccessLevel(appContext)),
      )

  val uiState: StateFlow<GalleryUiState> =
    combine(
        baseUiState,
        facePeopleGroupsFlow,
        selectionControlsFlow,
        modalControlsFlow,
      ) { base, groups, selection, modals ->
        val q = base.searchQuery.trim().lowercase()
        val isPeopleSearch = q == "portraits"
        val selectedPersonGroup =
          if (q.startsWith("person:")) {
            groups.peopleGroups.firstOrNull { q == "person:${it.id.lowercase()}" || base.searchQuery.trim() == "person:${it.id}" }
          } else null
        val namedPersonGroup =
          if (q.isNotEmpty() && !isPeopleSearch && selectedPersonGroup == null) {
            groups.peopleGroups.firstOrNull { it.name?.equals(q, ignoreCase = true) == true }
          } else null
        val peopleSearchActive =
          isPeopleSearch || selectedPersonGroup != null || namedPersonGroup != null

        val searchResults =
          if (!peopleSearchActive) {
            base.searchResults
          } else {
            val targetPhotoIds =
              when {
                isPeopleSearch -> groups.peopleGroups.flatMapTo(HashSet()) { it.photoIds }
                selectedPersonGroup != null -> selectedPersonGroup.photoIds.toHashSet()
                else -> namedPersonGroup!!.photoIds.toHashSet()
              }
            base.visibleMedia.filter { item ->
              val locOk = !base.searchOnlyLocation || item.hasGps
              val typeOk = base.searchTypeFilter == null || item.mediaType == base.searchTypeFilter
              locOk && typeOk && item.id in targetPhotoIds
            }
          }

        val validSelection =
          if (selection.selectedIds.isEmpty()) {
            emptySet()
          } else {
            val visibleIds = base.visibleMedia.mapTo(HashSet(base.visibleMedia.size)) { it.id }
            selection.selectedIds.filterTo(HashSet()) { it in visibleIds }
          }
        val selectedPinId =
          modals.selectedPinId?.takeIf { pinId -> base.geoItems.any { it.id == pinId } }
            ?: base.geoItems.firstOrNull()?.id

        base.copy(
          peopleGroups = groups.peopleGroups,
          unassignedFaceGroups = groups.unassignedFaceGroups,
          facePersonSuggestions = if (isPeopleSearch) groups.facePersonSuggestions else emptyMap(),
          ignoredPeopleGroups = groups.ignoredPeopleGroups,
          faceCorrectionHistory = groups.faceCorrectionHistory,
          faceIndexRevisions = groups.faceIndexRevisions,
          searchResults = searchResults,
          selectedMediaIds = validSelection,
          isSelectionMode =
            selection.isSelectionMode &&
              (validSelection.isNotEmpty() || explicitSelectionMode.value),
          showBatchMoveDialog = selection.showMoveDialog,
          showBatchDeleteConfirmDialog = selection.showDeleteConfirm,
          isPreparingShare = selection.isPreparingShare,
          selectedMapPinId = selectedPinId,
          activeLightboxId = modals.lightboxId,
          activeEditorId = modals.editorId,
          activeStoryIndex = modals.storyIdx,
          showVaultDialog = modals.vaultDialog,
          isVaultUnlocked = modals.vaultUnlocked,
          vaultDecryptedPreviews = modals.vaultPreviews,
          showRecycleBinSheet = modals.recycleSheet,
          depthModelState = modals.depthState,
          isSavingEdit = modals.savingEdit,
          mediaManagementPrompt = modals.mediaManagementPrompt,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue =
          GalleryUiState(mediaAccessLevel = MediaPermissionHelper.checkAccessLevel(appContext)),
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
    val now = System.currentTimeMillis()
    if (silent && (syncJob?.isActive == true || (now - lastSilentSyncAtMs) < 5_000L)) {
      return
    }
    if (syncJob?.isActive == true) return
    syncJob =
      viewModelScope.launch {
        isSyncingMedia.value = true
        try {
          val synced = galleryRepo.syncDeviceMediaStore()
          lastSilentSyncAtMs = System.currentTimeMillis()
          prefsRepo.setLastMediaSyncCount(synced)
          if (!silent) {
            emitMessage(
              if (synced > 0) "Synced $synced items from device MediaStore"
              else "Device library is up to date"
            )
          }
        } finally {
          isSyncingMedia.value = false
        }
      }
  }

  fun dismissPermissionBanner() {
    permissionBannerDismissed.value = true
  }

  fun dismissMediaManagementPrompt() {
    viewModelScope.launch {
      prefsRepo.setMediaManagementPromptSeen()
      showMediaManagementPrompt.value = false
    }
  }

  /** v3.0.0: the first-run intro finished; persist one-way and hide it. */
  fun markIntroDone() {
    viewModelScope.launch { prefsRepo.setIntroDone() }
  }

  /**
   * After a multi-item batch op, offer the "Media management" permission
   * once: with it granted, system-level trash/favorite PendingIntents
   * execute silently instead of the system confirming every single item.
   * Persisted so it is offered at most once per install.
   */
  private fun maybeOfferMediaManagement(count: Int) {
    if (count < 2) return
    if (MediaPermissionHelper.hasMediaManagementPermission(appContext)) return
    if (uiState.value.preferences.mediaManagementPromptSeen) return
    viewModelScope.launch {
      prefsRepo.setMediaManagementPromptSeen()
      showMediaManagementPrompt.value = true
    }
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

  fun setColumns(columns: Int) {
    val clamped = columns.coerceIn(2, 10)
    if (clamped == uiState.value.preferences.columns) return
    viewModelScope.launch { prefsRepo.updateColumns(clamped) }
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
      val selected = uiState.value.visibleMedia.filter { it.id in ids }
      galleryRepo.setTrashedBatch(ids, true)
      val synced =
        if (uiState.value.preferences.systemMediaSync) {
          galleryRepo.syncSystemTrash(selected, trashed = true)
        } else {
          0
        }
      clearSelection()
      emitMessage(
        if (synced > 0) {
          "Moved ${ids.size} ${if (ids.size == 1) "item" else "items"} to Recycle Bin (system sync: $synced)"
        } else {
          "Moved ${ids.size} ${if (ids.size == 1) "item" else "items"} to Recycle Bin"
        }
      )
      maybeOfferMediaManagement(ids.size)
    }
  }

  fun batchToggleFavorite() {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) return
    val visibleMap = uiState.value.visibleMedia.associateBy { it.id }
    val allAlreadyFav = ids.all { visibleMap[it]?.isFavorite == true }
    val targetFav = !allAlreadyFav
    viewModelScope.launch {
      val selected = uiState.value.visibleMedia.filter { it.id in ids }
      galleryRepo.setFavoriteBatch(ids, targetFav)
      val synced =
        if (uiState.value.preferences.systemMediaSync) {
          galleryRepo.syncSystemFavorites(selected, targetFav)
        } else {
          0
        }
      clearSelection()
      emitMessage(
        if (targetFav) {
          if (synced > 0) "Added ${ids.size} items to Favorites (system sync: $synced)"
          else "Added ${ids.size} items to Favorites"
        } else {
          if (synced > 0) "Removed ${ids.size} items from Favorites (system sync: $synced)"
          else "Removed ${ids.size} items from Favorites"
        }
      )
      maybeOfferMediaManagement(ids.size)
    }
  }

  fun batchHideToVault() {
    val ids = selectedMediaIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.hideToVaultEncrypted(ids)
      clearSelection()
      emitMessage("Encrypted & moved $count items to Hidden Vault")
      maybeOfferMediaManagement(count)
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
      emitMessage(
        "Ready to share ${payload.itemCount} ${if (payload.itemCount == 1) "item" else "items"}"
      )
    }
  }

  // Search & Places Map Mode
  fun setSearchTabMode(mode: SearchTabMode) {
    searchTabMode.value = mode
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  /** Persist a local person label. Reusing a name merges clusters in the People list. */
  fun setFacePersonName(group: FacePersonGroup, name: String?) {
    val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
    viewModelScope.launch {
      group.clusterIds.distinct().forEach { clusterId ->
        galleryRepo.saveFacePerson(FacePersonEntity(clusterId = clusterId, name = cleanName))
      }
      emitMessage(if (cleanName == null) "Person label removed" else "Saved person as $cleanName")
    }
  }

  fun setFacePersonIgnored(group: FacePersonGroup, ignored: Boolean) {
    viewModelScope.launch {
      group.clusterIds.distinct().forEach { clusterId ->
        galleryRepo.setFacePersonIgnored(clusterId, ignored)
      }
      emitMessage(if (ignored) "Person hidden from People" else "Person restored to People")
    }
  }

  fun setFacePersonCover(group: FacePersonGroup, photoId: Long, faceIndex: Int) {
    if (group.faces.none { it.photoId == photoId && it.faceIndex == faceIndex }) return
    viewModelScope.launch {
      group.clusterIds.distinct().forEach { clusterId ->
        galleryRepo.setFacePersonCover(clusterId, photoId, faceIndex)
      }
      emitMessage("Person cover updated")
    }
  }

  /** Explicitly assign a detected face to a stable person, as Ente does for cluster membership. */
  fun assignFaceToPerson(photoId: Long, faceIndex: Int, target: FacePersonGroup) {
    val targetClusterId = target.clusterIds.minOrNull() ?: return
    viewModelScope.launch {
      galleryRepo.assignFaceToPerson(photoId, faceIndex, targetClusterId)
      emitMessage("Face assigned to ${target.name?.takeIf { it.isNotBlank() } ?: "person"}")
    }
  }

  /** Remove one incorrectly grouped face and persist the rejection locally. */
  fun excludeFaceFromPerson(photoId: Long, faceIndex: Int) {
    viewModelScope.launch {
      galleryRepo.excludeFace(photoId, faceIndex)
      emitMessage("Face removed from person")
    }
  }

  fun undoLastFaceCorrection() {
    viewModelScope.launch {
      val undone = galleryRepo.undoLastFaceCorrection()
      emitMessage(if (undone) "Last face correction undone" else "No face correction to undo")
    }
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

  fun shareMediaItem(item: GalleryMediaItem, onLaunchIntent: (Intent) -> Unit) {
    if (isPreparingShare.value) return
    viewModelScope.launch {
      isPreparingShare.value = true
      val payload = mediaShareHelper.buildShareIntent(listOf(item))
      isPreparingShare.value = false
      if (payload == null) {
        emitMessage("Unable to prepare media for sharing")
        return@launch
      }
      onLaunchIntent(payload.chooserIntent)
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

  fun unhideBatchFromVault(ids: List<Long>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.unhideBatchFromVaultEncrypted(ids)
      emitMessage("Decrypted & restored $count ${if (count == 1) "item" else "items"}")
    }
  }

  fun deletePermanentlyFromVault(id: Long) {
    viewModelScope.launch {
      galleryRepo.deletePermanentlyFromVault(id)
      emitMessage("Encrypted vault file deleted permanently")
    }
  }

  fun deletePermanentlyBatchFromVault(ids: List<Long>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.deletePermanentlyBatchFromVault(ids)
      emitMessage("Permanently deleted $count encrypted ${if (count == 1) "item" else "items"}")
    }
  }

  fun importUrisToVault(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.importExternalUrisToVault(uris)
      emitMessage(
        if (count > 0) "Encrypted & imported $count ${if (count == 1) "item" else "items"} to Vault"
        else "Could not import selected media"
      )
    }
  }

  fun hideGalleryItemsToVault(ids: List<Long>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val count = galleryRepo.hideToVaultEncrypted(ids)
      emitMessage("Encrypted & moved $count ${if (count == 1) "item" else "items"} to Vault")
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
    // Capture before setTrashed: the Room flow re-emits on restore and the
    // item would already be gone from trashedMedia by the time we look.
    val item = uiState.value.trashedMedia.firstOrNull { it.id == id }
    viewModelScope.launch {
      galleryRepo.setTrashed(id, false)
      // v2.3.0: bring the underlying MediaStore entry out of the OS trash
      // too — the same two-part semantics restore-all already uses. The
      // gap was documented in the v2.2.0 release record.
      item?.let { runCatching { galleryRepo.syncSystemTrash(listOf(it), trashed = false) } }
      emitMessage("Restored item from Recycle Bin")
    }
  }

  /** Restores every trashed item at once (v2.2.0 E3), syncing the OS trash. */
  fun restoreAllFromTrash() {
    val items = uiState.value.trashedMedia
    if (items.isEmpty()) return
    viewModelScope.launch {
      val n = galleryRepo.restoreAllFromTrash(items)
      emitMessage("Restored $n item${if (n == 1) "" else "s"} from Recycle Bin")
    }
  }

  fun emptyRecycleBin() {
    viewModelScope.launch {
      galleryRepo.emptyRecycleBin()
      emitMessage("Recycle Bin emptied")
    }
  }

  /**
   * Manually triggers the 30-day purge pass — the same pass [TrashPurgeWorker]
   * runs daily. Exposed so the Recycle Bin can offer "Purge expired now"
   * without waiting for the next WorkManager window. Reports the count
   * purged back to the user; a 0-count is a no-op, not an error.
   */
  fun purgeExpiredTrashedNow() {
    viewModelScope.launch {
      val cutoff = System.currentTimeMillis() - com.toadgallery.app.data.trash.TrashPurgeWorker.RETENTION_MILLIS
      val n = galleryRepo.purgeTrashedOlderThan(cutoff)
      emitMessage(
        if (n == 0) "No items past the 30-day window yet"
        else "Purged $n item${if (n == 1) "" else "s"} past 30 days",
      )
    }
  }

  /**
   * Applies a trim/mute/speed plan to a video (v2.2.0 E1). The result is a
   * new "(Edited)" video copy; the original is untouched. The sheet closes on
   * attempt; success/failure surface as a snack message either way.
   */
  fun applyVideoEdit(item: GalleryMediaItem, plan: com.toadgallery.app.data.video.VideoEditPlan) {
    if (plan.isNoOp) {
      emitMessage("Nothing to change")
      return
    }
    viewModelScope.launch {
      runCatching { galleryRepo.applyVideoEdit(item, plan) }
        .onSuccess { emitMessage("Saved edited video copy") }
        .onFailure {
          emitMessage("Video edit failed: ${it.message?.take(60) ?: "unknown error"}")
        }
    }
  }

  /** Extracts the video frame at [positionMs] and saves it as a new photo. */
  fun extractVideoFrame(item: GalleryMediaItem, positionMs: Long) {
    viewModelScope.launch {
      runCatching { galleryRepo.extractVideoFrame(item, positionMs) }
        .onSuccess { emitMessage("Frame saved as photo") }
        .onFailure {
          emitMessage("Frame extraction failed: ${it.message?.take(60) ?: "unknown error"}")
        }
    }
  }

  fun setTonePreset(id: Long, preset: TonePreset) {
    viewModelScope.launch {
      galleryRepo.setTonePreset(id, preset)
      emitMessage("Applied ${preset.label} tone preset")
    }
  }

  // Full Photo Editor & Depth Anything V2 (bokeh-depth-dav2) Actions
  fun openEditor(id: Long) {
    activeEditorId.value = id
  }

  fun closeEditor() {
    activeEditorId.value = null
  }

  fun startDepthModelDownload() {
    depthModelManager.startDownload { _, msg -> emitMessage(msg) }
  }

  fun cancelDepthModelDownload() {
    depthModelManager.cancelDownload()
    emitMessage("Cancelled bokeh-depth-dav2 download")
  }

  fun deleteDepthModelPack() {
    viewModelScope.launch {
      val freed = depthModelManager.deleteModelPack()
      emitMessage(String.format("Removed bokeh-depth-dav2 (%.1f MB freed)", freed))
    }
  }

  suspend fun estimateDepthMap(bitmap: Bitmap): Result<DepthMapResult> =
    depthEngine.estimateDepth(bitmap, depthModelManager.modelFile)

  fun saveEditedAsCopy(sourceItem: GalleryMediaItem, recipe: PhotoEditRecipe) {
    if (isSavingEdit.value) return
    viewModelScope.launch {
      isSavingEdit.value = true
      try {
        val fullSource =
          PhotoEditorRenderer.loadSourceBitmap(appContext, sourceItem, maxDimension = 2048)
        if (fullSource == null) {
          emitMessage("Unable to load source photo for export")
          return@launch
        }
        val depthMap =
          if (recipe.bokeh.enabled && depthModelManager.state.value.isInstalled) {
            val geom =
              withContext(Dispatchers.Default) {
                PhotoEditorRenderer.applyGeometryOnly(fullSource, recipe.crop)
              }
            val res = depthEngine.estimateDepth(geom, depthModelManager.modelFile).getOrNull()
            if (geom !== fullSource) geom.recycle()
            res
          } else {
            null
          }
        val rendered =
          PhotoEditorRenderer.renderFullPreview(
            source = fullSource,
            recipe = recipe,
            depthMap = depthMap,
            includeCrop = true,
          )
        val newId = galleryRepo.saveEditedCopy(sourceItem, rendered, recipe)
        if (rendered !== fullSource) rendered.recycle()
        fullSource.recycle()
        activeEditorId.value = null
        activeLightboxId.value = newId
        emitMessage("Saved edited copy to gallery")
      } catch (t: Throwable) {
        emitMessage("Save copy failed: ${t.message?.take(48) ?: "unknown error"}")
      } finally {
        isSavingEdit.value = false
      }
    }
  }

  fun applyEditNonDestructively(sourceItem: GalleryMediaItem, recipe: PhotoEditRecipe) {
    if (isSavingEdit.value) return
    viewModelScope.launch {
      isSavingEdit.value = true
      try {
        if (recipe.isIdentity) {
          galleryRepo.revertNonDestructiveEdit(sourceItem)
          activeEditorId.value = null
          emitMessage("Reverted photo to original")
          return@launch
        }
        val fullSource =
          PhotoEditorRenderer.loadSourceBitmap(appContext, sourceItem, maxDimension = 2048)
        if (fullSource == null) {
          emitMessage("Unable to render non-destructive edit")
          return@launch
        }
        val depthMap =
          if (recipe.bokeh.enabled && depthModelManager.state.value.isInstalled) {
            val geom =
              withContext(Dispatchers.Default) {
                PhotoEditorRenderer.applyGeometryOnly(fullSource, recipe.crop)
              }
            val res = depthEngine.estimateDepth(geom, depthModelManager.modelFile).getOrNull()
            if (geom !== fullSource) geom.recycle()
            res
          } else {
            null
          }
        val rendered =
          PhotoEditorRenderer.renderFullPreview(
            source = fullSource,
            recipe = recipe,
            depthMap = depthMap,
            includeCrop = true,
          )
        galleryRepo.applyNonDestructiveEdit(sourceItem, rendered, recipe)
        if (rendered !== fullSource) rendered.recycle()
        fullSource.recycle()
        activeEditorId.value = null
        emitMessage("Applied non-destructive edit (reversible)")
      } catch (t: Throwable) {
        emitMessage("Apply failed: ${t.message?.take(48) ?: "unknown error"}")
      } finally {
        isSavingEdit.value = false
      }
    }
  }

  fun revertNonDestructiveEdit(sourceItem: GalleryMediaItem) {
    viewModelScope.launch {
      galleryRepo.revertNonDestructiveEdit(sourceItem)
      emitMessage("Reverted \"${sourceItem.title}\" to original")
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

  fun previousStorySlide() {
    val current = activeStoryIndex.value ?: return
    activeStoryIndex.value =
      if (current > 0) current - 1 else SampleMediaCatalog.stories.lastIndex
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
      galleryRepo.vaultCryptoManager.clearSessionCache()
    }
  }

  fun lockVaultNow() {
    isVaultUnlocked.value = false
    vaultDecryptedPreviews.value = emptyMap()
    galleryRepo.vaultCryptoManager.clearSessionCache()
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
    val editorId: Long?,
    val storyIdx: Int?,
    val vaultDialog: Boolean,
    val vaultUnlocked: Boolean,
    val vaultPreviews: Map<Long, ByteArray>,
    val recycleSheet: Boolean,
    val depthState: DepthModelPackState,
    val savingEdit: Boolean,
    val mediaManagementPrompt: Boolean,
  )

  private data class FacePeopleBundle(
    val faceRows: List<com.toadgallery.app.data.local.MediaItemEntity>,
    val facePeople: List<FacePersonEntity>,
    val faceExclusions: List<com.toadgallery.app.data.local.FaceExclusionEntity>,
    val faceAssignments: List<FaceAssignmentEntity>,
    val faceCorrectionHistory: List<FaceCorrectionHistoryEntity>,
  )

  private data class FacePeopleGroupsBundle(
    val peopleGroups: List<FacePersonGroup> = emptyList(),
    val unassignedFaceGroups: List<FacePersonGroup> = emptyList(),
    val facePersonSuggestions: Map<String, List<FacePersonSuggestion>> = emptyMap(),
    val ignoredPeopleGroups: List<FacePersonGroup> = emptyList(),
    val faceCorrectionHistory: List<FaceCorrectionHistoryEntity> = emptyList(),
    val faceIndexRevisions: Map<Long, Long> = emptyMap(),
  )

  private data class MediaBundle(
    val visible: List<GalleryMediaItem>,
    val vault: List<GalleryMediaItem>,
    val trashed: List<GalleryMediaItem>,
    val customAlbums: List<AlbumEntity>,
    val expiringSoonCount: Int = 0,
    val onThisDay: List<GalleryMediaItem> = emptyList(),
    val weekMemories: List<GalleryMediaItem> = emptyList(),
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
            container.depthModelManager,
            container.depthAnythingV2Engine,
          ) as T
      }
  }
}
