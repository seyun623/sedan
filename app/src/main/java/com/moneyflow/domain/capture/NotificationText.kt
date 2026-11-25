package com.moneyflow.domain.capture

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat

/** What a notification says, as plain strings (pure; built from a Notification for testing). */
data class NotificationMessage(val title: String, val text: String, val time: Long)

object NotificationText {
  /**
   * The messages in a notification. Chat apps (KakaoTalk, Samsung Messages, Google Messages) use
   * MessagingStyle, where the plain text may only say "새 메시지 2개": each message is read on its
   * own, and only those newer than [since] (the same chat is posted again for every new message).
   */
  fun messages(n: Notification, since: Long): List<NotificationMessage> {
    val e: Bundle = n.extras ?: return emptyList()
    val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
    if (style != null && style.messages.isNotEmpty()) {
      val chat = style.conversationTitle?.toString()
      return style.messages.filter { it.timestamp > since }.mapNotNull { m ->
        val text = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val sender = m.person?.name?.toString()
        NotificationMessage(chat ?: sender ?: title, text, m.timestamp)
      }
    }
    val text = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
      ?: e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")?.takeIf { it.isNotBlank() }
      ?: e.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
      ?: return emptyList()
    return listOf(NotificationMessage(title, text, 0L))
  }

  /** A group chat (KakaoTalk 단톡방): never a card company's alert. */
  fun isGroupChat(n: Notification): Boolean {
    val e = n.extras ?: return false
    return e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
      !e.getCharSequence(Notification.EXTRA_SUB_TEXT).isNullOrBlank() && e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null
  }

  /** Previews are hidden ("새 메시지가 있습니다"): nothing to read. */
  fun looksHidden(text: String): Boolean =
    Regex("^(새\\s*)?메시지(가)?\\s*(있|도착|\\d+\\s*개)|^새 메시지 \\d+개|내용을 보려면|잠금").containsMatchIn(text.trim())

  /** Extra a test notification from the app itself carries, so the listener reads it (as a test). */
  const val TEST_MARKER = "com.moneyflow.capture.TEST"
}
