package com.moneyflow

import com.moneyflow.ai.AiRequestException
import com.moneyflow.ai.GoogleVisionClient
import com.moneyflow.ai.HttpTransport
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.receipt.ReceiptInfo
import com.moneyflow.domain.receipt.ReceiptMatch
import com.moneyflow.domain.receipt.ReceiptParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** 3.2: 영수증 자동 인식 + 구글 Vision. */
@RunWith(RobolectricTestRunner::class)
class V320ReceiptTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

  @Test fun visionTextIsParsed() = runBlocking {
    val body = """{"responses":[{"fullTextAnnotation":{"text":"이마트 성수점\n합계 9,460\n2026-11-25"}}]}"""
    val client = GoogleVisionClient().apply { transport = HttpTransport { _, _, h, _ -> assertEquals("KEY", h["x-goog-api-key"]); 200 to body } }
    val text = client.readText("KEY", "AAAA")
    assertEquals(9_460L, ReceiptParser(LocalDate.of(2026, 11, 27)).parse(text)!!.total)
  }

  @Test fun visionErrorsSayWhatToDo() = runBlocking {
    val billing = GoogleVisionClient().apply { transport = HttpTransport { _, _, _, _ -> 403 to """{"error":{"message":"This API method requires billing to be enabled."}}""" } }
    val e = runCatching { billing.verify("KEY") }.exceptionOrNull()
    assertTrue(e is AiRequestException && e.message!!.contains("결제"))
    // An answer about the tiny test image still means the key works.
    val ok = GoogleVisionClient().apply { transport = HttpTransport { _, _, _, _ -> 200 to """{"responses":[{"error":{"message":"Bad image data"}}]}""" } }
    ok.verify("KEY")
  }

  @Test fun onlyReceiptLikeTextIsRead() {
    assertTrue(ReceiptParser.looksLikeReceipt("스타벅스\n합계 5,600\n신용카드 승인번호 1234"))
    assertFalse(ReceiptParser.looksLikeReceipt("생일 축하해\n2026년 11월"))
    assertFalse(ReceiptParser.looksLikeReceipt("메뉴판 아메리카노 4,500"))
  }

  @Test fun itemsGoOnTheCardPaymentAlreadyRecorded() {
    val day = LocalDate.of(2026, 11, 25)
    val info = ReceiptInfo(9_460, "이마트", day, listOf("서울우유", "대파"))
    val card = TransactionEntity(id = 7, type = TransactionType.EXPENSE, amount = 9_460, category = "생활/마트", accountId = 1, timestamp = at(day), memo = "이마트 성수점")
    val other = card.copy(id = 8, amount = 9_000)
    val far = card.copy(id = 9, timestamp = at(day.plusDays(3)))
    assertEquals(7L, ReceiptMatch.find(info, day, listOf(other, far, card), zone)!!.id)
    assertEquals("이마트 성수점 · 서울우유 외 1건", ReceiptMatch.newMemo(card.memo, info))
    // Already has product names: not matched again.
    assertNull(ReceiptMatch.find(info, day, listOf(card.copy(memo = "이마트 · 우유")), zone))
  }
}
