package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.SearchTabMode
import com.example.ui.components.GalleryMediaImage
import com.example.ui.components.people.FaceThumbnail
import com.example.ui.components.pinchToZoomGridColumns
import com.example.ui.theme.LocalToadColors
import com.example.data.ai.FacePersonGroup
import com.example.data.ai.FaceCropBounds
import com.example.ui.viewmodel.GalleryUiState
import com.example.ui.viewmodel.MapDisplayMode

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
  onNamePerson: (FacePersonGroup, String?) -> Unit = { _, _ -> },
  onExcludeFace: (Long, Int) -> Unit = { _, _ -> },
  onSetPersonCover: (FacePersonGroup, Long, Int) -> Unit = { _, _, _ -> },
  onSetPersonIgnored: (FacePersonGroup, Boolean) -> Unit = { _, _ -> },
  onAssignFace: (Long, Int, FacePersonGroup) -> Unit = { _, _, _ -> },
  onUndoFaceCorrection: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val suggestions = remember { listOf("sunset", "receipt", "5,000", "dogs", "nature", "notice", "coffee") }
  var typeMenuExpanded by remember { mutableStateOf(false) }
  var personBeingNamed by remember { mutableStateOf<FacePersonGroup?>(null) }
  var personNameInput by remember { mutableStateOf("") }
  var showFaceHistory by remember { mutableStateOf(false) }
  val prefsCols = uiState.preferences.columns.coerceIn(2, 10)
  val resultColumns = if (isWideLayout) (prefsCols + 1).coerceIn(2, 10) else prefsCols
  val compactResults = resultColumns >= 6

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
            else if (uiState.searchQuery.trim().equals("portraits", ignoreCase = true)) "People"
            else if (uiState.searchQuery.startsWith("person:")) "Person Photos"
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
        LazyColumn(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .pinchToZoomGridColumns(
                currentColumns = prefsCols,
                minColumns = 2,
                maxColumns = 10,
                onColumnsChange = onSetColumns,
              ),
          contentPadding = PaddingValues(bottom = 110.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item {
            Text(
              text = if (uiState.searchQuery.trim().equals("portraits", ignoreCase = true) || uiState.searchQuery.startsWith("person:")) "On-device face matching • private local embeddings" else "100% on-device semantic, folder & screenshot OCR search",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }

          // Hero Pill Search Input
          item {
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
          item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(suggestions) { suggestion ->
                val active = uiState.searchQuery.trim().equals(suggestion, ignoreCase = true)
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
          item {
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

          // Real face groups appear when opened from the People smart collection.
          if (uiState.searchQuery.trim().equals("portraits", ignoreCase = true)) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "PEOPLE ON THIS DEVICE",
                  style = MaterialTheme.typography.labelSmall,
                  color = extended.textMuted,
                )
                if (uiState.peopleGroups.isEmpty()) {
                  Text(
                    text = "No people groups yet. Download the face models and run Re-index Library to find recurring faces.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                } else {
                  uiState.peopleGroups.forEachIndexed { index, group ->
                    Surface(
                      onClick = { onQueryChange("person:${group.id}") },
                      modifier = Modifier.fillMaxWidth().testTag("people_group_${index + 1}"),
                      shape = RoundedCornerShape(16.dp),
                      color = MaterialTheme.colorScheme.surface,
                      border = BorderStroke(1.dp, extended.borderSubtle),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        val coverFace = group.faces.firstOrNull {
                          it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                        } ?: group.faces.firstOrNull()
                        val coverItem = coverFace?.let { face -> uiState.visibleMedia.firstOrNull { it.id == face.photoId } }
                        if (coverFace != null && coverItem != null) {
                          FaceThumbnail(
                            item = coverItem,
                            bounds = group.faceBounds[coverFace],
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                          )
                        } else {
                          val cover = uiState.visibleMedia.firstOrNull { it.id == group.coverPhotoId }
                          if (cover != null) GalleryMediaImage(item = cover, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                          TextButton(onClick = {
                            personBeingNamed = group
                            personNameInput = group.name.orEmpty()
                          }) {
                            Text(if (group.name.isNullOrBlank()) "Name" else "Edit")
                          }
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
                if (uiState.ignoredPeopleGroups.isNotEmpty()) {
                  Spacer(Modifier.height(8.dp))
                  Text("IGNORED PEOPLE", style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
                  uiState.ignoredPeopleGroups.forEachIndexed { index, group ->
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val coverFace = group.faces.firstOrNull {
                          it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                        } ?: group.faces.firstOrNull()
                        val cover = coverFace?.let { face -> uiState.visibleMedia.firstOrNull { it.id == face.photoId } }
                        if (coverFace != null && cover != null) {
                          FaceThumbnail(item = cover, bounds = group.faceBounds[coverFace], modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        }
                        Column {
                          Text(group.name?.takeIf { it.isNotBlank() } ?: "Ignored person ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                          Text("${group.photoIds.size} photos", style = MaterialTheme.typography.bodySmall, color = extended.textMuted)
                        }
                      }
                      TextButton(onClick = { onSetPersonIgnored(group, false) }) { Text("Unignore") }
                    }
                  }
                }
              }
            }
          }

          if (uiState.searchQuery.startsWith("person:")) {
            item {
              androidx.compose.material3.TextButton(onClick = { onQueryChange("portraits") }) {
                Text("← Back to People")
              }
            }
          }

          // Results Header
          item {
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
                    if (uiState.searchQuery.trim().equals("portraits", ignoreCase = true) || uiState.searchQuery.startsWith("person:")) "Face Groups"
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
            item {
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
            val rows = uiState.searchResults.chunked(resultColumns)
            val rowSpacing = if (compactResults) 4.dp else 10.dp
            val corner =
              when {
                resultColumns >= 6 -> 8.dp
                resultColumns >= 4 -> 14.dp
                else -> 20.dp
              }
            items(rows) { rowItems ->
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
                      GalleryMediaImage(item = item, modifier = Modifier.fillMaxSize())
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

  personBeingNamed?.let { group ->
    val existingNames = uiState.peopleGroups.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    AlertDialog(
      onDismissRequest = { personBeingNamed = null },
      title = { Text(if (group.name.isNullOrBlank()) "Name this person" else "Rename person") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = personNameInput,
            onValueChange = { personNameInput = it },
            label = { Text("Person name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("person_name_input"),
          )
          if (existingNames.isNotEmpty()) {
            Text("Or merge with an existing person", style = MaterialTheme.typography.labelMedium)
            existingNames.filterNot { it.equals(group.name, ignoreCase = true) }.forEach { existing ->
              TextButton(onClick = {
                onNamePerson(group, existing)
                personBeingNamed = null
              }) { Text(existing) }
            }
          }
          Text("Choose a face as cover", style = MaterialTheme.typography.labelMedium)
          group.faces
            .sortedByDescending { it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex }
            .take(16)
            .forEach { face ->
              val photo = uiState.visibleMedia.firstOrNull { it.id == face.photoId }
              if (photo != null) {
                val selected = face.photoId == group.coverPhotoId && face.faceIndex == group.coverFaceIndex
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  FaceThumbnail(
                    item = photo,
                    bounds = group.faceBounds[face],
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(photo.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Face ${face.faceIndex + 1}", style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
                  }
                  TextButton(onClick = {
                    onSetPersonCover(group, face.photoId, face.faceIndex)
                    personBeingNamed = null
                  }) { Text(if (selected) "Current cover" else "Use as cover") }
                }
              }
            }
          if (group.faces.isNotEmpty()) {
            Text("Remove an incorrectly grouped face", style = MaterialTheme.typography.labelMedium)
            group.faces.take(10).forEach { face ->
              val photo = uiState.visibleMedia.firstOrNull { it.id == face.photoId }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (photo != null) {
                  FaceThumbnail(
                    item = photo,
                    bounds = group.faceBounds[face],
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    photo?.title ?: "Photo unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    "Detected face ${face.faceIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extended.textMuted,
                  )
                }
                TextButton(onClick = {
                  onExcludeFace(face.photoId, face.faceIndex)
                  personBeingNamed = null
                }) { Text("Remove") }
                var assignMenuExpanded by remember(face.photoId, face.faceIndex) { mutableStateOf(false) }
                Box {
                  TextButton(onClick = { assignMenuExpanded = true }) { Text("Assign") }
                  DropdownMenu(
                    expanded = assignMenuExpanded,
                    onDismissRequest = { assignMenuExpanded = false },
                  ) {
                    uiState.peopleGroups.filter { target ->
                      target.clusterIds.firstOrNull() != group.clusterIds.firstOrNull()
                    }.forEach { target ->
                      DropdownMenuItem(
                        text = { Text(target.name?.takeIf { it.isNotBlank() } ?: "Unnamed person") },
                        onClick = {
                          onAssignFace(face.photoId, face.faceIndex, target)
                          assignMenuExpanded = false
                          personBeingNamed = null
                        },
                      )
                    }
                  }
                }
              }
            }
          }
          if (group.clusterIds.size > 1) {
            Text("Separate a merged cluster", style = MaterialTheme.typography.labelMedium)
            group.clusterIds.forEachIndexed { index, clusterId ->
              TextButton(onClick = {
                onNamePerson(group.copy(clusterIds = listOf(clusterId)), null)
                personBeingNamed = null
              }) { Text("Separate cluster ${index + 1}") }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          onNamePerson(group, personNameInput)
          personBeingNamed = null
        }, enabled = personNameInput.isNotBlank()) { Text("Save") }
      },
      dismissButton = {
        Row {
          if (!group.name.isNullOrBlank()) {
            TextButton(onClick = {
              onNamePerson(group, null)
              personBeingNamed = null
            }) { Text("Remove name") }
          }
          TextButton(onClick = { onSetPersonIgnored(group, true); personBeingNamed = null }) { Text("Ignore person") }
          TextButton(onClick = { onUndoFaceCorrection() }) { Text("Undo correction") }
          TextButton(onClick = { showFaceHistory = true }) { Text("History (${uiState.faceCorrectionHistory.size})") }
          TextButton(onClick = { personBeingNamed = null }) { Text("Cancel") }
        }
      },
    )
  }
  if (showFaceHistory) {
    AlertDialog(
      onDismissRequest = { showFaceHistory = false },
      title = { Text("Face correction history") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (uiState.faceCorrectionHistory.isEmpty()) {
            Text("No face corrections yet")
          } else {
            uiState.faceCorrectionHistory.take(20).forEach { entry ->
              val action = when {
                entry.undoneAt != null -> "Undone"
                entry.isExcluded -> "Removed from person"
                entry.nextPersonClusterId != null -> "Assigned to another person"
                else -> "Restored"
              }
              Text("$action • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAt))}")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showFaceHistory = false }) { Text("Close") }
      },
    )
  }
}
