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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.moneyflow.ui.theme.AccentGreenText
import com.moneyflow.ui.theme.AccentRedText
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.moneyflow.domain.todayFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimpleHomeUi(
  val monthLabel: String = "",
  val expense: Long = 0,
  val income: Long = 0,
  /** Spending that counts toward the budget ("예산 제외" left out). */
  val budgetExpense: Long = 0,
  /** Monthly budget, 0 = none. */
  val budget: Long = 0,
  val recent: List<TransactionEntity> = emptyList(),
  val hasAccounts: Boolean = true,
  val loaded: Boolean = false
) {
  val left: Long get() = budget - budgetExpense
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SimpleHomeViewModel @Inject constructor(
  private val ledger: LedgerRepository,
  budgets: BudgetRepository,
  private val settings: SettingsRepository,
  stats: MonthlyStatsCalculator,
  clock: AppClock
) : ViewModel() {
  // Follows the date, so the screen moves to the new month at midnight on the 1st.
  private val month = clock.todayFlow().map { java.time.YearMonth.from(it) }.distinctUntilChanged()

  val ui: StateFlow<SimpleHomeUi> = month.flatMapLatest { m ->
    combine(ledger.transactions, ledger.accounts, budgets.observeBudget(m)) { all, accounts, raw ->
      val b = budgets.withCarry(raw, settings.current().budgetCarryOver, clock.zone)
      val txs = stats.inMonth(all, m)
      val now = clock.nowMillis()
      SimpleHomeUi(
        monthLabel = "${m.monthValue}월",
        expense = stats.totalExpense(txs),
        income = stats.totalIncome(txs),
        budgetExpense = stats.budgetExpense(txs),
        budget = b.total,
        recent = all.filter { it.type != TransactionType.TRANSFER && it.timestamp <= now }.take(5),
        hasAccounts = accounts.isNotEmpty(),
        loaded = true
      )
    }
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimpleHomeUi())

  fun exit() = viewModelScope.launch { settings.setSimpleMode(false) }

  /** First account for a parent: a cash wallet or a bank book, with the current balance. */
  fun addAccount(name: String, bankName: String, type: com.moneyflow.data.model.AccountType, balance: Long, isDebt: Boolean) = viewModelScope.launch {
    ledger.addAccount(com.moneyflow.data.model.AccountEntity(name = name, bankName = bankName, accountType = type, balance = balance, isDebt = isDebt))
  }
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
  var confirmExit by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
  var addAccount by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
  Column(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp).testTag("simple_home"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("머니플로우", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Gray900, modifier = Modifier.weight(1f))
      IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "설정", tint = Gray600) }
    }
    if (!ui.loaded) return@Column
    if (!ui.hasAccounts) {
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardSurface).padding(24.dp)) {
        Text("먼저 돈이 들어 있는 곳을 하나 만들어 주세요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)
        Text("지갑(현금)이나 통장을 만들면 쓴 돈을 적을 수 있어요.", fontSize = 18.sp, color = Gray600, modifier = Modifier.padding(top = 4.dp))
        Button(
          onClick = { addAccount = true },
          modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 12.dp).testTag("simple_add_account"),
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) { Text("지갑·통장 만들기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
      }
    } else {
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardSurface).padding(24.dp)) {
        Text("${ui.monthLabel}에 쓴 돈", fontSize = 22.sp, color = Gray600)
        val amount = Formatters.formatCurrency(ui.expense)
        Text(
          amount, fontSize = if (amount.length > 11) 32.sp else 40.sp, fontWeight = FontWeight.Black, color = Gray900,
          maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("simple_expense")
        )
        if (ui.budget > 0) {
          Text(
            if (ui.left >= 0) "예산에서 ${Formatters.formatCurrency(ui.left)} 남았어요" else "예산보다 ${Formatters.formatCurrency(-ui.left)} 더 썼어요",
            fontSize = 20.sp, color = if (ui.left >= 0) AccentGreenText else AccentRedText
          )
        }
        if (ui.income > 0) Text("들어온 돈 ${Formatters.formatCurrency(ui.income)}", fontSize = 20.sp, color = Gray600)
      }
      Button(
        onClick = onAddExpense,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("simple_add_expense"),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
      ) { Text("돈 쓴 것 적기", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
      Button(
        onClick = onAddIncome,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("simple_add_income"),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreenText, contentColor = OnBrand)
      ) { Text("들어온 돈 적기", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    }

    Text("최근 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)
    if (ui.recent.isEmpty()) Text("아직 기록이 없어요.", fontSize = 20.sp, color = Gray600)
    ui.recent.forEach { tx ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardSurface)
          .clickable(role = Role.Button, onClickLabel = "자세히 보기") { onOpenTransaction(tx.id) }
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(tx.memo.ifBlank { tx.category }, fontSize = 20.sp, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(Formatters.formatFullDate(tx.timestamp), fontSize = 16.sp, color = Gray600)
        }
        Text(
          (if (tx.type == TransactionType.INCOME) "+" else "-") + Formatters.formatCurrency(tx.amount),
          fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (tx.type == TransactionType.INCOME) AccentGreenText else Gray900,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
    }
    OutlinedButton(onClick = onOpenLedger, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
      Text("모든 기록 보기", fontSize = 20.sp, textAlign = TextAlign.Center)
    }
    OutlinedButton(onClick = { confirmExit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("simple_exit"), shape = RoundedCornerShape(16.dp)) {
      Text("일반 화면으로 돌아가기", fontSize = 18.sp, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
  }

  if (confirmExit) {
    com.moneyflow.ui.common.ConfirmDialog(
      title = "일반 화면으로 바꿀까요?",
      message = "글씨가 작아지고 메뉴가 많은 화면이 나와요. 설정 → 화면 → 간단 모드에서 다시 켤 수 있어요.",
      confirmLabel = "바꾸기",
      onConfirm = { confirmExit = false; viewModel.exit() },
      onDismiss = { confirmExit = false },
      destructive = false
    )
  }
  if (addAccount) {
    com.moneyflow.ui.account.AccountFormDialog(
      initial = null,
      onDismiss = { addAccount = false },
      onSubmit = { d -> addAccount = false; viewModel.addAccount(d.name, d.bankName, d.type, d.balance, d.isDebt) }
    )
  }
}
