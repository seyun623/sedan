package com.moneyflow

import com.moneyflow.domain.alerts.BudgetReminders
import com.moneyflow.domain.alerts.WeeklyReport
import com.moneyflow.domain.family.FamilySettlement
import com.moneyflow.domain.family.SettleTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 2.21: 가족 정산 기록, 가족 예산 알림, 주간 리포트의 가족 합계. */
class V221FamilyTest {
  private val nov = YearMonth.of(2026, 11)

  @Test fun familyBudgetAlertAt80And100() {
    assertNull(BudgetReminders.family(nov, 2_000_000, 1_500_000, emptySet()))
    val r80 = BudgetReminders.family(nov, 2_000_000, 1_700_000, emptySet())!!
    assertEquals("family|2026-11|80", r80.key)
    assertTrue(r80.text.contains("300,000원 남았어요"))
    val r100 = BudgetReminders.family(nov, 2_000_000, 2_100_000, setOf("family|2026-11|80"))!!
    assertEquals("11월 가족 예산을 넘었어요", r100.title)
    // Sending 100% also covers 80%.
    assertEquals(listOf("family|2026-11|80"), BudgetReminders.alsoCovers(r100))
    assertNull(BudgetReminders.family(nov, 0, 5_000_000, emptySet()))
  }

  @Test fun weeklyReportShowsTheFamilyTotal() {
    val r = WeeklyReport.due(LocalDate.of(2026, 11, 30), 100_000, emptyList(), null, null, emptySet(), familyWeek = 260_000)!!
    assertTrue(r.text.contains("가족 전체 260,000원"))
  }

  @Test fun doneMarkChangesWithTheAmount() {
    val t = SettleTransfer("엄마", "나", 10_000)
    assertEquals("2026-11|엄마|나|10000", FamilySettlement.doneKey(nov, t))
    assertTrue(FamilySettlement.doneKey(nov, t) != FamilySettlement.doneKey(nov, t.copy(amount = 12_000)))
  }
}
