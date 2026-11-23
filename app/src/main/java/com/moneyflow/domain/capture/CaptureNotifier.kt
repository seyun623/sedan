package com.moneyflow.domain.capture

import com.moneyflow.data.model.TransactionType

/** What the add-transaction form should start with (from a notification or the widget). */
data class FormPrefill(
  val type: TransactionType = TransactionType.EXPENSE,
  val amount: Long? = null,
  val memo: String = "",
  val category: String? = null,
  val accountId: Long? = null,
  val timestamp: Long? = null
)

/** A foreign-currency card payment whose KRW amount the user should enter. */
data class ForeignPaymentPrompt(
  val key: String,
  val merchant: String,
  val foreignAmount: String,
  val provider: String?,
  val prefill: FormPrefill
)

/** Shows prompts from auto-capture (implemented with Android notifications). */
interface CaptureNotifier {
  fun foreignPayment(prompt: ForeignPaymentPrompt)
}
