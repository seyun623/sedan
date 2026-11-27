package com.moneyflow.alerts

import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.domain.alerts.BackupReminders
import com.moneyflow.domain.alerts.BudgetReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RemindersTest {
  private val month = YearMonth.of(2026, 11)
  private val budget = MonthBudget(month, 1_000_000, mapOf("카페/간식" to 50_000L), null)

  @Test fun budgetLevelsAreSentOnceAndOnlyHighest() {
    assertTrue(BudgetReminders.due(month, budget, 790_000, emptyMap(), emptySet()).isEmpty())
    val at80 = BudgetReminders.due(month, budget, 800_000, emptyMap(), emptySet())
    assertEquals(listOf("budget|2026-11|80"), at80.map { it.key })
    assertTrue(BudgetReminders.due(month, budget, 900_000, emptyMap(), setOf("budget|2026-11|80")).isEmpty())
    val over = BudgetReminders.due(month, budget, 1_050_000, emptyMap(), setOf("budget|2026-11|80"))
    assertEquals(listOf("budget|2026-11|100"), over.map { it.key })
    assertTrue(over[0].text.contains("50,000원"))
    // 100% covers 80% so a later 80% isn't sent.
    assertEquals(listOf("budget|2026-11|80"), BudgetReminders.alsoCovers(over[0]))
    assertTrue(BudgetReminders.due(month, MonthBudget(month, 0, emptyMap(), null), 5_000_000, emptyMap(), emptySet()).isEmpty())
  }

  @Test fun categoryBudgetOverspend() {
    val r = BudgetReminders.due(month, budget, 10_000, mapOf("카페/간식" to 51_000L, "식비" to 999_999L), emptySet())
    assertEquals(listOf("budget|2026-11|cat|카페/간식"), r.map { it.key })
    assertTrue(BudgetReminders.alsoCovers(r[0]).isEmpty())
  }

  @Test fun monthlySummaryOnFirstDaysOnce() {
    val day1 = LocalDate.of(2026, 12, 1)
    val r = BudgetReminders.monthlySummary(day1, 3_000_000, 1_200_000, 1_000_000, "식비" to 400_000L, 1_000_000, emptySet())
    assertNotNull(r)
    assertEquals("summary|2026-11", r!!.key)
    assertEquals("11월 가계부 요약", r.title)
    assertTrue(r.text.contains("예산 대비 120%"))
    assertTrue(r.text.contains("전달보다 200,000원 더 씀"))
    assertTrue(r.text.contains("식비"))
    assertNull(BudgetReminders.monthlySummary(day1, 1, 1, 0, null, null, setOf("summary|2026-11")))
    assertNull(BudgetReminders.monthlySummary(LocalDate.of(2026, 12, 4), 1, 1, 0, null, null, emptySet()))
    assertNull(BudgetReminders.monthlySummary(day1, 0, 0, 0, null, null, emptySet()))
  }

  @Test fun backupReminderAfterTwoWeeksWeeklyAndOnError() {
    val today = LocalDate.of(2026, 11, 23)
    assertNull(BackupReminders.due(today, today.minusDays(13), null, null, emptySet()))
    val stale = BackupReminders.due(today, today.minusDays(20), null, null, emptySet())
    assertNotNull(stale)
    assertTrue(stale!!.title.contains("20일"))
    assertNull(BackupReminders.due(today, today.minusDays(20), null, null, setOf(stale.key)))
    // Never backed up: counts from the first record.
    assertNull(BackupReminders.due(today, null, today.minusDays(3), null, emptySet()))
    assertEquals("아직 백업한 적이 없어요", BackupReminders.due(today, null, today.minusDays(30), null, emptySet())!!.title)
    assertNull(BackupReminders.due(today, null, null, null, emptySet()))
    assertEquals("자동 백업을 하지 못했어요", BackupReminders.due(today, today, today, "폴더 권한이 없어요", emptySet())!!.title)
  }
}
