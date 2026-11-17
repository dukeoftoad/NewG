package com.toadgallery.app.ui.screens

import com.toadgallery.app.ui.update.VersionRow
import com.toadgallery.app.ui.update.UpdateViewModel
import com.toadgallery.app.ui.update.UpdatePanel
import com.toadgallery.app.domain.AppIcon
import com.toadgallery.app.BuildConfig
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toadgallery.app.data.ai.FaceModelManager
import com.toadgallery.app.data.ai.FacePersonGroup
import com.toadgallery.app.data.ai.orderFaceAssignmentTargets
import com.toadgallery.app.ui.components.people.FaceThumbnail
import com.toadgallery.app.ui.components.people.PersonManagementSheet
import com.toadgallery.app.data.model.AppearanceMode
import com.toadgallery.app.data.model.ThemePalette
import com.toadgallery.app.data.model.VaultAutoLock
import com.toadgallery.app.domain.AppFont
import com.toadgallery.app.ui.components.restartApp
import com.toadgallery.app.ui.components.StorageBreakdownBar
import androidx.core.content.ContextCompat
import com.toadgallery.app.ui.components.DrawablePainter
import com.toadgallery.app.ui.components.ToadSwitch
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.viewmodel.GalleryUiState
import com.toadgallery.app.ui.viewmodel.GalleryViewModel
import com.toadgallery.app.ui.viewmodel.SettingsUiState
import com.toadgallery.app.ui.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsSubPageHeader(
  title: String,
  badge: String,
  onBack: () -> Unit,
) {
  val extended = LocalToadColors.current
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

  Column(modifier = Modifier.fillMaxWidth().background(extended.glassSurface)) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(top = statusBarTop + 10.dp, bottom = 10.dp, start = 12.dp, end = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      IconButton(
        onClick = onBack,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .testTag("settings_subpage_back_button")
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back to Settings",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "SETTINGS",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Surface(shape = CircleShape, color = extended.surfaceHigh) {
        Text(
          text = badge,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = extended.textMuted,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
    HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
  }
}

@Composable
internal fun SettingsSubAiEnginePage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
) {
  val extended = LocalToadColors.current
  val prefs = uiState.preferences
  val strictnessLabel =
    when {
      prefs.clusteringThreshold <= 0.18f -> "Very strict (splits lookalikes)"
      prefs.clusteringThreshold >= 0.30f -> "Relaxed (merges lighting)"
      else -> "Balanced (default 0.24)"
    }

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "AI Engine & Models",
      badge = "On-Device",
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("settings_ai_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Background Indexer Card
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "BACKGROUND INDEXER",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                  Text(
                    text = if (uiState.aiIndexing) "Running" else "Idle • Protected",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              Surface(shape = RoundedCornerShape(16.dp), color = extended.surfaceHigh) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(8.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                      text = uiState.aiStatusText,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Text(
                    text = "${uiState.aiProgress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              LinearProgressIndicator(
                progress = { uiState.aiProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = extended.surfaceHighest,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = viewModel::runOrPauseIndex,
                  modifier =
                    Modifier.weight(1f).minimumInteractiveComponentSize().testTag("ai_reindex_btn"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Icon(
                    imageVector =
                      if (uiState.aiIndexing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(if (uiState.aiIndexing) "Pause Index" else "Re-index Library")
                }

                OutlinedButton(
                  onClick = viewModel::resetAiIndex,
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("ai_reset_queue_btn"),
                  border = BorderStroke(1.dp, extended.borderStrong),
                ) {
                  Text("Reset Queue", color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }
        }

        // Face Clustering Threshold Card
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Face clustering threshold", style = MaterialTheme.typography.labelLarge)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                  Text(
                    text = String.format(Locale.US, "%.2f", prefs.clusteringThreshold),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              Slider(
                value = prefs.clusteringThreshold,
                onValueChange = viewModel::setClusteringThreshold,
                valueRange = 0.10f..0.40f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = extended.surfaceHighest,
                  ),
                modifier = Modifier.testTag("clustering_threshold_slider"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  "0.10 Stricter",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
                Text(
                  strictnessLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  "0.40 Broader",
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.textMuted,
                )
              }
            }
          }
        }

        // Model Packs & Developer Diagnostics
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "ON-DEVICE MODEL PACKS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )

              // Ente face detector + MobileFaceNet pack
              val facePack = uiState.faceModelState
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = extended.surfaceHigh,
                border = BorderStroke(1.dp, extended.borderSubtle),
                modifier = Modifier.testTag("face_model_pack_card"),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        "Ente Face Detection & Embeddings",
                        style = MaterialTheme.typography.labelLarge,
                      )
                      Text(
                        "YOLOv5Face Small + MobileFaceNet • ~35.9 MiB",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textMuted,
                      )
                    }
                    Surface(
                      shape = CircleShape,
                      color =
                        if (facePack.isInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                      Text(
                        text =
                          when {
                            facePack.isInstalled -> "Installed"
                            facePack.isDownloading -> "${facePack.progressPercent}%"
                            facePack.status == com.toadgallery.app.data.ai.FaceModelStatus.ERROR -> "Error"
                            else -> "On-Demand"
                          },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color =
                          if (facePack.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer
                          else extended.textMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }
                  if (facePack.isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      LinearProgressIndicator(
                        progress = { facePack.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                      )
                      Text(
                        text = facePack.progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  } else if (!facePack.errorMessage.isNullOrBlank()) {
                    Text(
                      text = facePack.errorMessage,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.error,
                    )
                  } else if (facePack.isInstalled) {
                    Text(
                      text = facePack.progressLabel,
                      style = MaterialTheme.typography.labelSmall,
                      color = extended.textMuted,
                    )
                  }
                  Button(
                    onClick = viewModel::handleFacePackAction,
                    modifier = Modifier.testTag("toggle_face_pack_btn"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor =
                          if (facePack.isInstalled) extended.dangerContainer
                          else MaterialTheme.colorScheme.primary,
                        contentColor =
                          if (facePack.isInstalled) extended.onDangerContainer
                          else MaterialTheme.colorScheme.onPrimary,
                      ),
                  ) {
                    Text(
                      text =
                        when {
                          facePack.isInstalled -> "Delete Face Pack"
                          facePack.isDownloading -> "Cancel Download"
                          else -> "Download (~35.9 MiB)"
                        },
                      style = MaterialTheme.typography.labelMedium,
                    )
                  }
                }
              }

              var showFaceModelDetails by remember { mutableStateOf(false) }
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extended.borderSubtle),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth().clickable {
                        showFaceModelDetails = !showFaceModelDetails
                      },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      "What do these models do?",
                      style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                      imageVector =
                        if (showFaceModelDetails) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                      contentDescription =
                        if (showFaceModelDetails) "Hide model details"
                        else "Show model details",
                      tint = extended.textMuted,
                    )
                  }
                  if (showFaceModelDetails) {
                    Text(
                      "1. YOLOv5Face Small — scans each photo and finds face locations. " +
                        "It proposes face boxes and five facial landmarks; non-maximum " +
                        "suppression removes duplicate overlapping detections.",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                    Text(
                      "2. Face alignment — uses the detected landmarks to rotate, scale " +
                        "and align each crop so faces can be compared more consistently. " +
                        "This step is preprocessing; it is not a separate downloaded model.",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                    Text(
                      "3. MobileFaceNet — converts each aligned face into a compact " +
                        "192-D L2-normalized embedding. Similar-looking faces have embeddings " +
                        "with small cosine distance; the vector stays strictly on-device.",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                    HorizontalDivider(color = extended.borderSubtle)
                    Text(
                      "How NewG uses them",
                      style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                      "Use Re-index Library above to scan eligible local photos. " +
                        "NewG groups recurring faces using centroid + median-member cosine " +
                        "distance, powers People albums, and supports cover selection, " +
                        "merge/split, face assignment/exclusion, and undo history.",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                    Text(
                      "Current status: Both ONNX models, stable per-photo face IDs, " +
                        "People clustering, suggested face matches, and local Room undo " +
                        "history run 100% offline on-device.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      "Model format: ONNX • Runtime: ONNX Runtime Android • " +
                        "Source: Ente FlutterFace demo (GPL-3.0 project).",
                      style = MaterialTheme.typography.labelSmall,
                      color = extended.textMuted,
                    )
                  }
                }
              }

              // Semantic CLIP Pack
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = extended.surfaceHigh,
                border = BorderStroke(1.dp, extended.borderSubtle),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        "Semantic Search (MobileCLIP)",
                        style = MaterialTheme.typography.labelLarge,
                      )
                      Text(
                        "~213 MB • Image + Text Encoders",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textMuted,
                      )
                    }
                    Surface(
                      shape = CircleShape,
                      color =
                        if (prefs.semanticModelInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                      Text(
                        text =
                          when {
                            prefs.semanticModelInstalled -> "Installed"
                            uiState.semanticDownloading -> "${uiState.semanticDownloadProgress}%"
                            else -> "Available"
                          },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color =
                          if (prefs.semanticModelInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                          else extended.textMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }

                  if (uiState.semanticDownloading) {
                    LinearProgressIndicator(
                      progress = { uiState.semanticDownloadProgress / 100f },
                      modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                  }

                  Button(
                    onClick = viewModel::handleSemanticPackAction,
                    modifier = Modifier.testTag("toggle_semantic_pack_btn"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor =
                          if (prefs.semanticModelInstalled) extended.dangerContainer
                          else MaterialTheme.colorScheme.primary,
                        contentColor =
                          if (prefs.semanticModelInstalled) extended.onDangerContainer
                          else MaterialTheme.colorScheme.onPrimary,
                      ),
                  ) {
                    Text(
                      text =
                        when {
                          prefs.semanticModelInstalled -> "Delete Semantic Pack"
                          uiState.semanticDownloading -> "Downloading..."
                          else -> "Download (~213 MB)"
                        },
                      style = MaterialTheme.typography.labelMedium,
                    )
                  }
                }
              }
            }
          }
        }

        // Depth Anything V2 Small int8 ONNX Pack (bokeh-depth-dav2)
        item {
          val depthPack = uiState.depthModelState
          val facePack = uiState.faceModelState
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = extended.surfaceHigh,
                border = BorderStroke(1.dp, extended.borderSubtle),
                modifier = Modifier.testTag("depth_model_pack_card"),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        "Bokeh Depth (bokeh-depth-dav2)",
                        style = MaterialTheme.typography.labelLarge,
                      )
                      Text(
                        "~27.3 MB • Depth Anything V2 Small INT8 ONNX",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textMuted,
                      )
                    }
                    Surface(
                      shape = CircleShape,
                      color =
                        if (depthPack.isInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                      Text(
                        text =
                          when {
                            depthPack.isInstalled -> "Installed"
                            depthPack.isDownloading -> "${depthPack.progressPercent}%"
                            else -> "On-Demand"
                          },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color =
                          if (depthPack.isInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                          else extended.textMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }

                  if (depthPack.isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      LinearProgressIndicator(
                        progress = { depthPack.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                      )
                      Text(
                        text = depthPack.progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  } else if (!depthPack.errorMessage.isNullOrBlank()) {
                    Text(
                      text = depthPack.errorMessage,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.error,
                    )
                  }

                  Button(
                    onClick = viewModel::handleDepthPackAction,
                    modifier = Modifier.testTag("toggle_depth_pack_btn"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor =
                          if (depthPack.isInstalled) extended.dangerContainer
                          else MaterialTheme.colorScheme.primary,
                        contentColor =
                          if (depthPack.isInstalled) extended.onDangerContainer
                          else MaterialTheme.colorScheme.onPrimary,
                      ),
                  ) {
                    Text(
                      text =
                        when {
                          depthPack.isInstalled -> "Delete bokeh-depth-dav2"
                          depthPack.isDownloading -> "Cancel Download"
                          else -> "Download (~27.3 MB)"
                        },
                      style = MaterialTheme.typography.labelMedium,
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

@Composable
internal fun SettingsSubVaultPage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
  onOpenVaultModal: () -> Unit,
) {
  val extended = LocalToadColors.current
  val prefs = uiState.preferences
  var showChangePinDialog by remember { mutableStateOf(false) }
  var currentPinDraft by remember { mutableStateOf("") }
  var newPinDraft by remember { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "Hidden Vault & Security",
      badge = "AES-256",
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Biometric unlock", style = MaterialTheme.typography.labelLarge)
                  Text(
                    "Allow quick biometric unlock for Hidden Vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
                ToadSwitch(
                  checked = prefs.vaultBiometrics,
                  onCheckedChange = viewModel::toggleVaultBiometrics,
                  contentDesc = "Toggle biometric vault unlock",
                  modifier = Modifier.testTag("switch_vault_biometrics"),
                )
              }

              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-lock timer", style = MaterialTheme.typography.labelLarge)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  VaultAutoLock.entries.forEach { option ->
                    val selected = prefs.vaultAutoLock == option
                    Surface(
                      onClick = { viewModel.setVaultAutoLock(option) },
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .testTag("autolock_${option.name.lowercase()}"),
                      shape = RoundedCornerShape(14.dp),
                      color =
                        if (selected) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
                    ) {
                      Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        Text(
                          text = option.label,
                          style = MaterialTheme.typography.labelSmall,
                          color =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else extended.textMuted,
                          textAlign = TextAlign.Center,
                        )
                      }
                    }
                  }
                }
              }

              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = onOpenVaultModal,
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text("Open Vault")
                }
                OutlinedButton(
                  onClick = {
                    currentPinDraft = ""
                    newPinDraft = ""
                    showChangePinDialog = true
                  },
                  modifier =
                    Modifier.weight(1f).minimumInteractiveComponentSize().testTag("change_pin_btn"),
                  border = BorderStroke(1.dp, extended.borderStrong),
                ) {
                  Text("Change PIN", color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showChangePinDialog) {
    AlertDialog(
      onDismissRequest = { showChangePinDialog = false },
      title = { Text("Update Vault PIN", style = MaterialTheme.typography.titleMedium) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Protected with salted SHA-256 verification. Default initial PIN is 1234.",
            style = MaterialTheme.typography.bodySmall,
            color = extended.textMuted,
          )
          OutlinedTextField(
            value = currentPinDraft,
            onValueChange = { input ->
              if (input.length <= 4 && input.all { it.isDigit() }) currentPinDraft = input
            },
            label = { Text("Current 4-digit PIN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("current_pin_input"),
          )
          OutlinedTextField(
            value = newPinDraft,
            onValueChange = { input ->
              if (input.length <= 4 && input.all { it.isDigit() }) newPinDraft = input
            },
            label = { Text("New 4-digit PIN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("new_pin_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateVaultPin(currentPinDraft, newPinDraft) { ok ->
              if (ok) showChangePinDialog = false
            }
          },
          enabled = currentPinDraft.length == 4 && newPinDraft.length == 4,
        ) {
          Text("Save PIN")
        }
      },
      dismissButton = {
        TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
internal fun SettingsSubStoragePage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
) {
  val extended = LocalToadColors.current
  val prefs = uiState.preferences
  val faceInstalled = uiState.faceModelState.isInstalled || prefs.faceModelInstalled
  val depthInstalled = uiState.depthModelState.isInstalled || prefs.depthModelInstalled
  val mlModelsMb =
    (if (faceInstalled) 35.9f else 0f) +
      (if (prefs.semanticModelInstalled) 213.0f else 0f) +
      (if (depthInstalled) 27.3f else 0f)
  val totalMb = 14.2f + mlModelsMb + prefs.mlIndexSizeMb + prefs.cacheSizeMb

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "Storage Manager",
      badge = String.format(Locale.US, "%.1f MB", totalMb),
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "APP-PRIVATE STORAGE",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = String.format(Locale.US, "%.1f MB total", totalMb),
                  style = MaterialTheme.typography.labelMedium,
                )
              }

              StorageBreakdownBar(
                faceModelInstalled = faceInstalled,
                semanticModelInstalled = prefs.semanticModelInstalled,
                depthModelInstalled = depthInstalled,
                mlIndexMb = prefs.mlIndexSizeMb,
                cacheMb = prefs.cacheSizeMb,
              )

              StorageLegendRow(Color(0xFF38BDF8), "Photo catalogue database", "14.2 MB")
              StorageLegendRow(
                MaterialTheme.colorScheme.primary,
                "Downloaded ML models",
                String.format(Locale.US, "%.1f MB", mlModelsMb),
              )
              StorageLegendRow(
                Color(0xFFA78BFA),
                "ML index store (faces + semantic)",
                String.format(Locale.US, "%.1f MB", prefs.mlIndexSizeMb),
              )
              StorageLegendRow(
                Color(0xFFFBBF24),
                "Thumbnail & temp cache",
                String.format(Locale.US, "%.1f MB", prefs.cacheSizeMb),
              )

              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = viewModel::clearCache,
                  modifier =
                    Modifier.weight(1f).minimumInteractiveComponentSize().testTag("clear_cache_btn"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Text("Clear Cache")
                }
                OutlinedButton(
                  onClick = viewModel::clearMlIndexStore,
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("reset_ml_index_btn"),
                  border = BorderStroke(1.dp, extended.borderStrong),
                ) {
                  Text("Reset ML Index", color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StorageLegendRow(dotColor: Color, label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
      Text(label, style = MaterialTheme.typography.bodySmall)
    }
    Text(value, style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
internal fun SettingsSubAboutPage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
  updateViewModel: UpdateViewModel =
    viewModel(factory = UpdateViewModel.factory(LocalContext.current.applicationContext)),
) {
  val extended = LocalToadColors.current
  val context = LocalContext.current

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "About toadGALLERY",
      badge = "v${BuildConfig.VERSION_NAME}",
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "t",
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text("toadGALLERY", style = MaterialTheme.typography.titleLarge)
              Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text =
                  "Local-first Android photo gallery with on-device face clustering, semantic Magic Search, encrypted Hidden Vault, and non-destructive editing.",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
                textAlign = TextAlign.Center,
              )
            }
          }
        }

        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Updates", style = MaterialTheme.typography.titleMedium)
              Text(
                "toadGALLERY contacts GitHub Releases once per app open (silently, in the " +
                  "background) and whenever you tap the button below. Nothing about this " +
                  "device is sent, and nothing downloads without a tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textMuted,
              )
              Spacer(modifier = Modifier.height(6.dp))
              VersionRow("Installed", BuildConfig.VERSION_NAME)
              val updateState by updateViewModel.state.collectAsStateWithLifecycle()
              UpdatePanel(
                update = updateState,
                onCheck = updateViewModel::checkForUpdates,
                onDownloadInstall = { updateViewModel.downloadAndInstall(context) },
                onDismiss = updateViewModel::clearUpdateState,
              )
            }
          }
        }

      }
    }
  }
}


@Composable
internal fun SettingsSubAppearancePage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
) {
  val extended = LocalToadColors.current
  val context = LocalContext.current
  val prefs = uiState.preferences
  val selectedIcon = prefs.appIcon
  var pendingIconRestart by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "Appearance",
      badge = "Theme & Icon",
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("settings_appearance_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Text("Appearance mode", style = MaterialTheme.typography.titleSmall)
              Surface(
                shape = CircleShape,
                color = extended.surfaceHigh,
                border = BorderStroke(1.dp, extended.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(modifier = Modifier.padding(4.dp)) {
                  AppearanceMode.entries.forEach { mode ->
                    val selected = prefs.appearanceMode == mode
                    Surface(
                      onClick = { viewModel.selectAppearanceMode(mode) },
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .testTag("theme_mode_${mode.name.lowercase()}"),
                      shape = CircleShape,
                      color =
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ) {
                      Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        if (selected) {
                          Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                          )
                          Spacer(modifier = Modifier.size(4.dp))
                        }
                        Text(
                          text = mode.label,
                          style = MaterialTheme.typography.labelMedium,
                          color =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else extended.textMuted,
                        )
                      }
                    }
                  }
                }
              }

              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text(
                    text = "Use system wallpaper colors",
                    style = MaterialTheme.typography.labelLarge,
                  )
                  Text(
                    text = "Follow Material You dynamic palette (Android 12+)",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
                ToadSwitch(
                  checked = prefs.useDynamicColors,
                  onCheckedChange = viewModel::toggleDynamicColors,
                  contentDesc = "Use system wallpaper dynamic colors",
                  modifier = Modifier.testTag("switch_dynamic_colors"),
                )
              }
            }
          }
        }

        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Color Palettes", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = prefs.palette.displayName,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("palette_grid")) {
                val gridCols = if (maxWidth >= 520.dp) 3 else 2
                val rows = ThemePalette.entries.chunked(gridCols)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  rows.forEach { rowPalettes ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      rowPalettes.forEach { palette ->
                        val selected = prefs.palette == palette
                        val surfaceBg = Color(palette.surfaceHex)
                        val accentBg = Color(palette.accentHex)
                        Surface(
                          onClick = { viewModel.selectPalette(palette) },
                          modifier =
                            Modifier.weight(1f)
                              .minimumInteractiveComponentSize()
                              .testTag("palette_card_${palette.name.lowercase()}"),
                          shape = RoundedCornerShape(18.dp),
                          color =
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else extended.surfaceHigh,
                          border =
                            BorderStroke(
                              width = if (selected) 2.dp else 1.dp,
                              color =
                                if (selected) MaterialTheme.colorScheme.primary
                                else extended.borderSubtle,
                            ),
                        ) {
                          Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                          ) {
                            // Visual Dual-Tone Swatch Tile Preview
                            Box(
                              modifier =
                                Modifier.fillMaxWidth()
                                  .height(54.dp)
                                  .clip(RoundedCornerShape(12.dp))
                                  .background(surfaceBg)
                                  .border(
                                    1.dp,
                                    accentBg.copy(alpha = 0.45f),
                                    RoundedCornerShape(12.dp),
                                  )
                                  .padding(8.dp)
                            ) {
                              Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                              ) {
                                Box(
                                  modifier =
                                    Modifier.size(20.dp).clip(CircleShape).background(accentBg)
                                )
                                Box(
                                  modifier =
                                    Modifier.width(26.dp)
                                      .height(8.dp)
                                      .clip(CircleShape)
                                      .background(accentBg.copy(alpha = 0.45f))
                                )
                              }
                              if (selected) {
                                Icon(
                                  imageVector = Icons.Filled.CheckCircle,
                                  contentDescription = "Selected",
                                  tint = accentBg,
                                  modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                                )
                              }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                              Text(
                                text = palette.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                              )
                              Text(
                                text = palette.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                              )
                            }
                          }
                        }
                      }
                      repeat((gridCols - rowPalettes.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                      }
                    }
                  }
                }
              }
            }
          }
        }

        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text("App Font", style = MaterialTheme.typography.titleSmall)
              Text(
                "Swap the letterforms across the whole app — sizes, weights and spacing " +
                  "keep their tuning.",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textMuted,
              )
              AppFont.entries.forEach { font ->
                val selected = prefs.appFont == font
                Surface(
                  onClick = { viewModel.selectAppFont(font) },
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("font_option_${font.name.lowercase()}"),
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                  border =
                    BorderStroke(
                      width = if (selected) 2.dp else 1.dp,
                      color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else extended.borderSubtle,
                    ),
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Text(
                      text = font.label,
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = font.description,
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                  }
                }
              }
            }
          }
        }

        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Text("Launcher icon", style = MaterialTheme.typography.titleMedium)
              Text(
                "Pick the icon for your home screen. The choice applies immediately and " +
                  "survives restarts — restart when asked to refresh it everywhere; some " +
                  "launchers take a moment on their own.",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textMuted,
              )
              AppIcon.entries.chunked(4).forEach { rowIcons ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  rowIcons.forEach { icon ->
                    AppIconTile(
                      icon = icon,
                      resId = appIconRes(context, icon),
                      selected = icon == selectedIcon,
                      onClick = {
                        viewModel.setAppIcon(icon)
                        pendingIconRestart = true
                      },
                      modifier = Modifier.weight(1f),
                    )
                  }
                  repeat(4 - rowIcons.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
              }
            }
          }
        }
      }
    }
  }

  if (pendingIconRestart) {
    AlertDialog(
      onDismissRequest = { pendingIconRestart = false },
      title = { Text("Restart to apply icon") },
      text = {
        Text(
          "The launcher icon is now \"${selectedIcon.label}\". Restart toadGALLERY so the " +
            "change applies everywhere immediately — pinned shortcuts keep working."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingIconRestart = false
            restartApp(context)
          }
        ) {
          Text("Restart now")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingIconRestart = false }) { Text("Later") }
      },
    )
  }
}

/** mipmap res id for a launcher-icon variant; 0 when the build doesn't ship it. */
private fun appIconRes(context: Context, icon: AppIcon): Int =
  context.resources.getIdentifier(icon.mipmapName, "mipmap", context.packageName)

@Composable
private fun AppIconTile(
  icon: AppIcon,
  resId: Int,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val context = LocalContext.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        )
        .clickable(onClick = onClick)
        .testTag("app_icon_tile_${icon.name}")
        .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (resId != 0) {
      // Launcher mipmaps resolve to <adaptive-icon> XMLs on API 26+, which
      // painterResource rejects; load through the framework and draw via a
      // plain Drawable-backed Painter instead.
      val painter = remember(resId) {
        ContextCompat.getDrawable(context, resId)?.let(::DrawablePainter)
      }
      if (painter != null) {
        Image(
          painter = painter,
          contentDescription = "App icon ${icon.label}",
          modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = icon.label,
      style = MaterialTheme.typography.labelSmall,
      color =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else extended.textDim,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (selected) {
      Text(
        text = "Selected",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
internal fun SettingsSubPeopleManagerPage(
  viewModel: SettingsViewModel,
  galleryState: GalleryUiState,
  galleryViewModel: GalleryViewModel,
) {
  val extended = LocalToadColors.current
  var personBeingNamed by remember { mutableStateOf<FacePersonGroup?>(null) }
  val mediaById =
    remember(galleryState.visibleMedia) { galleryState.visibleMedia.associateBy { it.id } }

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "People Manager",
      badge = "Faces",
      onBack = viewModel::backToHub,
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("people_manager_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          SettingsGroupCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("People management", style = MaterialTheme.typography.titleMedium)
              Text(
                "One place to manage detected people: rename, ignore, restore and assign " +
                  "unassigned faces. Ignored people stay out of the People page but remain " +
                  "visible and editable here.",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textMuted,
              )
              Text(
                "${galleryState.peopleGroups.size} people • " +
                  "${galleryState.ignoredPeopleGroups.size} hidden • " +
                  "${galleryState.unassignedFaceGroups.size} unassigned faces",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        if (
          galleryState.peopleGroups.isEmpty() &&
            galleryState.ignoredPeopleGroups.isEmpty() &&
            galleryState.unassignedFaceGroups.isEmpty()
        ) {
          item {
            SettingsGroupCard {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text("No people detected yet", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Turn on People albums and run library indexing in AI Engine — detected " +
                    "faces will show up here for naming and assignment.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = extended.textMuted,
                )
              }
            }
          }
        }

        if (galleryState.peopleGroups.isNotEmpty()) {
          item(key = "manager_people_header") {
            Text(
              "PEOPLE",
              style = MaterialTheme.typography.labelSmall,
              color = extended.textMuted,
            )
          }
          itemsIndexed(
            items = galleryState.peopleGroups,
            key = { _, group -> "manager_person_${group.id}" },
            contentType = { _, _ -> "manager_person_row" },
          ) { index, group ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                val coverFace =
                  group.faces.firstOrNull {
                    it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                  } ?: group.faces.firstOrNull()
                val cover = coverFace?.let { mediaById[it.photoId] }
                if (coverFace != null && cover != null) {
                  FaceThumbnail(
                    item = cover,
                    bounds = group.faceBounds[coverFace],
                    sourceRevision = galleryState.faceIndexRevisions[coverFace.photoId] ?: 0L,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                  )
                }
                Column {
                  Text(
                    group.name?.takeIf { it.isNotBlank() } ?: "Person ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    "${group.photoIds.size} photos • ${group.faceCount} faces",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
              }
              TextButton(onClick = { personBeingNamed = group }) {
                Text(if (group.name.isNullOrBlank()) "Name" else "Manage")
              }
            }
          }
        }

        if (galleryState.ignoredPeopleGroups.isNotEmpty()) {
          item(key = "manager_ignored_people_header") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                "IGNORED PEOPLE",
                style = MaterialTheme.typography.labelSmall,
                color = extended.textMuted,
              )
              Text(
                "Hidden from the People page — restore or manage them here",
                style = MaterialTheme.typography.labelSmall,
                color = extended.textMuted,
              )
            }
          }
          itemsIndexed(
            items = galleryState.ignoredPeopleGroups,
            key = { _, group -> "manager_ignored_person_${group.id}" },
            contentType = { _, _ -> "manager_ignored_person_row" },
          ) { index, group ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                val coverFace =
                  group.faces.firstOrNull {
                    it.photoId == group.coverPhotoId && it.faceIndex == group.coverFaceIndex
                  } ?: group.faces.firstOrNull()
                val cover = coverFace?.let { mediaById[it.photoId] }
                if (coverFace != null && cover != null) {
                  FaceThumbnail(
                    item = cover,
                    bounds = group.faceBounds[coverFace],
                    sourceRevision = galleryState.faceIndexRevisions[coverFace.photoId] ?: 0L,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                  )
                }
                Column {
                  Text(
                    group.name?.takeIf { it.isNotBlank() } ?: "Ignored person ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    "${group.photoIds.size} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { personBeingNamed = group }) { Text("Manage") }
                TextButton(onClick = { galleryViewModel.setFacePersonIgnored(group, false) }) {
                  Text("Unignore")
                }
              }
            }
          }
        }

        if (galleryState.unassignedFaceGroups.isNotEmpty()) {
          item(key = "manager_unassigned_faces_header") {
            Text(
              "UNASSIGNED FACES",
              style = MaterialTheme.typography.labelSmall,
              color = extended.textMuted,
            )
          }
          items(
            items = galleryState.unassignedFaceGroups,
            key = { candidate -> "manager_unassigned_face_${candidate.id}" },
            contentType = { "manager_unassigned_face_row" },
          ) { candidate ->
            val face = candidate.faces.firstOrNull()
            val photo = face?.let { mediaById[it.photoId] }
            if (face != null && photo != null) {
              val suggestionsForFace = galleryState.facePersonSuggestions[candidate.id].orEmpty()
              val suggestionScoreById =
                remember(suggestionsForFace) {
                  suggestionsForFace.associateBy { it.personId }
                }
              val orderedTargets =
                remember(galleryState.peopleGroups, suggestionsForFace) {
                  orderFaceAssignmentTargets(galleryState.peopleGroups, suggestionsForFace)
                }
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                FaceThumbnail(
                  item = photo,
                  bounds = candidate.faceBounds[face],
                  sourceRevision = galleryState.faceIndexRevisions[face.photoId] ?: 0L,
                  modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    photo.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      if (suggestionsForFace.isNotEmpty()) "Unassigned • Suggested match ready"
                      else "Unassigned detected face",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (suggestionsForFace.isNotEmpty()) MaterialTheme.colorScheme.primary
                      else extended.textMuted,
                  )
                }
                TextButton(onClick = { personBeingNamed = candidate }) { Text("Name") }
                var assignMenuExpanded by
                  remember(face.photoId, face.faceIndex) { mutableStateOf(false) }
                Box {
                  TextButton(onClick = { assignMenuExpanded = true }) { Text("Assign") }
                  DropdownMenu(
                    expanded = assignMenuExpanded,
                    onDismissRequest = { assignMenuExpanded = false },
                  ) {
                    if (suggestionScoreById.isNotEmpty()) {
                      DropdownMenuItem(
                        text = { Text("SUGGESTED MATCHES") },
                        enabled = false,
                        onClick = {},
                      )
                    }
                    orderedTargets.forEach { target ->
                      val matchInfo = suggestionScoreById[target.id]
                      val similarityPct =
                        matchInfo?.let {
                          ((1f - it.medianDistance.coerceIn(0f, 1f)) * 100f).roundToInt()
                        }
                      DropdownMenuItem(
                        text = {
                          Text(
                            (target.name?.takeIf { it.isNotBlank() } ?: "Unnamed person") +
                              if (similarityPct != null) " · Suggested ($similarityPct%)"
                              else ""
                          )
                        },
                        onClick = {
                          galleryViewModel.assignFaceToPerson(face.photoId, face.faceIndex, target)
                          assignMenuExpanded = false
                        },
                      )
                    }
                  }
                }
                TextButton(onClick = { galleryViewModel.setFacePersonIgnored(candidate, true) }) {
                  Text("Ignore")
                }
              }
            }
          }
        }
      }
    }
  }

  PersonManagementSheet(
    group = personBeingNamed,
    uiState = galleryState,
    onDismiss = { personBeingNamed = null },
    onNamePerson = galleryViewModel::setFacePersonName,
    onExcludeFace = galleryViewModel::excludeFaceFromPerson,
    onSetPersonCover = galleryViewModel::setFacePersonCover,
    onSetPersonIgnored = galleryViewModel::setFacePersonIgnored,
    onAssignFace = galleryViewModel::assignFaceToPerson,
    onUndoFaceCorrection = galleryViewModel::undoLastFaceCorrection,
  )
}
