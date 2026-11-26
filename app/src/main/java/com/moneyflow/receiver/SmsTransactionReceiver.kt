package com.moneyflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.CaptureChannel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records card/bank approval SMS. Uses goAsync() so the process stays alive until the
 * database write finishes. Message contents are never logged.
 */
@AndroidEntryPoint
class SmsTransactionReceiver : BroadcastReceiver() {

  /** Built on first use off the main thread (opening the encrypted database touches the Keystore). */
  @Inject lateinit var processor: dagger.Lazy<AutoCaptureProcessor>

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isNullOrEmpty()) return

    val body = messages.joinToString("") { it.messageBody.orEmpty() }
    val sender = messages.first().originatingAddress.orEmpty()
    val receivedAt = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

    val pending = goAsync()
    scope.launch {
      try {
        processor.get().process(CaptureChannel.SMS, sender, null, "", body, receivedAt)
      } catch (_: Exception) {
        // Ignore: a malformed message must never crash the receiver.
      } finally {
        pending.finish()
      }
    }
  }

  private companion object {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}
