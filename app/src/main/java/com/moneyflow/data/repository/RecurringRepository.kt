package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recurring (fixed) income/expense items and the engine that turns them into transactions.
 *
 * The engine always works from the real current date (never the month being viewed), catches
 * up months that were missed while the app was closed (up to [MAX_CATCH_UP_MONTHS]), clamps
 * day 29–31 to the month's last day, and marks each month with a compare-and-set inside the
 * same DB transaction as the insert, so concurrent runs cannot create duplicates.
 */
@Singleton
class RecurringRepository @Inject constructor(
  private val db: AppDatabase,
  private val ledger: LedgerRepository,
  private val clock: AppClock
) {
  private val dao = db.recurringTransactionDao()
  private val mutex = Mutex()

  val items: Flow<List<RecurringTransactionEntity>> = dao.observeAll()

  suspend fun add(item: RecurringTransactionEntity): Long {
    val start = item.startMonth.ifBlank { MonthKeys.of(clock.currentMonth()) }
    val id = dao.insert(item.copy(id = 0, startMonth = start, lastExecutedMonth = null))
    applyDue()
    return id
  }

  suspend fun update(item: RecurringTransactionEntity) {
    val account = db.accountDao().getById(item.accountId)
    dao.update(item.copy(accountName = account?.name ?: item.accountName))
    applyDue()
  }

  suspend fun delete(item: RecurringTransactionEntity) = dao.delete(item)

  suspend fun setActive(id: Long, active: Boolean) {
    dao.setActive(id, active)
    if (active) applyDue()
  }

  /**
   * Records this month's occurrence right now (e.g. paid early). Returns false if this month
   * was already recorded.
   */
  suspend fun executeNow(item: RecurringTransactionEntity): Boolean = mutex.withLock {
    val monthKey = MonthKeys.of(clock.currentMonth())
    db.withTransaction {
      if (dao.markExecutedIfNewer(item.id, monthKey) == 0) return@withTransaction false
      ledger.insertWithEffects(toTransaction(item, clock.nowMillis()))
      true
    }
  }

  /** Creates all due transactions. Returns how many were recorded. */
  suspend fun applyDue(): Int = mutex.withLock {
    val today = clock.today()
    val current = YearMonth.from(today)
    var created = 0
    for (item in dao.getActive()) {
      if (db.accountDao().getById(item.accountId) == null) continue
      val first = item.lastExecutedMonth?.let { MonthKeys.parse(it)?.plusMonths(1) }
        ?: MonthKeys.parse(item.startMonth)
        ?: current
      var month = maxOf(first, current.minusMonths(MAX_CATCH_UP_MONTHS - 1))
      while (!month.isAfter(current)) {
        val date = month.atDay(item.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
        if (date.isAfter(today)) break
        val key = MonthKeys.of(month)
        val millis = date.atTime(LocalTime.of(9, 0)).atZone(clock.zone).toInstant().toEpochMilli()
        val inserted = db.withTransaction {
          if (dao.markExecutedIfNewer(item.id, key) == 0) return@withTransaction false
          ledger.insertWithEffects(toTransaction(item, millis))
          true
        }
        if (inserted) created++
        month = month.plusMonths(1)
      }
    }
    created
  }

  private fun toTransaction(item: RecurringTransactionEntity, timestamp: Long) = TransactionEntity(
    type = item.type,
    amount = item.amount,
    category = item.category,
    accountId = item.accountId,
    accountName = item.accountName,
    timestamp = timestamp,
    memo = if (item.memo.isBlank()) item.title else "${item.title} - ${item.memo}",
    paymentMethod = item.paymentMethod,
    source = TransactionSource.RECURRING,
    recurringId = item.id
  )

  companion object {
    const val MAX_CATCH_UP_MONTHS = 12L
  }
}
