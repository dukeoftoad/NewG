package com.toadgallery.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toadgallery.app.data.media.MediaAccessLevel
import com.toadgallery.app.data.media.MediaPermissionHelper
import com.toadgallery.app.data.model.SettingsSubPage
import com.toadgallery.app.data.model.TopLevelTab
import android.app.Activity
import android.content.Intent
import com.toadgallery.app.data.security.BiometricAuthHelper
import com.toadgallery.app.ui.overlays.BatchDeleteConfirmDialog
import com.toadgallery.app.ui.overlays.BatchMoveDestinationDialog
import com.toadgallery.app.ui.overlays.HiddenVaultScreen
import com.toadgallery.app.ui.overlays.LightboxOverlay
import com.toadgallery.app.ui.overlays.PhotoEditorScreen
import com.toadgallery.app.ui.overlays.RecycleBinDialog
import com.toadgallery.app.ui.overlays.StoryViewerOverlay
import com.toadgallery.app.ui.screens.AlbumsScreen
import com.toadgallery.app.ui.screens.PhotosScreen
import com.toadgallery.app.ui.screens.SearchScreen
import com.toadgallery.app.ui.screens.SettingsScreen
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.update.UpdateViewModel
import com.toadgallery.app.update.StartupUpdateCheck
import com.toadgallery.app.update.UpdateBanner
import com.toadgallery.app.ui.theme.ToadGalleryTheme
import com.toadgallery.app.ui.viewmodel.GalleryViewModel
import com.toadgallery.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.merge

private data class NavTabSpec(
  val tab: TopLevelTab,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

private val TOP_LEVEL_TABS =
  listOf(
    NavTabSpec(TopLevelTab.PHOTOS, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    NavTabSpec(TopLevelTab.ALBUMS, Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial),
    NavTabSpec(TopLevelTab.SEARCH, Icons.Filled.Search, Icons.Outlined.Search),
    NavTabSpec(TopLevelTab.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings),
  )

@Composable
fun ToadGalleryApp(
  galleryViewModel: GalleryViewModel,
  settingsViewModel: SettingsViewModel,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
  val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

  // Self-update flow (ported from toadFILES): silent once-per-process check on
  // app open + the dismissible banner deep-linking into Settings → About.
  val updateViewModel: UpdateViewModel =
    viewModel(factory = UpdateViewModel.factory(context.applicationContext))
  val startupUpdate = remember { StartupUpdateCheck.instance }
  val updateBannerRelease by startupUpdate.banner.collectAsStateWithLifecycle()
  val updateBannerDismissed by startupUpdate.bannerDismissed.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { startupUpdate.checkOnce(this) }
  val prefs = galleryState.preferences

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(galleryViewModel, settingsViewModel) {
    merge(galleryViewModel.messages, settingsViewModel.messages).collect { msg ->
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(msg)
    }
  }

  val mediaPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions(),
      onResult = { galleryViewModel.refreshMediaPermissionsAndSync(silent = false) },
    )

  fun triggerSyncOrPermissions() {
    val level = MediaPermissionHelper.checkAccessLevel(context)
    when (level) {
      MediaAccessLevel.FULL -> galleryViewModel.refreshMediaPermissionsAndSync(silent = false)
      // Android 14+ partial grants ("Selected photos only") must not be
      // re-prompted on every launch — the system would only re-offer the
      // full/partial chooser and nag the user. The gallery banner handles
      // the upgrade path instead.
      MediaAccessLevel.PARTIAL -> galleryViewModel.refreshMediaPermissionsAndSync(silent = true)
      else -> mediaPermissionLauncher.launch(MediaPermissionHelper.requiredMediaPermissions())
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        galleryViewModel.refreshMediaPermissionsAndSync(silent = true)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  ToadGalleryTheme(
    palette = prefs.palette,
    appearanceMode = prefs.appearanceMode,
    useDynamicColors = prefs.useDynamicColors,
  ) {
    val extended = LocalToadColors.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateToTab(tab: TopLevelTab) {
      if (
        tab == TopLevelTab.SETTINGS &&
          currentDestination?.hierarchy?.any { it.route == TopLevelTab.SETTINGS.route } == true &&
          settingsState.currentSubPage != SettingsSubPage.HUB
      ) {
        settingsViewModel.backToHub()
        return
      }
      if (tab == TopLevelTab.SETTINGS) {
        settingsViewModel.backToHub()
      }
      navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavigationRail = maxWidth >= 600.dp

      Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
          SnackbarHost(
            hostState = snackbarHostState,
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (useNavigationRail) 12.dp else 84.dp),
          )
        },
        bottomBar = {
          if (!useNavigationRail && galleryState.selectedMediaIds.isEmpty()) {
            Column {
              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
              NavigationBar(
                containerColor = extended.glassSurface,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars,
              ) {
                TOP_LEVEL_TABS.forEach { spec ->
                  val selected =
                    currentDestination?.hierarchy?.any { it.route == spec.tab.route } == true
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateToTab(spec.tab) },
                    modifier = Modifier.testTag("nav_tab_${spec.tab.route}"),
                    icon = {
                      Icon(
                        imageVector = if (selected) spec.selectedIcon else spec.unselectedIcon,
                        contentDescription = spec.tab.label,
                      )
                    },
                    label = {
                      Text(
                        text = spec.tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                      )
                    },
                    colors =
                      NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = extended.textMuted,
                        unselectedTextColor = extended.textMuted,
                      ),
                  )
                }
              }
            }
          }
        },
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (useNavigationRail) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxHeight(),
            ) {
              Spacer(modifier = Modifier.weight(0.25f))
              TOP_LEVEL_TABS.forEach { spec ->
                val selected =
                  currentDestination?.hierarchy?.any { it.route == spec.tab.route } == true
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateToTab(spec.tab) },
                  modifier = Modifier.testTag("rail_tab_${spec.tab.route}"),
                  icon = {
                    Icon(
                      imageVector = if (selected) spec.selectedIcon else spec.unselectedIcon,
                      contentDescription = spec.tab.label,
                    )
                  },
                  label = { Text(spec.tab.label, style = MaterialTheme.typography.labelSmall) },
                  colors =
                    NavigationRailItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      selectedTextColor = MaterialTheme.colorScheme.onBackground,
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      unselectedIconColor = extended.textMuted,
                      unselectedTextColor = extended.textMuted,
                    ),
                )
              }
              Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(thickness = 1.dp, color = extended.borderSubtle)
          }

          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize()) {
              if (!updateBannerDismissed) {
                UpdateBanner(
                  release = updateBannerRelease,
                  onOpenUpdateFlow = {
                    navigateToTab(TopLevelTab.SETTINGS)
                    settingsViewModel.openSubPage(SettingsSubPage.ABOUT)
                  },
                  onDismiss = startupUpdate::dismissBanner,
                )
              }
              NavHost(
                navController = navController,
                startDestination = TopLevelTab.PHOTOS.route,
                modifier = Modifier.fillMaxSize().weight(1f),
              ) {
              composable(TopLevelTab.PHOTOS.route) {
                PhotosScreen(
                  uiState = galleryState,
                  isWideLayout = useNavigationRail,
                  onSelectCategory = galleryViewModel::setPhotoCategory,
                  onClearFolderFilter = galleryViewModel::clearFolderFilter,
                  onToggleCleanFeed = galleryViewModel::toggleHideScreenshots,
                  onToggleQuickLayout = galleryViewModel::toggleQuickLayout,
                  onSetColumns = galleryViewModel::setColumns,
                  onNavigateToTimelineSettings = {
                    navigateToTab(TopLevelTab.SETTINGS)
                    settingsViewModel.openSubPage(SettingsSubPage.LAYOUT)
                  },
                  onOpenVault = galleryViewModel::openVaultDialog,
                  onSyncOrRequestMediaPermissions = ::triggerSyncOrPermissions,
                  onDismissPermissionBanner = galleryViewModel::dismissPermissionBanner,
                  onDismissMediaManagementPrompt = galleryViewModel::dismissMediaManagementPrompt,
                  onResetFilters = galleryViewModel::resetPhotoFilters,
                  onOpenStory = galleryViewModel::openStory,
                  onOpenLightbox = galleryViewModel::openLightbox,
                  onToggleFavorite = galleryViewModel::toggleFavorite,
                  onEnterSelectionMode = galleryViewModel::enterSelectionMode,
                  onToggleMediaSelection = galleryViewModel::toggleMediaSelection,
                  onToggleSectionSelection = galleryViewModel::toggleSectionSelection,
                  onToggleSelectAllVisible = galleryViewModel::toggleSelectAllVisible,
                  onClearSelection = galleryViewModel::clearSelection,
                  onBatchShare = {
                    galleryViewModel.batchShareSelected { chooserIntent ->
                      if (context !is Activity) {
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      }
                      runCatching { context.startActivity(chooserIntent) }
                        .onFailure {
                          galleryViewModel.showMessage("No compatible share app available")
                        }
                    }
                  },
                  onBatchMove = galleryViewModel::openBatchMoveDialog,
                  onBatchFavorite = galleryViewModel::batchToggleFavorite,
                  onBatchHideToVault = galleryViewModel::batchHideToVault,
                  onBatchDelete = galleryViewModel::openBatchDeleteConfirmDialog,
                )
              }
              composable(TopLevelTab.ALBUMS.route) {
                AlbumsScreen(
                  uiState = galleryState,
                  isWideLayout = useNavigationRail,
                  onOpenSmartCollection = { collectionName ->
                    galleryViewModel.openSmartCollection(collectionName)
                    navigateToTab(TopLevelTab.SEARCH)
                  },
                  onOpenFolderFilter = { filter ->
                    galleryViewModel.openFolderFilter(filter)
                    navigateToTab(TopLevelTab.PHOTOS)
                  },
                  onCreateAlbum = galleryViewModel::createAlbum,
                  onOpenVault = galleryViewModel::openVaultDialog,
                  onOpenRecycleBin = galleryViewModel::openRecycleBin,
                )
              }
              composable(TopLevelTab.SEARCH.route) {
                SearchScreen(
                  uiState = galleryState,
                  isWideLayout = useNavigationRail,
                  onSelectSearchMode = galleryViewModel::setSearchTabMode,
                  onQueryChange = galleryViewModel::setSearchQuery,
                  onToggleLocationFilter = galleryViewModel::toggleSearchOnlyLocation,
                  onSelectTypeFilter = galleryViewModel::setSearchTypeFilter,
                  onSelectMapMode = galleryViewModel::setMapMode,
                  onSelectMapPin = galleryViewModel::selectMapPin,
                  onNotifyMissingGps = {
                    val missingGps = galleryState.visibleMedia.count { !it.hasGps }
                    galleryViewModel.showMessage(
                      "$missingGps items have no GPS coordinates (screenshots & downloads)"
                    )
                  },
                  onOpenLightbox = galleryViewModel::openLightbox,
                  onSetColumns = galleryViewModel::setColumns,
                )
              }
              composable(TopLevelTab.SETTINGS.route) {
                SettingsScreen(
                  uiState = settingsState,
                  viewModel = settingsViewModel,
                  galleryState = galleryState,
                  galleryViewModel = galleryViewModel,
                  mediaAccessLevel = galleryState.mediaAccessLevel,
                  onSyncMediaPermissions = ::triggerSyncOrPermissions,
                  onOpenVaultModal = galleryViewModel::openVaultDialog,
                )
              }
            }
            }
          }
        }
      }

      // Full-Screen Pinch-Zoom Lightbox Overlay
      val activeLightboxId = galleryState.activeLightboxId
      val lightboxList =
        remember(activeLightboxId, galleryState.filteredTimelineMedia, galleryState.visibleMedia) {
          if (activeLightboxId == null) emptyList()
          else {
            galleryState.filteredTimelineMedia.takeIf { list ->
              list.any { it.id == activeLightboxId }
            } ?: galleryState.visibleMedia
          }
        }
      val activeLightboxIdx =
        remember(activeLightboxId, lightboxList) {
          if (activeLightboxId == null) -1
          else lightboxList.indexOfFirst { it.id == activeLightboxId }
        }
      val activeLightboxItem = lightboxList.getOrNull(activeLightboxIdx)
      if (activeLightboxItem != null) {
        LightboxOverlay(
          item = activeLightboxItem,
          hasPrevious = activeLightboxIdx > 0,
          hasNext = activeLightboxIdx in 0 until lightboxList.lastIndex,
          showLocationExif = prefs.showLocationExif,
          onPrevious = { galleryViewModel.stepLightbox(-1) },
          onNext = { galleryViewModel.stepLightbox(1) },
          onClose = galleryViewModel::closeLightbox,
          onToggleFavorite = { galleryViewModel.toggleFavorite(activeLightboxItem) },
          onShare = {
            galleryViewModel.shareMediaItem(activeLightboxItem) { chooserIntent ->
              if (context !is Activity) {
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              runCatching { context.startActivity(chooserIntent) }
                .onFailure {
                  galleryViewModel.showMessage("No compatible share app available")
                }
            }
          },
          onHideToVault = { galleryViewModel.hideToVault(activeLightboxItem.id) },
          onMoveToTrash = { galleryViewModel.moveToTrash(activeLightboxItem.id) },
          onSelectTonePreset = { preset ->
            galleryViewModel.setTonePreset(activeLightboxItem.id, preset)
          },
          onOpenEditor = { galleryViewModel.openEditor(activeLightboxItem.id) },
          onRevertToOriginal = { galleryViewModel.revertNonDestructiveEdit(activeLightboxItem) },
        )
      }

      // Full-Screen Photo Editor Studio Overlay
      val activeEditorId = galleryState.activeEditorId
      val activeEditorItem =
        remember(activeEditorId, galleryState.visibleMedia) {
          activeEditorId?.let { editId ->
            galleryState.visibleMedia.firstOrNull { it.id == editId }
          }
        }
      if (activeEditorItem != null) {
        PhotoEditorScreen(
          item = activeEditorItem,
          depthModelState = galleryState.depthModelState,
          isSaving = galleryState.isSavingEdit,
          onEstimateDepth = galleryViewModel::estimateDepthMap,
          onStartDepthDownload = galleryViewModel::startDepthModelDownload,
          onCancelDepthDownload = galleryViewModel::cancelDepthModelDownload,
          onDeleteDepthModel = galleryViewModel::deleteDepthModelPack,
          onSaveAsCopy = { recipe -> galleryViewModel.saveEditedAsCopy(activeEditorItem, recipe) },
          onApplyNonDestructive = { recipe ->
            galleryViewModel.applyEditNonDestructively(activeEditorItem, recipe)
          },
          onRevertToOriginal = { galleryViewModel.revertNonDestructiveEdit(activeEditorItem) },
          onClose = galleryViewModel::closeEditor,
        )
      }

      // Full-Screen Story Viewer Overlay
      val activeStoryIdx = galleryState.activeStoryIndex
      if (activeStoryIdx != null) {
        StoryViewerOverlay(
          stories = galleryState.stories,
          activeIndex = activeStoryIdx,
          onAdvance = galleryViewModel::nextStorySlide,
          onClose = galleryViewModel::closeStory,
        )
      }

      // Full-Page Encrypted Hidden Vault Screen
      if (galleryState.showVaultDialog) {
        HiddenVaultScreen(
          vaultItems = galleryState.vaultMedia,
          visibleGalleryMedia = galleryState.visibleMedia,
          decryptedPreviews = galleryState.vaultDecryptedPreviews,
          columns = prefs.columns,
          isUnlocked = galleryState.isVaultUnlocked,
          biometricsEnabled = prefs.vaultBiometrics,
          onColumnsChange = galleryViewModel::setColumns,
          onVerifyPinAsync = galleryViewModel::verifyVaultPinAsync,
          onBiometricUnlockClick = {
            BiometricAuthHelper.authenticate(
              context = context,
              onSuccess = galleryViewModel::unlockVaultBiometric,
              onFallbackPin = { reason -> galleryViewModel.showMessage(reason) },
            )
          },
          onUnhideItem = galleryViewModel::unhideFromVault,
          onUnhideBatch = galleryViewModel::unhideBatchFromVault,
          onDeletePermanentItem = galleryViewModel::deletePermanentlyFromVault,
          onDeletePermanentBatch = galleryViewModel::deletePermanentlyBatchFromVault,
          onImportUris = galleryViewModel::importUrisToVault,
          onImportGalleryItems = galleryViewModel::hideGalleryItemsToVault,
          onLockNow = galleryViewModel::lockVaultNow,
          onClose = galleryViewModel::closeVaultDialog,
        )
      }

      // Batch Move Destination Modal (Albums, Folders, Hidden Vault)
      if (galleryState.showBatchMoveDialog) {
        BatchMoveDestinationDialog(
          customAlbums = galleryState.customAlbums,
          folderDestinations = galleryState.folderDestinations,
          selectedCount = galleryState.selectedMediaIds.size,
          onMoveToAlbum = galleryViewModel::batchMoveToAlbum,
          onCreateAlbumAndMove = galleryViewModel::batchCreateAlbumAndMove,
          onMoveToFolder = galleryViewModel::batchMoveToFolder,
          onMoveToVault = galleryViewModel::batchHideToVault,
          onDismiss = galleryViewModel::closeBatchMoveDialog,
        )
      }

      // Batch Delete Confirmation Modal
      if (galleryState.showBatchDeleteConfirmDialog) {
        BatchDeleteConfirmDialog(
          selectedCount = galleryState.selectedMediaIds.size,
          onConfirmDelete = galleryViewModel::confirmBatchDeleteToTrash,
          onDismiss = galleryViewModel::closeBatchDeleteConfirmDialog,
        )
      }

      // Recycle Bin Modal
      if (galleryState.showRecycleBinSheet) {
        RecycleBinDialog(
          trashedItems = galleryState.trashedMedia,
          onRestoreItem = galleryViewModel::restoreFromTrash,
          onEmptyBin = galleryViewModel::emptyRecycleBin,
          onClose = galleryViewModel::closeRecycleBin,
        )
      }
    }
  }
}
