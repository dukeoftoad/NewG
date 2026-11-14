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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SampleMediaCatalog
import com.example.data.media.MediaAccessLevel
import com.example.data.model.GalleryMediaItem
import com.example.data.model.GroupByMode
import com.example.data.model.LayoutMode
import com.example.data.model.MediaCategory
import com.example.data.model.StorySlide
import com.example.ui.components.MediaThumbnailCard
import com.example.ui.theme.LocalToadColors
import com.example.ui.viewmodel.GalleryUiState

@Composable
fun PhotosScreen(
  uiState: GalleryUiState,
  isWideLayout: Boolean,
  onSelectCategory: (MediaCategory) -> Unit,
  onClearFolderFilter: () -> Unit,
  onToggleCleanFeed: () -> Unit,
  onToggleQuickLayout: () -> Unit,
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
    if (isWideLayout) (prefs.columns + 1).coerceAtMost(5) else prefs.columns.coerceIn(2, 4)

  val groupedSections =
    remember(uiState.filteredTimelineMedia, prefs.groupBy) {
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
    }

  val listState = rememberLazyListState()
  val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
  val activeScrubberLabel by
    remember(groupedSections, prefs.groupBy, prefs.showStories, uiState.activeFolderFilter, uiState.mediaAccessLevel, uiState.permissionBannerDismissed) {
      derivedStateOf {
        val idx = listState.firstVisibleItemIndex
        val leadingItems =
          (if (uiState.mediaAccessLevel != MediaAccessLevel.FULL && !uiState.permissionBannerDismissed) 1 else 0) +
            (if (prefs.showStories && uiState.activeFolderFilter == null) 1 else 0)
        val stride = if (prefs.groupBy != GroupByMode.NONE) 2 else 1
        val sectionIdx =
          ((idx - leadingItems).coerceAtLeast(0) / stride)
            .coerceIn(0, groupedSections.lastIndex.coerceAtLeast(0))
        groupedSections.getOrNull(sectionIdx)?.first ?: "November 2026"
      }
    }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Sticky Translucent Frosted Header (switches to Multi-Select bar when active)
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(extended.glassSurface)
            .padding(top = statusBarTop + 8.dp, bottom = 10.dp)
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
              uiState.filteredTimelineMedia.isNotEmpty() &&
                uiState.filteredTimelineMedia.all { it.id in uiState.selectedMediaIds }
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
        } else {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "t",
                  style =
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              }
              Text(
                text = "toadGALLERY",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              IconButton(
                onClick = { onEnterSelectionMode(null) },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("select_mode_button")
                    .clip(CircleShape)
                    .background(extended.surfaceHigh),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Enter multi-select mode",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(19.dp),
                )
              }

              IconButton(
                onClick = onSyncOrRequestMediaPermissions,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("sync_mediastore_button")
                    .clip(CircleShape)
                    .background(extended.surfaceHigh),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Sync device MediaStore library",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(19.dp),
                )
              }

              IconButton(
                onClick = onToggleQuickLayout,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("toggle_layout_button")
                    .clip(CircleShape)
                    .background(extended.surfaceHigh),
              ) {
                Icon(
                  imageVector =
                    if (prefs.layoutMode == LayoutMode.COLLAGE) Icons.Filled.GridView
                    else Icons.Filled.ViewAgenda,
                  contentDescription = "Switch Grid or Collage layout",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(19.dp),
                )
              }

              Surface(
                onClick = onToggleCleanFeed,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("clean_feed_button"),
                shape = CircleShape,
                color =
                  if (prefs.hideScreenshots) MaterialTheme.colorScheme.primaryContainer
                  else extended.surfaceHigh,
                border =
                  BorderStroke(
                    width = 1.dp,
                    color =
                      if (prefs.hideScreenshots) MaterialTheme.colorScheme.primary
                      else Color.Transparent,
                  ),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector =
                      if (prefs.hideScreenshots) Icons.Outlined.HideImage
                      else Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint =
                      if (prefs.hideScreenshots) MaterialTheme.colorScheme.onPrimaryContainer
                      else extended.textMuted,
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = if (prefs.hideScreenshots) "No Shots" else "Clean",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (prefs.hideScreenshots) MaterialTheme.colorScheme.onPrimaryContainer
                      else extended.textMuted,
                  )
                }
              }
            }
          }
        }

        // Active folder filter pill if drilled down from AlbumsScreen
        val activeFolder = uiState.activeFolderFilter
        if (activeFolder != null) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        } else {
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Scrollable Filter Chips with Right-Edge Alpha Fade Mask
        LazyRow(
          modifier =
            Modifier.fillMaxWidth()
              .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
              .drawWithContent {
                drawContent()
                drawRect(
                  brush =
                    Brush.horizontalGradient(
                      0.86f to Color.Black,
                      1.0f to Color.Transparent,
                    ),
                  blendMode = BlendMode.DstIn,
                )
              },
          contentPadding = PaddingValues(start = 16.dp, end = 28.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

      // Timeline Scroll Content
      LazyColumn(
        state = listState,
        modifier =
          Modifier.fillMaxSize()
            .padding(bottom = if (uiState.selectedMediaIds.isNotEmpty()) 84.dp else 0.dp)
            .testTag("photos_timeline_list"),
        contentPadding =
          PaddingValues(bottom = if (uiState.selectedMediaIds.isNotEmpty()) 24.dp else 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (uiState.mediaAccessLevel != MediaAccessLevel.FULL && !uiState.permissionBannerDismissed) {
          item(key = "media_permission_banner") {
            MediaAccessBanner(
              accessLevel = uiState.mediaAccessLevel,
              onGrantClick = onSyncOrRequestMediaPermissions,
              onDismiss = onDismissPermissionBanner,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
          }
        }

        if (prefs.showStories && uiState.activeFolderFilter == null) {
          item(key = "stories_shelf") {
            StoriesCarouselShelf(
              stories = uiState.stories,
              onOpenStory = onOpenStory,
            )
          }
        }

        if (groupedSections.isEmpty()) {
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
          groupedSections.forEach { (sectionTitle, sectionItems) ->
            if (prefs.groupBy != GroupByMode.NONE) {
              item(key = "section_header_$sectionTitle") {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                  if (uiState.isSelectionMode) {
                    val sectionIds = remember(sectionItems) { sectionItems.map { it.id } }
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
                        text = if (allSectionSelected) "Selected (${sectionItems.size})" else "Select (${sectionItems.size})",
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

            item(key = "section_grid_$sectionTitle") {
              Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                if (prefs.layoutMode == LayoutMode.GRID) {
                  UniformPhotoGrid(
                    items = sectionItems,
                    columns = effectiveColumns,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedIds = uiState.selectedMediaIds,
                    onItemClick = { item ->
                      if (uiState.isSelectionMode) onToggleMediaSelection(item.id)
                      else onOpenLightbox(item.id)
                    },
                    onItemLongClick = { item -> onEnterSelectionMode(item.id) },
                  )
                } else {
                  StaggeredCollageColumns(
                    items = sectionItems,
                    columns = effectiveColumns,
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
    }

    // Floating Date Scrubber Pill
    if (groupedSections.isNotEmpty() && !uiState.isSelectionMode) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(end = 6.dp, top = 72.dp, bottom = 96.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AnimatedVisibility(
            visible = isScrolling,
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
              Modifier.width(5.dp)
                .height(48.dp)
                .clip(CircleShape)
                .background(
                  MaterialTheme.colorScheme.primary.copy(alpha = if (isScrolling) 0.95f else 0.45f)
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
  Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(stories, key = { it.id }) { story ->
        Surface(
          modifier =
            Modifier.width(134.dp)
              .height(168.dp)
              .testTag("story_card_${story.id}")
              .clip(RoundedCornerShape(20.dp))
              .clickable { onOpenStory(story.id) },
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
private fun UniformPhotoGrid(
  items: List<GalleryMediaItem>,
  columns: Int,
  isSelectionMode: Boolean,
  selectedIds: Set<Long>,
  onItemClick: (GalleryMediaItem) -> Unit,
  onItemLongClick: (GalleryMediaItem) -> Unit,
) {
  val rows = remember(items, columns) { items.chunked(columns) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    rows.forEach { rowItems ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        rowItems.forEach { item ->
          MediaThumbnailCard(
            item = item,
            forceSquare = true,
            isSelectionMode = isSelectionMode,
            isSelected = item.id in selectedIds,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
            modifier = Modifier.weight(1f),
          )
        }
        repeat((columns - rowItems.size).coerceAtLeast(0)) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun StaggeredCollageColumns(
  items: List<GalleryMediaItem>,
  columns: Int,
  isSelectionMode: Boolean,
  selectedIds: Set<Long>,
  onItemClick: (GalleryMediaItem) -> Unit,
  onItemLongClick: (GalleryMediaItem) -> Unit,
) {
  val columnLists =
    remember(items, columns) {
      val cols = List(columns) { mutableListOf<GalleryMediaItem>() }
      items.forEachIndexed { idx, item -> cols[idx % columns].add(item) }
      cols
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.Top,
  ) {
    columnLists.forEach { colItems ->
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        colItems.forEach { item ->
          MediaThumbnailCard(
            item = item,
            forceSquare = false,
            isSelectionMode = isSelectionMode,
            isSelected = item.id in selectedIds,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
          )
        }
      }
    }
  }
}
