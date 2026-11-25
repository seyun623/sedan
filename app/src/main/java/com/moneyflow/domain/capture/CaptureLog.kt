package com.moneyflow.domain.capture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One message the auto-capture looked at, and what it did with it. */
data class CaptureLogEntry(
  val time: Long,
  /** "알림" / "문자" / "시험" */
  val channel: String,
  /** App name (or "문자") */
  val source: String,
  /** Sender / chat title with digits partly hidden. */
  val sender: String,
  val recorded: Boolean,
  /** "기록됨 · 신한카드 Deep 12,500원" or why it was skipped. */
  val result: String,
  /** First characters of the message with digits hidden; only for messages that look financial. */
  val preview: String
)

/**
 * The last [MAX] messages the auto-capture looked at, kept in a small private file so the user
 * (and we) can see why a payment was or wasn't recorded. Message texts are not stored: only a
 * short preview with digits hidden, and only for messages that mention a card company or bank.
 */
@Singleton
class CaptureLog @Inject constructor(@ApplicationContext private val context: Context) {
  private val file get() = File(context.filesDir, "capture-log.json")
  private val _entries = MutableStateFlow(load())
  val entries: StateFlow<List<CaptureLogEntry>> = _entries.asStateFlow()

  @Synchronized
  fun add(entry: CaptureLogEntry) {
    val list = (listOf(entry) + _entries.value).take(MAX)
    _entries.value = list
    runCatching { file.writeText(encode(list)) }
  }

  @Synchronized
  fun clear() {
    _entries.value = emptyList()
    file.delete()
  }

  private fun load(): List<CaptureLogEntry> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

  companion object {
    const val MAX = 30

    /** "15447200" → "1544-****", "신한카드" stays. */
    fun maskSender(s: String): String {
      val digits = s.filter(Char::isDigit)
      return if (digits.length >= 7 && digits.length >= s.count { !it.isWhitespace() } - 2) {
        digits.take(4) + "-" + "*".repeat((digits.length - 4).coerceAtMost(8))
      } else s.take(20)
    }

    /** First 40 characters with every digit hidden. */
    fun maskPreview(text: String): String =
      text.replace(Regex("\\s+"), " ").trim().take(40).map { if (it.isDigit()) '•' else it }.joinToString("")

    fun describe(outcome: CaptureOutcome): Pair<Boolean, String> = when (outcome) {
      is CaptureOutcome.Recorded -> true to "기록됨 · ${outcome.account.name} ${com.moneyflow.util.Formatters.formatCurrency(outcome.payment.amount)}" +
        if (outcome.newAccount) " (새 계좌 '${outcome.account.name}'를 만들었어요 · 잔액은 자산 탭에서 맞춰 주세요)" else ""
      is CaptureOutcome.CancelledOriginal -> true to "취소 반영 · ${outcome.removed.memo} 지움"
      is CaptureOutcome.NeedsKrwAmount -> true to "해외 결제 · 원화 금액 입력 알림 보냄"
      is CaptureOutcome.WouldRecord -> true to "시험 성공 · ${outcome.account.name}${if (outcome.newAccount) "(새로 만들 계좌)" else ""}에 ${com.moneyflow.util.Formatters.formatCurrency(outcome.payment.amount)} ${outcome.payment.type.title}으로 기록돼요"
      is CaptureOutcome.Skipped -> false to "건너뜀 · ${outcome.reason}"
    }

    internal fun encode(list: List<CaptureLogEntry>): String = JSONArray(list.map {
      JSONObject().put("t", it.time).put("c", it.channel).put("s", it.source).put("f", it.sender)
        .put("r", it.recorded).put("m", it.result).put("p", it.preview)
    }).toString()

    internal fun decode(json: String): List<CaptureLogEntry> {
      val a = JSONArray(json)
      return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        CaptureLogEntry(o.getLong("t"), o.optString("c"), o.optString("s"), o.optString("f"), o.optBoolean("r"), o.optString("m"), o.optString("p"))
      }
    }
  }
}
