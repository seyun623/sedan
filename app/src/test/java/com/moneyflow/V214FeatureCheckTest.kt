package com.moneyflow

import com.moneyflow.ui.settings.CheckResult
import com.moneyflow.ui.settings.FeatureChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V214FeatureCheckTest {
  @Test fun reportAndStorage() {
    val results = mapOf("shop_orders" to CheckResult(true), "kb_login" to CheckResult(false, "여전히\t가려짐\n"))
    val back = FeatureChecks.decode(FeatureChecks.encode(results))
    assertEquals(true, back["shop_orders"]?.ok)
    assertEquals("여전히 가려짐 ", back["kb_login"]?.note)
    val report = FeatureChecks.report(FeatureChecks.visible(true), back, "2.17.1")
    assertTrue(report.startsWith("머니플로우 2.17.1 확인 결과"))
    assertTrue(report.contains("✅ 쇼핑몰 주문 → 상품명 붙이기"))
    assertTrue(report.contains("⚠ KB국민카드 로그인 — 여전히 가려짐"))
    assertTrue(report.contains("⬜ 다음 결제 금액 (청구 안내)"))
  }

  /** 2.17.1: only checks still waiting for a test are listed (finished ones were removed). */
  @Test fun onlyPendingChecksAreListed() {
    val ids = FeatureChecks.all.map { it.id }
    listOf("bill", "swipe_delete", "card_import", "capture").forEach { assertTrue(it !in ids) }
    assertTrue("capture_no_account" in ids && "benefit_search" in ids)
  }

  @Test fun basicEditionHidesAutoCapture() {
    assertTrue(FeatureChecks.visible(false).none { it.id == "capture_no_account" })
    assertTrue(FeatureChecks.visible(false).size < FeatureChecks.visible(true).size)
    assertEquals(FeatureChecks.all.size, FeatureChecks.visible(true).size)
  }
}
