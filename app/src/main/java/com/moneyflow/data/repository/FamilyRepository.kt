package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.FamilyMemberEntity
import com.moneyflow.data.model.FamilyTransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import com.moneyflow.data.model.spending

class FamilyImportException(message: String) : Exception(message)

/** One person's totals in the combined view. [memberId] null = this phone ("나"). */
data class FamilyPersonTotal(
  val memberId: Long?,
  val name: String,
  val income: Long,
  val expense: Long,
  /** When that person's backup was made (null for this phone: always current). */
  val asOf: Long?
)

/** One category's combined spending and each person's part (largest first). */
data class FamilyCategory(val category: String, val total: Long, val byPerson: List<Pair<String, Long>>)

data class FamilyMonth(
  val month: YearMonth,
  val people: List<FamilyPersonTotal>,
  /** Combined expense by category, largest first. */
  val categories: List<FamilyCategory>,
  /** Family monthly budget (0 = not set). */
  val budget: Long = 0,
  /** Everyone's spending that counts toward the budget ("예산 제외" left out). */
  val budgetExpense: Long = 0,
  /** 가족 정산: #공동 spending each person paid (this phone first). */
  val sharedPaid: List<Pair<String, Long>> = emptyList()
) {
  val totalIncome: Long get() = people.sumOf { it.income }
  val totalExpense: Long get() = people.sumOf { it.expense }
}

/** A goal several people save toward: same title on each phone ("가족 여행", "이사 자금"). */
data class SharedGoal(
  val title: String,
  /** The largest target any of them set. */
  val target: Long,
  val byPerson: List<Pair<String, Long>>,
  val targetDate: Long
) {
  val saved: Long get() = byPerson.sumOf { it.second }
  val progress: Float get() = if (target > 0) (saved.toFloat() / target).coerceIn(0f, 1f) else 0f
}

/** Result of loading a family member's backup. */
data class FamilyImportResult(
  val member: FamilyMemberEntity,
  val transactionCount: Int,
  val updated: Boolean,
  /** The file was older than the copy already loaded, so nothing changed. */
  val skippedAsOlder: Boolean = false
)

/**
 * Family combined view without a server: each person's backup file is loaded as a read-only
 * copy of their income and expenses. Loading the same person's newer backup replaces the copy.
 */
@Singleton
class FamilyRepository @Inject constructor(
  private val db: AppDatabase,
  private val backups: BackupRepository,
  private val settings: SettingsRepository,
  private val clock: AppClock
) {
  private val dao = db.familyDao()

  suspend fun setBudget(amount: Long) = settings.setFamilyBudget(amount.coerceAtLeast(0))

  val members: Flow<List<FamilyMemberEntity>> = dao.observeMembers()

  /** Opens a backup file (password-encrypted) and stores that person's income / expenses. */
  suspend fun import(
    file: ByteArray,
    password: CharArray,
    name: String,
    keepExistingName: Boolean = false,
    /** false: a file older than the loaded copy is ignored (automatic sync). */
    allowOlder: Boolean = true
  ): FamilyImportResult {
    val content = withContext(Dispatchers.Default) { backups.readBackup(file, password, newerDbOk = true) }
    val ownId = settings.installId()
    if (content.deviceId.isNotEmpty() && content.deviceId == ownId) {
      throw FamilyImportException("이 휴대폰에서 만든 백업이에요. 가족이 자기 휴대폰에서 만든 백업 파일을 골라 주세요.")
    }
    // Backups made before 2.0 have no install id; use the creation time so the same file isn't added twice.
    val deviceId = content.deviceId.ifEmpty { "legacy-${content.summary.createdAt}" }
    val now = clock.nowMillis()
    val items = content.transactions.filter { it.type != TransactionType.TRANSFER }
    return db.withTransaction {
      val existing = dao.byDevice(deviceId)
      if (!allowOlder && existing != null && content.summary.createdAt < existing.backupCreatedAt) {
        return@withTransaction FamilyImportResult(existing, 0, updated = false, skippedAsOlder = true)
      }
      val newName = if (keepExistingName && existing != null) existing.name else name.trim().ifEmpty { existing?.name ?: "가족" }
      val memberId = if (existing != null) {
        dao.updateMember(existing.id, newName, content.summary.createdAt, now)
        dao.clearTransactions(existing.id)
        dao.clearGoals(existing.id)
        existing.id
      } else {
        dao.insertMember(FamilyMemberEntity(name = name.trim().ifEmpty { "가족" }, deviceId = deviceId, backupCreatedAt = content.summary.createdAt, importedAt = now))
      }
      dao.insertTransactions(items.map {
        FamilyTransactionEntity(
          memberId = memberId, type = it.type, amount = it.amount, category = it.category,
          timestamp = it.timestamp, memo = it.memo, isExcludedFromBudget = it.isExcludedFromBudget
        )
      })
      dao.insertGoals(content.goals.map {
        com.moneyflow.data.model.FamilyGoalEntity(
          memberId = memberId, title = it.title, targetAmount = it.targetAmount, currentAmount = it.currentAmount, targetDate = it.targetDate
        )
      })
      FamilyImportResult(
        FamilyMemberEntity(memberId, newName, deviceId, content.summary.createdAt, now),
        items.size,
        updated = existing != null
      )
    }
  }

  /**
   * Goals with the same name (spaces / case ignored) on this phone and at least one family
   * member's phone, combined. Each person's saved amount comes from their own goal.
   */
  val sharedGoals: Flow<List<SharedGoal>> = combine(db.savingGoalDao().observeAll(), dao.observeGoals(), dao.observeMembers()) { mine, family, members ->
    val names = members.associate { it.id to it.name }
    data class Part(val key: String, val title: String, val person: String, val target: Long, val current: Long, val date: Long)
    val parts = mine.map { Part(norm(it.title), it.title, "나", it.targetAmount, it.currentAmount, it.targetDate) } +
      family.map { Part(norm(it.title), it.title, names[it.memberId] ?: "가족", it.targetAmount, it.currentAmount, it.targetDate) }
    parts.groupBy { it.key }
      .filterValues { list -> list.map { it.person }.distinct().size >= 2 }
      .map { (_, list) ->
        SharedGoal(
          title = list.firstOrNull { it.person == "나" }?.title ?: list.first().title,
          target = list.maxOf { it.target },
          byPerson = list.groupBy { it.person }.map { (p, l) -> p to l.sumOf { it.current } }.sortedByDescending { it.second },
          targetDate = list.maxOf { it.date }
        )
      }
      .sortedBy { it.title }
  }

  private fun norm(s: String) = s.replace(" ", "").lowercase()

  suspend fun delete(member: FamilyMemberEntity) = db.withTransaction {
    dao.clearGoals(member.id)
    dao.clearTransactions(member.id)
    dao.deleteMember(member.id)
  }

  /** Combined income / expense of this phone ("나") and every loaded family member for [month]. */
  fun month(month: YearMonth): Flow<FamilyMonth> {
    val start = MonthKeys.startMillis(month, clock.zone)
    val end = MonthKeys.endMillis(month, clock.zone)
    return combine(
      db.transactionDao().observeBetween(start, end),
      dao.observeBetween(start, end),
      dao.observeMembers(),
      settings.settings
    ) { mine, family, members, s ->
      val me = FamilyPersonTotal(
        null, "나",
        mine.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        mine.sumOf { it.spending }.coerceAtLeast(0),
        null
      )
      val others = members.map { m ->
        val list = family.filter { it.memberId == m.id }
        FamilyPersonTotal(
          m.id, m.name,
          list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
          list.sumOf { it.spending }.coerceAtLeast(0),
          m.backupCreatedAt
        )
      }
      val names = members.associate { it.id to it.name }
      // (category, person, amount) for every expense of everyone.
      val rows = mine.filter { it.type.isSpending }.map { Triple(it.category, "나", it.spending) } +
        family.filter { it.type.isSpending }.map { Triple(it.category, names[it.memberId] ?: "가족", it.spending) }
      val categories = rows.groupBy { it.first }.map { (c, list) ->
        FamilyCategory(
          c, list.sumOf { it.third },
          list.groupBy { it.second }.map { (p, l) -> p to l.sumOf { it.third } }.filter { it.second > 0 }.sortedByDescending { it.second }
        )
      }.filter { it.total > 0 }.sortedByDescending { it.total }
      val budgetExpense = (mine.filter { !it.isExcludedFromBudget }.sumOf { it.spending } +
        family.filter { !it.isExcludedFromBudget }.sumOf { it.spending }).coerceAtLeast(0)
      val shared = com.moneyflow.domain.family.FamilySettlement
      val sharedPaid = listOf("나" to mine.filter { shared.isShared(it.memo) }.sumOf { it.spending }) +
        members.map { m -> m.name to family.filter { it.memberId == m.id && shared.isShared(it.memo) }.sumOf { it.spending } }
      FamilyMonth(month, listOf(me) + others, categories, s.familyBudget, budgetExpense, sharedPaid)
    }
  }
}
