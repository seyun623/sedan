package com.moneyflow.domain.report

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.RuleRepository
import com.moneyflow.domain.CategorySpend
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.MonthlyStatsCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class NetWorthPoint(val month: YearMonth, val assets: Long, val debts: Long) {
  val netWorth: Long get() = assets - debts
}

/**
 * Month-end net worth, worked out backwards from today's balances by undoing later
 * transactions (same balance rules as the ledger). Balances corrected by hand are not
 * transactions, so older months are an estimate.
 */
object NetWorthHistory {
  /**
   * [nowMillis]: future-dated records (later installment parts) already changed today's balances
   * when they were saved, so they are treated as happening now.
   */
  fun monthly(
    accounts: List<AccountEntity>,
    txs: List<TransactionEntity>,
    endMonth: YearMonth,
    count: Int,
    zone: ZoneId,
    nowMillis: Long = Long.MAX_VALUE
  ): List<NetWorthPoint> {
    if (accounts.isEmpty()) return emptyList()
    val balance = accounts.associate { it.id to it.balance }.toMutableMap()
    val debt = accounts.associate { it.id to it.isDebt }
    val first = txs.minOfOrNull { it.timestamp }?.let { YearMonth.from(MonthKeys.toLocalDate(it, zone)) } ?: endMonth
    fun at(tx: TransactionEntity) = minOf(tx.timestamp, nowMillis)
    val sorted = txs.sortedByDescending { at(it) }
    var i = 0
    val out = mutableListOf<NetWorthPoint>()
    for (n in 0 until count) {
      val m = endMonth.minusMonths(n.toLong())
      if (m.isBefore(first) && n > 0) break
      val end = MonthKeys.endMillis(m, zone)
      while (i < sorted.size && at(sorted[i]) > end) { undo(sorted[i], balance, debt); i++ }
      out += NetWorthPoint(
        m,
        assets = balance.filterKeys { debt[it] == false }.values.sum(),
        debts = balance.filterKeys { debt[it] == true }.values.sum()
      )
    }
    return out.reversed()
  }

  private fun undo(tx: TransactionEntity, balance: MutableMap<Long, Long>, debt: Map<Long, Boolean>) {
    val a = tx.amount
    debt[tx.accountId]?.let { isDebt ->
      val delta = when (tx.type) {
        TransactionType.EXPENSE, TransactionType.TRANSFER -> if (isDebt) a else -a
        TransactionType.INCOME -> if (isDebt) -a else a
      }
      balance[tx.accountId] = balance.getValue(tx.accountId) - delta
    }
    if (tx.type == TransactionType.TRANSFER) {
      tx.toAccountId?.let { to -> debt[to]?.let { isDebt -> balance[to] = balance.getValue(to) - (if (isDebt) -a else a) } }
    }
  }
}

data class MonthTotals(val month: Int, val income: Long, val expense: Long)
data class PlaceSpend(val name: String, val count: Int, val amount: Long)

data class YearlyReport(
  val year: Int,
  val income: Long,
  val expense: Long,
  val months: List<MonthTotals>,
  val categories: List<CategorySpend>,
  val places: List<PlaceSpend>,
  val biggest: TransactionEntity?,
  val previousExpense: Long?,
  val transactionCount: Int
) {
  val saved: Long get() = income - expense
  val savingRate: Int? get() = if (income > 0) ((income - expense) * 100 / income).toInt() else null
  val topMonth: MonthTotals? get() = months.filter { it.expense > 0 }.maxByOrNull { it.expense }
  /** Average over months that had any spending. */
  val monthlyAverage: Long get() = months.filter { it.expense > 0 }.let { l -> if (l.isEmpty()) 0 else l.sumOf { it.expense } / l.size }
  val isEmpty: Boolean get() = transactionCount == 0
}

object YearlyReports {
  fun build(all: List<TransactionEntity>, year: Int, zone: ZoneId, stats: MonthlyStatsCalculator): YearlyReport {
    fun inYear(y: Int) = all.filter { MonthKeys.toLocalDate(it.timestamp, zone).year == y && it.type != TransactionType.TRANSFER }
    val txs = inYear(year)
    val expenses = txs.filter { it.type == TransactionType.EXPENSE }
    val months = (1..12).map { m ->
      val l = txs.filter { MonthKeys.toLocalDate(it.timestamp, zone).monthValue == m }
      MonthTotals(m, stats.totalIncome(l), stats.totalExpense(l))
    }
    val places = expenses.groupBy { RuleRepository.baseMerchant(it.memo).ifBlank { it.category } }
      .map { (name, l) -> PlaceSpend(name, l.size, l.sumOf { it.amount }) }
      .sortedByDescending { it.amount }.take(5)
    val prev = inYear(year - 1)
    return YearlyReport(
      year = year,
      income = stats.totalIncome(txs),
      expense = stats.totalExpense(txs),
      months = months,
      categories = stats.categoryBreakdown(txs),
      places = places,
      biggest = expenses.maxByOrNull { it.amount },
      previousExpense = if (prev.isEmpty()) null else stats.totalExpense(prev),
      transactionCount = txs.size
    )
  }

  /** Years that have records, newest first (always includes [today]'s year). */
  fun years(all: List<TransactionEntity>, today: LocalDate, zone: ZoneId): List<Int> =
    (all.map { MonthKeys.toLocalDate(it.timestamp, zone).year } + today.year).distinct().sortedDescending()
}
