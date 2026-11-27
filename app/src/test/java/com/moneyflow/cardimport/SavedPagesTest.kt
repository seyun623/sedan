package com.moneyflow.cardimport

import com.moneyflow.domain.cardimport.PerformanceInfo
import com.moneyflow.domain.cardimport.SavedPage
import com.moneyflow.domain.cardimport.SavedPages
import com.moneyflow.domain.cardimport.StatementExtraction
import com.moneyflow.domain.cardimport.StatementItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SavedPagesTest {
  @Test fun sessionIdsAndTokensAreNotStored() {
    assertEquals(
      "https://www.shinhancard.com/mob/MOBFM064N/MOBFM064R01.shc?menuId=12&page=2",
      SavedPages.sanitize("https://www.shinhancard.com/mob/MOBFM064N/MOBFM064R01.shc;jsessionid=ABC123?menuId=12&token=xyz&page=2&_=1700000")
    )
    assertEquals("https://card.example.com/history#/list", SavedPages.sanitize("https://card.example.com/history?sid=9&ts=1#/list"))
    assertEquals("https://a.com/b", SavedPages.sanitize("https://a.com/b"))
  }

  @Test fun problemsWhenSaving() {
    val home = "https://www.shinhancard.com"
    assertTrue(SavedPages.problem("https://www.shinhancard.com/", home, emptyList())!!.contains("첫 화면"))
    assertTrue(SavedPages.problem("http://x.com/a", home, emptyList())!!.contains("주소"))
    assertTrue(SavedPages.problem(null, home, emptyList())!!.contains("주소"))
    val saved = listOf(SavedPage("https://www.shinhancard.com/list", "이용내역", 1))
    assertTrue(SavedPages.problem("https://www.shinhancard.com/list;jsessionid=Q", home, saved)!!.contains("이미"))
    val full = (1..SavedPages.MAX_PER_SITE).map { SavedPage("https://x.com/$it", "$it", 1) }
    assertTrue(SavedPages.problem("https://x.com/new", home, full)!!.contains("까지"))
    assertNull(SavedPages.problem("https://www.shinhancard.com/benefit", home, saved))
  }

  @Test fun encodeDecodeRoundTrip() {
    val map = mapOf("shinhan" to listOf(SavedPage("https://a.com/1", "이용내역", 5)), "kb" to emptyList())
    assertEquals(mapOf("shinhan" to map["shinhan"]), SavedPages.decode(SavedPages.encode(map)))
    assertEquals(emptyMap<String, List<SavedPage>>(), SavedPages.decode("not json"))
  }

  @Test fun mergeDropsTheSamePaymentSeenOnTwoPages() {
    val a = StatementItem(LocalDate.of(2026, 11, 23), null, "DB손해보험", 33_170)
    val b = StatementItem(LocalDate.of(2026, 11, 20), null, "쿠팡", 8_590)
    val merged = SavedPages.merge(
      listOf(
        StatementExtraction(listOf(a, b), listOf(PerformanceInfo("전월 실적", 300_000)), "x"),
        StatementExtraction(listOf(a.copy(merchant = "DB 손해보험")), listOf(PerformanceInfo("전월 실적", 300_000)), "x")
      ),
      "기기에서 읽기"
    )
    assertEquals(listOf(a, b), merged.items)
    assertEquals(1, merged.performance.size)
    assertEquals("기기에서 읽기", merged.readBy)
  }
}
