package com.moneyflow.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.report.CardDeduction
import com.moneyflow.domain.report.DeductionKind
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaxPreviewState(val year: Int = 0, val result: CardDeduction.Result? = null, val salary: Long = 0)

@HiltViewModel
class TaxPreviewViewModel @Inject constructor(
  db: AppDatabase,
  private val settings: SettingsRepository,
  clock: AppClock
) : ViewModel() {
  private val today = clock.today()

  val state: StateFlow<TaxPreviewState> = combine(
    settings.settings, db.transactionDao().observeAll(), db.accountDao().observeAll()
  ) { s, txs, accounts ->
    val start = MonthKeys.startMillis(java.time.YearMonth.of(today.year, 1), clock.zone)
    val yearTxs = txs.filter { it.timestamp >= start }
    TaxPreviewState(
      today.year,
      if (s.annualSalary > 0) CardDeduction.estimate(s.annualSalary, yearTxs, accounts, today.monthValue) else null,
      s.annualSalary
    )
  }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaxPreviewState())

  fun setSalary(amount: Long) = viewModelScope.launch { settings.setAnnualSalary(amount) }
}

/** 연말정산 신용카드 등 소득공제 미리보기 (from this year's ledger; an estimate). */
@Composable
fun TaxPreviewScreen(onBack: () -> Unit, viewModel: TaxPreviewViewModel = hiltViewModel()) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  SettingsScaffold(title = "${s.year}년 카드 소득공제 미리보기", onBack = onBack) {
    SettingsSection("총급여 (세전 연봉)") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        var text by androidx.compose.runtime.saveable.rememberSaveable(s.salary > 0) { androidx.compose.runtime.mutableStateOf(if (s.salary > 0) s.salary.toString() else "") }
        KrwAmountField(
          value = text,
          onValueChange = { v -> text = v.filter(Char::isDigit).take(11); viewModel.setSalary(text.toLongOrNull() ?: 0L) },
          label = "총급여",
          modifier = Modifier.fillMaxWidth().testTag("tax_salary")
        )
        Text("원천징수영수증의 '총급여'예요. 이 휴대폰에만 저장돼요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
      }
    }
    val r = s.result
    if (r != null) {
      SettingsSection("올해 사용액 (${s.year}.1.1 ~ 오늘)") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("공제 문턱 (총급여의 25%): ${Formatters.formatCurrency(r.threshold)}", color = Gray900)
          LinearProgressIndicator(
            progress = { if (r.threshold <= 0) 1f else (r.total.toFloat() / r.threshold).coerceIn(0f, 1f) },
            color = TossBlue,
            modifier = Modifier.fillMaxWidth().testTag("tax_threshold_progress")
          )
          Text(
            if (r.toThreshold > 0) "문턱까지 ${Formatters.formatCurrency(r.toThreshold)} 남았어요" else "문턱을 넘었어요",
            fontWeight = FontWeight.SemiBold, color = Gray900
          )
          DeductionKind.entries.forEach { k ->
            Row(Modifier.fillMaxWidth()) {
              Text("${k.label} (${(k.rate * 100).toInt()}%)", color = Gray600, modifier = Modifier.weight(1f))
              Text(Formatters.formatCurrency(r.byKind[k] ?: 0), color = Gray900)
            }
          }
        }
      }
      SettingsSection("예상 소득공제") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("지금까지: ${Formatters.formatCurrency(r.deduction)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.testTag("tax_deduction"))
          Text("이 속도로 연말까지 쓰면: ${Formatters.formatCurrency(r.projectedDeduction)}", color = Gray600)
          Text("💡 ${CardDeduction.advice(r)}", color = Gray900)
          Text(
            "가계부 기록으로 계산한 예상치예요. 보험료·세금·관리비·상품권 구입·해외 결제는 빼고, 결제수단·가맹점 이름으로 체크카드·전통시장·대중교통을 나눠요. " +
              "실제 금액은 국세청 연말정산 간소화 자료로 확인해 주세요.",
            style = MaterialTheme.typography.bodySmall, color = Gray600
          )
        }
      }
    }
  }
}
