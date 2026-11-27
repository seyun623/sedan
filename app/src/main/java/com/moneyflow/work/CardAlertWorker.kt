package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.repository.CardRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.card.CardStatus
import com.moneyflow.util.Formatters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** A reminder to send. [key] makes sure it is sent once per 실적 period. */
data class CardAlert(val key: String, val title: String, val text: String)

/** Decides which reminders are due (pure, testable). */
object CardAlerts {
  fun due(statuses: List<CardStatus>, accountNames: Map<Long, String>, alreadySent: Set<String>): List<CardAlert> =
    statuses.flatMap { s ->
      val name = accountNames[s.accountId] ?: "카드"
      buildList {
        if (s.needsAttention) add(CardAlert(
          "short|${s.accountId}|${s.period.key}",
          "$name 실적이 ${Formatters.formatCurrency(s.shortage)} 모자라요",
          "실적 기간이 ${s.daysLeft}일 남았어요. (${Formatters.formatCurrency(s.performance)} / ${Formatters.formatCurrency(s.target)})"
        ))
        s.benefits.filter { it.capReached }.forEach { b ->
          add(CardAlert(
            "cap|${b.benefit.id}|${s.period.key}",
            "$name '${b.benefit.title}' 혜택 한도를 다 채웠어요",
            "이번 기간 혜택 ${Formatters.formatCurrency(b.earned)}을 모두 받았어요. 같은 곳은 다른 카드를 써 보세요."
          ))
        }
      }
    }.filter { it.key !in alreadySent }
}

@HiltWorker
class CardAlertWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val cards: CardRepository,
  private val ledger: LedgerRepository,
  private val settings: SettingsRepository
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val s = settings.current()
    if (!s.cardAlertsEnabled || !canNotify(applicationContext)) return Result.success()
    val names = ledger.getAccounts().associate { it.id to it.name }
    val alerts = CardAlerts.due(cards.currentStatuses(), names, s.sentCardAlerts)
    if (alerts.isEmpty()) return Result.success()
    alerts.forEach { a ->
      Notifications.post(applicationContext, com.moneyflow.domain.alerts.Reminder(a.key, com.moneyflow.domain.alerts.ReminderChannel.CARD, a.title, a.text))
    }
    settings.markCardAlertsSent(alerts.map { it.key })
    return Result.success()
  }

  companion object {

    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "card-alerts", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CardAlertWorker>(12, TimeUnit.HOURS).build()
      )
    }

    fun canNotify(context: Context): Boolean = Notifications.canNotify(context)
  }
}
