package com.moneyflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.repository.FinanceRepository
import com.moneyflow.util.PaymentNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for SMS payment notifications (Telephony.SMS_RECEIVED).
 * Captures bank/card approval SMS and registers them into the user's Room database.
 */
class SmsTransactionReceiver : BroadcastReceiver() {

  private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  companion object {
    const val TAG = "SmsTransactionReceiver"
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) return

    if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
      val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
      if (messages.isNullOrEmpty()) return

      val fullBody = StringBuilder()
      var originatingAddress = ""

      for (sms in messages) {
        fullBody.append(sms.messageBody)
        if (originatingAddress.isBlank()) {
          originatingAddress = sms.originatingAddress ?: ""
        }
      }

      val bodyText = fullBody.toString()
      Log.d(TAG, "Received SMS from $originatingAddress: $bodyText")

      val parsed = PaymentNotificationParser.parse(originatingAddress, bodyText)
      if (!parsed.isSuccess || parsed.amount <= 0L) {
        Log.d(TAG, "SMS parsed skipped: not a payment message (${parsed.reason})")
        return
      }

      Log.i(TAG, "SMS Payment recognized: ${parsed.providerName} ${parsed.merchantOrSender} ${parsed.amount}원")

      receiverScope.launch {
        try {
          val database = AppDatabase.getDatabase(context.applicationContext)
          val repository = FinanceRepository(
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao(),
            budgetDao = database.budgetDao(),
            savingGoalDao = database.savingGoalDao(),
            recurringTransactionDao = database.recurringTransactionDao()
          )

          val accounts = repository.allAccounts.firstOrNull() ?: emptyList()
          val targetAccount = accounts.find { acc ->
            acc.name.contains(parsed.providerName, ignoreCase = true) ||
              parsed.providerName.contains(acc.name, ignoreCase = true)
          } ?: accounts.firstOrNull { !it.isDebt } ?: accounts.firstOrNull()

          val accountId = targetAccount?.id ?: 1L
          val accountName = targetAccount?.name ?: parsed.providerName

          val tx = TransactionEntity(
            type = parsed.type,
            amount = parsed.amount,
            category = parsed.category,
            accountId = accountId,
            accountName = accountName,
            memo = "${parsed.merchantOrSender} (${parsed.providerName} SMS 자동등록)",
            timestamp = parsed.timestamp
          )

          repository.insertTransaction(tx)
          Log.i(TAG, "Successfully inserted SMS transaction into Room DB: $tx")
        } catch (e: Exception) {
          Log.e(TAG, "Error inserting SMS transaction: ${e.message}", e)
        }
      }
    }
  }
}
