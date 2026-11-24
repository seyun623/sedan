package com.moneyflow.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneyflow.MainActivity
import com.moneyflow.R
import com.moneyflow.domain.alerts.Reminder
import com.moneyflow.domain.alerts.ReminderChannel

/** Posts app notifications. */
object Notifications {
  fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  fun ensureChannel(context: Context, channel: ReminderChannel) {
    if (Build.VERSION.SDK_INT >= 26) {
      context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(channel.id, channel.label, NotificationManager.IMPORTANCE_DEFAULT))
    }
  }

  /** Opens the app; [extras] are handed to MainActivity (e.g. to open a prefilled form). */
  fun post(context: Context, reminder: Reminder, extras: Intent.() -> Unit = {}): Boolean {
    if (!canNotify(context)) return false
    ensureChannel(context, reminder.channel)
    val intent = Intent(context, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .apply(extras)
    val open = PendingIntent.getActivity(
      context, reminder.key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val n = NotificationCompat.Builder(context, reminder.channel.id)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(reminder.title)
      .setContentText(reminder.text.lineSequence().first())
      .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
      .setContentIntent(open)
      .setAutoCancel(true)
      // On the lock screen only "a notification" shows; amounts appear after unlocking the phone.
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(
        NotificationCompat.Builder(context, reminder.channel.id)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("머니플로우")
          .setContentText(reminder.channel.label)
          .build()
      )
      .build()
    return try {
      NotificationManagerCompat.from(context).notify(reminder.key.hashCode(), n)
      true
    } catch (_: SecurityException) {
      false
    }
  }
}
