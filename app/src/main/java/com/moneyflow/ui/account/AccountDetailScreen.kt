package com.moneyflow.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.TransactionRow
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
  onBack: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  onOpenCardImport: () -> Unit = {},
  viewModel: AccountDetailViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showEdit by rememberSaveable { mutableStateOf(false) }
  // Balance shown when the edit dialog was opened; if the user leaves it unchanged, the
  // current balance is kept (a transaction may have changed it while the dialog was open).
  var editBaselineBalance by rememberSaveable { mutableStateOf<Long?>(null) }
  var showProfileDialog by rememberSaveable { mutableStateOf(false) }
  /** null = closed, 0 = new benefit, otherwise the benefit id being edited. */
  var editingBenefitId by rememberSaveable { mutableStateOf<Long?>(null) }
  var showTransfer by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var confirmDeleteRelated by rememberSaveable { mutableStateOf<String?>(null) }
  var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        AccountEvent.Deleted -> onBack()
        is AccountEvent.ConfirmDeleteRelated -> confirmDeleteRelated = event.message
        is AccountEvent.Error -> errorMessage = event.message
      }
    }
  }

  val account = state.account
  Scaffold(
    containerColor = Gray100,
    // The nav host's scaffold already applies the system bar insets.
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text(account?.name ?: "계좌", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기") }
        },
        actions = {
          if (account != null) {
            IconButton(onClick = viewModel::toggleFavorite) {
              Icon(
                if (account.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (account.isFavorite) "즐겨찾기 해제" else "즐겨찾기",
                tint = if (account.isFavorite) AccentOrange else Gray600
              )
            }
            IconButton(onClick = { editBaselineBalance = account?.balance; showEdit = true }) { Icon(Icons.Filled.Edit, contentDescription = "계좌 수정", tint = Gray600) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "계좌 삭제", tint = Gray600) }
          }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Gray100, titleContentColor = Gray900)
      )
    }
  ) { padding ->
    when {
      state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = TossBlue)
      }
      account == null -> EmptyState(
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        title = "계좌를 찾을 수 없어요",
        description = "삭제되었거나 존재하지 않는 계좌예요.",
        actionLabel = "돌아가기",
        onAction = onBack,
        modifier = Modifier.padding(padding)
      )
      else -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("account_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item(key = "header") {
          AccountHeaderCard(account, transferEnabled = state.otherAccounts.isNotEmpty(), onTransfer = { showTransfer = true })
        }
        if (account.accountType == com.moneyflow.data.model.AccountType.CREDIT_CARD) item(key = "card_performance") {
          CardSection(
            status = state.cardStatus,
            profile = state.cardProfile,
            benefits = state.cardBenefits,
            imported = state.cardPerformance,
            onEditProfile = { showProfileDialog = true },
            onAddBenefit = { editingBenefitId = 0L },
            onEditBenefit = { editingBenefitId = it.id },
            onOpenCardImport = onOpenCardImport
          )
        }
        item(key = "tx_title") {
          Text(
            "거래 내역 ${state.transactions.size}건",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
          )
        }
        if (state.transactions.isEmpty()) {
          item(key = "empty") {
            Surface(shape = RoundedCornerShape(20.dp), color = CardSurface) {
              EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "이 계좌의 거래가 아직 없어요",
                description = "거래를 기록할 때 이 계좌를 선택하면 여기에 모아서 보여드려요."
              )
            }
          }
        } else {
          items(state.transactions, key = { it.id }) { tx ->
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
              TransactionRow(transaction = tx, onClick = { onOpenTransaction(tx.id) }, showDate = true)
            }
          }
        }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
      }
    }
  }

  if (showProfileDialog) {
    CardProfileDialog(
      initial = state.cardProfile,
      onDismiss = { showProfileDialog = false },
      onSave = viewModel::saveCardProfile
    )
  }
  editingBenefitId?.let { id ->
    BenefitDialog(
      initial = state.cardBenefits.firstOrNull { it.id == id },
      onDismiss = { editingBenefitId = null },
      onSave = viewModel::saveBenefit,
      onDelete = viewModel::deleteBenefit
    )
  }

  if (showEdit && account != null) {
    AccountFormDialog(
      initial = account,
      onDismiss = { showEdit = false },
      onSubmit = { d ->
        viewModel.update(
          account.copy(
            name = d.name, bankName = d.bankName, accountType = d.type, accountNumber = d.accountNumber,
            balance = d.balance, colorHex = d.colorHex, isDebt = d.isDebt
          ),
          balanceShownWhenEditing = editBaselineBalance
        )
        showEdit = false
      }
    )
  }

  if (showTransfer && account != null) {
    TransferDialog(
      accounts = listOf(account) + state.otherAccounts,
      initialFromId = account.id,
      fromLocked = true,
      onDismiss = { showTransfer = false },
      onTransfer = { _, to, amount, memo -> viewModel.transfer(to, amount, memo) }
    )
  }

  if (confirmDelete && account != null) {
    ConfirmDialog(
      title = "계좌를 삭제할까요?",
      message = "'${account.name}' 계좌를 삭제해요. 삭제한 계좌는 되돌릴 수 없어요.",
      confirmLabel = "삭제",
      onConfirm = { viewModel.delete() },
      onDismiss = { confirmDelete = false }
    )
  }

  confirmDeleteRelated?.let { message ->
    ConfirmDialog(
      title = "관련 거래도 함께 삭제할까요?",
      message = message,
      confirmLabel = "함께 삭제",
      onConfirm = { viewModel.delete(deleteRelated = true) },
      onDismiss = { confirmDeleteRelated = null }
    )
  }

  errorMessage?.let { message ->
    AlertDialog(
      onDismissRequest = { errorMessage = null },
      title = { Text("처리하지 못했어요", fontWeight = FontWeight.Bold) },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("확인", color = TossBlue) } }
    )
  }
}

@Composable
private fun AccountHeaderCard(account: AccountEntity, transferEnabled: Boolean, onTransfer: () -> Unit) {
  Surface(shape = RoundedCornerShape(24.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(account.colorHex), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
          listOf(account.bankName, account.accountType.displayName).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium,
          color = Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (account.isDebt) {
          Text(
            "부채",
            color = AccentRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.background(AccentRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }
      if (account.accountNumber.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(account.accountNumber, style = MaterialTheme.typography.bodySmall, color = Gray600, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Spacer(Modifier.height(12.dp))
      Text(if (account.isDebt) "갚을 금액" else "잔액", style = MaterialTheme.typography.bodySmall, color = Gray600)
      Text(
        Formatters.formatCurrency(account.balance),
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        color = if (account.isDebt) AccentRed else Gray900,
        maxLines = 1,
        modifier = Modifier.testTag("account_detail_balance")
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onTransfer,
        enabled = transferEnabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
      ) {
        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (transferEnabled) "이체하기" else "이체할 다른 계좌가 없어요", fontWeight = FontWeight.Bold)
      }
    }
  }
}
