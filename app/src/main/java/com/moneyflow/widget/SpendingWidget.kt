package com.moneyflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneyflow.MainActivity
import com.moneyflow.R
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.capture.FormPrefill
import com.moneyflow.util.Formatters
import com.moneyflow.work.PrefillIntents
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.moneyflow.data.model.spending

/** What the widget shows. [hidden] = app lock is on and amounts are not shown on the home screen. */
data class WidgetContent(
  val hidden: Boolean,
  val today: Long = 0,
  val month: Long = 0,
  val monthLabel: String = "",
  /** Remaining budget (negative = over); null when no budget is set. */
  val remainingBudget: Long? = null,
  val budgetTotal: Long = 0,
  /** This month's biggest categories (large widget). */
  val topCategories: List<Pair<String, Long>> = emptyList()
) {
  /** 0..100 of the budget used; null without a budget. */
  val budgetPercent: Int?
    get() {
      val left = remainingBudget ?: return null
      if (hidden || budgetTotal <= 0) return null
      return ((budgetTotal - left) * 100 / budgetTotal).toInt().coerceIn(0, 100)
    }
  val categoriesText: String
    get() = if (hidden) "" else topCategories.joinToString("\n") { (c, a) -> "$c  ${Formatters.formatCurrency(a)}" }
  val todayText: String get() = if (hidden) "앱 잠금 중" else Formatters.formatCurrency(today)
  val monthText: String get() = if (hidden) "눌러서 열기" else "$monthLabel ${Formatters.formatCurrency(month)}"
  val budgetText: String
    get() = when {
      hidden || remainingBudget == null -> ""
      remainingBudget >= 0 -> "예산 ${Formatters.formatCurrency(remainingBudget)} 남음"
      else -> "예산 ${Formatters.formatCurrency(-remainingBudget)} 초과"
    }

  companion object {
    /** Pure: totals for [today] from this month's transactions. */
    fun of(monthTxs: List<TransactionEntity>, today: LocalDate, zone: ZoneId, budgetTotal: Long, hidden: Boolean): WidgetContent {
      if (hidden) return WidgetContent(hidden = true)
      val expenses = monthTxs.filter { it.type.isSpending }
      val todayStart = MonthKeys.dayStartMillis(today, zone)
      val todayEnd = MonthKeys.dayEndMillis(today, zone)
      val budgetSpent = expenses.filter { !it.isExcludedFromBudget }.sumOf { it.spending }.coerceAtLeast(0)
      return WidgetContent(
        hidden = false,
        today = expenses.filter { it.timestamp in todayStart..todayEnd }.sumOf { it.spending }.coerceAtLeast(0),
        month = expenses.sumOf { it.spending }.coerceAtLeast(0),
        monthLabel = "${today.monthValue}월",
        remainingBudget = if (budgetTotal > 0) budgetTotal - budgetSpent else null,
        budgetTotal = budgetTotal,
        topCategories = expenses.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }
          .sortedByDescending { it.second }.take(3)
      )
    }
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
  fun db(): AppDatabase
  fun settings(): SettingsRepository
  fun budgets(): BudgetRepository
  fun clock(): AppClock
}

/** Home screen widget: today's and this month's spending, remaining budget, "+ 기록". */
class SpendingWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, ids)

  /** The widget was resized: pick the layout for the new size. */
  override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
    refresh(context, intArrayOf(id))

  private fun refresh(context: Context, ids: IntArray) {
    val pending = goAsync()
    scope.launch {
      try { SpendingWidget.update(context, ids) } catch (_: Exception) { } finally { pending.finish() }
    }
  }

  private companion object {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}

object SpendingWidget {
  /** Refreshes all placed widgets (call after records change). */
  suspend fun updateAll(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, SpendingWidgetProvider::class.java))
    if (ids.isNotEmpty()) update(context, ids)
  }

  suspend fun update(context: Context, ids: IntArray) {
    val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val clock = ep.clock()
    val s = ep.settings().current()
    val month = YearMonth.from(clock.today())
    val content = if (s.isLockEnabled && !s.widgetShowAmountsWhenLocked) {
      WidgetContent(hidden = true)
    } else {
      val txs = ep.db().transactionDao().getBetween(MonthKeys.startMillis(month, clock.zone), MonthKeys.endMillis(month, clock.zone))
      WidgetContent.of(txs, clock.today(), clock.zone, ep.budgets().observeBudget(month).first().total, hidden = false)
    }
    val manager = AppWidgetManager.getInstance(context)
    ids.forEach { id ->
      val rv = if (android.os.Build.VERSION.SDK_INT >= 31) {
        // The launcher picks the layout that fits as the widget is resized.
        RemoteViews(mapOf(
          android.util.SizeF(110f, 40f) to views(context, content, WidgetSize.SMALL),
          android.util.SizeF(180f, 110f) to views(context, content, WidgetSize.MEDIUM),
          android.util.SizeF(180f, 200f) to views(context, content, WidgetSize.LARGE)
        ))
      } else {
        val o = manager.getAppWidgetOptions(id)
        views(context, content, WidgetSize.of(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)))
      }
      manager.updateAppWidget(id, rv)
    }
  }

  enum class WidgetSize {
    SMALL, MEDIUM, LARGE;
    companion object {
      /** Size class from the widget's size in dp (0 = unknown → medium). */
      fun of(widthDp: Int, heightDp: Int): WidgetSize = when {
        widthDp == 0 && heightDp == 0 -> MEDIUM
        heightDp in 1..99 || widthDp in 1..169 -> SMALL
        heightDp >= 200 -> LARGE
        else -> MEDIUM
      }
    }
  }

  private fun views(context: Context, c: WidgetContent, size: WidgetSize): RemoteViews {
    val open = PendingIntent.getActivity(
      context, 100,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val add = PendingIntent.getActivity(
      context, 101,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .also { PrefillIntents.put(context, it, FormPrefill()) },
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    if (size == WidgetSize.SMALL) {
      return RemoteViews(context.packageName, R.layout.widget_spending_small).apply {
        setTextViewText(R.id.widget_today, c.todayText)
        setOnClickPendingIntent(R.id.widget_root, open)
        setOnClickPendingIntent(R.id.widget_add, add)
      }
    }
    return RemoteViews(context.packageName, R.layout.widget_spending).apply {
      setTextViewText(R.id.widget_today, c.todayText)
      setTextViewText(R.id.widget_month, c.monthText)
      setTextViewText(R.id.widget_budget, c.budgetText)
      val large = size == WidgetSize.LARGE
      val percent = c.budgetPercent
      setViewVisibility(R.id.widget_budget_bar, if (large && percent != null) android.view.View.VISIBLE else android.view.View.GONE)
      percent?.let { setProgressBar(R.id.widget_budget_bar, 100, it, false) }
      setViewVisibility(R.id.widget_categories, if (large && c.categoriesText.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
      setTextViewText(R.id.widget_categories, c.categoriesText)
      setOnClickPendingIntent(R.id.widget_root, open)
      setOnClickPendingIntent(R.id.widget_add, add)
    }
  }
}
