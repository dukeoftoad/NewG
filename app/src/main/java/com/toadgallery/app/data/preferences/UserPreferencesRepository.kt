package com.toadgallery.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.toadgallery.app.data.model.AppearanceMode
import com.toadgallery.app.data.model.GroupByMode
import com.toadgallery.app.data.model.LayoutMode
import com.toadgallery.app.data.model.ThemePalette
import com.toadgallery.app.data.model.UserPreferences
import com.toadgallery.app.data.model.VaultAutoLock
import com.toadgallery.app.data.security.PinHasher
import java.util.WeakHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val dataStoreLock = Any()
private val dataStoreInstances = WeakHashMap<Context, DataStore<Preferences>>()

private fun Context.toadDataStore(): DataStore<Preferences> {
  val appCtx = applicationContext
  return synchronized(dataStoreLock) {
    dataStoreInstances.getOrPut(appCtx) {
      PreferenceDataStoreFactory.create(
        produceFile = { appCtx.preferencesDataStoreFile("toad_gallery_prefs") }
      )
    }
  }
}

class UserPreferencesRepository(context: Context) {
  private val dataStore: DataStore<Preferences> = context.toadDataStore()

  private object Keys {
    val PALETTE = stringPreferencesKey("palette")
    val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
    val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
    val LAYOUT_MODE = stringPreferencesKey("layout_mode")
    val COLUMNS = intPreferencesKey("columns")
    val GROUP_BY = stringPreferencesKey("group_by")
    val SHOW_STORIES = booleanPreferencesKey("show_stories")
    val HIDE_SCREENSHOTS = booleanPreferencesKey("hide_screenshots")
    val AI_PEOPLE = booleanPreferencesKey("ai_people")
    val AI_MAGIC_SEARCH = booleanPreferencesKey("ai_magic_search")
    val AI_THINGS = booleanPreferencesKey("ai_things")
    val CLUSTERING_THRESHOLD = floatPreferencesKey("clustering_threshold")
    val FACE_MODEL_INSTALLED = booleanPreferencesKey("face_model_installed")
    val SEMANTIC_MODEL_INSTALLED = booleanPreferencesKey("semantic_model_installed")
    val DEPTH_MODEL_INSTALLED = booleanPreferencesKey("depth_model_installed")
    val SHOW_LOCATION_EXIF = booleanPreferencesKey("show_location_exif")
    val VAULT_BIOMETRICS = booleanPreferencesKey("vault_biometrics")
    val VAULT_AUTO_LOCK = stringPreferencesKey("vault_auto_lock")
    val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
    val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
    val VAULT_LAST_UNLOCKED_AT = longPreferencesKey("vault_last_unlocked_at")
    val LAST_MEDIA_SYNC_COUNT = intPreferencesKey("last_media_sync_count")
    val CACHE_SIZE_MB = floatPreferencesKey("cache_size_mb")
    val ML_INDEX_SIZE_MB = floatPreferencesKey("ml_index_size_mb")
    val SYSTEM_MEDIA_SYNC = booleanPreferencesKey("system_media_sync")
    val MEDIA_MGMT_PROMPT_SEEN = booleanPreferencesKey("media_mgmt_prompt_seen")
  }

  val preferences: Flow<UserPreferences> =
    dataStore.data.map { prefs ->
      UserPreferences(
        palette =
          prefs[Keys.PALETTE]?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() }
            ?: ThemePalette.TOAD_GREEN,
        appearanceMode =
          prefs[Keys.APPEARANCE_MODE]?.let {
            runCatching { AppearanceMode.valueOf(it) }.getOrNull()
          } ?: AppearanceMode.DARK,
        useDynamicColors = prefs[Keys.USE_DYNAMIC_COLORS] ?: false,
        layoutMode =
          prefs[Keys.LAYOUT_MODE]?.let { runCatching { LayoutMode.valueOf(it) }.getOrNull() }
            ?: LayoutMode.COLLAGE,
        columns = (prefs[Keys.COLUMNS] ?: 3).coerceIn(2, 10),
        groupBy =
          prefs[Keys.GROUP_BY]?.let { runCatching { GroupByMode.valueOf(it) }.getOrNull() }
            ?: GroupByMode.MONTH,
        showStories = prefs[Keys.SHOW_STORIES] ?: true,
        hideScreenshots = prefs[Keys.HIDE_SCREENSHOTS] ?: false,
        aiPeople = prefs[Keys.AI_PEOPLE] ?: true,
        aiMagicSearch = prefs[Keys.AI_MAGIC_SEARCH] ?: true,
        aiThings = prefs[Keys.AI_THINGS] ?: true,
        clusteringThreshold = (prefs[Keys.CLUSTERING_THRESHOLD] ?: 0.24f).coerceIn(0.10f, 0.40f),
        faceModelInstalled = prefs[Keys.FACE_MODEL_INSTALLED] ?: false,
        semanticModelInstalled = prefs[Keys.SEMANTIC_MODEL_INSTALLED] ?: false,
        depthModelInstalled = prefs[Keys.DEPTH_MODEL_INSTALLED] ?: false,
        showLocationExif = prefs[Keys.SHOW_LOCATION_EXIF] ?: true,
        vaultBiometrics = prefs[Keys.VAULT_BIOMETRICS] ?: true,
        vaultAutoLock =
          prefs[Keys.VAULT_AUTO_LOCK]?.let {
            runCatching { VaultAutoLock.valueOf(it) }.getOrNull()
          } ?: VaultAutoLock.IMMEDIATELY,
        vaultPinHash = prefs[Keys.VAULT_PIN_HASH].orEmpty(),
        vaultPinSalt = prefs[Keys.VAULT_PIN_SALT].orEmpty(),
        vaultLastUnlockedAt = prefs[Keys.VAULT_LAST_UNLOCKED_AT] ?: 0L,
        lastMediaSyncCount = prefs[Keys.LAST_MEDIA_SYNC_COUNT] ?: 0,
        cacheSizeMb = prefs[Keys.CACHE_SIZE_MB] ?: 42.8f,
        mlIndexSizeMb = prefs[Keys.ML_INDEX_SIZE_MB] ?: 18.4f,
        systemMediaSync = prefs[Keys.SYSTEM_MEDIA_SYNC] ?: false,
        mediaManagementPromptSeen = prefs[Keys.MEDIA_MGMT_PROMPT_SEEN] ?: false,
      )
    }

  suspend fun ensurePinInitialized() {
    dataStore.edit { prefs ->
      if (prefs[Keys.VAULT_PIN_HASH].isNullOrBlank() || prefs[Keys.VAULT_PIN_SALT].isNullOrBlank()) {
        val salt = PinHasher.generateSalt()
        prefs[Keys.VAULT_PIN_SALT] = salt
        prefs[Keys.VAULT_PIN_HASH] = PinHasher.hashPin(DEFAULT_PIN, salt)
      }
    }
  }

  suspend fun verifyPin(enteredPin: String): Boolean {
    ensurePinInitialized()
    val snap = dataStore.data.first()
    val salt = snap[Keys.VAULT_PIN_SALT].orEmpty()
    val expectedHash = snap[Keys.VAULT_PIN_HASH].orEmpty()
    return PinHasher.verifyPin(enteredPin, salt, expectedHash)
  }

  suspend fun updateVaultPin(newPin: String) {
    val salt = PinHasher.generateSalt()
    val hash = PinHasher.hashPin(newPin, salt)
    dataStore.edit {
      it[Keys.VAULT_PIN_SALT] = salt
      it[Keys.VAULT_PIN_HASH] = hash
    }
  }

  suspend fun recordVaultUnlocked(timestampMs: Long = System.currentTimeMillis()) {
    dataStore.edit { it[Keys.VAULT_LAST_UNLOCKED_AT] = timestampMs }
  }

  suspend fun clearVaultUnlockSession() {
    dataStore.edit { it[Keys.VAULT_LAST_UNLOCKED_AT] = 0L }
  }

  suspend fun setLastMediaSyncCount(count: Int) {
    dataStore.edit { it[Keys.LAST_MEDIA_SYNC_COUNT] = count }
  }

  /** Opt-in: batch trash/favorite operations also sync to the system MediaStore. */
  suspend fun setSystemMediaSync(enabled: Boolean) {
    dataStore.edit { it[Keys.SYSTEM_MEDIA_SYNC] = enabled }
  }

  suspend fun setMediaManagementPromptSeen() {
    dataStore.edit { it[Keys.MEDIA_MGMT_PROMPT_SEEN] = true }
  }

  suspend fun updatePalette(palette: ThemePalette) {
    dataStore.edit {
      it[Keys.PALETTE] = palette.name
      it[Keys.APPEARANCE_MODE] =
        if (palette == ThemePalette.BOTANICAL_LIGHT) AppearanceMode.LIGHT.name
        else AppearanceMode.DARK.name
    }
  }

  suspend fun updateAppearanceMode(mode: AppearanceMode) {
    dataStore.edit {
      it[Keys.APPEARANCE_MODE] = mode.name
      if (mode == AppearanceMode.LIGHT) {
        it[Keys.PALETTE] = ThemePalette.BOTANICAL_LIGHT.name
      } else if (it[Keys.PALETTE] == ThemePalette.BOTANICAL_LIGHT.name) {
        it[Keys.PALETTE] = ThemePalette.TOAD_GREEN.name
      }
    }
  }

  suspend fun setUseDynamicColors(enabled: Boolean) {
    dataStore.edit { it[Keys.USE_DYNAMIC_COLORS] = enabled }
  }

  suspend fun updateLayoutMode(mode: LayoutMode) {
    dataStore.edit { it[Keys.LAYOUT_MODE] = mode.name }
  }

  suspend fun updateColumns(columns: Int) {
    dataStore.edit { it[Keys.COLUMNS] = columns.coerceIn(2, 10) }
  }

  suspend fun updateGroupBy(groupBy: GroupByMode) {
    dataStore.edit { it[Keys.GROUP_BY] = groupBy.name }
  }

  suspend fun setShowStories(show: Boolean) {
    dataStore.edit { it[Keys.SHOW_STORIES] = show }
  }

  suspend fun setHideScreenshots(hide: Boolean) {
    dataStore.edit { it[Keys.HIDE_SCREENSHOTS] = hide }
  }

  suspend fun setAiPeople(enabled: Boolean) {
    dataStore.edit { it[Keys.AI_PEOPLE] = enabled }
  }

  suspend fun setAiMagicSearch(enabled: Boolean) {
    dataStore.edit { it[Keys.AI_MAGIC_SEARCH] = enabled }
  }

  suspend fun setAiThings(enabled: Boolean) {
    dataStore.edit { it[Keys.AI_THINGS] = enabled }
  }

  suspend fun setClusteringThreshold(threshold: Float) {
    dataStore.edit { it[Keys.CLUSTERING_THRESHOLD] = threshold.coerceIn(0.10f, 0.40f) }
  }

  suspend fun setFaceModelInstalled(installed: Boolean) {
    dataStore.edit { it[Keys.FACE_MODEL_INSTALLED] = installed }
  }

  suspend fun setSemanticModelInstalled(installed: Boolean) {
    dataStore.edit { it[Keys.SEMANTIC_MODEL_INSTALLED] = installed }
  }

  suspend fun setDepthModelInstalled(installed: Boolean) {
    dataStore.edit { it[Keys.DEPTH_MODEL_INSTALLED] = installed }
  }

  suspend fun setShowLocationExif(enabled: Boolean) {
    dataStore.edit { it[Keys.SHOW_LOCATION_EXIF] = enabled }
  }

  suspend fun setVaultBiometrics(enabled: Boolean) {
    dataStore.edit { it[Keys.VAULT_BIOMETRICS] = enabled }
  }

  suspend fun setVaultAutoLock(autoLock: VaultAutoLock) {
    dataStore.edit { it[Keys.VAULT_AUTO_LOCK] = autoLock.name }
  }

  suspend fun setCacheSizeMb(sizeMb: Float) {
    dataStore.edit { it[Keys.CACHE_SIZE_MB] = sizeMb }
  }

  suspend fun setMlIndexSizeMb(sizeMb: Float) {
    dataStore.edit { it[Keys.ML_INDEX_SIZE_MB] = sizeMb }
  }

  companion object {
    const val DEFAULT_PIN = "1234"
  }
}
