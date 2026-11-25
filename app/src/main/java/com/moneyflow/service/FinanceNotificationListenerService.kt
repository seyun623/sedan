package com.moneyflow.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.CaptureChannel
import com.moneyflow.domain.capture.FinancialApps
import com.moneyflow.domain.capture.NotificationText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads payment notifications from card / bank / pay apps, KakaoTalk card channels and the
 * text-message app, and records them. Notification text is never logged; the diagnostic log
 * keeps only a masked preview of financial messages.
 */
@AndroidEntryPoint
class FinanceNotificationListenerService : NotificationListenerService() {

  @Inject lateinit var processor: AutoCaptureProcessor

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val labelCache = HashMap<String, String?>()
  /** Newest message time already handled per notification (chat notifications are re-posted). */
  private val lastSeen = HashMap<String, Long>()

  override fun onListenerConnected() {
    super.onListenerConnected()
    status(this).edit().putLong(KEY_CONNECTED_AT, System.currentTimeMillis()).putBoolean(KEY_CONNECTED, true).apply()
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    status(this).edit().putBoolean(KEY_CONNECTED, false).apply()
    // Samsung sometimes leaves the listener unbound (after updates / memory cleanup): ask again.
    runCatching { requestRebind(ComponentName(this, FinanceNotificationListenerService::class.java)) }
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    sbn ?: return
    val sourcePackage = sbn.packageName ?: return
    val notification = sbn.notification ?: return
    val isTest = sourcePackage == applicationContext.packageName &&
      notification.extras?.getBoolean(NotificationText.TEST_MARKER, false) == true
    if (sourcePackage == applicationContext.packageName && !isTest) return

    // Group summaries and ongoing notifications (music, downloads…) are never payments.
    if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

    val label = labelCache.getOrPut(sourcePackage) { appLabel(sourcePackage) }
    if (!isTest && !FinancialApps.isAllowed(sourcePackage, label)) return
    // KakaoTalk group chats are never card alerts.
    if (sourcePackage == FinancialApps.KAKAO && NotificationText.isGroupChat(notification)) return

    val since = lastSeen[sbn.key] ?: 0L
    val messages = NotificationText.messages(notification, since)
    if (messages.isEmpty()) return
    messages.maxOfOrNull { it.time }?.takeIf { it > 0 }?.let { lastSeen[sbn.key] = it }
    if (lastSeen.size > 200) lastSeen.keys.take(100).forEach { lastSeen.remove(it) }

    for (m in messages) {
      if (NotificationText.looksHidden(m.text)) continue
      val channel = if (isTest) CaptureChannel.TEST else CaptureChannel.NOTIFICATION
      val time = if (m.time > 0) m.time else sbn.postTime
      scope.launch { processor.process(channel, sourcePackage, label, m.title, m.text, time) }
    }
  }

  private fun appLabel(pkg: String): String? = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val PREFS = "capture_listener_status"
    private const val KEY_CONNECTED = "connected"
    private const val KEY_CONNECTED_AT = "connected_at"

    private fun status(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The system has the listener running (connected since the last time it was bound). */
    fun isConnected(context: Context): Boolean = status(context).getBoolean(KEY_CONNECTED, false)

    /** Ask the system to (re)connect the listener, e.g. after an app update. */
    fun rebind(context: Context) {
      runCatching { requestRebind(ComponentName(context, FinanceNotificationListenerService::class.java)) }
    }
  }
}
