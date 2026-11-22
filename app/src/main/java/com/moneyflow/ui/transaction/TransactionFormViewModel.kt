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
  private val messages: UserMessageBus
) : ViewModel() {

  private val editingId: Long = savedState.get<Long>(ARG_TRANSACTION_ID) ?: 0L
  val isEditMode: Boolean get() = editingId > 0

  val accounts: StateFlow<List<AccountEntity>> =
    ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** The transaction being edited (null while loading or in create mode). */
  private val _editing = MutableStateFlow<TransactionEntity?>(null)
  val editing: StateFlow<TransactionEntity?> = _editing.asStateFlow()

  private val _events = Channel<FormEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  private val _suggestion = MutableStateFlow<CategorySuggestionUiState>(CategorySuggestionUiState.Idle)
  val categorySuggestionState: StateFlow<CategorySuggestionUiState> = _suggestion.asStateFlow()
  private var suggestionJob: Job? = null

  init {
    if (editingId > 0) viewModelScope.launch { _editing.value = ledger.getTransaction(editingId) }
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
    keepOpen: Boolean = false
  ) = viewModelScope.launch {
    val error = when {
      amount <= 0 -> "금액을 입력해 주세요."
      accountId <= 0 -> "계좌를 선택해 주세요."
      type == TransactionType.TRANSFER && toAccountId == null -> "받는 계좌를 선택해 주세요."
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
      runCatching { ledger.addTransaction(base) }
        .onSuccess {
          messages.show("${type.title} 내역을 저장했어요.")
          _events.send(if (keepOpen) FormEvent.SavedKeepOpen else FormEvent.Closed)
        }
        .onFailure { _events.send(FormEvent.Error(it.message ?: "저장하지 못했어요.")) }
    }
    resetCategorySuggestion()
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
