package com.moneyflow.ui.transaction

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text of a receipt photo on the phone (ML Kit, the Korean model is inside the app:
 * the photo is never sent anywhere).
 */
object ReceiptScanner {
  /** Where the camera app saves the photo (removed after reading). */
  fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  fun clearPhotos(context: Context) {
    File(context.cacheDir, "receipts").listFiles()?.forEach { it.delete() }
  }

  /**
   * The photo made small enough to read reliably (phone cameras take 12–200 MP photos, which can
   * fail or run out of memory) and the rotation the camera saved. Null when it can't be opened.
   */
  fun loadScaled(context: Context, uri: Uri, maxSide: Int = 2_000): Pair<android.graphics.Bitmap, Int>? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rotation = runCatching {
      resolver.openInputStream(uri)?.use { s ->
        when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
          android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
          android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
          android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 0
        }
      }
    }.getOrNull() ?: 0
    return bitmap to rotation
  }

  /** JPEG (upright, at most 1600 px) for reading with AI. */
  fun jpegForAi(bitmap: android.graphics.Bitmap, rotation: Int): ByteArray {
    val upright = if (rotation == 0) bitmap else android.graphics.Bitmap.createBitmap(
      bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true
    )
    val scale = 1_600f / maxOf(upright.width, upright.height)
    val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true) else upright
    return java.io.ByteArrayOutputStream().use { out -> small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }
  }

  suspend fun read(bitmap: android.graphics.Bitmap, rotation: Int): String {
    val image = InputImage.fromBitmap(bitmap, rotation)
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    try {
      return suspendCancellableCoroutine { cont ->
        recognizer.process(image)
          .addOnSuccessListener { result ->
            // Lines in reading order (top to bottom) so "합계" and its amount stay together.
            val lines = result.textBlocks.flatMap { it.lines }
              .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
            cont.resume(mergeRows(lines.map { Triple(it.text, it.boundingBox?.centerY() ?: 0, it.boundingBox?.height() ?: 0) }))
          }
          .addOnFailureListener { cont.resumeWithException(it) }
      }
    } finally {
      recognizer.close()
    }
  }

  /** Puts text pieces on the same row (receipt columns: "합계   15,000") back on one line. */
  internal fun mergeRows(pieces: List<Triple<String, Int, Int>>): String {
    val rows = mutableListOf<MutableList<Triple<String, Int, Int>>>()
    for (p in pieces) {
      val row = rows.lastOrNull()
      if (row != null && kotlin.math.abs(row.first().second - p.second) <= maxOf(row.first().third, p.third) / 2) row += p
      else rows += mutableListOf(p)
    }
    return rows.joinToString("\n") { r -> r.joinToString(" ") { it.first } }
  }
}
