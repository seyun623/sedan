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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Used until the user picks a model from the list their key can access. */
const val DEFAULT_AI_MODEL = "gemini-2.5-flash"

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
  /** AI category suggestion (user's own Gemini API key). */
  val aiEnabled: Boolean = false,
  /** API key encrypted with the Android Keystore (never stored in plain text). */
  val aiApiKeyEncrypted: String? = null,
  val aiModel: String = DEFAULT_AI_MODEL
) {
  val hasAiKey: Boolean get() = !aiApiKeyEncrypted.isNullOrBlank()
  val isAiReady: Boolean get() = aiEnabled && hasAiKey

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
    val AI_KEY = stringPreferencesKey("ai_api_key_enc")
    val AI_MODEL = stringPreferencesKey("ai_model")
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
      aiApiKeyEncrypted = p[Keys.AI_KEY],
      aiModel = p[Keys.AI_MODEL] ?: DEFAULT_AI_MODEL
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
  suspend fun setAiModel(model: String) = store.edit { it[Keys.AI_MODEL] = model }
  suspend fun setAiApiKeyEncrypted(encrypted: String?) = store.edit {
    if (encrypted == null) { it.remove(Keys.AI_KEY); it[Keys.AI_ENABLED] = false } else it[Keys.AI_KEY] = encrypted
  }
  suspend fun setLastBackupAt(millis: Long) = store.edit { it[Keys.LAST_BACKUP] = millis }
  suspend fun setOnboardingDone() = store.edit { it[Keys.ONBOARDING] = true }
  suspend fun setDefaultCaptureAccount(id: Long?) = store.edit {
    if (id == null) it.remove(Keys.DEFAULT_ACCOUNT) else it[Keys.DEFAULT_ACCOUNT] = id
  }
}
