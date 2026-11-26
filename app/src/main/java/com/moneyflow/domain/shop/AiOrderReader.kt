package com.moneyflow.domain.shop

import com.moneyflow.ai.AiService
import com.moneyflow.domain.cardimport.AiStatementReader
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Reads a shopping site's order list with the AI chosen in Settings (after the user agrees). */
@Singleton
class AiOrderReader @Inject constructor(private val ai: AiService) {

  suspend fun read(screenText: String, today: LocalDate, shop: String): List<ShopOrder> {
    val text = AiStatementReader.redact(screenText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).take(15_000)
    val prompt = buildString {
      appendLine("다음은 한국 쇼핑몰($shop)의 주문내역 화면에서 읽은 글자입니다.")
      appendLine("주문마다 주문일, 실제로 결제한 금액(배송비 포함, 할인·쿠폰 뺀 금액), 상품 이름들을 뽑아 주세요. 화면에 없는 내용은 만들지 마세요.")
      appendLine("- 날짜는 YYYY-MM-DD. 연도가 없으면 오늘($today) 기준 가장 최근 과거 날짜로.")
      appendLine("- amount는 원 단위 양의 정수. 취소·반품된 주문은 빼 주세요.")
      appendLine("- items는 상품 이름을 짧게 (옵션·수량은 빼도 됨), 최대 5개.")
      appendLine()
      appendLine("=== 화면 글자 ===")
      append(text)
    }
    val shape = """{"orders":[{"date":"YYYY-MM-DD","amount":0,"items":["상품 이름"]}]}"""
    val json = JSONObject(ai.completeJson(prompt, schema(), shape))
    return parse(json.optJSONArray("orders"), shop, today)
  }

  companion object {
    fun parse(arr: JSONArray?, shop: String, today: LocalDate): List<ShopOrder> {
      if (arr == null) return emptyList()
      return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()?.takeIf { !it.isAfter(today.plusDays(1)) } ?: return@mapNotNull null
        val amount = o.optLong("amount").takeIf { it in 1..50_000_000 } ?: return@mapNotNull null
        val items = o.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.optString(it).trim().take(40) }.filter { it.isNotEmpty() } }.orEmpty()
        ShopOrder(date, amount, items.take(5), shop)
      }.take(200)
    }

    private fun schema() = JSONObject().put("type", "OBJECT").put(
      "properties", JSONObject().put(
        "orders", JSONObject().put("type", "ARRAY").put(
          "items", JSONObject().put("type", "OBJECT").put(
            "properties", JSONObject()
              .put("date", JSONObject().put("type", "STRING"))
              .put("amount", JSONObject().put("type", "INTEGER"))
              .put("items", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")))
          ).put("required", JSONArray().put("date").put("amount").put("items"))
        )
      )
    ).put("required", JSONArray().put("orders"))
  }
}
