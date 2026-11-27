package com.moneyflow.domain

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.moneyflow.domain.capture.CaptureLog
import com.moneyflow.domain.capture.FinancialApps
import com.moneyflow.domain.capture.NotificationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationCaptureTest {
  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  private fun chat(title: String, vararg msgs: Pair<String, Long>, group: Boolean = false): Notification {
    val me = Person.Builder().setName("나").build()
    val style = NotificationCompat.MessagingStyle(me).setGroupConversation(group)
    if (group) style.setConversationTitle("가족방")
    msgs.forEach { (text, t) -> style.addMessage(text, t, Person.Builder().setName(title).build()) }
    return NotificationCompat.Builder(ctx, "c").setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title).setContentText("새 메시지 ${msgs.size}개").setStyle(style).build()
  }

  @Test fun messagingStyleIsReadMessageByMessage() {
    val n = chat("신한카드", "신한카드 승인 12,500원" to 1_000L, "신한카드 승인 3,000원" to 2_000L)
    val all = NotificationText.messages(n, since = 0)
    assertEquals(listOf("신한카드 승인 12,500원", "신한카드 승인 3,000원"), all.map { it.text })
    assertEquals("신한카드", all.first().title)
    // The same chat posted again: only the new message.
    assertEquals(listOf("신한카드 승인 3,000원"), NotificationText.messages(n, since = 1_000L).map { it.text })
  }

  @Test fun bigTextAndGroupChats() {
    val big = NotificationCompat.Builder(ctx, "c").setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("1544-7200").setContentText("[Web발신] 신한카드…")
      .setStyle(NotificationCompat.BigTextStyle().bigText("[Web발신]\n신한카드(1234)승인 12,500원")).build()
    assertEquals("[Web발신]\n신한카드(1234)승인 12,500원", NotificationText.messages(big, 0).single().text)
    assertTrue(NotificationText.isGroupChat(chat("엄마", "카드 승인 5,000원" to 1L, group = true)))
    assertFalse(NotificationText.isGroupChat(chat("신한카드", "승인" to 1L)))
    assertTrue(NotificationText.looksHidden("새 메시지가 있습니다"))
    assertFalse(NotificationText.looksHidden("신한카드 승인 12,500원"))
  }

  @Test fun gates() {
    assertEquals(FinancialApps.Gate.KAKAO_CHANNEL, FinancialApps.gate("com.kakao.talk", "카카오톡", "KB국민카드"))
    assertEquals(FinancialApps.Gate.BLOCKED, FinancialApps.gate("com.kakao.talk", "카카오톡", "엄마"))
    assertEquals(FinancialApps.Gate.SMS_APP, FinancialApps.gate("com.samsung.android.messaging", "메시지", "1544-7200"))
    assertEquals(FinancialApps.Gate.BLOCKED, FinancialApps.gate("org.telegram.messenger", "Telegram", "신한카드"))
    assertTrue(FinancialApps.sameCompany("KB국민카드", "KB국민카드"))
    assertFalse(FinancialApps.sameCompany("삼성카드", "신한카드"))
  }

  @Test fun logHidesNumbers() {
    assertEquals("1544-****", CaptureLog.maskSender("15447200"))
    assertEquals("신한카드", CaptureLog.maskSender("신한카드"))
    assertEquals("신한카드(••••)승인 ••,•••원", CaptureLog.maskPreview("신한카드(1234)승인 12,500원"))
    val e = com.moneyflow.domain.capture.CaptureLogEntry(1L, "알림", "카카오톡", "신한카드", true, "기록됨", "x")
    assertEquals(listOf(e), CaptureLog.decode(CaptureLog.encode(listOf(e))))
  }
}
