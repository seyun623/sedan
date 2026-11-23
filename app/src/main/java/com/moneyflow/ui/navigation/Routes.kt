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
