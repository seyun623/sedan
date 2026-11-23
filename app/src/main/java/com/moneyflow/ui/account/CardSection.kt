package com.moneyflow.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.CardPerformance
import com.moneyflow.domain.card.CardStatus
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

/** 실적 progress and benefits of a card account. */
@Composable
internal fun CardSection(
  status: CardStatus?,
  profile: CardProfileEntity?,
  benefits: List<CardBenefitEntity>,
  imported: CardPerformance?,
  onEditProfile: () -> Unit,
  onAddBenefit: () -> Unit,
  onEditBenefit: (CardBenefitEntity) -> Unit,
  onOpenCardImport: () -> Unit
) {
  Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.fillMaxWidth().testTag("card_section")) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("카드 실적", fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
        TextButton(onClick = onEditProfile) { Text(if (profile == null) "실적 조건 설정" else "조건 수정") }
      }
      if (status == null || profile == null) {
        Text("전월 실적 기준을 설정하면 이번 기간 실적과 남은 금액을 알려 드려요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
      } else {
        Text("${status.period.label} · ${status.daysLeft}일 남음", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(Formatters.formatCurrency(status.performance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Gray900)
          Text(" / ${Formatters.formatCurrency(status.target)}", color = Gray600, modifier = Modifier.padding(bottom = 2.dp))
        }
        LinearProgressIndicator(
          progress = { status.progress },
          color = if (status.achieved) AccentGreen else if (status.needsAttention) AccentOrange else TossBlue,
          trackColor = Gray200,
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
          if (status.achieved) "이번 기간 실적을 채웠어요 🎉" else "${Formatters.formatCurrency(status.shortage)} 더 쓰면 실적을 채워요",
          color = if (status.achieved) AccentGreen else if (status.needsAttention) AccentOrange else Gray900,
          fontWeight = FontWeight.SemiBold
        )
        Text("지난 기간 실적 ${Formatters.formatCurrency(status.previousPerformance)}", style = MaterialTheme.typography.bodySmall, color = Gray600)
      }
      if (imported != null) {
        Text(
          "카드사 화면 기준: ${imported.label} ${Formatters.formatCurrency(imported.amount)} (${Formatters.formatFullDate(imported.fetchedAt)})",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      TextButton(onClick = onOpenCardImport, modifier = Modifier.padding(top = 2.dp)) { Text("카드사에서 실적·내역 가져오기") }

      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("카드 혜택", fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
        TextButton(onClick = onAddBenefit, modifier = Modifier.testTag("add_benefit")) { Text("혜택 추가") }
      }
      if (benefits.isEmpty()) {
        Text("할인·적립 혜택을 등록하면 이번 기간에 받은 혜택과 남은 한도를 계산해요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
      }
      benefits.forEach { b ->
        val usage = status?.benefits?.firstOrNull { it.benefit.id == b.id }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.title, fontWeight = FontWeight.SemiBold, color = if (b.isActive) Gray900 else Gray600, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onEditBenefit(b) }) { Text("수정") }
          }
          Text(benefitSummary(b), style = MaterialTheme.typography.bodySmall, color = Gray600)
          when {
            !b.isActive -> Text("사용 안 함", style = MaterialTheme.typography.bodySmall, color = Gray600)
            usage == null -> Text("실적 조건을 설정하면 받은 혜택을 계산해요", style = MaterialTheme.typography.bodySmall, color = Gray600)
            !usage.eligible -> Text("지난 기간 실적 미달로 이번 기간 혜택이 없어요", style = MaterialTheme.typography.bodySmall, color = AccentRed)
            else -> {
              Text(
                "이번 기간 ${usage.matchingCount}건 · 받은 혜택 ${Formatters.formatCurrency(usage.earned)}" +
                  (usage.remainingCap?.let { " · 남은 한도 ${Formatters.formatCurrency(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (usage.capReached) AccentOrange else AccentGreen
              )
              if (b.monthlyCap > 0) {
                LinearProgressIndicator(
                  progress = { (usage.earned.toFloat() / b.monthlyCap).coerceIn(0f, 1f) },
                  color = if (usage.capReached) AccentOrange else AccentGreen,
                  trackColor = Gray200,
                  modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
              }
            }
          }
        }
      }
      if (status != null && status.totalBenefit > 0) {
        Text("이번 기간 받은 혜택 합계 ${Formatters.formatCurrency(status.totalBenefit)}", fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}

private fun benefitSummary(b: CardBenefitEntity): String = buildList {
  add(listOfNotNull(b.category, b.merchantKeywords.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "모든 결제" })
  add(if (b.kind == BenefitKind.PERCENT) "${formatPercent(b.value)}%" else "건당 ${Formatters.formatCurrency(b.value)}")
  if (b.monthlyCap > 0) add("월 한도 ${Formatters.formatCurrency(b.monthlyCap)}")
  if (b.minPerformance > 0) add("전월 실적 ${Formatters.formatCurrency(b.minPerformance)} 이상")
  if (b.minAmount > 0) add("${Formatters.formatCurrency(b.minAmount)} 이상 결제")
}.joinToString(" · ")

private fun formatPercent(basisPoints: Long): String =
  if (basisPoints % 100 == 0L) (basisPoints / 100).toString() else "%.1f".format(basisPoints / 100.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardProfileDialog(
  initial: CardProfileEntity?,
  onDismiss: () -> Unit,
  onSave: (target: Long, cycleStartDay: Int, excludedCategories: Set<String>, excludedKeywords: String) -> Unit
) {
  var target by rememberSaveable { mutableStateOf(initial?.performanceTarget?.takeIf { it > 0 }?.toString() ?: "") }
  var cycleDay by rememberSaveable { mutableStateOf((initial?.cycleStartDay ?: 1).toString()) }
  var excluded by rememberSaveable { mutableStateOf(initial?.excludedCategories?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
  var keywords by rememberSaveable { mutableStateOf(initial?.excludedKeywords ?: "관리비,세금,상품권") }
  val day = cycleDay.toIntOrNull()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("실적 조건", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KrwAmountField(value = target, onValueChange = { target = it }, label = "전월 실적 기준 (예: 300,000)", supportingText = "0이나 빈칸으로 저장하면 실적 관리를 꺼요")
        OutlinedTextField(
          value = cycleDay,
          onValueChange = { cycleDay = it.filter(Char::isDigit).take(2) },
          label = { Text("실적 기간 시작일 (1~28)") },
          supportingText = { Text("보통 1일(매월 1일~말일). 결제일 기준 카드는 카드사 안내의 이용기간 시작일") },
          isError = day == null || day !in 1..28,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
        Text("실적에서 빠지는 카테고리", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FinanceCategories.categoriesFor(TransactionType.EXPENSE).forEach { c ->
            FilterChip(selected = c in excluded, onClick = { excluded = if (c in excluded) excluded - c else excluded + c }, label = { Text(c) })
          }
        }
        OutlinedTextField(
          value = keywords,
          onValueChange = { keywords = it },
          label = { Text("실적에서 빠지는 내역 (쉼표로 구분)") },
          supportingText = { Text("메모에 이 단어가 있으면 실적에서 빼요") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      TextButton(enabled = day != null && day in 1..28, onClick = { onSave(target.toLongOrNull() ?: 0, day ?: 1, excluded, keywords); onDismiss() }) {
        Text("저장", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BenefitDialog(
  initial: CardBenefitEntity?,
  onDismiss: () -> Unit,
  onSave: (CardBenefitEntity) -> Unit,
  onDelete: (CardBenefitEntity) -> Unit
) {
  var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
  var category by rememberSaveable { mutableStateOf(initial?.category) }
  var keywords by rememberSaveable { mutableStateOf(initial?.merchantKeywords ?: "") }
  var kind by rememberSaveable { mutableStateOf(initial?.kind ?: BenefitKind.PERCENT) }
  var percent by rememberSaveable { mutableStateOf(initial?.takeIf { it.kind == BenefitKind.PERCENT }?.let { formatPercent(it.value) } ?: "") }
  var fixed by rememberSaveable { mutableStateOf(initial?.takeIf { it.kind == BenefitKind.FIXED }?.value?.toString() ?: "") }
  var cap by rememberSaveable { mutableStateOf(initial?.monthlyCap?.takeIf { it > 0 }?.toString() ?: "") }
  var minPerf by rememberSaveable { mutableStateOf(initial?.minPerformance?.takeIf { it > 0 }?.toString() ?: "") }
  var minAmount by rememberSaveable { mutableStateOf(initial?.minAmount?.takeIf { it > 0 }?.toString() ?: "") }
  var active by rememberSaveable { mutableStateOf(initial?.isActive ?: true) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }

  val value: Long? = when (kind) {
    BenefitKind.PERCENT -> percent.toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }?.let { Math.round(it * 100) }
    BenefitKind.FIXED -> fixed.toLongOrNull()?.takeIf { it > 0 }
  }
  val valid = title.isNotBlank() && value != null

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "혜택 추가" else "혜택 수정", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it.take(30) }, label = { Text("이름 (예: 카페 10% 할인)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("benefit_title"))
        Text("적용 카테고리", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = category == null, onClick = { category = null }, label = { Text("전체") })
          FinanceCategories.categoriesFor(TransactionType.EXPENSE).forEach { c ->
            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
          }
        }
        OutlinedTextField(
          value = keywords, onValueChange = { keywords = it },
          label = { Text("가맹점 키워드 (선택, 쉼표로 구분)") },
          supportingText = { Text("예: 스타벅스,투썸 — 입력하면 이 가맹점에서만 적용") },
          modifier = Modifier.fillMaxWidth()
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          BenefitKind.entries.forEachIndexed { i, k ->
            SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, BenefitKind.entries.size)) { Text(k.label) }
          }
        }
        if (kind == BenefitKind.PERCENT) {
          OutlinedTextField(
            value = percent, onValueChange = { percent = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
            label = { Text("할인·적립률 (%)") }, suffix = { Text("%") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
          )
        } else {
          KrwAmountField(value = fixed, onValueChange = { fixed = it }, label = "결제 1건당 혜택")
        }
        KrwAmountField(value = cap, onValueChange = { cap = it }, label = "월 혜택 한도 (없으면 비워 두기)")
        KrwAmountField(value = minPerf, onValueChange = { minPerf = it }, label = "필요한 전월 실적 (없으면 비워 두기)")
        KrwAmountField(value = minAmount, onValueChange = { minAmount = it }, label = "최소 결제 금액 (없으면 비워 두기)")
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("사용", modifier = Modifier.weight(1f))
          Switch(checked = active, onCheckedChange = { active = it })
        }
        if (initial != null) {
          TextButton(onClick = { confirmDelete = true }) { Text("혜택 삭제", color = AccentRed) }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = {
        onSave(
          CardBenefitEntity(
            id = initial?.id ?: 0, accountId = initial?.accountId ?: 0, title = title.trim(), category = category,
            merchantKeywords = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
            kind = kind, value = value ?: 0, monthlyCap = cap.toLongOrNull() ?: 0, minPerformance = minPerf.toLongOrNull() ?: 0,
            minAmount = minAmount.toLongOrNull() ?: 0, isActive = active
          )
        )
        onDismiss()
      }) { Text("저장", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
  if (confirmDelete && initial != null) {
    ConfirmDialog(
      title = "혜택을 삭제할까요?",
      message = "'${initial.title}' 혜택이 삭제돼요. 가계부 내역은 그대로예요.",
      confirmLabel = "삭제",
      onConfirm = { onDelete(initial); onDismiss() },
      onDismiss = { confirmDelete = false }
    )
  }
}
