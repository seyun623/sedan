package com.moneyflow.domain.capture

/**
 * 지역화폐(지역사랑상품권)·온누리상품권: prepaid money spent like a card. Recorded on a "현금"-type
 * account named after it, with the payment method "지역화폐" / "온누리상품권" (2.17), so the
 * 연말정산 preview can count 전통시장 (온누리) spending.
 */
object Vouchers {
  const val ONNURI = "온누리상품권"
  const val LOCAL = "지역화폐"

  /** (text to look for, name of the voucher) — the first match in a message wins. */
  val PROVIDERS = listOf(
    "온누리" to ONNURI,
    "경기지역화폐" to "경기지역화폐", "인천e음" to "인천e음", "인천이음" to "인천e음", "동백전" to "부산 동백전",
    "온통대전" to "대전 온통대전", "여민전" to "세종 여민전", "탐나는전" to "제주 탐나는전", "광주상생카드" to "광주상생카드",
    "대구로페이" to "대구로페이", "울산페이" to "울산페이", "서울사랑상품권" to "서울사랑상품권",
    "지역사랑상품권" to "지역사랑상품권", "지역화폐" to LOCAL
  )

  /** App labels of voucher apps (chak, 지역화폐 apps of each city …). */
  val LABEL = Regex("지역화폐|지역사랑|온누리|상품권|e음|동백전|온통대전|여민전|탐나는전|상생카드|대구로|울산페이|chak", RegexOption.IGNORE_CASE)

  fun isVoucher(name: String?): Boolean = name != null && (LABEL.containsMatchIn(name) || PROVIDERS.any { name.contains(it.first) })

  /** The payment method shown in the ledger for a voucher payment. */
  fun paymentMethod(name: String?): String = if (name?.contains("온누리") == true) ONNURI else LOCAL
}
