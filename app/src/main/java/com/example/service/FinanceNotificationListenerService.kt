package com.example.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.util.PaymentNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Background Android NotificationListenerService that automatically intercepts
 * financial payment notifications from Korean banks, credit card apps, and mobile pay services.
 * Automatically parses amount, merchant, and category, and registers transactions into Room DB.
 */
class FinanceNotificationListenerService : NotificationListenerService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  companion object {
    const val TAG = "FinanceNotificationSvc"
    const val ACTION_TRANSACTION_AUTO_PARSED = "com.example.action.TRANSACTION_AUTO_PARSED"
    const val EXTRA_MERCHANT = "extra_merchant"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_CATEGORY = "extra_category"
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    if (sbn == null) return

    val packageName = sbn.packageName ?: ""
    val notification = sbn.notification ?: return
    val extras = notification.extras ?: return

    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    val contentText = if (bigText.isNotBlank()) bigText else text

    if (contentText.isBlank()) return

    // Quick filter: Check if notification content looks like a financial transaction
    val combined = "$title $contentText"
    val isFinancial = combined.contains("승인") ||
      combined.contains("결제") ||
      combined.contains("출금") ||
      combined.contains("입금") ||
      combined.contains("원") ||
      combined.contains("카드")

    if (!isFinancial) return

    Log.d(TAG, "Intercepted potential payment notification from $packageName: $title - $contentText")

    val parsed = PaymentNotificationParser.parse(title, contentText)
    if (!parsed.isSuccess || parsed.amount <= 0L) {
      Log.d(TAG, "Parser skipped: ${parsed.reason}")
      return
    }

    Log.i(TAG, "Successfully parsed payment: ${parsed.providerName} ${parsed.merchantOrSender} ${parsed.amount}원 (${parsed.category})")

    // Persist into Room Database asynchronously
    serviceScope.launch {
      try {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FinanceRepository(
          accountDao = database.accountDao(),
          transactionDao = database.transactionDao(),
          budgetDao = database.budgetDao(),
          savingGoalDao = database.savingGoalDao(),
          recurringTransactionDao = database.recurringTransactionDao()
        )

        // Find matching account or fallback to first active account
        val accounts = repository.allAccounts.firstOrNull() ?: emptyList()
        val targetAccount = accounts.find { acc ->
          acc.name.contains(parsed.providerName, ignoreCase = true) ||
            parsed.providerName.contains(acc.name, ignoreCase = true) ||
            parsed.providerName.contains(acc.bankName, ignoreCase = true)
        } ?: accounts.firstOrNull { !it.isDebt } ?: accounts.firstOrNull()

        val accountId = targetAccount?.id ?: 1L
        val accountName = targetAccount?.name ?: parsed.providerName

        val tx = TransactionEntity(
          type = parsed.type,
          amount = parsed.amount,
          category = parsed.category,
          accountId = accountId,
          accountName = accountName,
          memo = "${parsed.merchantOrSender} (${parsed.providerName} 자동등록)",
          timestamp = parsed.timestamp
        )

        repository.insertTransaction(tx)
        Log.i(TAG, "Auto-registered transaction ID into Room DB: $tx")

        // Broadcast notification to UI
        val broadcastIntent = Intent(ACTION_TRANSACTION_AUTO_PARSED).apply {
          setPackage(packageName)
          putExtra(EXTRA_MERCHANT, parsed.merchantOrSender)
          putExtra(EXTRA_AMOUNT, parsed.amount)
          putExtra(EXTRA_CATEGORY, parsed.category)
        }
        sendBroadcast(broadcastIntent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to auto-register notification transaction: ${e.message}", e)
      }
    }
  }
}
