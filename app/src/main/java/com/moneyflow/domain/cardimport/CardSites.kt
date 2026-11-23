package com.moneyflow.domain.cardimport

/** Card company websites opened in the in-app browser. [provider] matches account bank names. */
data class CardSite(val id: String, val name: String, val url: String, val provider: String)

object CardSites {
  val all = listOf(
    CardSite("shinhan", "신한카드", "https://www.shinhancard.com", "신한카드"),
    CardSite("kb", "KB국민카드", "https://card.kbcard.com", "KB국민카드"),
    CardSite("hyundai", "현대카드", "https://www.hyundaicard.com", "현대카드"),
    CardSite("samsung", "삼성카드", "https://www.samsungcard.com", "삼성카드"),
    CardSite("lotte", "롯데카드", "https://www.lottecard.co.kr", "롯데카드"),
    CardSite("woori", "우리카드", "https://www.wooricard.com", "우리카드"),
    CardSite("hana", "하나카드", "https://www.hanacard.co.kr", "하나카드"),
    CardSite("nh", "NH농협카드", "https://card.nonghyup.com", "NH농협카드"),
    CardSite("bc", "BC카드", "https://www.bccard.com", "BC카드"),
    CardSite("ibk", "IBK기업은행 카드", "https://www.ibk.co.kr", "IBK기업은행")
  )

  fun byId(id: String): CardSite? = all.firstOrNull { it.id == id }
}
