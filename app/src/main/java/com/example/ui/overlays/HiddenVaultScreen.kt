package com.example.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MediaType
import com.example.ui.components.DecryptedVaultImage
import com.example.ui.components.pinchToZoomGridColumns
import com.example.ui.theme.LocalToadColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HiddenVaultScreen(
  vaultItems: List<GalleryMediaItem>,
  visibleGalleryMedia: List<GalleryMediaItem>,
  decryptedPreviews: Map<Long, ByteArray>,
  columns: Int,
  isUnlocked: Boolean,
  biometricsEnabled: Boolean,
  onColumnsChange: (Int) -> Unit,
  onVerifyPinAsync: (String, (Boolean) -> Unit) -> Unit,
  onBiometricUnlockClick: () -> Unit,
  onUnhideItem: (Long) -> Unit,
  onUnhideBatch: (List<Long>) -> Unit,
  onDeletePermanentItem: (Long) -> Unit,
  onDeletePermanentBatch: (List<Long>) -> Unit,
  onImportUris: (List<Uri>) -> Unit,
  onImportGalleryItems: (List<Long>) -> Unit,
  onLockNow: () -> Unit,
  onClose: () -> Unit,
) {
  val extended = LocalToadColors.current
  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val effectiveCols = columns.coerceIn(2, 10)
  val compactVaultGrid = effectiveCols >= 6

  var enteredPin by remember { mutableStateOf("") }
  var verifying by remember { mutableStateOf(false) }
  var inspectedItemId by remember(isUnlocked) { mutableStateOf<Long?>(null) }
  var isSelectionMode by remember(isUnlocked) { mutableStateOf(false) }
  var selectedVaultIds by remember(isUnlocked) { mutableStateOf<Set<Long>>(emptySet()) }
  var showImportSheet by remember(isUnlocked) { mutableStateOf(false) }
  var showBatchDeleteConfirm by remember(isUnlocked) { mutableStateOf(false) }

  var isPinching by remember { mutableStateOf(false) }
  var showDensityPill by remember { mutableStateOf(false) }
  var lastCols by remember { mutableIntStateOf(effectiveCols) }

  LaunchedEffect(effectiveCols, isPinching) {
    if (isPinching || effectiveCols != lastCols) {
      lastCols = effectiveCols
      showDensityPill = true
      if (!isPinching) {
        delay(1200)
        showDensityPill = false
      }
    } else {
      showDensityPill = false
    }
  }

  // Keep selected IDs pruned if items leave the vault
  LaunchedEffect(vaultItems) {
    val validIds = vaultItems.map { it.id }.toSet()
    val filtered = selectedVaultIds.filter { it in validIds }.toSet()
    if (filtered != selectedVaultIds) {
      selectedVaultIds = filtered
      if (filtered.isEmpty()) isSelectionMode = false
    }
    if (inspectedItemId != null && inspectedItemId !in validIds) {
      inspectedItemId = null
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
      onResult = { uris ->
        if (uris.isNotEmpty()) {
          onImportUris(uris)
          showImportSheet = false
        }
      },
    )

  BackHandler {
    when {
      showBatchDeleteConfirm -> showBatchDeleteConfirm = false
      showImportSheet -> showImportSheet = false
      inspectedItemId != null -> inspectedItemId = null
      isSelectionMode -> {
        selectedVaultIds = emptySet()
        isSelectionMode = false
      }
      else -> onClose()
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize().testTag("hidden_vault_screen"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Full-Screen Header
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(extended.glassSurface)
              .padding(top = statusTop + 6.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
        ) {
          if (isUnlocked && isSelectionMode) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                IconButton(
                  onClick = {
                    selectedVaultIds = emptySet()
                    isSelectionMode = false
                  },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("vault_clear_selection_button")
                      .clip(CircleShape)
                      .background(extended.surfaceHigh),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Exit vault selection",
                    tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Column {
                  Text(
                    text = "${selectedVaultIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "Tap vault items to batch unhide or shred",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
              }

              val allSelected =
                vaultItems.isNotEmpty() && vaultItems.all { it.id in selectedVaultIds }
              Surface(
                onClick = {
                  selectedVaultIds =
                    if (allSelected) emptySet() else vaultItems.map { it.id }.toSet()
                },
                enabled = vaultItems.isNotEmpty(),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("vault_select_all_button"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
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
                    text = if (allSelected) "Deselect All" else "Select All",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                IconButton(
                  onClick = onClose,
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("vault_close_button")
                      .clip(CircleShape)
                      .background(extended.surfaceHigh),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close Hidden Vault",
                    tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Column {
                  Text("Hidden Vault", style = MaterialTheme.typography.titleMedium)
                  Text(
                    text =
                      if (isUnlocked) "AES-256-GCM • ${vaultItems.size} items • ${effectiveCols}/row"
                      else "Salted SHA-256 PIN or Biometrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
              }

              if (isUnlocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  IconButton(
                    onClick = { showImportSheet = true },
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .testTag("vault_import_button")
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Add,
                      contentDescription = "Import media to vault",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  IconButton(
                    onClick = { isSelectionMode = true },
                    enabled = vaultItems.isNotEmpty(),
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .testTag("vault_select_mode_button")
                        .clip(CircleShape)
                        .background(extended.surfaceHigh),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = "Select vault items",
                      tint =
                        if (vaultItems.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else extended.textDim,
                    )
                  }
                  IconButton(
                    onClick = {
                      inspectedItemId = null
                      selectedVaultIds = emptySet()
                      isSelectionMode = false
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
                      tint = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

        if (!isUnlocked) {
          // Full-Screen PIN & Biometrics Lock Gate
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(28.dp),
                )
              }

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Private AES-256 Workspace",
                  style = MaterialTheme.typography.titleLarge,
                  textAlign = TextAlign.Center,
                )
                Text(
                  text = "Enter your 4-digit Vault PIN to decrypt private photos in memory.",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                  textAlign = TextAlign.Center,
                )
              }

              // PIN Indicator Dots
              Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                repeat(4) { idx ->
                  val filled = idx < enteredPin.length
                  Box(
                    modifier =
                      Modifier.padding(horizontal = 8.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                          if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
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
                      style = MaterialTheme.typography.labelLarge,
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
                          modifier = Modifier.padding(vertical = 12.dp),
                          contentAlignment = Alignment.Center,
                        ) {
                          Text(
                            text = key,
                            style =
                              MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                              ),
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
        } else {
          // Unlocked Full-Page Workspace
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxWidth()
                .pinchToZoomGridColumns(
                  currentColumns = effectiveCols,
                  minColumns = 2,
                  maxColumns = 10,
                  onColumnsChange = onColumnsChange,
                  onPinchActiveChange = { isPinching = it },
                )
          ) {
            val gridSpacing = if (compactVaultGrid) 3.dp else 6.dp
            val corner = if (compactVaultGrid) 6.dp else 14.dp
            val rows = remember(vaultItems, effectiveCols) { vaultItems.chunked(effectiveCols) }

            LazyColumn(
              modifier = Modifier.fillMaxSize().testTag("vault_grid_list"),
              contentPadding =
                PaddingValues(
                  start = 12.dp,
                  end = 12.dp,
                  top = 12.dp,
                  bottom = if (selectedVaultIds.isNotEmpty()) 110.dp else 40.dp,
                ),
              verticalArrangement = Arrangement.spacedBy(gridSpacing),
            ) {
              item(key = "vault_session_banner") {
                Surface(
                  modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
              }

              if (vaultItems.isEmpty()) {
                item(key = "vault_empty_state") {
                  Column(
                    modifier =
                      Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(64.dp)
                          .clip(CircleShape)
                          .background(extended.surfaceHigh),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                      )
                    }
                    Text(
                      text = "No encrypted items in vault",
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text =
                        "Import private photos directly via Photo Picker or move existing items from your gallery.",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                      textAlign = TextAlign.Center,
                    )
                    Button(
                      onClick = { showImportSheet = true },
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("vault_empty_import_button"),
                      shape = CircleShape,
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      Text("Import to Vault")
                    }
                  }
                }
              } else {
                items(items = rows, key = { row -> "vault_row_${row.first().id}" }, contentType = { "vault_row" }) { rowItems ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                  ) {
                    rowItems.forEach { item ->
                      val isSelected = item.id in selectedVaultIds
                      Box(
                        modifier =
                          Modifier.weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(corner))
                            .border(
                              width =
                                if (isSelected) (if (compactVaultGrid) 2.dp else 2.5.dp)
                                else (if (compactVaultGrid) 0.5.dp else 1.dp),
                              color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else extended.borderSubtle,
                              shape = RoundedCornerShape(corner),
                            )
                            .testTag("vault_item_${item.id}")
                            .combinedClickable(
                              onClick = {
                                if (isSelectionMode) {
                                  val next =
                                    if (isSelected) selectedVaultIds - item.id
                                    else selectedVaultIds + item.id
                                  selectedVaultIds = next
                                  if (next.isEmpty()) isSelectionMode = false
                                } else {
                                  inspectedItemId = item.id
                                }
                              },
                              onLongClick = {
                                isSelectionMode = true
                                selectedVaultIds = selectedVaultIds + item.id
                              },
                            )
                      ) {
                        DecryptedVaultImage(
                          item = item,
                          decryptedBytes = decryptedPreviews[item.id],
                          modifier = Modifier.fillMaxSize(),
                        )
                        if (isSelected) {
                          Box(
                            modifier =
                              Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                          )
                        }
                        if (isSelectionMode) {
                          Box(
                            modifier =
                              Modifier.align(Alignment.TopStart)
                                .padding(if (compactVaultGrid) 3.dp else 6.dp)
                                .size(if (compactVaultGrid) 16.dp else 22.dp)
                                .clip(CircleShape)
                                .background(
                                  if (isSelected) MaterialTheme.colorScheme.primary
                                  else Color.Black.copy(alpha = 0.52f)
                                )
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                          ) {
                            if (isSelected) {
                              Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (compactVaultGrid) 10.dp else 14.dp),
                              )
                            }
                          }
                        } else if (!compactVaultGrid && item.mediaType == MediaType.VIDEO) {
                          Row(
                            modifier =
                              Modifier.align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                          ) {
                            Icon(
                              imageVector = Icons.Filled.PlayArrow,
                              contentDescription = null,
                              tint = Color.White,
                              modifier = Modifier.size(11.dp),
                            )
                            Text(
                              text = item.durationLabel ?: "VID",
                              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                              color = Color.White,
                            )
                          }
                        } else if (!compactVaultGrid && item.mediaType == MediaType.GIF) {
                          Box(
                            modifier =
                              Modifier.align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                          ) {
                            Text(
                              text = "GIF",
                              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                              color = Color(0xFF6EE7B7),
                            )
                          }
                        }
                      }
                    }
                    repeat((effectiveCols - rowItems.size).coerceAtLeast(0)) {
                      Spacer(modifier = Modifier.weight(1f))
                    }
                  }
                }
              }
            }

            // Pinch Density Feedback Pill
            androidx.compose.animation.AnimatedVisibility(
              visible = showDensityPill,
              enter = fadeIn(),
              exit = fadeOut(),
              modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = extended.glassSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 6.dp,
              ) {
                Text(
                  text = "$effectiveCols per row",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
              }
            }

            // Floating Multi-Select Batch Action Bar
            androidx.compose.animation.AnimatedVisibility(
              visible = isSelectionMode && selectedVaultIds.isNotEmpty(),
              enter = slideInVertically { it } + fadeIn(),
              exit = slideOutVertically { it } + fadeOut(),
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
              Surface(
                modifier =
                  Modifier.fillMaxWidth().widthIn(max = 520.dp).testTag("vault_batch_action_bar"),
                shape = RoundedCornerShape(24.dp),
                color = extended.glassSurface,
                border = BorderStroke(1.dp, extended.borderStrong),
                shadowElevation = 10.dp,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Button(
                    onClick = {
                      onUnhideBatch(selectedVaultIds.toList())
                      selectedVaultIds = emptySet()
                      isSelectionMode = false
                    },
                    modifier =
                      Modifier.weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("vault_batch_unhide_button"),
                    shape = RoundedCornerShape(16.dp),
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
                    Text(
                      text = "Unhide (${selectedVaultIds.size})",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  Button(
                    onClick = { showBatchDeleteConfirm = true },
                    modifier =
                      Modifier.weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("vault_batch_delete_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = extended.dangerContainer,
                        contentColor = Color(0xFFFCA5A5),
                      ),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.DeleteForever,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                      text = "Delete (${selectedVaultIds.size})",
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

      // Full-Screen Decrypted Zoom + Swipe Viewer
      val activeItemId = inspectedItemId
      if (isUnlocked && activeItemId != null && vaultItems.any { it.id == activeItemId }) {
        HiddenVaultViewerOverlay(
          vaultItems = vaultItems,
          decryptedPreviews = decryptedPreviews,
          initialItemId = activeItemId,
          onUnhideItem = { id ->
            onUnhideItem(id)
            if (vaultItems.size <= 1) inspectedItemId = null
          },
          onDeletePermanentItem = { id ->
            onDeletePermanentItem(id)
            if (vaultItems.size <= 1) inspectedItemId = null
          },
          onClose = { inspectedItemId = null },
        )
      }

      // Direct Import Sheet (Photo Picker + In-App Gallery Picker)
      if (isUnlocked && showImportSheet) {
        HiddenVaultImportSheet(
          visibleGalleryMedia = visibleGalleryMedia,
          onPickFromDevicePhotoPicker = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          onImportFromGallery = onImportGalleryItems,
          onDismiss = { showImportSheet = false },
        )
      }

      // Batch Permanent Delete Confirmation
      if (showBatchDeleteConfirm) {
        val deleteCount = selectedVaultIds.size
        AlertDialog(
          onDismissRequest = { showBatchDeleteConfirm = false },
          modifier = Modifier.testTag("vault_batch_delete_confirm_dialog"),
          title = { Text("Delete $deleteCount Private Items?") },
          text = {
            Text(
              "Selected encrypted vault files will be permanently shredded and cannot be recovered."
            )
          },
          confirmButton = {
            Button(
              onClick = {
                onDeletePermanentBatch(selectedVaultIds.toList())
                selectedVaultIds = emptySet()
                isSelectionMode = false
                showBatchDeleteConfirm = false
              },
              modifier = Modifier.testTag("vault_confirm_batch_delete_button"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
              Text("Delete Forever")
            }
          },
          dismissButton = {
            TextButton(
              onClick = { showBatchDeleteConfirm = false },
              modifier = Modifier.testTag("vault_cancel_batch_delete_button"),
            ) {
              Text("Cancel")
            }
          },
        )
      }
    }
  }
}
