package com.moneyflow.work

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.alerts.Reminder
import com.moneyflow.domain.alerts.ReminderChannel
import com.moneyflow.domain.capture.FormPrefill
import com.moneyflow.domain.receipt.ReceiptMatch
import com.moneyflow.domain.receipt.ReceiptReading
import com.moneyflow.util.Formatters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 영수증 자동 인식 (3.2): when a new camera photo appears, checks on the phone whether it is a
 * receipt; if so reads it and either adds the product names to the card payment already recorded
 * (same amount, same day) or offers a filled-in form in a notification. Other photos are skipped
 * and never leave the phone.
 */
@HiltWorker
class ReceiptWatchWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val reading: ReceiptReading,
  private val settings: SettingsRepository,
  private val db: AppDatabase,
  private val ledger: LedgerRepository,
  private val clock: AppClock
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val s = settings.current()
    if (!s.receiptAutoEnabled || !hasPhotoAccess(applicationContext)) return Result.success()
    try {
      val photos = newCameraPhotos(s.receiptLastScan)
      photos.maxOfOrNull { it.added }?.let { settings.setReceiptLastScan(it) }
      for (photo in photos.take(MAX_PER_RUN)) handle(photo)
    } catch (e: Exception) {
      com.moneyflow.util.ProblemLog.record("영수증 자동 인식", e)
    } finally {
      // A content trigger fires once: wait for the next new photo.
      schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }
    return Result.success()
  }

  private data class Photo(val id: Long, val added: Long, val taken: Long)

  private suspend fun handle(photo: Photo) {
    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo.id)
    if (!reading.looksLikeReceipt(uri)) return
    val takenDate = MonthKeys.toLocalDate(if (photo.taken > 0) photo.taken else photo.added * 1000, clock.zone)
    val outcome = reading.read(uri, clock.today())
    val key = "receipt|${photo.id}"
    if (outcome !is ReceiptReading.Outcome.Read) {
      post(Reminder(key, ReminderChannel.RECEIPT, "🧾 영수증을 읽지 못했어요", "눌러서 직접 입력하거나, 내역 추가에서 사진을 다시 골라 주세요."), FormPrefill())
      return
    }
    val info = outcome.result.info
    val date = info.date ?: takenDate
    val from = MonthKeys.dayStartMillis(date.minusDays(1), clock.zone)
    val to = MonthKeys.dayStartMillis(date.plusDays(2), clock.zone) - 1
    val match = ReceiptMatch.find(info, date, db.transactionDao().getBetween(from, to), clock.zone)
    if (match != null && info.items.isNotEmpty()) {
      val memo = ReceiptMatch.newMemo(match.memo, info)
      ledger.updateTransaction(match.copy(memo = memo))
      post(Reminder(key, ReminderChannel.RECEIPT, "🧾 영수증 품목을 붙였어요", "$memo ${Formatters.formatCurrency(info.total)} (이미 기록된 카드 결제)"), null)
      return
    }
    if (match != null) return // Already recorded and nothing to add.
    val time = clock.nowMillis().let { now ->
      date.atTime(java.time.Instant.ofEpochMilli(now).atZone(clock.zone).toLocalTime()).atZone(clock.zone).toInstant().toEpochMilli()
    }
    post(
      Reminder(key, ReminderChannel.RECEIPT, "🧾 ${info.merchant.ifEmpty { "영수증" }} ${Formatters.formatCurrency(info.total)}",
        "${date.monthValue}/${date.dayOfMonth} · ${info.memo} · 눌러서 확인하고 저장하세요 (${outcome.result.by}로 읽음)"),
      FormPrefill(amount = info.total, memo = info.memo, timestamp = time)
    )
  }

  private fun post(r: Reminder, prefill: FormPrefill?) {
    Notifications.post(applicationContext, r) { prefill?.let { PrefillIntents.put(applicationContext, this, it) } }
  }

  private fun newCameraPhotos(sinceSeconds: Long): List<Photo> {
    val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_TAKEN)
    val selection = "${MediaStore.Images.Media.DATE_ADDED} > ? AND $pathColumn LIKE ?"
    val args = arrayOf(sinceSeconds.toString(), if (Build.VERSION.SDK_INT >= 29) "DCIM/%" else "%/DCIM/%")
    val out = mutableListOf<Photo>()
    applicationContext.contentResolver.query(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { c ->
      while (c.moveToNext() && out.size < 20) out += Photo(c.getLong(0), c.getLong(1), c.getLong(2))
    }
    return out
  }

  companion object {
    private const val NAME = "receipt_watch"
    private const val MAX_PER_RUN = 5

    val PERMISSION: String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPhotoAccess(context: Context) = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
      val constraints = Constraints.Builder()
        .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
        .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
        .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, OneTimeWorkRequestBuilder<ReceiptWatchWorker>().setConstraints(constraints).build())
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
  }
}
