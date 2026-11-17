package com.toadgallery.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.SearchTabMode
import com.toadgallery.app.ui.components.GalleryMediaImage
import com.toadgallery.app.ui.components.people.FaceThumbnail
import com.toadgallery.app.ui.components.pinchToZoomGridColumns
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.viewmodel.GalleryUiState
import com.toadgallery.app.ui.viewmodel.MapDisplayMode

@Composable
fun SearchScreen(
  uiState: GalleryUiState,
  isWideLayout: Boolean,
  onSelectSearchMode: (SearchTabMode) -> Unit,
  onQueryChange: (String) -> Unit,
  onToggleLocationFilter: () -> Unit,
  onSelectTypeFilter: (MediaType?) -> Unit,
  onSelectMapMode: (MapDisplayMode) -> Unit,
  onSelectMapPin: (Long) -> Unit,
  onNotifyMissingGps: () -> Unit,
  onOpenLightbox: (Long) -> Unit,
  onSetColumns: (Int) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val suggestions = remember {
    listOf("sunset", "receipt", "5,000", "dogs", "nature", "notice", "coffee")
  }
  var typeMenuExpanded by remember { mutableStateOf(false) }
  val prefsCols = uiState.preferences.columns.coerceIn(2, 10)
  val resultColumns = if (isWideLayout) (prefsCols + 1).coerceIn(2, 10) else prefsCols
  val compactResults = resultColumns >= 6

  // O(1) lookup map so People & Unassigned face rows never scan visibleMedia linearly
  val mediaById = remember(uiState.visibleMedia) { uiState.visibleMedia.associateBy { it.id } }
  val trimmedQuery = uiState.searchQuery.trim()
  val isPeopleHub = trimmedQuery.equals("portraits", ignoreCase = true)
  val isPersonDetail = trimmedQuery.startsWith("person:", ignoreCase = true)
  val activePersonGroup =
    remember(isPersonDetail, trimmedQuery, uiState.peopleGroups) {
      if (!isPersonDetail) null
      else {
        val targetId = trimmedQuery.removePrefix("person:")
        uiState.peopleGroups.firstOrNull { it.id.equals(targetId, ignoreCase = true) }
      }
    }

  Box(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 880.dp)
          .padding(top = statusBarTop + 12.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Unified Header + Mode Switcher (Magic Search | Places Map)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            if (uiState.searchTabMode == SearchTabMode.PLACES_MAP) "Discover Places"
            else if (isPeopleHub) "People"
            else if (isPersonDetail) activePersonGroup?.name?.takeIf { it.isNotBlank() } ?: "Person Photos"
            else "Magic Search",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, extended.borderSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.padding(4.dp)) {
            SearchTabMode.entries.forEach { mode ->
              val selected = uiState.searchTabMode == mode
              val icon =
                if (mode == SearchTabMode.MAGIC_SEARCH) Icons.Filled.Search else Icons.Filled.Map
              Surface(
                onClick = { onSelectSearchMode(mode) },
                modifier =
                  Modifier.weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag(
                      if (mode == SearchTabMode.MAGIC_SEARCH) "search_mode_magic"
                      else "search_mode_map"
                    ),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
              ) {
                Row(
                  modifier = Modifier.padding(vertical = 10.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                      if (selected) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (selected) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                  )
                }
              }
            }
          }
        }
      }

      if (uiState.searchTabMode == SearchTabMode.PLACES_MAP) {
        PlacesMapContent(
          uiState = uiState,
          onSelectMode = onSelectMapMode,
          onSelectPin = onSelectMapPin,
          onOpenLightbox = onOpenLightbox,
          onNotifyMissingGps = onNotifyMissingGps,
          modifier = Modifier.weight(1f).padding(bottom = 12.dp),
        )
      } else {
        val resultRows =
          remember(uiState.searchResults, resultColumns) {
            uiState.searchResults.chunked(resultColumns)
          }

        LazyColumn(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .testTag("search_results_list")
              .pinchToZoomGridColumns(
                currentColumns = prefsCols,
                minColumns = 2,
                maxColumns = 10,
                onColumnsChange = onSetColumns,
              ),
          contentPadding = PaddingValues(bottom = 110.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "search_subtitle") {
            Text(
              text =
                if (isPeopleHub || isPersonDetail)
                  "On-device face matching • private local embeddings"
                else "100% on-device semantic, folder & screenshot OCR search",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }

          // Hero Pill Search Input
          item(key = "search_input_bar") {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.5.dp, extended.borderStrong),
              modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                  if (uiState.searchQuery.isEmpty()) {
                    Text(
                      text = "Try 'sunsets', '5,000', 'dogs', 'notice'...",
                      style = MaterialTheme.typography.bodyMedium,
                      color = extended.textDim,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  BasicTextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle =
                      MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                      ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("magic_search_input"),
                  )
                }
                if (uiState.searchQuery.isNotEmpty()) {
                  IconButton(
                    onClick = { onQueryChange("") },
                    modifier =
                      Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Clear search query",
                      tint = extended.textMuted,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
            }
          }

          // Suggested Prompt Chips
          item(key = "search_suggestions_row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(suggestions, key = { it }) { suggestion ->
                val active = trimmedQuery.equals(suggestion, ignoreCase = true)
                Surface(
                  onClick = { onQueryChange(if (active) "" else suggestion) },
                  modifier = Modifier.testTag("search_suggestion_$suggestion"),
                  shape = CircleShape,
                  color =
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, extended.borderSubtle),
                ) {
                  Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (active) MaterialTheme.colorScheme.onPrimary else extended.textMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                  )
                }
              }
            }
          }

          // Secondary Attribute Filters
          item(key = "search_secondary_filters") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                onClick = onToggleLocationFilter,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("search_gps_filter_button"),
                shape = RoundedCornerShape(14.dp),
                color =
                  if (uiState.searchOnlyLocation) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extended.borderSubtle),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint =
                      if (uiState.searchOnlyLocation) MaterialTheme.colorScheme.onPrimaryContainer
                      else extended.textMuted,
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = "Has GPS Location",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (uiState.searchOnlyLocation) MaterialTheme.colorScheme.onPrimaryContainer
                      else extended.textMuted,
                  )
                }
              }

              Box {
                Surface(
                  onClick = { typeMenuExpanded = true },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("search_type_filter_button"),
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, extended.borderSubtle),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.FilterList,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(15.dp),
                    )
                    Text(
                      text = uiState.searchTypeFilter?.label ?: "All Types",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }

                DropdownMenu(
                  expanded = typeMenuExpanded,
                  onDismissRequest = { typeMenuExpanded = false },
                ) {
                  DropdownMenuItem(
                    text = { Text("All Types") },
                    onClick = {
                      onSelectTypeFilter(null)
                      typeMenuExpanded = false
                    },
                  )
                  MediaType.entries.forEach { mType ->
                    DropdownMenuItem(
                      text = { Text(mType.label) },
                      onClick = {
                        onSelectTypeFilter(mType)
                        typeMenuExpanded = false
                      },
                    )
                  }
                }
              }
            }
          }

          // Real face groups appear lazily when opened from the People smart collection.
          if (isPeopleHub) {
            item(key = "people_section_header") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "PEOPLE ON THIS DEVICE",
                  style = MaterialTheme.typography.labelSmall,
                  color = extended.textMuted,
                )
                if (uiState.faceCorrectionHistory.any { it.undoneAt == null }) {
                  Surface(
                    onClick = onUndoFaceCorrection,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = "Undo last change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                    }
                  }
                }
              }
            }

            if (uiState.peopleGroups.isEmpty()) {
              item(key = "people_empty_hint") {
                Text(
                  text =
                    "No people groups yet. Download the face models and run Re-index Library to find recurring faces.",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
              }
            } else {
              // Pro Horizontal Face Avatar Carousel Strip for instant tap-to-filter
              item(key = "people_avatar_carousel") {
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                  itemsIndexed(uiState.peopleGroups, key = { _, g -> "avatar_${g.id}" }) { index, group ->
                    val coverFace =
                      group.faces.firstOrNull {
                        it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                      } ?: group.faces.firstOrNull()
                    val coverItem = coverFace?.let { mediaById[it.photoId] } ?: mediaById[group.coverPhotoId]
                    val label = group.name?.takeIf { it.isNotBlank() } ?: "Person ${index + 1}"

                    Column(
                      modifier =
                        Modifier.width(68.dp)
                          .clip(RoundedCornerShape(14.dp))
                          .clickable { onQueryChange("person:${group.id}") }
                          .padding(vertical = 4.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(extended.surfaceHigh)
                      ) {
                        if (coverFace != null && coverItem != null) {
                          FaceThumbnail(
                            item = coverItem,
                            bounds = group.faceBounds[coverFace],
                            sourceRevision = uiState.faceIndexRevisions[coverFace.photoId] ?: 0L,
                            modifier = Modifier.fillMaxSize(),
                          )
                        } else if (coverItem != null) {
                          GalleryMediaImage(item = coverItem, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                          modifier =
                            Modifier.align(Alignment.BottomEnd)
                              .padding(3.dp)
                              .clip(CircleShape)
                              .background(Color.Black.copy(alpha = 0.72f))
                              .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                          Text(
                            text = "${group.photoIds.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color.White,
                          )
                        }
                      }
                      Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }
                }
              }

              // Lazy People Cards (composed on demand as user scrolls)
              itemsIndexed(
                items = uiState.peopleGroups,
                key = { _, group -> "people_group_card_${group.id}" },
                contentType = { _, _ -> "people_group_card" },
              ) { index, group ->
                Surface(
                  onClick = { onQueryChange("person:${group.id}") },
                  modifier = Modifier.fillMaxWidth().testTag("people_group_${index + 1}"),
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, extended.borderSubtle),
                ) {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    val coverFace =
                      group.faces.firstOrNull {
                        it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                      } ?: group.faces.firstOrNull()
                    val coverItem = coverFace?.let { mediaById[it.photoId] }
                    if (coverFace != null && coverItem != null) {
                      FaceThumbnail(
                        item = coverItem,
                        bounds = group.faceBounds[coverFace],
                        sourceRevision = uiState.faceIndexRevisions[coverFace.photoId] ?: 0L,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                      )
                    } else {
                      val cover = mediaById[group.coverPhotoId]
                      if (cover != null) {
                        GalleryMediaImage(
                          item = cover,
                          modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                        )
                      }
                    }
                    Column(
                      modifier = Modifier.weight(1f).padding(start = 10.dp),
                      verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Text(
                        text = group.name?.takeIf { it.isNotBlank() } ?: "Person ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      Text(
                        text = "${group.photoIds.size} photos • ${group.faceCount} face matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textMuted,
                      )
                    }
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }

            }
          }

          if (isPersonDetail) {
            item(key = "person_detail_banner") {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onQueryChange("portraits") }) { Text("← Back to People") }
                if (activePersonGroup != null) {
                  Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extended.borderStrong),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        val coverFace =
                          activePersonGroup.faces.firstOrNull {
                            it.photoId == activePersonGroup.coverPhotoId &&
                              it.faceIndex == activePersonGroup.coverFaceIndex
                          } ?: activePersonGroup.faces.firstOrNull()
                        val coverItem = coverFace?.let { mediaById[it.photoId] }
                        if (coverFace != null && coverItem != null) {
                          FaceThumbnail(
                            item = coverItem,
                            bounds = activePersonGroup.faceBounds[coverFace],
                            sourceRevision =
                              uiState.faceIndexRevisions[coverFace.photoId] ?: 0L,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                          )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                          Text(
                            text =
                              activePersonGroup.name?.takeIf { it.isNotBlank() }
                                ?: "Unnamed Person",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                          )
                          Text(
                            text =
                              "${activePersonGroup.photoIds.size} photos • ${activePersonGroup.faceCount} face appearances",
                            style = MaterialTheme.typography.bodySmall,
                            color = extended.textMuted,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          // Results Header
          item(key = "search_results_header") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${uiState.searchResults.size} matches found",
                style = MaterialTheme.typography.labelMedium,
                color = extended.textMuted,
              )
              Surface(shape = CircleShape, color = extended.surfaceHigh) {
                Text(
                  text =
                    if (isPeopleHub || isPersonDetail) "Face Groups"
                    else if (uiState.preferences.aiMagicSearch) "CLIP + OCR Active"
                    else "Metadata Only",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }

          if (uiState.searchResults.isEmpty()) {
            item(key = "search_empty_results") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.SearchOff,
                  contentDescription = null,
                  tint = extended.textDim,
                  modifier = Modifier.size(42.dp),
                )
                Text(
                  text = "No matching memories",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = "Try searching for 'sunset', 'receipt', '5,000', or clear filters.",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
                Button(
                  onClick = {
                    onQueryChange("")
                    onSelectTypeFilter(null)
                    if (uiState.searchOnlyLocation) onToggleLocationFilter()
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Text("Clear Search")
                }
              }
            }
          } else {
            val rowSpacing = if (compactResults) 4.dp else 10.dp
            val corner =
              when {
                resultColumns >= 6 -> 8.dp
                resultColumns >= 4 -> 14.dp
                else -> 20.dp
              }
            items(
              items = resultRows,
              key = { row -> "search_row_${row.first().id}" },
              contentType = { "search_row" },
            ) { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
              ) {
                rowItems.forEach { item ->
                  Surface(
                    modifier =
                      Modifier.weight(1f)
                        .then(
                          if (compactResults) Modifier.aspectRatio(1f)
                          else if (resultColumns >= 4) Modifier.height(126.dp)
                          else Modifier.height(160.dp)
                        )
                        .testTag("search_result_${item.id}")
                        .clip(RoundedCornerShape(corner))
                        .clickable { onOpenLightbox(item.id) },
                    shape = RoundedCornerShape(corner),
                    color = MaterialTheme.colorScheme.surface,
                    border =
                      BorderStroke(
                        width = if (compactResults) 0.5.dp else 1.dp,
                        color = extended.borderSubtle,
                      ),
                  ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                      GalleryMediaImage(
                        item = item,
                        thumbnailSizePx = if (compactResults) 220 else 384,
                        modifier = Modifier.fillMaxSize(),
                      )
                      if (!compactResults) {
                        Box(
                          modifier =
                            Modifier.fillMaxSize()
                              .background(
                                Brush.verticalGradient(
                                  0.35f to Color.Transparent,
                                  1.0f to Color.Black.copy(alpha = 0.88f),
                                )
                              )
                        )
                        Column(
                          modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                          verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                          Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                          Text(
                            text = item.tags.take(3).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color(0xFF6EE7B7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                      }
                    }
                  }
                }
                repeat(resultColumns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
          }
        }
      }
    }
  }

