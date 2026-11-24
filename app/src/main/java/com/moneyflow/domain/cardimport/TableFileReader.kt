package com.moneyflow.domain.cardimport

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

class UnsupportedFileException(message: String) : Exception(message)

/**
 * Reads the cells of a statement file downloaded from a card company or bank:
 * CSV / TSV (UTF-8 or EUC-KR), .xlsx, and the HTML tables many Korean sites save as ".xls".
 * Old binary .xls files are not supported (the user is asked to save as xlsx / CSV).
 */
object TableFileReader {
  /** Limit for one unpacked part of an .xlsx file. */
  private const val MAX_UNZIPPED = 40 * 1024 * 1024
  private const val MAX_BYTES = 10 * 1024 * 1024

  fun read(bytes: ByteArray, fileName: String): List<List<String>> {
    if (bytes.size > MAX_BYTES) throw UnsupportedFileException("파일이 너무 커요 (10MB 이하).")
    if (bytes.isEmpty()) throw UnsupportedFileException("빈 파일이에요.")
    val rows = when {
      isZip(bytes) -> readXlsx(bytes)
      isOle(bytes) -> throw UnsupportedFileException(
        "이 엑셀 파일(옛 .xls 형식)은 읽을 수 없어요. 엑셀이나 구글 시트에서 열어 '다른 이름으로 저장 → xlsx 또는 CSV'로 저장한 뒤 다시 골라 주세요."
      )
      else -> {
        val text = decode(bytes)
        if (looksLikeHtml(text)) readHtml(text) else readDelimited(text)
      }
    }
    val cleaned = rows.map { r -> r.map { it.replace(' ', ' ').trim() } }.filter { r -> r.any { it.isNotEmpty() } }
    if (cleaned.isEmpty()) throw UnsupportedFileException("파일에서 표를 찾지 못했어요. ($fileName)")
    return cleaned
  }

  private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
  private fun isOle(b: ByteArray) = b.size > 8 && (b[0].toInt() and 0xFF) == 0xD0 && (b[1].toInt() and 0xFF) == 0xCF

  /** UTF-8 when valid, otherwise EUC-KR (MS949), which older Korean bank files use. */
  internal fun decode(bytes: ByteArray): String {
    val body = if (bytes.size >= 3 && (bytes[0].toInt() and 0xFF) == 0xEF && (bytes[1].toInt() and 0xFF) == 0xBB && (bytes[2].toInt() and 0xFF) == 0xBF)
      bytes.copyOfRange(3, bytes.size) else bytes
    return try {
      Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(body)).toString()
    } catch (e: CharacterCodingException) {
      String(body, runCatching { Charset.forName("MS949") }.getOrElse { Charset.forName("EUC-KR") })
    }
  }

  private fun looksLikeHtml(text: String): Boolean {
    val head = text.take(4000).lowercase()
    return "<table" in head || "<html" in head || "<tr" in head
  }

  // ---- CSV / TSV ---------------------------------------------------------------------------

  internal fun readDelimited(text: String): List<List<String>> {
    val sample = text.lineSequence().take(20).joinToString("\n")
    val delimiter = listOf(',', '\t', ';', '|').maxByOrNull { d -> sample.count { it == d } } ?: ','
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
      val c = text[i]
      when {
        quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
        c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
        !quoted && c == delimiter -> { row += cell.toString(); cell.clear() }
        !quoted && (c == '\n' || c == '\r') -> {
          if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
          row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
        }
        else -> cell.append(c)
      }
      i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    return rows
  }

  // ---- HTML table ("xls" saved by many Korean sites) -----------------------------------------

  private val ROW = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  private val CELL = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  private val TAG = Regex("<[^>]+>")
  private val BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

  internal fun readHtml(html: String): List<List<String>> =
    ROW.findAll(html).map { r -> CELL.findAll(r.groupValues[1]).map { c -> htmlText(c.groupValues[1]) }.toList() }.toList()

  private fun htmlText(s: String): String = unescape(TAG.replace(BR.replace(s, " "), "")).replace(Regex("\\s+"), " ").trim()

  private fun unescape(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    .replace("&amp;", "&")

  // ---- XLSX (first sheet) ------------------------------------------------------------------

  /** Reads one zip entry, refusing "zip bombs" that unpack to more than [MAX_UNZIPPED] bytes. */
  private fun ZipInputStream.readLimited(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
      val n = read(buf)
      if (n < 0) break
      out.write(buf, 0, n)
      if (out.size() > MAX_UNZIPPED) throw UnsupportedFileException("엑셀 파일이 너무 커요. 기간을 나눠서 받아 주세요.")
    }
    return out.toByteArray()
  }

  internal fun readXlsx(bytes: ByteArray): List<List<String>> {
    var shared: List<String> = emptyList()
    var sheet: String? = null
    var firstSheetName: String? = null
    val sheets = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val e = zip.nextEntry ?: break
        val name = e.name
        when {
          name == "xl/sharedStrings.xml" -> shared = parseShared(zip.readLimited().toString(Charsets.UTF_8))
          name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> sheets[name] = zip.readLimited().toString(Charsets.UTF_8)
        }
      }
    }
    firstSheetName = sheets.keys.minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
    sheet = firstSheetName?.let { sheets[it] }
    if (sheet == null) throw UnsupportedFileException("엑셀 파일에서 시트를 찾지 못했어요.")
    return parseSheet(sheet, shared)
  }

  private val SI = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
  private val T = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
  private val XROW = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
  private val XCELL = Regex("<c ([^>]*?)(/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
  private val V = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
  private val REF = Regex("r=\"([A-Z]+)\\d+\"")
  private val TYPE = Regex("t=\"(\\w+)\"")

  private fun parseShared(xml: String): List<String> =
    SI.findAll(xml).map { si -> T.findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) } }.toList()

  private fun parseSheet(xml: String, shared: List<String>): List<List<String>> =
    XROW.findAll(xml).map { row ->
      val cells = sortedMapOf<Int, String>()
      var next = 0
      XCELL.findAll(row.groupValues[1]).forEach { c ->
        val attrs = c.groupValues[1]
        val col = REF.find(attrs)?.groupValues?.get(1)?.let(::columnIndex) ?: next
        next = col + 1
        val inner = c.groupValues.getOrNull(3).orEmpty()
        val raw = V.find(inner)?.groupValues?.get(1)
        val value = when (TYPE.find(attrs)?.groupValues?.get(1)) {
          "s" -> raw?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
          "inlineStr" -> T.findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
          "b" -> if (raw == "1") "TRUE" else "FALSE"
          else -> unescape(raw.orEmpty())
        }
        cells[col] = value
      }
      val width = (cells.keys.maxOrNull() ?: -1) + 1
      (0 until width).map { cells[it].orEmpty() }
    }.toList()

  private fun columnIndex(letters: String): Int = letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
}
