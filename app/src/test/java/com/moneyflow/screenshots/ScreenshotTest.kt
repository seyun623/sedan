package com.moneyflow.screenshots

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.flow.first
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

  private fun home() = HomeViewModel(ledger, budgets, settings, com.moneyflow.data.repository.CardRepository(db, clock), seeder, stats, bus, clock)
  private fun monthStore() = SelectedMonthStore(clock)

  @Test fun homeLight() { val vm = home(); shot("01_home", false) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = vm) } }
  @Test fun homeDark() { val vm = home(); shot("01_home", true) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = vm) } }

  @Test fun ledgerLight() { val vm = LedgerViewModel(ledger, monthStore(), stats, bus, clock); shot("02_ledger", false) { LedgerScreen({}, {}, viewModel = vm) } }
  @Test fun ledgerDark() { val vm = LedgerViewModel(ledger, monthStore(), stats, bus, clock); shot("02_ledger", true) { LedgerScreen({}, {}, viewModel = vm) } }

  @Test fun analyticsLight() { val vm = AnalyticsViewModel(ledger, monthStore(), stats, clock); shot("03_analytics", false) { AnalyticsScreen({}, viewModel = vm) } }
  @Test fun analyticsDark() { val vm = AnalyticsViewModel(ledger, monthStore(), stats, clock); shot("03_analytics", true) { AnalyticsScreen({}, viewModel = vm) } }

  private fun budgetVm() = BudgetViewModel(ledger, budgets, recurring, monthStore(), stats, bus, clock, settings)
  @Test fun budgetLight() { val vm = budgetVm(); shot("04_budget", false) { BudgetScreen({}, viewModel = vm) } }
  @Test fun budgetDark() { val vm = budgetVm(); shot("04_budget", true) { BudgetScreen({}, viewModel = vm) } }

  private fun formVm() = TransactionFormViewModel(SavedStateHandle(), ledger, CategorySuggester(com.moneyflow.ai.AiService(settings, com.moneyflow.testing.PlainSecretStore, com.moneyflow.ai.GeminiClient(), com.moneyflow.ai.OpenAiCompatClient()), db, clock), settings, com.moneyflow.data.repository.CardRepository(db, clock), bus, com.moneyflow.ui.common.PrefillStore())
  @Test fun formLight() { val vm = formVm(); shot("05_form", false) { TransactionFormScreen({}, viewModel = vm) } }
  @Test fun formDark() { val vm = formVm(); shot("05_form", true) { TransactionFormScreen({}, viewModel = vm) } }

  private fun settingsVm() = SettingsViewModel(settings, ledger, AppLockManager(settings, clock), AutoCaptureProcessor(db, ledger, settings, clock, object : com.moneyflow.domain.capture.CaptureNotifier { override fun foreignPayment(prompt: com.moneyflow.domain.capture.ForeignPaymentPrompt) {} }), seeder, bus)
  @Test fun settingsLight() { val vm = settingsVm(); shot("06_settings", false) { SettingsScreen({}, {}, {}, viewModel = vm) } }
  @Test fun settingsDark() { val vm = settingsVm(); shot("06_settings", true) { SettingsScreen({}, {}, {}, viewModel = vm) } }

  private val lockSettings = AppSettings(pinHash = "x", pinSalt = "y")
  @Test fun lockLight() = shot("07_lock", false) { LockScreen(lockSettings, { _, _ -> }, {}) }
  @Test fun securityLight() { val vm = settingsVm(); shot("08_security", false) { com.moneyflow.ui.settings.SecuritySettingsScreen({}, viewModel = vm) } }
  @Test fun securityDark() { val vm = settingsVm(); shot("08_security", true) { com.moneyflow.ui.settings.SecuritySettingsScreen({}, viewModel = vm) } }
  @Test fun captureLight() { val vm = settingsVm(); shot("09_autocapture", false) { com.moneyflow.ui.settings.AutoCaptureSettingsScreen({}, viewModel = vm) } }
  @Test fun captureDark() { val vm = settingsVm(); shot("09_autocapture", true) { com.moneyflow.ui.settings.AutoCaptureSettingsScreen({}, viewModel = vm) } }
  private fun accountVm() = com.moneyflow.ui.account.AccountDetailViewModel(
    SavedStateHandle(mapOf(com.moneyflow.ui.account.ARG_ACCOUNT_ID to 3L)), ledger, settings, com.moneyflow.data.repository.CardRepository(db, clock),
    com.moneyflow.domain.card.BenefitReader(com.moneyflow.ai.AiService(settings, com.moneyflow.testing.PlainSecretStore, com.moneyflow.ai.GeminiClient(), com.moneyflow.ai.OpenAiCompatClient())), bus)
  @Test fun accountLight() { val vm = accountVm(); shot("10_account", false) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = vm) } }
  @Test fun accountDark() { val vm = accountVm(); shot("10_account", true) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = vm) } }
  private fun backupVm() = com.moneyflow.ui.settings.BackupViewModel(
    androidx.test.core.app.ApplicationProvider.getApplicationContext(),
    com.moneyflow.data.repository.BackupRepository(db, settings, clock), recurring, settings,
    com.moneyflow.data.repository.AutoBackupRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(),
      com.moneyflow.data.repository.BackupRepository(db, settings, clock), settings, com.moneyflow.testing.PlainSecretStore, clock), bus)
  @Test fun backupLight() { val vm = backupVm(); shot("11_backup", false) { com.moneyflow.ui.settings.BackupScreen({}, viewModel = vm) } }
  @Test fun backupDark() { val vm = backupVm(); shot("11_backup", true) { com.moneyflow.ui.settings.BackupScreen({}, viewModel = vm) } }
  @Test fun firstRunHome() {
    runBlocking { db.backupDao().clearTransactions(); db.backupDao().clearRecurring(); db.backupDao().clearGoals(); db.backupDao().clearAccounts() }
    val vm = home(); shot("12_firstrun", false) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = vm) }
  }
  @Test fun firstRunHomeDark() {
    runBlocking { db.backupDao().clearTransactions(); db.backupDao().clearRecurring(); db.backupDao().clearGoals(); db.backupDao().clearAccounts() }
    val vm = home(); shot("12_firstrun", true) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = vm) }
  }
  private fun aiVm() = com.moneyflow.ui.settings.AiSettingsViewModel(settings, com.moneyflow.testing.PlainSecretStore,
    com.moneyflow.ai.AiService(settings, com.moneyflow.testing.PlainSecretStore, com.moneyflow.ai.GeminiClient(), com.moneyflow.ai.OpenAiCompatClient()), bus)
  @Test fun aiLight() { runBlocking { settings.setAiApiKeyEncrypted("enc:k"); settings.setAiEnabled(true) }; val vm = aiVm(); shot("13_ai", false) { com.moneyflow.ui.settings.AiSettingsScreen({}, viewModel = vm) } }
  @Test fun aiDark() { val vm = aiVm(); shot("13_ai", true) { com.moneyflow.ui.settings.AiSettingsScreen({}, viewModel = vm) } }
  @Test fun cardImportList() = shot("14_cardimport", false) { com.moneyflow.ui.cardimport.CardImportScreen({}, {}) }
  @Test fun cardImportListDark() = shot("14_cardimport", true) { com.moneyflow.ui.cardimport.CardImportScreen({}, {}) }
  private fun previewVm(): com.moneyflow.ui.cardimport.CardImportPreviewViewModel {
    val session = com.moneyflow.domain.cardimport.CardImportSession()
    val text = "전월 이용실적 412,000원\n2026.11.22 스타벅스 역삼점 5,600원\n2026.11.21 점심 식사 12,000원\n2026.11.20 쿠팡 32,000원\n2026.11.19 GS25 강남점 승인취소 -3,500원\n2026.11.18 이마트 성수점 120,000원 3개월"
    session.put(com.moneyflow.domain.cardimport.CardSites.byId("hyundai")!!, com.moneyflow.domain.cardimport.StatementTextParser(clock.today()).parse(text))
    return com.moneyflow.ui.cardimport.CardImportPreviewViewModel(session,
      com.moneyflow.data.repository.CardImportRepository(db, ledger, settings, clock), ledger, com.moneyflow.data.repository.CardRepository(db, clock), settings, bus)
  }
  @Test fun cardPreview() { val vm = previewVm(); shot("15_cardpreview", false) { com.moneyflow.ui.cardimport.CardImportPreviewScreen({}, {}, viewModel = vm) } }
  @Test fun cardPreviewDark() { val vm = previewVm(); shot("15_cardpreview", true) { com.moneyflow.ui.cardimport.CardImportPreviewScreen({}, {}, viewModel = vm) } }
  private fun seedCard() = runBlocking {
    db.cardDao().upsertProfile(com.moneyflow.data.model.CardProfileEntity(3L, 300_000, 1, "", "관리비"))
    db.cardDao().insertBenefit(com.moneyflow.data.model.CardBenefitEntity(accountId = 3L, title = "카페 10% 할인", category = "카페/간식", value = 1000, monthlyCap = 5_000))
    db.cardDao().insertBenefit(com.moneyflow.data.model.CardBenefitEntity(accountId = 3L, title = "쇼핑 5% 적립", category = "쇼핑", value = 500, monthlyCap = 10_000, minPerformance = 300_000))
  }
  @Test fun cardAccountLight() { seedCard(); val vm = accountVm(); shot("16_cardaccount", false) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = vm) } }
  @Test fun cardAccountDark() { seedCard(); val vm = accountVm(); shot("16_cardaccount", true) { com.moneyflow.ui.account.AccountDetailScreen({}, {}, viewModel = vm) } }
  @Test fun homeCardGoals() { seedCard(); val vm = home(); shot("17_homecards", false) { HomeScreen({}, {}, {}, {}, {}, {}, {}, {}, viewModel = vm) } }
  @Test fun lockDark() = shot("07_lock", true) { LockScreen(lockSettings, { _, _ -> }, {}) }
  private fun guideShot(dark: Boolean) {
    seedCard()
    val repo = com.moneyflow.data.repository.CardRepository(db, clock)
    val rows = runBlocking { repo.guide.first() }
    val statuses = runBlocking { repo.currentStatuses() }
    val picks = repo.recommend(statuses, "카페/간식", "스타벅스", 6_000)
    val accounts = runBlocking { ledger.accounts.first() }
    shot("18_cardguide", dark) {
      androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
        com.moneyflow.ui.home.CardGuideCard(rows, accounts.associate { it.id to it.name }, {})
        com.moneyflow.ui.transaction.CardPickHint(picks, accounts, selectedId = accounts.first { it.id != 3L }.id, onUse = {})
        com.moneyflow.ui.transaction.CardPickHint(picks, accounts, selectedId = 3L, onUse = {})
      }
    }
  }
  @Test fun cardGuide() = guideShot(false)
  @Test fun cardGuideDark() = guideShot(true)
  @Test fun rules() {
    runBlocking {
      db.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "스타벅스", category = "카페/간식"))
      db.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "포인트 적립", action = com.moneyflow.data.model.RuleAction.IGNORE))
    }
    val vm = com.moneyflow.ui.settings.RulesViewModel(com.moneyflow.data.repository.RuleRepository(db), bus)
    shot("19_rules", false) { com.moneyflow.ui.settings.RulesScreen({}, viewModel = vm) }
  }
  @Test fun family() {
    val other = com.moneyflow.testing.inMemoryDb()
    val otherSettings = com.moneyflow.testing.testSettingsRepository()
    val repo = com.moneyflow.data.repository.FamilyRepository(db, com.moneyflow.data.repository.BackupRepository(db, settings, clock), settings, clock)
    runBlocking {
      val l = LedgerRepository(other, clock)
      val a = l.addAccount(com.moneyflow.testing.bank("아내 통장"))
      l.addTransaction(com.moneyflow.data.model.TransactionEntity(type = com.moneyflow.data.model.TransactionType.EXPENSE, amount = 84_000, category = "생활/마트", accountId = a, timestamp = clock.nowMillis(), memo = "장보기"))
      l.addTransaction(com.moneyflow.data.model.TransactionEntity(type = com.moneyflow.data.model.TransactionType.INCOME, amount = 2_500_000, category = "급여", accountId = a, timestamp = clock.nowMillis(), memo = "급여"))
      val file = com.moneyflow.data.repository.BackupRepository(other, otherSettings, clock).createBackup("1234".toCharArray(), "2.0.0")
      repo.import(file, "1234".toCharArray(), "아내")
    }
    val vm = com.moneyflow.ui.family.FamilyViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext(), repo, bus, clock)
    shot("20_family", false) { com.moneyflow.ui.family.FamilyScreen({}, viewModel = vm) }
    other.close()
  }
}
