package com.moneyflow.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportHelper {
  private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
  private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)

  fun exportTransactionsToCsv(
    context: Context,
    transactions: List<TransactionEntity>,
    titleSuffix: String = ""
  ) {
    if (transactions.isEmpty()) {
      Toast.makeText(context, "내보낼 거래 내역이 없습니다.", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      val timestampStr = fileDateFormat.format(Date())
      val sanitizedSuffix = titleSuffix.replace(" ", "_").replace("년", "").replace("월", "")
      val fileName = "MoneyFlow_가계부_${sanitizedSuffix}_${timestampStr}.csv"
      val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
      // Remove earlier exports so financial data does not accumulate in the cache.
      cacheDir.listFiles()?.forEach { it.delete() }
      val file = File(cacheDir, fileName)

      // Write UTF-8 with BOM (\uFEFF) so Excel on Windows and macOS automatically parses Korean characters correctly
      FileOutputStream(file).use { fos ->
        OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
          // Write UTF-8 BOM
          writer.write("\uFEFF")

          // CSV Header
          writer.write("일시,구분,카테고리,내용(메모),금액(원),출금계좌,입금계좌,결제수단,예산제외여부\n")

          // Rows
          transactions.forEach { tx ->
            val dateStr = escapeCsv(dateTimeFormat.format(Date(tx.timestamp)))
            val typeStr = when (tx.type) {
              TransactionType.EXPENSE -> "지출"
              TransactionType.INCOME -> "수입"
              TransactionType.TRANSFER -> "이체"
              TransactionType.REFUND -> "환불"
            }
            val categoryStr = escapeCsv(tx.category)
            val memoStr = escapeCsv(tx.memo)
            val amountStr = tx.amount.toString()
            val accountStr = escapeCsv(tx.accountName)
            val toAccountStr = escapeCsv(tx.toAccountName ?: "")
            val methodStr = escapeCsv(tx.paymentMethod)
            val budgetExcludedStr = if (tx.isExcludedFromBudget) "Y" else "N"

            writer.write("$dateStr,$typeStr,$categoryStr,$memoStr,$amountStr,$accountStr,$toAccountStr,$methodStr,$budgetExcludedStr\n")
          }
          writer.flush()
        }
      }

      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, "머니플로우 가계부 내역 ($titleSuffix)")
        putExtra(Intent.EXTRA_TEXT, "머니플로우에서 내보낸 가계부 거래 내역 (${transactions.size}건) 파일입니다.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "가계부 CSV 파일 내보내기 / 공유")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(context, "CSV 내보내기 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  /**
   * Escapes a CSV field. Values starting with = + - @ (or tab/CR) are prefixed with an apostrophe
   * so spreadsheet apps do not execute them as formulas (memos can come from SMS text).
   */
  internal fun escapeCsv(value: String): String {
    var str = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value
    if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
      str = str.replace("\"", "\"\"")
      return "\"$str\""
    }
    return str
  }

  private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
