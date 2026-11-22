package com.moneyflow.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import com.moneyflow.ai.CategorySuggester
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.data.repository.SampleDataSeeder
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.data.repository.ThemeMode
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.security.AppLockManager
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import com.moneyflow.ui.analytics.AnalyticsScreen
import com.moneyflow.ui.analytics.AnalyticsViewModel
import com.moneyflow.ui.budget.BudgetScreen
import com.moneyflow.ui.budget.BudgetViewModel
import com.moneyflow.ui.common.SelectedMonthStore
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.home.HomeScreen
import com.moneyflow.ui.home.HomeViewModel
import com.moneyflow.ui.ledger.LedgerScreen
import com.moneyflow.ui.ledger.LedgerViewModel
import com.moneyflow.ui.lock.LockScreen
import com.moneyflow.ui.settings.SettingsScreen
import com.moneyflow.ui.settings.SettingsViewModel
import com.moneyflow.ui.theme.MoneyFlowTheme
import com.moneyflow.ui.transaction.TransactionFormScreen
import com.moneyflow.ui.transaction.TransactionFormViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/**
 * Renders the main screens with sample data in light and dark theme and writes PNGs to
 * build/screenshots (visual check only; no golden comparison).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
  @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private lateinit var budgets: BudgetRepository
  private lateinit var recurring: RecurringRepository
  private lateinit var settings: SettingsRepository
  private lateinit var seeder: SampleDataSeeder
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 21, 12, 0))
  private val bus = UserMessageBus()
  private val stats = MonthlyStatsCalculator(clock.zone)

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    ledger = LedgerRepository(db, clock)
    budgets = BudgetRepository(db)
    recurring = RecurringRepository(db, ledger, clock)
    settings = testSettingsRepository()
    seeder = SampleDataSeeder(ledger, budgets, recurring, clock)
    seeder.seed()
  }

  @After fun tearDown() = db.close()

  private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      MoneyFlowTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        androidx.compose.foundation.layout.Box(
          androidx.compose.ui.Modifier.fillMaxSize().background(com.moneyflow.ui.theme.Gray100)
        ) { content() }
      }
    }
    repeat(8) {
      Thread.sleep(150)
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
      compose.mainClock.advanceTimeBy(500)
    }
    val view = compose.activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bmp))
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "${name}_${if (dark) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun home() = HomeViewModel(ledger, budgets, settings, seeder, stats, bus, clock)
  private fun monthStore() = SelectedMonthStore(clock)

  @Test fun homeLight() = shot("01_home", false) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = home()) }
  @Test fun homeDark() = shot("01_home", true) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = home()) }

  @Test fun ledgerLight() = shot("02_ledger", false) { LedgerScreen({}, {}, viewModel = LedgerViewModel(ledger, monthStore(), stats, bus, clock)) }
  @Test fun ledgerDark() = shot("02_ledger", true) { LedgerScreen({}, {}, viewModel = LedgerViewModel(ledger, monthStore(), stats, bus, clock)) }

  @Test fun analyticsLight() = shot("03_analytics", false) { AnalyticsScreen({}, viewModel = AnalyticsViewModel(ledger, monthStore(), stats, clock)) }
  @Test fun analyticsDark() = shot("03_analytics", true) { AnalyticsScreen({}, viewModel = AnalyticsViewModel(ledger, monthStore(), stats, clock)) }

  private fun budgetVm() = BudgetViewModel(ledger, budgets, recurring, monthStore(), stats, bus, clock)
  @Test fun budgetLight() = shot("04_budget", false) { BudgetScreen({}, viewModel = budgetVm()) }
  @Test fun budgetDark() = shot("04_budget", true) { BudgetScreen({}, viewModel = budgetVm()) }

  private fun formVm() = TransactionFormViewModel(SavedStateHandle(), ledger, CategorySuggester(), bus)
  @Test fun formLight() = shot("05_form", false) { TransactionFormScreen({}, viewModel = formVm()) }
  @Test fun formDark() = shot("05_form", true) { TransactionFormScreen({}, viewModel = formVm()) }

  private fun settingsVm() = SettingsViewModel(settings, ledger, AppLockManager(settings, clock), AutoCaptureProcessor(db, ledger, settings, clock), seeder, bus)
  @Test fun settingsLight() = shot("06_settings", false) { SettingsScreen({}, {}, {}, viewModel = settingsVm()) }
  @Test fun settingsDark() = shot("06_settings", true) { SettingsScreen({}, {}, {}, viewModel = settingsVm()) }

  private val lockSettings = AppSettings(pinHash = "x", pinSalt = "y")
  @Test fun lockLight() = shot("07_lock", false) { LockScreen(lockSettings, { _, _ -> }, {}) }
  @Test fun securityLight() = shot("08_security", false) { com.moneyflow.ui.settings.SecuritySettingsScreen({}, viewModel = settingsVm()) }
  @Test fun securityDark() = shot("08_security", true) { com.moneyflow.ui.settings.SecuritySettingsScreen({}, viewModel = settingsVm()) }
  @Test fun captureLight() = shot("09_autocapture", false) { com.moneyflow.ui.settings.AutoCaptureSettingsScreen({}, viewModel = settingsVm()) }
  @Test fun captureDark() = shot("09_autocapture", true) { com.moneyflow.ui.settings.AutoCaptureSettingsScreen({}, viewModel = settingsVm()) }
  private fun accountVm() = com.moneyflow.ui.account.AccountDetailViewModel(
    SavedStateHandle(mapOf(com.moneyflow.ui.account.ARG_ACCOUNT_ID to 3L)), ledger, bus)
  @Test fun accountLight() = shot("10_account", false) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = accountVm()) }
  @Test fun accountDark() = shot("10_account", true) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = accountVm()) }
  @Test fun lockDark() = shot("07_lock", true) { LockScreen(lockSettings, { _, _ -> }, {}) }
}

