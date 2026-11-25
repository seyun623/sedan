package com.moneyflow

import com.moneyflow.ui.settings.CheckResult
import com.moneyflow.ui.settings.FeatureChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V214FeatureCheckTest {
  @Test fun reportAndStorage() {
    val results = mapOf("bill" to CheckResult(true), "card_import" to CheckResult(false, "롯데\t여전히 안 눌림\n"))
    val back = FeatureChecks.decode(FeatureChecks.encode(results))
    assertEquals(true, back["bill"]?.ok)
    assertEquals("롯데 여전히 안 눌림 ", back["card_import"]?.note)
    val report = FeatureChecks.report(FeatureChecks.visible(true), back, "2.14.2")
    assertTrue(report.startsWith("머니플로우 2.14.2 확인 결과"))
    assertTrue(report.contains("✅ 카드 결제일 미리 알림"))
    assertTrue(report.contains("⚠ 카드사 가져오기 — 롯데 여전히 안 눌림"))
    assertTrue(report.contains("⬜ 결제 알림 자동 등록"))
  }

  @Test fun basicEditionHidesAutoCapture() {
    assertTrue(FeatureChecks.visible(false).none { it.id == "capture" })
    assertEquals(FeatureChecks.all.size, FeatureChecks.visible(true).size)
  }
}
