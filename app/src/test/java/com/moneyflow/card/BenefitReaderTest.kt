package com.moneyflow.card

import com.moneyflow.data.model.BenefitKind
import com.moneyflow.domain.card.BenefitReader
import com.moneyflow.domain.cardimport.AiStatementReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BenefitReaderTest {

  @Test fun parsesAndValidatesBenefits() {
    val list = BenefitReader.parse(JSONArray("""[
      {"title":"커피 10% 할인","category":"카페/간식","merchants":"스타벅스, 투썸플레이스","kind":"PERCENT","value":10,"monthlyCap":5000,"minPerformance":300000,"minAmount":0},
      {"title":"전 가맹점 0.5% 적립","category":"","merchants":"","kind":"PERCENT","value":0.5},
      {"title":"편의점 건당 500원","category":"편의점","merchants":"GS25/CU","kind":"FIXED","value":500,"minAmount":5000},
      {"title":"커피 10%  할인","category":"카페/간식","kind":"PERCENT","value":10},
      {"title":"잘못된 값","kind":"PERCENT","value":150},
      {"title":"0원","kind":"FIXED","value":0},
      {"title":"","category":"교통","kind":"PERCENT","value":7,"monthlyCap":-10}
    ]"""))
    assertEquals(listOf("커피 10% 할인", "전 가맹점 0.5% 적립", "편의점 건당 500원", "교통 7% 혜택"), list.map { it.title })
    with(list[0]) {
      assertEquals("카페/간식", category); assertEquals("스타벅스,투썸플레이스", merchantKeywords)
      assertEquals(1000, value); assertEquals(5_000, monthlyCap); assertEquals(300_000, minPerformance)
    }
    assertEquals(50, list[1].value)
    assertNull(list[1].category)
    with(list[2]) {
      assertEquals(BenefitKind.FIXED, kind); assertNull(category) // "편의점" isn't an app category
      assertEquals("GS25,CU", merchantKeywords); assertEquals(5_000, minAmount)
    }
    assertEquals(0, list[3].monthlyCap)
    assertTrue(BenefitReader.parse(null).isEmpty())
  }

  @Test fun statementReadingCarriesBenefits() {
    val r = AiStatementReader.parseResult(
      JSONObject("""{"transactions":[],"performance":[],"benefits":[{"title":"영화 5,000원","category":"문화/여가","kind":"FIXED","value":5000}]}"""),
      "Google Gemini"
    )
    assertEquals(1, r.benefits.size)
    assertTrue(!r.isEmpty)
    assertTrue(AiStatementReader.parseResult(JSONObject("""{"transactions":[],"performance":[]}"""), "x").isEmpty)
  }
}
