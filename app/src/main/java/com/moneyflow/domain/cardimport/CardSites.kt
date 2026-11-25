package com.moneyflow.domain.cardimport

/**
 * Card company websites opened in the in-app browser. [provider] matches account bank names.
 * [pcUrl] is opened in "PC 화면" mode ([desktopDefault]: sites whose phone page can't log in).
 * [guide] tells where the usage list is, shown when the menu can't be found automatically.
 */
data class CardSite(
  val id: String,
  val name: String,
  val url: String,
  val provider: String,
  val pcUrl: String = url,
  val desktopDefault: Boolean = false,
  val guide: String? = null,
  /** Known usage pages of the phone site (title, address), offered in "이용내역 메뉴 찾기". */
  val pages: List<Pair<String, String>> = emptyList()
)

object CardSites {
  val all = listOf(
    CardSite("shinhan", "신한카드", "https://www.shinhancard.com", "신한카드"),
    CardSite(
      "kb", "KB국민카드", "https://m.kbcard.com", "KB국민카드", pcUrl = "https://card.kbcard.com",
      pages = listOf(
        "카드 이용내역" to "https://m.kbcard.com/MKB/DVIEW/KMBM0001",
        "결제예정금액" to "https://m.kbcard.com/MKB/DVIEW/MMBMCXHIAMKC0016",
        "이용실적·혜택" to "https://m.kbcard.com/MKB/DVIEW/MMBMCXHIABNSD0006"
      )
    ),
    // The phone site can't log in; the PC site can (with the number from the 현대카드 app).
    CardSite("hyundai", "현대카드", "https://www.hyundaicard.com", "현대카드", desktopDefault = true, guide = "PC 화면에서 로그인 → My Account → 이용내역"),
    CardSite("samsung", "삼성카드", "https://www.samsungcard.com", "삼성카드"),
    CardSite(
      "lotte", "롯데카드", "https://m.lottecard.co.kr/app/LPMAIAA_V100.lc", "롯데카드", pcUrl = "https://www.lottecard.co.kr",
      guide = "로그인 → MY → 이용내역(매출전표). 화면이 눌리지 않으면 ⋮ → '화면 가림막 걷기' 또는 'PC 화면으로 보기'"
    ),
    CardSite("woori", "우리카드", "https://m.wooricard.com", "우리카드", pcUrl = "https://www.wooricard.com"),
    // The phone home page first tries to open the 하나Pay app; its main page doesn't.
    CardSite(
      "hana", "하나카드", "https://m.hanacard.co.kr/MKMAIN1010M.web", "하나카드", pcUrl = "https://www.hanacard.co.kr",
      pages = listOf(
        "카드 이용내역" to "https://m.hanacard.co.kr/MKCAPV1010M.web",
        "명세서" to "https://m.hanacard.co.kr/MKSTMT1010M.web"
      )
    ),
    CardSite("nh", "NH농협카드", "https://card.nonghyup.com", "NH농협카드"),
    CardSite(
      "bc", "BC카드", "https://m.bccard.com/app/mobileweb/index.do", "BC카드", pcUrl = "https://www.bccard.com",
      pages = listOf(
        "카드 승인내역" to "https://m.bccard.com/app/mobileweb/CardUseInq.do",
        "실적 충족 현황" to "https://m.bccard.com/app/mobileweb/CardAchvInq.do"
      )
    ),
    CardSite("ibk", "IBK기업은행 카드", "https://www.ibk.co.kr", "IBK기업은행", guide = "카드 이용내역은 은행 홈페이지의 카드 메뉴 안에 있어요")
  )

  fun byId(id: String): CardSite? = all.firstOrNull { it.id == id }
}
