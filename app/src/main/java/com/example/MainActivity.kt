package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AccountEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.AddAccountDialog
import com.example.ui.components.AddGoalDialog
import com.example.ui.components.AddRecurringDialog
import com.example.ui.components.AddTransactionBottomSheet
import com.example.ui.components.SecuritySettingsDialog
import com.example.ui.components.SetBudgetDialog
import com.example.ui.components.TransferDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.TransactionFormScreen
import com.example.ui.theme.Gray600
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TossBlue
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CsvExportHelper
import com.example.util.configureAppCheck
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
  private val viewModel: FinanceViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MoneyFlowApp(viewModel = viewModel)
      }
    }
  }
}

enum class NavigationTab(
  val title: String,
  val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
  val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
  HOME("자산", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
  LEDGER("가계부", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
  ANALYTICS("소비분석", Icons.Filled.BarChart, Icons.Outlined.BarChart),
  BUDGET("예산·목표", Icons.Filled.Savings, Icons.Outlined.Savings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyFlowApp(viewModel: FinanceViewModel) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val transferSuccessMessage by viewModel.transferSuccessMessage.collectAsStateWithLifecycle()
  val categorySuggestionState by viewModel.categorySuggestionState.collectAsStateWithLifecycle()
  var selectedTab by remember { mutableIntStateOf(0) }

  // Full-screen Transaction Form Screen State (Create & Edit CRUD)
  var isTransactionFormOpen by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

  // Modal Dialog States
  var showTransferDialog by remember { mutableStateOf(false) }
  var transferSelectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var showAddAccountDialog by remember { mutableStateOf(false) }
  var showAddGoalDialog by remember { mutableStateOf(false) }
  var showSetBudgetDialog by remember { mutableStateOf(false) }
  var showAddRecurringDialog by remember { mutableStateOf(false) }
  var showSecurityDialog by remember { mutableStateOf(false) }
  var showAddTransactionBottomSheet by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(transferSuccessMessage) {
    transferSuccessMessage?.let { msg ->
      scope.launch {
        val isDeleteMsg = msg.contains("삭제되었습니다")
        val result = snackbarHostState.showSnackbar(
          message = msg,
          actionLabel = if (isDeleteMsg) "실행 취소" else null,
          duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && isDeleteMsg) {
          viewModel.undoLastDelete()
        }
        viewModel.clearTransferMessage()
      }
    }
  }

  // Biometric Lock Guard: Protect all financial data until authenticated
  if (uiState.isAppLocked) {
    BiometricLockScreen(
      onUnlockSuccess = { viewModel.unlockApp() },
      onVerifyPin = { viewModel.verifyPin(it) }
    )
    return
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (!isTransactionFormOpen) {
        NavigationBar(
          containerColor = Color.White,
          tonalElevation = 8.dp,
          modifier = Modifier.testTag("bottom_nav_bar")
        ) {
          NavigationTab.values().forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            NavigationBarItem(
              selected = isSelected,
              onClick = { selectedTab = index },
              icon = {
                Icon(
                  imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                  contentDescription = tab.title
                )
              },
              label = {
                Text(
                  text = tab.title,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TossBlue,
                selectedTextColor = TossBlue,
                indicatorColor = TossBlue.copy(alpha = 0.12f),
                unselectedIconColor = Gray600,
                unselectedTextColor = Gray600
              ),
              modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
          }
        }
      }
    },
    floatingActionButton = {
      if (!isTransactionFormOpen && (selectedTab == 0 || selectedTab == 1)) {
        FloatingActionButton(
          onClick = {
            showAddTransactionBottomSheet = true
          },
          containerColor = TossBlue,
          contentColor = Color.White,
          shape = CircleShape,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
          modifier = Modifier.testTag("fab_add_transaction")
        ) {
          Icon(Icons.Default.Add, contentDescription = "거래 내역 추가")
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (isTransactionFormOpen) {
        TransactionFormScreen(
          accounts = uiState.accounts,
          editingTransaction = editingTransaction,
          categorySuggestionState = categorySuggestionState,
          onRequestCategorySuggestion = { keyword, type, debounceMs ->
            viewModel.suggestCategoryFromKeyword(
              keyword = keyword,
              transactionType = type,
              debounceMs = debounceMs
            )
          },
          onResetCategorySuggestion = {
            viewModel.resetCategorySuggestion()
          },
          onBack = {
            viewModel.resetCategorySuggestion()
            isTransactionFormOpen = false
            editingTransaction = null
          },
          onSaveNew = { type, amount, category, account, toAccount, timestamp, memo, method, isExcluded, closeAfter ->
            viewModel.addTransaction(
              type = type,
              amount = amount,
              category = category,
              account = account,
              toAccount = toAccount,
              timestamp = timestamp,
              memo = memo,
              paymentMethod = method,
              isExcludedFromBudget = isExcluded
            )
            viewModel.resetCategorySuggestion()
            if (closeAfter) {
              isTransactionFormOpen = false
              editingTransaction = null
            }
          },
          onUpdateExisting = { id, type, amount, category, account, toAccount, timestamp, memo, method, isExcluded ->
            viewModel.updateTransaction(
              id = id,
              type = type,
              amount = amount,
              category = category,
              account = account,
              toAccount = toAccount,
              timestamp = timestamp,
              memo = memo,
              paymentMethod = method,
              isExcludedFromBudget = isExcluded
            )
            viewModel.resetCategorySuggestion()
          },
          onDeleteExisting = { tx ->
            viewModel.deleteTransaction(tx)
            viewModel.resetCategorySuggestion()
          }
        )
      } else {
        when (selectedTab) {
          0 -> HomeScreen(
            uiState = uiState,
            onOpenTransfer = { account ->
              transferSelectedAccount = account
              showTransferDialog = true
            },
            onOpenAddTransaction = {
              showAddTransactionBottomSheet = true
            },
            onOpenAddAccount = { showAddAccountDialog = true },
            onNavigateToAnalytics = { selectedTab = 2 },
            onOpenSetBudget = { showSetBudgetDialog = true },
            onOpenSecurity = { showSecurityDialog = true },
            onNavigateToLedger = { selectedTab = 1 },
            onEditTransaction = { tx ->
              viewModel.resetCategorySuggestion()
              editingTransaction = tx
              isTransactionFormOpen = true
            }
          )
          1 -> LedgerScreen(
            uiState = uiState,
            onPrevMonth = { viewModel.prevMonth() },
            onNextMonth = { viewModel.nextMonth() },
            onFilterTypeSelected = { viewModel.setFilterType(it) },
            onCategoryFilterSelected = { viewModel.setSelectedCategory(it) },
            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
            onDateRangePresetSelect = { viewModel.setDateRangePreset(it) },
            onCustomDateRangeSet = { start, end -> viewModel.setCustomDateRange(start, end) },
            onResetAllFilters = { viewModel.resetAllFilters() },
            onToggleCalendarView = { viewModel.toggleCalendarView() },
            onSelectCalendarDate = { viewModel.selectCalendarDate(it) },
            onOpenAddTransaction = {
              showAddTransactionBottomSheet = true
            },
            onEditTransaction = { tx ->
              viewModel.resetCategorySuggestion()
              editingTransaction = tx
              isTransactionFormOpen = true
            },
            onDeleteTransaction = { viewModel.deleteTransaction(it) },
            onExportCsv = {
              CsvExportHelper.exportTransactionsToCsv(
                context = context,
                transactions = uiState.filteredTransactions,
                titleSuffix = uiState.currentMonthDisplay
              )
            }
          )
          2 -> AnalyticsScreen(
            uiState = uiState,
            onPrevMonth = { viewModel.prevMonth() },
            onNextMonth = { viewModel.nextMonth() }
          )
          3 -> BudgetScreen(
            uiState = uiState,
            onOpenSetBudget = { showSetBudgetDialog = true },
            onOpenAddRecurring = { showAddRecurringDialog = true },
            onToggleRecurringActive = { id, isActive -> viewModel.toggleRecurringActive(id, isActive) },
            onExecuteRecurringNow = { item -> viewModel.executeRecurringNow(item) },
            onDeleteRecurring = { item -> viewModel.deleteRecurring(item) },
            onOpenAddGoal = { showAddGoalDialog = true },
            onDepositToGoal = { goal ->
              val firstBank = uiState.accounts.firstOrNull { !it.isDebt }
              if (firstBank != null) {
                viewModel.depositToGoal(goal, 100000L, firstBank.id)
              }
            },
            onExportCsv = {
              CsvExportHelper.exportTransactionsToCsv(
                context = context,
                transactions = uiState.allMonthTransactions,
                titleSuffix = uiState.currentMonthDisplay
              )
            }
          )
        }
      }
    }
  }

  if (showTransferDialog) {
    TransferDialog(
      accounts = uiState.accounts,
      initialFromAccount = transferSelectedAccount,
      onDismiss = { showTransferDialog = false },
      onTransfer = { from, to, amount, memo ->
        viewModel.transfer(from, to, amount, memo)
      }
    )
  }

  if (showAddAccountDialog) {
    AddAccountDialog(
      onDismiss = { showAddAccountDialog = false },
      onConfirm = { name, bank, type, accNum, bal, color, isDebt ->
        viewModel.addAccount(name, bank, type, accNum, bal, color, isDebt)
      }
    )
  }

  if (showAddGoalDialog) {
    AddGoalDialog(
      onDismiss = { showAddGoalDialog = false },
      onConfirm = { title, targetAmt, currAmt, targetDate, emoji ->
        viewModel.addSavingGoal(title, targetAmt, currAmt, targetDate, emoji)
      }
    )
  }

  if (showSetBudgetDialog) {
    SetBudgetDialog(
      currentTotalBudget = uiState.currentBudget,
      currentCategoryBudgets = uiState.categoryBudgets,
      monthDisplay = uiState.currentMonthDisplay,
      onDismiss = { showSetBudgetDialog = false },
      onConfirm = { total, limits ->
        viewModel.saveCategoryBudgets(total, limits)
        showSetBudgetDialog = false
      }
    )
  }

  if (showAddRecurringDialog) {
    AddRecurringDialog(
      accounts = uiState.accounts,
      onDismiss = { showAddRecurringDialog = false },
      onConfirm = { title, type, amt, cat, day, accId, accName, memo ->
        viewModel.addRecurringTransaction(
          title = title,
          type = type,
          amount = amt,
          category = cat,
          dayOfMonth = day,
          accountId = accId,
          accountName = accName,
          memo = memo
        )
        showAddRecurringDialog = false
      }
    )
  }

  if (showSecurityDialog) {
    SecuritySettingsDialog(
      isBiometricEnabled = uiState.isBiometricEnabled,
      onToggleBiometric = { viewModel.toggleBiometricEnabled(it) },
      onLockNow = { viewModel.lockApp() },
      onDismiss = { showSecurityDialog = false }
    )
  }

  if (showAddTransactionBottomSheet) {
    AddTransactionBottomSheet(
      accounts = uiState.accounts,
      onDismiss = { showAddTransactionBottomSheet = false },
      onSave = { type, amount, category, account, timestamp, memo, method, isExcluded ->
        viewModel.addTransaction(
          type = type,
          amount = amount,
          category = category,
          account = account,
          toAccount = null,
          timestamp = timestamp,
          memo = memo,
          paymentMethod = method,
          isExcludedFromBudget = isExcluded
        )
      }
    )
  }
}
