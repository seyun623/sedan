package com.moneyflow.domain.cardimport

import com.moneyflow.ai.AiService
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a card statement screen with the AI provider chosen in Settings. Works for any card
 * company's layout, but sends the screen text (card transactions) to that AI service, so it is
 * used only after the user agrees each time.
 */
@Singleton
class AiStatementReader @Inject constructor(private val ai: AiService) {

  suspend fun read(screenText: String, today: LocalDate): StatementExtraction {
    val text = screenText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").take(MAX_CHARS)
    val prompt = buildString {
      appendLine("다음은 한국 카드사 홈페이지의 카드 이용내역/명세서/실적 화면에서 읽은 글자입니다.")
      appendLine("카드 결제 내역과 실적 금액만 정확히 뽑아 주세요. 화면에 없는 내용은 만들지 마세요.")
      appendLine("- 날짜는 YYYY-MM-DD. 연도가 없으면 오늘(${today}) 기준 가장 최근 과거 날짜로.")
      appendLine("- amount는 원 단위 양의 정수. 취소/환불 건은 cancel=true.")
      appendLine("- 누적·잔액·한도·포인트 금액은 결제 금액이 아닙니다.")
      appendLine("- 할부는 installmentMonths(개월 수), 일시불이면 0.")
      appendLine("- performance: '전월 실적', '이용 실적'처럼 실적으로 표시된 금액.")
      appendLine()
      appendLine("=== 화면 글자 ===")
      append(text)
    }
    val shape = """{"transactions":[{"date":"YYYY-MM-DD","time":"HH:mm 또는 빈 문자열","merchant":"가맹점","amount":0,"cancel":false,"installmentMonths":0}],"performance":[{"label":"설명","amount":0}]}"""
    val json = JSONObject(ai.completeJson(prompt, schema(), shape))
    return parseResult(json, ai.providerLabel())
  }

  companion object {
    private const val MAX_CHARS = 15_000

    internal fun parseResult(json: JSONObject, readBy: String): StatementExtraction {
      val items = json.optJSONArray("transactions").objects().mapNotNull { o ->
        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
        val amount = o.optLong("amount", 0L).let { kotlin.math.abs(it) }
        val merchant = o.optString("merchant").trim().take(40)
        if (amount <= 0 || merchant.isEmpty()) return@mapNotNull null
        StatementItem(
          date = date,
          time = o.optString("time").takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
          merchant = merchant,
          amount = amount,
          isCancel = o.optBoolean("cancel"),
          installmentMonths = o.optInt("installmentMonths", 0).takeIf { it > 1 }
        )
      }
      val perf = json.optJSONArray("performance").objects().mapNotNull { o ->
        val amount = o.optLong("amount", 0L)
        if (amount <= 0) null else PerformanceInfo(o.optString("label").ifBlank { "이용 실적" }.take(30), amount)
      }
      return StatementExtraction(items, perf, readBy)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
      if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun schema(): JSONObject {
      val tx = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
        .put("date", JSONObject().put("type", "STRING"))
        .put("time", JSONObject().put("type", "STRING"))
        .put("merchant", JSONObject().put("type", "STRING"))
        .put("amount", JSONObject().put("type", "INTEGER"))
        .put("cancel", JSONObject().put("type", "BOOLEAN"))
        .put("installmentMonths", JSONObject().put("type", "INTEGER")))
        .put("required", JSONArray(listOf("date", "merchant", "amount", "cancel")))
      val perf = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
        .put("label", JSONObject().put("type", "STRING"))
        .put("amount", JSONObject().put("type", "INTEGER")))
        .put("required", JSONArray(listOf("label", "amount")))
      return JSONObject().put("type", "OBJECT").put("properties", JSONObject()
        .put("transactions", JSONObject().put("type", "ARRAY").put("items", tx))
        .put("performance", JSONObject().put("type", "ARRAY").put("items", perf)))
        .put("required", JSONArray(listOf("transactions", "performance")))
    }
  }
}
