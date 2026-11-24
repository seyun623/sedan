package com.moneyflow.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.MonthSwitcher
import com.moneyflow.ui.common.TransactionRow
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.CsvExportHelper
import com.moneyflow.util.Formatters
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LedgerScreen(
  onAddTransaction: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  viewModel: LedgerViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val query by viewModel.queryInput.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val filters = state.filters

  // Group by day, keeping the newest-first order of the list.
  val groupedByDay = remember(state.transactions) {
    val zone = com.moneyflow.domain.APP_ZONE
    state.transactions.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
  }
  val today = state.today
  var showDetail by remember { mutableStateOf(false) }
  if (showDetail) {
    DetailFilterDialog(
      state = state,
      onDismiss = { showDetail = false },
      onApply = viewModel::setDetail,
      onSave = { viewModel.saveCurrent(it) },
      onDeleteSaved = viewModel::deleteSaved
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Gray100)
      .testTag("ledger_screen")
  ) {
    LedgerHeader(
      state = state,
      onPrevMonth = viewModel::previousMonth,
      onNextMonth = viewModel::nextMonth,
      onCurrentMonth = viewModel::goToCurrentMonth,
      onToggleCalendar = viewModel::toggleCalendarView,
      onExportCsv = {
        // Export exactly what is listed; name the file after the shown range (and mark filtered lists).
        val suffix = if (state.filters.isAnyActive) "${state.dateRangeLabel}_검색결과" else state.monthLabel
        CsvExportHelper.exportTransactionsToCsv(context, state.transactions, suffix)
      }
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      // Bottom padding keeps the last row clear of the floating "+" button.
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item(key = "filters", contentType = "filters") {
        LedgerFilterBar(
          query = query,
          onQueryChange = viewModel::setQuery,
          state = state,
          onTypeSelect = viewModel::setType,
          onCategorySelect = viewModel::setCategory,
          onPresetSelect = viewModel::setPreset,
          onCustomRange = { s, e -> viewModel.setCustomRange(s, e) },
          onResetFilters = viewModel::resetFilters,
          onOpenDetail = { showDetail = true },
          onApplySaved = viewModel::applySaved
        )
      }

      if (filters.isCalendarView) {
        item(key = "calendar", contentType = "calendar") {
          LedgerCalendar(
            month = state.month,
            dailyExpense = state.dailyExpense,
            dailyIncome = state.dailyIncome,
            selectedDate = filters.selectedDate,
            today = today,
            onSelectDate = { viewModel.selectDate(it) }
          )
        }
      }

      when {
        state.isLoading -> item(key = "loading", contentType = "loading") {
          Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TossBlue)
          }
        }
        state.transactions.isEmpty() -> item(key = "empty", contentType = "empty") {
          if (filters.isAnyActive) {
            EmptyState(
              icon = Icons.Default.SearchOff,
              title = "조건에 맞는 내역이 없어요",
              description = "검색어나 기간, 카테고리 조건을 바꿔 보세요.",
              actionLabel = "필터 초기화",
              onAction = viewModel::resetFilters,
              modifier = Modifier.testTag("ledger_empty_filtered")
            )
          } else {
            EmptyState(
              icon = Icons.AutoMirrored.Filled.ReceiptLong,
              title = "이번 달 내역이 없어요",
              description = "지출이나 수입을 기록해 보세요.",
              actionLabel = "내역 추가",
              onAction = onAddTransaction,
              modifier = Modifier.testTag("ledger_empty")
            )
          }
        }
        else -> groupedByDay.forEach { (date, dayTxs) ->
          item(key = "day_$date", contentType = "dayHeader") {
            DayHeader(dayTxs)
          }
          items(dayTxs, key = { it.id }, contentType = { "tx" }) { tx ->
            SwipeToDeleteRow(
              transaction = tx,
              onClick = { onOpenTransaction(tx.id) },
              onDelete = { viewModel.delete(tx) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LedgerHeader(
  state: LedgerUiState,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onCurrentMonth: () -> Unit,
  onToggleCalendar: () -> Unit,
  onExportCsv: () -> Unit
) {
  Surface(color = CardSurface, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthSwitcher(
          label = state.monthLabel,
          onPrevious = onPrevMonth,
          onNext = onNextMonth,
          showToday = !state.isCurrentMonth,
          onToday = onCurrentMonth,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleCalendar, modifier = Modifier.testTag("toggle_calendar_button")) {
          Icon(
            imageVector = if (state.filters.isCalendarView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.CalendarMonth,
            contentDescription = if (state.filters.isCalendarView) "목록으로 보기" else "달력으로 보기",
            tint = TossBlue
          )
        }
        IconButton(onClick = onExportCsv, modifier = Modifier.testTag("ledger_export_csv_button")) {
          Icon(Icons.Default.FileDownload, contentDescription = "CSV로 내보내기", tint = TossBlue)
        }
      }

      // Month income / expense (unfiltered)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .background(Gray100, RoundedCornerShape(14.dp))
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text("총 수입", style = MaterialTheme.typography.labelSmall, color = Gray600)
          Text(
            "+${Formatters.formatCurrency(state.monthlyIncome)}",
            style = MaterialTheme.typography.titleMedium,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Box(Modifier.width(1.dp).height(28.dp).background(Gray200))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
          Text("총 지출", style = MaterialTheme.typography.labelSmall, color = Gray600)
          Text(
            "-${Formatters.formatCurrency(state.monthlyExpense)}",
            style = MaterialTheme.typography.titleMedium,
            color = AccentRed,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun DayHeader(dayTxs: List<TransactionEntity>) {
  val dayExpense = dayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
  val dayIncome = dayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      Formatters.formatDayHeader(dayTxs.first().timestamp),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = Gray900,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    if (dayIncome > 0) {
      Text(
        "+${Formatters.formatCurrency(dayIncome)}",
        style = MaterialTheme.typography.bodySmall,
        color = AccentGreen,
        fontWeight = FontWeight.Bold,
        maxLines = 1
      )
    }
    if (dayIncome > 0 && dayExpense > 0) Spacer(Modifier.width(8.dp))
    if (dayExpense > 0) {
      Text(
        "-${Formatters.formatCurrency(dayExpense)}",
        style = MaterialTheme.typography.bodySmall,
        color = AccentRed,
        fontWeight = FontWeight.Bold,
        maxLines = 1
      )
    }
  }
}

/** A transaction card that can be swiped right-to-left to delete (the VM offers undo). */
@Composable
private fun SwipeToDeleteRow(
  transaction: TransactionEntity,
  onClick: () -> Unit,
  onDelete: () -> kotlinx.coroutines.Deferred<Boolean>
) {
  val dismissState = rememberSwipeToDismissBoxState()
  val scope = rememberCoroutineScope()
  val shape = RoundedCornerShape(16.dp)
  SwipeToDismissBox(
    state = dismissState,
    enableDismissFromStartToEnd = false,
    onDismiss = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) {
        scope.launch {
          // Leave the row dismissed while the list updates; bring it back only if nothing was deleted.
          if (!onDelete().await()) dismissState.reset()
        }
      }
    },
    backgroundContent = {
      Box(
        Modifier.fillMaxSize().background(AccentRed, shape).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("삭제", color = OnBrand, fontWeight = FontWeight.Bold)
          Spacer(Modifier.width(6.dp))
          Icon(Icons.Default.Delete, contentDescription = null, tint = OnBrand, modifier = Modifier.size(20.dp))
        }
      }
    },
    modifier = Modifier.semantics {
      // Swipe is not discoverable for screen-reader users: expose delete as an action.
      customActions = listOf(CustomAccessibilityAction("삭제") { onDelete(); true })
    }
  ) {
    Surface(color = CardSurface, shape = shape, modifier = Modifier.fillMaxWidth()) {
      TransactionRow(transaction = transaction, onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
    }
  }
}
