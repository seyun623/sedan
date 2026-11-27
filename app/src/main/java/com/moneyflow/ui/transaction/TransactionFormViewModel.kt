package com.moneyflow.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.ai.CategorySuggester
import com.moneyflow.ai.CategorySuggestionUiState
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.LedgerResult
import com.moneyflow.data.repository.SettingsRepository
import kotlinx.coroutines.flow.map
import com.moneyflow.ui.common.UserMessage
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Navigation argument: id of the transaction to edit; absent or 0 = new transaction. */
const val ARG_TRANSACTION_ID = "transactionId"

/** One-shot events for the form screen. */
sealed interface FormEvent {
  /** Saved; close the form. */
  data object Closed : FormEvent
  /** Saved with "save and add another"; clear the inputs. */
  data object SavedKeepOpen : FormEvent
  data class Error(val message: String) : FormEvent
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
  savedState: SavedStateHandle,
  private val ledger: LedgerRepository,
  private val suggester: CategorySuggester,
  settings: SettingsRepository,
  private val cards: com.moneyflow.data.repository.CardRepository,
  private val messages: UserMessageBus,
  val receipts: com.moneyflow.domain.receipt.ReceiptReading,
  prefillStore: com.moneyflow.ui.common.PrefillStore
) : ViewModel() {

  private val editingId: Long = savedState.get<Long>(ARG_TRANSACTION_ID) ?: 0L
  val isEditMode: Boolean get() = editingId > 0

  /** Values to start a new transaction with (from a notification); applied once by the screen. */
  val prefill: com.moneyflow.domain.capture.FormPrefill? = if (editingId > 0) null else prefillStore.take()

  /** All accounts; null until the first load completes (so the UI can tell "loading" from "none"). */
  val accounts: StateFlow<List<AccountEntity>?> =
    ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** The transaction being edited (null while loading or in create mode). */
  private val _editing = MutableStateFlow<TransactionEntity?>(null)
  val editing: StateFlow<TransactionEntity?> = _editing.asStateFlow()

  /** Edit mode only: true when the transaction to edit no longer exists. */
  private val _editingNotFound = MutableStateFlow(false)
  val editingNotFound: StateFlow<Boolean> = _editingNotFound.asStateFlow()

  private val _events = Channel<FormEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  /** AI suggestion is available (turned on with an API key in Settings → AI 설정). */
  val aiReady: StateFlow<Boolean> = settings.settings.map { it.isAiReady }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /** Recently used #tags (quick chips under the memo). */
  val recentTags: StateFlow<List<String>> = kotlinx.coroutines.flow.combine(ledger.transactions, settings.settings) { txs, s ->
    val recent = com.moneyflow.util.Tags.recent(txs)
    // With family sync on, #공동 (가족 정산) is always offered.
    val shared = com.moneyflow.domain.family.FamilySettlement.DEFAULT_TAG
    if (s.familySyncEnabled && shared !in recent) recent.take(7) + shared else recent
  }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Card statuses used for "이 결제는 ○○카드가 유리해요". */
  val cardStatuses: StateFlow<List<com.moneyflow.domain.card.CardStatus>> =
    cards.statuses.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun recommendCard(
    statuses: List<com.moneyflow.domain.card.CardStatus>, category: String, memo: String, amount: Long
  ): List<com.moneyflow.domain.card.CardPick> = cards.recommend(statuses, category, memo, amount)

  private val _suggestion = MutableStateFlow<CategorySuggestionUiState>(CategorySuggestionUiState.Idle)
  val categorySuggestionState: StateFlow<CategorySuggestionUiState> = _suggestion.asStateFlow()
  private var suggestionJob: Job? = null

  init {
    if (editingId > 0) viewModelScope.launch {
      val tx = ledger.getTransaction(editingId)
      _editing.value = tx
      _editingNotFound.value = tx == null
    }
  }

  /**
   * Saves the form. For a transfer, [toAccountId] is required and must differ from [accountId].
   * [keepOpen] = "저장 후 계속 입력" (create mode only).
   */
  fun save(
    type: TransactionType,
    amount: Long,
    category: String,
    accountId: Long,
    toAccountId: Long?,
    timestamp: Long,
    memo: String,
    paymentMethod: String,
    isExcludedFromBudget: Boolean,
    keepOpen: Boolean = false,
    installmentMonths: Int = 0
  ) = viewModelScope.launch {
    val error = when {
      amount <= 0 -> "금액을 입력해 주세요."
      accountId <= 0 -> "계좌를 선택해 주세요."
      type == TransactionType.TRANSFER && toAccountId == null && !allowsNoReceiver(type) -> "받는 계좌를 선택해 주세요."
      type == TransactionType.TRANSFER && toAccountId == accountId -> "보내는 계좌와 받는 계좌가 같아요."
      else -> null
    }
    if (error != null) { _events.send(FormEvent.Error(error)); return@launch }

    val base = TransactionEntity(
      id = if (isEditMode) editingId else 0,
      type = type,
      amount = amount,
      category = category,
      accountId = accountId,
      toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
      timestamp = timestamp,
      memo = memo.trim().ifBlank { category },
      paymentMethod = paymentMethod,
      isExcludedFromBudget = isExcludedFromBudget
    )
    if (isEditMode) {
      val original = _editing.value
      val updated = base.copy(
        source = original?.source ?: base.source,
        dedupKey = original?.dedupKey,
        recurringId = original?.recurringId,
        goalId = original?.goalId
      )
      when (val r = ledger.updateTransaction(updated)) {
        LedgerResult.Ok -> { messages.show("내역을 수정했어요."); _events.send(FormEvent.Closed) }
        is LedgerResult.Rejected -> _events.send(FormEvent.Error(r.reason))
      }
    } else {
      val split = type == TransactionType.EXPENSE && installmentMonths > 1
      runCatching { if (split) ledger.addInstallments(base, installmentMonths) else ledger.addTransaction(base) }
        .onSuccess {
          messages.show(if (split) "할부 ${installmentMonths}개월로 나눠 저장했어요." else "${type.title} 내역을 저장했어요.")
          _events.send(if (keepOpen) FormEvent.SavedKeepOpen else FormEvent.Closed)
        }
        .onFailure { _events.send(FormEvent.Error(it.message ?: "저장하지 못했어요.")) }
    }
    resetCategorySuggestion()
  }

  /** Editing a transfer that was recorded without a receiving account (e.g. a goal deposit). */
  private fun allowsNoReceiver(type: TransactionType): Boolean {
    val original = _editing.value ?: return false
    return isEditMode && type == TransactionType.TRANSFER &&
      original.type == TransactionType.TRANSFER && original.toAccountId == null
  }

  /** Number of parts when the edited transaction is part of a split installment purchase (else 0). */
  private val _installmentParts = MutableStateFlow(0)
  val installmentParts: StateFlow<Int> = _installmentParts.asStateFlow()

  fun loadInstallmentGroup() = viewModelScope.launch {
    val tx = _editing.value ?: return@launch
    _installmentParts.value = ledger.installmentGroup(tx).size.takeIf { it > 1 } ?: 0
  }

  fun deleteInstallmentGroup() = viewModelScope.launch {
    val tx = _editing.value ?: return@launch
    val n = ledger.deleteInstallmentGroup(tx)
    messages.show("할부 ${n}건을 모두 삭제했어요.")
    _events.send(FormEvent.Closed)
  }

  fun delete() = viewModelScope.launch {
    val tx = _editing.value ?: return@launch
    val removed = ledger.deleteTransaction(tx)
    if (removed != null) {
      messages.show(
        UserMessage("'${removed.memo}' 내역을 삭제했어요.", "실행 취소") { ledger.restoreTransaction(removed) }
      )
    }
    _events.send(FormEvent.Closed)
  }

  // ---- AI category suggestion (unchanged behaviour) ----

  fun suggestCategoryFromKeyword(keyword: String, transactionType: TransactionType = TransactionType.EXPENSE, debounceMs: Long = 0L) {
    val trimmed = keyword.trim()
    if (trimmed.length < 2) {
      _suggestion.value = CategorySuggestionUiState.Idle
      return
    }
    suggestionJob?.cancel()
    suggestionJob = viewModelScope.launch {
      if (debounceMs > 0) delay(debounceMs)
      _suggestion.value = CategorySuggestionUiState.Loading(trimmed)
      try {
        val rec = suggester.suggest(trimmed, transactionType)
        _suggestion.value = if (rec == null) {
          CategorySuggestionUiState.Error("AI 추천 응답이 비어있습니다.")
        } else {
          CategorySuggestionUiState.Success(rec)
        }
      } catch (e: TimeoutCancellationException) {
        _suggestion.value = CategorySuggestionUiState.Error("AI 추천 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _suggestion.value = CategorySuggestionUiState.Error(e.localizedMessage ?: "AI 카테고리 추천 중 오류가 발생했습니다.")
      }
    }
  }

  fun resetCategorySuggestion() {
    suggestionJob?.cancel()
    _suggestion.value = CategorySuggestionUiState.Idle
  }
}
