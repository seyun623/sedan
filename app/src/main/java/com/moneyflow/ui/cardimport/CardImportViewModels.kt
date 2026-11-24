package com.moneyflow.ui.cardimport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.ai.AiService
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.repository.BenefitImportResult
import com.moneyflow.data.repository.CardImportRepository
import com.moneyflow.data.repository.CardRepository
import com.moneyflow.data.repository.ImportRow
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.cardimport.AiStatementReader
import com.moneyflow.domain.cardimport.CardImportSession
import com.moneyflow.domain.cardimport.CardSite
import com.moneyflow.domain.cardimport.CardSites
import com.moneyflow.domain.cardimport.StatementExtraction
import com.moneyflow.domain.cardimport.StatementTextParser
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

const val ARG_SITE_ID = "siteId"

sealed interface BrowserEvent {
  data object OpenPreview : BrowserEvent
  data class Error(val message: String) : BrowserEvent
  data class Info(val message: String) : BrowserEvent
}

@HiltViewModel
class CardBrowserViewModel @Inject constructor(
  savedState: SavedStateHandle,
  private val ai: AiService,
  private val aiReader: AiStatementReader,
  private val session: CardImportSession,
  private val clock: AppClock,
  private val settings: com.moneyflow.data.repository.SettingsRepository
) : ViewModel() {
  val site: CardSite = CardSites.byId(checkNotNull(savedState.get<String>(ARG_SITE_ID))) ?: CardSites.all.first()

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  private val _aiLabel = MutableStateFlow<String?>(null)
  /** Provider name when AI is set up (enables "AI로 읽기"), else null. */
  val aiLabel: StateFlow<String?> = _aiLabel.asStateFlow()

  private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  init {
    viewModelScope.launch { _aiLabel.value = if (ai.isReady()) ai.providerLabel() else null }
  }

  /** Pages of this card company read automatically after login ("이 화면 기억하기"). */
  val savedPages: StateFlow<List<com.moneyflow.domain.cardimport.SavedPage>> = settings.settings
    .map { it.cardSavedPages[site.id].orEmpty() }
    .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

  /** Remembers the page now shown; returns why it can't, or null when saved. */
  fun savePage(url: String?, title: String) = viewModelScope.launch {
    val current = settings.current().cardSavedPages[site.id].orEmpty()
    val problem = com.moneyflow.domain.cardimport.SavedPages.problem(url, site.url, current)
    if (problem != null) { _events.send(BrowserEvent.Error(problem)); return@launch }
    val page = com.moneyflow.domain.cardimport.SavedPage(
      com.moneyflow.domain.cardimport.SavedPages.sanitize(url!!), title.ifBlank { "화면 ${current.size + 1}" }.take(40), clock.nowMillis()
    )
    settings.setCardSavedPages(site.id, current + page)
    _events.send(BrowserEvent.Info("'${page.title}' 화면을 기억했어요. 다음부터 로그인한 뒤 '기억한 화면 읽기'를 누르면 자동으로 읽어요."))
  }

  fun removePage(page: com.moneyflow.domain.cardimport.SavedPage) = viewModelScope.launch {
    settings.setCardSavedPages(site.id, settings.current().cardSavedPages[site.id].orEmpty().filter { it.url != page.url })
  }

  /** Reads the texts of several remembered pages and opens one combined preview. */
  fun readMany(pages: List<Pair<com.moneyflow.domain.cardimport.SavedPage, String>>, useAi: Boolean) = viewModelScope.launch {
    if (_busy.value || pages.isEmpty()) return@launch
    _busy.value = true
    try {
      val readings = pages.map { (_, text) ->
        if (useAi) aiReader.read(text, clock.today())
        else withContext(Dispatchers.Default) { StatementTextParser(clock.today()).parse(text) }
      }
      val readBy = if (useAi) readings.first().readBy else StatementTextParser.READ_LOCAL
      val merged = com.moneyflow.domain.cardimport.SavedPages.merge(readings, readBy)
      val empty = pages.indices.filter { readings[it].isEmpty }.map { pages[it].first.title }
      if (merged.isEmpty) {
        _events.send(BrowserEvent.Error("기억한 화면에서 내역을 찾지 못했어요. 화면이 바뀌었거나 다시 열 수 없는 화면일 수 있어요. 화면을 직접 열고 '이 화면 가져오기'를 눌러 보세요."))
      } else {
        session.put(site, merged)
        if (empty.isNotEmpty()) _events.send(BrowserEvent.Info("${empty.joinToString(", ")} 화면에서는 내역을 찾지 못했어요."))
        _events.send(BrowserEvent.OpenPreview)
      }
    } catch (e: Exception) {
      _events.send(BrowserEvent.Error(com.moneyflow.util.UserErrors.message(e, "화면을 읽지 못했어요.")))
    } finally {
      _busy.value = false
    }
  }

  fun read(screenText: String, useAi: Boolean) = viewModelScope.launch {
    if (_busy.value) return@launch
    _busy.value = true
    try {
      val result: StatementExtraction = if (useAi) {
        aiReader.read(screenText, clock.today())
      } else {
        withContext(Dispatchers.Default) { StatementTextParser(clock.today()).parse(screenText) }
      }
      if (result.isEmpty) {
        _events.send(BrowserEvent.Error(
          if (useAi) "AI가 이 화면에서 결제 내역·실적·혜택을 찾지 못했어요. 이용내역·명세서·혜택 화면에서 다시 시도해 주세요."
          else "이 화면에서 결제 내역을 찾지 못했어요. 이용내역·명세서 화면을 연 뒤 다시 시도하거나, 'AI로 읽기'를 써 보세요."
        ))
      } else {
        session.put(site, result)
        _events.send(BrowserEvent.OpenPreview)
      }
    } catch (e: Exception) {
      _events.send(BrowserEvent.Error(com.moneyflow.util.UserErrors.message(e, "화면을 읽지 못했어요.")))
    } finally {
      _busy.value = false
    }
  }
}

data class PreviewState(
  val site: CardSite? = null,
  val extraction: StatementExtraction? = null,
  val accounts: List<AccountEntity> = emptyList(),
  val account: AccountEntity? = null,
  val rows: List<ImportRow> = emptyList(),
  val selected: Set<Int> = emptySet(),
  val savePerformance: Boolean = true,
  /** Record installment purchases as monthly parts. */
  val splitInstallments: Boolean = false,
  /** Indexes of extraction.benefits to add. */
  val selectedBenefits: Set<Int> = emptySet(),
  val busy: Boolean = false
) {
  val canImport: Boolean get() = !busy && account != null &&
    (selected.isNotEmpty() || selectedBenefits.isNotEmpty() || (savePerformance && extraction?.performance?.isNotEmpty() == true))
}

@HiltViewModel
class CardImportPreviewViewModel @Inject constructor(
  private val session: CardImportSession,
  private val repo: CardImportRepository,
  private val ledger: LedgerRepository,
  private val cards: CardRepository,
  private val settings: com.moneyflow.data.repository.SettingsRepository,
  private val messages: UserMessageBus
) : ViewModel() {
  private val _state = MutableStateFlow(PreviewState())
  val state: StateFlow<PreviewState> = _state.asStateFlow()

  private val _done = Channel<Unit>(Channel.BUFFERED)
  val done = _done.receiveAsFlow()

  init {
    viewModelScope.launch {
      val (site, extraction) = session.current.value ?: return@launch
      val accounts = ledger.accounts.first()
      val account = repo.suggestAccount(site.provider, accounts)
      _state.value = PreviewState(
        site, extraction, accounts, account,
        splitInstallments = settings.current().splitInstallments,
        selectedBenefits = extraction.benefits.indices.toSet()
      )
      account?.let { selectAccount(it) }
    }
  }

  fun selectAccount(account: AccountEntity) = viewModelScope.launch {
    val extraction = _state.value.extraction ?: return@launch
    val rows = repo.review(extraction.items, account.id)
    _state.value = _state.value.copy(
      account = account,
      rows = rows,
      selected = rows.indices.filter { rows[it].selectedByDefault }.toSet()
    )
  }

  fun toggle(index: Int) {
    val s = _state.value
    _state.value = s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
  }

  fun setAll(selected: Boolean) {
    val s = _state.value
    _state.value = s.copy(selected = if (selected) s.rows.indices.toSet() else emptySet())
  }

  fun setSavePerformance(on: Boolean) { _state.value = _state.value.copy(savePerformance = on) }
  fun setSplitInstallments(on: Boolean) { _state.value = _state.value.copy(splitInstallments = on) }

  fun toggleBenefit(index: Int) {
    val s = _state.value
    _state.value = s.copy(selectedBenefits = if (index in s.selectedBenefits) s.selectedBenefits - index else s.selectedBenefits + index)
  }

  fun import() = viewModelScope.launch {
    val s = _state.value
    val account = s.account ?: return@launch
    _state.value = s.copy(busy = true)
    val result = repo.import(s.rows.filterIndexed { i, _ -> i in s.selected }, account, s.splitInstallments)
    if (s.savePerformance) s.extraction?.performance?.firstOrNull()?.let { repo.savePerformance(account.id, it) }
    val benefits = s.extraction?.benefits.orEmpty().filterIndexed { i, _ -> i in s.selectedBenefits }
    val benefitResult = if (benefits.isEmpty()) null else cards.addBenefits(account.id, benefits, setTargetIfMissing = true)
    session.clear()
    messages.show(buildString {
      if (s.selected.isNotEmpty()) append("${account.name}에 ${result.added}건을 추가했어요.")
      if (result.cancelled > 0) append(" 취소 ${result.cancelled}건을 반영했어요.")
      if (result.skipped > 0) append(" ${result.skipped}건은 건너뛰었어요.")
      benefitResult?.let { append(" ${benefitMessage(it)}") }
      if (isEmpty()) append("실적을 저장했어요.")
    }.trim())
    _state.value = _state.value.copy(busy = false)
    _done.send(Unit)
  }
}

/** "혜택 3개를 등록했어요 (1개는 이미 있어요). 실적 기준을 30만 원으로 설정했어요." */
fun benefitMessage(r: BenefitImportResult): String = buildString {
  append("혜택 ${r.added}개를 등록했어요.")
  if (r.skipped > 0) append(" (${r.skipped}개는 이미 있어요)")
  r.targetSet?.let { append(" 실적 기준을 ${com.moneyflow.util.Formatters.formatCurrency(it)}(으)로 설정했어요.") }
}
