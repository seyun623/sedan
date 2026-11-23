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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Refresh
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.json.JSONArray

// ---------------------------------------------------------------------------------------------
// 1. Card company list
// ---------------------------------------------------------------------------------------------

@Composable
fun CardImportScreen(onBack: () -> Unit, onOpenSite: (CardSite) -> Unit) {
  SettingsScaffold(title = "카드사에서 가져오기", onBack = onBack) {
    SettingsSection("카드사 선택") {
      CardSites.all.forEach { site ->
        SettingsItem(
          title = site.name,
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

/** Collects visible text of the page and its same-origin frames. */
private const val READ_TEXT_JS = """
(function(){
  function t(d){ try{ var s = d.body ? d.body.innerText : '';
    var f = d.querySelectorAll('iframe,frame');
    for (var i=0;i<f.length;i++){ try{ s += '\n' + t(f[i].contentDocument); }catch(e){} }
    return s; }catch(e){ return ''; } }
  return t(document);
})()
"""

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CardBrowserScreen(
  onClose: () -> Unit,
  onOpenPreview: () -> Unit,
  viewModel: CardBrowserViewModel = hiltViewModel()
) {
  val site = viewModel.site
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val aiLabel by viewModel.aiLabel.collectAsStateWithLifecycle()
  var webView by remember { mutableStateOf<WebView?>(null) }
  var loading by remember { mutableStateOf(true) }
  var pageTitle by remember { mutableStateOf(site.name) }
  var pendingText by remember { mutableStateOf<String?>(null) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var aiConsent by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { e ->
      when (e) {
        BrowserEvent.OpenPreview -> onOpenPreview()
        is BrowserEvent.Error -> error = e.message
      }
    }
  }

  // Always wipe the login session when the browser screen goes away.
  DisposableEffect(Unit) {
    onDispose {
      webView?.let { wv ->
        wv.stopLoading()
        wv.clearHistory()
        wv.clearCache(true)
        wv.clearFormData()
        wv.destroy()
      }
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
      WebStorage.getInstance().deleteAllData()
    }
  }

  BackHandler {
    val wv = webView
    if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(site.name, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(pageTitle, style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
        navigationIcon = {
          IconButton(onClick = { val wv = webView; if (wv != null && wv.canGoBack()) wv.goBack() else onClose() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
          }
        },
        actions = {
          IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "새로고침") }
          IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "로그아웃하고 닫기") }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface)
      )
    },
    bottomBar = {
      Surface(color = CardSurface, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
          if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = TossBlue)
          Button(
            onClick = {
              webView?.evaluateJavascript(READ_TEXT_JS) { raw ->
                val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
                if (text.isBlank()) error = "화면 내용을 읽지 못했어요. 페이지가 다 열린 뒤 다시 시도해 주세요."
                else pendingText = text
              }
            },
            enabled = !busy && !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("card_read_button"),
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
            shape = RoundedCornerShape(14.dp)
          ) { Text("이 화면 가져오기", fontWeight = FontWeight.Bold) }
          Text(
            "이용내역·명세서·실적·혜택 화면에서 눌러 주세요",
            style = MaterialTheme.typography.labelSmall,
            color = Gray600,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
          )
        }
      }
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Some card sites refuse embedded browsers; present as the regular mobile browser.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://") || url.startsWith("http://")) return false
                openExternal(view, url)
                return true
              }

              override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
              override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                pageTitle = view.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
              }
            }
            loadUrl(site.url)
            webView = this
          }
        }
      )
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = TossBlue)
    }
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

  error?.let { msg ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("알림", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }
    )
  }
}

/** Card sites hand off to their apps (approval, 간편인증) with intent:// or custom schemes. */
private fun openExternal(view: WebView, url: String) {
  val ctx = view.context
  try {
    val intent = if (url.startsWith("intent:")) {
      Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        component = null
        selector = null
      }
    } else {
      Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    try {
      ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      val fallback = intent.getStringExtra("browser_fallback_url")
      if (fallback != null && fallback.startsWith("https://")) view.loadUrl(fallback)
      else intent.`package`?.let { pkg ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
      }
    }
  } catch (_: Exception) {
    // Malformed or unsupported link: ignore.
  }
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
                Text(s.account?.let { "${it.name} (${it.bankName})" } ?: "계좌를 선택해 주세요", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
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
            Text("결제 내역 ${s.rows.size}건 · 선택 ${s.selected.size}건", fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
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
                  if (item.isCancel) add("취소 → 원래 결제 삭제")
                }
                Text(
                  "${item.date.monthValue}.${item.date.dayOfMonth}${item.time?.let { " $it" } ?: ""} · ${tags.joinToString(" · ")}",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (row.isDuplicate || item.isCancel) AccentOrange else Gray600,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(Modifier.width(8.dp))
              Text(
                (if (item.isCancel) "+" else "-") + Formatters.formatCurrency(item.amount),
                fontWeight = FontWeight.Bold,
                color = if (item.isCancel) TossBlue else AccentRed
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
