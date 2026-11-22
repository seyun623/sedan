package com.moneyflow.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportHelperTest {
  @Test fun formulaTriggersAreNeutralized() {
    assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, CsvExportHelper.escapeCsv("=HYPERLINK(\"x\")"))
    assertEquals("'+1", CsvExportHelper.escapeCsv("+1"))
    assertEquals("'@SUM(A1)", CsvExportHelper.escapeCsv("@SUM(A1)"))
  }

  @Test fun normalValuesAreQuotedOnlyWhenNeeded() {
    assertEquals("스타벅스", CsvExportHelper.escapeCsv("스타벅스"))
    assertEquals("\"a,b\"", CsvExportHelper.escapeCsv("a,b"))
  }
}
