package com.moneyflow.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneyflow.ui.account.ARG_ACCOUNT_ID
import com.moneyflow.ui.account.AccountDetailScreen
import com.moneyflow.ui.analytics.AnalyticsScreen
import com.moneyflow.ui.budget.BudgetScreen
import com.moneyflow.ui.cardimport.ARG_SITE_ID
import com.moneyflow.ui.cardimport.CardBrowserScreen
import com.moneyflow.ui.cardimport.CardImportPreviewScreen
import com.moneyflow.ui.cardimport.CardImportScreen
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.home.HomeScreen
import com.moneyflow.ui.ledger.LedgerScreen
import com.moneyflow.ui.settings.AiSettingsScreen
import com.moneyflow.ui.settings.AutoCaptureSettingsScreen
import com.moneyflow.ui.settings.BackupScreen
import com.moneyflow.ui.settings.SecuritySettingsScreen
import com.moneyflow.ui.settings.SettingsScreen
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.transaction.ARG_TRANSACTION_ID
import com.moneyflow.ui.transaction.TransactionFormScreen

@Composable
fun MoneyFlowNavHost(
  messages: UserMessageBus,
  navController: NavHostController = rememberNavController(),
  prefill: com.moneyflow.ui.common.PrefillStore? = null
) {
  val snackbarHostState = remember { SnackbarHostState() }
  // A notification / widget asked for the add form (e.g. "해외 결제 원화 금액 입력").
  if (prefill != null) LaunchedEffect(prefill) {
    prefill.pending.collect { p ->
      if (p != null) {
        prefill.opened()
        navController.navigate(Routes.transaction()) { launchSingleTop = false }
      }
    }
  }
  LaunchedEffect(messages) {
    messages.messages.collect { msg ->
      snackbarHostState.currentSnackbarData?.dismiss()
      val result = snackbarHostState.showSnackbar(
        message = msg.text,
        actionLabel = msg.actionLabel,
        duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
      )
      if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
    }
  }

  val backStack by navController.currentBackStackEntryAsState()
  val currentDestination = backStack?.destination
  val currentTab = TopLevelTab.entries.firstOrNull { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }

  fun openTab(tab: TopLevelTab) = navController.navigate(tab.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
  val openTransaction: (Long?) -> Unit = { id -> navController.navigate(Routes.transaction(id)) }
  val openAccount: (Long) -> Unit = { id -> navController.navigate(Routes.account(id)) }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (currentTab != null) {
        NavigationBar(containerColor = CardSurface, modifier = Modifier.testTag("bottom_nav_bar")) {
          TopLevelTab.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
              selected = selected,
              onClick = { openTab(tab) },
              icon = { Icon(if (selected) tab.activeIcon else tab.inactiveIcon, contentDescription = null) },
              label = { Text(tab.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) },
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
      if (currentTab == TopLevelTab.HOME || currentTab == TopLevelTab.LEDGER) {
        FloatingActionButton(
          onClick = { openTransaction(null) },
          containerColor = TossBlue,
          contentColor = OnBrand,
          shape = CircleShape,
          modifier = Modifier.testTag("fab_add_transaction")
        ) { Icon(Icons.Default.Add, contentDescription = "거래 추가") }
      }
    }
  ) { padding ->
    NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
      composable(Routes.HOME) {
        HomeScreen(
          onAddTransaction = { openTransaction(null) },
          onOpenTransaction = { openTransaction(it) },
          onOpenAccount = openAccount,
          onOpenSettings = { navController.navigate(Routes.SETTINGS) },
          onOpenSecurity = { navController.navigate(Routes.SECURITY) },
          onOpenBackup = { navController.navigate(Routes.BACKUP) },
          onNavigateToLedger = { openTab(TopLevelTab.LEDGER) },
          onNavigateToAnalytics = { openTab(TopLevelTab.ANALYTICS) },
          onNavigateToBudget = { openTab(TopLevelTab.BUDGET) }
        )
      }
      composable(Routes.LEDGER) {
        LedgerScreen(onAddTransaction = { openTransaction(null) }, onOpenTransaction = { openTransaction(it) })
      }
      composable(Routes.ANALYTICS) { AnalyticsScreen(onOpenTransaction = { openTransaction(it) }) }
      composable(Routes.BUDGET) { BudgetScreen(onOpenTransaction = { openTransaction(it) }) }
      composable(
        Routes.TRANSACTION,
        arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType; defaultValue = 0L })
      ) { TransactionFormScreen(onClose = { navController.popBackStack() }) }
      composable(Routes.ACCOUNT, arguments = listOf(navArgument(ARG_ACCOUNT_ID) { type = NavType.LongType })) {
        AccountDetailScreen(
          onBack = { navController.popBackStack() },
          onOpenTransaction = { openTransaction(it) },
          onOpenCardImport = { navController.navigate(Routes.CARD_IMPORT) }
        )
      }
      composable(Routes.SETTINGS) {
        SettingsScreen(
          onBack = { navController.popBackStack() },
          onOpenSecurity = { navController.navigate(Routes.SECURITY) },
          onOpenAutoCapture = { navController.navigate(Routes.AUTO_CAPTURE) },
          onOpenBackup = { navController.navigate(Routes.BACKUP) },
          onOpenAi = { navController.navigate(Routes.AI) },
          onOpenCardImport = { navController.navigate(Routes.CARD_IMPORT) },
          onOpenReleaseHistory = { navController.navigate(Routes.RELEASE_HISTORY) },
          onOpenRules = { navController.navigate(Routes.RULES) },
          onOpenFamily = { navController.navigate(Routes.FAMILY) },
          onOpenDrive = { navController.navigate(Routes.DRIVE) }
        )
      }
      composable(Routes.DRIVE) { com.moneyflow.ui.settings.DriveSettingsScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.FAMILY) { com.moneyflow.ui.family.FamilyScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.RULES) { com.moneyflow.ui.settings.RulesScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.SECURITY) { SecuritySettingsScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.AUTO_CAPTURE) { AutoCaptureSettingsScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.CARD_IMPORT) {
        CardImportScreen(
          onBack = { navController.popBackStack() },
          onOpenSite = { navController.navigate(Routes.cardBrowser(it.id)) },
          onOpenFile = { navController.navigate(Routes.FILE_IMPORT) }
        )
      }
      composable(Routes.FILE_IMPORT) {
        com.moneyflow.ui.cardimport.FileImportScreen(
          onBack = { navController.popBackStack() },
          onOpenPreview = { navController.navigate(Routes.CARD_PREVIEW) }
        )
      }
      composable(Routes.CARD_BROWSER, arguments = listOf(navArgument(ARG_SITE_ID) { type = NavType.StringType })) {
        CardBrowserScreen(onClose = { navController.popBackStack() }, onOpenPreview = { navController.navigate(Routes.CARD_PREVIEW) })
      }
      composable(Routes.CARD_PREVIEW) {
        // After importing, go back to the card site to read another month if needed.
        CardImportPreviewScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
      }
      composable(Routes.RELEASE_HISTORY) {
        com.moneyflow.ui.whatsnew.ReleaseHistoryScreen(onBack = { navController.popBackStack() }, currentVersion = com.moneyflow.BuildConfig.VERSION_NAME)
      }
      composable(Routes.AI) { AiSettingsScreen(onBack = { navController.popBackStack() }) }
      composable(Routes.BACKUP) {
        BackupScreen(
          onBack = { navController.popBackStack() },
          onRestored = { navController.popBackStack(Routes.HOME, inclusive = false) }
        )
      }
    }
  }
}
