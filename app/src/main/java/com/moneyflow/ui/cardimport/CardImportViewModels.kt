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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

const val ARG_SITE_ID = "siteId"

/** Largest card-site file read directly (이용내역 엑셀). */
private const val MAX_DOWNLOAD = 15 * 1024 * 1024
private const val MAX_REDIRECTS = 5

sealed interface BrowserEvent {
  data object OpenPreview : BrowserEvent
  /** Orders were read from a shopping site: open the screen that links them to payments. */
  data object OpenOrders : BrowserEvent
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
  private val settings: com.moneyflow.data.repository.SettingsRepository,
  @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
  private val orderSession: com.moneyflow.domain.cardimport.OrderSession,
  private val aiOrders: com.moneyflow.domain.shop.AiOrderReader
) : ViewModel() {
  val site: CardSite = CardSites.byId(checkNotNull(savedState.get<String>(ARG_SITE_ID))) ?: CardSites.all.first()

  /** The card site page and its login: kept here so the app lock and the preview don't end it. */
  val web = CardWebSession(context, site.desktopDefault)

  private val _desktop = MutableStateFlow<Boolean?>(null)
  /** PC page (true) or phone page (false); null until the saved choice is known. */
  val desktop: StateFlow<Boolean?> = _desktop.asStateFlow()

  /** Where the browser starts (the PC or the phone address of the site). */
  fun startUrl(): String = if (_desktop.value == true) site.pcUrl else site.url

  /** What the page now shown looks like (login form, payments found), checked after each load. */
  data class PageCheck(val login: Boolean, val items: Int, val performance: Int)
  private val _pageCheck = MutableStateFlow<PageCheck?>(null)
  val pageCheck: StateFlow<PageCheck?> = _pageCheck.asStateFlow()
  private var probeJob: kotlinx.coroutines.Job? = null

  private val _appCode = MutableStateFlow<String?>(null)
  /** The 7-digit login code the site shows for its app (copied and typed into the app). */
  val appCode: StateFlow<String?> = _appCode.asStateFlow()

  private val _found = MutableStateFlow<List<com.moneyflow.domain.cardimport.CardBrowsing.FoundLink>?>(null)
  /** Menu links to usage / statement pages found on the page ("내역 화면 찾기"). */
  val found: StateFlow<List<com.moneyflow.domain.cardimport.CardBrowsing.FoundLink>?> = _found.asStateFlow()

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  private val _aiLabel = MutableStateFlow<String?>(null)
  /** Provider name when AI is set up (enables "AI로 읽기"), else null. */
  val aiLabel: StateFlow<String?> = _aiLabel.asStateFlow()

  private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  init {
    viewModelScope.launch { _aiLabel.value = if (ai.isReady()) ai.providerLabel() else null }
    viewModelScope.launch {
      val d = !site.pcBlocked && (settings.current().cardDesktopModes[site.id] ?: site.desktopDefault)
      web.prepare(d)
      _desktop.value = d
    }
    viewModelScope.launch { web.pageLoads.collect { if (it > 0) probePage() } }
    // The code appears when the user picks "앱으로 로그인" on the page (no new page load): look every few seconds.
    // Only while the screen shows it (not in the background, behind the lock screen or the preview).
    if (site.appNames.isNotEmpty()) viewModelScope.launch {
      _appCode.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { watched ->
        while (watched) {
          kotlinx.coroutines.delay(2_500)
          if ((_pageCheck.value?.items ?: 0) > 0) { _appCode.value = null; continue }
          val text = web.evaluate(com.moneyflow.domain.cardimport.CardBrowsing.READ_TEXT_JS)
          _appCode.value = withContext(Dispatchers.Default) { com.moneyflow.domain.cardimport.CardBrowsing.findAppCode(text) }
        }
      }
    }
    viewModelScope.launch {
      web.download.collect { d -> if (d != null) { web.downloadHandled(); importDownload(d) } }
    }
  }

  override fun onCleared() {
    web.close()
  }

  /** Switches the site between its PC and phone pages (remembered for this card company). */
  fun setDesktop(on: Boolean) = viewModelScope.launch {
    if (on && site.pcBlocked) {
      _events.send(BrowserEvent.Info("${site.name} PC 화면은 보안프로그램(키보드 보안 등)이 없으면 로그인할 수 없어 휴대폰 화면만 써요."))
      return@launch
    }
    settings.setCardDesktopMode(site.id, on)
    _desktop.value = on
    web.setDesktop(on, if (on) site.pcUrl else site.url)
  }

  /** Looks at the new page: a login form folds the buttons away, found payments bring them back. */
  private fun probePage() {
    // The payment check reads card statements; a shop's order list would only confuse it.
    if (site.shop) return
    probeJob?.cancel()
    probeJob = viewModelScope.launch {
      _pageCheck.value = null
      // Card sites fill their lists with scripts after the page has loaded.
      for (wait in listOf(1_500L, 3_500L)) {
        kotlinx.coroutines.delay(wait)
        val o = runCatching { org.json.JSONObject(web.evaluate(com.moneyflow.domain.cardimport.CardBrowsing.READ_PAGE_JS)) }.getOrNull() ?: continue
        val login = o.optBoolean("login")
        val r = withContext(Dispatchers.Default) { StatementTextParser(clock.today()).parse(o.optString("t")) }
        _pageCheck.value = PageCheck(login, r.items.size, r.performance.size)
        if (login || r.items.isNotEmpty()) break
      }
    }
  }

  /** A shopping site's order list → orders → the screen that puts product names on payments. */
  private suspend fun readOrders(screenText: String, useAi: Boolean) {
    _busy.value = true
    try {
      val local = { com.moneyflow.domain.shop.OrderTextParser(clock.today(), site.provider).parse(screenText) }
      val orders = if (useAi) {
        try {
          aiOrders.read(screenText, clock.today(), site.provider)
        } catch (e: com.moneyflow.ai.AiRequestException) {
          _events.send(BrowserEvent.Info("AI가 응답하지 않아 기기에서 읽었어요. (${e.message})"))
          withContext(Dispatchers.Default) { local() }
        }
      } else withContext(Dispatchers.Default) { local() }
      if (orders.isEmpty()) {
        _events.send(BrowserEvent.Error(
          "이 화면에서 주문을 찾지 못했어요. 주문내역(주문목록) 화면을 연 뒤 다시 눌러 주세요." +
            if (!useAi) " 'AI로 읽기'가 더 정확해요." else ""
        ))
      } else {
        orderSession.put(site, orders)
        _events.send(BrowserEvent.OpenOrders)
      }
    } catch (e: Exception) {
      _events.send(BrowserEvent.Error(com.moneyflow.util.UserErrors.message(e, "주문을 읽지 못했어요.")))
    } finally {
      _busy.value = false
    }
  }

  fun findLinks() = viewModelScope.launch {
    val known = if (_desktop.value == true) emptyList() else site.pages
    val links = com.moneyflow.domain.cardimport.CardBrowsing.withKnownPages(
      known, com.moneyflow.domain.cardimport.CardBrowsing.parseFoundLinks(web.evaluate(com.moneyflow.domain.cardimport.CardBrowsing.FIND_LINKS_JS))
    )
    if (links.isEmpty()) {
      _events.send(BrowserEvent.Error(
        "이 화면에서 이용내역·명세서 메뉴를 찾지 못했어요. 로그인한 뒤 다시 눌러 보세요." +
          (site.guide?.let { "\n\n${site.name}: $it" } ?: "")
      ))
    } else {
      _found.value = links
    }
  }

  fun openFound(link: com.moneyflow.domain.cardimport.CardBrowsing.FoundLink) = viewModelScope.launch {
    _found.value = null
    if (link.id.isEmpty()) { web.loadInFront(link.url); return@launch }
    val clicked = web.evaluate(com.moneyflow.domain.cardimport.CardBrowsing.clickFoundJs(link.id)) == "ok"
    if (!clicked && link.url.startsWith("https://")) web.loadInFront(link.url)
  }

  fun dismissFound() { _found.value = null }

  /** "화면 가림막 걷기": for pages whose leftover dimmed layer blocks every touch. */
  fun removeOverlays() = viewModelScope.launch {
    val n = web.evaluate(com.moneyflow.domain.cardimport.CardBrowsing.REMOVE_OVERLAYS_JS).toIntOrNull() ?: 0
    _events.send(BrowserEvent.Info(
      if (n > 0) "화면을 가리던 막 ${n}개를 걷었어요. 이제 눌러 보세요."
      else "걷을 가림막을 찾지 못했어요. ⋮ → '${if (_desktop.value == true) "휴대폰" else "PC"} 화면으로 보기'를 써 보세요."
    ))
  }

  /** The site offered a file (이용내역 엑셀 저장): read it right away with the same login. */
  private suspend fun importDownload(d: SiteDownload) {
    if (site.shop) {
      _events.send(BrowserEvent.Info("쇼핑몰 파일은 읽지 않아요. 주문내역 화면에서 '이 화면 가져오기'를 눌러 주세요."))
      return
    }
    if (!d.url.startsWith("https://")) {
      _events.send(BrowserEvent.Error("이 파일은 바로 읽을 수 없어요. 휴대폰에 저장한 뒤 '엑셀·CSV 파일에서 가져오기'로 열어 주세요."))
      return
    }
    if (_busy.value) return
    _busy.value = true
    try {
      val name = com.moneyflow.domain.cardimport.CardBrowsing.downloadFileName(d.contentDisposition, d.url, d.mimeType)
      val bytes = withContext(Dispatchers.IO) { fetchFile(d) }
      val rows = withContext(Dispatchers.Default) { com.moneyflow.domain.cardimport.TableFileReader.read(bytes, name) }
      val result = withContext(Dispatchers.Default) { com.moneyflow.domain.cardimport.TableStatementReader(clock.today()).read(rows, "카드사 파일") }
      if (result.items.isEmpty()) {
        _events.send(BrowserEvent.Error("받은 파일에서 결제 내역을 찾지 못했어요. 이용내역 화면에서 '이 화면 가져오기'를 눌러 주세요."))
      } else {
        session.put(site, result)
        _events.send(BrowserEvent.OpenPreview)
      }
    } catch (e: Exception) {
      _events.send(BrowserEvent.Error(com.moneyflow.util.UserErrors.message(e, "파일을 받지 못했어요. 이용내역 화면에서 '이 화면 가져오기'를 눌러 주세요.")))
    } finally {
      _busy.value = false
    }
  }

  /**
   * Downloads with the page's login. Redirects are followed by hand so each host only gets its own
   * cookies (a file server must not receive the card company's login), and only over https.
   */
  private fun fetchFile(d: SiteDownload): ByteArray {
    var url = d.url
    repeat(MAX_REDIRECTS + 1) {
      if (!url.startsWith("https://")) throw java.io.IOException("안전하지 않은 주소라 파일을 받지 않았어요.")
      val c = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
      try {
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", d.userAgent)
        web.cookiesFor(url)?.let { c.setRequestProperty("Cookie", it) }
        val code = c.responseCode
        if (code in 300..399) {
          val next = c.getHeaderField("Location") ?: throw java.io.IOException("파일을 받지 못했어요 ($code).")
          url = java.net.URL(java.net.URL(url), next).toString()
          return@repeat
        }
        if (code != 200) throw java.io.IOException("파일을 받지 못했어요 ($code).")
        return c.inputStream.use { input ->
          val out = java.io.ByteArrayOutputStream()
          val buf = ByteArray(16 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_DOWNLOAD) throw java.io.IOException("파일이 너무 커요.")
          }
          out.toByteArray()
        }
      } finally {
        c.disconnect()
      }
    }
    throw java.io.IOException("파일 주소가 너무 여러 번 바뀌었어요.")
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
    // Never read a shopping site's pages as card spending.
    if (site.shop) { readOrders(pages.joinToString("\n") { it.second }, useAi); return@launch }
    if (_busy.value || pages.isEmpty()) return@launch
    _busy.value = true
    try {
      var aiFailed: String? = null
      val readings = pages.map { (_, text) ->
        val local = suspend { withContext(Dispatchers.Default) { StatementTextParser(clock.today()).parse(text) } }
        if (!useAi) local()
        else try {
          aiReader.read(text, clock.today())
        } catch (e: com.moneyflow.ai.AiRequestException) {
          aiFailed = e.message
          local()
        }
      }
      aiFailed?.let { _events.send(BrowserEvent.Info("AI가 응답하지 않은 화면은 기기에서 읽었어요. ($it)")) }
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
    if (site.shop) { readOrders(screenText, useAi); return@launch }
    _busy.value = true
    try {
      val result: StatementExtraction = if (useAi) {
        try {
          aiReader.read(screenText, clock.today()).let { r ->
            if (r.bill != null) r else r.copy(bill = com.moneyflow.domain.card.BillText.find(screenText, clock.today()))
          }
        } catch (e: com.moneyflow.ai.AiRequestException) {
          // The AI didn't answer: what the device can read is better than nothing.
          val local = withContext(Dispatchers.Default) { StatementTextParser(clock.today()).parse(screenText) }
          if (local.isEmpty) throw e
          _events.send(BrowserEvent.Info("AI가 응답하지 않아 기기에서 읽었어요. (${e.message})"))
          local
        }
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
  /** Keep the bill (결제 예정 금액) on the card account. */
  val saveBill: Boolean = true,
  /** Record installment purchases as monthly parts. */
  val splitInstallments: Boolean = false,
  /** Indexes of extraction.benefits to add. */
  val selectedBenefits: Set<Int> = emptySet(),
  val busy: Boolean = false,
  /** "삼성카드": the card company has no account yet; one can be made on import. */
  val newAccountName: String? = null,
  /** Import into a new account named [newAccountName] (made when importing). */
  val createNew: Boolean = false
) {
  val canImport: Boolean get() = !busy && (account != null || createNew) &&
    (selected.isNotEmpty() || selectedBenefits.isNotEmpty() || (savePerformance && extraction?.performance?.isNotEmpty() == true) ||
      (saveBill && extraction?.bill != null))
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
      val named = repo.namedAccount(site.provider, accounts)
      // A card company without its own account: offer to make it rather than filling another card.
      val newName = site.provider.takeIf { named == null && it.endsWith("카드") }
      val account = if (newName != null) null else repo.suggestAccount(site.provider, accounts)
      _state.value = PreviewState(
        site, extraction, accounts, account,
        splitInstallments = settings.current().splitInstallments,
        selectedBenefits = extraction.benefits.indices.toSet(),
        newAccountName = newName,
        createNew = newName != null
      )
      if (account != null) selectAccount(account) else if (newName != null) chooseNewAccount()
    }
  }

  /** Import into a new card account named after the card company. */
  fun chooseNewAccount() = viewModelScope.launch {
    val extraction = _state.value.extraction ?: return@launch
    // Nothing is recorded on a new account yet: no duplicates to look for.
    val rows = repo.review(extraction.items, -1L)
    _state.value = _state.value.copy(
      account = null,
      createNew = true,
      rows = rows,
      selected = rows.indices.filter { rows[it].selectedByDefault }.toSet()
    )
  }

  fun selectAccount(account: AccountEntity) = viewModelScope.launch {
    val extraction = _state.value.extraction ?: return@launch
    val rows = repo.review(extraction.items, account.id)
    _state.value = _state.value.copy(
      createNew = false,
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
  fun setSaveBill(on: Boolean) { _state.value = _state.value.copy(saveBill = on) }
  fun setSplitInstallments(on: Boolean) { _state.value = _state.value.copy(splitInstallments = on) }

  fun toggleBenefit(index: Int) {
    val s = _state.value
    _state.value = s.copy(selectedBenefits = if (index in s.selectedBenefits) s.selectedBenefits - index else s.selectedBenefits + index)
  }

  fun import() = viewModelScope.launch {
    val s = _state.value
    if (s.busy) return@launch
    _state.value = s.copy(busy = true)
    val account = try {
      s.account ?: s.newAccountName?.takeIf { s.createNew }?.let { name ->
        // Keep the new account in the state at once: a retry after a failure must not make another.
        repo.createCardAccount(name).also { made -> _state.value = _state.value.copy(account = made, createNew = false, accounts = _state.value.accounts + made) }
      }
    } catch (e: Exception) {
      null
    } ?: run {
      _state.value = _state.value.copy(busy = false); return@launch
    }
    val result = try {
      repo.import(s.rows.filterIndexed { i, _ -> i in s.selected }, account, s.splitInstallments)
    } catch (e: Exception) {
      com.moneyflow.util.ProblemLog.record("카드 가져오기", e)
      messages.show(com.moneyflow.util.UserErrors.message(e, "가져오지 못했어요. 다시 시도해 주세요."))
      _state.value = _state.value.copy(busy = false)
      return@launch
    }
    if (s.savePerformance) s.extraction?.performance?.firstOrNull()?.let { repo.savePerformance(account.id, it) }
    val bill = s.extraction?.bill?.takeIf { s.saveBill }
    bill?.let { repo.saveBill(account.id, it) }
    val benefits = s.extraction?.benefits.orEmpty().filterIndexed { i, _ -> i in s.selectedBenefits }
    val benefitResult = if (benefits.isEmpty()) null else cards.addBenefits(account.id, benefits, setTargetIfMissing = true)
    session.clear()
    messages.show(buildString {
      if (s.selected.isNotEmpty()) append("${account.name}에 ${result.added}건을 추가했어요.")
      if (result.cancelled > 0) append(" 취소 ${result.cancelled}건을 반영했어요.")
      if (result.skipped > 0) append(" ${result.skipped}건은 건너뛰었어요.")
      benefitResult?.let { append(" ${benefitMessage(it)}") }
      bill?.let { append(" 결제 예정 금액 ${com.moneyflow.util.Formatters.formatCurrency(it.amount)}을 저장했어요.") }
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
