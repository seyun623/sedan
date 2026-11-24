package com.moneyflow.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
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
    val sw = StringWriter()
    e.printStackTrace(PrintWriter(sw))
    // Keep it short: the first lines of each cause are enough to find the place.
    val trace = sw.toString().lines().filter { it.isNotBlank() }
      .filterIndexed { i, line -> i < 25 || line.startsWith("Caused by") }
      .joinToString("\n")
    return "[$time] v$appVersion $where\n$trace\n\n"
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
