package com.moneyflow.domain.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.moneyflow.ai.GoogleVisionClient
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.security.SecretStore
import com.moneyflow.ui.transaction.ReceiptScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A read receipt and who read it ("휴대폰", "구글 Vision", "Gemini"). */
data class ReceiptRead(val info: ReceiptInfo, val by: String)

/**
 * Reads a receipt photo, cheapest and most private first:
 * 1. the phone's text reading (ML Kit), 2. the same on a sharpened black-and-white copy,
 * 3. Google Cloud Vision (when a key is set), 4. Gemini (when the AI is set up).
 * Steps 3–4 send the photo out, and only run when [allowNetwork].
 */
@Singleton
class ReceiptReading @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settings: SettingsRepository,
  private val secrets: SecretStore,
  private val vision: GoogleVisionClient,
  private val ai: AiReceiptReader
) {
  /** What happened, for the message when nothing was found. */
  sealed interface Outcome {
    data class Read(val result: ReceiptRead) : Outcome
    data object CannotOpen : Outcome
    /** No total found; [hadText] = some text was there (not a receipt, or blurred). */
    data class NotFound(val hadText: Boolean, val phoneText: String, val error: String? = null) : Outcome
  }

  suspend fun read(uri: Uri, today: LocalDate, allowNetwork: Boolean = true, onStep: (String) -> Unit = {}): Outcome {
    val (bitmap, rotation) = withContext(Dispatchers.IO) { runCatching { ReceiptScanner.loadScaled(context, uri) }.getOrNull() }
      ?: return Outcome.CannotOpen
    val parser = ReceiptParser(today)
    val text = runCatching { ReceiptScanner.read(bitmap, rotation) }
      .onFailure { com.moneyflow.util.ProblemLog.record("영수증 글자 인식", it) }.getOrDefault("")
    parser.parse(text)?.let { return Outcome.Read(ReceiptRead(it, "휴대폰")) }
    // Faded thermal paper: black-and-white with more contrast often reads better.
    val sharp = withContext(Dispatchers.Default) { enhance(bitmap) }
    val text2 = runCatching { ReceiptScanner.read(sharp, rotation) }.getOrDefault("")
    parser.parse(text2)?.let { return Outcome.Read(ReceiptRead(it, "휴대폰")) }
    val phoneText = if (text2.length > text.length) text2 else text
    if (!allowNetwork) return Outcome.NotFound(phoneText.isNotBlank(), phoneText)

    var error: String? = null
    val jpeg by lazy { ReceiptScanner.jpegForAi(bitmap, rotation) }
    val s = settings.current()
    val visionKey = s.visionKeyEncrypted?.let { runCatching { secrets.decrypt(it) }.getOrNull() }
    if (visionKey != null) {
      onStep("휴대폰에서 읽지 못해 구글 Vision으로 읽는 중이에요…")
      try {
        val b64 = withContext(Dispatchers.Default) { android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP) }
        parser.parse(vision.readText(visionKey, b64))?.let { return Outcome.Read(ReceiptRead(it, "구글 Vision")) }
      } catch (e: Exception) {
        error = e.message
      }
    }
    if (ai.canRead()) {
      onStep("AI(Gemini)로 읽는 중이에요…")
      try {
        ai.read(jpeg, today)?.let { return Outcome.Read(ReceiptRead(it, "Gemini")) }
      } catch (e: Exception) {
        error = error ?: e.message
      }
    }
    return Outcome.NotFound(phoneText.isNotBlank(), phoneText, error)
  }

  /** Quick check (phone only) whether a photo's text looks like a receipt. */
  suspend fun looksLikeReceipt(uri: Uri): Boolean {
    val (bitmap, rotation) = withContext(Dispatchers.IO) { runCatching { ReceiptScanner.loadScaled(context, uri, 1_400) }.getOrNull() } ?: return false
    val text = runCatching { ReceiptScanner.read(bitmap, rotation) }.getOrDefault("")
    return ReceiptParser.looksLikeReceipt(text)
  }

  private fun enhance(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val contrast = 1.6f
    val shift = (-0.5f * contrast + 0.5f) * 255f
    val m = ColorMatrix().apply { setSaturation(0f) }
    m.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, shift, 0f, contrast, 0f, 0f, shift, 0f, 0f, contrast, 0f, shift, 0f, 0f, 0f, 1f, 0f)))
    Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
    return out
  }
}
