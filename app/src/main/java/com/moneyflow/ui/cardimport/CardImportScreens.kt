package com.moneyflow.ui.cardimport

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.domain.cardimport.CardBrowsing
import com.moneyflow.domain.cardimport.CardSite
import com.moneyflow.domain.cardimport.CardSites
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.settings.SettingsItem
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Wait after a remembered page has loaded so its script-filled lists appear. */
private const val AUTO_SETTLE_MS = 2_500L

// ---------------------------------------------------------------------------------------------
// 1. Card company list
// ---------------------------------------------------------------------------------------------

@Composable
fun CardImportScreen(onBack: () -> Unit, onOpenSite: (CardSite) -> Unit, onOpenFile: () -> Unit = {}) {
  SettingsScaffold(title = "카드사에서 가져오기", onBack = onBack) {
    SettingsSection("파일로 가져오기") {
      SettingsItem(
        title = "엑셀·CSV 파일에서 가져오기",
        subtitle = "카드사·은행에서 받은 이용내역 파일로 예전 내역을 한 번에 넣어요",
        icon = Icons.Filled.UploadFile,
        onClick = onOpenFile,
        modifier = Modifier.testTag("card_import_file")
      )
    }
    SettingsSection("카드 혜택 찾기 (로그인 없이)") {
      SettingsItem(
        title = CardSites.cardGorilla.name,
        subtitle = "카드 이름으로 혜택을 자세히 찾아 'AI로 읽기'로 등록해요",
        icon = Icons.Filled.CreditCard,
        onClick = { onOpenSite(CardSites.cardGorilla) },
        modifier = Modifier.testTag("card_site_cardgorilla")
      )
    }
    SettingsSection("카드사 선택") {
      CardSites.all.forEach { site ->
        SettingsItem(
          title = site.name,
          subtitle = site.webLimit,
          icon = Icons.Filled.CreditCard,
          onClick = { onOpenSite(site) },
          modifier = Modifier.testTag("card_site_${site.id}")
        )
      }
    }
    SettingsSection("이렇게 가져와요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "앱 안에서 카드사 홈페이지가 열리면 직접 로그인하세요. 카드사 앱 승인·인증서도 평소처럼 쓰면 돼요.",
          "이용내역·명세서·실적 화면을 연 뒤 아래 '이 화면 가져오기'를 누르면 내용을 읽어 미리보기를 보여 줘요.",
          "카드 혜택 안내 화면을 'AI로 읽기'로 가져오면 혜택(할인율·한도·실적 조건)을 한 번에 등록할 수 있어요.",
          "자주 보는 화면은 '이 화면 기억하기'를 눌러 두세요. 다음부터는 로그인만 하고 '기억한 화면 읽기'를 누르면 차례로 열어 한 번에 읽어요.",
          "미리보기에서 확인한 내역만 가계부에 추가돼요. 이미 있는 내역은 '중복'으로 표시하고 빼 둬요.",
          "아이디·비밀번호는 머니플로우에 저장되지 않아요. 화면을 닫으면 로그인 기록(쿠키 등)을 지워요.",
          "'기기에서 읽기'는 내용을 밖으로 보내지 않아요. 'AI로 읽기'는 화면 글자(카드 이용내역)가 AI 설정에서 고른 서비스로 전송돼요."
        ).forEach {
          Row(Modifier.padding(vertical = 3.dp)) {
            Text("• ", style = MaterialTheme.typography.bodySmall, color = Gray600)
            Text(it, style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 2. In-app browser
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardBrowserScreen(
  onClose: () -> Unit,
  onOpenPreview: () -> Unit,
  viewModel: CardBrowserViewModel = hiltViewModel()
) {
  val site = viewModel.site
  val web = viewModel.web
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val aiLabel by viewModel.aiLabel.collectAsStateWithLifecycle()
  val desktop by viewModel.desktop.collectAsStateWithLifecycle()
  val loading by web.loading.collectAsStateWithLifecycle()
  val pageTitle by web.title.collectAsStateWithLifecycle()
  val popups by web.popups.collectAsStateWithLifecycle()
  val jsDialog by web.jsDialog.collectAsStateWithLifecycle()
  val blockedApp by web.blockedApp.collectAsStateWithLifecycle()
  val pageCheck by viewModel.pageCheck.collectAsStateWithLifecycle()
  val found by viewModel.found.collectAsStateWithLifecycle()
  val appCode by viewModel.appCode.collectAsStateWithLifecycle()
  var tipClosed by rememberSaveable { mutableStateOf(false) }
  var pendingText by remember { mutableStateOf<String?>(null) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var aiConsent by remember { mutableStateOf<String?>(null) }
  val savedPages by viewModel.savedPages.collectAsStateWithLifecycle()
  /** "필요한 것만 가져오기": the site's known pages (phone page) and the pages the user remembered. */
  val tourPages = remember(savedPages, desktop) {
    val known = if (desktop == true) emptyList() else site.pages.map { (title, url) -> com.moneyflow.domain.cardimport.SavedPage(url, title, 0L) }
    (known + savedPages).distinctBy { it.url }
  }
  /** Asking how to read the remembered pages (device / AI). */
  var autoAsk by remember { mutableStateOf(false) }
  var autoAiConsent by remember { mutableStateOf(false) }
  /** "2/3" while touring the remembered pages; null when not running. */
  var autoProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var autoJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  var managePages by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  /** The buttons at the bottom folded into a small button (so the site's own buttons are reachable). */
  var folded by rememberSaveable { mutableStateOf(false) }
  val imeVisible = WindowInsets.isImeVisible
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current
  val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

  LaunchedEffect(viewModel) {
    viewModel.events.collect { e ->
      when (e) {
        BrowserEvent.OpenPreview -> onOpenPreview()
        is BrowserEvent.Error -> error = e.message
        is BrowserEvent.Info -> error = e.message
      }
    }
  }

  // A login page: fold the buttons away. Payments on the page: show them again.
  LaunchedEffect(pageCheck) {
    val c = pageCheck ?: return@LaunchedEffect
    if (c.login) folded = true else if (c.items > 0 || c.performance > 0) folded = false
  }

  /** Opens every remembered page in turn, reads it, then hands all texts to the view model. */
  fun runSavedPages(useAi: Boolean) {
    val pages = tourPages
    autoJob = scope.launch {
      val texts = mutableListOf<Pair<com.moneyflow.domain.cardimport.SavedPage, String>>()
      try {
        for ((n, page) in pages.withIndex()) {
          autoProgress = n + 1 to pages.size
          web.closeTopPopupsForTour()
          // Wait for this page's own load to finish, not the previous page's.
          val loadsBefore = web.pageLoads.value
          web.loadInFront(page.url)
          kotlinx.coroutines.withTimeoutOrNull(25_000) { web.pageLoads.first { it > loadsBefore } }
          // Card sites fill their lists with scripts after the page has loaded.
          kotlinx.coroutines.delay(AUTO_SETTLE_MS)
          val o = runCatching { org.json.JSONObject(web.evaluate(CardBrowsing.READ_PAGE_JS)) }.getOrNull()
          if (o?.optBoolean("login") == true) {
            error = "로그인이 필요해요. 이 화면에서 로그인한 뒤 '기억한 화면 읽기'를 다시 눌러 주세요."
            return@launch
          }
          o?.optString("t")?.takeIf { it.isNotBlank() }?.let { texts += page to it }
        }
        if (texts.isEmpty()) error = "기억한 화면을 열지 못했어요. 로그인 상태를 확인해 주세요."
        else viewModel.readMany(texts, useAi)
      } finally {
        autoProgress = null
      }
    }
  }

  val goBack: () -> Unit = {
    val top = popups.lastOrNull()
    val main = web.front
    when {
      top != null && top.canGoBack() -> top.goBack()
      top != null -> web.closeTopPopup()
      main != null && main.canGoBack() -> main.goBack()
      else -> onClose()
    }
  }
  BackHandler(onBack = goBack)

  val readThisPage: () -> Unit = {
    scope.launch {
      val text = web.evaluate(CardBrowsing.READ_TEXT_JS)
      if (text.isBlank()) error = "화면 내용을 읽지 못했어요. 페이지가 다 열린 뒤 다시 시도해 주세요."
      else pendingText = text
    }
  }

  Scaffold(
    modifier = Modifier.imePadding(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(site.name + if (desktop == true) " · PC 화면" else "", fontWeight = FontWeight.Bold, maxLines = 1)
            Text(pageTitle.ifEmpty { if (loading) "여는 중…" else "" }, style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
        navigationIcon = {
          IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
        },
        actions = {
          IconButton(onClick = { web.front?.reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "새로고침") }
          Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("card_browser_menu")) {
              Icon(Icons.Filled.MoreVert, contentDescription = "더 보기")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text("이용내역 메뉴 찾기") },
                onClick = { menuOpen = false; viewModel.findLinks() },
                modifier = Modifier.testTag("card_find_links")
              )
              DropdownMenuItem(
                text = { Text(if (desktop == true) "휴대폰 화면으로 보기" else "PC 화면으로 보기") },
                onClick = { menuOpen = false; viewModel.setDesktop(desktop != true) },
                modifier = Modifier.testTag("card_toggle_desktop")
              )
              DropdownMenuItem(
                text = { Text("화면이 눌리지 않을 때: 가림막 걷기") },
                onClick = { menuOpen = false; viewModel.removeOverlays() },
                modifier = Modifier.testTag("card_remove_overlays")
              )
              DropdownMenuItem(
                text = { Text("이 화면 기억하기") },
                onClick = { menuOpen = false; viewModel.savePage(web.front?.url, web.front?.title.orEmpty()) }
              )
              if (savedPages.isNotEmpty()) DropdownMenuItem(text = { Text("기억한 화면 관리") }, onClick = { menuOpen = false; managePages = true })
              DropdownMenuItem(text = { Text("로그아웃하고 닫기", color = AccentRed) }, onClick = { menuOpen = false; onClose() })
            }
          }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface)
      )
    },
    bottomBar = {
      // The keyboard needs the room while typing (login), and a folded bar leaves the site's own buttons free.
      if (!imeVisible && !folded) {
        Surface(color = CardSurface, shadowElevation = 6.dp) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (busy || autoProgress != null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp), color = TossBlue)
            val check = pageCheck
            if (check != null && check.items > 0 && autoProgress == null) {
              Text(
                "이 화면에서 결제 내역 ${check.items}건이 보여요",
                style = MaterialTheme.typography.labelMedium, color = TossBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp).testTag("card_found_items")
              )
            }
            val progress = autoProgress
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              when {
                progress != null -> {
                  Text("필요한 화면 읽는 중 (${progress.first}/${progress.second})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                  TextButton(onClick = { autoJob?.cancel() }) { Text("멈추기") }
                }
                tourPages.isNotEmpty() -> {
                  // Log in, then one tap: 이용내역 · 결제 예정 금액 · 실적 pages are read in turn.
                  Button(
                    onClick = { autoAsk = true },
                    enabled = !busy && !loading,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("card_auto_read_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
                    shape = RoundedCornerShape(14.dp)
                  ) { Text("필요한 것만 가져오기", fontWeight = FontWeight.Bold, maxLines = 1) }
                  OutlinedButton(
                    onClick = readThisPage,
                    enabled = !busy && !loading,
                    modifier = Modifier.height(48.dp).testTag("card_read_button")
                  ) { Text("이 화면") }
                  IconButton(onClick = { folded = true }, modifier = Modifier.testTag("card_fold_bar")) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "버튼 접기")
                  }
                }
                else -> {
                  Button(
                    onClick = readThisPage,
                    enabled = !busy && !loading,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("card_read_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
                    shape = RoundedCornerShape(14.dp)
                  ) { Text("이 화면 가져오기", fontWeight = FontWeight.Bold) }
                  run {
                    OutlinedButton(
                      onClick = { viewModel.findLinks() },
                      enabled = !busy && !loading,
                      modifier = Modifier.height(48.dp).testTag("card_find_links_button")
                    ) { Text("메뉴 찾기") }
                  }
                  IconButton(onClick = { folded = true }, modifier = Modifier.testTag("card_fold_bar")) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "버튼 접기")
                  }
                }
              }
            }
          }
        }
      }
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      if (desktop != null) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx -> web.mainView(ctx, viewModel.startUrl()) },
          onRelease = { web.detach(it) }
        )
      }
      popups.lastOrNull()?.let { popup ->
        Column(Modifier.fillMaxSize().background(CardSurface)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Gray100).padding(start = 16.dp)
          ) {
            Text("카드사 창 (팝업)", style = MaterialTheme.typography.labelLarge, color = Gray600, modifier = Modifier.weight(1f))
            IconButton(onClick = { web.closeTopPopup() }, modifier = Modifier.testTag("card_popup_close")) {
              Icon(Icons.Filled.Close, contentDescription = "팝업 닫기")
            }
          }
          androidx.compose.runtime.key(popup) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { ctx -> popup.also { web.attach(it, ctx) } },
              onRelease = { web.detach(it) }
            )
          }
        }
      }
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = TossBlue)
      Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val code = appCode
        if (code != null && popups.isEmpty()) {
          Surface(color = TossBlue, shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("card_app_code")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
              Column(Modifier.weight(1f)) {
                Text("앱에 입력할 코드", style = MaterialTheme.typography.labelSmall, color = OnBrand)
                Text(code.chunked(if (code.length == 7) 3 else 4).joinToString(" "), color = OnBrand, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
              }
              TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                if (!openCardApp(context, site.appNames)) {
                  error = "코드를 복사했어요. ${site.appNames.first()} 앱을 직접 열어 붙여 넣은 뒤 머니플로우로 돌아오세요. 이 화면은 그대로 있어요."
                }
              }) { Text("복사하고 앱 열기", color = OnBrand, fontWeight = FontWeight.Bold) }
            }
          }
        }
        val tip = listOfNotNull(site.webLimit?.let { "⚠ $it" }, site.loginTip).joinToString("\n").ifEmpty { null }
        val check = pageCheck
        if (tip != null && !tipClosed && code == null && (check == null || check.items == 0) && popups.isEmpty()) {
          Surface(color = CardSurface, shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("card_login_tip")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
              Text("💡 $tip", style = MaterialTheme.typography.bodySmall, color = Gray900, modifier = Modifier.weight(1f))
              IconButton(onClick = { tipClosed = true }) { Icon(Icons.Filled.Close, contentDescription = "안내 닫기", tint = Gray600) }
            }
          }
        }
      }
      if (folded && !imeVisible) {
        androidx.compose.material3.SmallFloatingActionButton(
          onClick = { folded = false },
          containerColor = TossBlue,
          contentColor = OnBrand,
          modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).testTag("card_unfold_bar")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
            Text("가져오기", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  jsDialog?.let { d ->
    AlertDialog(
      onDismissRequest = { d.cancel(); web.answeredDialog() },
      title = { Text(site.name, fontWeight = FontWeight.Bold) },
      text = { Text(d.message) },
      confirmButton = { TextButton(onClick = { d.ok(); web.answeredDialog() }) { Text("확인", fontWeight = FontWeight.Bold) } },
      dismissButton = if (d.isConfirm) ({ TextButton(onClick = { d.cancel(); web.answeredDialog() }) { Text("취소") } }) else null
    )
  }

  blockedApp?.let {
    AlertDialog(
      onDismissRequest = web::dismissBlockedApp,
      title = { Text("카드사 앱으로 넘어가려고 해요", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "${site.name} 홈페이지가 스스로 앱을 열려고 해서 멈춰 두었어요. 웹에서 계속하려면 '여기서 계속'을 누르세요. " +
            "로그인 승인처럼 앱이 꼭 필요하면 '앱 열기'를 누르고, 끝나면 머니플로우로 돌아오세요. 이 화면은 그대로 있어요."
        )
      },
      confirmButton = { TextButton(onClick = web::dismissBlockedApp) { Text("여기서 계속", fontWeight = FontWeight.Bold) } },
      dismissButton = { TextButton(onClick = { web.openBlockedApp(context) }) { Text("앱 열기") } }
    )
  }

  found?.let { links ->
    AlertDialog(
      onDismissRequest = viewModel::dismissFound,
      title = { Text("어느 화면을 열까요?", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          links.forEach { link ->
            Text(
              link.text,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.fillMaxWidth().clickable { viewModel.openFound(link) }.padding(vertical = 10.dp)
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = viewModel::dismissFound) { Text("닫기") } }
    )
  }

  pendingText?.let { text ->
    AlertDialog(
      onDismissRequest = { pendingText = null },
      title = { Text("어떻게 읽을까요?", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("기기에서 읽기: 화면 내용을 밖으로 보내지 않아요. 카드사 화면 모양에 따라 일부를 못 읽을 수 있어요.", style = MaterialTheme.typography.bodySmall)
          Text(
            if (aiLabel != null) "AI로 읽기: 화면 글자(카드 이용내역·혜택)를 ${aiLabel}(으)로 보내 정확하게 읽어요. 혜택 화면은 AI로만 읽을 수 있어요."
            else "AI로 읽기: 설정 → AI 설정에서 AI를 켜면 쓸 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { pendingText = null; viewModel.read(text, useAi = false) }) { Text("기기에서 읽기", fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        if (aiLabel != null) TextButton(onClick = { pendingText = null; aiConsent = text }) { Text("AI로 읽기") }
      }
    )
  }

  aiConsent?.let { text ->
    AlertDialog(
      onDismissRequest = { aiConsent = null },
      title = { Text("카드 이용내역을 AI로 보낼까요?", fontWeight = FontWeight.Bold) },
      text = { Text("지금 화면의 글자(가맹점, 금액, 날짜 등)가 ${aiLabel}(으)로 전송돼요. 카드번호 일부나 이름이 화면에 있으면 함께 전송될 수 있어요.") },
      confirmButton = { TextButton(onClick = { aiConsent = null; viewModel.read(text, useAi = true) }) { Text("보내고 읽기", fontWeight = FontWeight.Bold) } },
      dismissButton = { TextButton(onClick = { aiConsent = null }) { Text("취소") } }
    )
  }

  if (autoAsk) {
    AlertDialog(
      onDismissRequest = { autoAsk = false },
      title = { Text("필요한 화면 ${tourPages.size}개를 읽을까요?", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("로그인한 상태에서 아래 화면을 차례로 열어 이용내역·결제 예정 금액·실적만 읽고, 한 번에 미리보기로 보여 드려요.", style = MaterialTheme.typography.bodySmall)
          Text(tourPages.joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, color = Gray900, fontWeight = FontWeight.SemiBold)
          Text(
            if (aiLabel != null) "AI로 읽기: 각 화면의 글자를 ${aiLabel}(으)로 보내 정확하게 읽어요."
            else "AI로 읽기는 설정 → AI 설정에서 AI를 켜면 쓸 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = Gray600
          )
        }
      },
      confirmButton = { TextButton(onClick = { autoAsk = false; runSavedPages(useAi = false) }) { Text("기기에서 읽기", fontWeight = FontWeight.Bold) } },
      dismissButton = { if (aiLabel != null) TextButton(onClick = { autoAsk = false; autoAiConsent = true }) { Text("AI로 읽기") } }
    )
  }

  if (autoAiConsent) {
    AlertDialog(
      onDismissRequest = { autoAiConsent = false },
      title = { Text("카드 이용내역을 AI로 보낼까요?", fontWeight = FontWeight.Bold) },
      text = { Text("화면 ${tourPages.size}개의 글자(가맹점, 금액, 날짜 등)가 ${aiLabel}(으)로 전송돼요. 카드번호 일부나 이름이 화면에 있으면 함께 전송될 수 있어요.") },
      confirmButton = { TextButton(onClick = { autoAiConsent = false; runSavedPages(useAi = true) }) { Text("보내고 읽기", fontWeight = FontWeight.Bold) } },
      dismissButton = { TextButton(onClick = { autoAiConsent = false }) { Text("취소") } }
    )
  }

  if (managePages) {
    AlertDialog(
      onDismissRequest = { managePages = false },
      title = { Text("기억한 화면", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (savedPages.isEmpty()) Text("기억한 화면이 없어요.", color = Gray600)
          savedPages.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.url.substringAfter("://").take(60), style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              TextButton(onClick = { viewModel.removePage(p) }) { Text("지우기", color = AccentRed) }
            }
          }
          Text("화면은 카드사마다 ${com.moneyflow.domain.cardimport.SavedPages.MAX_PER_SITE}개까지 기억해요. 아이디·비밀번호는 기억하지 않아요.", style = MaterialTheme.typography.labelSmall, color = Gray600)
        }
      },
      confirmButton = { TextButton(onClick = { managePages = false }) { Text("닫기") } }
    )
  }

  error?.let { msg ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("확인이 필요해요", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }
    )
  }
}

/** Opens the card company's app (found by its name among installed apps); false when none is installed. */
private fun openCardApp(context: android.content.Context, names: List<String>): Boolean {
  val pm = context.packageManager
  val launchers = runCatching {
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
  }.getOrDefault(emptyList())
  fun norm(s: String) = s.replace(" ", "").lowercase()
  val pick = names.firstNotNullOfOrNull { name ->
    launchers.firstOrNull { norm(it.loadLabel(pm).toString()).contains(norm(name)) }
  } ?: return false
  return runCatching {
    context.startActivity(pm.getLaunchIntentForPackage(pick.activityInfo.packageName) ?: return false)
  }.isSuccess
}

// ---------------------------------------------------------------------------------------------
// 3. Preview & import
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardImportPreviewScreen(
  onBack: () -> Unit,
  onDone: () -> Unit,
  viewModel: CardImportPreviewViewModel = hiltViewModel()
) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  var accountMenu by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

  Scaffold(
    containerColor = Gray100,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text("가져올 내역 확인", fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Gray100)
      )
    },
    bottomBar = {
      Surface(color = CardSurface, shadowElevation = 6.dp) {
        Button(
          onClick = viewModel::import,
          enabled = s.canImport,
          modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp).testTag("card_import_button"),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
          shape = RoundedCornerShape(14.dp)
        ) {
          val label = listOfNotNull(
            s.selected.size.takeIf { it > 0 }?.let { "내역 ${it}건" },
            s.selectedBenefits.size.takeIf { it > 0 }?.let { "혜택 ${it}개" }
          ).joinToString(" · ")
          Text(if (label.isEmpty()) "실적만 저장" else "$label 추가", fontWeight = FontWeight.Bold)
        }
      }
    }
  ) { padding ->
    val extraction = s.extraction
    if (extraction == null) {
      EmptyState(icon = Icons.Filled.CreditCard, title = "읽은 내용이 없어요", description = "카드사 화면에서 다시 가져와 주세요.", modifier = Modifier.padding(padding))
      return@Scaffold
    }
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item(key = "account") {
        Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp)) {
            Text("${s.site?.name ?: ""} · ${extraction.readBy}", style = MaterialTheme.typography.labelMedium, color = Gray600)
            Spacer(Modifier.height(6.dp))
            Text("어느 계좌(카드)에 넣을까요?", fontWeight = FontWeight.Bold, color = Gray900)
            Box {
              OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                  s.account?.let { "${it.name} (${it.bankName})" }
                    ?: s.newAccountName?.takeIf { s.createNew }?.let { "＋ 새 카드 계좌 '$it' 만들기" }
                    ?: "계좌를 선택해 주세요",
                  maxLines = 1, overflow = TextOverflow.Ellipsis
                )
              }
              DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                s.newAccountName?.let { name ->
                  DropdownMenuItem(text = { Text("＋ 새 카드 계좌 '$name' 만들기", color = TossBlue) }, onClick = { accountMenu = false; viewModel.chooseNewAccount() })
                }
                s.accounts.forEach { a ->
                  DropdownMenuItem(text = { Text("${a.name} · ${a.accountType.displayName}") }, onClick = { accountMenu = false; viewModel.selectAccount(a) })
                }
              }
            }
            if (s.accounts.isEmpty()) {
              Text("먼저 자산 탭에서 카드 계좌를 추가해 주세요.", color = AccentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
          }
        }
      }

      if (extraction.performance.isNotEmpty()) {
        item(key = "performance") {
          Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
              Text("카드사 화면의 실적", fontWeight = FontWeight.Bold, color = Gray900)
              extraction.performance.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                  Text(p.label, color = Gray600, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(Formatters.formatCurrency(p.amount), fontWeight = FontWeight.Bold, color = Gray900)
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("이 카드 계좌에 실적 저장", modifier = Modifier.weight(1f), color = Gray900)
                Switch(checked = s.savePerformance, onCheckedChange = viewModel::setSavePerformance)
              }
            }
          }
        }
      }

      extraction.bill?.let { bill ->
        item(key = "bill") {
          Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.fillMaxWidth().testTag("preview_bill")) {
            Column(Modifier.padding(16.dp)) {
              Text("결제 예정 금액", fontWeight = FontWeight.Bold, color = Gray900)
              Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(bill.dueDate?.let { "${it.monthValue}/${it.dayOfMonth} 결제" } ?: bill.label, color = Gray600, modifier = Modifier.weight(1f))
                Text(Formatters.formatCurrency(bill.amount), fontWeight = FontWeight.Bold, color = Gray900)
              }
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("이 카드 계좌에 저장 (결제일 알림에 금액 표시)", modifier = Modifier.weight(1f), color = Gray900)
                Switch(checked = s.saveBill, onCheckedChange = viewModel::setSaveBill)
              }
            }
          }
        }
      }

      if (extraction.benefits.isNotEmpty()) {
        item(key = "benefits") {
          Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
              Text("카드사 화면의 혜택", fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.padding(horizontal = 8.dp))
              Text(
                "고른 혜택이 이 카드 계좌에 등록돼요. 같은 이름의 혜택은 건너뛰어요. 실적 조건이 없으면 혜택에 필요한 전월 실적으로 설정해요.",
                style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
              extraction.benefits.forEachIndexed { i, b ->
                Row(
                  Modifier.fillMaxWidth().clickable { viewModel.toggleBenefit(i) }.padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Checkbox(checked = i in s.selectedBenefits, onCheckedChange = { viewModel.toggleBenefit(i) })
                  Column(Modifier.weight(1f)) {
                    Text(b.title, fontWeight = FontWeight.SemiBold, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(com.moneyflow.ui.account.benefitSummary(b), style = MaterialTheme.typography.bodySmall, color = Gray600)
                  }
                }
              }
            }
          }
        }
      }

      if (s.rows.isNotEmpty()) {
        item(key = "header") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내역 ${s.rows.size}건 · 선택 ${s.selected.size}건", fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.setAll(s.selected.size != s.rows.size) }) {
              Text(if (s.selected.size == s.rows.size) "모두 해제" else "모두 선택")
            }
          }
        }
        itemsIndexed(s.rows, key = { i, _ -> "row_$i" }) { i, row ->
          val item = row.item
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardSurface,
            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggle(i) }
          ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = i in s.selected, onCheckedChange = { viewModel.toggle(i) })
              Column(Modifier.weight(1f)) {
                Text(item.merchant, fontWeight = FontWeight.SemiBold, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val tags = buildList {
                  add(row.category)
                  item.installmentMonths?.let { add("할부 ${it}개월") }
                  if (row.isDuplicate) add("이미 있음")
                  if (row.cancelledInStatement) add("같은 명세서에서 취소됨")
                  if (item.isCancel) add("취소 → 원래 결제 삭제")
                  if (item.isIncome) add("입금")
                }
                Text(
                  "${item.date.monthValue}.${item.date.dayOfMonth}${item.time?.let { " $it" } ?: ""} · ${tags.joinToString(" · ")}",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (row.isDuplicate || item.isCancel || row.cancelledInStatement) AccentOrange else Gray600,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(Modifier.width(8.dp))
              Text(
                (if (item.isCancel || item.isIncome) "+" else "-") + Formatters.formatCurrency(item.amount),
                fontWeight = FontWeight.Bold,
                color = if (item.isCancel || item.isIncome) TossBlue else AccentRed
              )
            }
          }
        }
      }
      if (s.rows.any { (it.item.installmentMonths ?: 0) > 1 && !it.item.isCancel }) {
        item(key = "installments") {
          Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text("할부는 개월 수만큼 나눠 기록", color = Gray900, fontWeight = FontWeight.SemiBold)
                Text("매달 지출이 실제 청구액과 맞아요", style = MaterialTheme.typography.bodySmall, color = Gray600)
              }
              Switch(checked = s.splitInstallments, onCheckedChange = viewModel::setSplitInstallments)
            }
          }
        }
      }
      item(key = "note") {
        Text(
          "'이미 있음'은 같은 날 같은 금액의 내역이 이 계좌에 있어서 빼 둔 거예요. 필요하면 직접 선택하세요.",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          modifier = Modifier.padding(top = 4.dp, bottom = 80.dp)
        )
      }
    }
  }
}
