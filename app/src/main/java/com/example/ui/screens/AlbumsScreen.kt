package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.SampleMediaCatalog
import com.example.data.model.AlbumFolder
import com.example.data.model.TimelineFolderFilter
import com.example.ui.theme.LocalToadColors
import com.example.ui.viewmodel.GalleryUiState

private data class SmartCollectionTile(
  val name: String,
  val subtitle: String,
  val drawableName: String,
  val icon: ImageVector,
  val enabled: Boolean,
)

@Composable
fun AlbumsScreen(
  uiState: GalleryUiState,
  isWideLayout: Boolean,
  onOpenSmartCollection: (String) -> Unit,
  onOpenFolderFilter: (TimelineFolderFilter) -> Unit,
  onCreateAlbum: (String) -> Unit,
  onOpenVault: () -> Unit,
  onOpenRecycleBin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val prefs = uiState.preferences
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  var showCreateDialog by remember { mutableStateOf(false) }
  var newAlbumName by remember { mutableStateOf("") }

  val smartCollections =
    remember(prefs.aiPeople, prefs.aiThings, uiState.geoItems.size, uiState.peopleGroups.size) {
      listOf(
        SmartCollectionTile(
          name = "People",
          subtitle = if (prefs.aiPeople) "${uiState.peopleGroups.size} people found" else "Disabled in AI",
          drawableName = "sample_portrait_warm",
          icon = Icons.Filled.Group,
          enabled = prefs.aiPeople,
        ),
        SmartCollectionTile(
          name = "Places",
          subtitle = "${uiState.geoItems.size} on map",
          drawableName = "sample_lake_sunset",
          icon = Icons.Filled.Place,
          enabled = true,
        ),
        SmartCollectionTile(
          name = "Things",
          subtitle = if (prefs.aiThings) "Dogs, Sunsets" else "Disabled in AI",
          drawableName = "sample_golden_puppy",
          icon = Icons.Filled.AutoAwesome,
          enabled = prefs.aiThings,
        ),
        SmartCollectionTile(
          name = "Receipts & Docs",
          subtitle = "OCR scans",
          drawableName = "sample_upi_receipt",
          icon = Icons.Filled.Description,
          enabled = true,
        ),
      )
    }

  val gridCols = if (isWideLayout) 3 else 2

  Box(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).testTag("albums_list"),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = statusBarTop + 14.dp,
          bottom = 110.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Albums",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Surface(
            onClick = { showCreateDialog = true },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("new_album_button"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "New Album",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      // On-Device Smart Collections
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "ON-DEVICE SMART COLLECTIONS",
              style = MaterialTheme.typography.labelSmall,
              color = extended.textMuted,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Private AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          smartCollections.chunked(gridCols).forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              rowItems.forEach { tile ->
                Surface(
                  onClick = { onOpenSmartCollection(tile.name) },
                  modifier =
                    Modifier.weight(1f)
                      .testTag("smart_collection_${tile.name.lowercase().replace(" ", "_")}"),
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, extended.borderSubtle),
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Image(
                      painter =
                        painterResource(SampleMediaCatalog.resolveDrawableRes(tile.drawableName)),
                      contentDescription = tile.name,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = tile.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
              repeat(gridCols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
        }
      }

      // Real Device Buckets & Custom Albums
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "DEVICE & CUSTOM FOLDERS",
            style = MaterialTheme.typography.labelSmall,
            color = extended.textMuted,
          )
          uiState.folderAlbums.chunked(gridCols).forEach { rowFolders ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              rowFolders.forEach { folder ->
                AlbumFolderCard(
                  folder = folder,
                  onClick = {
                    val filter =
                      when {
                        folder.isFavoritesAlbum -> TimelineFolderFilter.Favorites
                        folder.customAlbumId != null ->
                          TimelineFolderFilter.CustomAlbum(folder.customAlbumId, folder.name)
                        folder.filterBucketName != null ->
                          TimelineFolderFilter.DeviceBucket(folder.filterBucketName)
                        else -> TimelineFolderFilter.DeviceBucket(folder.name)
                      }
                    onOpenFolderFilter(filter)
                  },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(gridCols - rowFolders.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
        }
      }

      // Surfaced Utilities & Privacy
      item {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, extended.borderSubtle),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "UTILITIES & PRIVACY",
              style = MaterialTheme.typography.labelSmall,
              color = extended.textMuted,
              modifier = Modifier.padding(horizontal = 2.dp),
            )

            UtilityActionRow(
              icon = Icons.Filled.Lock,
              iconBg = MaterialTheme.colorScheme.primaryContainer,
              iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
              title = "Hidden Vault",
              subtitle = "${uiState.vaultMedia.size} AES-256 encrypted items • PIN & biometrics",
              onClick = onOpenVault,
              testTag = "albums_hidden_vault_button",
            )

            UtilityActionRow(
              icon = Icons.Outlined.DeleteOutline,
              iconBg = MaterialTheme.colorScheme.secondaryContainer,
              iconTint = MaterialTheme.colorScheme.onSurface,
              title = "Recycle Bin",
              subtitle = "${uiState.trashedMedia.size} deleted items • Auto-deletes in 30 days",
              onClick = onOpenRecycleBin,
              testTag = "albums_recycle_bin_button",
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create New Album", style = MaterialTheme.typography.titleMedium) },
      text = {
        OutlinedTextField(
          value = newAlbumName,
          onValueChange = { newAlbumName = it },
          label = { Text("Album name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_album_name_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newAlbumName.isNotBlank()) {
              onCreateAlbum(newAlbumName)
              newAlbumName = ""
              showCreateDialog = false
            }
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text("Create")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun AlbumFolderCard(
  folder: AlbumFolder,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  Surface(
    onClick = onClick,
    modifier = modifier.testTag("album_folder_${folder.name.lowercase().replace(" ", "_")}"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, extended.borderSubtle),
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        if (!folder.coverUriString.isNullOrBlank()) {
          AsyncImage(
            model =
              ImageRequest.Builder(LocalContext.current)
                .data(folder.coverUriString)
                .crossfade(true)
                .build(),
            contentDescription = folder.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Image(
            painter =
              painterResource(SampleMediaCatalog.resolveDrawableRes(folder.coverDrawableName)),
            contentDescription = folder.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        Box(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(8.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.7f))
              .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = "${folder.itemCount}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = folder.name,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = extended.textDim,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun UtilityActionRow(
  icon: ImageVector,
  iconBg: Color,
  iconTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val extended = LocalToadColors.current
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag(testTag),
    shape = RoundedCornerShape(18.dp),
    color = extended.surfaceHigh,
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = extended.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = extended.textMuted,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
