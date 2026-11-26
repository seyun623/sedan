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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
  /** Lockout end on the monotonic clock, valid only within boot [pinLockBoot]. */
  val pinLockedUntilElapsed: Long = 0L,
  val pinLockBoot: String = "",
  val deviceLockEnabled: Boolean = false,
  /** A recovery code was made for the PIN (only its hash is kept). */
  val hasRecoveryCode: Boolean = false,
  val recoveryHash: String? = null,
  val recoverySalt: String? = null,
  val secureScreen: Boolean = true,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val notificationCaptureEnabled: Boolean = false,
  val smsCaptureEnabled: Boolean = false,
  /** Account used for auto-captured transactions when no account matches the card/bank name. */
  val autoCaptureDefaultAccountId: Long? = null,
  /** A payment from a card company / bank with no matching account creates that account. */
  val autoCreateCaptureAccounts: Boolean = true,
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
  /** Version whose "what's new" was last shown (null = never). */
  val lastSeenVersion: String? = null,
  /** Alerts already sent ("kind|account|period"), so each is sent once. */
  val sentCardAlerts: Set<String> = emptySet(),
  /** Last "실적" figure read from each card company screen, by account id. */
  val cardPerformance: Map<Long, CardPerformance> = emptyMap(),
  /** Next bill of each card account (from the card company's screen or its 청구 안내). */
  val cardBills: Map<Long, com.moneyflow.domain.card.CardBill> = emptyMap(),
  /** 2.20: point balances read from card company screens, by card account. */
  val cardPoints: Map<Long, com.moneyflow.domain.card.CardPoint> = emptyMap(),
  /** Auto backup: folder picked with the system file picker (tree URI). */
  val autoBackupFolder: String? = null,
  /** Auto backup password, encrypted with the Android Keystore. */
  val autoBackupPasswordEncrypted: String? = null,
  val autoBackupIntervalDays: Int = 7,
  val autoBackupLastAt: Long = 0L,
  /** Last auto backup failure (null = last run succeeded). */
  val autoBackupError: String? = null,
  /** Remind when there has been no backup for a while. */
  val backupReminderEnabled: Boolean = true,
  /** Budget 80% / 100% alerts. */
  val budgetAlertsEnabled: Boolean = false,
  /** Last month's summary on the 1st. */
  val monthlySummaryEnabled: Boolean = false,
  /** 2.19: every Monday, last week's spending against the usual. */
  val weeklyReportEnabled: Boolean = false,
  /** Split installment purchases (auto-captured / imported) into monthly parts. */
  val splitInstallments: Boolean = false,
  /** Google Drive bot (service account) key, encrypted with the Keystore. */
  val driveKeyEncrypted: String? = null,
  val driveBotEmail: String? = null,
  /** The family folder shared with the bot. */
  val driveFolderId: String? = null,
  val driveFolderName: String? = null,
  /** Name of the file made for the connection test. */
  val driveTestFileName: String? = null,
  /** Family monthly budget for the combined view (0 = not set). */
  val familyBudget: Long = 0L,
  /** 총급여 (세전 연봉) for the 연말정산 카드 소득공제 미리보기; 0 = not entered. */
  val annualSalary: Long = 0L,
  /** 2.18: last month's leftover (or overspending) is added to this month's budget. */
  val budgetCarryOver: Boolean = false,
  val billRemindersEnabled: Boolean = true,
  /** When the safety copy made just before the last restore was taken (0 = none). */
  val preRestoreAt: Long = 0L,
  val preRestorePasswordEncrypted: String? = null,
  /** 간단 모드: big letters, one home screen with big buttons. */
  val simpleMode: Boolean = false,
  val ledgerSavedFilters: String? = null,
  /** Duplicate groups the user marked "not a duplicate" (ids joined by commas). */
  val cleanupDismissed: Set<String> = emptySet(),
  /** Family auto-sync through the Drive bot: my name, family password (Keystore-encrypted), my file in the folder. */
  val familySyncName: String? = null,
  val familySyncPasswordEncrypted: String? = null,
  val familySyncFileName: String? = null,
  val familySyncLastAt: Long = 0L,
  val familySyncLastUploadAt: Long = 0L,
  val familySyncError: String? = null,
  /** Repeating-payment suggestions the user said are not 고정 거래. */
  val dismissedRecurringSuggestions: Set<String> = emptySet(),
  /** Card company pages to read automatically after login, by card site id. */
  val cardSavedPages: Map<String, List<com.moneyflow.domain.cardimport.SavedPage>> = emptyMap(),
  /** Card sites the user switched to the PC (true) or mobile (false) page; others use the site default. */
  val cardDesktopModes: Map<String, Boolean> = emptyMap(),
  /** Show amounts on the home screen widget while app lock is on. */
  val widgetShowAmountsWhenLocked: Boolean = false
) {
  val familySyncEnabled: Boolean get() =
    familySyncPasswordEncrypted != null && familySyncFileName != null && driveKeyEncrypted != null && driveFolderId != null

  val autoBackupEnabled: Boolean get() = autoBackupFolder != null && autoBackupPasswordEncrypted != null
  val aiApiKeyEncrypted: String? get() = aiKeysEncrypted[aiProvider]
  val aiModel: String get() = aiModels[aiProvider]?.takeIf { it.isNotBlank() } ?: aiProvider.defaultModel
  val aiBaseUrl: String? get() = if (aiProvider == AiProvider.CUSTOM) aiCustomBaseUrl else aiProvider.defaultBaseUrl
  val hasAiKey: Boolean get() = !aiApiKeyEncrypted.isNullOrBlank()
  val isAiReady: Boolean
    get() = aiEnabled && hasAiKey && aiModel.isNotBlank() &&
      (aiProvider != AiProvider.CUSTOM || aiCustomBaseUrl.startsWith("https://"))

  /** App lock with its own PIN. */
  val usesPinLock: Boolean get() = pinHash != null && pinSalt != null
  /** App lock that asks for the phone's own screen lock (fingerprint, face, pattern, phone PIN). */
  val usesDeviceLock: Boolean get() = deviceLockEnabled && !usesPinLock
  val isLockEnabled: Boolean get() = usesPinLock || deviceLockEnabled
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
    val LOCKED_UNTIL_ELAPSED = longPreferencesKey("pin_locked_until_elapsed")
    val LOCK_BOOT = stringPreferencesKey("pin_lock_boot_id")
    val DEVICE_LOCK = booleanPreferencesKey("device_lock_enabled")
    val RECOVERY_HASH = stringPreferencesKey("pin_recovery_hash")
    val RECOVERY_SALT = stringPreferencesKey("pin_recovery_salt")
    val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
    val THEME = stringPreferencesKey("theme_mode")
    val NOTI_CAPTURE = booleanPreferencesKey("notification_capture")
    val SMS_CAPTURE = booleanPreferencesKey("sms_capture")
    val DEFAULT_ACCOUNT = longPreferencesKey("auto_capture_default_account")
    val AUTO_CREATE_ACCOUNTS = booleanPreferencesKey("auto_capture_create_accounts")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
    val LAST_BACKUP = longPreferencesKey("last_backup_at")
    val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    val CARD_PERFORMANCE = stringPreferencesKey("card_performance_json")
    val CARD_BILLS = stringPreferencesKey("card_bills_json")
    val CARD_POINTS = stringPreferencesKey("card_points_json")
    val CARD_ALERTS = booleanPreferencesKey("card_alerts_enabled")
    val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
    val SENT_CARD_ALERTS = androidx.datastore.preferences.core.stringSetPreferencesKey("sent_card_alerts")
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val AI_CUSTOM_URL = stringPreferencesKey("ai_custom_base_url")
    /** v1.1.0 single Gemini key / model (read as the Gemini entries). */
    val LEGACY_AI_KEY = stringPreferencesKey("ai_api_key_enc")
    val LEGACY_AI_MODEL = stringPreferencesKey("ai_model")
    val AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder")
    val AUTO_BACKUP_PW = stringPreferencesKey("auto_backup_password_enc")
    val AUTO_BACKUP_DAYS = intPreferencesKey("auto_backup_interval_days")
    val AUTO_BACKUP_LAST = longPreferencesKey("auto_backup_last_at")
    val AUTO_BACKUP_ERROR = stringPreferencesKey("auto_backup_error")
    val BACKUP_REMINDER = booleanPreferencesKey("backup_reminder")
    val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
    val MONTHLY_SUMMARY = booleanPreferencesKey("monthly_summary")
    val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
    val WIDGET_SHOW_LOCKED = booleanPreferencesKey("widget_show_amounts_locked")
    val SPLIT_INSTALLMENTS = booleanPreferencesKey("split_installments")
    val INSTALL_ID = stringPreferencesKey("install_id")
    val CARD_SAVED_PAGES = stringPreferencesKey("card_saved_pages_json")
    val CARD_VIEW_MODES = androidx.datastore.preferences.core.stringSetPreferencesKey("card_view_modes")
    val FAMILY_BUDGET = longPreferencesKey("family_budget")
    val ANNUAL_SALARY = longPreferencesKey("annual_salary")
    val BUDGET_CARRY_OVER = booleanPreferencesKey("budget_carry_over")
    val DRIVE_KEY = stringPreferencesKey("drive_bot_key_enc")
    val DRIVE_BOT_EMAIL = stringPreferencesKey("drive_bot_email")
    val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
    val DRIVE_FOLDER_NAME = stringPreferencesKey("drive_folder_name")
    val DRIVE_TEST_FILE = stringPreferencesKey("drive_test_file_name")
    val BILL_REMINDERS = booleanPreferencesKey("bill_reminders")
    val PRE_RESTORE_AT = longPreferencesKey("pre_restore_at")
    val PRE_RESTORE_PW = stringPreferencesKey("pre_restore_password_enc")
    val SIMPLE_MODE = booleanPreferencesKey("simple_mode")
    val LEDGER_FILTERS = stringPreferencesKey("ledger_saved_filters_json")
    val CLEANUP_DISMISSED = androidx.datastore.preferences.core.stringSetPreferencesKey("cleanup_dismissed")
    val FAMILY_SYNC_NAME = stringPreferencesKey("family_sync_name")
    val FAMILY_SYNC_PW = stringPreferencesKey("family_sync_password_enc")
    val FAMILY_SYNC_FILE = stringPreferencesKey("family_sync_file_name")
    val FAMILY_SYNC_LAST = longPreferencesKey("family_sync_last_at")
    val FAMILY_SYNC_LAST_UP = longPreferencesKey("family_sync_last_upload_at")
    val FAMILY_SYNC_ERROR = stringPreferencesKey("family_sync_error")
    /** Data version: raised on every change; [FAMILY_SYNC_UP_GEN] is the version last uploaded. */
    val FAMILY_SYNC_GEN = longPreferencesKey("family_sync_generation")
    val FAMILY_SYNC_UP_GEN = longPreferencesKey("family_sync_uploaded_generation")
    val FAMILY_SYNC_FAILED = stringPreferencesKey("family_sync_failed_json")
    /** Drive modifiedTime of my family file right after this phone last wrote it. */
    val FAMILY_SYNC_MY_MODIFIED = stringPreferencesKey("family_sync_my_modified")
    val FAMILY_SYNC_SEEN = stringPreferencesKey("family_sync_seen_json")
    val RECURRING_DISMISSED = androidx.datastore.preferences.core.stringSetPreferencesKey("recurring_suggestions_dismissed")
    fun aiKey(p: AiProvider) = stringPreferencesKey("ai_key_${p.name}")
    fun aiModel(p: AiProvider) = stringPreferencesKey("ai_model_${p.name}")
  }

  val settings: Flow<AppSettings> = store.data.catch { e ->
    // A damaged settings file must not crash every screen: start from defaults.
    if (e is java.io.IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e
  }.map { p ->
    AppSettings(
      pinHash = p[Keys.PIN_HASH],
      pinSalt = p[Keys.PIN_SALT],
      biometricEnabled = p[Keys.BIOMETRIC] ?: false,
      lockTimeoutSeconds = p[Keys.LOCK_TIMEOUT] ?: 30,
      failedPinAttempts = p[Keys.FAILED] ?: 0,
      pinLockedUntil = p[Keys.LOCKED_UNTIL] ?: 0L,
      pinLastFailureAt = p[Keys.LAST_FAILURE] ?: 0L,
      pinLockedUntilElapsed = p[Keys.LOCKED_UNTIL_ELAPSED] ?: 0L,
      pinLockBoot = p[Keys.LOCK_BOOT] ?: "",
      deviceLockEnabled = p[Keys.DEVICE_LOCK] ?: false,
      hasRecoveryCode = p[Keys.RECOVERY_HASH] != null,
      recoveryHash = p[Keys.RECOVERY_HASH],
      recoverySalt = p[Keys.RECOVERY_SALT],
      secureScreen = p[Keys.SECURE_SCREEN] ?: true,
      themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
      notificationCaptureEnabled = p[Keys.NOTI_CAPTURE] ?: false,
      smsCaptureEnabled = p[Keys.SMS_CAPTURE] ?: false,
      autoCaptureDefaultAccountId = p[Keys.DEFAULT_ACCOUNT],
      autoCreateCaptureAccounts = p[Keys.AUTO_CREATE_ACCOUNTS] ?: true,
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
      cardBills = parseBills(p[Keys.CARD_BILLS]),
      cardPoints = parsePoints(p[Keys.CARD_POINTS]),
      cardAlertsEnabled = p[Keys.CARD_ALERTS] ?: false,
      lastSeenVersion = p[Keys.LAST_SEEN_VERSION],
      sentCardAlerts = p[Keys.SENT_CARD_ALERTS] ?: emptySet(),
      autoBackupFolder = p[Keys.AUTO_BACKUP_FOLDER],
      autoBackupPasswordEncrypted = p[Keys.AUTO_BACKUP_PW],
      autoBackupIntervalDays = p[Keys.AUTO_BACKUP_DAYS] ?: 7,
      autoBackupLastAt = p[Keys.AUTO_BACKUP_LAST] ?: 0L,
      autoBackupError = p[Keys.AUTO_BACKUP_ERROR],
      backupReminderEnabled = p[Keys.BACKUP_REMINDER] ?: true,
      budgetAlertsEnabled = p[Keys.BUDGET_ALERTS] ?: false,
      monthlySummaryEnabled = p[Keys.MONTHLY_SUMMARY] ?: false,
      weeklyReportEnabled = p[Keys.WEEKLY_REPORT] ?: false,
      widgetShowAmountsWhenLocked = p[Keys.WIDGET_SHOW_LOCKED] ?: false,
      splitInstallments = p[Keys.SPLIT_INSTALLMENTS] ?: false,
      cardSavedPages = com.moneyflow.domain.cardimport.SavedPages.decode(p[Keys.CARD_SAVED_PAGES]),
      cardDesktopModes = p[Keys.CARD_VIEW_MODES].orEmpty().mapNotNull { e ->
        e.split('=').takeIf { it.size == 2 }?.let { (id, mode) -> id to (mode == "pc") }
      }.toMap(),
      dismissedRecurringSuggestions = p[Keys.RECURRING_DISMISSED] ?: emptySet(),
      familyBudget = p[Keys.FAMILY_BUDGET] ?: 0L,
      annualSalary = p[Keys.ANNUAL_SALARY] ?: 0L,
      budgetCarryOver = p[Keys.BUDGET_CARRY_OVER] ?: false,
      driveKeyEncrypted = p[Keys.DRIVE_KEY],
      driveBotEmail = p[Keys.DRIVE_BOT_EMAIL],
      driveFolderId = p[Keys.DRIVE_FOLDER_ID],
      driveFolderName = p[Keys.DRIVE_FOLDER_NAME],
      driveTestFileName = p[Keys.DRIVE_TEST_FILE],
      billRemindersEnabled = p[Keys.BILL_REMINDERS] ?: true,
      preRestoreAt = p[Keys.PRE_RESTORE_AT] ?: 0L,
      preRestorePasswordEncrypted = p[Keys.PRE_RESTORE_PW],
      simpleMode = p[Keys.SIMPLE_MODE] ?: false,
      ledgerSavedFilters = p[Keys.LEDGER_FILTERS],
      cleanupDismissed = p[Keys.CLEANUP_DISMISSED] ?: emptySet(),
      familySyncName = p[Keys.FAMILY_SYNC_NAME],
      familySyncPasswordEncrypted = p[Keys.FAMILY_SYNC_PW],
      familySyncFileName = p[Keys.FAMILY_SYNC_FILE],
      familySyncLastAt = p[Keys.FAMILY_SYNC_LAST] ?: 0L,
      familySyncLastUploadAt = p[Keys.FAMILY_SYNC_LAST_UP] ?: 0L,
      familySyncError = p[Keys.FAMILY_SYNC_ERROR]
    )
  }.distinctUntilChanged()

  suspend fun current(): AppSettings = settings.first()

  suspend fun setPin(hash: String, salt: String) = store.edit {
    it[Keys.PIN_HASH] = hash
    it[Keys.PIN_SALT] = salt
    it[Keys.DEVICE_LOCK] = false
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
  }

  /** Turns the phone-lock mode on (removing any app PIN) or off. */
  suspend fun setDeviceLock(enabled: Boolean) = store.edit {
    it[Keys.DEVICE_LOCK] = enabled
    if (enabled) {
      listOf(Keys.PIN_HASH, Keys.PIN_SALT, Keys.RECOVERY_HASH, Keys.RECOVERY_SALT).forEach { k -> it.remove(k) }
      it[Keys.BIOMETRIC] = false
      it[Keys.FAILED] = 0
      it[Keys.LOCKED_UNTIL] = 0L
    }
  }

  suspend fun setRecoveryCode(hash: String, salt: String) = store.edit {
    it[Keys.RECOVERY_HASH] = hash
    it[Keys.RECOVERY_SALT] = salt
  }

  suspend fun clearPin() = store.edit {
    it[Keys.DEVICE_LOCK] = false
    it.remove(Keys.PIN_HASH)
    it.remove(Keys.PIN_SALT)
    it.remove(Keys.RECOVERY_HASH)
    it.remove(Keys.RECOVERY_SALT)
    it[Keys.BIOMETRIC] = false
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
  }

  suspend fun recordPinFailure(attempts: Int, lockedUntil: Long, failedAt: Long, lockedUntilElapsed: Long = 0L, bootId: String = "") = store.edit {
    it[Keys.FAILED] = attempts
    it[Keys.LOCKED_UNTIL] = lockedUntil
    it[Keys.LAST_FAILURE] = failedAt
    it[Keys.LOCKED_UNTIL_ELAPSED] = lockedUntilElapsed
    it[Keys.LOCK_BOOT] = bootId
  }

  suspend fun resetPinFailures() = store.edit {
    it[Keys.FAILED] = 0
    it[Keys.LOCKED_UNTIL] = 0L
    it[Keys.LOCKED_UNTIL_ELAPSED] = 0L
  }

  suspend fun setBiometricEnabled(enabled: Boolean) = store.edit { it[Keys.BIOMETRIC] = enabled }
  suspend fun setLockTimeout(seconds: Int) = store.edit { it[Keys.LOCK_TIMEOUT] = seconds }
  suspend fun setSecureScreen(enabled: Boolean) = store.edit { it[Keys.SECURE_SCREEN] = enabled }
  suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
  suspend fun setNotificationCapture(enabled: Boolean) = store.edit { it[Keys.NOTI_CAPTURE] = enabled }
  suspend fun setSmsCapture(enabled: Boolean) = store.edit { it[Keys.SMS_CAPTURE] = enabled }
  suspend fun setAiEnabled(enabled: Boolean) = store.edit { it[Keys.AI_ENABLED] = enabled }
  suspend fun setLastSeenVersion(version: String) = store.edit { it[Keys.LAST_SEEN_VERSION] = version }

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

  /** Forgets card bills and 실적 read from card companies (after a restore: account ids changed). */
  suspend fun clearCardFigures() = store.edit {
    it.remove(Keys.CARD_BILLS)
    it.remove(Keys.CARD_PERFORMANCE)
    it.remove(Keys.CARD_POINTS)
  }

  suspend fun setCardBill(accountId: Long, bill: com.moneyflow.domain.card.CardBill) = store.edit {
    val map = parseBills(it[Keys.CARD_BILLS]).toMutableMap()
    map[accountId] = bill
    val json = org.json.JSONObject()
    map.forEach { (id, b) ->
      json.put(id.toString(), org.json.JSONObject().put("amount", b.amount).put("due", b.dueEpochDay ?: -1).put("src", b.source).put("at", b.fetchedAt))
    }
    it[Keys.CARD_BILLS] = json.toString()
  }

  suspend fun setCardPoint(accountId: Long, point: com.moneyflow.domain.card.CardPoint?) = store.edit {
    val map = parsePoints(it[Keys.CARD_POINTS]).toMutableMap()
    if (point == null) map.remove(accountId) else map[accountId] = point
    val json = org.json.JSONObject()
    map.forEach { (id, v) -> json.put(id.toString(), org.json.JSONObject().put("label", v.label).put("amount", v.amount).put("at", v.fetchedAt)) }
    it[Keys.CARD_POINTS] = json.toString()
  }

  private fun parsePoints(raw: String?): Map<Long, com.moneyflow.domain.card.CardPoint> = runCatching {
    val json = org.json.JSONObject(raw ?: return emptyMap())
    json.keys().asSequence().associate { k ->
      val o = json.getJSONObject(k)
      k.toLong() to com.moneyflow.domain.card.CardPoint(o.optString("label"), o.optLong("amount"), o.optLong("at"))
    }
  }.getOrDefault(emptyMap())

  private fun parseBills(raw: String?): Map<Long, com.moneyflow.domain.card.CardBill> = runCatching {
    val json = org.json.JSONObject(raw ?: return emptyMap())
    json.keys().asSequence().associate { k ->
      val o = json.getJSONObject(k)
      k.toLong() to com.moneyflow.domain.card.CardBill(o.optLong("amount"), o.optLong("due", -1).takeIf { it >= 0 }, o.optString("src"), o.optLong("at"))
    }
  }.getOrDefault(emptyMap())

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
  /** Records sent reminders (card, budget, backup …); keeps only the most recent ones. */
  suspend fun markAlertsSent(keys: Collection<String>) = markCardAlertsSent(keys)

  suspend fun setAutoBackup(folder: String, passwordEncrypted: String, intervalDays: Int) = store.edit {
    it[Keys.AUTO_BACKUP_FOLDER] = folder
    it[Keys.AUTO_BACKUP_PW] = passwordEncrypted
    it[Keys.AUTO_BACKUP_DAYS] = intervalDays
    it.remove(Keys.AUTO_BACKUP_ERROR)
  }

  suspend fun clearAutoBackup() = store.edit {
    it.remove(Keys.AUTO_BACKUP_FOLDER)
    it.remove(Keys.AUTO_BACKUP_PW)
    it.remove(Keys.AUTO_BACKUP_ERROR)
  }

  suspend fun setAutoBackupInterval(days: Int) = store.edit { it[Keys.AUTO_BACKUP_DAYS] = days }

  /** Result of an auto backup run: [error] null = success at [at]. */
  suspend fun setAutoBackupResult(at: Long, error: String?) = store.edit {
    if (error == null) {
      it[Keys.AUTO_BACKUP_LAST] = at
      it.remove(Keys.AUTO_BACKUP_ERROR)
    } else {
      it[Keys.AUTO_BACKUP_ERROR] = error
    }
  }

  /** Remembers whether a card site is shown as its PC page ("PC 화면으로 보기"). */
  suspend fun setBudgetCarryOver(on: Boolean) = store.edit { it[Keys.BUDGET_CARRY_OVER] = on }

  suspend fun setAnnualSalary(amount: Long) = store.edit { it[Keys.ANNUAL_SALARY] = amount.coerceIn(0, 10_000_000_000) }

  suspend fun setCardDesktopMode(siteId: String, desktop: Boolean) = store.edit {
    it[Keys.CARD_VIEW_MODES] = it[Keys.CARD_VIEW_MODES].orEmpty().filterNot { e -> e.startsWith("$siteId=") }.toSet() + "$siteId=${if (desktop) "pc" else "mobile"}"
  }

  /** Replaces the remembered pages of one card site. */
  suspend fun setCardSavedPages(siteId: String, pages: List<com.moneyflow.domain.cardimport.SavedPage>) = store.edit {
    val map = com.moneyflow.domain.cardimport.SavedPages.decode(it[Keys.CARD_SAVED_PAGES]).toMutableMap()
    map[siteId] = pages
    it[Keys.CARD_SAVED_PAGES] = com.moneyflow.domain.cardimport.SavedPages.encode(map)
  }

  suspend fun setDriveKey(encrypted: String?, botEmail: String?) = store.edit {
    if (encrypted == null) {
      it.remove(Keys.DRIVE_KEY); it.remove(Keys.DRIVE_BOT_EMAIL); it.remove(Keys.DRIVE_FOLDER_ID); it.remove(Keys.DRIVE_FOLDER_NAME)
    } else {
      it[Keys.DRIVE_KEY] = encrypted
      if (botEmail != null) it[Keys.DRIVE_BOT_EMAIL] = botEmail
    }
  }

  suspend fun setDriveFolder(id: String, name: String) = store.edit {
    it[Keys.DRIVE_FOLDER_ID] = id
    it[Keys.DRIVE_FOLDER_NAME] = name
  }

  suspend fun setDriveTestFile(name: String) = store.edit { it[Keys.DRIVE_TEST_FILE] = name }

  suspend fun setFamilySync(name: String, passwordEncrypted: String) = store.edit {
    it[Keys.FAMILY_SYNC_NAME] = name
    it[Keys.FAMILY_SYNC_PW] = passwordEncrypted
    it.remove(Keys.FAMILY_SYNC_ERROR)
    // A new password may open files that failed before.
    it.remove(Keys.FAMILY_SYNC_FAILED)
  }

  /** My family file was just written with the data of version [generation]. */
  suspend fun setFamilySyncFile(name: String, uploadedAt: Long, generation: Long) = store.edit {
    it[Keys.FAMILY_SYNC_FILE] = name
    it[Keys.FAMILY_SYNC_LAST_UP] = uploadedAt
    it[Keys.FAMILY_SYNC_UP_GEN] = generation
    it.remove(Keys.FAMILY_SYNC_ERROR)
  }

  suspend fun clearFamilySync() = store.edit {
    listOf(Keys.FAMILY_SYNC_PW, Keys.FAMILY_SYNC_FILE, Keys.FAMILY_SYNC_ERROR, Keys.FAMILY_SYNC_SEEN, Keys.FAMILY_SYNC_FAILED).forEach { k -> it.remove(k) }
    it.remove(Keys.FAMILY_SYNC_LAST); it.remove(Keys.FAMILY_SYNC_LAST_UP)
  }

  /** The records changed: the family file needs uploading. (Not part of [AppSettings], so screens don't redraw.) */
  suspend fun markFamilyDataChanged() = store.edit { it[Keys.FAMILY_SYNC_GEN] = (it[Keys.FAMILY_SYNC_GEN] ?: 1L) + 1 }

  /** (current data version, version last uploaded). Different = there are changes to upload. */
  suspend fun familySyncGenerations(): Pair<Long, Long> =
    store.data.first().let { (it[Keys.FAMILY_SYNC_GEN] ?: 1L) to (it[Keys.FAMILY_SYNC_UP_GEN] ?: 0L) }

  /**
   * Result of a sync run. [uploadedGeneration] is the data version that was uploaded (null = no
   * upload); a change made while uploading keeps a newer version, so it is uploaded next time.
   */
  /** modifiedTime of my family file after my last upload (null: not known yet, e.g. after taking a file over). */
  suspend fun familySyncMyModified(): String? = store.data.first()[Keys.FAMILY_SYNC_MY_MODIFIED]
  suspend fun setFamilySyncMyModified(value: String?) = store.edit {
    if (value.isNullOrEmpty()) it.remove(Keys.FAMILY_SYNC_MY_MODIFIED) else it[Keys.FAMILY_SYNC_MY_MODIFIED] = value
  }

  suspend fun setFamilySyncResult(at: Long, uploadedAt: Long?, uploadedGeneration: Long?, error: String?) = store.edit {
    it[Keys.FAMILY_SYNC_LAST] = at
    if (uploadedAt != null && uploadedGeneration != null) {
      it[Keys.FAMILY_SYNC_LAST_UP] = uploadedAt
      it[Keys.FAMILY_SYNC_UP_GEN] = maxOf(it[Keys.FAMILY_SYNC_UP_GEN] ?: 0L, uploadedGeneration)
    }
    if (error == null) it.remove(Keys.FAMILY_SYNC_ERROR) else it[Keys.FAMILY_SYNC_ERROR] = error
  }

  /** Drive file id → modified time of family files that couldn't be opened (wrong password …). */
  suspend fun familySyncFailed(): Map<String, String> = jsonMap(store.data.first()[Keys.FAMILY_SYNC_FAILED])

  suspend fun setFamilySyncFailed(failed: Map<String, String>) = store.edit {
    it[Keys.FAMILY_SYNC_FAILED] = org.json.JSONObject(failed as Map<*, *>).toString()
  }

  private fun jsonMap(json: String?): Map<String, String> = runCatching {
    val o = org.json.JSONObject(json ?: "{}")
    o.keys().asSequence().associateWith { o.getString(it) }
  }.getOrDefault(emptyMap())

  /** Drive file id → modified time of family files already loaded. */
  suspend fun familySyncSeen(): Map<String, String> = runCatching {
    val o = org.json.JSONObject(store.data.first()[Keys.FAMILY_SYNC_SEEN] ?: "{}")
    o.keys().asSequence().associateWith { o.getString(it) }
  }.getOrDefault(emptyMap())

  suspend fun setFamilySyncSeen(seen: Map<String, String>) = store.edit {
    it[Keys.FAMILY_SYNC_SEEN] = org.json.JSONObject(seen as Map<*, *>).toString()
  }

  suspend fun setFamilyBudget(amount: Long) = store.edit { it[Keys.FAMILY_BUDGET] = amount }

  suspend fun dismissRecurringSuggestion(key: String) = store.edit {
    it[Keys.RECURRING_DISMISSED] = ((it[Keys.RECURRING_DISMISSED] ?: emptySet()) + key).toList().takeLast(200).toSet()
  }

  suspend fun setSplitInstallments(enabled: Boolean) = store.edit { it[Keys.SPLIT_INSTALLMENTS] = enabled }
  suspend fun setBackupReminder(enabled: Boolean) = store.edit { it[Keys.BACKUP_REMINDER] = enabled }
  suspend fun dismissDuplicate(key: String) = store.edit {
    it[Keys.CLEANUP_DISMISSED] = ((it[Keys.CLEANUP_DISMISSED] ?: emptySet()) + key).toList().takeLast(300).toSet()
  }
  suspend fun setLedgerSavedFilters(json: String) = store.edit { it[Keys.LEDGER_FILTERS] = json }
  suspend fun setSimpleMode(enabled: Boolean) = store.edit { it[Keys.SIMPLE_MODE] = enabled }
  suspend fun setPreRestore(at: Long, passwordEncrypted: String?) = store.edit {
    if (passwordEncrypted == null) { it.remove(Keys.PRE_RESTORE_AT); it.remove(Keys.PRE_RESTORE_PW) }
    else { it[Keys.PRE_RESTORE_AT] = at; it[Keys.PRE_RESTORE_PW] = passwordEncrypted }
  }
  suspend fun setBillReminders(enabled: Boolean) = store.edit { it[Keys.BILL_REMINDERS] = enabled }
  suspend fun setBudgetAlerts(enabled: Boolean) = store.edit { it[Keys.BUDGET_ALERTS] = enabled }
  suspend fun setMonthlySummary(enabled: Boolean) = store.edit { it[Keys.MONTHLY_SUMMARY] = enabled }
  suspend fun setWeeklyReport(enabled: Boolean) = store.edit { it[Keys.WEEKLY_REPORT] = enabled }
  suspend fun setWidgetShowAmountsWhenLocked(enabled: Boolean) = store.edit { it[Keys.WIDGET_SHOW_LOCKED] = enabled }

  /** Random id of this installation (tells family backups apart from this phone's own). */
  suspend fun installId(): String {
    store.data.first()[Keys.INSTALL_ID]?.let { return it }
    var id = ""
    store.edit { p -> id = p[Keys.INSTALL_ID] ?: java.util.UUID.randomUUID().toString().also { p[Keys.INSTALL_ID] = it } }
    return id
  }

  /** After restoring this person's backup on a new phone, keep their id so family copies update in place. */
  suspend fun setInstallId(id: String) = store.edit { it[Keys.INSTALL_ID] = id }

  suspend fun setLastBackupAt(millis: Long) = store.edit { it[Keys.LAST_BACKUP] = millis }
  suspend fun setOnboardingDone() = store.edit { it[Keys.ONBOARDING] = true }
  suspend fun setAutoCreateCaptureAccounts(on: Boolean) = store.edit { it[Keys.AUTO_CREATE_ACCOUNTS] = on }
  suspend fun setDefaultCaptureAccount(id: Long?) = store.edit {
    if (id == null) it.remove(Keys.DEFAULT_ACCOUNT) else it[Keys.DEFAULT_ACCOUNT] = id
  }
}
