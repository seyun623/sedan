package com.example.util

import com.example.data.model.FinanceCategories
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedTransactionResult(
  val rawText: String,
  val providerName: String,
  val type: TransactionType,
  val amount: Long,
  val merchantOrSender: String,
  val category: String,
  val timestamp: Long,
  val isSuccess: Boolean,
  val reason: String? = null
)

/**
 * Intelligent parser engine for Korean bank/card payment SMS and push notifications.
 * Automatically parses financial provider, transaction type (지출/입금), amount,
 * merchant name, and classifies the category using merchant keyword semantics.
 */
object PaymentNotificationParser {

  // Provider keywords
  private val providers = listOf(
    "신한카드", "신한은행", "신한SOL",
    "KB국민카드", "KB국민은행", "KB Pay", "국민카드", "국민은행",
    "토스뱅크", "토스",
    "카카오뱅크", "카카오페이",
    "현대카드",
    "삼성카드", "삼성페이",
    "우리카드", "우리은행", "WON뱅킹",
    "하나카드", "하나은행", "하나원큐",
    "NH농협카드", "NH농협은행", "농협",
    "롯데카드",
    "BC카드", "IBK기업은행", "케이뱅크", "네이버페이"
  )

  // Category mapping keywords
  private val categoryKeywords: Map<String, List<String>> = mapOf(
    "카페/간식" to listOf(
      "스타벅스", "메가커피", "메가MGC", "이디야", "투썸", "투썸플레이스", "빽다방",
      "컴포즈", "폴바셋", "할리스", "파스쿠찌", "커피", "카페", "베이커리", "파리바게뜨",
      "뚜레쥬르", "배스킨라빈스", "던킨", "와플", "디저트", "차차", "공차"
    ),
    "식비" to listOf(
      "식당", "음식점", "맥도날드", "버거킹", "롯데리아", "맘스터치", "KFC", "서브웨이",
      "배달의민족", "배민", "요기요", "쿠팡이츠", "김밥", "국밥", "치킨", "피자", "짜장",
      "반점", "갈비", "삼겹살", "고기", "스시", "초밥", "돈까스", "우동", "라멘", "한식",
      "양식", "중식", "일식", "분식", "마라탕", "교촌", "BHC", "BBQ"
    ),
    "생활/마트" to listOf(
      "GS25", "CU", "씨유", "세븐일레븐", "이마트24", "미니스톱", "편의점",
      "이마트", "홈플러스", "롯데마트", "노브랜드", "식자재", "마트", "슈퍼",
      "다이소", "올리브영", "모던하우스", "생활용품"
    ),
    "쇼핑" to listOf(
      "쿠팡", "네이버페이", "11번가", "G마켓", "옥션", "마켓컬리", "컬리", "무신사",
      "지그재그", "에이블리", "백화점", "아울렛", "면세점", "자라", "유니클로", "스파오",
      "쇼핑몰", "인터파크", "인터넷쇼핑"
    ),
    "교통" to listOf(
      "카카오T", "카카오택시", "택시", "코레일", "SRT", "티머니", "지하철", "버스",
      "하이패스", "주유소", "GS칼텍스", "SK에너지", "S-OIL", "현대오일뱅크", "알뜰주유소",
      "쏘카", "그린카", "따릉이", "주차장", "대리운전"
    ),
    "주거/통신" to listOf(
      "SKT", "KT", "LGU+", "LG유플러스", "알뜰폰", "통신요금", "관리비", "월세",
      "도시가스", "한국전력", "전기요금", "수도요금", "아파트관리비"
    ),
    "문화/여가" to listOf(
      "CGV", "롯데시네마", "메가박스", "영화관", "넷플릭스", "유튜브", "디즈니",
      "웨이브", "티빙", "왓챠", "멜론", "지니뮤직", "스포티파이", "인터파크티켓",
      "예스24", "교보문고", "영풍문고", "헬스장", "피트니스", "골프", "볼링", "노래방", "PC방"
    ),
    "의료/건강" to listOf(
      "병원", "의원", "내과", "이비인후과", "치과", "안과", "정형외과", "피부과",
      "약국", "한의원", "보건소", "종합병원", "약방"
    ),
    "급여/수입" to listOf(
      "급여", "월급", "상여", "보너스", "수당", "이자", "배당", "용돈", "입금", "환급"
    )
  )

  /**
   * Parses arbitrary notification or SMS text into a structured financial transaction.
   */
  fun parse(title: String, text: String): ParsedTransactionResult {
    val fullText = "$title $text".trim()
    if (fullText.isBlank()) {
      return ParsedTransactionResult(
        rawText = fullText,
        providerName = "알 수 없음",
        type = TransactionType.EXPENSE,
        amount = 0L,
        merchantOrSender = "알 수 없음",
        category = "기타 지출",
        timestamp = System.currentTimeMillis(),
        isSuccess = false,
        reason = "텍스트가 비어 있습니다."
      )
    }

    // 1. Identify Financial Provider
    val detectedProvider = providers.find { fullText.contains(it, ignoreCase = true) }
      ?: providers.find { title.contains(it, ignoreCase = true) }
      ?: when {
        title.contains("카드", ignoreCase = true) -> title
        title.contains("은행", ignoreCase = true) -> title
        title.contains("페이", ignoreCase = true) -> title
        else -> "카드/은행 결제"
      }

    // 2. Identify Transaction Type (EXPENSE vs INCOME)
    val isIncome = fullText.contains("입금") ||
      fullText.contains("급여") ||
      fullText.contains("환급") ||
      fullText.contains("캐시백") ||
      (fullText.contains("받은 돈") && !fullText.contains("보낸 돈"))

    val txType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

    // 3. Extract Amount
    val amount = extractAmount(fullText)
    if (amount <= 0L) {
      return ParsedTransactionResult(
        rawText = fullText,
        providerName = detectedProvider,
        type = txType,
        amount = 0L,
        merchantOrSender = "알 수 없음",
        category = if (isIncome) "기타 수입" else "기타 지출",
        timestamp = System.currentTimeMillis(),
        isSuccess = false,
        reason = "결제 금액을 감지할 수 없습니다."
      )
    }

    // 4. Extract Merchant / Sender
    val merchant = extractMerchant(fullText, detectedProvider, isIncome)

    // 5. Classify Category
    val category = if (isIncome) {
      if (merchant.contains("급여") || fullText.contains("급여") || fullText.contains("월급")) "월급" else "기타 수입"
    } else {
      classifyCategory(merchant, fullText)
    }

    return ParsedTransactionResult(
      rawText = fullText,
      providerName = detectedProvider,
      type = txType,
      amount = amount,
      merchantOrSender = merchant,
      category = category,
      timestamp = System.currentTimeMillis(),
      isSuccess = true
    )
  }

  /**
   * Extracts currency amount from string, handling patterns like:
   * 15,000원, 24000원, ₩35,000, 1,200,000 등
   */
  private fun extractAmount(text: String): Long {
    // Pattern 1: [0-9,]+원
    val wonPattern = Pattern.compile("([0-9]{1,3}(,[0-9]{3})+|[0-9]+)\\s*원")
    val wonMatcher = wonPattern.matcher(text)
    if (wonMatcher.find()) {
      val raw = wonMatcher.group(1)?.replace(",", "") ?: ""
      val parsed = raw.toLongOrNull() ?: 0L
      if (parsed > 0L) return parsed
    }

    // Pattern 2: [승인/출금/입금/결제] [0-9,]+
    val actionPattern = Pattern.compile("(승인|출금|입금|결제|사용)\\s*([0-9]{1,3}(,[0-9]{3})+|[0-9]+)")
    val actionMatcher = actionPattern.matcher(text)
    if (actionMatcher.find()) {
      val raw = actionMatcher.group(2)?.replace(",", "") ?: ""
      val parsed = raw.toLongOrNull() ?: 0L
      if (parsed > 0L) return parsed
    }

    // Pattern 3: ₩[0-9,]+
    val symbolPattern = Pattern.compile("[₩￦]\\s*([0-9]{1,3}(,[0-9]{3})+|[0-9]+)")
    val symbolMatcher = symbolPattern.matcher(text)
    if (symbolMatcher.find()) {
      val raw = symbolMatcher.group(1)?.replace(",", "") ?: ""
      val parsed = raw.toLongOrNull() ?: 0L
      if (parsed > 0L) return parsed
    }

    return 0L
  }

  /**
   * Extracts merchant name by filtering out common payment template boilerplate.
   */
  private fun extractMerchant(text: String, provider: String, isIncome: Boolean): String {
    // Strip common tags
    var clean = text
      .replace(Regex("\\[Web발신\\]"), "")
      .replace(Regex("\\[[^\\]]+\\]"), " ") // Strip brackets like [승인], [신한카드]
      .replace(Regex("일시불"), " ")
      .replace(Regex("할부"), " ")
      .replace(Regex("누적[0-9,]+원?"), " ")
      .replace(Regex("잔액[0-9,]+원?"), " ")
      .replace(Regex("잔여한도[0-9,]+원?"), " ")
      .replace(Regex("[0-9]{1,2}/[0-9]{1,2}\\s+[0-9]{1,2}:[0-9]{1,2}"), " ") // Strip date/time like 10/29 18:20
      .replace(Regex("([0-9]{1,3}(,[0-9]{3})+|[0-9]+)\\s*원"), " ") // Strip amount
      .replace(Regex("[0-9]{4}-[0-9]{4}"), " ")
      .replace(Regex("[가-힣]\\*[가-힣]"), " ") // Strip masked names like 홍*동
      .replace(provider, " ")
      .replace(Regex("(체크카드|신용카드|승인|결제|출금|입금|완료|잔여|한도|지출|사용)"), " ")
      .trim()

    val tokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() && it.length >= 2 }
    return tokens.lastOrNull() ?: tokens.firstOrNull() ?: if (isIncome) "입금처" else "일반 가맹점"
  }

  /**
   * Classifies category based on merchant name and full text.
   */
  fun classifyCategory(merchant: String, fullText: String): String {
    val searchScope = "$merchant $fullText"

    for ((category, keywords) in categoryKeywords) {
      if (keywords.any { searchScope.contains(it, ignoreCase = true) }) {
        return category
      }
    }

    return "기타 지출"
  }
}
