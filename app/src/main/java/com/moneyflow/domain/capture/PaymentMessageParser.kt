package com.moneyflow.domain.capture

import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** What a payment message means. */
enum class PaymentKind {
  /** A purchase / withdrawal / deposit to record. */
  PAYMENT,
  /** A cancelled purchase ("승인취소"): the original purchase should be removed. */
  CANCELLATION,
  /** Paid in a foreign currency; the KRW amount is not known yet. */
  FOREIGN_CURRENCY
}

data class ParsedPayment(
  val kind: PaymentKind,
  val type: TransactionType,
  val amount: Long,
  val merchant: String,
  val category: String,
  val provider: String?,
  val cardLast4: String?,
  val installmentMonths: Int?,
  /** Payment time stated in the message, or null if the message has none. */
  val paidAt: Long?,
  val foreignAmount: String? = null,
  /** For a card bill paid from a bank account: the card company named in the message ("삼성카드"). */
  val billCard: String? = null
)

sealed interface ParseResult {
  data class Success(val payment: ParsedPayment) : ParseResult
  data class Ignored(val reason: String) : ParseResult
}

/**
 * Parses Korean card / bank / pay notifications and SMS into payments.
 *
 * It is intentionally strict: a message is only accepted when it contains both a payment action
 * (승인, 결제, 출금, 입금, 사용 …) and a KRW amount, so ordinary chat messages that mention money
 * ("치킨 20,000원 보낼게") are rejected.
 */
class PaymentMessageParser(private val zone: ZoneId) {

  fun parse(title: String, body: String, receivedAt: Long): ParseResult {
    val text = normalize("$title $body")
    if (text.isBlank()) return ParseResult.Ignored("빈 메시지")
    // Links: card companies don't send purchase alerts with links; smishing texts do.
    if (LINK.containsMatchIn(text)) return ParseResult.Ignored("링크가 있는 메시지")
    if (REJECTED.containsMatchIn(text)) return ParseResult.Ignored("거절·실패 알림")
    if (NOTICE.containsMatchIn(text)) return ParseResult.Ignored("광고·안내 메시지")

    val provider = detectProvider(text)
    val paidAt = extractDateTime(text, receivedAt)
    val cardLast4 = CARD_LAST4.find(text)?.groupValues?.get(1)
    val installment = INSTALLMENT.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 1 }

    val isCancel = CANCEL.containsMatchIn(text)
    val action = ACTION.find(text) ?: return ParseResult.Ignored("결제/입출금 문구가 없음")

    // Foreign currency purchases ("해외승인 USD 12.50") have no KRW amount yet.
    FOREIGN.find(text)?.let { m ->
      if (amountCandidates(text).isEmpty()) {
        val merchant = extractMerchant(text, provider, isIncome = false)
        return ParseResult.Success(
          ParsedPayment(
            kind = PaymentKind.FOREIGN_CURRENCY,
            type = TransactionType.EXPENSE,
            amount = 0,
            merchant = merchant,
            category = classify(merchant, text),
            provider = provider,
            cardLast4 = cardLast4,
            installmentMonths = installment,
            paidAt = paidAt,
            foreignAmount = m.value.trim()
          )
        )
      }
    }

    val amount = pickAmount(text, action.range.first) ?: return ParseResult.Ignored("금액을 찾을 수 없음")
    if (amount <= 0) return ParseResult.Ignored("금액이 0원")

    val billCard = PROVIDERS.filter { it.second.endsWith("카드") }
      .mapNotNull { (needle, name) -> text.indexOf(needle, ignoreCase = true).takeIf { it >= 0 }?.let { it to name } }
      .minByOrNull { it.first }?.second
    val fromBank = provider != null && !provider.endsWith("카드") && !provider.endsWith("페이") && provider != "토스"
    // "[KB] 출금 1,234,560원 삼성카드" / "신한카드결제": a bank paying a card bill, even without "대금".
    val isCardBill = CARD_BILL.containsMatchIn(text) ||
      (fromBank && billCard != null && BANK_DEBIT.containsMatchIn(text) && !INCOME_ONLY.containsMatchIn(text))
    val sentToMe = SENT_TO_ME.containsMatchIn(text)
    val isIncome = !isCancel && !isCardBill &&
      (sentToMe || (INCOME.containsMatchIn(text) && !WITHDRAW.containsMatchIn(text)))
    val type = when {
      isCardBill -> TransactionType.TRANSFER
      isIncome -> TransactionType.INCOME
      else -> TransactionType.EXPENSE
    }
    val merchant = extractMerchant(text, provider, isIncome)
    val category = when (type) {
      TransactionType.TRANSFER -> FinanceCategories.CARD_PAYMENT
      TransactionType.INCOME -> classifyIncome(text)
      TransactionType.EXPENSE -> classify(merchant, text)
    }
    return ParseResult.Success(
      ParsedPayment(
        kind = if (isCancel) PaymentKind.CANCELLATION else PaymentKind.PAYMENT,
        type = type,
        amount = amount,
        merchant = merchant,
        category = category,
        provider = provider,
        cardLast4 = cardLast4,
        installmentMonths = installment,
        paidAt = paidAt,
        billCard = if (isCardBill) billCard else null
      )
    )
  }

  // ---- Normalization ---------------------------------------------------------------------

  private fun normalize(s: String) = s
    .replace("[Web발신]", " ")
    .replace("[국외발신]", " ")
    .replace('\n', ' ')
    .replace('\r', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()

  // ---- Provider ----------------------------------------------------------------------------

  /** The provider that appears earliest in the text wins (so a PG name later in the text loses). */
  fun detectProvider(text: String): String? =
    startBankTag(text)?.let { BANK_TAGS[it.groupValues[1].uppercase()] } ?: PROVIDERS.mapNotNull { (needle, name) ->
      val idx = text.indexOf(needle, ignoreCase = true)
      if (idx >= 0) idx to name else null
    }.minByOrNull { it.first }?.second

  // ---- Amount --------------------------------------------------------------------------------

  private data class AmountCandidate(val value: Long, val start: Int)

  /** All "N원" amounts that are not balances, totals, limits or reward points. */
  private fun amountCandidates(text: String): List<AmountCandidate> =
    WON_AMOUNT.findAll(text).mapNotNull { m ->
      val before = text.substring(maxOf(0, m.range.first - 8), m.range.first)
      if (NON_PAYMENT_PREFIX.containsMatchIn(before)) return@mapNotNull null
      val v = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@mapNotNull null
      AmountCandidate(v, m.range.first)
    }.toList()

  private fun pickAmount(text: String, actionIndex: Int): Long? {
    val candidates = amountCandidates(text)
    if (candidates.isNotEmpty()) {
      // Prefer the amount closest to the action keyword.
      return candidates.minByOrNull { kotlin.math.abs(it.start - actionIndex) }!!.value
    }
    // "승인 12,000" (amount without 원); dates and times are removed first so "승인 11/25" isn't 11원.
    val noDates = text.replace(DATE_TIME, " ")
    ACTION_AMOUNT.find(noDates)?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull()?.let { return it }
    // "승인 11/25 14:30 홍*동 12,000 스타벅스": a comma-grouped number after the action word.
    return BARE_AMOUNT.findAll(noDates).firstOrNull { it.range.first > actionIndex.coerceAtMost(noDates.length) - 1 }
      ?.value?.replace(",", "")?.toLongOrNull()
  }

  // ---- Date / time ---------------------------------------------------------------------------

  /**
   * Reads "MM/dd HH:mm" (or MM.dd / MM-dd). The year is taken from [receivedAt]; a date that would
   * fall more than a day in the future belongs to the previous year (Dec 31 message read on Jan 1).
   */
  fun extractDateTime(text: String, receivedAt: Long): Long? {
    val m = DATE_TIME.find(text) ?: return null
    val (mo, d, h, mi) = m.destructured
    val received = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(receivedAt), zone)
    return runCatching {
      var dt = LocalDateTime.of(received.year, mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone)
      if (dt.isAfter(received.plusDays(1))) dt = dt.minusYears(1)
      dt.toInstant().toEpochMilli()
    }.getOrNull()
  }

  // ---- Merchant ------------------------------------------------------------------------------

  fun extractMerchant(text: String, provider: String?, isIncome: Boolean): String {
    // Pay apps: "스타벅스에서 12,500원 결제했어요"
    PAY_STYLE.find(text)?.let { return it.groupValues[1].trim() }
    // Transfers in pay/bank apps: "홍길동님이 50,000원을 보냈어요" / "홍길동님에게 ... 보냈어요"
    PERSON_STYLE.find(text)?.let { return it.groupValues[1].trim() }

    // Everything that is not the merchant / sender is removed; what remains is the name.
    var clean = " $text "
      .replace(BALANCE_SEGMENT, " ")
      .replace(REWARD_NOTE, " ")
      .replace(FOREIGN, " ")
      .replace(DATE_TIME, " ")
      .replace(WON_AMOUNT, " ")
      .replace(BARE_AMOUNT, " ")
      .replace(CARD_LAST4, " ")
      .replace(MASKED_NAME, " ")
      .replace(Regex("\\[[^\\]]*\\]"), " ")
    startBankTag(text)?.let { clean = clean.replaceFirst(it.value.trim(), " ") }
    provider?.let { p -> PROVIDERS.filter { it.second == p }.forEach { clean = clean.replace(it.first, " ", ignoreCase = true) } }
    clean = clean.replace(NOISE, " ").replace(Regex("\\(\\s*\\)"), " ")
    val tokens = clean.split(Regex("\\s+"))
      .map { it.trim(',', '.', '/', ':', '-') }
      .filter { it.length >= 2 && !MASKED_NUMBER.matches(it) && it.any { c -> c.isLetter() } }
      .take(3)
    return if (tokens.isNotEmpty()) tokens.joinToString(" ") else if (isIncome) "입금" else "알 수 없는 가맹점"
  }

  /** Short bank tags at the start of bank SMS: "신한 11/25", "[KB]11/25", "우리 ", "[하나]" … */
  private fun startBankTag(text: String): MatchResult? = START_BANK_TAG.find(text.trimStart())

  // ---- Category ------------------------------------------------------------------------------

  fun classify(merchant: String, fullText: String): String {
    for (scope in listOf(merchant, fullText)) {
      val tokens = scope.split(Regex("[\\s()\\[\\]/·,]+")).filter { it.isNotBlank() }
      for ((category, keywords) in CATEGORY_KEYWORDS) {
        val hit = keywords.any { kw ->
          if (kw.length <= 2) tokens.any { it.equals(kw, ignoreCase = true) }
          else scope.contains(kw, ignoreCase = true)
        }
        if (hit) return category
      }
    }
    return FinanceCategories.OTHER_EXPENSE
  }

  private fun classifyIncome(text: String): String = when {
    Regex("급여|월급").containsMatchIn(text) -> FinanceCategories.SALARY
    Regex("상여|성과급|보너스").containsMatchIn(text) -> "상여금"
    Regex("이자|배당").containsMatchIn(text) -> "금융소득/배당"
    else -> FinanceCategories.OTHER_INCOME
  }

  companion object {
    /** "12,000원" / "12,000 원" — but not "123 원할머니보쌈" (a number, then a name starting with 원). */
    private val WON_AMOUNT = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:원|\\s원(?![가-힣A-Za-z]))")
    private val ACTION_AMOUNT = Regex("(승인|출금|입금|결제|사용)\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?![0-9]*[/.:])")
    /** Web links and URL shorteners (merchant names like "NETFLIX.COM" are fine). */
    private val LINK = Regex("https?://|www\\.|\\b(?:bit\\.ly|han\\.gl|me2\\.do|url\\.kr|vo\\.la|t\\.ly|buly\\.kr|tinyurl\\.com|goo\\.gl)/", RegexOption.IGNORE_CASE)
    private val REJECTED = Regex("승인\\s*거절|거절|결제\\s*실패|승인\\s*실패|한도\\s*초과|잔액\\s*부족|사용\\s*불가|결제\\s*불가")
    private val NOTICE = Regex("\\(광고\\)|결제\\s*예정|청구\\s*(금액|예정|서)|명세서|이용\\s*대금\\s*(안내|명세)|이벤트|최대\\s*[0-9,]+\\s*원|캐시백\\s*(이벤트|혜택|받)")
    private val BANK_DEBIT = Regex("출금|자동\\s*이체|자동\\s*납부|카드\\s*결제")
    private val INCOME_ONLY = Regex("입금")
    private val ACTION = Regex("승인|결제|출금|입금|사용|이용|지급|결제했어요|보냈어요|받았어요")
    private val CANCEL = Regex("승인\\s*취소|취소\\s*승인|결제\\s*취소|취소\\s*완료|매출\\s*취소|사용\\s*취소")
    private val INCOME = Regex("입금|급여|받았어요|받은\\s*돈|환급|이자\\s*지급")
    private val WITHDRAW = Regex("출금|보냈어요")
    private val CARD_BILL = Regex("(카드\\s*(결제)?\\s*대금|결제대금|카드값).{0,12}(출금|납부|결제)|(출금|납부).{0,12}(카드\\s*대금|결제대금)")
    private val NON_PAYMENT_PREFIX = Regex("누적|잔액|잔고|잔여|한도|이용가능|캐시백|적립|포인트|할인")
    private const val NUM = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private val FOREIGN = Regex("(USD|US\\$|EUR|JPY|GBP|CNY|AUD|CAD|HKD|SGD|THB|VND|\\$|€|¥)\\s*$NUM|$NUM\\s*(USD|EUR|JPY|GBP|CNY|달러|엔|유로|위안)")
    private val INSTALLMENT = Regex("([0-9]{1,2})\\s*개월")
    private val CARD_LAST4 = Regex("\\(([0-9*]{3,4})\\)")
    private val MASKED_NUMBER = Regex("[0-9*\\-()]+")
    private val MASKED_NAME = Regex("[가-힣]\\*[가-힣]{0,2}")
    private val DATE_TIME = Regex("(\\d{1,2})[/.\\-](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")
    private val PAY_STYLE = Regex("([^\\s\\[\\]]{2,30}?)에서\\s*[0-9,]+\\s*원")
    private val BALANCE_SEGMENT = Regex("(누적|잔액|잔고|잔여한도|사용가능금액|사용가능|이용가능)\\s*:?\\s*-?[0-9,]+\\s*원?")
    private val REWARD_NOTE = Regex("\\((캐시백|적립|할인)[^)]*\\)")
    private val BARE_AMOUNT = Regex("(?<![0-9A-Za-z*])[0-9]{1,3}(?:,[0-9]{3})+(?![0-9])")
    private val PERSON_STYLE = Regex("([^\\s\\[\\]]{1,20})님(?:이|에게|께)\\s*[0-9,]+\\s*원")
    private val SENT_TO_ME = Regex("님이\\s*[0-9,]+\\s*원을?\\s*(보냈|입금)")
    private val START_BANK_TAG = Regex("^(?:\\[Web발신\\]\\s*)?\\[?(신한|KB|국민|우리|하나|농협|NH|IBK|기업|SC|씨티|부산|경남|대구|광주|전북|제주|수협|새마을|신협|우체국|케이뱅크|카카오뱅크|토스뱅크)\\]?(?=[\\s0-9]|$)", RegexOption.IGNORE_CASE)
    private val BANK_TAGS = mapOf(
      "신한" to "신한은행", "KB" to "KB국민은행", "국민" to "KB국민은행", "우리" to "우리은행", "하나" to "하나은행",
      "농협" to "NH농협은행", "NH" to "NH농협은행", "IBK" to "IBK기업은행", "기업" to "IBK기업은행",
      "SC" to "SC제일은행", "씨티" to "씨티은행", "부산" to "부산은행", "경남" to "경남은행", "대구" to "iM뱅크",
      "광주" to "광주은행", "전북" to "전북은행", "제주" to "제주은행", "수협" to "수협은행", "새마을" to "새마을금고",
      "신협" to "신협", "우체국" to "우체국", "케이뱅크" to "케이뱅크", "카카오뱅크" to "카카오뱅크", "토스뱅크" to "토스뱅크"
    )
    private val NOISE = Regex("Web발신|체크카드|신용카드|체크|신용|일시불|할부|[0-9]{1,2}\\s*개월|승인\\s*취소|취소|승인|결제했어요|결제|출금|입금|완료|사용|이용|해외|국내|원$")

    /** (text to look for, canonical provider name). Order does not matter. */
    val PROVIDERS = listOf(
      "신한카드" to "신한카드", "신한은행" to "신한은행", "SOL페이" to "신한카드",
      "KB국민카드" to "KB국민카드", "국민카드" to "KB국민카드", "KB Pay" to "KB국민카드",
      "KB국민은행" to "KB국민은행", "국민은행" to "KB국민은행",
      "현대카드" to "현대카드", "삼성카드" to "삼성카드", "롯데카드" to "롯데카드",
      "우리카드" to "우리카드", "우리은행" to "우리은행",
      "하나카드" to "하나카드", "하나은행" to "하나은행",
      "NH농협카드" to "NH농협카드", "농협카드" to "NH농협카드", "NH농협은행" to "NH농협은행", "농협은행" to "NH농협은행",
      "BC카드" to "BC카드", "비씨카드" to "BC카드",
      "IBK기업은행" to "IBK기업은행", "기업은행" to "IBK기업은행",
      "카카오뱅크" to "카카오뱅크", "케이뱅크" to "케이뱅크", "토스뱅크" to "토스뱅크",
      "카카오페이" to "카카오페이", "네이버페이" to "네이버페이", "토스페이" to "토스페이", "토스" to "토스",
      "삼성페이" to "삼성페이"
    )

    private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
      "카페/간식" to listOf(
        "스타벅스", "메가커피", "메가MGC", "이디야", "투썸", "빽다방", "컴포즈", "폴바셋", "할리스",
        "파스쿠찌", "커피", "카페", "베이커리", "파리바게뜨", "뚜레쥬르", "배스킨라빈스", "던킨", "디저트", "공차"
      ),
      "식비" to listOf(
        "식당", "음식점", "맥도날드", "버거킹", "롯데리아", "맘스터치", "KFC", "서브웨이", "배달의민족", "배민",
        "요기요", "쿠팡이츠", "김밥", "국밥", "치킨", "피자", "반점", "갈비", "삼겹살", "스시", "초밥", "돈까스",
        "우동", "라멘", "분식", "마라탕", "교촌", "BHC", "BBQ"
      ),
      "생활/마트" to listOf(
        "GS25", "CU", "씨유", "세븐일레븐", "이마트24", "미니스톱", "편의점", "이마트", "홈플러스", "롯데마트",
        "노브랜드", "식자재", "마트", "슈퍼", "다이소", "올리브영", "생활용품"
      ),
      "쇼핑" to listOf(
        "쿠팡", "11번가", "G마켓", "지마켓", "옥션", "컬리", "무신사", "지그재그", "에이블리", "백화점", "아울렛",
        "면세점", "유니클로", "스마트스토어", "쇼핑", "인터파크"
      ),
      "교통" to listOf(
        "카카오T", "택시", "코레일", "SRT", "티머니", "지하철", "버스", "하이패스", "주유소", "GS칼텍스",
        "SK에너지", "S-OIL", "오일뱅크", "쏘카", "그린카", "주차"
      ),
      "주거/통신" to listOf(
        "SKT", "KT", "LGU+", "유플러스", "알뜰폰", "통신요금", "관리비", "월세", "도시가스", "한국전력", "전기요금", "수도요금"
      ),
      "문화/여가" to listOf(
        "CGV", "롯데시네마", "메가박스", "넷플릭스", "유튜브", "디즈니", "웨이브", "티빙", "왓챠", "멜론",
        "지니뮤직", "스포티파이", "예스24", "교보문고", "헬스", "피트니스", "골프", "볼링", "노래방", "PC방"
      ),
      "의료/건강" to listOf(
        "병원", "의원", "내과", "이비인후과", "치과", "안과", "정형외과", "피부과", "약국", "한의원", "보건소"
      ),
      FinanceCategories.FINANCE_INSURANCE to listOf("보험", "대출이자", "이자납부", "대출상환", "증권")
    )
  }
}
