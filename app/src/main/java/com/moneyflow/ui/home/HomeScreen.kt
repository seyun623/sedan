package com.moneyflow.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.ui.account.AccountFormDialog
import com.moneyflow.ui.account.TransferDialog
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.TransactionRow
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.util.Formatters

@Composable
fun HomeScreen(
  onAddTransaction: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  onOpenAccount: (Long) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSecurity: () -> Unit,
  onNavigateToLedger: () -> Unit,
  onNavigateToAnalytics: () -> Unit,
  onNavigateToBudget: () -> Unit,
  onOpenBackup: () -> Unit = {},
  viewModel: HomeViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showAddAccount by rememberSaveable { mutableStateOf(false) }
  var showTransfer by rememberSaveable { mutableStateOf(false) }
  var transferFromId by rememberSaveable { mutableStateOf<Long?>(null) }
  var lockTipDismissed by rememberSaveable { mutableStateOf(false) }

  val groups = remember(state.accounts) { groupAccounts(state.accounts) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("home_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item(key = "header") {
      HomeHeader(onAddAccount = { showAddAccount = true }, onOpenSettings = onOpenSettings)
    }

    if (state.isLoading) {
      item(key = "loading") {
        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = TossBlue)
        }
      }
      return@LazyColumn
    }

    if (!state.isLockEnabled && !lockTipDismissed) {
      item(key = "lock_tip") {
        LockTipCard(onOpenSecurity = onOpenSecurity, onDismiss = { lockTipDismissed = true })
      }
    }

    if (state.hasNoAccounts) {
      item(key = "onboarding") {
        Surface(shape = RoundedCornerShape(24.dp), color = CardSurface) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
            EmptyState(
              icon = Icons.Filled.AccountBalanceWallet,
              title = "먼저 계좌를 등록해 주세요",
              description = "통장, 카드, 현금을 등록하면 잔액과 지출을 한눈에 볼 수 있어요.",
              actionLabel = "계좌 추가",
              onAction = { showAddAccount = true },
              modifier = Modifier.testTag("home_onboarding")
            )
            TextButton(onClick = onOpenBackup, modifier = Modifier.testTag("home_restore_backup")) {
              Text("백업 파일에서 복원하기", color = TossBlue, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = viewModel::loadSampleData, modifier = Modifier.testTag("home_load_sample")) {
              Text("샘플 데이터로 둘러보기", color = Gray600, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
      return@LazyColumn
    }

    item(key = "assets") {
      TotalAssetsCard(
        state = state,
        onTransfer = { transferFromId = null; showTransfer = true }
      )
    }

    item(key = "spending") {
      SpendingSummaryCard(
        state = state,
        onNavigateToAnalytics = onNavigateToAnalytics,
        onNavigateToBudget = onNavigateToBudget
      )
    }

    if (state.cardStatuses.isNotEmpty()) {
      item(key = "card_goals") {
        CardGoalsCard(
          statuses = state.cardStatuses,
          names = state.accounts.associate { it.id to it.name },
          onOpenAccount = onOpenAccount
        )
      }
    }

    groups.forEach { group ->
      item(key = "group_${group.title}") {
        AccountSectionHeader(title = group.title, count = group.accounts.size, total = group.total, isDebt = group.isDebt)
      }
      items(group.accounts, key = { "acc_${it.id}" }) { account ->
        AccountCard(account = account, onClick = { onOpenAccount(account.id) })
      }
    }

    item(key = "recent_header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "최근 거래",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onNavigateToLedger, modifier = Modifier.testTag("home_view_all_transactions")) {
          Text("전체 보기", color = TossBlue, fontWeight = FontWeight.Bold)
        }
      }
    }

    if (state.recentTransactions.isEmpty()) {
      item(key = "recent_empty") {
        Surface(shape = RoundedCornerShape(20.dp), color = CardSurface) {
          EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "아직 거래 내역이 없어요",
            description = "첫 지출이나 수입을 기록해 보세요.",
            actionLabel = "거래 추가",
            onAction = onAddTransaction
          )
        }
      }
    } else {
      items(state.recentTransactions, key = { "tx_${it.id}" }) { tx ->
        Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
          TransactionRow(transaction = tx, onClick = { onOpenTransaction(tx.id) }, showDate = true)
        }
      }
    }
  }

  if (showAddAccount) {
    AccountFormDialog(
      initial = null,
      onDismiss = { showAddAccount = false },
      onSubmit = { d ->
        viewModel.addAccount(d.name, d.bankName, d.type, d.accountNumber, d.balance, d.colorHex, d.isDebt)
        showAddAccount = false
      }
    )
  }

  if (showTransfer) {
    TransferDialog(
      accounts = state.accounts,
      initialFromId = transferFromId,
      onDismiss = { showTransfer = false },
      onTransfer = { from, to, amount, memo -> viewModel.transfer(from, to, amount, memo) }
    )
  }
}

@Composable
private fun HomeHeader(onAddAccount: () -> Unit, onOpenSettings: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      "내 자산",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.ExtraBold,
      color = Gray900,
      modifier = Modifier.weight(1f).padding(start = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    IconButton(onClick = onAddAccount, modifier = Modifier.testTag("home_add_account_button")) {
      Icon(Icons.Filled.Add, contentDescription = "계좌 추가", tint = Gray900)
    }
    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings_button")) {
      Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = Gray900)
    }
  }
}

@Composable
private fun AccountSectionHeader(title: String, count: Int, total: Long, isDebt: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      "$title $count",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Gray900,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      Formatters.formatCurrency(total),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = if (isDebt) AccentRed else TossBlue,
      maxLines = 1
    )
  }
}

internal data class AccountGroup(
  val title: String,
  val accounts: List<AccountEntity>,
  val isDebt: Boolean
) {
  val total: Long get() = accounts.sumOf { it.balance }
}

/** Groups accounts for the home list; favorites first inside each group. */
internal fun groupAccounts(accounts: List<AccountEntity>): List<AccountGroup> {
  val sorted = accounts.sortedByDescending { it.isFavorite }
  val debts = sorted.filter { it.isDebt }
  val assets = sorted.filter { !it.isDebt }
  return listOf(
    AccountGroup("입출금 · 예적금 · 현금", assets.filter { it.accountType != AccountType.INVESTMENT }, isDebt = false),
    AccountGroup("투자", assets.filter { it.accountType == AccountType.INVESTMENT }, isDebt = false),
    AccountGroup("카드 · 대출", debts, isDebt = true)
  ).filter { it.accounts.isNotEmpty() }
}
