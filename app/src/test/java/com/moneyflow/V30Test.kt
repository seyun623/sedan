package com.moneyflow

import com.moneyflow.domain.StartGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3.0: 시작하기 checklist. */
class V30Test {
  @Test fun stepsFollowWhatIsDone() {
    val steps = StartGuide.steps(hasAccounts = true, autoCaptureAvailable = true, hasAutoRecords = false, budgetSet = true, familyOn = false, backupOn = true)
    assertEquals(listOf("account", "capture", "budget", "family", "backup"), steps.map { it.id })
    assertEquals(listOf("capture", "family"), steps.filter { !it.done }.map { it.id })
    assertTrue(StartGuide.visible(steps, dismissed = false))
    assertFalse(StartGuide.visible(steps, dismissed = true))
  }

  @Test fun basicEditionHasNoCaptureStepAndHidesWhenAllDone() {
    val steps = StartGuide.steps(true, autoCaptureAvailable = false, hasAutoRecords = false, budgetSet = true, familyOn = true, backupOn = true)
    assertTrue(steps.none { it.id == "capture" })
    assertFalse(StartGuide.visible(steps, dismissed = false))
  }
}
