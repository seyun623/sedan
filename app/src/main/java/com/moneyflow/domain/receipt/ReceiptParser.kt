package com.moneyflow.domain.receipt

import java.time.LocalDate

/** What a receipt says: the paid total, the shop, the date and a few item names. */
data class ReceiptInfo(val total: Long, val merchant: String, val date: LocalDate?, val items: List<String>) {
  /** "이마트 · 우유 외 2건" */
  val memo: String
    get() = when {
      items.isEmpty() -> merchant
      merchant.isEmpty() -> summary
      else -> "$merchant · $summary"
    }
  private val summary: String get() = if (items.size == 1) items.first() else "${items.first()} 외 ${items.size - 1}건"
}

/**
 * Reads a receipt from the text recognized in its photo (on the phone, nothing is sent out).
 * The paid total ("합계", "결제금액", "받을금액") wins; without one, the largest amount.
 */
class ReceiptParser(private val today: LocalDate) {

  fun parse(text: String): ReceiptInfo? {
    val lines = text.split('\n').map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val total = total(lines) ?: return null
    return ReceiptInfo(total.first, merchant(lines), date(lines), items(lines, total.second))
  }

  /** (amount, line index of the total). */
  private fun total(lines: List<String>): Pair<Long, Int>? {
    for (rank in TOTALS.indices) {
      for (i in lines.indices.reversed()) {
        val line = lines[i]
        if (!TOTALS[rank].containsMatchIn(line) || NOT_TOTAL.containsMatchIn(line)) continue
        // The amount is on the same line, or (two-column receipts) on the next line.
        val amount = amountIn(line.substring(TOTALS[rank].find(line)!!.range.last + 1))
          ?: lines.getOrNull(i + 1)?.takeIf { ONLY_AMOUNT.matches(it) }?.let(::amountIn)
        if (amount != null && amount in 100..100_000_000) return amount to i
      }
    }
    val all = lines.withIndex().filter { !NOT_TOTAL.containsMatchIn(it.value) && !PHONE_OR_NUMBER.containsMatchIn(it.value) && DATE.find(it.value) == null && !TIME.containsMatchIn(it.value) }
      .mapNotNull { (i, l) -> amountIn(l)?.takeIf { it in 100..100_000_000 }?.let { it to i } }
    return all.maxByOrNull { it.first }
  }

  private fun amountIn(text: String): Long? =
    AMOUNT.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").replace(".", "").toLongOrNull() }.lastOrNull()

  private fun merchant(lines: List<String>): String {
    lines.firstNotNullOfOrNull { l -> MERCHANT_LABEL.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 } }
      ?.let { return clean(it) }
    return lines.take(6).firstOrNull { l ->
      l.count { it in '가'..'힣' } >= 2 && l.count { it.isDigit() } <= 2 && !NOT_MERCHANT.containsMatchIn(l)
    }?.let(::clean).orEmpty()
  }

  private fun clean(s: String) = s.replace(Regex("[\\[\\]()*<>]"), " ").replace(Regex("\\s+"), " ").trim().take(30)

  private fun date(lines: List<String>): LocalDate? {
    for (l in lines) {
      val m = DATE.find(l) ?: continue
      val (y, mo, d) = m.destructured
      val year = y.toInt().let { if (it < 100) 2000 + it else it }
      val date = runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull() ?: continue
      if (!date.isAfter(today.plusDays(1)) && date.isAfter(today.minusYears(2))) return date
    }
    return null
  }

  /** Names on price lines above the total (at most 5). */
  private fun items(lines: List<String>, totalLine: Int): List<String> =
    lines.take(totalLine).asSequence()
      .filter { AMOUNT.containsMatchIn(it) && !NOT_ITEM.containsMatchIn(it) && !PHONE_OR_NUMBER.containsMatchIn(it) && DATE.find(it) == null }
      .map { it.replace(AMOUNT, " ").replace(Regex("[0-9*#@xX×]+"), " ").replace(Regex("\\s+"), " ").trim() }
      .filter { name -> name.count { it in '가'..'힣' || it.isLetter() } >= 2 }
      .map { it.take(20) }
      .distinct().take(5).toList()

  companion object {
    private val RECEIPT_WORDS = Regex("영수증|합\\s*계|결제\\s*금액|받을\\s*금액|신용\\s*카드|카드\\s*결제|승인\\s*번호|부가세|과세|사업자|매출|판매\\s*총액|TOTAL", RegexOption.IGNORE_CASE)

    /** Words found on almost every receipt (two of them): photos of people or places are skipped. */
    fun looksLikeReceipt(text: String): Boolean = RECEIPT_WORDS.findAll(text).map { it.value.replace(" ", "") }.distinct().count() >= 2

    private val AMOUNT = Regex("(?<![0-9.,])([0-9]{1,3}(?:[,.][0-9]{3})+|[0-9]{3,8})\\s*원?(?![0-9])")
    private val TIME = Regex("(?<![0-9])[0-2]?[0-9]:[0-5][0-9]")
    private val ONLY_AMOUNT = Regex("[₩\\s]*[0-9][0-9,]{2,12}\\s*원?")
    /** Totals, best first. */
    private val TOTALS = listOf(
      Regex("(받을|결제|청구|승인)\\s*금\\s*액|카드\\s*결제|신용\\s*카드|총\\s*결제"),
      Regex("합\\s*계\\s*(금\\s*액)?|총\\s*(금\\s*액|합\\s*계|액)|판매\\s*총액|TOTAL", RegexOption.IGNORE_CASE)
    )
    private val NOT_TOTAL = Regex("부가세|부가\\s*가치|과세|면세|공급\\s*가|거스름|받은\\s*돈|잔돈|포인트|적립|할인|봉사료|사업자|전화|TEL|카드\\s*번호|승인\\s*번호|No\\.", RegexOption.IGNORE_CASE)
    private val PHONE_OR_NUMBER = Regex("[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}|[0-9]{3}-[0-9]{2}-[0-9]{5}|[0-9]{4}[-*][0-9*]{4}[-*][0-9*]{4}")
    private val MERCHANT_LABEL = Regex("(?:상\\s*호|가맹점\\s*명?|매장\\s*명|점\\s*포\\s*명)\\s*[:：]?\\s*(.+)")
    private val NOT_MERCHANT = Regex("영수증|RECEIPT|전표|카드|고객|매출|사업자|대표|주소|TEL|전화|교환|환불", RegexOption.IGNORE_CASE)
    private val NOT_ITEM = Regex("합\\s*계|총|결제|부가세|과세|면세|공급|거스름|받은|할인|포인트|적립|카드|승인|사업자|대표|전화|금액|수량|단가")
    private val DATE = Regex("(?<![0-9])(20[0-9]{2}|[0-9]{2})\\s*[-./년]\\s*([0-9]{1,2})\\s*[-./월]\\s*([0-9]{1,2})")
  }
}

/** Reads a receipt photo with the AI (Gemini) when the phone's text reading found nothing. */
class AiReceiptReader @javax.inject.Inject constructor(private val ai: com.moneyflow.ai.AiService) {
  suspend fun canRead(): Boolean = ai.isReady() && ai.canSearchWeb()

  suspend fun read(jpeg: ByteArray, today: LocalDate): ReceiptInfo? {
    val schema = org.json.JSONObject().put("type", "OBJECT").put("properties", org.json.JSONObject()
      .put("total", org.json.JSONObject().put("type", "INTEGER"))
      .put("merchant", org.json.JSONObject().put("type", "STRING"))
      .put("date", org.json.JSONObject().put("type", "STRING"))
      .put("items", org.json.JSONObject().put("type", "ARRAY").put("items", org.json.JSONObject().put("type", "STRING"))))
      .put("required", org.json.JSONArray(listOf("total")))
    val prompt = "이 사진은 한국 영수증입니다. 실제로 결제한 합계 금액(원), 가게 이름, 결제 날짜(YYYY-MM-DD), 산 물건 이름 최대 5개를 읽어 주세요. " +
      "사진에 없는 값은 만들지 말고 비워 두세요. 영수증이 아니면 total을 0으로 하세요."
    val base64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
    val json = org.json.JSONObject(ai.completeJson(prompt, schema, """{"total":12300,"merchant":"","date":"","items":[]}""", "image/jpeg" to base64))
    val total = json.optLong("total", 0L).takeIf { it in 100..100_000_000 } ?: return null
    val date = runCatching { LocalDate.parse(json.optString("date").trim()) }.getOrNull()
      ?.takeIf { !it.isAfter(today.plusDays(1)) && it.isAfter(today.minusYears(2)) }
    val items = json.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.optString(it).trim().take(20) }.filter { it.length >= 2 }.distinct().take(5) }.orEmpty()
    return ReceiptInfo(total, json.optString("merchant").trim().take(30), date, items)
  }
}

/** A receipt and an already recorded card payment of the same amount (pure, testable). */
object ReceiptMatch {
  /**
   * The expense of exactly [info]'s total, recorded within a day of [date], that has no product
   * names yet (" · "), closest in time first. The card alert usually recorded it already.
   */
  fun find(info: ReceiptInfo, date: java.time.LocalDate, txs: List<com.moneyflow.data.model.TransactionEntity>, zone: java.time.ZoneId) =
    txs.filter { tx ->
      tx.type == com.moneyflow.data.model.TransactionType.EXPENSE && tx.amount == info.total && !tx.memo.contains(" · ") &&
        kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, java.time.Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate())) <= 1
    }.minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate())) }

  /** "스타벅스" + items → "스타벅스 · 아메리카노 외 1건" (the shop name stays first). */
  fun newMemo(memo: String, info: ReceiptInfo): String {
    if (info.items.isEmpty()) return memo
    val summary = if (info.items.size == 1) info.items.first() else "${info.items.first()} 외 ${info.items.size - 1}건"
    return "${memo.ifBlank { info.merchant }} · $summary".take(90)
  }
}
