package com.moneyflow.ui.family

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.FamilyMemberEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.repository.FamilyMonth
import com.moneyflow.data.repository.FamilyRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.MonthSwitcher
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.settings.SettingsItem
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repo: FamilyRepository,
  private val messages: UserMessageBus,
  clock: AppClock
) : ViewModel() {
  private val _month = MutableStateFlow(clock.currentMonth())
  val month: StateFlow<YearMonth> = _month.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val summary: StateFlow<FamilyMonth?> = _month.flatMapLatest { repo.month(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
  val members: StateFlow<List<FamilyMemberEntity>> = repo.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  val sharedGoals: StateFlow<List<com.moneyflow.data.repository.SharedGoal>> =
    repo.sharedGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()
  private val _errors = Channel<String>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  fun moveMonth(delta: Long) { _month.value = _month.value.plusMonths(delta) }

  fun import(uri: Uri, password: String, name: String) = viewModelScope.launch {
    if (_busy.value) return@launch
    _busy.value = true
    try {
      val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("파일을 열 수 없어요.")
      }
      val r = repo.import(bytes, password.toCharArray(), name)
      messages.show(if (r.updated) "${r.member.name}님의 기록을 새 백업으로 바꿨어요. (${r.transactionCount}건)" else "${r.member.name}님의 기록 ${r.transactionCount}건을 불러왔어요.")
    } catch (e: Exception) {
      _errors.send(e.message ?: "불러오지 못했어요.")
    } finally {
      _busy.value = false
    }
  }

  fun setBudget(amount: Long) = viewModelScope.launch {
    repo.setBudget(amount)
    messages.show(if (amount > 0) "가족 예산을 ${Formatters.formatCurrency(amount)}(으)로 정했어요." else "가족 예산을 지웠어요.")
  }

  fun delete(member: FamilyMemberEntity) = viewModelScope.launch {
    repo.delete(member)
    messages.show("${member.name}님의 기록을 지웠어요.")
  }
}

/** 가족 합산: this phone + each family member's latest backup, month by month. */
@Composable
fun FamilyScreen(onBack: () -> Unit, viewModel: FamilyViewModel = hiltViewModel()) {
  val month by viewModel.month.collectAsStateWithLifecycle()
  val summary by viewModel.summary.collectAsStateWithLifecycle()
  val members by viewModel.members.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val sharedGoals by viewModel.sharedGoals.collectAsStateWithLifecycle()
  var pickedUri by rememberSaveable { mutableStateOf<String?>(null) }
  /** Name suggested when updating an existing member. */
  var suggestedName by rememberSaveable { mutableStateOf("") }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var deleting by remember { mutableStateOf<FamilyMemberEntity?>(null) }
  var managing by remember { mutableStateOf<FamilyMemberEntity?>(null) }
  var editBudget by rememberSaveable { mutableStateOf(false) }

  val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pickedUri = uri.toString() }
  LaunchedEffect(viewModel) { viewModel.errors.collect { error = it } }

  SettingsScaffold(title = "가족 합산", onBack = onBack) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)
    MonthSwitcher(
      label = Formatters.formatMonth(month),
      onPrevious = { viewModel.moveMonth(-1) },
      onNext = { viewModel.moveMonth(1) },
      modifier = Modifier.padding(horizontal = 8.dp)
    )
    summary?.let { s -> FamilySummary(s, onEditBudget = { editBudget = true }) }

    if (members.isNotEmpty()) SettingsSection("함께 모으는 목표") {
      if (sharedGoals.isEmpty()) {
        Text(
          "가족이 각자 예산·목표 탭에서 같은 이름의 저축 목표(예: 가족 여행)를 만들면, 여기서 함께 모은 금액을 보여 드려요.",
          style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp)
        )
      }
      sharedGoals.forEach { g -> SharedGoalRow(g) }
    }

    SettingsSection("가족 기록") {
      members.forEach { m ->
        SettingsItem(
          title = m.name,
          subtitle = "${Formatters.formatDateTime(m.backupCreatedAt)} 백업 기준",
          icon = Icons.Filled.FamilyRestroom,
          onClick = { managing = m }
        )
      }
      SettingsItem(
        title = "가족 백업 파일 불러오기",
        subtitle = "가족이 자기 휴대폰에서 만든 백업 파일(.mfbackup)을 골라요",
        icon = Icons.Filled.PersonAdd,
        enabled = !busy,
        showChevron = false,
        onClick = { suggestedName = ""; openFile.launch(arrayOf("*/*")) },
        modifier = Modifier.testTag("family_add")
      )
    }

    SettingsSection("알아 두세요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "서버 없이 백업 파일로 합쳐 봐요. 가족이 '백업 파일 보내기'로 보내 준 파일과 그 비밀번호가 필요해요.",
          "불러온 가족 기록은 보기 전용이에요. 이 휴대폰의 가계부·잔액에는 섞이지 않아요.",
          "가족 기록은 불러온 백업 시점까지예요. 같은 사람의 새 백업을 불러오면 바뀌어요.",
          "계좌 사이 이체는 빼고 수입·지출만 합쳐요."
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(vertical = 3.dp)) }
      }
    }
  }

  pickedUri?.let { uri ->
    FamilyImportDialog(
      initialName = suggestedName,
      onDismiss = { pickedUri = null },
      onSubmit = { pw, name -> pickedUri = null; viewModel.import(Uri.parse(uri), pw, name) }
    )
  }
  if (editBudget) {
    var value by rememberSaveable { mutableStateOf(summary?.budget?.takeIf { it > 0 }?.toString() ?: "") }
    AlertDialog(
      onDismissRequest = { editBudget = false },
      title = { Text("가족 한 달 예산", fontWeight = FontWeight.Bold) },
      text = {
        com.moneyflow.ui.common.KrwAmountField(value = value, onValueChange = { value = it }, label = "가족 전체 예산", supportingText = "비워 두면 가족 예산을 쓰지 않아요")
      },
      confirmButton = { TextButton(onClick = { editBudget = false; viewModel.setBudget(value.toLongOrNull() ?: 0) }) { Text("저장", fontWeight = FontWeight.Bold) } },
      dismissButton = { TextButton(onClick = { editBudget = false }) { Text("취소") } }
    )
  }
  managing?.let { m ->
    AlertDialog(
      onDismissRequest = { managing = null },
      title = { Text(m.name, fontWeight = FontWeight.Bold) },
      text = { Text("${Formatters.formatDateTime(m.backupCreatedAt)}에 만든 백업을 보고 있어요. 새 백업 파일을 받았다면 바꿔 주세요.") },
      confirmButton = {
        TextButton(onClick = { managing = null; suggestedName = m.name; openFile.launch(arrayOf("*/*")) }) { Text("새 백업으로 바꾸기", fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { managing = null; deleting = m }) { Text("기록 지우기", color = AccentRed) } }
    )
  }
  deleting?.let { m ->
    ConfirmDialog(
      title = "${m.name}님의 기록을 지울까요?",
      message = "이 휴대폰에 불러온 가족 기록만 지워져요. 가족의 휴대폰에는 영향이 없어요.",
      confirmLabel = "지우기",
      onConfirm = { deleting = null; viewModel.delete(m) },
      onDismiss = { deleting = null }
    )
  }
  error?.let { msg ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("알림", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }
    )
  }
}

@Composable
private fun FamilySummary(s: FamilyMonth, onEditBudget: () -> Unit) {
  androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    color = com.moneyflow.ui.theme.CardSurface,
    modifier = Modifier.fillMaxWidth().testTag("family_summary")
  ) {
    Column(Modifier.padding(20.dp)) {
      Text("가족 전체 지출", color = Gray600, style = MaterialTheme.typography.labelLarge)
      Text(Formatters.formatCurrency(s.totalExpense), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = Gray900)
      Text("수입 ${Formatters.formatCurrency(s.totalIncome)}", color = AccentGreen, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(8.dp))
      if (s.budget > 0) {
        val left = s.budget - s.totalExpense
        LinearProgressIndicator(
          progress = { (s.totalExpense.toFloat() / s.budget).coerceIn(0f, 1f) },
          color = if (left < 0) AccentRed else TossBlue,
          trackColor = Gray200,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (left >= 0) "가족 예산 ${Formatters.formatCurrency(s.budget)} 중 ${Formatters.formatCurrency(left)} 남음"
            else "가족 예산보다 ${Formatters.formatCurrency(-left)} 더 썼어요",
            color = if (left < 0) AccentRed else Gray600, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
          )
          TextButton(onClick = onEditBudget) { Text("수정") }
        }
      } else {
        TextButton(onClick = onEditBudget, modifier = Modifier.testTag("family_budget")) { Text("가족 예산 정하기") }
      }
      Spacer(Modifier.height(4.dp))
      s.people.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(p.name, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Column(horizontalAlignment = Alignment.End) {
            Text("지출 ${Formatters.formatCurrency(p.expense)}", color = Gray900, style = MaterialTheme.typography.bodyMedium)
            Text("수입 ${Formatters.formatCurrency(p.income)}", color = Gray600, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      if (s.categories.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("함께 많이 쓴 곳", fontWeight = FontWeight.Bold, color = Gray900)
        val max = s.categories.first().total.coerceAtLeast(1)
        val showPeople = s.people.size > 1
        s.categories.take(6).forEach { c ->
          Column(Modifier.padding(vertical = 4.dp)) {
            Row {
              Text(c.category, color = Gray900, modifier = Modifier.weight(1f))
              Text(Formatters.formatCurrency(c.total), color = Gray600)
            }
            LinearProgressIndicator(
              progress = { c.total.toFloat() / max },
              color = Color(FinanceCategories.getColorForCategory(c.category)),
              trackColor = Gray200,
              modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
            )
            if (showPeople) {
              Text(
                c.byPerson.joinToString(" · ") { (p, a) -> "$p ${Formatters.formatCurrency(a)}" },
                style = MaterialTheme.typography.labelSmall, color = Gray600, modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FamilyImportDialog(initialName: String, onDismiss: () -> Unit, onSubmit: (password: String, name: String) -> Unit) {
  var name by rememberSaveable { mutableStateOf(initialName) }
  var pw by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("가족 백업 불러오기", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(10) }, label = { Text("이름 (예: 엄마, 남편)") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("family_name")
        )
        OutlinedTextField(
          value = pw, onValueChange = { pw = it }, label = { Text("그 백업 파일의 비밀번호") }, singleLine = true,
          visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth().testTag("family_password")
        )
      }
    },
    confirmButton = { TextButton(enabled = name.isNotBlank() && pw.length >= 4, onClick = { onSubmit(pw, name) }) { Text("불러오기", fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}

@Composable
private fun SharedGoalRow(g: com.moneyflow.data.repository.SharedGoal) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("shared_goal")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(g.title, fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("${(g.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = TossBlue)
    }
    LinearProgressIndicator(progress = { g.progress }, color = TossBlue, trackColor = Gray200, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    Text(
      "${Formatters.formatCurrency(g.saved)} / ${Formatters.formatCurrency(g.target)}" +
        if (g.saved < g.target) " · ${Formatters.formatCurrency(g.target - g.saved)} 남음" else " · 달성!",
      style = MaterialTheme.typography.bodySmall, color = Gray900
    )
    Text(g.byPerson.joinToString(" · ") { (p, a) -> "$p ${Formatters.formatCurrency(a)}" }, style = MaterialTheme.typography.labelSmall, color = Gray600)
  }
}
