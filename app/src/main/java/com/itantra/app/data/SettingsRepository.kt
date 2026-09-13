package com.itantra.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

private val Context.itantraSettingsDataStore by preferencesDataStore(name = "itantra_settings")

/**
 * All persisted user settings. Defaults mirror the pre-persistence hardcoded
 * values so first launch is visually identical.
 */
data class AppSettings(
    val themeMode: String = DEFAULT_THEME_MODE,
    val callsign: String = DEFAULT_CALLSIGN,
    val txPower: String = DEFAULT_TX_POWER,
    val beaconInterval: Int = DEFAULT_BEACON_INTERVAL,
    val meshHopLimit: Int = DEFAULT_MESH_HOP_LIMIT,
    val vadSensitivity: String = DEFAULT_VAD_SENSITIVITY,
    val noiseSuppressionEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val zeroLogPrivacy: Boolean = false,
    val forceMaxVolumeAlerts: Boolean = true,
    val isLowPowerListeningEnabled: Boolean = true,
    val userName: String = "",
    val userAge: Int? = null,
    val userGender: String = "Male",
    val userLanguages: Set<String> = setOf("hi", "en"),
    val relativeRelation: String = "Parent",
    val relativePhone: String = "",
    val isOnboardingCompleted: Boolean = false
) {
    companion object {
        const val DEFAULT_THEME_MODE = "light"
        const val DEFAULT_CALLSIGN = "ITANTRA-UNIT-ALPHA"
        const val DEFAULT_TX_POWER = "Balanced (500m)"
        const val DEFAULT_BEACON_INTERVAL = 30
        const val DEFAULT_MESH_HOP_LIMIT = 5
        const val DEFAULT_VAD_SENSITIVITY = "Balanced"
        const val NODE_ID_UNSET = -1L
    }
}

/**
 * DataStore-backed settings persistence. Exposes hot [StateFlow]s for the
 * settings and for the set of installed model language tags (so installed
 * packs are remembered across restarts), plus suspend setters.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CALLSIGN = stringPreferencesKey("callsign")
        val TX_POWER = stringPreferencesKey("tx_power")
        val BEACON_INTERVAL = intPreferencesKey("beacon_interval")
        val MESH_HOP_LIMIT = intPreferencesKey("mesh_hop_limit")
        val VAD_SENSITIVITY = stringPreferencesKey("vad_sensitivity")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val ZERO_LOG_PRIVACY = booleanPreferencesKey("zero_log_privacy")
        val FORCE_MAX_VOLUME_ALERTS = booleanPreferencesKey("force_max_volume_alerts")
        val LOW_POWER_LISTENING = booleanPreferencesKey("low_power_listening")
        val INSTALLED_MODEL_TAGS = stringSetPreferencesKey("installed_model_tags")
        val NODE_ID = longPreferencesKey("node_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val USER_LANGUAGES = stringSetPreferencesKey("user_languages")
        val RELATIVE_RELATION = stringPreferencesKey("relative_relation")
        val RELATIVE_PHONE = stringPreferencesKey("relative_phone")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PAIRED_WALKIE_NODE_IDS = stringSetPreferencesKey("paired_walkie_node_ids")
        val SELECTED_LANGUAGE_CODE = stringPreferencesKey("selected_language_code")
        val POWER_BUTTON_SOS_ENABLED = booleanPreferencesKey("power_button_sos_enabled")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings> = appContext.itantraSettingsDataStore.data
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.DEFAULT_THEME_MODE,
                callsign = prefs[Keys.CALLSIGN] ?: AppSettings.DEFAULT_CALLSIGN,
                txPower = prefs[Keys.TX_POWER] ?: AppSettings.DEFAULT_TX_POWER,
                beaconInterval = prefs[Keys.BEACON_INTERVAL] ?: AppSettings.DEFAULT_BEACON_INTERVAL,
                meshHopLimit = prefs[Keys.MESH_HOP_LIMIT] ?: AppSettings.DEFAULT_MESH_HOP_LIMIT,
                vadSensitivity = prefs[Keys.VAD_SENSITIVITY] ?: AppSettings.DEFAULT_VAD_SENSITIVITY,
                noiseSuppressionEnabled = prefs[Keys.NOISE_SUPPRESSION] ?: true,
                keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
                zeroLogPrivacy = prefs[Keys.ZERO_LOG_PRIVACY] ?: false,
                forceMaxVolumeAlerts = prefs[Keys.FORCE_MAX_VOLUME_ALERTS] ?: true,
                isLowPowerListeningEnabled = prefs[Keys.LOW_POWER_LISTENING] ?: true,
                userName = prefs[Keys.USER_NAME] ?: "",
                userAge = prefs[Keys.USER_AGE],
                userGender = prefs[Keys.USER_GENDER] ?: "Male",
                userLanguages = prefs[Keys.USER_LANGUAGES] ?: setOf("hi", "en"),
                relativeRelation = prefs[Keys.RELATIVE_RELATION] ?: "Parent",
                relativePhone = prefs[Keys.RELATIVE_PHONE] ?: "",
                isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val installedLanguageTags: StateFlow<Set<String>> = appContext.itantraSettingsDataStore.data
        .map { prefs -> prefs[Keys.INSTALLED_MODEL_TAGS] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val pairedWalkieNodeIds: StateFlow<Set<Long>> = appContext.itantraSettingsDataStore.data
        .map { prefs ->
            prefs[Keys.PAIRED_WALKIE_NODE_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val selectedLanguageCode: StateFlow<String?> = appContext.itantraSettingsDataStore.data
        .map { prefs -> prefs[Keys.SELECTED_LANGUAGE_CODE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val powerButtonSosEnabled: StateFlow<Boolean> = appContext.itantraSettingsDataStore.data
        .map { prefs -> prefs[Keys.POWER_BUTTON_SOS_ENABLED] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    /** Sentinel meaning "no persistent mesh node id assigned yet". */
    val nodeId: StateFlow<Long> = appContext.itantraSettingsDataStore.data
        .map { prefs -> prefs[Keys.NODE_ID] ?: AppSettings.NODE_ID_UNSET }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings.NODE_ID_UNSET)

    /**
     * Returns this installation's stable mesh node id, generating and
     * persisting one on first access. A random (positive) 63-bit id is used;
     * note that [clearAll] wipes it too, intentionally issuing a fresh
     * identity after an emergency wipe.
     */
    suspend fun ensureNodeId(): Long {
        val existing = appContext.itantraSettingsDataStore.data.first()[Keys.NODE_ID]
        if (existing != null && existing > 0) return existing
        val generated = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
        put(Keys.NODE_ID, generated)
        return generated
    }

    suspend fun setSelectedLanguageCode(value: String) = put(Keys.SELECTED_LANGUAGE_CODE, value)
    suspend fun setPowerButtonSosEnabled(value: Boolean) = put(Keys.POWER_BUTTON_SOS_ENABLED, value)

    suspend fun setThemeMode(value: String) = put(Keys.THEME_MODE, value)
    suspend fun setCallsign(value: String) = put(Keys.CALLSIGN, value)
    suspend fun setTxPower(value: String) = put(Keys.TX_POWER, value)
    suspend fun setBeaconInterval(value: Int) = put(Keys.BEACON_INTERVAL, value)
    suspend fun setMeshHopLimit(value: Int) = put(Keys.MESH_HOP_LIMIT, value)
    suspend fun setVadSensitivity(value: String) = put(Keys.VAD_SENSITIVITY, value)
    suspend fun setNoiseSuppressionEnabled(value: Boolean) = put(Keys.NOISE_SUPPRESSION, value)
    suspend fun setKeepScreenAwake(value: Boolean) = put(Keys.KEEP_SCREEN_AWAKE, value)
    suspend fun setZeroLogPrivacy(value: Boolean) = put(Keys.ZERO_LOG_PRIVACY, value)
    suspend fun setForceMaxVolumeAlerts(value: Boolean) = put(Keys.FORCE_MAX_VOLUME_ALERTS, value)
    suspend fun setLowPowerListeningEnabled(value: Boolean) = put(Keys.LOW_POWER_LISTENING, value)
    suspend fun setInstalledLanguageTags(value: Set<String>) = put(Keys.INSTALLED_MODEL_TAGS, value)
    suspend fun setUserName(value: String) = put(Keys.USER_NAME, value)
    suspend fun setUserAge(value: Int?) = if (value != null) put(Keys.USER_AGE, value) else appContext.itantraSettingsDataStore.edit { it.remove(Keys.USER_AGE) }
    suspend fun setUserGender(value: String) = put(Keys.USER_GENDER, value)
    suspend fun setUserLanguages(value: Set<String>) = put(Keys.USER_LANGUAGES, value)
    suspend fun setRelativeRelation(value: String) = put(Keys.RELATIVE_RELATION, value)
    suspend fun setRelativePhone(value: String) = put(Keys.RELATIVE_PHONE, value)
    suspend fun setOnboardingCompleted(value: Boolean) = put(Keys.ONBOARDING_COMPLETED, value)

    suspend fun addPairedWalkieNodeId(nodeId: Long) {
        appContext.itantraSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.PAIRED_WALKIE_NODE_IDS] ?: emptySet()
            prefs[Keys.PAIRED_WALKIE_NODE_IDS] = current + nodeId.toString()
        }
    }

    suspend fun removePairedWalkieNodeId(nodeId: Long) {
        appContext.itantraSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.PAIRED_WALKIE_NODE_IDS] ?: emptySet()
            prefs[Keys.PAIRED_WALKIE_NODE_IDS] = current - nodeId.toString()
        }
    }

    suspend fun setPairedWalkieNodeIds(nodeIds: Set<Long>) {
        appContext.itantraSettingsDataStore.edit { prefs ->
            prefs[Keys.PAIRED_WALKIE_NODE_IDS] = nodeIds.map { it.toString() }.toSet()
        }
    }

    suspend fun saveOnboardingProfile(
        name: String,
        age: Int?,
        gender: String,
        languages: Set<String>,
        relation: String,
        phone: String
    ) {
        appContext.itantraSettingsDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
            if (age != null) prefs[Keys.USER_AGE] = age else prefs.remove(Keys.USER_AGE)
            prefs[Keys.USER_GENDER] = gender
            prefs[Keys.USER_LANGUAGES] = languages
            prefs[Keys.RELATIVE_RELATION] = relation
            prefs[Keys.RELATIVE_PHONE] = phone
            prefs[Keys.ONBOARDING_COMPLETED] = true
            if (name.isNotBlank()) {
                prefs[Keys.CALLSIGN] = name.trim().uppercase().replace(Regex("\\s+"), "-").take(18)
            }
        }
    }

    /**
     * Emergency wipe: removes every persisted preference. Both hot flows
     * revert to their defaults on the next DataStore emission.
     */
    suspend fun clearAll() {
        appContext.itantraSettingsDataStore.edit { it.clear() }
    }

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        appContext.itantraSettingsDataStore.edit { prefs -> prefs[key] = value }
    }
}
