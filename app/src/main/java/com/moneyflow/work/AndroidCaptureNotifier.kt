package com.moneyflow.work

import android.content.Context
import android.content.Intent
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.alerts.Reminder
import com.moneyflow.domain.alerts.ReminderChannel
import com.moneyflow.domain.capture.CaptureNotifier
import com.moneyflow.domain.capture.ForeignPaymentPrompt
import com.moneyflow.domain.capture.FormPrefill

/** "해외 결제 USD 12.99 — 원화 금액을 입력해 주세요": tapping opens a prefilled add form. */
class AndroidCaptureNotifier(private val context: Context) : CaptureNotifier {
  override fun foreignPayment(prompt: ForeignPaymentPrompt) {
    Notifications.post(
      context,
      Reminder(
        prompt.key, ReminderChannel.CAPTURE,
        "해외 결제 ${prompt.foreignAmount} · 원화 금액을 입력해 주세요",
        "${prompt.provider?.let { "$it · " } ?: ""}${prompt.merchant}\n카드사에서 원화 금액이 확정되면 눌러서 기록하세요."
      )
    ) { PrefillIntents.put(context, this, prompt.prefill) }
  }
}

/** Puts a [FormPrefill] into / reads it from an Intent (notification → MainActivity). */
object PrefillIntents {
  private const val OPEN_FORM = "com.moneyflow.extra.OPEN_FORM"
  private const val TYPE = "com.moneyflow.extra.TYPE"
  private const val AMOUNT = "com.moneyflow.extra.AMOUNT"
  private const val MEMO = "com.moneyflow.extra.MEMO"
  private const val CATEGORY = "com.moneyflow.extra.CATEGORY"
  private const val ACCOUNT = "com.moneyflow.extra.ACCOUNT"
  private const val TIME = "com.moneyflow.extra.TIME"
  private const val TOKEN = "com.moneyflow.extra.TOKEN"

  /**
   * A random value only this app knows: MainActivity is exported (launcher), so another app could
   * send it a made-up prefill; only intents made by our own notifications / widget carry it.
   */
  @Synchronized
  internal fun token(context: Context): String {
    val prefs = context.getSharedPreferences("prefill_intents", Context.MODE_PRIVATE)
    prefs.getString("token", null)?.let { return it }
    val fresh = java.util.UUID.randomUUID().toString()
    prefs.edit().putString("token", fresh).apply()
    return fresh
  }

  fun put(context: Context, intent: Intent, p: FormPrefill) {
    intent.putExtra(OPEN_FORM, true).putExtra(TOKEN, token(context)).putExtra(TYPE, p.type.name).putExtra(MEMO, p.memo)
    p.amount?.let { intent.putExtra(AMOUNT, it) }
    p.category?.let { intent.putExtra(CATEGORY, it) }
    p.accountId?.let { intent.putExtra(ACCOUNT, it) }
    p.timestamp?.let { intent.putExtra(TIME, it) }
  }

  /** The prefill in [intent] (removed from it so it's handled once), or null. */
  fun take(context: Context, intent: Intent?): FormPrefill? {
    if (intent?.getBooleanExtra(OPEN_FORM, false) != true) return null
    if (intent.getStringExtra(TOKEN) != token(context)) { intent.removeExtra(OPEN_FORM); return null }
    val p = FormPrefill(
      type = runCatching { TransactionType.valueOf(intent.getStringExtra(TYPE) ?: "") }.getOrDefault(TransactionType.EXPENSE),
      amount = intent.getLongExtra(AMOUNT, 0L).takeIf { it > 0 },
      memo = intent.getStringExtra(MEMO).orEmpty().take(100),
      category = intent.getStringExtra(CATEGORY),
      accountId = intent.getLongExtra(ACCOUNT, 0L).takeIf { it > 0 },
      timestamp = intent.getLongExtra(TIME, 0L).takeIf { it > 0 }
    )
    intent.removeExtra(OPEN_FORM)
    return p
  }
}
