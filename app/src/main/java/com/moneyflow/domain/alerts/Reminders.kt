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
  CAPTURE("capture_alerts", "결제 확인 알림")
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
    sent: Set<String>
  ): Reminder? {
    if (today.dayOfMonth > 3) return null
    val last = YearMonth.from(today).minusMonths(1)
    val key = "summary|$last"
    if (key in sent || (income == 0L && expense == 0L)) return null
    val text = buildList {
      add("지출 ${Formatters.formatCurrency(expense)} · 수입 ${Formatters.formatCurrency(income)}")
      if (budgetTotal > 0) add("예산 대비 ${expense * 100 / budgetTotal}%")
      previousExpense?.takeIf { it > 0 }?.let { prev ->
        val diff = expense - prev
        add(if (diff >= 0) "전달보다 ${Formatters.formatCurrency(diff)} 더 씀" else "전달보다 ${Formatters.formatCurrency(-diff)} 덜 씀")
      }
      topCategory?.let { add("가장 많이 쓴 곳: ${it.first} ${Formatters.formatCurrency(it.second)}") }
    }.joinToString("\n")
    return Reminder(key, ReminderChannel.BUDGET, "${last.monthValue}월 가계부 요약", text)
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
      "휴대폰을 잃어버리면 기록도 사라져요. 설정 → 백업 및 복원에서 백업하거나 자동 백업을 켜 주세요."
    )
  }
}
