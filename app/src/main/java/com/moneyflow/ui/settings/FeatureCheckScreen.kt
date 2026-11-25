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
  val all = listOf(
    FeatureCheck(
      "capture", "결제 알림 자동 등록", "2.13",
      listOf(
        "'열기'를 눌러 맨 위 점검 목록이 모두 ✅인지 봐 주세요. ⚠가 있으면 그 줄을 눌러 고쳐요.",
        "'시험 알림 보내기'를 누르고 10초 안에 버튼 아래에 뜨는 문구(✅/⚠)를 확인해 주세요.",
        "실제로 카드를 한 번 쓴 뒤 '최근 받은 결제 알림'에 '기록됨'이 뜨는지 봐 주세요."
      ),
      CheckTarget.AUTO_CAPTURE, fullOnly = true
    ),
    FeatureCheck(
      "bill", "카드 결제일 미리 알림", "2.6",
      listOf(
        "자산 탭에서 카드를 열어 '결제일'이 입력돼 있는지 확인해 주세요.",
        "아래 '시험 알림 보내기'를 누르면 결제일 알림이 지금 바로 와요. (진짜 알림은 결제일 3일 전에 와요)"
      )
    ),
    FeatureCheck(
      "networth", "순자산 그래프", "2.7",
      listOf("'열기'를 누른 뒤 아래로 내리면 '순자산 추이' 그래프가 있어요. 최근 12개월이 보이는지 봐 주세요."),
      CheckTarget.ANALYTICS
    ),
    FeatureCheck(
      "yearly", "연간 결산 이미지 공유", "2.7",
      listOf("'열기' → '이미지로 공유'를 눌러 카카오톡 등으로 이미지가 보내지는지 봐 주세요. (나에게 보내기로 시험)"),
      CheckTarget.YEARLY
    ),
    FeatureCheck(
      "simple", "간단 모드 (부모님용 큰 화면)", "2.8",
      listOf(
        "설정 → 화면 → '간단 모드'를 켜면 큰 글씨 화면이 나와요.",
        "간단 모드 화면 맨 아래 '일반 화면으로 돌아가기'를 누르면 원래대로 돌아와요."
      ),
      CheckTarget.SETTINGS, openLabel = "설정 열기"
    ),
    FeatureCheck(
      "drive_restore", "드라이브의 내 가족 파일로 복원 (선택)", "2.11",
      listOf(
        "지금 기록을 덮어쓰니 꼭 필요할 때만 해 주세요. (먼저 백업 권장)",
        "'열기' → '드라이브의 내 가족 파일로 복원' → 가족 비밀번호 입력."
      ),
      CheckTarget.DRIVE
    ),
    FeatureCheck(
      "card_import", "카드사 가져오기", "2.14",
      listOf(
        "현대: PC 화면으로 열려요. 현대카드 앱 인증번호로 로그인 → 앱 다녀와서 잠금을 풀어도 화면이 그대로인지.",
        "KB국민: 아래 버튼이 접혀 '다른 수단으로 로그인'이 눌리는지.",
        "롯데: 화면이 안 눌리면 오른쪽 위 ⋮ → '가림막 걷기'.",
        "하나: 앱으로 넘어가지 않고 웹에 머무는지.",
        "우리·농협·IBK: 로그인 후 '메뉴 찾기'로 이용내역 화면이 열리는지."
      ),
      CheckTarget.CARD_IMPORT
    ),
    FeatureCheck(
      "swipe_delete", "밀어서 삭제 확인", "2.14",
      listOf("아래 시험용 내역을 왼쪽으로 밀어 보세요. '이 내역을 지울까요?'가 뜨면 정상이에요. (시험용이라 실제로 지워지지 않아요)")
    ),
    FeatureCheck(
      "rules", "규칙 저장 시 기존 내역 바꾸기", "2.14.1",
      listOf(
        "'열기' → '앱이 기억한 분류'에서 기타지출인 가게(예: 후라토식당)를 눌러 카테고리를 골라 저장해 주세요.",
        "'이미 있던 내역 N건도 바꿨어요'가 뜨고, 목록과 가계부에서 바뀌었는지 봐 주세요."
      ),
      CheckTarget.RULES
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
