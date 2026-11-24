package com.moneyflow

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.security.PinCheckResult
import com.moneyflow.ui.lock.LockScreen
import com.moneyflow.ui.theme.MoneyFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The lock screen on a device: PIN entry, wrong PIN message, unlock. */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
  @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

  @Test fun pinEntryWrongThenRight() {
    val entered = mutableListOf<String>()
    rule.setContent {
      MoneyFlowTheme {
        LockScreen(
          settings = AppSettings(pinHash = "x", pinSalt = "y"),
          onVerifyPin = { pin, done ->
            entered += pin
            done(if (pin == "2468") PinCheckResult.Success else PinCheckResult.Wrong(attemptsLeft = 4))
          },
          onBiometricSuccess = {}
        )
      }
    }
    rule.onNodeWithTag("lock_screen").assertExists()
    rule.onNodeWithTag("lock_confirm_button").assertIsNotEnabled()
    "1357".forEach { rule.onNodeWithText(it.toString()).performClick() }
    rule.onNodeWithTag("lock_confirm_button").assertIsEnabled().performClick()
    rule.onNodeWithText("PIN이 맞지 않아요. 4회 남음").assertExists()

    "2468".forEach { rule.onNodeWithText(it.toString()).performClick() }
    rule.onNodeWithTag("lock_confirm_button").performClick()
    rule.waitForIdle()
    assertEquals(listOf("1357", "2468"), entered)
  }
}
