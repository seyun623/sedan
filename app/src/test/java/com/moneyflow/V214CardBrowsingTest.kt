package com.moneyflow

import com.moneyflow.domain.cardimport.CardBrowsing
import com.moneyflow.domain.cardimport.CardBrowsing.LinkAction
import com.moneyflow.domain.cardimport.CardSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

class V214CardBrowsingTest {

  @Test fun desktopUserAgentKeepsChromeVersion() {
    val phone = "Mozilla/5.0 (Linux; Android 14; SM-S918N Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"
    val pc = CardBrowsing.desktopUserAgent(phone)
    assertTrue(pc.contains("Windows NT 10.0"))
    assertTrue(pc.contains("Chrome/131.0.6778.135"))
    assertTrue(!pc.contains("Mobile") && !pc.contains("Android"))
    assertTrue(!CardBrowsing.mobileUserAgent(phone).contains("; wv"))
  }

  @Test fun webPagesStayAppsOnlyWhenTapped() {
    assertEquals(LinkAction.LOAD_IN_PAGE, CardBrowsing.linkAction("https://www.hanacard.co.kr/login", userTapped = false))
    // 하나카드-style automatic switch to the app: stopped.
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("intent://main#Intent;scheme=hanaskcard;package=com.hanaskcard.paycla;end", userTapped = false))
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("market://details?id=com.hanaskcard.paycla", userTapped = false))
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("https://play.google.com/store/apps/details?id=x", userTapped = false))
    // The user pressed "앱카드로 인증": open the app.
    assertEquals(LinkAction.OPEN_APP, CardBrowsing.linkAction("kb-acp://auth?x=1", userTapped = true))
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("javascript:void(0)", userTapped = true))
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("file:///sdcard/x", userTapped = true))
  }

  @Test fun appRedirectorsAndDeclinedKinds() {
    // 롯데카드 opens its app through an onelink.me page.
    assertEquals(LinkAction.BLOCK, CardBrowsing.linkAction("https://lottecard.onelink.me/abc", userTapped = false))
    assertEquals("lottecard.onelink.me", CardBrowsing.appLinkKind("https://lottecard.onelink.me/abc"))
    assertEquals("oneqpay", CardBrowsing.appLinkKind("oneqpay://webview?x=1"))
    assertEquals("hanaskcard", CardBrowsing.appLinkKind("intent://main#Intent;scheme=hanaskcard;package=com.x;end"))
    val merged = CardBrowsing.withKnownPages(
      listOf("카드 이용내역" to "https://m.hanacard.co.kr/MKCAPV1010M.web"),
      listOf(CardBrowsing.FoundLink("이용내역", "https://m.hanacard.co.kr/MKCAPV1010M.web", "mf0"), CardBrowsing.FoundLink("명세서", "", "mf1"))
    )
    assertEquals(listOf("📌 카드 이용내역", "명세서"), merged.map { it.text })
    assertEquals("", merged.first().id)
  }

  @Test fun appLoginCode() {
    assertEquals("1234567", CardBrowsing.findAppCode("현대카드 앱에서 아래 인증번호를 입력해 주세요\n123 4567\n남은 시간 2:58"))
    assertEquals("7654321", CardBrowsing.findAppCode("모니모 앱 실행 후 결제코드 7654321 을 입력하세요"))
    assertEquals("1234567", CardBrowsing.findAppCode("SOL페이 앱에 코드를 입력하세요 1 2 3 4 5 6 7"))
    // Phone numbers, amounts and plain text are not codes.
    assertNull(CardBrowsing.findAppCode("앱 설치 문의 고객센터 번호 1588 8100"))
    assertNull(CardBrowsing.findAppCode("앱카드 이용금액 1,234,567원"))
    assertNull(CardBrowsing.findAppCode("코드 1234567"))
    assertNull(CardBrowsing.findAppCode("앱으로 로그인 번호 12345678"))
  }

  @Test fun loginAdviceFollowsTheSiteCheck() {
    // PC page by default where it logs in more easily (app code / 간편인증); 우리·KB stay on the phone page.
    assertEquals(setOf("hyundai", "lotte", "hana"), CardSites.all.filter { it.desktopDefault }.map { it.id }.toSet())
    assertTrue(CardSites.byId("woori")!!.loginTip!!.contains("보안프로그램"))
    assertTrue(CardSites.all.filter { it.loginTip != null }.all { it.appNames.isNotEmpty() })
  }

  @Test fun intentFallbackUrl() {
    val url = "intent://open#Intent;scheme=hanacard;package=com.hana;S.browser_fallback_url=https%3A%2F%2Fm.hanacard.co.kr%2Fweb;end"
    assertEquals("https://m.hanacard.co.kr/web", CardBrowsing.fallbackUrl(url))
    assertNull(CardBrowsing.fallbackUrl("intent://open#Intent;scheme=x;S.browser_fallback_url=http%3A%2F%2Fevil.com;end"))
    assertNull(CardBrowsing.fallbackUrl("hanacard://open"))
  }

  @Test fun foundLinksAreRankedAndFiltered() {
    val json = """[
      {"t":"카드 혜택 안내","u":"https://x/benefit","id":"mf0"},
      {"t":"  실적 조회 ","u":"","id":"mf1"},
      {"t":"이용대금명세서","u":"https://x/bill","id":"mf2"},
      {"t":"이용내역 조회","u":"https://x/use","id":"mf3"},
      {"t":"이용내역 조회","u":"https://x/use","id":"mf4"},
      {"t":"해외이용내역 안내","u":"","id":"mf5"},
      {"t":"승인내역","u":"","id":"mf6"},
      {"t":"결제예정금액","u":"","id":"mf7"},
      {"t":"명세서 신청·변경","u":"","id":"mf8"}
    ]"""
    val links = CardBrowsing.parseFoundLinks(json)
    assertEquals(listOf("이용내역 조회", "승인내역", "이용대금명세서", "결제예정금액", "실적 조회"), links.map { it.text })
    assertEquals("mf3", links.first().id)
    assertTrue(CardBrowsing.parseFoundLinks("not json").isEmpty())
    assertTrue(CardBrowsing.clickFoundJs("mf3\"];alert(1)//").contains("data-mf-find=\"mf3alert1\""))
  }

  @Test fun downloadNames() {
    assertEquals("이용내역.xlsx", CardBrowsing.downloadFileName("attachment; filename*=UTF-8''%EC%9D%B4%EC%9A%A9%EB%82%B4%EC%97%AD.xlsx", "https://x/d", null))
    assertEquals("use.xls", CardBrowsing.downloadFileName("attachment; filename=\"use.xls\"", "https://x/d", null))
    assertEquals("download.csv", CardBrowsing.downloadFileName(null, "https://x/download?id=3", "text/csv"))
    assertEquals("카드내역", CardBrowsing.downloadFileName(null, "https://x/", null))
  }

  @Test fun hyundaiOpensAsPcPage() {
    assertTrue(CardSites.byId("hyundai")!!.desktopDefault)
    assertTrue(CardSites.all.none { it.desktopDefault && it.loginTip == null })
  }
}
