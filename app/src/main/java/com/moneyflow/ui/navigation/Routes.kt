package com.moneyflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
  const val HOME = "home"
  const val LEDGER = "ledger"
  const val ANALYTICS = "analytics"
  const val BUDGET = "budget"
  const val TRANSACTION = "transaction?transactionId={transactionId}"
  const val ACCOUNT = "account/{accountId}"
  const val SETTINGS = "settings"
  const val SECURITY = "settings/security"
  const val AUTO_CAPTURE = "settings/auto-capture"
  const val BACKUP = "settings/backup"
  const val AI = "settings/ai"
  const val RELEASE_HISTORY = "settings/release-history"
  const val RULES = "settings/rules"
  const val FAMILY = "family"
  const val DRIVE = "settings/drive"
  const val CLEANUP = "settings/cleanup"
  const val YEARLY = "analytics/yearly"
  const val REPORT = "settings/report"
  const val FEATURE_CHECK = "settings/feature-check"
  const val CARD_IMPORT = "card-import"
  const val CARD_BROWSER = "card-import/site/{siteId}"
  const val CARD_PREVIEW = "card-import/preview"
  const val FILE_IMPORT = "card-import/file"
  const val ORDER_LINK = "card-import/orders"
  fun cardBrowser(siteId: String) = "card-import/site/$siteId"

  fun transaction(id: Long? = null) = if (id == null) "transaction" else "transaction?transactionId=$id"
  fun account(id: Long) = "account/$id"
}

enum class TopLevelTab(
  val route: String,
  val title: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector
) {
  HOME(Routes.HOME, "자산", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
  LEDGER(Routes.LEDGER, "가계부", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
  ANALYTICS(Routes.ANALYTICS, "소비분석", Icons.Filled.BarChart, Icons.Outlined.BarChart),
  BUDGET(Routes.BUDGET, "예산·목표", Icons.Filled.Savings, Icons.Outlined.Savings)
}
