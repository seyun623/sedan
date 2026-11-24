package com.moneyflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimpleHomeUi(
  val monthLabel: String = "",
  val expense: Long = 0,
  val income: Long = 0,
  /** Monthly budget, 0 = none. */
  val budget: Long = 0,
  val recent: List<TransactionEntity> = emptyList()
) {
  val left: Long get() = budget - expense
}

@HiltViewModel
class SimpleHomeViewModel @Inject constructor(
  ledger: LedgerRepository,
  budgets: BudgetRepository,
  private val settings: SettingsRepository,
  stats: MonthlyStatsCalculator,
  clock: AppClock
) : ViewModel() {
  private val month = clock.currentMonth()
  val ui: StateFlow<SimpleHomeUi> = combine(ledger.transactions, budgets.observeBudget(month)) { all, b ->
    val txs = stats.inMonth(all, month)
    SimpleHomeUi("${month.monthValue}월", stats.totalExpense(txs), stats.totalIncome(txs), b.total, all.filter { it.type != TransactionType.TRANSFER }.take(5))
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimpleHomeUi())

  fun exit() = viewModelScope.launch { settings.setSimpleMode(false) }
}

/** 간단 모드 home: this month's spending in big letters, two big buttons and the last few records. */
@Composable
fun SimpleHomeScreen(
  onAddExpense: () -> Unit,
  onAddIncome: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  onOpenLedger: () -> Unit,
  onOpenSettings: () -> Unit,
  viewModel: SimpleHomeViewModel = hiltViewModel()
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  Column(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp).testTag("simple_home"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("머니플로우", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Gray900, modifier = Modifier.weight(1f))
      IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "설정", tint = Gray600) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardSurface).padding(24.dp)) {
      Text("${ui.monthLabel}에 쓴 돈", fontSize = 22.sp, color = Gray600)
      Text(Formatters.formatCurrency(ui.expense), fontSize = 40.sp, fontWeight = FontWeight.Black, color = Gray900, modifier = Modifier.testTag("simple_expense"))
      if (ui.budget > 0) {
        Text(
          if (ui.left >= 0) "예산에서 ${Formatters.formatCurrency(ui.left)} 남았어요" else "예산보다 ${Formatters.formatCurrency(-ui.left)} 더 썼어요",
          fontSize = 20.sp, color = if (ui.left >= 0) AccentGreen else AccentRed
        )
      }
      if (ui.income > 0) Text("들어온 돈 ${Formatters.formatCurrency(ui.income)}", fontSize = 20.sp, color = Gray600)
    }
    Button(
      onClick = onAddExpense,
      modifier = Modifier.fillMaxWidth().height(80.dp).testTag("simple_add_expense"),
      shape = RoundedCornerShape(20.dp),
      colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
    ) { Text("돈 쓴 것 적기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
    Button(
      onClick = onAddIncome,
      modifier = Modifier.fillMaxWidth().height(68.dp).testTag("simple_add_income"),
      shape = RoundedCornerShape(20.dp),
      colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = OnBrand)
    ) { Text("들어온 돈 적기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

    Text("최근 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)
    if (ui.recent.isEmpty()) Text("아직 기록이 없어요.", fontSize = 20.sp, color = Gray600)
    ui.recent.forEach { tx ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardSurface)
          .clickable { onOpenTransaction(tx.id) }
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(tx.memo.ifBlank { tx.category }, fontSize = 20.sp, color = Gray900, maxLines = 1)
          Text(Formatters.formatFullDate(tx.timestamp), fontSize = 16.sp, color = Gray600)
        }
        Text(
          (if (tx.type == TransactionType.INCOME) "+" else "-") + Formatters.formatCurrency(tx.amount),
          fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (tx.type == TransactionType.INCOME) AccentGreen else Gray900
        )
      }
    }
    OutlinedButton(onClick = onOpenLedger, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
      Text("모든 기록 보기", fontSize = 20.sp)
    }
    OutlinedButton(onClick = viewModel::exit, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("simple_exit"), shape = RoundedCornerShape(16.dp)) {
      Text("일반 화면으로 돌아가기", fontSize = 18.sp)
    }
    Spacer(Modifier.height(24.dp))
  }
}
