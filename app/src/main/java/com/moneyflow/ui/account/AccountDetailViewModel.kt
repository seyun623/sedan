package com.moneyflow.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.LedgerResult
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ARG_ACCOUNT_ID = "accountId"

data class AccountDetailUiState(
  val account: AccountEntity? = null,
  val transactions: List<TransactionEntity> = emptyList(),
  val otherAccounts: List<AccountEntity> = emptyList(),
  val isLoading: Boolean = true
)

sealed interface AccountEvent {
  data object Deleted : AccountEvent
  /** Deletion needs confirmation because the account is in use. */
  data class ConfirmDeleteRelated(val message: String) : AccountEvent
  data class Error(val message: String) : AccountEvent
}

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
  savedState: SavedStateHandle,
  private val ledger: LedgerRepository,
  private val messages: UserMessageBus
) : ViewModel() {
  private val accountId: Long = checkNotNull(savedState.get<Long>(ARG_ACCOUNT_ID))

  val uiState: StateFlow<AccountDetailUiState> = combine(
    ledger.observeAccount(accountId),
    ledger.observeAccountTransactions(accountId),
    ledger.accounts
  ) { account, txs, all ->
    AccountDetailUiState(account, txs, all.filter { it.id != accountId }, isLoading = false)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

  private val _events = Channel<AccountEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  fun update(updated: AccountEntity) = viewModelScope.launch {
    when (val r = ledger.updateAccount(updated.copy(id = accountId))) {
      LedgerResult.Ok -> messages.show("계좌 정보를 수정했어요.")
      is LedgerResult.Rejected -> _events.send(AccountEvent.Error(r.reason))
    }
  }

  fun toggleFavorite() = viewModelScope.launch {
    val a = uiState.value.account ?: return@launch
    ledger.updateAccount(a.copy(isFavorite = !a.isFavorite))
  }

  /** First call without [deleteRelated]; if the account is in use the UI is asked to confirm. */
  fun delete(deleteRelated: Boolean = false) = viewModelScope.launch {
    val name = uiState.value.account?.name ?: return@launch
    when (val r = ledger.deleteAccount(accountId, deleteRelated)) {
      LedgerResult.Ok -> {
        messages.show("'$name' 계좌를 삭제했어요.")
        _events.send(AccountEvent.Deleted)
      }
      is LedgerResult.Rejected -> _events.send(
        AccountEvent.ConfirmDeleteRelated("${r.reason}\n함께 삭제하면 관련 거래가 지워지고 다른 계좌 잔액도 되돌려집니다.")
      )
    }
  }

  fun transfer(to: AccountEntity, amount: Long, memo: String) = viewModelScope.launch {
    val from = uiState.value.account ?: return@launch
    runCatching { ledger.transfer(from, to, amount, memo) }
      .onSuccess { messages.show("${to.name}(으)로 이체를 기록했어요.") }
      .onFailure { _events.send(AccountEvent.Error(it.message ?: "이체하지 못했어요.")) }
  }
}
