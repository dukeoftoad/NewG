package com.example.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AlbumEntity
import com.example.ui.theme.LocalToadColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchMoveDestinationDialog(
  customAlbums: List<AlbumEntity>,
  folderDestinations: List<String>,
  selectedCount: Int,
  onMoveToAlbum: (Long, String) -> Unit,
  onCreateAlbumAndMove: (String) -> Unit,
  onMoveToFolder: (String) -> Unit,
  onMoveToVault: () -> Unit,
  onDismiss: () -> Unit,
) {
  val extended = LocalToadColors.current
  var newAlbumName by remember { mutableStateOf("") }
  var customFolderName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("batch_move_dialog"),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Move $selectedCount ${if (selectedCount == 1) "item" else "items"}",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Hidden Vault quick destination
        Surface(
          onClick = onMoveToVault,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("move_dest_vault"),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Hidden Vault",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              )
              Text(
                text = "Encrypt with AES-256 and hide from timeline",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
              )
            }
          }
        }

        HorizontalDivider(color = extended.borderSubtle)

        // Device & Gallery Folders
        Text(
          text = "GALLERY FOLDERS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          folderDestinations.forEach { folder ->
            Surface(
              onClick = { onMoveToFolder(folder) },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("move_dest_folder_${folder.lowercase().replace(" ", "_")}"),
              shape = CircleShape,
              color = extended.surfaceHigh,
              border = BorderStroke(1.dp, extended.borderSubtle),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.FolderOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(15.dp),
                )
                Text(text = folder, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = customFolderName,
            onValueChange = { customFolderName = it },
            label = { Text("Move to new folder") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("move_custom_folder_input"),
          )
          Button(
            onClick = {
              if (customFolderName.isNotBlank()) {
                onMoveToFolder(customFolderName.trim())
                customFolderName = ""
              }
            },
            enabled = customFolderName.isNotBlank(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("move_custom_folder_button"),
          ) {
            Text("Move")
          }
        }

        HorizontalDivider(color = extended.borderSubtle)

        // Custom Albums
        Text(
          text = "CUSTOM ALBUMS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        if (customAlbums.isEmpty()) {
          Text(
            text = "No custom albums yet. Create one below to organize these items.",
            style = MaterialTheme.typography.bodySmall,
            color = extended.textMuted,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            customAlbums.forEach { album ->
              Surface(
                onClick = { onMoveToAlbum(album.id, album.name) },
                modifier =
                  Modifier.fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("move_dest_album_${album.id}"),
                shape = RoundedCornerShape(14.dp),
                color = extended.surfaceHigh,
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(album.name, style = MaterialTheme.typography.labelLarge)
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = newAlbumName,
            onValueChange = { newAlbumName = it },
            label = { Text("New album name") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("move_new_album_input"),
          )
          Button(
            onClick = {
              if (newAlbumName.isNotBlank()) {
                onCreateAlbumAndMove(newAlbumName.trim())
                newAlbumName = ""
              }
            },
            enabled = newAlbumName.isNotBlank(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("move_create_album_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.CreateNewFolder,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text("Create")
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("move_dialog_cancel_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun BatchDeleteConfirmDialog(
  selectedCount: Int,
  onConfirmDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  val extended = LocalToadColors.current
  val noun = if (selectedCount == 1) "item" else "items"

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("batch_delete_confirm_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Filled.Delete,
        contentDescription = null,
        tint = extended.onDangerContainer,
      )
    },
    title = {
      Text(
        text = "Move $selectedCount $noun to Recycle Bin?",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text =
          "Selected $noun will be removed from your gallery grid and kept in the Recycle Bin for 30 days so you can restore them anytime.",
        style = MaterialTheme.typography.bodyMedium,
        color = extended.textMuted,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmDelete,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("confirm_batch_delete_button"),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = extended.dangerContainer,
            contentColor = extended.onDangerContainer,
          ),
      ) {
        Text("Move to Trash")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("cancel_batch_delete_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}
