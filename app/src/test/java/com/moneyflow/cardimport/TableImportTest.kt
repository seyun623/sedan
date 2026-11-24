package com.moneyflow.cardimport

import com.moneyflow.domain.cardimport.TableFileReader
import com.moneyflow.domain.cardimport.TableStatementReader
import com.moneyflow.domain.cardimport.UnsupportedFileException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TableImportTest {
  private val today = LocalDate.of(2026, 11, 24)
  private val reader = TableStatementReader(today)

  private val cardCsv = """
    현대카드 이용내역,,,,,
    조회기간,2026.11.01 ~ 2026.11.24,,,,
    이용일,이용시간,가맹점명,이용금액,할부,취소여부
    2026.11.21,14:32,"스타벅스 역삼점","5,600",일시불,
    2026.11.20,09:10,GS25 강남점,"3,500",일시불,취소
    2026.11.18,,이마트 성수점,"120,000",3개월,
    합계,,,"129,100",,
  """.trimIndent()

  @Test fun cardStatementCsv() {
    val rows = TableFileReader.read(cardCsv.toByteArray(), "hyundai.csv")
    val r = reader.read(rows, "파일 읽기")
    assertEquals(3, r.items.size)
    with(r.items[0]) {
      assertEquals(LocalDate.of(2026, 11, 21), date); assertEquals(LocalTime.of(14, 32), time)
      assertEquals("스타벅스 역삼점", merchant); assertEquals(5_600, amount); assertFalse(isCancel); assertNull(installmentMonths)
    }
    assertTrue(r.items[1].isCancel)
    assertEquals(3, r.items[2].installmentMonths)
  }

  @Test fun bankStatementInEucKrWithDepositsAsIncome() {
    val text = "거래일시\t적요\t출금액\t입금액\t잔액\n2026-11-25 09:00:00\t(주)머니회사 급여\t0\t3,200,000\t4,000,000\n2026-11-23 12:01:00\t관리비\t150,000\t0\t800,000\n"
    val rows = TableFileReader.read(text.toByteArray(charset("EUC-KR")), "bank.txt")
    val r = reader.read(rows, "x")
    assertEquals(2, r.items.size)
    assertTrue(r.items[0].isIncome)
    assertEquals(3_200_000, r.items[0].amount)
    assertEquals("(주)머니회사 급여", r.items[0].merchant)
    assertFalse(r.items[1].isIncome)
    assertEquals(LocalTime.of(12, 1), r.items[1].time)
  }

  @Test fun htmlTableSavedAsXls() {
    val html = """<html><body><table>
      <tr><th>승인일자</th><th>가맹점</th><th>승인금액</th></tr>
      <tr><td>26.11.02</td><td>쿠팡&amp;쿠페이</td><td>32,000원</td></tr>
      <tr><td>11/01</td><td>CU<br>편의점</td><td>(1,200)</td></tr>
    </table></body></html>"""
    val r = reader.read(TableFileReader.read(html.toByteArray(), "card.xls"), "x")
    assertEquals(listOf("쿠팡&쿠페이", "CU 편의점"), r.items.map { it.merchant })
    assertEquals(LocalDate.of(2026, 11, 2), r.items[0].date)
    assertEquals(LocalDate.of(2026, 11, 1), r.items[1].date)
    assertTrue("negative amount = cancellation", r.items[1].isCancel)
  }

  @Test fun xlsxWithSharedStringsAndDateSerials() {
    val shared = """<sst><si><t>이용일자</t></si><si><t>이용가맹점</t></si><si><t>이용금액</t></si><si><t>배달의민족</t></si></sst>"""
    val sheet = """<worksheet><sheetData>
      <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
      <row r="2"><c r="A2"><v>46346.5</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>23000</v></c></row>
      <row r="3"><c r="A3" t="inlineStr"><is><t>2026-11-03</t></is></c><c r="C3"><v>9000</v></c><c r="B3" t="inlineStr"><is><t>올리브영</t></is></c></row>
    </sheetData></worksheet>"""
    val bytes = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(shared.toByteArray()); z.closeEntry()
        z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
      }
    }.toByteArray()
    val r = reader.read(TableFileReader.read(bytes, "a.xlsx"), "x")
    assertEquals(2, r.items.size)
    assertEquals(LocalDate.of(2026, 11, 20), r.items[0].date)
    assertEquals(LocalTime.of(12, 0), r.items[0].time)
    assertEquals("배달의민족", r.items[0].merchant)
    assertEquals("올리브영", r.items[1].merchant)
    assertEquals(9_000, r.items[1].amount)
  }

  @Test fun oldBinaryXlsAndUnknownTablesAreRejectedClearly() {
    val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0)
    try { TableFileReader.read(ole, "old.xls"); fail() } catch (e: UnsupportedFileException) { assertTrue(e.message!!.contains("xlsx")) }
    val r = reader.read(TableFileReader.read("이름,나이\n홍길동,30\n".toByteArray(), "x.csv"), "x")
    assertTrue(r.items.isEmpty())
  }

  @Test fun cellParsers() {
    assertEquals(-3_500L, reader.parseAmount("-3,500원"))
    assertEquals(-1_200L, reader.parseAmount("(1,200)"))
    assertNull(reader.parseAmount("abc"))
    assertNull(reader.parseAmount(""))
    assertEquals(LocalDate.of(2026, 11, 21), reader.parseDate("20261121")!!.first)
    // A future month/day without a year means last year.
    assertEquals(LocalDate.of(2025, 12, 30), reader.parseDate("12/30")!!.first)
    assertEquals(6, reader.parseInstallment("06개월"))
    assertNull(reader.parseInstallment("일시불"))
  }
}
