package com.example.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GalleryMediaItem
import com.example.ui.components.DecryptedVaultImage
import com.example.ui.theme.LocalToadColors

@Composable
fun HiddenVaultDialog(
  vaultItems: List<GalleryMediaItem>,
  decryptedPreviews: Map<Long, ByteArray>,
  isUnlocked: Boolean,
  biometricsEnabled: Boolean,
  onVerifyPinAsync: (String, (Boolean) -> Unit) -> Unit,
  onBiometricUnlockClick: () -> Unit,
  onUnhideItem: (Long) -> Unit,
  onDeletePermanentItem: (Long) -> Unit,
  onLockNow: () -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)
  val extended = LocalToadColors.current
  var enteredPin by remember { mutableStateOf("") }
  var verifying by remember { mutableStateOf(false) }
  var inspectedItemId by remember(isUnlocked) { mutableStateOf<Long?>(null) }

  val inspectedItem = inspectedItemId?.let { id -> vaultItems.firstOrNull { it.id == id } }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.92f).widthIn(max = 480.dp).padding(vertical = 24.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.size(40.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Column {
              Text("Hidden Vault", style = MaterialTheme.typography.titleMedium)
              Text(
                text =
                  if (isUnlocked) "AES-256-GCM • ${vaultItems.size} private items"
                  else "Salted SHA-256 PIN or Biometrics",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isUnlocked) {
              IconButton(
                onClick = {
                  inspectedItemId = null
                  onLockNow()
                },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("vault_lock_button")
                    .clip(CircleShape)
                    .background(extended.surfaceHigh),
              ) {
                Icon(
                  imageVector = Icons.Filled.Lock,
                  contentDescription = "Lock Hidden Vault",
                )
              }
            }
            IconButton(
              onClick = onClose,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("vault_close_button")
                  .clip(CircleShape)
                  .background(extended.surfaceHigh),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close Hidden Vault",
              )
            }
          }
        }

        if (isUnlocked) {
          if (inspectedItem != null) {
            // Private Decrypted Item Inspector
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                IconButton(
                  onClick = { inspectedItemId = null },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .clip(CircleShape)
                      .background(extended.surfaceHigh),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to vault grid",
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = inspectedItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "Decrypted in memory • ${inspectedItem.dateLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
              ) {
                DecryptedVaultImage(
                  item = inspectedItem,
                  decryptedBytes = decryptedPreviews[inspectedItem.id],
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize(),
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = {
                    onUnhideItem(inspectedItem.id)
                    inspectedItemId = null
                  },
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("vault_restore_item_${inspectedItem.id}"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text("Unhide")
                }

                OutlinedButton(
                  onClick = {
                    onDeletePermanentItem(inspectedItem.id)
                    inspectedItemId = null
                  },
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("vault_delete_item_${inspectedItem.id}"),
                  border = BorderStroke(1.dp, extended.dangerContainer),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text("Delete", color = Color(0xFFFCA5A5))
                }
              }
            }
          } else {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.LockOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Decrypted in session • Tap an item to inspect or unhide",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            if (vaultItems.isEmpty()) {
              Text(
                text = "No encrypted items in vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textMuted,
                modifier = Modifier.padding(vertical = 18.dp),
              )
            } else {
              val rows = vaultItems.chunked(3)
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    rowItems.forEach { item ->
                      Box(
                        modifier =
                          Modifier.weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("vault_item_${item.id}")
                            .clickable { inspectedItemId = item.id }
                      ) {
                        DecryptedVaultImage(
                          item = item,
                          decryptedBytes = decryptedPreviews[item.id],
                          modifier = Modifier.fillMaxSize(),
                        )
                      }
                    }
                    repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                  }
                }
              }
            }
          }
        } else {
          // PIN Indicator Dots
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            repeat(4) { idx ->
              val filled = idx < enteredPin.length
              Box(
                modifier =
                  Modifier.padding(horizontal = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                      if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
              )
            }
          }

          if (biometricsEnabled) {
            Surface(
              onClick = onBiometricUnlockClick,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("vault_biometric_button"),
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.Fingerprint,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                  text = "Unlock with Biometrics",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }

          fun submitPin(pin: String) {
            if (verifying || pin.length != 4) return
            verifying = true
            onVerifyPinAsync(pin) { ok ->
              verifying = false
              if (!ok) enteredPin = ""
            }
          }

          val keys =
            listOf(
              listOf("1", "2", "3"),
              listOf("4", "5", "6"),
              listOf("7", "8", "9"),
              listOf("Clear", "0", "Unlock"),
            )
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { rowKeys ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                rowKeys.forEach { key ->
                  Surface(
                    onClick = {
                      when (key) {
                        "Clear" -> enteredPin = ""
                        "Unlock" -> submitPin(enteredPin)
                        else -> {
                          if (!verifying && enteredPin.length < 4) {
                            val next = enteredPin + key
                            enteredPin = next
                            if (next.length == 4) submitPin(next)
                          }
                        }
                      }
                    },
                    modifier =
                      Modifier.weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("vault_key_${key.lowercase()}"),
                    shape = RoundedCornerShape(16.dp),
                    color =
                      if (key == "Unlock") MaterialTheme.colorScheme.primary
                      else extended.surfaceHigh,
                  ) {
                    Box(
                      modifier = Modifier.padding(vertical = 13.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = key,
                        style =
                          MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color =
                          if (key == "Unlock") MaterialTheme.colorScheme.onPrimary
                          else MaterialTheme.colorScheme.onSurface,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
