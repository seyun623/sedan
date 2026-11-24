package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.alerts.BackupReminders
import com.moneyflow.domain.alerts.BudgetReminders
import com.moneyflow.domain.alerts.Reminder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Works out which budget / summary / backup reminders are due now. */
@Singleton
class ReminderChecker @Inject constructor(
  private val db: AppDatabase,
  private val budgets: BudgetRepository,
  private val settings: SettingsRepository,
  private val stats: MonthlyStatsCalculator,
  private val clock: AppClock
) {
  suspend fun due(): List<Reminder> {
    val s = settings.current()
    val sent = s.sentCardAlerts
    val today = clock.today()
    val month = clock.currentMonth()
    val zone = clock.zone
    val dao = db.transactionDao()
    val out = mutableListOf<Reminder>()

    if (s.budgetAlertsEnabled) {
      val txs = dao.getBetween(MonthKeys.startMillis(month, zone), MonthKeys.endMillis(month, zone))
      val budgetTxs = txs.filter { it.type == TransactionType.EXPENSE && !it.isExcludedFromBudget }
      out += BudgetReminders.due(
        month, budgets.observeBudget(month).first(), budgetTxs.sumOf { it.amount },
        budgetTxs.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }, sent
      )
    }
    if (s.monthlySummaryEnabled) {
      val last = month.minusMonths(1)
      val txs = dao.getBetween(MonthKeys.startMillis(last, zone), MonthKeys.endMillis(last, zone))
      val before = dao.getBetween(MonthKeys.startMillis(last.minusMonths(1), zone), MonthKeys.endMillis(last.minusMonths(1), zone))
      val top = stats.categoryBreakdown(txs).firstOrNull()?.let { it.category to it.amount }
      BudgetReminders.monthlySummary(
        today, stats.totalIncome(txs), stats.totalExpense(txs), budgets.observeBudget(last).first().total, top,
        stats.totalExpense(before).takeIf { before.isNotEmpty() }, sent
      )?.let { out += it }
    }
    if (s.billRemindersEnabled) {
      out += com.moneyflow.domain.alerts.BillReminders.due(today, db.accountDao().getAll(), db.recurringTransactionDao().getActive(), sent)
    }
    if (s.backupReminderEnabled) {
      val lastBackup = s.lastBackupAt.takeIf { it > 0 }?.let { MonthKeys.toLocalDate(it, zone) }
      val firstUse = dao.earliestTimestamp()?.let { MonthKeys.toLocalDate(it, zone) }?.let { minOf(it, today) }
      BackupReminders.due(today, lastBackup, firstUse, s.autoBackupError?.takeIf { s.autoBackupEnabled }, sent)?.let { out += it }
    }
    return out
  }

  suspend fun markSent(reminders: List<Reminder>) =
    settings.markAlertsSent(reminders.flatMap { listOf(it.key) + BudgetReminders.alsoCovers(it) })
}

/** Budget alerts, monthly summary and backup reminders, checked about every 6 hours. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val checker: ReminderChecker
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    if (!Notifications.canNotify(applicationContext)) return Result.success()
    val due = checker.due()
    val posted = due.filter { Notifications.post(applicationContext, it) }
    checker.markSent(posted)
    return Result.success()
  }

  companion object {
    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "reminders", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
      )
    }
  }
}
