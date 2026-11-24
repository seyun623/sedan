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
  val budget: Long = 0
) {
  val totalIncome: Long get() = people.sumOf { it.income }
  val totalExpense: Long get() = people.sumOf { it.expense }
}

/** Result of loading a family member's backup. */
data class FamilyImportResult(val member: FamilyMemberEntity, val transactionCount: Int, val updated: Boolean)

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
  suspend fun import(file: ByteArray, password: CharArray, name: String): FamilyImportResult {
    val content = withContext(Dispatchers.Default) { backups.readBackup(file, password) }
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
      val memberId = if (existing != null) {
        dao.updateMember(existing.id, name.trim().ifEmpty { existing.name }, content.summary.createdAt, now)
        dao.clearTransactions(existing.id)
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
      FamilyImportResult(
        FamilyMemberEntity(memberId, name.trim().ifEmpty { existing?.name ?: "가족" }, deviceId, content.summary.createdAt, now),
        items.size,
        updated = existing != null
      )
    }
  }

  suspend fun delete(member: FamilyMemberEntity) = db.withTransaction {
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
        mine.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
        null
      )
      val others = members.map { m ->
        val list = family.filter { it.memberId == m.id }
        FamilyPersonTotal(
          m.id, m.name,
          list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
          list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
          m.backupCreatedAt
        )
      }
      val names = members.associate { it.id to it.name }
      // (category, person, amount) for every expense of everyone.
      val rows = mine.filter { it.type == TransactionType.EXPENSE }.map { Triple(it.category, "나", it.amount) } +
        family.filter { it.type == TransactionType.EXPENSE }.map { Triple(it.category, names[it.memberId] ?: "가족", it.amount) }
      val categories = rows.groupBy { it.first }.map { (c, list) ->
        FamilyCategory(
          c, list.sumOf { it.third },
          list.groupBy { it.second }.map { (p, l) -> p to l.sumOf { it.third } }.sortedByDescending { it.second }
        )
      }.sortedByDescending { it.total }
      FamilyMonth(month, listOf(me) + others, categories, s.familyBudget)
    }
  }
}
