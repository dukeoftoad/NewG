package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.data.local.SampleMediaCatalog
import com.example.data.media.MediaAccessLevel
import com.example.data.model.GalleryMediaItem
import com.example.data.model.GroupByMode
import com.example.data.model.LayoutMode
import com.example.data.model.MediaCategory
import com.example.data.model.StorySlide
import com.example.ui.components.MediaThumbnailCard
import com.example.ui.components.ToadBrandIcon
import com.example.ui.components.pinchToZoomGridColumns
import com.example.ui.theme.LocalToadColors
import com.example.ui.viewmodel.GalleryUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PreparedTimelineSection(
  val title: String,
  val items: List<GalleryMediaItem>,
  val itemIds: List<Long>,
  val chunks: List<List<GalleryMediaItem>>,
  val startLazyIndex: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
  uiState: GalleryUiState,
  isWideLayout: Boolean,
  onSelectCategory: (MediaCategory) -> Unit,
  onClearFolderFilter: () -> Unit,
  onToggleCleanFeed: () -> Unit,
  onToggleQuickLayout: () -> Unit,
  onSetColumns: (Int) -> Unit,
  onNavigateToTimelineSettings: () -> Unit,
  onOpenVault: () -> Unit,
  onSyncOrRequestMediaPermissions: () -> Unit,
  onDismissPermissionBanner: () -> Unit,
  onResetFilters: () -> Unit,
  onOpenStory: (Int) -> Unit,
  onOpenLightbox: (Long) -> Unit,
  onEnterSelectionMode: (Long?) -> Unit,
  onToggleMediaSelection: (Long) -> Unit,
  onToggleSectionSelection: (List<Long>) -> Unit,
  onToggleSelectAllVisible: () -> Unit,
  onClearSelection: () -> Unit,
  onBatchShare: () -> Unit,
  onBatchMove: () -> Unit,
  onBatchFavorite: () -> Unit,
  onBatchHideToVault: () -> Unit,
  onBatchDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(enabled = uiState.isSelectionMode) { onClearSelection() }

  val extended = LocalToadColors.current
  val prefs = uiState.preferences
  val effectiveColumns =
    if (isWideLayout) (prefs.columns + 1).coerceIn(2, 10) else prefs.columns.coerceIn(2, 10)
  val compactThumbnails = effectiveColumns >= 6
  val coroutineScope = rememberCoroutineScope()
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current

  val showPermissionBanner =
    uiState.mediaAccessLevel != MediaAccessLevel.FULL && !uiState.permissionBannerDismissed
  val showStoriesShelf = prefs.showStories && uiState.activeFolderFilter == null
  val leadingItemsCount = (if (showPermissionBanner) 1 else 0) + (if (showStoriesShelf) 1 else 0)

  // Memoize grouped sections AND chunked rows/blocks so LazyColumn never reallocates lists on scroll
  val preparedSections =
    remember(
      uiState.filteredTimelineMedia,
      prefs.groupBy,
      prefs.layoutMode,
      effectiveColumns,
      leadingItemsCount,
    ) {
      val rawSections =
        if (prefs.groupBy == GroupByMode.NONE) {
          if (uiState.filteredTimelineMedia.isEmpty()) emptyList()
          else listOf("All Media" to uiState.filteredTimelineMedia)
        } else {
          uiState.filteredTimelineMedia
            .groupBy { item ->
              when (prefs.groupBy) {
                GroupByMode.DAY -> item.dateLabel
                GroupByMode.WEEK -> item.weekGroup
                GroupByMode.MONTH -> item.monthGroup
                GroupByMode.NONE -> "All Media"
              }
            }
            .entries
            .map { it.key to it.value }
        }

      var cursor = leadingItemsCount
      val chunkSize =
        if (prefs.layoutMode == LayoutMode.GRID) effectiveColumns
        else (effectiveColumns * 3).coerceAtLeast(6)

      rawSections.map { (title, sectionItems) ->
        val start = cursor
        if (prefs.groupBy != GroupByMode.NONE) cursor += 1
        val chunks = sectionItems.chunked(chunkSize)
        cursor += chunks.size.coerceAtLeast(1)
        PreparedTimelineSection(
          title = title,
          items = sectionItems,
          itemIds = sectionItems.map { it.id },
          chunks = chunks,
          startLazyIndex = start,
        )
      }
    }

  val listState = rememberLazyListState()
  var isDraggingScrubber by remember { mutableStateOf(false) }
  var scrubFraction by remember { mutableFloatStateOf(0f) }
  val isScrolling by remember { derivedStateOf { listState.isScrollInProgress || isDraggingScrubber } }

  val activeScrubberLabel by
    remember(preparedSections) {
      derivedStateOf {
        if (preparedSections.isEmpty()) return@derivedStateOf ""
        val idx = listState.firstVisibleItemIndex
        var matched = 0
        for (i in preparedSections.indices) {
          if (preparedSections[i].startLazyIndex <= idx) matched = i else break
        }
        preparedSections.getOrNull(matched)?.title.orEmpty()
      }
    }

  val scrollProgressFraction by
    remember {
      derivedStateOf {
        if (isDraggingScrubber) {
          scrubFraction
        } else {
          val total = listState.layoutInfo.totalItemsCount
          if (total <= 1) 0f
          else (listState.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
      }
    }

  val maxCollapsePx = remember(density) { with(density) { 46.dp.toPx() } }
  var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
  var moreMenuExpanded by remember { mutableStateOf(false) }
  var isPinching by remember { mutableStateOf(false) }
  var showDensityPill by remember { mutableStateOf(false) }
  var lastObservedCols by remember { mutableIntStateOf(effectiveColumns) }

  LaunchedEffect(effectiveColumns, isPinching) {
    if (isPinching || effectiveColumns != lastObservedCols) {
      lastObservedCols = effectiveColumns
      showDensityPill = true
      if (!isPinching) {
        delay(1200)
        showDensityPill = false
      }
    } else {
      showDensityPill = false
    }
  }

  val nestedScrollConnection =
    remember(maxCollapsePx, uiState.isSelectionMode) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (uiState.isSelectionMode) return Offset.Zero
          val deltaY = available.y
          if (deltaY < 0f && collapseOffsetPx < maxCollapsePx) {
            val prev = collapseOffsetPx
            val next = (prev - deltaY).coerceIn(0f, maxCollapsePx)
            collapseOffsetPx = next
            return Offset(0f, -(next - prev))
          }
          if (deltaY > 0f && collapseOffsetPx > 0f) {
            val prev = collapseOffsetPx
            val next = (prev - deltaY).coerceIn(0f, maxCollapsePx)
            collapseOffsetPx = next
            return Offset(0f, -(next - prev))
          }
          return Offset.Zero
        }
      }
    }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .nestedScroll(nestedScrollConnection)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Unified Translucent Scroll-Collapsing Header (switches to Multi-Select bar when active)
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(extended.glassSurface)
            .padding(top = statusBarTop + 4.dp, bottom = 6.dp)
      ) {
        if (uiState.isSelectionMode) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              IconButton(
                onClick = onClearSelection,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("selection_clear_button")
                    .clip(CircleShape)
                    .background(extended.surfaceHigh),
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Exit multi-select mode",
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              }
              Column {
                Text(
                  text = "${uiState.selectedMediaIds.size} selected",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = "Tap photos to toggle • Choose action below",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
              }
            }

            val allVisibleSelected =
              remember(uiState.filteredTimelineMedia, uiState.selectedMediaIds) {
                uiState.filteredTimelineMedia.isNotEmpty() &&
                  uiState.filteredTimelineMedia.all { it.id in uiState.selectedMediaIds }
              }
            val hasVisibleItems = uiState.filteredTimelineMedia.isNotEmpty()
            Surface(
              onClick = onToggleSelectAllVisible,
              enabled = hasVisibleItems,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("selection_select_all_button"),
              shape = CircleShape,
              color =
                if (hasVisibleItems) MaterialTheme.colorScheme.primaryContainer
                else extended.surfaceHigh,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.SelectAll,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = if (allVisibleSelected) "Deselect All" else "Select All",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Unified Header Box: on scroll collapse, toadGALLERY fades out and chips merge inline
        // between the pinned ToadBrandIcon (left) and More menu (right).
        if (!uiState.isSelectionMode) {
          val collapseProgress = (collapseOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
          val titleAlpha = (1f - collapseProgress * 1.3f).coerceIn(0f, 1f)
          val chipsTopPadding = lerp(46.dp, 2.dp, collapseProgress)
          val chipsStartInset = lerp(0.dp, 46.dp, collapseProgress)
          val chipsEndInset = lerp(0.dp, 52.dp, collapseProgress)

          Box(modifier = Modifier.fillMaxWidth()) {
            // Filter Chips Row (animates upward & insets between Toad icon + More menu on collapse)
            LazyRow(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(top = chipsTopPadding, start = chipsStartInset, end = chipsEndInset),
              contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(MediaCategory.entries) { category ->
                val selected = uiState.activeCategory == category
                Surface(
                  onClick = { onSelectCategory(category) },
                  modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}"),
                  shape = CircleShape,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                  border =
                    BorderStroke(
                      1.dp,
                      if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
                    ),
                ) {
                  Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (selected) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                  )
                }
              }
            }

            // Pinned Left: Toad Brand Icon + Slowly Fading toadGALLERY Title
            Row(
              modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              ToadBrandIcon(
                modifier =
                  Modifier.clickable {
                    coroutineScope.launch {
                      collapseOffsetPx = 0f
                      listState.animateScrollToItem(0)
                    }
                  }
              )
              if (titleAlpha > 0.01f) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier =
                    Modifier.graphicsLayer {
                      alpha = titleAlpha
                      translationY = -collapseProgress * 10.dp.toPx()
                    },
                ) {
                  Text(
                    text = "toadGALLERY",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Surface(
                    shape = CircleShape,
                    color = extended.surfaceHigh,
                  ) {
                    Text(
                      text = "${uiState.filteredTimelineMedia.size}",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                  }
                }
              }
            }

            // Pinned Right: Fixed More (⋮) Menu Anchor
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) {
              IconButton(
                onClick = { moreMenuExpanded = true },
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("photos_more_menu_button"),
              ) {
                Box(
                  modifier =
                    Modifier.size(34.dp)
                      .clip(CircleShape)
                      .background(extended.surfaceHigh),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More gallery options",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp),
                  )
                }
              }

              DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                modifier = Modifier.testTag("photos_more_menu"),
              ) {
                DropdownMenuItem(
                  text = { Text("Multi-Select") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  },
                  onClick = {
                    moreMenuExpanded = false
                    onEnterSelectionMode(null)
                  },
                  modifier = Modifier.testTag("menu_multi_select"),
                )
                DropdownMenuItem(
                  text = { Text("Refresh Library") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.Refresh,
                      contentDescription = null,
                    )
                  },
                  onClick = {
                    moreMenuExpanded = false
                    onSyncOrRequestMediaPermissions()
                  },
                  modifier = Modifier.testTag("menu_refresh_library"),
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      if (prefs.layoutMode == LayoutMode.COLLAGE) "Switch to Grid View"
                      else "Switch to Collage View"
                    )
                  },
                  leadingIcon = {
                    Icon(
                      imageVector =
                        if (prefs.layoutMode == LayoutMode.COLLAGE) Icons.Filled.GridView
                        else Icons.Filled.ViewAgenda,
                      contentDescription = null,
                    )
                  },
                  onClick = {
                    moreMenuExpanded = false
                    onToggleQuickLayout()
                  },
                  modifier = Modifier.testTag("menu_toggle_layout"),
                )
                DropdownMenuItem(
                  text = { Text("Clean Feed (Hide Screenshots)") },
                  leadingIcon = {
                    Icon(
                      imageVector =
                        if (prefs.hideScreenshots) Icons.Outlined.HideImage
                        else Icons.Outlined.Smartphone,
                      contentDescription = null,
                    )
                  },
                  trailingIcon =
                    if (prefs.hideScreenshots) {
                      {
                        Icon(
                          imageVector = Icons.Filled.Check,
                          contentDescription = "Active",
                          tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                    } else null,
                  onClick = {
                    moreMenuExpanded = false
                    onToggleCleanFeed()
                  },
                  modifier = Modifier.testTag("menu_clean_feed"),
                )
                HorizontalDivider(thickness = 0.5.dp, color = extended.borderSubtle)
                DropdownMenuItem(
                  text = { Text("Timeline Layout (${prefs.columns}/row)") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Tune,
                      contentDescription = null,
                    )
                  },
                  onClick = {
                    moreMenuExpanded = false
                    onNavigateToTimelineSettings()
                  },
                  modifier = Modifier.testTag("menu_timeline_settings"),
                )
                DropdownMenuItem(
                  text = { Text("Hidden Vault") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  },
                  onClick = {
                    moreMenuExpanded = false
                    onOpenVault()
                  },
                  modifier = Modifier.testTag("menu_hidden_vault"),
                )
              }
            }
          }
        } else {
          // Keep Filter Chips accessible in Selection Mode too
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(MediaCategory.entries) { category ->
              val selected = uiState.activeCategory == category
              Surface(
                onClick = { onSelectCategory(category) },
                modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}"),
                shape = CircleShape,
                color =
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surface,
                border =
                  BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
                  ),
              ) {
                Text(
                  text = category.label,
                  style = MaterialTheme.typography.labelMedium,
                  color =
                    if (selected) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                  modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                )
              }
            }
          }
        }

        // Active folder filter pill if drilled down from AlbumsScreen
        val activeFolder = uiState.activeFolderFilter
        if (activeFolder != null) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Folder: ${activeFolder.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Surface(
              onClick = onClearFolderFilter,
              modifier = Modifier.testTag("clear_folder_filter_button"),
              shape = CircleShape,
              color = extended.surfaceHigh,
            ) {
              Text(
                text = "Show All",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

      // Pull-to-Refresh + Pinch-to-Zoom Timeline Content
      PullToRefreshBox(
        isRefreshing = uiState.isSyncingMedia,
        onRefresh = onSyncOrRequestMediaPermissions,
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .testTag("photos_pull_refresh")
            .pinchToZoomGridColumns(
              currentColumns = prefs.columns,
              minColumns = 2,
              maxColumns = 10,
              onColumnsChange = onSetColumns,
              onPinchActiveChange = { isPinching = it },
            ),
      ) {
        LazyColumn(
          state = listState,
          modifier =
            Modifier.fillMaxSize()
              .padding(bottom = if (uiState.selectedMediaIds.isNotEmpty()) 84.dp else 0.dp)
              .testTag("photos_timeline_list"),
          contentPadding =
            PaddingValues(
              top = 6.dp,
              bottom = if (uiState.selectedMediaIds.isNotEmpty()) 24.dp else 120.dp,
            ),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (showPermissionBanner) {
            item(key = "media_permission_banner") {
              MediaAccessBanner(
                accessLevel = uiState.mediaAccessLevel,
                onGrantClick = onSyncOrRequestMediaPermissions,
                onDismiss = onDismissPermissionBanner,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }

          if (showStoriesShelf) {
            item(key = "stories_shelf") {
              StoriesCarouselShelf(
                stories = uiState.stories,
                onOpenStory = onOpenStory,
              )
            }
          }

          if (preparedSections.isEmpty()) {
            item(key = "empty_timeline") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.PhotoLibrary,
                  contentDescription = null,
                  tint = extended.textDim,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = "No media matches this filter",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = "Reset active filters or sync your full device library.",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Button(
                    onClick = onResetFilters,
                    modifier = Modifier.testTag("reset_filters_button"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                      ),
                  ) {
                    Text("Reset Filters")
                  }
                }
              }
            }
          } else {
            preparedSections.forEach { section ->
              val sectionTitle = section.title
              val sectionItems = section.items
              if (prefs.groupBy != GroupByMode.NONE) {
                item(key = "section_header_$sectionTitle", contentType = "section_header") {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = sectionTitle,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (uiState.isSelectionMode) {
                      val sectionIds = section.itemIds
                      val allSectionSelected =
                        sectionIds.isNotEmpty() && sectionIds.all { it in uiState.selectedMediaIds }
                      Surface(
                        onClick = { onToggleSectionSelection(sectionIds) },
                        modifier =
                          Modifier.minimumInteractiveComponentSize()
                            .testTag(
                              "section_select_${sectionTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                            ),
                        shape = CircleShape,
                        color =
                          if (allSectionSelected) MaterialTheme.colorScheme.primary
                          else extended.surfaceHigh,
                      ) {
                        Text(
                          text =
                            if (allSectionSelected) "Selected (${sectionItems.size})"
                            else "Select (${sectionItems.size})",
                          style = MaterialTheme.typography.labelSmall,
                          color =
                            if (allSectionSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                      }
                    } else {
                      Text(
                        text = "${sectionItems.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textDim,
                      )
                    }
                  }
                }
              }

              if (prefs.layoutMode == LayoutMode.GRID) {
                val spacing = if (compactThumbnails) 2.dp else 4.dp
                items(
                  items = section.chunks,
                  key = { row -> "grid_row_${sectionTitle}_${row.first().id}" },
                  contentType = { "grid_row" },
                ) { rowItems ->
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(bottom = spacing),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                  ) {
                    rowItems.forEach { item ->
                      MediaThumbnailCard(
                        item = item,
                        forceSquare = true,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = item.id in uiState.selectedMediaIds,
                        compactMode = compactThumbnails,
                        onClick = {
                          if (uiState.isSelectionMode) onToggleMediaSelection(item.id)
                          else onOpenLightbox(item.id)
                        },
                        onLongClick = { onEnterSelectionMode(item.id) },
                        modifier = Modifier.weight(1f),
                      )
                    }
                    repeat((effectiveColumns - rowItems.size).coerceAtLeast(0)) {
                      Spacer(modifier = Modifier.weight(1f))
                    }
                  }
                }
              } else {
                items(
                  items = section.chunks,
                  key = { block -> "collage_block_${sectionTitle}_${block.first().id}" },
                  contentType = { "collage_block" },
                ) { blockItems ->
                  Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    StaggeredCollageColumns(
                      items = blockItems,
                      columns = effectiveColumns,
                      compactMode = compactThumbnails,
                      isSelectionMode = uiState.isSelectionMode,
                      selectedIds = uiState.selectedMediaIds,
                      onItemClick = { item ->
                        if (uiState.isSelectionMode) onToggleMediaSelection(item.id)
                        else onOpenLightbox(item.id)
                      },
                      onItemLongClick = { item -> onEnterSelectionMode(item.id) },
                    )
                  }
                }
              }
            }
          }
        }

        // Transient Pinch-to-Zoom Column Density Pill
        // Fully qualified: the outer Column's ColumnScope.AnimatedVisibility
        // member would shadow the top-level import, but K2 rejects it here
        // because this call sits inside PullToRefreshBox's BoxScope.
        androidx.compose.animation.AnimatedVisibility(
          visible = showDensityPill,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = extended.glassSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 6.dp,
            modifier = Modifier.testTag("grid_density_pill"),
          ) {
            Text(
              text =
                if (compactThumbnails) "$effectiveColumns per row • Compact"
                else "$effectiveColumns per row",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }
      }
    }

    // Interactive Pro Date Fast-Scrubber Track
    if (preparedSections.isNotEmpty() && !uiState.isSelectionMode) {
      BoxWithConstraints(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(end = 4.dp, top = 88.dp, bottom = 104.dp)
            .fillMaxHeight()
      ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val thumbHeightPx = with(density) { 46.dp.toPx() }
        val maxTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetY = (scrollProgressFraction * maxTravelPx).roundToInt()

        Row(
          modifier =
            Modifier.offset { IntOffset(0, thumbOffsetY) }
              .pointerInput(preparedSections) {
                var lastSectionIdx = -1
                detectVerticalDragGestures(
                  onDragStart = {
                    isDraggingScrubber = true
                    scrubFraction = scrollProgressFraction
                  },
                  onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val nextFrac = (scrubFraction + dragAmount / maxTravelPx).coerceIn(0f, 1f)
                    scrubFraction = nextFrac
                    val secIdx =
                      ((nextFrac * preparedSections.size).toInt()).coerceIn(
                        0,
                        preparedSections.lastIndex,
                      )
                    if (secIdx != lastSectionIdx) {
                      lastSectionIdx = secIdx
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      coroutineScope.launch {
                        listState.scrollToItem(preparedSections[secIdx].startLazyIndex)
                      }
                    }
                  },
                  onDragEnd = { isDraggingScrubber = false },
                  onDragCancel = { isDraggingScrubber = false },
                )
              },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AnimatedVisibility(
            visible = isScrolling && activeScrubberLabel.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              shadowElevation = 6.dp,
            ) {
              Text(
                text = activeScrubberLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }

          Box(
            modifier =
              Modifier.width(if (isDraggingScrubber) 7.dp else 5.dp)
                .height(46.dp)
                .clip(CircleShape)
                .background(
                  MaterialTheme.colorScheme.primary.copy(
                    alpha = if (isScrolling) 0.95f else 0.42f
                  )
                )
          )
        }
      }
    }

    // Floating Bottom Action Bar — appears only when items are selected
    val hasSelection = uiState.selectedMediaIds.isNotEmpty()
    AnimatedVisibility(
      visible = hasSelection,
      enter = slideInVertically { it } + fadeIn(),
      exit = slideOutVertically { it } + fadeOut(),
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
      Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).testTag("batch_action_bar"),
        shape = RoundedCornerShape(24.dp),
        color = extended.glassSurface,
        border = BorderStroke(1.dp, extended.borderStrong),
        shadowElevation = 10.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BatchActionItem(
            icon = Icons.Filled.Share,
            label = if (uiState.isPreparingShare) "..." else "Share",
            tint = MaterialTheme.colorScheme.primary,
            enabled = hasSelection && !uiState.isPreparingShare,
            onClick = onBatchShare,
            testTag = "batch_share_button",
            modifier = Modifier.weight(1f),
          )
          BatchActionItem(
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            label = "Move",
            tint = MaterialTheme.colorScheme.secondary,
            enabled = hasSelection,
            onClick = onBatchMove,
            testTag = "batch_move_button",
            modifier = Modifier.weight(1f),
          )
          BatchActionItem(
            icon = Icons.Filled.Favorite,
            label = "Favorite",
            tint = Color(0xFFFB7185),
            enabled = hasSelection,
            onClick = onBatchFavorite,
            testTag = "batch_favorite_button",
            modifier = Modifier.weight(1f),
          )
          BatchActionItem(
            icon = Icons.Filled.Lock,
            label = "Hide",
            tint = MaterialTheme.colorScheme.onSurface,
            enabled = hasSelection,
            onClick = onBatchHideToVault,
            testTag = "batch_hide_button",
            modifier = Modifier.weight(1f),
          )
          BatchActionItem(
            icon = Icons.Filled.Delete,
            label = "Delete",
            tint = Color(0xFFFCA5A5),
            enabled = hasSelection,
            onClick = onBatchDelete,
            testTag = "batch_delete_button",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun MediaAccessBanner(
  accessLevel: MediaAccessLevel,
  onGrantClick: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val isPartial = accessLevel == MediaAccessLevel.PARTIAL

  Surface(
    modifier = modifier.fillMaxWidth().testTag("media_permission_banner"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text =
          if (isPartial) "Partial Photo Access Active"
          else "Sync Your Full Device Library",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text =
          if (isPartial)
            "You granted access to selected items only. Allow full library access to browse all device folders automatically."
          else
            "Grant gallery permission so toadGALLERY can automatically display all device photos, videos, and real folders alongside sample items.",
        style = MaterialTheme.typography.bodySmall,
        color = extended.textMuted,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onDismiss) { Text("Later", color = extended.textMuted) }
        Spacer(modifier = Modifier.width(6.dp))
        Button(
          onClick = onGrantClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_media_permission_button"),
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text(if (isPartial) "Manage Access" else "Allow Full Library")
        }
      }
    }
  }
}

@Composable
private fun BatchActionItem(
  icon: ImageVector,
  label: String,
  tint: Color,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    color = Color.Transparent,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (enabled) tint else extended.textDim,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = if (enabled) MaterialTheme.colorScheme.onSurface else extended.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun StoriesCarouselShelf(
  stories: List<StorySlide>,
  onOpenStory: (Int) -> Unit,
) {
  val extended = LocalToadColors.current
  Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "MEMORIES & STORIES",
        style = MaterialTheme.typography.labelSmall,
        color = extended.textMuted,
      )
      Text(
        text = "Auto-curated",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(stories, key = { _, story -> story.id }) { idx, story ->
        Surface(
          modifier =
            Modifier.width(134.dp)
              .height(168.dp)
              .testTag("story_card_${story.id}")
              .clip(RoundedCornerShape(20.dp))
              .clickable { onOpenStory(idx) },
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, extended.borderStrong),
          shadowElevation = 4.dp,
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            Image(
              painter =
                painterResource(SampleMediaCatalog.resolveDrawableRes(story.drawableResName)),
              contentDescription = story.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors =
                        listOf(
                          Color.Transparent,
                          Color.Black.copy(alpha = 0.25f),
                          Color.Black.copy(alpha = 0.88f),
                        )
                    )
                  )
            )
            Box(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .padding(8.dp)
                  .size(26.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.55f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play story",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
              )
            }
            Column(
              modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = story.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = story.subtitle.substringBefore("•").trim(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StaggeredCollageColumns(
  items: List<GalleryMediaItem>,
  columns: Int,
  compactMode: Boolean,
  isSelectionMode: Boolean,
  selectedIds: Set<Long>,
  onItemClick: (GalleryMediaItem) -> Unit,
  onItemLongClick: (GalleryMediaItem) -> Unit,
) {
  val spacing = if (compactMode) 2.dp else 4.dp
  val columnLists =
    remember(items, columns) {
      val cols = List(columns) { mutableListOf<GalleryMediaItem>() }
      items.forEachIndexed { idx, item -> cols[idx % columns].add(item) }
      cols
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(spacing),
    verticalAlignment = Alignment.Top,
  ) {
    columnLists.forEach { colItems ->
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(spacing),
      ) {
        colItems.forEach { item ->
          MediaThumbnailCard(
            item = item,
            forceSquare = false,
            isSelectionMode = isSelectionMode,
            isSelected = item.id in selectedIds,
            compactMode = compactMode,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
          )
        }
      }
    }
  }
}
