package com.toadgallery.app.data.model

import com.toadgallery.app.BuildConfig
import com.toadgallery.app.domain.AppFont
import com.toadgallery.app.domain.AppIcon

import androidx.compose.runtime.Immutable

enum class ThemePalette(
  val displayName: String,
  val subtitle: String,
  val accentHex: Long,
  val surfaceHex: Long,
) {
  TOAD_GREEN("Toad Green", "Signature botanical dark", 0xFF6EE7B7, 0xFF07130E),
  DYNAMIC_AMBER("Dynamic Amber", "Warm Material You gold", 0xFFFBBF24, 0xFF14100B),
  OCEAN_SLATE("Ocean Slate", "Deep twilight blue", 0xFF38BDF8, 0xFF08111A),
  OLED_BLACK("OLED Pure Black", "Zero-nit battery saver", 0xFF6EE7B7, 0xFF000000),
  TOAD_FOREST("Toad Forest", "Dark forest look — from toadFILES", 0xFF9FE06B, 0xFF0D120E),
  SLATE_NEON("Slate Neon", "Graphite + electric green — from toadFILES", 0xFF57F2A0, 0xFF0A0C0B),
  M3_BASELINE("Material 3", "Google's M3 baseline — from toadFILES", 0xFFD0BCFF, 0xFF141218),
  BOTANICAL_LIGHT("Botanical Light", "High-clarity daylight", 0xFF0F7654, 0xFFF4F9F6),
}

enum class AppearanceMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

enum class LayoutMode(val label: String) {
  GRID("Grid"),
  COLLAGE("Collage"),
}

enum class GroupByMode(val label: String) {
  NONE("None"),
  DAY("Day"),
  WEEK("Week"),
  MONTH("Month"),
}

enum class MediaCategory(val label: String) {
  ALL("All"),
  CAMERA("Camera"),
  SCREENSHOTS("Screenshots"),
  VIDEOS("Videos"),
  GIFS("GIFs"),
}

enum class MediaType(val label: String) {
  PHOTO("Photos Only"),
  SCREENSHOT("OCR Screenshots"),
  VIDEO("Videos"),
  GIF("GIFs"),
}

enum class TonePreset(val label: String) {
  NATURAL("Natural"),
  BOTANICAL_VIVID("Botanical Vivid"),
  WARM_FILM("Warm Film"),
  MONOCHROME("Monochrome"),
}

enum class VaultAutoLock(val label: String, val timeoutMs: Long) {
  IMMEDIATELY("Immediately", 0L),
  ONE_MINUTE("1 minute", 60_000L),
  FIVE_MINUTES("5 minutes", 300_000L),
}

enum class TopLevelTab(val route: String, val label: String) {
  PHOTOS("photos", "Photos"),
  ALBUMS("albums", "Albums"),
  SEARCH("search", "Search"),
  SETTINGS("settings", "Settings"),
}

enum class SearchTabMode(val label: String) {
  MAGIC_SEARCH("Magic Search"),
  PLACES_MAP("Places Map"),
}

enum class SettingsSubPage(val title: String, val badge: String) {
  HUB("Settings", "v${BuildConfig.VERSION_NAME}"),
  APPEARANCE("Appearance", "Theme & Icon"),
  LAYOUT("Timeline Layout", "Display"),
  AI_ENGINE("AI Engine & Models", "On-Device"),
  VAULT_SECURITY("Hidden Vault & Security", "AES-256"),
  STORAGE("Storage Manager", "Local"),
  ABOUT("About toadGALLERY", "v${BuildConfig.VERSION_NAME}"),
  PEOPLE_MANAGER("People Manager", "Faces"),
}

sealed interface TimelineFolderFilter {
  val label: String

  data object Favorites : TimelineFolderFilter {
    override val label: String = "Favorites"
  }

  data class DeviceBucket(val bucketName: String) : TimelineFolderFilter {
    override val label: String = bucketName
  }

  data class CustomAlbum(val albumId: Long, val albumName: String) : TimelineFolderFilter {
    override val label: String = albumName
  }
}

@Immutable
data class GalleryMediaItem(
  val id: Long,
  val title: String,
  val uriString: String?,
  val drawableResName: String?,
  val mediaStoreId: Long? = null,
  val bucketName: String = "Camera",
  val encryptedVaultFile: String? = null,
  val category: MediaCategory,
  val mediaType: MediaType,
  val durationLabel: String?,
  val dateLabel: String,
  val weekGroup: String,
  val monthGroup: String,
  val timestamp: Long,
  val locationName: String,
  val hasGps: Boolean,
  val mapX: Float,
  val mapY: Float,
  val aspectRatio: Float,
  val isFavorite: Boolean,
  val isHidden: Boolean,
  val isTrashed: Boolean,
  /** Epoch millis when the item entered the Recycle Bin; null when not trashed. */
  val trashedAt: Long? = null,
  val tags: List<String>,
  val ocrText: String,
  val exifSummary: String,
  val tonePreset: TonePreset,
  val albumId: Long?,
  val editedUriString: String? = null,
  val editRecipeJson: String? = null,
) {
  val hasNonDestructiveEdits: Boolean
    get() = !editedUriString.isNullOrBlank() || !editRecipeJson.isNullOrBlank()
}

@Immutable
data class AlbumFolder(
  val id: Long,
  val name: String,
  val itemCount: Int,
  val coverUriString: String?,
  val coverDrawableName: String?,
  val filterCategory: MediaCategory?,
  val filterBucketName: String? = null,
  val customAlbumId: Long? = null,
  val isFavoritesAlbum: Boolean = false,
)

@Immutable
data class StorySlide(
  val id: Int,
  val title: String,
  val subtitle: String,
  val caption: String,
  val drawableResName: String,
)

@Immutable
data class UserPreferences(
  val palette: ThemePalette = ThemePalette.TOAD_GREEN,
  val appearanceMode: AppearanceMode = AppearanceMode.DARK,
  val useDynamicColors: Boolean = false,
  val layoutMode: LayoutMode = LayoutMode.COLLAGE,
  val columns: Int = 3,
  val groupBy: GroupByMode = GroupByMode.MONTH,
  val showStories: Boolean = true,
  val hideScreenshots: Boolean = false,
  val aiPeople: Boolean = true,
  val aiMagicSearch: Boolean = true,
  val aiThings: Boolean = true,
  val clusteringThreshold: Float = 0.24f,
  val faceModelInstalled: Boolean = false,
  val semanticModelInstalled: Boolean = false,
  val depthModelInstalled: Boolean = false,
  val showLocationExif: Boolean = true,
  val vaultBiometrics: Boolean = true,
  val vaultAutoLock: VaultAutoLock = VaultAutoLock.IMMEDIATELY,
  val vaultPinHash: String = "",
  val vaultPinSalt: String = "",
  val vaultLastUnlockedAt: Long = 0L,
  val lastMediaSyncCount: Int = 0,
  val cacheSizeMb: Float = 42.8f,
  val mlIndexSizeMb: Float = 18.4f,
  val systemMediaSync: Boolean = false,
  val mediaManagementPromptSeen: Boolean = false,
  /** One-time eager backfill of legacy v8 null-faceId feedback rows (E3, v2.1.0). */
  val legacyFaceIdBackfillDone: Boolean = false,
  val appIcon: AppIcon = AppIcon.CLASSIC_TOAD,
  val appFont: AppFont = AppFont.DEFAULT,
  /** v3.0.0: first-run intro has been seen; never re-shown on upgrade. */
  val introDone: Boolean = false,
)
