package com.moneyflow.ai

import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.capture.PaymentMessageParser
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AiCategoryRecommendation(
  val keyword: String,
  val recommendedCategory: String,
  val alternativeCategory: String?,
  val suggestedPaymentMethod: String,
  val confidence: Int,
  val reason: String
)

sealed interface CategorySuggestionUiState {
  data object Idle : CategorySuggestionUiState
  data class Loading(val keyword: String) : CategorySuggestionUiState
  data class Success(val recommendation: AiCategoryRecommendation) : CategorySuggestionUiState
  data class Error(val message: String) : CategorySuggestionUiState
}

class AiNotConfiguredException : Exception("설정 → AI 설정에서 API 키를 등록하고 켜 주세요.")

/**
 * Category suggestion for the memo typed in the transaction form.
 *
 * To save the (often limited free) AI quota it answers locally first: the category the user last
 * used for the same memo, then the built-in merchant keyword rules. Only unknown keywords go to
 * the AI provider selected in Settings, and AI answers are cached for the session.
 */
@Singleton
class CategorySuggester @Inject constructor(
  private val ai: AiService,
  private val db: AppDatabase,
  clock: AppClock
) {
  private val rules = PaymentMessageParser(clock.zone)
  private val cache = object : LinkedHashMap<String, AiCategoryRecommendation>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AiCategoryRecommendation>?) = size > 200
  }

  /** Throws AiNotConfiguredException / AiRequestException; returns null when the response is empty. */
  suspend fun suggest(trimmed: String, transactionType: TransactionType): AiCategoryRecommendation? {
    val allowedCategories = if (transactionType == TransactionType.INCOME) {
      FinanceCategories.incomeCategories.map { it.first }
    } else {
      FinanceCategories.expenseCategories.map { it.first }
    }
    localSuggestion(trimmed, transactionType, allowedCategories)?.let { return it }
    val cacheKey = "${transactionType.name}|${trimmed.lowercase()}"
    synchronized(cache) { cache[cacheKey] }?.let { return it }
    if (!ai.isReady()) throw AiNotConfiguredException()

    val prompt = buildString {
      appendLine("당신은 대한민국 가계부 애플리케이션의 지출/수입 카테고리 자동 추천 AI입니다.")
      appendLine("사용자가 입력한 결제 가맹점명 또는 거래 내용 키워드를 분석하여 가장 적합한 카테고리를 추천해주세요.")
      appendLine("거래 유형: ${transactionType.title}")
      appendLine("입력 키워드: \"$trimmed\"")
      appendLine("허용된 카테고리 목록: ${allowedCategories.joinToString(", ")}")
      appendLine("주의: 반드시 위 허용된 카테고리 목록 중 하나를 recommendedCategory로 선택해야 합니다.")
    }
    val shape = """{"recommendedCategory": "<허용 목록 중 하나>", "alternativeCategory": "<허용 목록 중 하나 또는 빈 문자열>", "suggestedPaymentMethod": "체크카드|신용카드|계좌이체|현금|간편결제", "confidence": <0~100 정수>, "reason": "<한국어 한 문장>"}"""
    val text = ai.completeJson(prompt, schema(allowedCategories), shape).trim()
    if (text.isEmpty()) return null

    val json = JSONObject(text)
    val recCat = json.optString("recommendedCategory", "").trim()
    val altCat = json.optString("alternativeCategory", "").trim().ifEmpty { null }
    val method = json.optString("suggestedPaymentMethod", "체크카드").trim()
    val confidence = json.optInt("confidence", 85).coerceIn(0, 100)
    val reason = json.optString("reason", "키워드 분석 기반 추천").trim()

    val finalCategory = if (recCat in allowedCategories) {
      recCat
    } else {
      allowedCategories.firstOrNull() ?: "기타지출"
    }

    val result = AiCategoryRecommendation(
      keyword = trimmed,
      recommendedCategory = finalCategory,
      alternativeCategory = if (altCat in allowedCategories && altCat != finalCategory) altCat else null,
      suggestedPaymentMethod = method,
      confidence = confidence,
      reason = reason
    )
    synchronized(cache) { cache[cacheKey] = result }
    return result
  }

  /** Answers from the user's own history or the built-in rules, without calling the AI. */
  private suspend fun localSuggestion(keyword: String, type: TransactionType, allowed: List<String>): AiCategoryRecommendation? {
    val remembered = db.transactionDao().lastCategoryFor(type, keyword)?.takeIf { it in allowed }
    val ruleBased = if (type == TransactionType.EXPENSE) rules.classify(keyword, keyword).takeIf { it in allowed && it != FinanceCategories.OTHER_EXPENSE } else null
    val category = remembered ?: ruleBased ?: return null
    return AiCategoryRecommendation(
      keyword = keyword,
      recommendedCategory = category,
      alternativeCategory = null,
      suggestedPaymentMethod = "체크카드",
      confidence = if (remembered != null) 100 else 90,
      reason = if (remembered != null) "예전에 같은 내용으로 고른 카테고리예요." else "자주 쓰는 가맹점 규칙으로 분류했어요."
    )
  }

  private fun schema(categories: List<String>) = JSONObject()
    .put("type", "OBJECT")
    .put("properties", JSONObject()
      .put("recommendedCategory", JSONObject().put("type", "STRING").put("enum", JSONArray(categories))
        .put("description", "입력된 거래 키워드에 가장 적합한 1순위 카테고리명"))
      .put("alternativeCategory", JSONObject().put("type", "STRING").put("nullable", true)
        .put("description", "2순위 대안 카테고리명 (없으면 빈 문자열)"))
      .put("suggestedPaymentMethod", JSONObject().put("type", "STRING")
        .put("enum", JSONArray(listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제")))
        .put("description", "해당 거래에 가장 자연스러운 결제 수단 추천"))
      .put("confidence", JSONObject().put("type", "INTEGER").put("description", "분류 신뢰도 점수 (0에서 100 사이의 정수)"))
      .put("reason", JSONObject().put("type", "STRING").put("description", "추천 이유 (간결한 한국어 한 문장)")))
    .put("required", JSONArray(listOf("recommendedCategory", "suggestedPaymentMethod", "confidence", "reason")))
}
