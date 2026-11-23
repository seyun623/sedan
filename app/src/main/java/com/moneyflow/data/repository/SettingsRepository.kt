package com.moneyflow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneyflow.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("시스템 설정"), LIGHT("라이트"), DARK("다크") }

data class AppSettings(
  /** App lock is on when a PIN has been set. */
  val pinHash: String? = null,
  val pinSalt: String? = null,
  val biometricEnabled: Boolean = false,
  /** Seconds in background before the app locks again. */
  val lockTimeoutSeconds: Int = 30,
  val failedPinAttempts: Int = 0,
  val pinLockedUntil: Long = 0L,
  /** Wall-clock time of the last wrong PIN (detects the device clock being set back). */
  val pinLastFailureAt: Long = 0L,
  val secureScreen: Boolean = true,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val notificationCaptureEnabled: Boolean = false,
  val smsCaptureEnabled: Boolean = false,
  /** Account used for auto-captured transactions when no account matches the card/bank name. */
  val autoCaptureDefaultAccountId: Long? = null,
  val onboardingDone: Boolean = false,
  /** When a backup file was last created (0 = never). */
  val lastBackupAt: Long = 0L,
  /** AI (user's own API key). */
  val aiEnabled: Boolean = false,
  val aiProvider: AiProvider = AiProvider.GEMINI,
  /** API keys per provider, encrypted with the Android Keystore (never stored in plain text). */
  val aiKeysEncrypted: Map<AiProvider, String> = emptyMap(),
  val aiModels: Map<AiProvider, String> = emptyMap(),
  /** Base URL for [AiProvider.CUSTOM]. */
  val aiCustomBaseUrl: String = "",
  val cardAlertsEnabled: Boolean = false,
  /** Alerts already sent ("kind|account|period"), so each is sent once. */
  val sentCardAlerts: Set<String> = emptySet(),
  /** Last "실적" figure read from each card company screen, by account id. */
  val cardPerformance: Map<Long, CardPerformance> = emptyMap()
) {
  val aiApiKeyEncrypted: String? get() = aiKeysEncrypted[aiProvider]
  val aiModel: String get() = aiModels[aiProvider]?.takeIf { it.isNotBlank() } ?: aiProvider.defaultModel
  val aiBaseUrl: String? get() = if (aiProvider == AiProvider.CUSTOM) aiCustomBaseUrl else aiProvider.defaultBaseUrl
  val hasAiKey: Boolean get() = !aiApiKeyEncrypted.isNullOrBlank()
  val isAiReady: Boolean
    get() = aiEnabled && hasAiKey && aiModel.isNotBlank() &&
      (aiProvider != AiProvider.CUSTOM || aiCustomBaseUrl.startsWith("https://"))

  val isLockEnabled: Boolean get() = pinHash != null && pinSalt != null
}

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

  private object Keys {
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_seconds")
    val FAILED = intPreferencesKey("failed_pin_attempts")
    val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
    val LAST_FAILURE = longPreferencesKey("pin_last_failure_at")
    val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
    val THEME = stringPreferencesKey("theme_mode")
    val NOTI_CAPTURE = booleanPreferencesKey("notification_capture")
    val SMS_CAPTURE = booleanPreferencesKey("sms_capture")
    val DEFAULT_ACCOUNT = longPreferencesKey("auto_capture_default_account")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
    val LAST_BACKUP = longPreferencesKey("last_backup_at")
    val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    val CARD_PERFORMANCE = stringPreferencesKey("card_performance_json")
    val CARD_ALERTS = booleanPreferencesKey("card_alerts_enabled")
    val SENT_CARD_ALERTS = androidx.datastore.preferences.core.stringSetPreferencesKey("sent_card_alerts")
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val AI_CUSTOM_URL = stringPreferencesKey("ai_custom_base_url")
    /** v1.1.0 single Gemini key / model (read as the Gemini entries). */
    val LEGACY_AI_KEY = stringPreferencesKey("ai_api_key_enc")
    val LEGACY_AI_MODEL = stringPreferencesKey("ai_model")
    fun aiKey(p: AiProvider) = stringPreferencesKey("ai_key_${p.name}")
    fun aiModel(p: AiProvider) = stringPreferencesKey("ai_model_${p.name}")
  }

  val settings: Flow<AppSettings> = store.data.map { p ->
    AppSettings(
      pinHash = p[Keys.PIN_HASH],
      pinSalt = p[Keys.PIN_SALT],
      biometricEnabled = p[Keys.BIOMETRIC] ?: false,
      lockTimeoutSeconds = p[Keys.LOCK_TIMEOUT] ?: 30,
      failedPinAttempts = p[Keys.FAILED] ?: 0,
      pinLockedUntil = p[Keys.LOCKED_UNTIL] ?: 0L,
      pinLastFailureAt = p[Keys.LAST_FAILURE] ?: 0L,
      secureScreen = p[Keys.SECURE_SCREEN] ?: true,
      themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
      notificationCaptureEnabled = p[Keys.NOTI_CAPTURE] ?: false,
      smsCaptureEnabled = p[Keys.SMS_CAPTURE] ?: false,
      autoCaptureDefaultAccountId = p[Keys.DEFAULT_ACCOUNT],
      onboardingDone = p[Keys.ONBOARDING] ?: false,
      lastBackupAt = p[Keys.LAST_BACKUP] ?: 0L,
      aiEnabled = p[Keys.AI_ENABLED] ?: false,
      aiProvider = p[Keys.AI_PROVIDER]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.GEMINI,
      aiKeysEncrypted = AiProvider.entries.mapNotNull { pr ->
        (p[Keys.aiKey(pr)] ?: if (pr == AiProvider.GEMINI) p[Keys.LEGACY_AI_KEY] else null)?.let { pr to it }
      }.toMap(),
      aiModels = AiProvider.entries.mapNotNull { pr ->
        (p[Keys.aiModel(pr)] ?: if (pr == AiProvider.GEMINI) p[Keys.LEGACY_AI_MODEL] else null)?.let { pr to it }
      }.toMap(),
      aiCustomBaseUrl = p[Keys.AI_CUSTOM_URL] ?: "",
      cardPerformance = parsePerformance(p[Keys.CARD_PERFORMANCE]),
      cardAlertsEnabled = p[Keys.CARD_ALERTS] ?: false,
      sentCardAlerts = p[Keys.SENT_CARD_ALERTS] ?: emptySet()
    )
  }

  suspend fun current(): AppSettings = settings.first()

  suspend fun setPin(hash: String, salt: String) = store.edit {
    it[Keys.PIN_HASH] = hash
    it[Keys.PIN_SALT] = salt
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
  }

  suspend fun clearPin() = store.edit {
    it.remove(Keys.PIN_HASH)
    it.remove(Keys.PIN_SALT)
    it[Keys.BIOMETRIC] = false
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
  }

  suspend fun recordPinFailure(attempts: Int, lockedUntil: Long, failedAt: Long) = store.edit {
    it[Keys.FAILED] = attempts
    it[Keys.LOCKED_UNTIL] = lockedUntil
    it[Keys.LAST_FAILURE] = failedAt
  }

  suspend fun resetPinFailures() = store.edit {
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
  }

  suspend fun setBiometricEnabled(enabled: Boolean) = store.edit { it[Keys.BIOMETRIC] = enabled }
  suspend fun setLockTimeout(seconds: Int) = store.edit { it[Keys.LOCK_TIMEOUT] = seconds }
  suspend fun setSecureScreen(enabled: Boolean) = store.edit { it[Keys.SECURE_SCREEN] = enabled }
  suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
  suspend fun setNotificationCapture(enabled: Boolean) = store.edit { it[Keys.NOTI_CAPTURE] = enabled }
  suspend fun setSmsCapture(enabled: Boolean) = store.edit { it[Keys.SMS_CAPTURE] = enabled }
  suspend fun setAiEnabled(enabled: Boolean) = store.edit { it[Keys.AI_ENABLED] = enabled }
  suspend fun setCardAlertsEnabled(enabled: Boolean) = store.edit { it[Keys.CARD_ALERTS] = enabled }

  /** Records sent alerts; keeps only the most recent ones. */
  suspend fun markCardAlertsSent(keys: Collection<String>) = store.edit {
    it[Keys.SENT_CARD_ALERTS] = ((it[Keys.SENT_CARD_ALERTS] ?: emptySet()) + keys).toList().takeLast(200).toSet()
  }

  suspend fun setCardPerformance(accountId: Long, value: CardPerformance) = store.edit {
    val map = parsePerformance(it[Keys.CARD_PERFORMANCE]).toMutableMap()
    map[accountId] = value
    val json = org.json.JSONObject()
    map.forEach { (id, v) ->
      json.put(id.toString(), org.json.JSONObject().put("label", v.label).put("amount", v.amount).put("at", v.fetchedAt))
    }
    it[Keys.CARD_PERFORMANCE] = json.toString()
  }

  private fun parsePerformance(raw: String?): Map<Long, CardPerformance> = runCatching {
    val json = org.json.JSONObject(raw ?: return emptyMap())
    json.keys().asSequence().associate { k ->
      val o = json.getJSONObject(k)
      k.toLong() to CardPerformance(o.optString("label"), o.optLong("amount"), o.optLong("at"))
    }
  }.getOrDefault(emptyMap())

  suspend fun setAiProvider(provider: AiProvider) = store.edit { it[Keys.AI_PROVIDER] = provider.name }
  suspend fun setAiCustomBaseUrl(url: String) = store.edit { it[Keys.AI_CUSTOM_URL] = url.trim() }

  /** Sets the model of the currently selected provider. */
  suspend fun setAiModel(model: String) = store.edit {
    val provider = it[Keys.AI_PROVIDER]?.let { n -> runCatching { AiProvider.valueOf(n) }.getOrNull() } ?: AiProvider.GEMINI
    it[Keys.aiModel(provider)] = model
  }

  /** Sets (or with null removes) the key of the currently selected provider. Removing turns AI off. */
  suspend fun setAiApiKeyEncrypted(encrypted: String?) = store.edit {
    val provider = it[Keys.AI_PROVIDER]?.let { n -> runCatching { AiProvider.valueOf(n) }.getOrNull() } ?: AiProvider.GEMINI
    if (provider == AiProvider.GEMINI) it.remove(Keys.LEGACY_AI_KEY)
    if (encrypted == null) {
      it.remove(Keys.aiKey(provider))
      it[Keys.AI_ENABLED] = false
    } else {
      it[Keys.aiKey(provider)] = encrypted
    }
  }
  suspend fun setLastBackupAt(millis: Long) = store.edit { it[Keys.LAST_BACKUP] = millis }
  suspend fun setOnboardingDone() = store.edit { it[Keys.ONBOARDING] = true }
  suspend fun setDefaultCaptureAccount(id: Long?) = store.edit {
    if (id == null) it.remove(Keys.DEFAULT_ACCOUNT) else it[Keys.DEFAULT_ACCOUNT] = id
  }
}
