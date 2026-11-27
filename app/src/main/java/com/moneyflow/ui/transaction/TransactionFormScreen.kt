package com.moneyflow.ui.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.ai.CategorySuggestionUiState
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.common.sanitizeAmountInput
import com.moneyflow.ui.theme.AccentPurple
import com.moneyflow.ui.theme.AccentPurpleLight
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.AccentRedLight
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

private const val AI_TYPING_DEBOUNCE_MS = 800L

/** The only add / edit transaction UI. Create mode when there is no transaction id argument. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
  onClose: () -> Unit,
  viewModel: TransactionFormViewModel = hiltViewModel()
) {
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val editing by viewModel.editing.collectAsStateWithLifecycle()
  val editingNotFound by viewModel.editingNotFound.collectAsStateWithLifecycle()
  val suggestion by viewModel.categorySuggestionState.collectAsStateWithLifecycle()
  // The AI suggestion UI appears only when AI is turned on with an API key (Settings → AI 설정).
  val showAi by viewModel.aiReady.collectAsStateWithLifecycle()
  val cardStatuses by viewModel.cardStatuses.collectAsStateWithLifecycle()
  val recentTags by viewModel.recentTags.collectAsStateWithLifecycle()
  val isEditMode = viewModel.isEditMode
  val zone = remember { com.moneyflow.domain.APP_ZONE }

  // ---- Form state (survives rotation / process death) ----
  var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
  var amount by rememberSaveable { mutableStateOf("") }
  var memo by rememberSaveable { mutableStateOf("") }
  var category by rememberSaveable { mutableStateOf(FinanceCategories.categoriesFor(TransactionType.EXPENSE).first()) }
  var accountId by rememberSaveable { mutableStateOf(0L) }
  var toAccountId by rememberSaveable { mutableStateOf(0L) }
  var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
  var paymentMethod by rememberSaveable { mutableStateOf(PAYMENT_METHODS.first()) }
  var excludeFromBudget by rememberSaveable { mutableStateOf(false) }
  var prefilled by rememberSaveable { mutableStateOf(false) }
  var amountError by rememberSaveable { mutableStateOf(false) }
  var appliedSuggestionKey by rememberSaveable { mutableStateOf("") }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showTimePicker by rememberSaveable { mutableStateOf(false) }
  var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
  var installmentMonths by rememberSaveable { mutableStateOf(0) }
  val installmentParts by viewModel.installmentParts.collectAsStateWithLifecycle()
  var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var closed by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val focusManager = LocalFocusManager.current

  fun close() {
    if (closed) return
    closed = true
    onClose()
  }

  // Edit mode: copy the loaded transaction into the form exactly once.
  LaunchedEffect(editing) {
    val tx = editing ?: return@LaunchedEffect
    if (prefilled) return@LaunchedEffect
    type = tx.type
    amount = tx.amount.toString()
    memo = tx.memo
    category = tx.category
    accountId = tx.accountId
    toAccountId = tx.toAccountId ?: 0L
    timestamp = tx.timestamp
    paymentMethod = tx.paymentMethod
    excludeFromBudget = tx.isExcludedFromBudget
    prefilled = true
  }

  // Create mode from a notification (e.g. a foreign-currency payment): start with its values.
  LaunchedEffect(Unit) {
    val p = viewModel.prefill ?: return@LaunchedEffect
    if (prefilled) return@LaunchedEffect
    type = p.type
    p.amount?.let { amount = it.toString() }
    memo = p.memo
    p.category?.takeIf { it in FinanceCategories.categoriesFor(p.type) }?.let { category = it }
    p.accountId?.let { accountId = it }
    p.timestamp?.let { timestamp = it }
    prefilled = true
  }

  // Create mode: default to the first account once accounts are loaded.
  LaunchedEffect(accounts) {
    val list = accounts ?: return@LaunchedEffect
    if (!isEditMode && list.none { it.id == accountId }) accountId = list.firstOrNull()?.id ?: 0L
  }

  // A transfer recorded without a receiving account (goal deposits, captured card-bill payments)
  // must stay that way when edited; never auto-pick an account for it.
  val editingWithoutReceiver = isEditMode && editing?.let { it.type == TransactionType.TRANSFER && it.toAccountId == null } == true
  val keepWithoutReceiver = editingWithoutReceiver && type == TransactionType.TRANSFER

  // Transfer: the receiving account must exist and differ from the sending account.
  LaunchedEffect(type, accountId, accounts, prefilled) {
    val list = accounts ?: return@LaunchedEffect
    if (type != TransactionType.TRANSFER || (isEditMode && !prefilled) || keepWithoutReceiver) return@LaunchedEffect
    if (toAccountId == accountId || list.none { it.id == toAccountId }) {
      toAccountId = list.firstOrNull { it.id != accountId }?.id ?: 0L
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        FormEvent.Closed -> { saving = false; close() }
        FormEvent.SavedKeepOpen -> {
          saving = false
          amount = ""
          memo = ""
          amountError = false
          appliedSuggestionKey = ""
        }
        is FormEvent.Error -> {
          saving = false
          snackbarHostState.currentSnackbarData?.dismiss()
          launch { snackbarHostState.showSnackbar(event.message) }
        }
      }
    }
  }

  if (showAi) {
    // Apply a fresh AI suggestion once (not again after rotation, so manual picks stay).
    LaunchedEffect(suggestion) {
      val rec = (suggestion as? CategorySuggestionUiState.Success)?.recommendation ?: return@LaunchedEffect
      val key = "${rec.keyword}|${rec.recommendedCategory}|${rec.suggestedPaymentMethod}"
      if (key == appliedSuggestionKey) return@LaunchedEffect
      appliedSuggestionKey = key
      if (rec.recommendedCategory in FinanceCategories.categoriesFor(type)) category = rec.recommendedCategory
      if (type != TransactionType.TRANSFER && rec.suggestedPaymentMethod in PAYMENT_METHODS) {
        paymentMethod = rec.suggestedPaymentMethod
      }
    }
  }

  val isDirty: Boolean = if (isEditMode) {
    val tx = editing
    tx != null && prefilled && (
      tx.type != type || tx.amount.toString() != amount || tx.category != category ||
        tx.accountId != accountId || tx.timestamp != timestamp || tx.memo != memo ||
        tx.paymentMethod != paymentMethod || tx.isExcludedFromBudget != excludeFromBudget ||
        (type == TransactionType.TRANSFER && !keepWithoutReceiver && (tx.toAccountId ?: 0L) != toAccountId)
      )
  } else {
    amount.isNotEmpty() || memo.isNotBlank()
  }

  fun requestClose() {
    if (isDirty) showDiscardConfirm = true else close()
  }
  BackHandler { requestClose() }

  fun save(keepOpen: Boolean) {
    if (saving) return
    focusManager.clearFocus()
    val value = amount.toLongOrNull() ?: 0L
    amountError = value <= 0L
    saving = true
    viewModel.save(
      type = type,
      amount = value,
      category = category,
      accountId = accountId,
      toAccountId = if (type == TransactionType.TRANSFER && !keepWithoutReceiver) toAccountId.takeIf { it > 0 } else null,
      timestamp = timestamp,
      memo = memo,
      paymentMethod = paymentMethod,
      isExcludedFromBudget = type.isSpending && excludeFromBudget,
      keepOpen = keepOpen,
      installmentMonths = if (type == TransactionType.EXPENSE && !isEditMode) installmentMonths else 0
    )
  }

  val accountList = accounts
  val isLoading = accountList == null || (isEditMode && !prefilled && !editingNotFound)
  val showForm = !isLoading && !(isEditMode && editingNotFound) && !accountList.isNullOrEmpty()

  Scaffold(
    modifier = Modifier.testTag("transaction_form_screen"),
    containerColor = Gray100,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      FormTopBar(
        title = if (isEditMode) "내역 수정" else "내역 추가",
        onBack = ::requestClose,
        onDelete = if (isEditMode && editing != null) ({ showDeleteConfirm = true }) else null
      )
    },
    bottomBar = {
      if (showForm) {
        FormActionBar(
          isEditMode = isEditMode,
          accentColor = type.accentColor(),
          enabled = !saving,
          onSave = { save(keepOpen = false) },
          onSaveAndContinue = { save(keepOpen = true) }
        )
      }
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when {
        isLoading -> CircularProgressIndicator(color = TossBlue, modifier = Modifier.align(Alignment.Center))
        isEditMode && editingNotFound -> EmptyState(
          icon = Icons.AutoMirrored.Filled.ReceiptLong,
          title = "내역을 찾을 수 없어요",
          description = "이미 삭제된 내역일 수 있어요.",
          actionLabel = "돌아가기",
          onAction = ::close,
          modifier = Modifier.align(Alignment.Center)
        )
        accountList.isNullOrEmpty() -> EmptyState(
          icon = Icons.Default.AccountBalance,
          title = "먼저 계좌를 등록해 주세요",
          description = "자산 탭에서 계좌를 추가하면 내역을 기록할 수 있어요.",
          actionLabel = "돌아가기",
          onAction = ::close,
          modifier = Modifier.align(Alignment.Center).testTag("form_no_accounts")
        )
        else -> Column(
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          TypeSegmentedControl(selected = type) { newType ->
            if (newType != type) {
              type = newType
              val valid = FinanceCategories.categoriesFor(newType)
              if (category !in valid) category = valid.first()
            }
          }

          if (!isEditMode) {
            ReceiptButtons(
              reading = viewModel.receipts,
              onRead = { info ->
                type = TransactionType.EXPENSE
                amount = info.total.toString()
                amountError = false
                memo = info.memo
                info.date?.let { d ->
                  val zone = java.time.ZoneId.systemDefault()
                  val time = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
                  timestamp = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
                }
                if (info.merchant.isNotEmpty()) viewModel.suggestCategoryFromKeyword(info.memo, TransactionType.EXPENSE, 0L)
              },
              onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
            )
          }

          if (!isEditMode) {
            QuickTemplatesCard(type) { tplAmount, tplCategory, tplMemo, tplMethod ->
              amount = tplAmount.toString()
              amountError = false
              category = tplCategory
              memo = tplMemo
              paymentMethod = tplMethod
            }
          }

          // Amount
          FormCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              SectionLabel("${type.title} 금액", Modifier.weight(1f))
              val parsed = amount.toLongOrNull() ?: 0L
              if (parsed > 0) {
                Text(
                  Formatters.formatKoreanUnitCurrency(parsed),
                  style = MaterialTheme.typography.labelLarge,
                  color = type.accentColor(),
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
            Spacer(Modifier.height(6.dp))
            KrwAmountField(
              value = amount,
              onValueChange = { amount = it; amountError = false },
              isError = amountError,
              supportingText = if (amountError) "금액을 입력해 주세요" else null,
              modifier = Modifier.testTag("form_amount_input")
            )
            Spacer(Modifier.height(6.dp))
            AmountIncrementRow { inc ->
              amount = sanitizeAmountInput(((amount.toLongOrNull() ?: 0L) + inc).toString())
              amountError = false
            }
          }

          // Memo (+ AI category suggestion)
          FormCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              SectionLabel("메모", Modifier.weight(1f))
              if (showAi) {
                AiSuggestButton(enabled = memo.trim().length >= 2) {
                  viewModel.suggestCategoryFromKeyword(memo, type, 0L)
                }
              }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
              value = memo,
              onValueChange = { text ->
                memo = text
                if (showAi) viewModel.suggestCategoryFromKeyword(text, type, AI_TYPING_DEBOUNCE_MS)
              },
              modifier = Modifier.fillMaxWidth().testTag("form_memo_input"),
              placeholder = {
                Text(
                  if (showAi) "예: 스타벅스, 카카오택시 (입력하면 AI가 카테고리를 추천해요)"
                  else "예: 스타벅스, 점심 식사",
                  color = Gray400,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              trailingIcon = {
                if (memo.isNotEmpty()) {
                  IconButton(onClick = {
                    memo = ""
                    viewModel.resetCategorySuggestion()
                  }) { Icon(Icons.Default.Clear, contentDescription = "메모 지우기", tint = Gray400) }
                }
              }
            )
            if (recentTags.isNotEmpty() && type != TransactionType.TRANSFER) {
              Spacer(Modifier.height(6.dp))
              TagChips(tags = recentTags, memo = memo, onToggle = { tag -> memo = com.moneyflow.util.Tags.append(memo, tag) })
            }
            if (showAi) {
              Spacer(Modifier.height(6.dp))
              AiExampleChips { sample ->
                memo = sample
                viewModel.suggestCategoryFromKeyword(sample, type, 0L)
              }
              Spacer(Modifier.height(8.dp))
              AiSuggestionPanel(
                state = suggestion,
                onPickCategory = { picked ->
                  if (picked in FinanceCategories.categoriesFor(type)) category = picked
                },
                onPickPaymentMethod = { if (it in PAYMENT_METHODS) paymentMethod = it },
                onRetry = { viewModel.suggestCategoryFromKeyword(memo, type, 0L) }
              )
            }
          }

          DateTimeCard(
            timestamp = timestamp,
            dayOffsetSelected = { offset ->
              Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() == LocalDate.now(zone).minusDays(offset.toLong())
            },
            onPickDate = { showDatePicker = true },
            onPickTime = { showTimePicker = true },
            onQuickDay = { offset ->
              val current = Instant.ofEpochMilli(timestamp).atZone(zone)
              timestamp = ZonedDateTime.of(LocalDate.now(zone).minusDays(offset.toLong()), current.toLocalTime(), zone)
                .toInstant().toEpochMilli()
            }
          )

          val categories = FinanceCategories.categoriesFor(type).let { base ->
            // Keep a non-standard category of an edited transaction selectable.
            if (category.isNotBlank() && category !in base) base + category else base
          }
          CategoryCard(categories = categories, selected = category, onSelect = { category = it })

          // Account(s), payment method, budget exclusion
          FormCard {
            val isTransfer = type == TransactionType.TRANSFER
            AccountDropdown(
              label = if (isTransfer) "보내는 계좌" else "계좌",
              accounts = accountList.orEmpty(),
              selectedId = accountId,
              onSelect = { accountId = it }
            )
            if (isTransfer && keepWithoutReceiver) {
              Spacer(Modifier.height(4.dp))
              Text(
                if (editing?.goalId != null) "저축 목표로 보낸 이체예요. 받는 계좌는 없어요." else "받는 계좌 없이 기록된 이체예요.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            } else if (isTransfer) {
              Spacer(Modifier.height(4.dp))
              val others = accountList.orEmpty().filter { it.id != accountId }
              if (others.isEmpty()) {
                Text(
                  "이체하려면 계좌가 2개 이상 필요해요.",
                  style = MaterialTheme.typography.bodySmall,
                  color = AccentRed
                )
              } else {
                AccountDropdown(
                  label = "받는 계좌",
                  accounts = others,
                  selectedId = toAccountId,
                  onSelect = { toAccountId = it },
                  testTag = "form_to_account_dropdown"
                )
              }
            } else {
              if (type == TransactionType.EXPENSE && !isEditMode) {
                val picks = remember(cardStatuses, category, memo, amount) {
                  viewModel.recommendCard(cardStatuses, category, memo, amount.toLongOrNull() ?: 0L)
                }
                CardPickHint(
                  picks = picks,
                  accounts = accountList.orEmpty(),
                  selectedId = accountId,
                  onUse = { accountId = it }
                )
              }
              Spacer(Modifier.height(4.dp))
              SectionLabel("결제 수단")
              PaymentMethodChips(selected = paymentMethod, onSelect = { paymentMethod = it })
              val isCard = paymentMethod == "신용카드" ||
                accountList.orEmpty().firstOrNull { it.id == accountId }?.accountType == com.moneyflow.data.model.AccountType.CREDIT_CARD
              if (type == TransactionType.EXPENSE && !isEditMode && isCard) {
                Spacer(Modifier.height(8.dp))
                InstallmentChips(
                  months = installmentMonths,
                  amount = amount.toLongOrNull() ?: 0L,
                  onSelect = { installmentMonths = it }
                )
              }
            }
            if (type.isSpending) {
              Spacer(Modifier.height(8.dp))
              ExcludeFromBudgetRow(checked = excludeFromBudget, onCheckedChange = { excludeFromBudget = it })
            }
          }
          Spacer(Modifier.height(8.dp))
        }
      }
    }
  }

  // ---- Dialogs ----
  if (showDatePicker) {
    val current = Instant.ofEpochMilli(timestamp).atZone(zone)
    val pickerState = rememberDatePickerState(
      initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
              timestamp = ZonedDateTime.of(date, current.toLocalTime(), zone).toInstant().toEpochMilli()
            }
            showDatePicker = false
          },
          modifier = Modifier.testTag("date_picker_confirm_button")
        ) { Text("확인", color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소", color = Gray600) } },
      colors = DatePickerDefaults.colors(containerColor = CardSurface)
    ) {
      DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = CardSurface))
    }
  }

  if (showTimePicker) {
    val current = Instant.ofEpochMilli(timestamp).atZone(zone)
    val timeState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = false)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      containerColor = CardSurface,
      title = { Text("시간 선택", fontWeight = FontWeight.Bold, color = Gray900) },
      text = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) }
      },
      confirmButton = {
        TextButton(
          onClick = {
            timestamp = ZonedDateTime.of(current.toLocalDate(), LocalTime.of(timeState.hour, timeState.minute), zone)
              .toInstant().toEpochMilli()
            showTimePicker = false
          },
          modifier = Modifier.testTag("time_picker_confirm_button")
        ) { Text("확인", color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("취소", color = Gray600) } }
    )
  }

  if (showDeleteConfirm) {
    LaunchedEffect(Unit) { viewModel.loadInstallmentGroup() }
    if (installmentParts > 1) {
      AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("할부 내역을 삭제할까요?", fontWeight = FontWeight.Bold) },
        text = { Text("이 결제는 ${installmentParts}개월 할부로 나눠 기록돼 있어요. 이 달 것만 지우거나 모두 지울 수 있어요.") },
        confirmButton = { TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteInstallmentGroup() }) { Text("${installmentParts}건 모두 삭제", color = AccentRed, fontWeight = FontWeight.Bold) } },
        dismissButton = {
          Row {
            TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) { Text("이것만 삭제") }
          }
        }
      )
    } else {
      ConfirmDialog(
        title = "내역을 삭제할까요?",
        message = "삭제한 뒤 바로 실행 취소할 수 있어요.",
        confirmLabel = "삭제",
        onConfirm = { viewModel.delete() },
        onDismiss = { showDeleteConfirm = false }
      )
    }
  }

  if (showDiscardConfirm) {
    ConfirmDialog(
      title = "작성을 그만둘까요?",
      message = "작성 중인 내용이 사라져요.",
      confirmLabel = "나가기",
      dismissLabel = "계속 작성",
      onConfirm = ::close,
      onDismiss = { showDiscardConfirm = false }
    )
  }
}

@Composable
private fun FormTopBar(title: String, onBack: () -> Unit, onDelete: (() -> Unit)?) {
  Surface(color = CardSurface, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("form_back_button")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기", tint = Gray900)
      }
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Gray900,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      if (onDelete != null) {
        IconButton(onClick = onDelete, modifier = Modifier.testTag("form_delete_button")) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "내역 삭제", tint = AccentRed)
        }
      }
    }
  }
}

@Composable
private fun FormActionBar(
  isEditMode: Boolean,
  accentColor: Color,
  enabled: Boolean,
  onSave: () -> Unit,
  onSaveAndContinue: () -> Unit
) {
  Surface(color = CardSurface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (!isEditMode) {
        OutlinedButton(
          onClick = onSaveAndContinue,
          enabled = enabled,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("form_save_and_continue_button")
        ) {
          Text("저장 후 계속 입력", fontWeight = FontWeight.Bold, color = com.moneyflow.ui.theme.TossBlueText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
      }
      Button(
        onClick = onSave,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = OnBrand),
        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("form_submit_button")
      ) {
        Text(if (isEditMode) "수정 완료" else "저장", fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
  }
}

// ---- AI category suggestion UI (only shown when AI is set up) ----

@Composable
private fun AiSuggestButton(enabled: Boolean, onClick: () -> Unit) {
  val bg = AccentPurpleLight
  TextButton(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    colors = ButtonDefaults.textButtonColors(containerColor = bg, contentColor = AccentPurple),
    modifier = Modifier.testTag("ai_suggest_category_button")
  ) {
    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text("AI 카테고리 추천", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}

@Composable
private fun AiExampleChips(onPick: (String) -> Unit) {
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text("AI 분류 예시:", style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1)
    listOf("카카오택시 야간호출", "스타벅스 리저브", "올리브영 스킨케어", "종로약국 감기약", "CGV 아이맥스 관람", "쿠팡 로켓프레시")
      .forEach { sample ->
        Surface(onClick = { onPick(sample) }, shape = RoundedCornerShape(8.dp), color = Gray100) {
          Text(
            sample,
            style = MaterialTheme.typography.labelSmall,
            color = Gray900,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
          )
        }
      }
  }
}

@Composable
private fun AiSuggestionPanel(
  state: CategorySuggestionUiState,
  onPickCategory: (String) -> Unit,
  onPickPaymentMethod: (String) -> Unit,
  onRetry: () -> Unit
) {
  val purpleLight = AccentPurpleLight
  val redLight = AccentRedLight
  val panelBg = Gray100
  val chipBorder = Gray200
  val chipBg = CardSurface
  val blueContainer = TossBlueContainer
  when (state) {
    is CategorySuggestionUiState.Idle -> Box(
      Modifier.fillMaxWidth().background(panelBg, RoundedCornerShape(12.dp)).padding(10.dp).testTag("ai_suggestion_idle_box")
    ) {
      Text(
        "💡 거래처나 내용을 입력하면 AI가 알맞은 카테고리를 추천해요.",
        style = MaterialTheme.typography.labelSmall,
        color = Gray600
      )
    }
    is CategorySuggestionUiState.Loading -> Row(
      Modifier.fillMaxWidth().background(purpleLight, RoundedCornerShape(12.dp)).padding(12.dp).testTag("ai_suggestion_loading_box"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentPurple)
      Spacer(Modifier.width(10.dp))
      Text(
        "'${state.keyword}'에 맞는 카테고리를 찾고 있어요…",
        style = MaterialTheme.typography.labelMedium,
        color = AccentPurple,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    is CategorySuggestionUiState.Success -> {
      val rec = state.recommendation
      val recColor = Color(FinanceCategories.getColorForCategory(rec.recommendedCategory))
      Column(
        Modifier
          .fillMaxWidth()
          .border(1.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
          .background(purpleLight, RoundedCornerShape(14.dp))
          .padding(12.dp)
          .testTag("ai_suggestion_success_box")
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            "AI 추천 결과",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AccentPurple,
            modifier = Modifier.weight(1f)
          )
          Text("정확도 ${rec.confidence}%", style = MaterialTheme.typography.labelSmall, color = AccentPurple, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(onClick = { onPickCategory(rec.recommendedCategory) }, shape = RoundedCornerShape(10.dp), color = recColor) {
            Text(
              "추천: ${rec.recommendedCategory} ✓",
              style = MaterialTheme.typography.labelMedium,
              color = OnBrand,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
          val alt = rec.alternativeCategory
          if (!alt.isNullOrBlank()) {
            Surface(
              onClick = { onPickCategory(alt) },
              shape = RoundedCornerShape(10.dp),
              color = chipBg,
              border = androidx.compose.foundation.BorderStroke(1.dp, chipBorder)
            ) {
              Text(
                "대안: $alt",
                style = MaterialTheme.typography.labelSmall,
                color = Gray900,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
          Surface(onClick = { onPickPaymentMethod(rec.suggestedPaymentMethod) }, shape = RoundedCornerShape(10.dp), color = blueContainer) {
            Text(
              rec.suggestedPaymentMethod,
              style = MaterialTheme.typography.labelSmall,
              color = com.moneyflow.ui.theme.TossBlueText,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(rec.reason, style = MaterialTheme.typography.bodySmall, color = Gray900)
      }
    }
    is CategorySuggestionUiState.Error -> Row(
      Modifier.fillMaxWidth().background(redLight, RoundedCornerShape(12.dp)).padding(start = 12.dp).testTag("ai_suggestion_error_box"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        state.message,
        style = MaterialTheme.typography.labelSmall,
        color = AccentRed,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(vertical = 8.dp)
      )
      TextButton(onClick = onRetry) { Text("재시도", color = AccentRed, fontWeight = FontWeight.Bold) }
    }
  }
}

/** 영수증 사진으로 채우기: camera or gallery → text read on the phone → amount, shop, date, items. */
@Composable
private fun ReceiptButtons(
  reading: com.moneyflow.domain.receipt.ReceiptReading,
  onRead: (com.moneyflow.domain.receipt.ReceiptInfo) -> Unit,
  onMessage: (String) -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
  fun readUri(uri: android.net.Uri) {
    busy = true
    scope.launch {
      try {
        when (val o = reading.read(uri, java.time.LocalDate.now(), onStep = onMessage)) {
          is com.moneyflow.domain.receipt.ReceiptReading.Outcome.Read -> {
            onRead(o.result.info)
            val by = if (o.result.by == "휴대폰") "" else "${o.result.by}로 "
            onMessage("영수증을 ${by}읽었어요: ${com.moneyflow.util.Formatters.formatCurrency(o.result.info.total)}. 맞는지 확인하고 저장하세요.")
          }
          com.moneyflow.domain.receipt.ReceiptReading.Outcome.CannotOpen -> onMessage("사진을 열지 못했어요. 다른 사진으로 해 주세요.")
          is com.moneyflow.domain.receipt.ReceiptReading.Outcome.NotFound -> onMessage(
            (if (!o.hadText) "사진에서 글자를 찾지 못했어요. 영수증이 화면에 꽉 차게, 밝은 곳에서 찍어 주세요."
            else "영수증에서 합계 금액을 찾지 못했어요. 합계 부분이 보이게 다시 찍어 주세요.") +
              (o.error?.let { " ($it)" } ?: "")
          )
        }
      } catch (e: Exception) {
        com.moneyflow.util.ProblemLog.record("영수증 읽기", e)
        onMessage("영수증을 읽지 못했어요. 다시 시도해 주세요.")
      } finally {
        ReceiptScanner.clearPhotos(context)
        busy = false
      }
    }
  }
  val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
    val uri = photoUri?.let(android.net.Uri::parse)
    photoUri = null
    if (ok && uri != null) readUri(uri) else ReceiptScanner.clearPhotos(context)
  }
  val gallery = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) readUri(uri)
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    androidx.compose.material3.OutlinedButton(
      onClick = {
        val uri = ReceiptScanner.newPhotoUri(context)
        photoUri = uri.toString()
        try { camera.launch(uri) } catch (e: android.content.ActivityNotFoundException) { onMessage("카메라 앱을 찾지 못했어요. 앨범에서 골라 주세요.") }
      },
      enabled = !busy,
      modifier = Modifier.weight(1f).testTag("receipt_camera")
    ) { Text("📷 영수증 찍기") }
    androidx.compose.material3.OutlinedButton(
      onClick = { gallery.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
      enabled = !busy,
      modifier = Modifier.weight(1f).testTag("receipt_gallery")
    ) { Text("앨범에서 고르기") }
  }
  if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)
}
