package com.toadgallery.app.ui.components.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.ai.FacePersonGroup
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.viewmodel.GalleryUiState

/**
 * Ente-inspired person editor: naming/merge, face cover selection,
 * per-face correction, reassignment, split and local correction history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonManagementSheet(
  group: FacePersonGroup?,
  uiState: GalleryUiState,
  onDismiss: () -> Unit,
  onNamePerson: (FacePersonGroup, String?) -> Unit,
  onExcludeFace: (Long, Int) -> Unit,
  onSetPersonCover: (FacePersonGroup, Long, Int) -> Unit,
  onSetPersonIgnored: (FacePersonGroup, Boolean) -> Unit,
  onAssignFace: (Long, Int, FacePersonGroup) -> Unit,
  onUndoFaceCorrection: () -> Unit,
) {
  if (group == null) return
  val extended = LocalToadColors.current
  var nameInput by remember(group.id, group.name) { mutableStateOf(group.name.orEmpty()) }
  var showHistory by remember { mutableStateOf(false) }

  val mediaById = remember(uiState.visibleMedia) { uiState.visibleMedia.associateBy { it.id } }
  val existingNames =
    remember(uiState.peopleGroups, group.name) {
      uiState.peopleGroups
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
        .filterNot { it.equals(group.name, ignoreCase = true) }
        .distinct()
        .sorted()
    }
  val coverCandidates =
    remember(group.faces, group.coverPhotoId, group.coverFaceIndex) {
      group.faces
        .sortedByDescending {
          it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
        }
        .take(16)
    }
  val assignableTargets =
    remember(uiState.peopleGroups, group.clusterIds) {
      uiState.peopleGroups.filter { target ->
        target.clusterIds.none { it in group.clusterIds }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (group.name.isNullOrBlank()) "Name this person" else "Edit person")
        Text(
          text = "${group.photoIds.size} photos • ${group.faceCount} detected faces",
          style = MaterialTheme.typography.bodySmall,
          color = extended.textMuted,
        )
      }
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          label = { Text("Person name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (existingNames.isNotEmpty()) {
          Text(
            "Or merge with an existing person",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            existingNames.forEach { existing ->
              TextButton(
                onClick = {
                  onNamePerson(group, existing)
                  onDismiss()
                }
              ) {
                Text(existing)
              }
            }
          }
        }

        HorizontalDivider(color = extended.borderSubtle)

        Text(
          "Choose a face as cover",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        coverCandidates.forEach { face ->
          val photo = mediaById[face.photoId]
          if (photo != null) {
            val selected =
              face.photoId == group.coverPhotoId && face.faceIndex == group.coverFaceIndex
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else extended.surfaceHigh,
              border =
                BorderStroke(
                  1.dp,
                  if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                FaceThumbnail(
                  item = photo,
                  bounds = group.faceBounds[face],
                  sourceRevision = uiState.faceIndexRevisions[face.photoId] ?: 0L,
                  modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    photo.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    "Face ${face.faceIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extended.textMuted,
                  )
                }
                TextButton(
                  onClick = {
                    onSetPersonCover(group, face.photoId, face.faceIndex)
                    onDismiss()
                  }
                ) {
                  Text(if (selected) "Current cover" else "Use as cover")
                }
              }
            }
          }
        }

        if (group.faces.isNotEmpty()) {
          HorizontalDivider(color = extended.borderSubtle)
          Text(
            "Correct face grouping",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          group.faces.take(10).forEach { face ->
            val photo = mediaById[face.photoId]
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (photo != null) {
                FaceThumbnail(
                  item = photo,
                  bounds = group.faceBounds[face],
                  sourceRevision = uiState.faceIndexRevisions[face.photoId] ?: 0L,
                  modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
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
              TextButton(
                onClick = {
                  onExcludeFace(face.photoId, face.faceIndex)
                  onDismiss()
                }
              ) {
                Text("Not this person")
              }
              var assignExpanded by
                remember(face.photoId, face.faceIndex) { mutableStateOf(false) }
              Box {
                TextButton(onClick = { assignExpanded = true }) { Text("Assign") }
                DropdownMenu(
                  expanded = assignExpanded,
                  onDismissRequest = { assignExpanded = false },
                ) {
                  assignableTargets.forEach { target ->
                    DropdownMenuItem(
                      text = {
                        Text(target.name?.takeIf { it.isNotBlank() } ?: "Unnamed person")
                      },
                      onClick = {
                        onAssignFace(face.photoId, face.faceIndex, target)
                        assignExpanded = false
                        onDismiss()
                      },
                    )
                  }
                }
              }
            }
          }
        }

        if (group.clusterIds.size > 1) {
          HorizontalDivider(color = extended.borderSubtle)
          Text(
            "Separate a merged cluster",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          group.clusterIds.forEachIndexed { index, clusterId ->
            TextButton(
              onClick = {
                onNamePerson(group.copy(clusterIds = listOf(clusterId)), null)
                onDismiss()
              }
            ) {
              Text("Separate cluster ${index + 1}")
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onNamePerson(group, nameInput)
          onDismiss()
        },
        enabled = nameInput.isNotBlank(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      Row {
        if (!group.name.isNullOrBlank()) {
          TextButton(
            onClick = {
              onNamePerson(group, null)
              onDismiss()
            }
          ) {
            Text("Remove name")
          }
        }
        TextButton(
          onClick = {
            onSetPersonIgnored(group, true)
            onDismiss()
          }
        ) {
          Text("Ignore")
        }
        TextButton(onClick = onUndoFaceCorrection) { Text("Undo") }
        TextButton(onClick = { showHistory = true }) {
          Text("History (${uiState.faceCorrectionHistory.size})")
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
  )

  if (showHistory) {
    AlertDialog(
      onDismissRequest = { showHistory = false },
      title = { Text("Face correction history") },
      text = {
        Column(
          modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (uiState.faceCorrectionHistory.isEmpty()) {
            Text("No face corrections yet")
          } else {
            uiState.faceCorrectionHistory.take(20).forEach { entry ->
              val action =
                when {
                  entry.undoneAt != null -> "Undone"
                  entry.isExcluded -> "Removed from person"
                  entry.nextPersonClusterId != null -> "Assigned to another person"
                  else -> "Restored"
                }
              val photoTitle = mediaById[entry.photoId]?.title ?: "Photo #${entry.photoId}"
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = extended.surfaceHigh,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(
                    "$action • $photoTitle",
                    style = MaterialTheme.typography.labelMedium,
                  )
                  Text(
                    java.text.DateFormat.getDateTimeInstance()
                      .format(java.util.Date(entry.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHistory = false }) { Text("Close") }
      },
    )
  }
}
