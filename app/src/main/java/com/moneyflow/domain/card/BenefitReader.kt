package com.moneyflow.domain.card

import com.moneyflow.ai.AiService
import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Turns a card's benefit description (card company benefit screen or pasted text) into
 * [CardBenefitEntity] drafts with the AI provider chosen in Settings.
 */
@Singleton
class BenefitReader @Inject constructor(private val ai: AiService) {

  suspend fun read(text: String): List<CardBenefitEntity> {
    val clean = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").take(MAX_CHARS)
    val prompt = buildString {
      appendLine("다음은 한국 신용/체크카드의 혜택 안내 글입니다.")
      appendLine(RULES)
      appendLine()
      appendLine("=== 혜택 안내 글 ===")
      append(clean)
    }
    val json = JSONObject(ai.completeJson(prompt, schema(), """{"benefits":[$ITEM_SHAPE]}"""))
    return parse(json.optJSONArray("benefits"))
  }

  companion object {
    private const val MAX_CHARS = 15_000

    private val CATEGORIES = FinanceCategories.categoriesFor(TransactionType.EXPENSE)

    /** Shared with the card statement reader (benefit screens read in the card browser). */
    val RULES = """
      카드 할인·적립·캐시백 혜택을 benefits 배열로 정리해 주세요. 글에 없는 혜택은 만들지 마세요.
      - title: 짧은 이름 (예: "스타벅스 10% 할인", "편의점 건당 500원")
      - category: ${CATEGORIES.joinToString(", ")} 중 가장 알맞은 하나. 모든 가맹점이나 알 수 없으면 빈 문자열
      - merchants: 특정 가맹점(브랜드)에만 적용되면 쉼표로 구분한 가맹점 이름 (예: "스타벅스,투썸플레이스"), 아니면 빈 문자열
      - kind: 결제 금액의 %면 "PERCENT", 건당 정해진 금액이면 "FIXED"
      - value: PERCENT면 퍼센트 숫자(10% → 10, 0.5% → 0.5), FIXED면 원 단위 금액
      - monthlyCap: 월 할인·적립 한도(원), 없으면 0. 실적 구간별로 다르면 가장 낮은 구간 기준
      - minPerformance: 혜택에 필요한 전월 실적(원), 없으면 0. 구간이 여러 개면 가장 낮은 구간
      - minAmount: 최소 결제 금액(원) 조건, 없으면 0
      - 연회비, 포인트 전환, 서비스(라운지, 발레파킹 등)처럼 결제 할인·적립이 아닌 것은 빼 주세요.
    """.trimIndent()

    const val ITEM_SHAPE =
      """{"title":"이름","category":"카테고리 또는 빈 문자열","merchants":"가맹점,가맹점","kind":"PERCENT","value":10,"monthlyCap":0,"minPerformance":0,"minAmount":0}"""

    fun itemSchema(): JSONObject = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
      .put("title", JSONObject().put("type", "STRING"))
      .put("category", JSONObject().put("type", "STRING"))
      .put("merchants", JSONObject().put("type", "STRING"))
      .put("kind", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("PERCENT", "FIXED"))))
      .put("value", JSONObject().put("type", "NUMBER"))
      .put("monthlyCap", JSONObject().put("type", "INTEGER"))
      .put("minPerformance", JSONObject().put("type", "INTEGER"))
      .put("minAmount", JSONObject().put("type", "INTEGER")))
      .put("required", JSONArray(listOf("title", "kind", "value")))

    private fun schema(): JSONObject = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
      .put("benefits", JSONObject().put("type", "ARRAY").put("items", itemSchema())))
      .put("required", JSONArray(listOf("benefits")))

    /** Validates the AI answer; drops items that can't be used. accountId is set when saving. */
    fun parse(array: JSONArray?): List<CardBenefitEntity> {
      if (array == null) return emptyList()
      val seen = mutableSetOf<String>()
      return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { o ->
        val kind = if (o.optString("kind").equals("FIXED", ignoreCase = true)) BenefitKind.FIXED else BenefitKind.PERCENT
        val raw = o.optDouble("value", 0.0).takeIf { !it.isNaN() } ?: 0.0
        val value = when (kind) {
          BenefitKind.PERCENT -> raw.takeIf { it > 0 && it <= 100 }?.let { (it * 100).roundToLong() }
          BenefitKind.FIXED -> raw.roundToLong().takeIf { it > 0 }
        } ?: return@mapNotNull null
        val category = o.optString("category").trim().takeIf { it in CATEGORIES }
        val merchants = o.optString("merchants").split(',', '/', '·').map { it.trim() }.filter { it.isNotEmpty() }
          .distinct().take(10).joinToString(",")
        val title = o.optString("title").trim().ifEmpty {
          val target = merchants.substringBefore(',').ifEmpty { category ?: "전체" }
          if (kind == BenefitKind.PERCENT) "$target ${if (raw % 1.0 == 0.0) raw.toLong() else raw}% 혜택" else "$target 건당 ${value}원"
        }.take(30)
        if (!seen.add(title.replace(" ", ""))) return@mapNotNull null
        CardBenefitEntity(
          accountId = 0,
          title = title,
          category = category,
          merchantKeywords = merchants,
          kind = kind,
          value = value,
          monthlyCap = o.optLong("monthlyCap", 0L).coerceAtLeast(0),
          minPerformance = o.optLong("minPerformance", 0L).coerceAtLeast(0),
          minAmount = o.optLong("minAmount", 0L).coerceAtLeast(0)
        )
      }
    }
  }
}
