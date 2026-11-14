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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.ui.theme.LocalToadColors
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
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val suggestions = remember { listOf("sunset", "receipt", "5,000", "dogs", "nature", "notice", "coffee") }
  var typeMenuExpanded by remember { mutableStateOf(false) }
  val resultColumns = if (isWideLayout) 3 else 2

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
            if (uiState.searchTabMode == SearchTabMode.MAGIC_SEARCH) "Magic Search"
            else "Discover Places",
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
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(bottom = 110.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item {
            Text(
              text = "100% on-device semantic, folder & screenshot OCR search",
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
                    if (uiState.preferences.aiMagicSearch) "CLIP + OCR Active"
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
            items(rows) { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                rowItems.forEach { item ->
                  Surface(
                    modifier =
                      Modifier.weight(1f)
                        .height(160.dp)
                        .testTag("search_result_${item.id}")
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenLightbox(item.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extended.borderSubtle),
                  ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                      GalleryMediaImage(item = item, modifier = Modifier.fillMaxSize())
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
                repeat(resultColumns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
          }
        }
      }
    }
  }
}
