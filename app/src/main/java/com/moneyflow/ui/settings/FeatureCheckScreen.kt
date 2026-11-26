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
  /** Only what still needs a check on a real phone (confirmed items are removed). Version 3.0.0. */
  val all = listOf(
    FeatureCheck(
      "start_guide", "홈 '시작하기'·설정 정리", "3.0",
      listOf(
        "홈 위쪽 '시작하기'에서 안 한 항목을 누르면 그 화면으로 가는지, '닫기'를 누르면 사라지는지 봐 주세요.",
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
      "card_points", "포인트 모아보기", "2.20",
      listOf("카드사 가져오기 → 카드사 로그인 → 포인트 조회 화면에서 '이 화면 가져오기' → 미리보기의 포인트를 저장 → 자산 탭 '포인트 모아보기'에 나오는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "weekly_report", "주간 소비 리포트", "2.19",
      listOf("설정 → 알림 → '매주 월요일 주간 소비 리포트'를 켜 두면, 월요일(늦어도 수요일)에 '지난주 ○○원 썼어요, 평소보다 …' 알림이 오는지 봐 주세요."),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "receipt", "영수증 사진으로 입력", "2.18",
      listOf(
        "+ 버튼(내역 추가) → '📷 영수증 찍기'로 영수증을 찍거나 '앨범에서 고르기'로 사진을 고르세요.",
        "금액·날짜·메모(가게 · 품목)가 채워지는지 봐 주세요. 틀리면 어떤 영수증(마트·카페 등)이었는지 적어 주세요."
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
      "bc_members", "우리·농협·IBK → BC카드로 가져오기", "2.17.2",
      listOf("BC 마크가 있는 우리·농협·IBK 카드가 있으면: 카드사 가져오기 → '우리·농협·IBK 카드 (BC카드로)'에서 그 카드 → BC카드 로그인 → 카드 승인내역 → '이 화면 가져오기' → 그 카드 계좌에 기록되는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "capture_no_account", "결제수단이 없는 결제 기록", "2.17.1",
      listOf(
        "계좌로 등록하지 않은 카드·페이(카카오페이·네이버페이 등)로 결제해 보세요.",
        "'최근 받은 결제 알림'에 '기록됨'이 뜨고, 자산 탭에 그 이름의 계좌(또는 '기타 결제수단')가 생기는지 봐 주세요. 안 되면 그 줄의 문구를 적어 주세요."
      ),
      CheckTarget.AUTO_CAPTURE, fullOnly = true
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
      "card_needed_only", "필요한 것만 가져오기", "2.15",
      listOf("카드사 가져오기 → 하나카드(휴대폰 화면) 로그인 → 아래 '필요한 것만 가져오기' → 미리보기에 이용내역·결제 예정 금액이 나오는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "kb_login", "KB국민카드 로그인", "2.17.1",
      listOf("카드사 가져오기 → KB국민카드: 처음엔 아래 버튼이 접혀 있어요. '다른 수단으로 로그인'이 화면 아래에서 올라오는지 봐 주세요. 버튼을 다시 열려면 위쪽 '가져오기'를 누르세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "shop_orders", "쇼핑몰 주문 → 상품명 붙이기", "2.16",
      listOf("카드사 가져오기 → 쇼핑몰 주문내역(쿠팡 등) 로그인 → 주문목록에서 '이 화면 가져오기' → 결제에 상품 이름이 붙는지 봐 주세요."),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "benefit_search", "카드 혜택 자동 찾기", "2.17.1",
      listOf("자산 탭 → 카드 → 카드 혜택 → '혜택 찾기' → 카드 이름(예: 신한카드 Deep Dream)을 넣고 '찾기' → 혜택 목록이 나오는지 봐 주세요."),
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
      "security_pin", "보안: PIN 확인", "2.15.6",
      listOf("앱 PIN을 쓰는 중이면: 설정 → 보안 → 생체 인증 켜기 / 복구 코드 새로 만들기를 누를 때 '현재 PIN 확인'이 먼저 뜨는지 봐 주세요."),
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
  private val messages: UserMessageBus
) : ViewModel() {
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
