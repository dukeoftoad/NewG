package com.example.ui.components.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.ai.FacePersonGroup
import com.example.data.ai.FaceReference
import com.example.ui.theme.LocalToadColors
import com.example.ui.viewmodel.GalleryUiState

/**
 * Ente-inspired person editor: naming/merge, face cover selection,
 * per-face correction, reassignment, split and local correction history.
 */
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
  val existingNames = uiState.peopleGroups
    .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
    .distinct()
    .sorted()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (group.name.isNullOrBlank()) "Name this person" else "Edit person") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          label = { Text("Person name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (existingNames.isNotEmpty()) {
          Text("Or merge with an existing person", style = MaterialTheme.typography.labelMedium)
          existingNames.filterNot { it.equals(group.name, ignoreCase = true) }.forEach { existing ->
            TextButton(onClick = {
              onNamePerson(group, existing)
              onDismiss()
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
                  sourceRevision = uiState.faceIndexRevisions[face.photoId] ?: 0L,
                  modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(photo.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("Face ${face.faceIndex + 1}", style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
                }
                TextButton(onClick = {
                  onSetPersonCover(group, face.photoId, face.faceIndex)
                  onDismiss()
                }) { Text(if (selected) "Current cover" else "Use as cover") }
              }
            }
          }

        if (group.faces.isNotEmpty()) {
          Text("Correct face grouping", style = MaterialTheme.typography.labelMedium)
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
                  sourceRevision = uiState.faceIndexRevisions[face.photoId] ?: 0L,
                  modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(photo?.title ?: "Photo unavailable", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Detected face ${face.faceIndex + 1}", style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
              }
              TextButton(onClick = {
                onExcludeFace(face.photoId, face.faceIndex)
                onDismiss()
              }) { Text("Not this person") }
              var assignExpanded by remember(face.photoId, face.faceIndex) { mutableStateOf(false) }
              Box {
                TextButton(onClick = { assignExpanded = true }) { Text("Assign") }
                DropdownMenu(expanded = assignExpanded, onDismissRequest = { assignExpanded = false }) {
                  uiState.peopleGroups.filter { target ->
                    target.clusterIds.none { it in group.clusterIds }
                  }.forEach { target ->
                    DropdownMenuItem(
                      text = { Text(target.name?.takeIf { it.isNotBlank() } ?: "Unnamed person") },
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
          Text("Separate a merged cluster", style = MaterialTheme.typography.labelMedium)
          group.clusterIds.forEachIndexed { index, clusterId ->
            TextButton(onClick = {
              onNamePerson(group.copy(clusterIds = listOf(clusterId)), null)
              onDismiss()
            }) { Text("Separate cluster ${index + 1}") }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onNamePerson(group, nameInput)
        onDismiss()
      }, enabled = nameInput.isNotBlank()) { Text("Save") }
    },
    dismissButton = {
      Row {
        if (!group.name.isNullOrBlank()) {
          TextButton(onClick = {
            onNamePerson(group, null)
            onDismiss()
          }) { Text("Remove name") }
        }
        TextButton(onClick = { onSetPersonIgnored(group, true); onDismiss() }) { Text("Ignore") }
        TextButton(onClick = onUndoFaceCorrection) { Text("Undo") }
        TextButton(onClick = { showHistory = true }) { Text("History (${uiState.faceCorrectionHistory.size})") }
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
            val availableGroups = uiState.peopleGroups + uiState.unassignedFaceGroups + uiState.ignoredPeopleGroups
            uiState.faceCorrectionHistory.take(20).forEach { entry ->
              val action = when {
                entry.undoneAt != null -> "Undone"
                entry.isExcluded -> "Removed from person"
                entry.nextPersonClusterId != null -> "Assigned to another person"
                else -> "Restored"
              }
              val reference = FaceReference(entry.photoId, entry.faceIndex)
              val photo = uiState.visibleMedia.firstOrNull { it.id == entry.photoId }
              val bounds = availableGroups.firstNotNullOfOrNull { it.faceBounds[reference] }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (photo != null) {
                  FaceThumbnail(
                    item = photo,
                    bounds = bounds,
                    sourceRevision = uiState.faceIndexRevisions[entry.photoId] ?: 0L,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                  )
                } else {
                  Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                  ) { Text("Unavailable", style = MaterialTheme.typography.labelSmall) }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(action, style = MaterialTheme.typography.bodyMedium)
                  Text("Photo: ${photo?.title ?: "Photo unavailable"}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    if (entry.faceIndex >= 0) "Face ${entry.faceIndex + 1}" else "Face unavailable — review manually",
                    style = MaterialTheme.typography.labelSmall,
                    color = extended.textMuted,
                  )
                  Text(
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
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
