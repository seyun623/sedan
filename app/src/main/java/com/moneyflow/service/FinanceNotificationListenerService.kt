package com.moneyflow.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.CaptureChannel
import com.moneyflow.domain.capture.FinancialApps
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads payment notifications from card / bank / pay apps and records them.
 * Notification text is never logged.
 */
@AndroidEntryPoint
class FinanceNotificationListenerService : NotificationListenerService() {

  @Inject lateinit var processor: AutoCaptureProcessor

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val labelCache = HashMap<String, String?>()

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    sbn ?: return
    val sourcePackage = sbn.packageName ?: return
    if (sourcePackage == applicationContext.packageName) return

    val notification = sbn.notification ?: return
    // Group summaries and ongoing notifications (music, downloads…) are never payments.
    if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

    val label = labelCache.getOrPut(sourcePackage) { appLabel(sourcePackage) }
    if (!FinancialApps.isAllowed(sourcePackage, label)) return

    val extras = notification.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
      ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
      ?: return
    if (text.isBlank()) return

    scope.launch {
      runCatching {
        processor.process(CaptureChannel.NOTIFICATION, sourcePackage, label, title, text, sbn.postTime)
      }
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
}
