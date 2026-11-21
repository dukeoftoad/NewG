package com.toadgallery.app.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.ui.components.GalleryMediaImage
import com.toadgallery.app.ui.theme.LocalToadColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVaultImportSheet(
  visibleGalleryMedia: List<GalleryMediaItem>,
  onPickFromDevicePhotoPicker: () -> Unit,
  onImportFromGallery: (List<Long>) -> Unit,
  onDismiss: () -> Unit,
) {
  val extended = LocalToadColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showGalleryPicker by remember { mutableStateOf(false) }
  var selectedGalleryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 20.dp)
          .testTag("vault_import_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!showGalleryPicker) {
        Text("Import to Hidden Vault", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "Imported photos and videos are encrypted with AES-256-GCM inside app-private storage.",
          style = MaterialTheme.typography.bodySmall,
          color = extended.textMuted,
        )

        Surface(
          onClick = onPickFromDevicePhotoPicker,
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("vault_import_photo_picker"),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Device Photo Picker",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = "Select photos or videos privately with zero extra storage permissions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
              )
            }
          }
        }

        Surface(
          onClick = { showGalleryPicker = true },
          enabled = visibleGalleryMedia.isNotEmpty(),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("vault_import_from_gallery"),
          shape = RoundedCornerShape(18.dp),
          color = extended.surfaceHigh,
          border = BorderStroke(1.dp, extended.borderSubtle),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surface),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Pick from Gallery",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  if (visibleGalleryMedia.isEmpty()) "No visible items available"
                  else "Encrypt & hide from ${visibleGalleryMedia.size} visible items",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
              )
            }
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Pick from Gallery", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${selectedGalleryIds.size} selected to encrypt & hide",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          TextButton(onClick = { showGalleryPicker = false }) { Text("Back") }
        }

        val rows = remember(visibleGalleryMedia) { visibleGalleryMedia.chunked(3) }
        LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp),
          contentPadding = PaddingValues(vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(rows) { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              rowItems.forEach { item ->
                val isSelected = item.id in selectedGalleryIds
                Box(
                  modifier =
                    Modifier.weight(1f)
                      .aspectRatio(1f)
                      .clip(RoundedCornerShape(10.dp))
                      .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color =
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else extended.borderSubtle,
                        shape = RoundedCornerShape(10.dp),
                      )
                      .testTag("vault_gallery_pick_${item.id}")
                      .clickable {
                        selectedGalleryIds =
                          if (isSelected) selectedGalleryIds - item.id
                          else selectedGalleryIds + item.id
                      }
                ) {
                  GalleryMediaImage(item = item, modifier = Modifier.fillMaxSize())
                  if (isSelected) {
                    Box(
                      modifier =
                        Modifier.fillMaxSize()
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                  }
                  Box(
                    modifier =
                      Modifier.align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else Color.Black.copy(alpha = 0.48f)
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                      )
                    }
                  }
                }
              }
              repeat((3 - rowItems.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Button(
          onClick = {
            if (selectedGalleryIds.isNotEmpty()) {
              onImportFromGallery(selectedGalleryIds.toList())
              onDismiss()
            }
          },
          enabled = selectedGalleryIds.isNotEmpty(),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("vault_confirm_gallery_import_button"),
          shape = RoundedCornerShape(16.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text(
            text =
              if (selectedGalleryIds.isEmpty()) "Select items to move"
              else "Encrypt & Move (${selectedGalleryIds.size})",
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
