package com.moneyflow.domain.alerts

import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.util.Formatters
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ReminderChannel(val id: String, val label: String) {
  BUDGET("budget_alerts", "예산 알림"),
  BACKUP("backup_alerts", "백업 알림"),
  CARD("card_alerts", "카드 실적·혜택 알림"),
  CAPTURE("capture_alerts", "결제 확인 알림"),
  BILL("bill_alerts", "결제일 미리 알림")
}

/** A notification to show. [key] makes sure each one is sent once. */
data class Reminder(val key: String, val channel: ReminderChannel, val title: String, val text: String)

/** Budget 80% / 100% alerts and the monthly summary (pure, testable). */
object BudgetReminders {
  private val LEVELS = listOf(100, 80)

  /**
   * @param spent budget expense of [month] so far (excluded-from-budget items not counted)
   * @param categorySpent the same by category
   */
  fun due(month: YearMonth, budget: MonthBudget, spent: Long, categorySpent: Map<String, Long>, sent: Set<String>): List<Reminder> {
    val out = mutableListOf<Reminder>()
    val label = "${month.monthValue}월"
    if (budget.total > 0) {
      // Only the highest level reached; lower levels are marked as sent with it.
      LEVELS.firstOrNull { spent * 100 >= budget.total * it }?.let { level ->
        val key = "budget|$month|$level"
        if (key !in sent) {
          out += Reminder(
            key, ReminderChannel.BUDGET,
            if (level >= 100) "$label 예산을 넘었어요" else "$label 예산의 ${level}%를 썼어요",
            if (level >= 100) "예산 ${Formatters.formatCurrency(budget.total)}보다 ${Formatters.formatCurrency(spent - budget.total)} 더 썼어요."
            else "${Formatters.formatCurrency(spent)} / ${Formatters.formatCurrency(budget.total)} · ${Formatters.formatCurrency(budget.total - spent)} 남았어요."
          )
        }
      }
    }
    budget.categoryLimits.forEach { (category, limit) ->
      val used = categorySpent[category] ?: 0L
      val key = "budget|$month|cat|$category"
      if (limit > 0 && used > limit && key !in sent) {
        out += Reminder(key, ReminderChannel.BUDGET, "$label $category 예산을 넘었어요",
          "${Formatters.formatCurrency(used)} / ${Formatters.formatCurrency(limit)}")
      }
    }
    return out
  }

  /** Keys to mark as sent together with [r] (a 100% alert also covers 80%). */
  fun alsoCovers(r: Reminder): List<String> =
    if (r.key.endsWith("|100") && r.key.count { it == '|' } == 2) listOf(r.key.removeSuffix("|100") + "|80") else emptyList()

  /** Summary of last month, sent once during the first 3 days of a month. */
  fun monthlySummary(
    today: LocalDate,
    income: Long,
    expense: Long,
    budgetTotal: Long,
    topCategory: Pair<String, Long>?,
    previousExpense: Long?,
    sent: Set<String>,
    /** Spending that counts toward the budget (items marked "예산 제외" left out). */
    budgetExpense: Long = expense
  ): Reminder? {
    if (today.dayOfMonth > 3) return null
    val last = YearMonth.from(today).minusMonths(1)
    val key = "summary|$last"
    if (key in sent || (income == 0L && expense == 0L)) return null
    val text = buildList {
      add("지출 ${Formatters.formatCurrency(expense)} · 수입 ${Formatters.formatCurrency(income)}")
      if (budgetTotal > 0) add("예산 대비 ${budgetExpense * 100 / budgetTotal}%")
      previousExpense?.takeIf { it > 0 }?.let { prev ->
        val diff = expense - prev
        add(if (diff >= 0) "전달보다 ${Formatters.formatCurrency(diff)} 더 씀" else "전달보다 ${Formatters.formatCurrency(-diff)} 덜 씀")
      }
      topCategory?.let { add("가장 많이 쓴 곳: ${it.first} ${Formatters.formatCurrency(it.second)}") }
    }.joinToString("\n")
    return Reminder(key, ReminderChannel.BUDGET, "${last.monthValue}월 가계부 요약", text)
  }
}

/** Card payment day (3 days before) and recurring payments (the day before), pure and testable. */
object BillReminders {
  const val CARD_DAYS_BEFORE = 3L
  const val RECURRING_DAYS_BEFORE = 1L

  /** The next date with day [day] (clamped to the month's length) on or after [from]. */
  fun nextDate(from: LocalDate, day: Int): LocalDate {
    fun inMonth(m: YearMonth) = m.atDay(day.coerceIn(1, m.lengthOfMonth()))
    val thisMonth = inMonth(YearMonth.from(from))
    return if (!thisMonth.isBefore(from)) thisMonth else inMonth(YearMonth.from(from).plusMonths(1))
  }

  fun due(
    today: LocalDate,
    accounts: List<com.moneyflow.data.model.AccountEntity>,
    recurring: List<com.moneyflow.data.model.RecurringTransactionEntity>,
    sent: Set<String>,
    /** Bills read from the card company (amount and date to pay). */
    bills: Map<Long, com.moneyflow.domain.card.CardBill> = emptyMap()
  ): List<Reminder> {
    val out = mutableListOf<Reminder>()
    accounts.forEach { a ->
      val bill = bills[a.id]?.takeIf { b -> b.dueDate?.let { !it.isBefore(today) } == true }
      // The card company's own date wins over the day typed in (it moves on weekends and holidays).
      val date = bill?.dueDate ?: a.paymentDay.takeIf { it in 1..31 }?.let { nextDate(today, it) } ?: return@forEach
      val days = ChronoUnit.DAYS.between(today, date)
      val key = "bill|card|${a.id}|$date"
      if (days in 0..CARD_DAYS_BEFORE && key !in sent) {
        val label = if (a.accountType == com.moneyflow.data.model.AccountType.LOAN) "상환일" else "결제일"
        val whenText = if (days == 0L) "오늘" else "${days}일 뒤(${date.monthValue}/${date.dayOfMonth})"
        out += Reminder(key, ReminderChannel.BILL, "${a.name} $label${if (days == 0L) "이에요" else "이 다가와요"}",
          "$whenText ${label}이에요." + when {
            bill != null -> " 결제하실 금액 ${Formatters.formatCurrency(bill.amount)} (${bill.source} 기준). 통장 잔액을 확인해 주세요."
            a.isDebt && a.balance > 0 -> " 지금까지 ${if (label == "상환일") "남은 금액" else "카드 사용 잔액(할부 포함)"} ${Formatters.formatCurrency(a.balance)}"
            else -> ""
          })
      }
    }
    recurring.filter { it.isActive && it.type != com.moneyflow.data.model.TransactionType.INCOME }.forEach { r ->
      val date = nextDate(today, r.dayOfMonth)
      val dateMonth = YearMonth.from(date).toString()
      if (r.lastExecutedMonth != null && r.lastExecutedMonth >= dateMonth) return@forEach
      if (r.startMonth.isNotEmpty() && r.startMonth > dateMonth) return@forEach
      val days = ChronoUnit.DAYS.between(today, date)
      val key = "bill|rec|${r.id}|$dateMonth"
      if (days in 1..RECURRING_DAYS_BEFORE && key !in sent) {
        out += Reminder(key, ReminderChannel.BILL, "내일 '${r.title}' ${Formatters.formatCurrency(r.amount)}이 나가요",
          "${r.accountName}에서 ${date.monthValue}/${date.dayOfMonth}에 빠져나가요. 잔액을 확인해 주세요.")
      }
    }
    return out
  }
}

/** "백업한 지 오래됐어요" (pure, testable). */
object BackupReminders {
  const val STALE_DAYS = 14L

  /**
   * @param lastBackupAt last backup (manual or automatic), 0 = never
   * @param firstUseAt earliest record in the app (no reminder before using it for a while)
   * @param autoBackupError the last automatic backup failed with this message
   */
  fun due(today: LocalDate, lastBackup: LocalDate?, firstUse: LocalDate?, autoBackupError: String?, sent: Set<String>): Reminder? {
    val weekKey = today.toEpochDay() / 7
    if (autoBackupError != null) {
      val key = "backup|error|$weekKey"
      return if (key in sent) null else Reminder(key, ReminderChannel.BACKUP, "자동 백업을 하지 못했어요",
        "$autoBackupError\n설정 → 백업 및 복원에서 확인해 주세요.")
    }
    val since = lastBackup ?: firstUse ?: return null
    val days = ChronoUnit.DAYS.between(since, today)
    if (days < STALE_DAYS) return null
    val key = "backup|stale|$weekKey"
    if (key in sent) return null
    return Reminder(
      key, ReminderChannel.BACKUP,
      if (lastBackup == null) "아직 백업한 적이 없어요" else "백업한 지 ${days}일 지났어요",
      "백업해 두면 휴대폰을 바꾸거나 잃어버려도 기록을 그대로 옮길 수 있어요. 설정 → 백업 및 복원에서 자동 백업을 켜 두면 편해요."
    )
  }
}
