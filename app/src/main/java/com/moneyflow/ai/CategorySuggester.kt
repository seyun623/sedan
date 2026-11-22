package com.moneyflow.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import kotlinx.coroutines.withTimeout
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

/**
 * Gemini (Firebase AI Logic) category suggestion — moved unchanged out of the old view model.
 * The AI feature is kept as-is for now; its future (keep / replace / remove) is still to be decided.
 */
@Singleton
class CategorySuggester @Inject constructor() {

  private val categorySuggestionSchema = Schema.obj(
    properties = mapOf(
      "recommendedCategory" to Schema.string(
        description = "입력된 거래 키워드에 가장 적합한 1순위 카테고리명 (제공된 허용 카테고리 목록 중 하나여야 함)"
      ),
      "alternativeCategory" to Schema.string(
        description = "2순위 대안 카테고리명 (제공된 허용 카테고리 목록 중 하나, 없으면 빈 문자열)",
        nullable = true
      ),
      "suggestedPaymentMethod" to Schema.enumeration(
        values = listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제"),
        description = "해당 거래에 가장 자연스러운 결제 수단 추천"
      ),
      "confidence" to Schema.integer(
        description = "분류 신뢰도 점수 (0에서 100 사이의 정수)"
      ),
      "reason" to Schema.string(
        description = "입력된 키워드를 바탕으로 해당 카테고리를 추천한 이유 (간결한 한국어 한 문장)"
      )
    ),
    optionalProperties = listOf("alternativeCategory")
  )

  private val categoryAiModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = categorySuggestionSchema
      }
    )
  }


  /** Throws on network / timeout errors; returns null when the response is empty. */
  suspend fun suggest(trimmed: String, transactionType: TransactionType): AiCategoryRecommendation? {
    val allowedCategories = if (transactionType == TransactionType.INCOME) {
      FinanceCategories.incomeCategories.map { it.first }
    } else {
      FinanceCategories.expenseCategories.map { it.first }
    }

    val prompt = buildString {
      appendLine("당신은 대한민국 가계부 애플리케이션의 지출/수입 카테고리 자동 추천 AI입니다.")
      appendLine("사용자가 입력한 결제 가맹점명 또는 거래 내용 키워드를 분석하여 가장 적합한 카테고리를 추천해주세요.")
      appendLine("거래 유형: ${transactionType.title}")
      appendLine("입력 키워드: \"$trimmed\"")
      appendLine("허용된 카테고리 목록: ${allowedCategories.joinToString(", ")}")
      appendLine("주의: 반드시 위 허용된 카테고리 목록 중 하나를 recommendedCategory로 선택해야 합니다.")
    }

    val response = withTimeout(45_000L) {
      categoryAiModel.generateContent(prompt)
    }

    val text = response.text?.trim()
    if (text.isNullOrEmpty()) return null

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

    return AiCategoryRecommendation(
        keyword = trimmed,
        recommendedCategory = finalCategory,
        alternativeCategory = if (altCat in allowedCategories && altCat != finalCategory) altCat else null,
        suggestedPaymentMethod = method,
        confidence = confidence,
        reason = reason
      )
  }
}
