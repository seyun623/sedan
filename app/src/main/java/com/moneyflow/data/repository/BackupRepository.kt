package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.BudgetEntity
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.backup.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

class BackupFormatException(message: String) : Exception(message)

/** What a backup file contains (shown before restoring). */
data class BackupSummary(
  val createdAt: Long,
  val appVersion: String,
  val accountCount: Int,
  val transactionCount: Int,
  val goalCount: Int,
  val recurringCount: Int
)

/** A decrypted, parsed and validated backup ready to restore. */
class BackupContent internal constructor(
  val summary: BackupSummary,
  /** Install id of the phone that made the backup ("" for backups before 2.0). */
  val deviceId: String = "",
  internal val accounts: List<AccountEntity>,
  internal val transactions: List<TransactionEntity>,
  internal val budgets: List<BudgetEntity>,
  internal val categoryBudgets: List<CategoryBudgetEntity>,
  internal val goals: List<SavingGoalEntity>,
  internal val recurring: List<RecurringTransactionEntity>,
  internal val cardProfiles: List<CardProfileEntity> = emptyList(),
  internal val cardBenefits: List<CardBenefitEntity> = emptyList(),
  internal val categoryRules: List<com.moneyflow.data.model.CategoryRuleEntity> = emptyList()
)

/**
 * Full backup / restore of the ledger as a single password-encrypted file.
 *
 * Everything (accounts with their balances, transactions, budgets, goals, recurring items) is
 * copied as-is with original ids, so relationships and balances are restored exactly; no balance
 * effects are re-applied. App settings (PIN, theme, auto-capture switches) are device-specific
 * and are not part of the backup.
 */
@Singleton
class BackupRepository @Inject constructor(
  private val db: AppDatabase,
  private val settings: SettingsRepository,
  private val clock: AppClock
) {
  private val dao = db.backupDao()

  /** Creates the encrypted backup file contents. */
  suspend fun createBackup(password: CharArray, appVersion: String, recordAsBackup: Boolean = true): ByteArray {
    val deviceId = settings.installId()
    val json = db.withTransaction {
      JSONObject()
        .put("deviceId", deviceId)
        .put("format", FORMAT)
        .put("formatVersion", FORMAT_VERSION)
        .put("dbVersion", AppDatabase.VERSION)
        .put("appVersion", appVersion)
        .put("createdAt", clock.nowMillis())
        .put("accounts", JSONArray(dao.accounts().map(::accountJson)))
        .put("transactions", JSONArray(dao.transactions().map(::transactionJson)))
        .put("budgets", JSONArray(dao.budgets().map { JSONObject().put("id", it.id).put("monthKey", it.monthKey).put("totalBudget", it.totalBudget) }))
        .put("categoryBudgets", JSONArray(dao.categoryBudgets().map {
          JSONObject().put("monthKey", it.monthKey).put("category", it.category).put("limitAmount", it.limitAmount)
        }))
        .put("goals", JSONArray(dao.goals().map(::goalJson)))
        .put("recurring", JSONArray(dao.recurring().map(::recurringJson)))
        .put("cardProfiles", JSONArray(db.cardDao().getProfiles().map {
          JSONObject().put("accountId", it.accountId).put("performanceTarget", it.performanceTarget)
            .put("cycleStartDay", it.cycleStartDay).put("excludedCategories", it.excludedCategories).put("excludedKeywords", it.excludedKeywords)
        }))
        .put("cardBenefits", JSONArray(db.cardDao().getBenefits().map {
          JSONObject().put("id", it.id).put("accountId", it.accountId).put("title", it.title).put("category", it.category ?: JSONObject.NULL)
            .put("merchantKeywords", it.merchantKeywords).put("kind", it.kind.name).put("value", it.value).put("monthlyCap", it.monthlyCap)
            .put("minPerformance", it.minPerformance).put("minAmount", it.minAmount).put("isActive", it.isActive)
        }))
        .put("categoryRules", JSONArray(db.ruleDao().getAll().map {
          JSONObject().put("id", it.id).put("keyword", it.keyword).put("action", it.action.name).put("category", it.category)
        }))
    }
    val bytes = withContext(Dispatchers.Default) {
      BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), password)
    }
    if (recordAsBackup) settings.setLastBackupAt(clock.nowMillis())
    return bytes
  }

  /** Decrypts and validates a backup file. Throws WrongPasswordException / NotABackupFileException / BackupFormatException. */
  fun readBackup(file: ByteArray, password: CharArray): BackupContent {
    val text = BackupCrypto.decrypt(file, password).toString(Charsets.UTF_8)
    val root = try { JSONObject(text) } catch (e: Exception) { throw BackupFormatException("백업 내용을 읽을 수 없어요.") }
    if (root.optString("format") != FORMAT) throw BackupFormatException("머니플로우 백업 파일이 아니에요.")
    if (root.optInt("formatVersion") > FORMAT_VERSION || root.optInt("dbVersion") > AppDatabase.VERSION) {
      throw BackupFormatException("더 새로운 버전의 앱에서 만든 백업이에요. 앱을 업데이트한 뒤 다시 시도해 주세요.")
    }
    try {
      val accounts = root.getJSONArray("accounts").objects().map(::parseAccount)
      val transactions = root.getJSONArray("transactions").objects().map(::parseTransaction)
      val budgets = root.getJSONArray("budgets").objects().map {
        BudgetEntity(id = it.getLong("id"), monthKey = it.getString("monthKey"), totalBudget = it.getLong("totalBudget"))
      }
      val categoryBudgets = root.getJSONArray("categoryBudgets").objects().map {
        CategoryBudgetEntity(it.getString("monthKey"), it.getString("category"), it.getLong("limitAmount"))
      }
      val goals = root.getJSONArray("goals").objects().map(::parseGoal)
      val recurring = root.getJSONArray("recurring").objects().map(::parseRecurring)
      // Added in database v4; older backups simply have none.
      val cardProfiles = root.optJSONArray("cardProfiles")?.objects().orEmpty().map {
        CardProfileEntity(it.getLong("accountId"), it.getLong("performanceTarget"), it.optInt("cycleStartDay", 1),
          it.optString("excludedCategories"), it.optString("excludedKeywords"))
      }
      val cardBenefits = root.optJSONArray("cardBenefits")?.objects().orEmpty().map {
        CardBenefitEntity(
          id = it.getLong("id"), accountId = it.getLong("accountId"), title = it.getString("title"),
          category = it.optStringOrNull("category"), merchantKeywords = it.optString("merchantKeywords"),
          kind = enumOf(it.optString("kind", "PERCENT"), BenefitKind.PERCENT), value = it.getLong("value"),
          monthlyCap = it.optLong("monthlyCap"), minPerformance = it.optLong("minPerformance"),
          minAmount = it.optLong("minAmount"), isActive = it.optBoolean("isActive", true)
        )
      }

      // Added in database v5.
      val categoryRules = root.optJSONArray("categoryRules")?.objects().orEmpty().map {
        com.moneyflow.data.model.CategoryRuleEntity(
          id = it.getLong("id"), keyword = it.getString("keyword"),
          action = enumOf(it.optString("action", "CATEGORY"), com.moneyflow.data.model.RuleAction.CATEGORY),
          category = it.optString("category")
        )
      }

      val accountIds = accounts.map { it.id }.toSet()
      if (transactions.any { it.accountId !in accountIds || (it.toAccountId != null && it.toAccountId !in accountIds) }) {
        throw BackupFormatException("백업 파일의 거래와 계좌 정보가 맞지 않아요.")
      }
      return BackupContent(
        summary = BackupSummary(
          createdAt = root.getLong("createdAt"),
          appVersion = root.optString("appVersion"),
          accountCount = accounts.size,
          transactionCount = transactions.size,
          goalCount = goals.size,
          recurringCount = recurring.size
        ),
        deviceId = root.optString("deviceId"),
        accounts = accounts,
        transactions = transactions,
        budgets = budgets,
        categoryBudgets = categoryBudgets,
        goals = goals,
        recurring = recurring,
        cardProfiles = cardProfiles,
        cardBenefits = cardBenefits,
        categoryRules = categoryRules
      )
    } catch (e: BackupFormatException) {
      throw e
    } catch (e: Exception) {
      throw BackupFormatException("백업 파일 형식이 올바르지 않아요.")
    }
  }

  /** Replaces ALL current data with the backup, in one transaction (all or nothing). */
  suspend fun restore(content: BackupContent) {
    db.withTransaction {
      db.cardDao().clearBenefits()
      db.cardDao().clearProfiles()
      db.ruleDao().clear()
      dao.clearTransactions()
      dao.clearRecurring()
      dao.clearGoals()
      dao.clearCategoryBudgets()
      dao.clearBudgets()
      dao.clearAccounts()
      dao.insertAccounts(content.accounts)
      dao.insertTransactions(content.transactions)
      dao.insertBudgets(content.budgets)
      dao.insertCategoryBudgets(content.categoryBudgets)
      dao.insertGoals(content.goals)
      dao.insertRecurring(content.recurring)
      db.cardDao().insertProfiles(content.cardProfiles)
      db.cardDao().insertBenefits(content.cardBenefits)
      db.ruleDao().insertAll(content.categoryRules)
    }
    // The same person on a new phone: family members' copies of this person keep updating in place.
    if (content.deviceId.isNotEmpty()) settings.setInstallId(content.deviceId)
    // The default auto-capture account may not exist in the restored data.
    val defaultId = settings.current().autoCaptureDefaultAccountId
    if (defaultId != null && content.accounts.none { it.id == defaultId }) settings.setDefaultCaptureAccount(null)
  }

  // ---- JSON mapping ------------------------------------------------------------------------

  private fun accountJson(a: AccountEntity) = JSONObject()
    .put("id", a.id).put("name", a.name).put("bankName", a.bankName).put("accountType", a.accountType.name)
    .put("accountNumber", a.accountNumber).put("balance", a.balance).put("colorHex", a.colorHex)
    .put("isFavorite", a.isFavorite).put("isDebt", a.isDebt).put("paymentDay", a.paymentDay)

  private fun parseAccount(o: JSONObject) = AccountEntity(
    id = o.getLong("id"), name = o.getString("name"), bankName = o.getString("bankName"),
    accountType = enumOf(o.getString("accountType"), AccountType.BANK),
    accountNumber = o.optString("accountNumber"), balance = o.getLong("balance"),
    colorHex = o.optLong("colorHex", 0xFF0064FF), isFavorite = o.optBoolean("isFavorite"), isDebt = o.optBoolean("isDebt"),
    paymentDay = o.optInt("paymentDay", 0).coerceIn(0, 31)
  )

  private fun transactionJson(t: TransactionEntity) = JSONObject()
    .put("id", t.id).put("type", t.type.name).put("amount", t.amount).put("category", t.category)
    .put("accountId", t.accountId).put("accountName", t.accountName)
    .put("toAccountId", t.toAccountId ?: JSONObject.NULL).put("toAccountName", t.toAccountName ?: JSONObject.NULL)
    .put("timestamp", t.timestamp).put("memo", t.memo).put("paymentMethod", t.paymentMethod)
    .put("isExcludedFromBudget", t.isExcludedFromBudget).put("source", t.source.name)
    .put("dedupKey", t.dedupKey ?: JSONObject.NULL).put("recurringId", t.recurringId ?: JSONObject.NULL)
    .put("goalId", t.goalId ?: JSONObject.NULL)

  private fun parseTransaction(o: JSONObject) = TransactionEntity(
    id = o.getLong("id"), type = enumOf(o.getString("type"), TransactionType.EXPENSE),
    amount = o.getLong("amount"), category = o.getString("category"),
    accountId = o.getLong("accountId"), accountName = o.optString("accountName"),
    toAccountId = o.optLongOrNull("toAccountId"), toAccountName = o.optStringOrNull("toAccountName"),
    timestamp = o.getLong("timestamp"), memo = o.optString("memo"), paymentMethod = o.optString("paymentMethod", "체크카드"),
    isExcludedFromBudget = o.optBoolean("isExcludedFromBudget"),
    source = enumOf(o.optString("source", "MANUAL"), TransactionSource.MANUAL),
    dedupKey = o.optStringOrNull("dedupKey"), recurringId = o.optLongOrNull("recurringId"), goalId = o.optLongOrNull("goalId")
  )

  private fun goalJson(g: SavingGoalEntity) = JSONObject()
    .put("id", g.id).put("title", g.title).put("targetAmount", g.targetAmount).put("currentAmount", g.currentAmount)
    .put("targetDate", g.targetDate).put("iconEmoji", g.iconEmoji).put("colorHex", g.colorHex)

  private fun parseGoal(o: JSONObject) = SavingGoalEntity(
    id = o.getLong("id"), title = o.getString("title"), targetAmount = o.getLong("targetAmount"),
    currentAmount = o.getLong("currentAmount"), targetDate = o.getLong("targetDate"),
    iconEmoji = o.optString("iconEmoji", "💰"), colorHex = o.optLong("colorHex", 0xFF0064FF)
  )

  private fun recurringJson(r: RecurringTransactionEntity) = JSONObject()
    .put("id", r.id).put("title", r.title).put("type", r.type.name).put("amount", r.amount).put("category", r.category)
    .put("dayOfMonth", r.dayOfMonth).put("accountId", r.accountId).put("accountName", r.accountName).put("memo", r.memo)
    .put("paymentMethod", r.paymentMethod).put("isActive", r.isActive)
    .put("lastExecutedMonth", r.lastExecutedMonth ?: JSONObject.NULL).put("startMonth", r.startMonth)

  private fun parseRecurring(o: JSONObject) = RecurringTransactionEntity(
    id = o.getLong("id"), title = o.getString("title"), type = enumOf(o.getString("type"), TransactionType.EXPENSE),
    amount = o.getLong("amount"), category = o.getString("category"), dayOfMonth = o.getInt("dayOfMonth"),
    accountId = o.getLong("accountId"), accountName = o.optString("accountName"), memo = o.optString("memo"),
    paymentMethod = o.optString("paymentMethod", "계좌이체"), isActive = o.optBoolean("isActive", true),
    lastExecutedMonth = o.optStringOrNull("lastExecutedMonth"), startMonth = o.optString("startMonth")
  )

  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
  private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
  private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
  private inline fun <reified E : Enum<E>> enumOf(name: String, fallback: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)

  companion object {
    const val FORMAT = "moneyflow-backup"
    const val FORMAT_VERSION = 1
    const val FILE_EXTENSION = "mfbackup"
  }
}
