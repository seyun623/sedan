package com.moneyflow.domain.card

import java.time.LocalDate

/** A card bill: the amount the card company will take and when ("12/14 결제하실 금액 523,000원"). */
data class BillInfo(val amount: Long, val dueDate: LocalDate?, val label: String = "결제 예정 금액")

/** A bill kept for a card account (from the card company's screen or its 청구 안내 message). */
data class CardBill(val amount: Long, val dueEpochDay: Long?, val source: String, val fetchedAt: Long) {
  val dueDate: LocalDate? get() = dueEpochDay?.let(LocalDate::ofEpochDay)
}

/**
 * Finds the bill amount and payment date in a card company's screen text or its monthly
 * "결제 예정 금액 안내" message (pure, testable).
 */
object BillText {
  /** Words that name the amount to be paid (strong: also enough in a message). */
  private val STRONG = Regex("결제\\s*(하실|예정|될|할)\\s*금액|청구\\s*(금액|예정\\s*금액)|납부\\s*(하실|예정|할)\\s*금액|결제\\s*예정\\s*액|이번\\s*달\\s*결제\\s*금액|이용\\s*대금\\s*(청구|명세)")
  /** Weaker words, used on card company screens only. */
  private val WEAK = Regex("결제\\s*금액|이용\\s*대금|명세서\\s*금액")
  private val AMOUNT = Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\\s*원")
  /** Amounts that are not the bill even when next to it. */
  private val NOT_BILL_BEFORE = Regex("(최소|일부|리볼빙|할부|일시불|잔여|한도|포인트|캐시백|할인|적립|연체|이자|해외)[가-힣\\s]{0,6}[:：]?\\s*$")
  private val TOTAL_AMOUNT = Regex("(?:합계|총\\s*(?:결제|청구)?\\s*금액|총액)\\s*[:：]?\\s*([0-9,]+\\s*원)")
  private val EVENT = Regex("이벤트|캐시백|추첨|응모|쿠폰|최대\\s*[0-9,]+\\s*원|혜택\\s*(받|드려)|\\(광고\\)")
  private val DUE_WORD = Regex("결제\\s*일|납부\\s*일|출금\\s*일|결제\\s*예정\\s*일|청구\\s*일|자동\\s*이체\\s*일")
  private val FULL_DATE = Regex("(?<![0-9])(20\\d{2})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})")
  private val SHORT_DATE = Regex("(?<![0-9.,])(\\d{1,2})\\s*(?:[./]|월\\s*)(\\d{1,2})(?:\\s*일)?(?![0-9,])")

  /**
   * The bill in [text], or null. [message] = an SMS / KakaoTalk notice (stricter: needs a strong
   * word, and ads or events are never bills).
   */
  fun find(text: String, today: LocalDate, message: Boolean = false): BillInfo? {
    if (message && EVENT.containsMatchIn(text)) return null
    val flat = text.replace(' ', ' ')
    val words = STRONG.findAll(flat).toList().ifEmpty { if (message) emptyList() else WEAK.findAll(flat).toList() }
    for (w in words) {
      val window = flat.substring(w.range.first, (w.range.last + 80).coerceAtMost(flat.length))
      // "일시불 400,000원 / 할부 123,000원 / 합계 523,000원": the total is the bill.
      val total = TOTAL_AMOUNT.find(window)?.groupValues?.get(1)?.let { t -> AMOUNT.find(t) }
      val amount = total ?: AMOUNT.findAll(window).firstOrNull { m ->
        !NOT_BILL_BEFORE.containsMatchIn(window.substring(0, m.range.first))
      } ?: continue
      val value = amount.groupValues[1].replace(",", "").toLongOrNull()?.takeIf { it in 1..100_000_000 } ?: continue
      val due = dueDate(flat, today) ?: dateIn(window, today)
      return BillInfo(value, due, w.value.replace(Regex("\\s+"), " "))
    }
    return null
  }

  /** The payment date: next to 결제일 / 납부일 words, else a date right before the bill words. */
  fun dueDate(text: String, today: LocalDate): LocalDate? {
    for (w in DUE_WORD.findAll(text)) {
      val window = text.substring((w.range.first - 25).coerceAtLeast(0), (w.range.last + 30).coerceAtMost(text.length))
      dateIn(window, today)?.let { return it }
    }
    STRONG.find(text)?.let { w ->
      dateIn(text.substring((w.range.first - 20).coerceAtLeast(0), w.range.first), today)?.let { return it }
    }
    return null
  }

  private fun dateIn(window: String, today: LocalDate): LocalDate? {
    FULL_DATE.find(window)?.let { m ->
      val (y, mo, d) = m.destructured
      return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    SHORT_DATE.find(window)?.let { m ->
      val (mo, d) = m.destructured
      val date = runCatching { LocalDate.of(today.year, mo.toInt(), d.toInt()) }.getOrNull() ?: return null
      // A bill is due soon: "1/14" read in December is next January.
      return if (date.isBefore(today.minusDays(45))) date.plusYears(1) else date
    }
    return null
  }
}
