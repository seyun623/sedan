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
  val pages: List<Pair<String, String>> = emptyList(),
  /** The easiest way to log in on this site from a phone (shown over the page). */
  val loginTip: String? = null,
  /** Names of the card company's app (launcher label) for "코드 복사하고 앱 열기", best first. */
  val appNames: List<String> = emptyList(),
  /** The PC page can't log in without Windows security programs: always use the phone page. */
  val pcBlocked: Boolean = false,
  /** What the website can't do (checked on a real phone); shown in the card company list. */
  val webLimit: String? = null
)

object CardSites {
  // Login methods checked on each site's login page (2026-11-25): most offer a 7-digit code that
  // is typed into the card company's app — the easiest way from a phone.
  val all = listOf(
    CardSite(
      "shinhan", "신한카드", "https://www.shinhancard.com", "신한카드",
      loginTip = "'SOL페이' 로그인을 고르면 7자리 코드가 나와요 → 신한 SOL페이 앱에 입력하고 돌아오세요 (10분). 아이디·비밀번호 직접 입력도 돼요. 보안프로그램은 '설치 안 함'",
      appNames = listOf("SOL페이", "신한카드")
    ),
    CardSite(
      "kb", "KB국민카드", "https://m.kbcard.com", "KB국민카드", pcUrl = "https://card.kbcard.com",
      pages = listOf(
        "카드 이용내역" to "https://m.kbcard.com/MKB/DVIEW/KMBM0001",
        "결제예정금액" to "https://m.kbcard.com/MKB/DVIEW/MMBMCXHIAMKC0016",
        "이용실적·혜택" to "https://m.kbcard.com/MKB/DVIEW/MMBMCXHIABNSD0006"
      ),
      loginTip = "휴대폰 화면에서 아이디, KB Pay 번호, 카드(문자) 인증 중 편한 걸로 로그인하세요. PC 화면은 보안프로그램 설치를 요구해서 안 돼요",
      appNames = listOf("KB Pay", "KB국민카드"),
      pcBlocked = true
    ),
    // The phone site can't log in; the PC site can (with the number from the 현대카드 app).
    CardSite(
      "hyundai", "현대카드", "https://www.hyundaicard.com", "현대카드", desktopDefault = true,
      guide = "PC 화면에서 로그인 → My Account → 이용내역",
      loginTip = "'현대카드 앱' 로그인을 고르면 7자리 숫자가 나와요 → 현대카드 앱에 입력하고 돌아오세요 (3분 안에)",
      appNames = listOf("현대카드")
    ),
    CardSite(
      "samsung", "삼성카드", "https://www.samsungcard.com", "삼성카드",
      loginTip = "'모니모페이' 로그인을 고르면 7자리 코드가 나와요 → 모니모 앱에 입력하고 돌아오세요 (5분). 아이디·비밀번호 직접 입력도 돼요. 보안프로그램은 '설치 안 함'",
      appNames = listOf("모니모", "monimo", "삼성카드")
    ),
    // The phone page has a layer that blocks touches; the PC page logs in with the LOCA app code.
    CardSite(
      "lotte", "롯데카드", "https://m.lottecard.co.kr/app/LPMAIAA_V100.lc", "롯데카드", pcUrl = "https://www.lottecard.co.kr",
      desktopDefault = true,
      guide = "로그인 → MY → 이용내역(매출전표). 화면이 눌리지 않으면 ⋮ → '가림막 걷기'",
      loginTip = "'로카페이' 로그인을 고르면 7자리 코드가 나와요 → 로카(LOCA) 앱에 입력하고 돌아오세요 (5분). 아이디는 가상키패드만 돼요",
      appNames = listOf("LOCA", "로카", "롯데카드")
    ),
    // PC 홈페이지는 보안프로그램 4종이 없으면 로그인 화면이 열리지 않아요.
    CardSite(
      "woori", "우리카드", "https://m.wooricard.com", "우리카드", pcUrl = "https://www.wooricard.com",
      loginTip = "PC 화면은 보안프로그램 때문에 로그인이 안 돼요. 휴대폰 화면에서 우리WON페이 코드나 아이디로 로그인해 주세요",
      appNames = listOf("우리WON카드", "우리WON페이", "우리카드"),
      pcBlocked = true,
      webLimit = "웹에서는 이용내역을 보여 주지 않아요 (앱 전용) → 결제 알림 자동 등록을 써 주세요"
    ),
    // The phone page (its main page, not the root that opens the 하나Pay app) is the default; the PC
    // page offers 토스·네이버 간편인증 when needed.
    CardSite(
      "hana", "하나카드", "https://m.hanacard.co.kr/MKMAIN1010M.web", "하나카드", pcUrl = "https://www.hanacard.co.kr",
      pages = listOf(
        "카드 이용내역" to "https://m.hanacard.co.kr/MKCAPV1010M.web",
        "명세서" to "https://m.hanacard.co.kr/MKSTMT1010M.web"
      ),
      loginTip = "하나Pay 7자리 코드나 아이디로 로그인하세요. 토스·네이버 간편인증을 쓰려면 ⋮ → 'PC 화면으로 보기'",
      appNames = listOf("하나Pay", "하나카드")
    ),
    CardSite(
      "nh", "NH농협카드", "https://card.nonghyup.com", "NH농협카드", appNames = listOf("NH pay", "NH농협카드"),
      pcBlocked = true,
      webLimit = "웹에서는 이용내역을 보여 주지 않아요 (NH pay 앱 전용) → 결제 알림 자동 등록을 써 주세요"
    ),
    CardSite(
      "bc", "BC카드", "https://m.bccard.com/app/mobileweb/index.do", "BC카드", pcUrl = "https://www.bccard.com",
      pages = listOf(
        "카드 승인내역" to "https://m.bccard.com/app/mobileweb/CardUseInq.do",
        "실적 충족 현황" to "https://m.bccard.com/app/mobileweb/CardAchvInq.do"
      ),
      appNames = listOf("페이북", "BC카드")
    ),
    CardSite(
      "ibk", "IBK기업은행 카드", "https://www.ibk.co.kr", "IBK기업은행",
      guide = "로그인 → 카드 → 나의카드관리 → 카드정보관리 → '이용내역' 칸의 바로가기",
      webLimit = "은행 인터넷뱅킹 로그인(인증서)이 필요해요"
    )
  )

  /**
   * Card product search (no login): benefits are listed in detail for every card, usually more
   * than the card company's own screen after login. Read with "이 화면 가져오기" → "AI로 읽기".
   */
  val cardGorilla = CardSite(
    "cardgorilla", "카드고릴라 (카드 혜택 검색)", "https://www.card-gorilla.com", "",
    loginTip = "위 검색에서 카드 이름을 찾아 카드 상세 화면을 연 뒤, 아래 '이 화면 가져오기' → 'AI로 읽기'를 누르세요"
  )

  fun byId(id: String): CardSite? = all.firstOrNull { it.id == id } ?: cardGorilla.takeIf { it.id == id }
}
