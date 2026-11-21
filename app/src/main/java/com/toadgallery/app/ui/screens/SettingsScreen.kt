package com.toadgallery.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toadgallery.app.data.media.MediaAccessLevel
import com.toadgallery.app.data.media.MediaPermissionHelper
import com.toadgallery.app.BuildConfig
import com.toadgallery.app.data.model.GroupByMode
import com.toadgallery.app.data.model.LayoutMode
import com.toadgallery.app.data.model.SettingsSubPage
import com.toadgallery.app.ui.viewmodel.GalleryUiState
import com.toadgallery.app.ui.viewmodel.GalleryViewModel
import com.toadgallery.app.ui.components.HeroGradientCard
import com.toadgallery.app.ui.components.SectionHeading
import com.toadgallery.app.ui.components.StorageBreakdownBar
import com.toadgallery.app.ui.components.ToadSwitch
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.theme.ToadMarkTokens
import com.toadgallery.app.ui.viewmodel.SettingsUiState
import com.toadgallery.app.ui.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
  galleryState: GalleryUiState,
  galleryViewModel: GalleryViewModel,
  mediaAccessLevel: MediaAccessLevel,
  onSyncMediaPermissions: () -> Unit,
  onOpenVaultModal: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(enabled = uiState.currentSubPage != SettingsSubPage.HUB) {
    viewModel.backToHub()
  }

  AnimatedContent(
    targetState = uiState.currentSubPage,
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    transitionSpec = {
      if (targetState != SettingsSubPage.HUB) {
        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
          (slideOutHorizontally { -it / 6 } + fadeOut())
      } else {
        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
          (slideOutHorizontally { it / 6 } + fadeOut())
      }
    },
    label = "settingsRouter",
  ) { page ->
    when (page) {
      SettingsSubPage.HUB ->
        SettingsHubView(
          uiState = uiState,
          viewModel = viewModel,
          galleryState = galleryState,
          mediaAccessLevel = mediaAccessLevel,
          onSyncMediaPermissions = onSyncMediaPermissions,
        )
      SettingsSubPage.APPEARANCE ->
        SettingsSubAppearancePage(uiState = uiState, viewModel = viewModel)
      SettingsSubPage.LAYOUT -> SettingsSubLayoutPage(uiState = uiState, viewModel = viewModel)
      SettingsSubPage.AI_ENGINE -> SettingsSubAiEnginePage(uiState = uiState, viewModel = viewModel)
      SettingsSubPage.VAULT_SECURITY ->
        SettingsSubVaultPage(
          uiState = uiState,
          viewModel = viewModel,
          onOpenVaultModal = onOpenVaultModal,
        )
      SettingsSubPage.STORAGE -> SettingsSubStoragePage(uiState = uiState, viewModel = viewModel)
      SettingsSubPage.ABOUT -> SettingsSubAboutPage(uiState = uiState, viewModel = viewModel)
      SettingsSubPage.PEOPLE_MANAGER ->
        SettingsSubPeopleManagerPage(
          viewModel = viewModel,
          galleryState = galleryState,
          galleryViewModel = galleryViewModel,
        )
    }
  }
}

@Composable
private fun SettingsHubView(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
  galleryState: GalleryUiState,
  mediaAccessLevel: MediaAccessLevel,
  onSyncMediaPermissions: () -> Unit,
) {
  val extended = LocalToadColors.current
  val context = LocalContext.current
  val mediaManagementGranted = MediaPermissionHelper.hasMediaManagementPermission(context)
  val prefs = uiState.preferences
  val q = uiState.searchQuery.trim().lowercase()
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

  fun matches(vararg keywords: String): Boolean =
    q.isEmpty() || keywords.any { it.lowercase().contains(q) }

  val showAppearance =
    matches("appearance", "theme", "color", "palette", "dark", "light", "material", "layout", "grid", "collage", "stories", "group", "columns", "icon")
  val showAi =
    matches("ai", "people", "faces", "magic search", "things", "models", "onnx", "indexing", "npu", "clip", "clustering", "bokeh", "depth", "dav2")
  val showPrivacy =
    matches("privacy", "permissions", "mediastore", "sync", "location", "exif", "gps", "vault", "pin", "biometric", "security", "hidden", "media management", "batch")
  val showStorage = matches("storage", "cache", "database", "data", "clear", "space", "mb", "index")
  val showAbout = matches("about", "version", "diagnostics", "self-test", "changelog", "update")
  val nothingFound = !showAppearance && !showAi && !showPrivacy && !showStorage && !showAbout

  val faceInstalled = uiState.faceModelState.isInstalled || prefs.faceModelInstalled
  val depthInstalled = uiState.depthModelState.isInstalled || prefs.depthModelInstalled
  val mlModelsMb =
    (if (faceInstalled) 35.9f else 0f) +
      (if (prefs.semanticModelInstalled) 213.0f else 0f) +
      (if (depthInstalled) 27.3f else 0f)
  val totalStorageMb = 14.2f + mlModelsMb + prefs.mlIndexSizeMb + prefs.cacheSizeMb

  Column(modifier = Modifier.fillMaxSize()) {
    // Sticky Hub Header with Live Settings Search
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(extended.glassSurface)
          .padding(top = statusBarTop + 10.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extended.borderSubtle),
        modifier = Modifier.fillMaxWidth().height(48.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = extended.textMuted,
            modifier = Modifier.size(18.dp),
          )
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (uiState.searchQuery.isEmpty()) {
              Text(
                text = "Search settings, AI models, vault, theme...",
                style = MaterialTheme.typography.bodyMedium,
                color = extended.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = uiState.searchQuery,
              onValueChange = viewModel::setSearchQuery,
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth().testTag("settings_search_input"),
            )
          }
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.setSearchQuery("") },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear settings search",
                tint = extended.textMuted,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }

    HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("settings_hub_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        if (q.isEmpty()) {
          item {
            HeroGradientCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
              ) {
                Column(
                  modifier = Modifier.weight(1f).padding(end = 12.dp),
                  verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background,
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                      )
                      Text(
                        text = "100% OFFLINE • NPU READY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                  Text(
                    text = "On-Device Privacy & AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      "Zero cloud uploads. Active palette: ${prefs.palette.displayName} • ${prefs.columns}-col ${prefs.layoutMode.label.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }

                Button(
                  onClick = { viewModel.openSubPage(SettingsSubPage.AI_ENGINE) },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("hero_ai_status_button"),
                  shape = RoundedCornerShape(16.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                  Text("AI Status", style = MaterialTheme.typography.labelMedium)
                }
              }
            }
          }
        }

        if (nothingFound) {
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
                modifier = Modifier.size(40.dp),
              )
              Text(
                text = "No matching settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = "No options matched \"${uiState.searchQuery}\"",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
              )
              Button(onClick = { viewModel.setSearchQuery("") }) { Text("Clear Search") }
            }
          }
        }

        // Section 1: Appearance & Timeline
        if (showAppearance) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionHeading(title = "Appearance & Timeline", trailing = "Look & grid")
              SettingsGroupCard {
                SettingsDrillRow(
                  icon = Icons.Filled.Palette,
                  iconContainer = MaterialTheme.colorScheme.primaryContainer,
                  iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                  title = "Appearance",
                  subtitle =
                    "${prefs.appearanceMode.label} • ${prefs.palette.displayName} • ${prefs.appIcon.label}",
                  trailingBadgeColor = MaterialTheme.colorScheme.primary,
                  onClick = { viewModel.openSubPage(SettingsSubPage.APPEARANCE) },
                  testTag = "settings_row_appearance",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.GridView,
                  iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "Timeline Layout & Density",
                  subtitle =
                    "${prefs.layoutMode.label} • ${prefs.columns} per row • Group by ${prefs.groupBy.label}",
                  onClick = { viewModel.openSubPage(SettingsSubPage.LAYOUT) },
                  testTag = "settings_row_layout",
                )
              }
            }
          }
        }

        // Section 2: On-Device AI & Smart Features
        if (showAi) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionHeading(title = "On-Device AI & Smart Features", trailing = "Local NPU")
              SettingsGroupCard {
                SettingsToggleRow(
                  icon = Icons.Filled.Group,
                  title = "People albums",
                  subtitle = "On-device face clustering",
                  checked = prefs.aiPeople,
                  onToggle = viewModel::toggleAiPeople,
                  testTag = "switch_ai_people",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsToggleRow(
                  icon = Icons.Filled.AutoAwesome,
                  title = "Magic search",
                  subtitle = "Semantic scenes & screenshot OCR",
                  checked = prefs.aiMagicSearch,
                  onToggle = viewModel::toggleAiMagicSearch,
                  testTag = "switch_ai_magic",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsToggleRow(
                  icon = Icons.Outlined.Category,
                  title = "Things collections",
                  subtitle = "Auto-group Dogs, Sunsets & Receipts",
                  checked = prefs.aiThings,
                  onToggle = viewModel::toggleAiThings,
                  testTag = "switch_ai_things",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.Memory,
                  iconContainer = MaterialTheme.colorScheme.primaryContainer,
                  iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                  title = "AI Models & Indexing Engine",
                  subtitle = "Manage packs, thresholds & queue",
                  trailingPill = String.format(Locale.US, "Ready • %.1f MB", mlModelsMb),
                  containerColor = extended.surfaceHigh,
                  onClick = { viewModel.openSubPage(SettingsSubPage.AI_ENGINE) },
                  testTag = "settings_row_ai_engine",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.Group,
                  iconContainer = MaterialTheme.colorScheme.primaryContainer,
                  iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                  title = "People Manager",
                  subtitle =
                    "Rename, ignore & assign faces • " +
                      "${galleryState.peopleGroups.size} people, " +
                      "${galleryState.ignoredPeopleGroups.size} hidden, " +
                      "${galleryState.unassignedFaceGroups.size} unassigned",
                  trailingBadgeColor = MaterialTheme.colorScheme.primary,
                  onClick = { viewModel.openSubPage(SettingsSubPage.PEOPLE_MANAGER) },
                  testTag = "settings_row_people_manager",
                )
              }
            }
          }
        }

        // Section 3: Privacy, Permissions & Vault
        if (showPrivacy) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionHeading(title = "Privacy, Permissions & Vault", trailing = "Zero tracking")
              SettingsGroupCard {
                SettingsDrillRow(
                  icon = Icons.Filled.PhotoLibrary,
                  iconContainer = extended.surfaceHigh,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "Gallery Media Permissions",
                  subtitle = "${mediaAccessLevel.label} • Tap to sync MediaStore",
                  trailingPill =
                    if (mediaAccessLevel == MediaAccessLevel.FULL) "Full" else "Sync",
                  onClick = onSyncMediaPermissions,
                  testTag = "settings_row_media_permissions",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.Tune,
                  iconContainer = extended.surfaceHigh,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "Media Management",
                  subtitle =
                    if (mediaManagementGranted) {
                      "Granted • batch deletes & favorites skip system dialogs"
                    } else {
                      "Tap to grant • smoother batch deletes and favorites"
                    },
                  trailingPill = if (mediaManagementGranted) "On" else "Grant",
                  onClick = {
                    MediaPermissionHelper.mediaManagementRequestIntent(context)?.let {
                      context.startActivity(it)
                    }
                  },
                  testTag = "settings_row_media_management",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsToggleRow(
                  icon = Icons.Filled.Place,
                  title = "Location & EXIF GPS data",
                  subtitle =
                    if (prefs.showLocationExif) "Shown locally in viewer & map"
                    else "GPS coordinates masked",
                  checked = prefs.showLocationExif,
                  onToggle = viewModel::toggleLocationExif,
                  testTag = "switch_location_exif",
                )
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.Shield,
                  iconContainer = MaterialTheme.colorScheme.primaryContainer,
                  iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                  title = "Hidden Vault & Security",
                  subtitle =
                    "AES-256-GCM • ${if (prefs.vaultBiometrics) "SHA-256 PIN & biometrics" else "SHA-256 PIN"}",
                  onClick = { viewModel.openSubPage(SettingsSubPage.VAULT_SECURITY) },
                  testTag = "settings_row_vault",
                )
              }
            }
          }
        }

        // Section 4: Storage & Data Management
        if (showStorage) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionHeading(
                title = "Storage & Data Management",
                trailing = String.format(Locale.US, "%.1f MB", totalStorageMb),
              )
              SettingsGroupCard {
                Column(modifier = Modifier.padding(14.dp)) {
                  StorageBreakdownBar(
                    faceModelInstalled = faceInstalled,
                    semanticModelInstalled = prefs.semanticModelInstalled,
                    depthModelInstalled = depthInstalled,
                    mlIndexMb = prefs.mlIndexSizeMb,
                    cacheMb = prefs.cacheSizeMb,
                  )
                }
                HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)
                SettingsDrillRow(
                  icon = Icons.Filled.Storage,
                  iconContainer = extended.surfaceHigh,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "Storage & Cache Manager",
                  subtitle =
                    String.format(
                      Locale.US,
                      "Database, ML models & %.1f MB cache",
                      prefs.cacheSizeMb,
                    ),
                  onClick = { viewModel.openSubPage(SettingsSubPage.STORAGE) },
                  testTag = "settings_row_storage",
                )
              }
            }
          }
        }

        // Section 5: About & Diagnostics
        if (showAbout) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionHeading(title = "About & Diagnostics")
              SettingsGroupCard {
                SettingsDrillRow(
                  icon = Icons.Filled.Info,
                  iconContainer = extended.surfaceHigh,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "About toadGALLERY",
                  subtitle = "${BuildConfig.VERSION_NAME} • Engine self-test & changelog",
                  onClick = { viewModel.openSubPage(SettingsSubPage.ABOUT) },
                  testTag = "settings_row_about",
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSubLayoutPage(
  uiState: SettingsUiState,
  viewModel: SettingsViewModel,
) {
  val extended = LocalToadColors.current
  val prefs = uiState.preferences

  Column(modifier = Modifier.fillMaxSize()) {
    SettingsSubPageHeader(
      title = "Timeline Layout",
      badge = "${prefs.columns} cols",
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
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Text("Thumbnail Arrangement", style = MaterialTheme.typography.titleSmall)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                LayoutModeCard(
                  icon = Icons.Filled.GridView,
                  title = "Grid",
                  subtitle = "Uniform square tiles",
                  selected = prefs.layoutMode == LayoutMode.GRID,
                  onClick = { viewModel.setLayoutMode(LayoutMode.GRID) },
                  testTag = "layout_option_grid",
                  modifier = Modifier.weight(1f),
                )
                LayoutModeCard(
                  icon = Icons.Filled.ViewAgenda,
                  title = "Collage",
                  subtitle = "Aspect-aware staggered",
                  selected = prefs.layoutMode == LayoutMode.COLLAGE,
                  onClick = { viewModel.setLayoutMode(LayoutMode.COLLAGE) },
                  testTag = "layout_option_collage",
                  modifier = Modifier.weight(1f),
                )
              }

              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

              // Photos per Row (2..10 synced with Pinch-to-Zoom)
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text("Photos per row", style = MaterialTheme.typography.labelLarge)
                    Text(
                      text =
                        if (prefs.columns >= 6)
                          "Compact grid • Badges auto-hidden at 6+"
                        else "Synced live with pinch-to-zoom (2–10)",
                      style = MaterialTheme.typography.bodySmall,
                      color = extended.textMuted,
                    )
                  }
                  Surface(shape = CircleShape, color = extended.surfaceHigh) {
                    Row(
                      modifier = Modifier.padding(4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      IconButton(
                        onClick = { viewModel.changeColumns(-1) },
                        enabled = prefs.columns > 2,
                        modifier =
                          Modifier.minimumInteractiveComponentSize().testTag("columns_minus_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Remove,
                          contentDescription = "Decrease columns",
                        )
                      }
                      Text(
                        text = "${prefs.columns}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      IconButton(
                        onClick = { viewModel.changeColumns(1) },
                        enabled = prefs.columns < 10,
                        modifier =
                          Modifier.minimumInteractiveComponentSize().testTag("columns_plus_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Add,
                          contentDescription = "Increase columns",
                        )
                      }
                    }
                  }
                }

                Slider(
                  value = prefs.columns.toFloat(),
                  onValueChange = { viewModel.setColumns(it.roundToInt()) },
                  valueRange = 2f..10f,
                  steps = 7,
                  colors =
                    SliderDefaults.colors(
                      thumbColor = MaterialTheme.colorScheme.primary,
                      activeTrackColor = MaterialTheme.colorScheme.primary,
                      inactiveTrackColor = extended.surfaceHighest,
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("columns_slider"),
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  listOf(2, 4, 6, 8, 10).forEach { preset ->
                    val active = prefs.columns == preset
                    Surface(
                      onClick = { viewModel.setColumns(preset) },
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .testTag("columns_preset_$preset"),
                      shape = CircleShape,
                      color =
                        if (active) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
                      border =
                        BorderStroke(
                          1.dp,
                          if (active) MaterialTheme.colorScheme.primary
                          else extended.borderSubtle,
                        ),
                    ) {
                      Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        Text(
                          text = "$preset",
                          style = MaterialTheme.typography.labelMedium,
                          color =
                            if (active) MaterialTheme.colorScheme.onPrimary
                            else extended.textMuted,
                        )
                      }
                    }
                  }
                }
              }

              HorizontalDivider(thickness = 1.dp, color = extended.borderSubtle)

              // Group By Selector
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Group timeline by", style = MaterialTheme.typography.labelLarge)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  GroupByMode.entries.forEach { mode ->
                    val selected = prefs.groupBy == mode
                    Surface(
                      onClick = { viewModel.setGroupBy(mode) },
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .testTag("groupby_${mode.name.lowercase()}"),
                      shape = RoundedCornerShape(14.dp),
                      color =
                        if (selected) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
                    ) {
                      Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                      ) {
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
                  Text("Stories on home page", style = MaterialTheme.typography.labelLarge)
                  Text(
                    "Lead timeline with curated memory cards",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textMuted,
                  )
                }
                ToadSwitch(
                  checked = prefs.showStories,
                  onCheckedChange = viewModel::toggleShowStories,
                  contentDesc = "Toggle stories shelf on Photos tab",
                  modifier = Modifier.testTag("switch_show_stories"),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LayoutModeCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  Surface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
    shape = RoundedCornerShape(18.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else extended.surfaceHigh,
    border =
      BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(title, style = MaterialTheme.typography.labelLarge)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extended.textMuted)
    }
  }
}

@Composable
internal fun SettingsGroupCard(content: @Composable () -> Unit) {
  val extended = LocalToadColors.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, extended.borderSubtle),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
  }
}

@Composable
private fun SettingsDrillRow(
  icon: ImageVector,
  iconContainer: Color,
  iconTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
  trailingBadgeColor: Color? = null,
  trailingPill: String? = null,
  containerColor: Color = Color.Transparent,
) {
  val extended = LocalToadColors.current
  Surface(
    onClick = onClick,
    color = containerColor,
    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = ToadMarkTokens.RowPaddingH, vertical = ToadMarkTokens.RowPaddingV),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ToadMarkTokens.RowIconGap),
    ) {
      Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(iconContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp),
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
      if (trailingBadgeColor != null) {
        Box(
          modifier =
            Modifier.size(16.dp)
              .clip(CircleShape)
              .background(trailingBadgeColor)
              .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
        )
      }
      if (!trailingPill.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(ToadMarkTokens.RadiusBadge),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = trailingPill,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = ToadMarkTokens.BadgeFontSize),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(
              horizontal = ToadMarkTokens.BadgePaddingH,
              vertical = ToadMarkTokens.BadgePaddingV,
            ),
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = extended.textDim,
        modifier = Modifier.size(ToadMarkTokens.RowChevronSize),
      )
    }
  }
}

@Composable
private fun SettingsToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onToggle: () -> Unit,
  testTag: String,
) {
  val extended = LocalToadColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = ToadMarkTokens.RowPaddingH, vertical = ToadMarkTokens.RowPaddingVCompact),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ToadMarkTokens.RowIconGap),
  ) {
    Box(
      modifier =
        Modifier.size(38.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(extended.surfaceHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
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
      )
    }
    ToadSwitch(
      checked = checked,
      onCheckedChange = onToggle,
      contentDesc = title,
      modifier = Modifier.testTag(testTag),
    )
  }
}
