package com.moneyflow

import com.moneyflow.domain.alerts.WeeklyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 2.19: 주간 소비 리포트. */
class V219WeeklyReportTest {
  private val monday = LocalDate.of(2026, 11, 30)

  @Test fun lastWeekIsMondayToSunday() {
    assertEquals(LocalDate.of(2026, 11, 23), WeeklyReport.lastWeekStart(monday))
    assertEquals(LocalDate.of(2026, 11, 23), WeeklyReport.lastWeekStart(monday.plusDays(2)))
  }

  @Test fun comparesWithTheUsualWeek() {
    val r = WeeklyReport.due(monday, 320_000, listOf(380_000, 360_000, 350_000, 0), "식비" to 120_000L, "쿠팡" to 52_000L, emptySet())!!
    assertEquals("weekly|2026-11-23", r.key)
    assertTrue(r.title.contains("11.23~11.29"))
    assertTrue(r.text, r.text.contains("평소보다 12% 적어요"))
    assertTrue(r.text.contains("가장 많이 쓴 곳: 식비"))
    assertTrue(r.text.contains("가장 큰 지출: 쿠팡"))
    assertTrue(WeeklyReport.due(monday, 420_000, listOf(300_000), null, null, emptySet())!!.text.contains("40% 많아요"))
  }

  @Test fun sentOnceAndOnlyEarlyInTheWeek() {
    assertNull(WeeklyReport.due(monday, 100_000, emptyList(), null, null, setOf("weekly|2026-11-23")))
    assertNull(WeeklyReport.due(monday.plusDays(3), 100_000, emptyList(), null, null, emptySet()))
    assertNull(WeeklyReport.due(monday, 0, emptyList(), null, null, emptySet()))
    // A first week (nothing to compare): just the amount.
    assertEquals(false, WeeklyReport.due(monday, 50_000, emptyList(), null, null, emptySet())!!.text.contains("평소"))
  }
}
