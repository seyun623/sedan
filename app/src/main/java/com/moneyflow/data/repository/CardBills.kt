package com.moneyflow.data.repository

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.domain.card.BillInfo
import com.moneyflow.domain.card.CardBill

/** Keeps a card's next bill; the bill's date also fills the card's 결제일 when it was not set. */
object CardBills {
  suspend fun save(db: AppDatabase, settings: SettingsRepository, accountId: Long, info: BillInfo, source: String, now: Long) {
    settings.setCardBill(accountId, CardBill(info.amount, info.dueDate?.toEpochDay(), source, now))
    val due = info.dueDate ?: return
    val account = db.accountDao().getById(accountId) ?: return
    if (account.paymentDay == 0) db.accountDao().update(account.copy(paymentDay = due.dayOfMonth))
  }
}
