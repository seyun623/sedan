package com.moneyflow.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.BuildConfig
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.alerts.BillReminders
import com.moneyflow.domain.alerts.Reminder
import com.moneyflow.domain.alerts.ReminderChannel
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.theme.AccentGreenText
import com.moneyflow.ui.theme.AccentRedText
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.work.Notifications
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Where a check's "열기" button goes. */
enum class CheckTarget { AUTO_CAPTURE, ANALYTICS, YEARLY, DRIVE, CARD_IMPORT, RULES, SETTINGS }

/** A feature waiting to be tried on a real phone (docs/verification-checklist.md). */
data class FeatureCheck(
  val id: String,
  val title: String,
  val version: String,
  val steps: List<String>,
  val open: CheckTarget? = null,
  val openLabel: String = "열기",
  /** Only in the 자동 등록판. */
  val fullOnly: Boolean = false
)

/** The user's answer to one check. */
data class CheckResult(val ok: Boolean, val note: String = "")

object FeatureChecks {
  /** Only what still needs a check on a real phone (confirmed items are removed). Version 3.1.0. */
  val all = listOf(
    FeatureCheck(
      "backup_ai", "예비 AI (Gemini → Groq)", "3.1",
      listOf(
        "설정 → AI 카테고리 추천 → '예비 AI'에서 Groq '키 받으러 가기' → 키 복사 → '복사한 키 넣기' → '✅ 키 등록됨'이 뜨는지 봐 주세요.",
        "Gemini 한도가 찼을 때(혜택 찾기 등) '한도가 차서 Groq로 처리했어요'가 뜨고 결과가 나오는지 봐 주세요."
      ),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "start_guide", "홈 '시작하기'·설정 정리", "3.0.1",
      listOf(
        "할 일을 이미 다 해 두셔서 '시작하기'가 안 보였던 거예요. 아래 '시작하기 다시 보이기'를 누르고 홈에서 ✅ 표시와 '닫기'를 봐 주세요.",
        "설정에서 '기록 도구'가 맨 위에 있고, '알림 설정'을 누르면 알림 6가지가 펼쳐지는지 봐 주세요."
      ),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "family_more", "가족 정산 기록·가족 예산 알림", "2.21",
      listOf(
        "설정 → 가족 합산 → 가족 정산의 '보냈어요'를 눌러 ✅가 붙는지 봐 주세요.",
        "가족 합산 화면에서 가족 예산을 정하고 예산 알림을 켜 두면, 가족 전체 지출이 80%를 넘을 때 알림이 오는지 봐 주세요."
      ),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "card_points", "포인트 모아보기", "3.0.1",
      listOf("카드사 로그인 → ⋮ → '이용내역 메뉴 찾기'에 이제 포인트 메뉴도 나와요. 포인트 화면에서 '이 화면 가져오기' → 미리보기 맨 아래 포인트를 저장 → 자산 탭 '포인트 모아보기'에 나오는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "weekly_report", "주간 소비 리포트", "2.19",
      listOf("설정 → 알림 → '매주 월요일 주간 소비 리포트'를 켜 두면, 월요일(늦어도 수요일)에 '지난주 ○○원 썼어요, 평소보다 …' 알림이 오는지 봐 주세요."),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "receipt", "영수증 사진으로 입력", "3.0.1",
      listOf(
        "+ 버튼(내역 추가) → '📷 영수증 찍기'로 영수증을 찍거나 '앨범에서 고르기'로 사진을 고르세요.",
        "금액·날짜·메모(가게 · 품목)가 채워지는지 봐 주세요. 큰 사진도 줄여서 읽고, 휴대폰에서 못 읽으면 Gemini로 사진을 읽어요. 틀리면 어떤 영수증이었는지와 화면에 뜬 문구를 적어 주세요."
      )
    ),
    FeatureCheck(
      "budget_carry", "예산 이월", "2.18",
      listOf("예산 탭 → 남은 예산 카드의 '남은 예산 다음 달로 넘기기'를 켜면, 지난달 남은(초과한) 금액이 이번 달 예산에 더해지고(빠지고) 아래에 '+ 지난달 남은 …'이 보이는지 봐 주세요. 지난달·이번 달 모두 예산이 있어야 해요.")
    ),
    FeatureCheck(
      "family_settle", "가족 정산", "2.18",
      listOf("같이 쓴 지출의 메모에 #공동 을 붙이고 (가족도 각자), 설정 → 가족 합산 → '가족 정산'에 누가 누구에게 얼마 보내면 되는지 나오는지 봐 주세요. '정산 내용 복사'로 카톡에 붙여 넣을 수 있어요."),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "bc_members", "우리·농협·IBK → BC카드로 가져오기", "3.0.1",
      listOf("BC 마크가 있는 우리·농협·IBK 카드가 있으면: 카드사 가져오기 → '우리·농협·IBK 카드 (BC카드로)'에서 그 카드 → BC카드 로그인 → 카드 승인내역 → '이 화면 가져오기' → 미리보기에서 그 카드 내역만 골라 가져온 뒤, 남은 내역을 다른 카드로 이어서 가져올 수 있는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "next_bill", "다음 결제 금액 (청구 안내)", "2.15",
      listOf(
        "카드사에서 '결제 예정 금액 안내' 카톡·문자가 오면 (보통 결제일 1~2주 전) 결제 알림 자동 등록 화면의 목록에 '청구 안내 · … 저장'이 뜨는지 봐 주세요.",
        "자산 탭 → 그 카드를 열면 맨 위 '다음 결제'에 금액과 결제일이 보이는지 봐 주세요."
      ),
      CheckTarget.AUTO_CAPTURE, fullOnly = true
    ),
    FeatureCheck(
      "card_needed_only", "필요한 것만 가져오기", "3.0.1",
      listOf("카드사 로그인 → '필요한 것만 가져오기' → 미리보기에 이번 달 결제 예정 금액과 최근 1달 이용내역만 나오는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "kb_login", "KB국민카드 로그인", "3.0.1",
      listOf("카드사 가져오기 → KB국민카드 → '다른 로그인 방법 선택'을 누르면, 화면 밖에 걸리던 아래 창이 위로 올라와 고를 수 있는지 봐 주세요. (키보드를 열 때 화면이 검게 변하던 것도 고쳤어요)"),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "shop_orders", "쇼핑몰 주문 → 상품명 붙이기", "2.16",
      listOf("카드사 가져오기 → 쇼핑몰 주문내역(쿠팡 등) 로그인 → 주문목록에서 '이 화면 가져오기' → 결제에 상품 이름이 붙는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "benefit_search", "카드 혜택 자동 찾기", "3.0.1",
      listOf(
        "자산 탭 → 카드 → '혜택 찾기' → 카드 이름을 넣고 '찾기' → 혜택과 함께 '실적 조건도 저장'(전월 실적·실적 제외)이 나오는지 봐 주세요.",
        "한도 초과가 뜨면 다른 Gemini 모델로 자동으로 다시 시도해요. 그래도 안 되면 '한도가 풀리면 자동으로 찾아 등록하기'를 눌러 두고, 나중에 알림이 오는지 봐 주세요."
      ),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "vouchers", "지역화폐·온누리상품권", "2.17",
      listOf("지역화폐·온누리로 결제한 뒤 결제수단이 '지역화폐'/'온누리상품권'으로 기록되는지 봐 주세요. 안 되면 결제 알림 자동 등록 → '추가로 읽을 앱'에서 그 앱을 골라 주세요."),
      CheckTarget.AUTO_CAPTURE, fullOnly = true
    ),
    FeatureCheck(
      "tax_preview", "연말정산 카드 소득공제 미리보기", "2.17",
      listOf("소비분석 탭 맨 아래 → '연말정산 카드 소득공제 미리보기' → 총급여를 넣으면 문턱·예상 공제액·안내가 나오는지 봐 주세요."),
      CheckTarget.ANALYTICS
    ),
    FeatureCheck(
      "security_pin", "보안: PIN 확인·지문으로 열기", "3.0.1",
      listOf("앱 PIN을 쓰는 중이면: 설정 → 보안 → 생체 인증 켜기 / 복구 코드 새로 만들기를 누를 때 '현재 PIN 확인'이 먼저 뜨는지 봐 주세요.", "휴대폰 잠금으로 열기를 쓰면: 설정 → 앱 잠금 및 보안 → '지문으로 열기'를 켜고, 앱을 다시 열 때 지문을 먼저 묻는지 봐 주세요."),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    )
  )

  fun visible(autoCaptureAvailable: Boolean) = all.filter { autoCaptureAvailable || !it.fullOnly }

  /** Text to paste into the chat: every check with its answer. */
  fun report(checks: List<FeatureCheck>, results: Map<String, CheckResult>, version: String): String =
    buildString {
      append("머니플로우 $version 확인 결과\n")
      checks.forEach { c ->
        val r = results[c.id]
        val mark = when (r?.ok) { true -> "✅"; false -> "⚠"; null -> "⬜" }
        append("$mark ${c.title}")
        if (r != null && r.note.isNotBlank()) append(" — ${r.note.trim()}")
        append('\n')
      }
    }.trimEnd()

  fun encode(results: Map<String, CheckResult>): Set<String> =
    results.map { (id, r) -> "$id\t${if (r.ok) 1 else 0}\t${r.note.replace('\t', ' ').replace('\n', ' ')}" }.toSet()

  fun decode(raw: Set<String>): Map<String, CheckResult> = raw.mapNotNull { line ->
    val p = line.split('\t')
    if (p.size < 2) null else p[0] to CheckResult(p[1] == "1", p.getOrElse(2) { "" })
  }.toMap()
}

@HiltViewModel
class FeatureCheckViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val db: AppDatabase,
  private val clock: AppClock,
  private val messages: UserMessageBus,
  private val settings: com.moneyflow.data.repository.SettingsRepository,
  private val budgets: com.moneyflow.data.repository.BudgetRepository,
  private val processor: com.moneyflow.domain.capture.AutoCaptureProcessor
) : ViewModel() {
  /** Result of a "바로 시험" shown in a dialog (title, text). */
  private val _demo = MutableStateFlow<Pair<String, String>?>(null)
  val demo: StateFlow<Pair<String, String>?> = _demo.asStateFlow()
  fun closeDemo() { _demo.value = null }

  private val stats = com.moneyflow.domain.MonthlyStatsCalculator(clock.zone)

  /** "바로 시험" button of a check, or null. */
  fun trial(id: String): String? = when (id) {
    "weekly_report" -> "지금 보내 보기"
    "budget_carry" -> "이월 미리 보기"
    "family_settle" -> "예시로 보기"
    "family_more" -> "가족 예산 알림 시험"
    "next_bill" -> "시험 청구 안내 넣기"
    "vouchers" -> "시험 문자 읽어 보기"
    "start_guide" -> "시작하기 다시 보이기"
    else -> null
  }

  fun runTrial(id: String) = viewModelScope.launch {
    runCatching {
      when (id) {
        "weekly_report" -> weeklyNow()
        "budget_carry" -> carryPreview()
        "family_settle" -> settleDemo()
        "family_more" -> familyAlertTest()
        "next_bill" -> billTest()
        "vouchers" -> voucherTest()
        "start_guide" -> { settings.resetStartGuide(); messages.show("홈에 '시작하기'를 다시 보여 드려요. 할 일을 모두 해 둔 상태면 '모두 완료'로 보여요.") }
      }
    }.onFailure { messages.show("시험하지 못했어요: ${it.message ?: "오류"}") }
  }

  private fun canNotify(): Boolean = Notifications.canNotify(context).also {
    if (!it) messages.show("알림이 꺼져 있어요. 휴대폰 설정 → 애플리케이션 → 머니플로우 → 알림을 켜 주세요.")
  }

  /** Last week's report right now (any day of the week). */
  private suspend fun weeklyNow() {
    if (!canNotify()) return
    val today = clock.today()
    val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val start = com.moneyflow.domain.alerts.WeeklyReport.lastWeekStart(monday)
    val dao = db.transactionDao()
    suspend fun week(from: java.time.LocalDate) =
      dao.getBetween(com.moneyflow.domain.MonthKeys.dayStartMillis(from, clock.zone), com.moneyflow.domain.MonthKeys.dayStartMillis(from.plusDays(7), clock.zone) - 1)
    val last = week(start)
    val spending = last.filter { it.type.isSpending }
    val earlier = (1..4).map { week(start.minusWeeks(it.toLong())) }.filter { it.isNotEmpty() }.map { stats.budgetExpense(it.filter { t -> t.type.isSpending }) }
    val top = stats.categoryBreakdown(last).firstOrNull()?.let { it.category to it.amount }
    val biggest = spending.filter { it.type == TransactionType.EXPENSE }.maxByOrNull { it.amount }?.let { it.memo.ifBlank { it.category }.take(20) to it.amount }
    val r = com.moneyflow.domain.alerts.WeeklyReport.due(monday, stats.budgetExpense(spending), earlier, top, biggest, emptySet())
    if (r == null) { messages.show("지난주(${start.monthValue}.${start.dayOfMonth}~) 지출이 없어 보낼 리포트가 없어요."); return }
    Notifications.post(context, r.copy(key = "test|weekly", title = "[시험] ${r.title}"))
    messages.show("주간 리포트를 보냈어요. 알림창을 내려 확인해 주세요.")
  }

  private suspend fun carryPreview() {
    val month = clock.currentMonth()
    val budget = budgets.observeBudget(month).first()
    val title = "예산 이월 미리 보기"
    if (!budget.isSet) { _demo.value = title to "이번 달 예산이 없어요. 예산 탭에서 예산을 먼저 정해 주세요."; return }
    val previous = budgets.observeBudget(month.minusMonths(1)).first()
    if (!previous.isSet) { _demo.value = title to "지난달 예산이 없어서 넘길 금액이 없어요. (예산을 처음 정한 달 다음 달부터 이월돼요)"; return }
    val carried = budgets.withCarry(budget, true, clock.zone)
    val c = carried.carry
    val won: (Long) -> String = { com.moneyflow.util.Formatters.formatCurrency(it) }
    _demo.value = title to buildString {
      appendLine("지난달 예산 ${won(previous.total)}")
      appendLine(if (c >= 0) "지난달 남은 금액 ${won(c)}" else "지난달 초과 금액 ${won(-c)}")
      appendLine()
      appendLine("이월을 켜면 이번 달 예산은")
      append("${won(budget.total)} ${if (c >= 0) "+" else "−"} ${won(kotlin.math.abs(c))} = ${won(carried.total)}")
      if (!settings.current().budgetCarryOver) append("\n\n지금은 꺼져 있어요. 예산 탭의 '남은 예산 다음 달로 넘기기'를 켜면 적용돼요.")
    }
  }

  private fun settleDemo() {
    val s = com.moneyflow.domain.family.FamilySettlement.settle(listOf("나" to 90_000L, "엄마" to 30_000L, "아빠" to 0L))
    _demo.value = "가족 정산 예시" to (
      "같이 쓴 돈에 #공동 을 붙였다고 해 볼게요.\n· 나: 장보기 90,000원 #공동\n· 엄마: 외식 30,000원 #공동\n· 아빠: 없음\n\n" +
        com.moneyflow.domain.family.FamilySettlement.message("예시", s) { com.moneyflow.util.Formatters.formatCurrency(it) } +
        "\n\n실제로는 설정 → 가족 합산 → '가족 정산'에 이렇게 나와요."
      )
  }

  private suspend fun familyAlertTest() {
    if (!canNotify()) return
    val s = settings.current()
    val budget = s.familyBudget.takeIf { it > 0 } ?: 2_000_000L
    val r = com.moneyflow.domain.alerts.BudgetReminders.family(clock.currentMonth(), budget, budget * 85 / 100, emptySet())!!
    Notifications.post(context, r.copy(key = "test|family", title = "[시험] ${r.title}"))
    messages.show(
      "가족 예산 85%를 쓴 상황으로 시험 알림을 보냈어요." +
        if (s.familyBudget <= 0) " (가족 예산이 없어 200만 원으로 가정했어요. 가족 합산 화면에서 가족 예산을 정해 두세요)" else ""
    )
  }

  /** A sample "결제 예정 금액" message read like a real one; saved on the matching card. */
  private suspend fun billTest() {
    val card = db.accountDao().getAll().firstOrNull { it.accountType == com.moneyflow.data.model.AccountType.CREDIT_CARD && it.bankName.endsWith("카드") }
      ?: run { messages.show("카드 계좌가 없어요. 자산 탭에서 카드를 먼저 추가해 주세요."); return }
    val due = com.moneyflow.domain.alerts.BillReminders.nextDate(clock.today().plusDays(7), card.paymentDay.takeIf { it in 1..31 } ?: 14)
    val text = "[${card.bankName}] ${card.name} ${due.monthValue}월 ${due.dayOfMonth}일 결제예정금액 123,450원 안내드립니다."
    val outcome = processor.process(com.moneyflow.domain.capture.CaptureChannel.TEST, "test", "시험", "", text, clock.nowMillis())
    if (outcome is com.moneyflow.domain.capture.CaptureOutcome.BillNoticed && outcome.account.id != 0L) {
      com.moneyflow.data.repository.CardBills.save(db, settings, outcome.account.id, outcome.bill, "시험 안내", clock.nowMillis())
    }
    _demo.value = "청구 안내 시험" to (
      "보낸 시험 문자:\n$text\n\n결과: ${com.moneyflow.domain.capture.CaptureLog.describe(outcome).second}" +
        if (outcome is com.moneyflow.domain.capture.CaptureOutcome.BillNoticed) "\n\n자산 탭 → ${outcome.account.name} 맨 위 '다음 결제'에서 확인해 보세요. 진짜 안내가 오면 그 금액으로 바뀌어요." else ""
      )
  }

  /** Sample 지역화폐 / 온누리 messages checked like real ones (nothing saved). */
  private suspend fun voucherTest() {
    val samples = listOf(
      "[경기지역화폐] 결제완료 12,000원 행복마트 잔액 38,000원",
      "[온누리상품권] 15,000원 결제되었습니다. 망원시장 과일가게"
    )
    val lines = samples.map { t ->
      val o = processor.process(com.moneyflow.domain.capture.CaptureChannel.TEST, "test", "시험", "", t, clock.nowMillis())
      "• $t\n  → ${com.moneyflow.domain.capture.CaptureLog.describe(o).second}"
    }
    _demo.value = "지역화폐·온누리 시험 (저장하지 않아요)" to lines.joinToString("\n\n")
  }
  private val prefs = context.getSharedPreferences("feature_checks", Context.MODE_PRIVATE)
  private val _results = MutableStateFlow(FeatureChecks.decode(prefs.getStringSet(KEY, emptySet()).orEmpty()))
  val results: StateFlow<Map<String, CheckResult>> = _results.asStateFlow()

  fun answer(id: String, result: CheckResult?) {
    val next = _results.value.toMutableMap().apply { if (result == null) remove(id) else put(id, result) }
    _results.value = next
    prefs.edit().putStringSet(KEY, FeatureChecks.encode(next)).apply()
  }

  /** Posts the card payment-day reminder now (same text as the real one), for every card with a payment day. */
  fun sendBillTest() = viewModelScope.launch {
    if (!Notifications.canNotify(context)) {
      messages.show("알림이 꺼져 있어요. 휴대폰 설정 → 애플리케이션 → 머니플로우 → 알림을 켜 주세요.")
      return@launch
    }
    val today = clock.today()
    val cards = db.accountDao().getAll().filter { it.paymentDay in 1..31 }
    if (cards.isEmpty()) {
      messages.show("결제일이 입력된 카드가 없어요. 자산 탭 → 카드 → 수정에서 결제일을 넣어 주세요.")
      return@launch
    }
    cards.take(3).forEach { a ->
      val date = BillReminders.nextDate(today, a.paymentDay)
      val days = ChronoUnit.DAYS.between(today, date)
      Notifications.post(
        context,
        Reminder(
          "test|bill|${a.id}", ReminderChannel.BILL, "[시험] ${a.name} 결제일이 다가와요",
          "다음 결제일은 ${date.monthValue}/${date.dayOfMonth} (${if (days == 0L) "오늘" else "${days}일 뒤"})예요. 진짜 알림은 결제일 ${BillReminders.CARD_DAYS_BEFORE}일 전에 와요."
        )
      )
    }
    messages.show("결제일 시험 알림을 ${cards.size.coerceAtMost(3)}개 보냈어요. 알림창을 내려 확인해 주세요.")
  }

  companion object { private const val KEY = "results" }
}

/** Settings → 앱 정보 → 새 기능 확인하기: try what is waiting for a check, record ✅/⚠, copy the results. */
@Composable
fun FeatureCheckScreen(
  onBack: () -> Unit,
  onOpen: (CheckTarget) -> Unit,
  viewModel: FeatureCheckViewModel = hiltViewModel()
) {
  val results by viewModel.results.collectAsStateWithLifecycle()
  val demo by viewModel.demo.collectAsStateWithLifecycle()
  demo?.let { (title, text) ->
    androidx.compose.material3.AlertDialog(
      onDismissRequest = viewModel::closeDemo,
      title = { Text(title, fontWeight = FontWeight.Bold) },
      text = { Text(text) },
      confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::closeDemo) { Text("확인") } }
    )
  }
  val checks = FeatureChecks.visible(BuildConfig.AUTO_CAPTURE_AVAILABLE)
  val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }

  SettingsScaffold(title = "새 기능 확인하기", onBack = onBack) {
    Text(
      "아직 확인하지 못한 기능이에요. 해 보고 ✅ 또는 ⚠를 눌러 주세요. 다 하면 맨 아래 '결과 복사'를 눌러 대화창에 붙여 넣어 주시면 돼요.",
      style = MaterialTheme.typography.bodySmall, color = Gray600,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
    checks.forEach { c ->
      val r = results[c.id]
      SettingsSection(
        (when (r?.ok) { true -> "✅ "; false -> "⚠ "; null -> "" }) + c.title + " (${c.version})"
      ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          c.steps.forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = Gray900)
          }
          if (c.id == "swipe_delete") SwipeDemo()
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            c.open?.let { target ->
              Button(
                onClick = { onOpen(target) },
                colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
                modifier = Modifier.testTag("check_open_${c.id}")
              ) { Text(c.openLabel) }
            }
            viewModel.trial(c.id)?.let { label ->
              OutlinedButton(onClick = { viewModel.runTrial(c.id) }, modifier = Modifier.testTag("check_try_${c.id}")) { Text(label) }
            }
            if (c.id == "bill") {
              Button(
                onClick = viewModel::sendBillTest,
                colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
                modifier = Modifier.testTag("check_bill_test")
              ) { Text("시험 알림 보내기") }
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { viewModel.answer(c.id, if (r?.ok == true) null else CheckResult(true, r?.note.orEmpty())) },
              modifier = Modifier.weight(1f).testTag("check_ok_${c.id}")
            ) { Text(if (r?.ok == true) "✅ 잘 돼요 (취소)" else "✅ 잘 돼요", color = AccentGreenText, fontWeight = FontWeight.Bold) }
            OutlinedButton(
              onClick = { viewModel.answer(c.id, if (r?.ok == false) null else CheckResult(false, r?.note.orEmpty())) },
              modifier = Modifier.weight(1f).testTag("check_bad_${c.id}")
            ) { Text(if (r?.ok == false) "⚠ 안 돼요 (취소)" else "⚠ 안 돼요", color = AccentRedText, fontWeight = FontWeight.Bold) }
          }
          if (r?.ok == false) {
            OutlinedTextField(
              value = r.note,
              onValueChange = { viewModel.answer(c.id, r.copy(note = it.take(200))) },
              label = { Text("어떻게 안 되나요? (짧게)") },
              modifier = Modifier.fillMaxWidth().testTag("check_note_${c.id}")
            )
          }
        }
      }
    }
    SettingsSection(null) {
      SettingsItem(
        title = if (copied) "복사했어요. 대화창에 붙여 넣어 주세요" else "결과 복사",
        subtitle = "위의 ✅/⚠ 결과를 글로 복사해요 (가계부 내용은 들어가지 않아요)",
        onClick = {
          clipboard.setText(androidx.compose.ui.text.AnnotatedString(FeatureChecks.report(checks, results, BuildConfig.VERSION_NAME)))
          copied = true
        },
        modifier = Modifier.testTag("check_copy")
      )
    }
  }
}

/** A sample row that behaves like the ledger's (nothing is really deleted). */
@Composable
private fun SwipeDemo() {
  var round by remember { mutableIntStateOf(0) }
  var deleted by remember { mutableStateOf(false) }
  val sample = remember {
    TransactionEntity(id = -1, type = TransactionType.EXPENSE, amount = 4_500, category = "카페/간식", accountId = 0, timestamp = System.currentTimeMillis(), memo = "시험용 · 카페")
  }
  if (deleted) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("시험용 내역을 지웠어요 (실제 기록은 그대로예요)", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.weight(1f))
      OutlinedButton(onClick = { deleted = false; round++ }) { Text("다시") }
    }
  } else {
    key(round) {
      com.moneyflow.ui.ledger.SwipeToDeleteRow(
        transaction = sample,
        onClick = {},
        onDelete = { deleted = true; CompletableDeferred(true) }
      )
    }
  }
}
