package com.moneyflow.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App errors kept in a small file on the phone (never sent anywhere by itself). The user can
 * look at it and send it from Settings → 문제 보고. Only error names and code locations are
 * written, plus the message of errors the app shows anyway.
 */
object ProblemLog {
  private const val FILE = "problems.log"
  private const val MAX_BYTES = 128 * 1024
  @Volatile private var dir: File? = null
  private var version = ""

  fun install(context: Context, appVersion: String) {
    dir = File(context.filesDir, "logs").apply { mkdirs() }
    version = appVersion
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      runCatching { write("앱 멈춤 (${thread.name})", e) }
      previous?.uncaughtException(thread, e)
    }
  }

  /** Records a handled error (e.g. a failed background job). */
  fun record(where: String, e: Throwable) = runCatching { write(where, e) }

  fun file(): File? = dir?.let { File(it, FILE) }

  fun read(): String = file()?.takeIf { it.exists() }?.readText().orEmpty()

  fun clear() { file()?.delete() }

  /** Text for a report: device info and the log. */
  fun report(): String = buildString {
    appendLine("머니플로우 문제 보고")
    appendLine("앱 버전: $version")
    appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    appendLine("만든 때: ${stamp()}")
    appendLine("----")
    append(read().ifEmpty { "(기록된 오류 없음)\n" })
  }

  internal fun format(where: String, e: Throwable, time: String, appVersion: String): String {
    // Error names and code locations only. Messages can contain file names, amounts or server
    // replies, so only the app's own Korean messages (already shown on screen) are kept.
    val out = StringBuilder("[$time] v$appVersion $where\n")
    var cause: Throwable? = e
    var depth = 0
    while (cause != null && depth < 4) {
      if (depth > 0) out.append("Caused by: ")
      out.append(cause.javaClass.name)
      if (isAppMessage(cause)) out.append(": ").append(cause.message!!.take(200))
      out.append('\n')
      cause.stackTrace.take(if (depth == 0) 20 else 6).forEach { out.append("\tat ").append(it).append('\n') }
      cause = cause.cause.takeIf { it !== cause }
      depth++
    }
    return out.append('\n').toString()
  }

  /** The app's own messages (written in Korean for the user), not system / server text. */
  private fun isAppMessage(e: Throwable): Boolean {
    val m = e.message ?: return false
    return e.javaClass.name.startsWith("com.moneyflow") || m.any { it in '\uAC00'..'\uD7A3' }
  }

  @Synchronized private fun write(where: String, e: Throwable) {
    val f = file() ?: return
    if (f.exists() && f.length() > MAX_BYTES) {
      val keep = f.readText().takeLast(MAX_BYTES / 2)
      val start = keep.indexOf("\n[")
      f.writeText(if (start >= 0) keep.substring(start + 1) else keep)
    }
    f.appendText(format(where, e, stamp(), version))
  }

  private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
}
