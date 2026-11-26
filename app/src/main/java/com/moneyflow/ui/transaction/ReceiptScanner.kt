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

  suspend fun read(context: Context, uri: Uri): String {
    val image = InputImage.fromFilePath(context, uri)
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
