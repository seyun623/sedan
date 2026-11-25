package com.moneyflow.ui.cardimport

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.moneyflow.domain.cardimport.CardBrowsing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** A JavaScript alert / confirm from the card site, answered on our own dialog. */
data class JsDialog(val message: String, val isConfirm: Boolean, private val result: JsResult) {
  fun ok() = result.confirm()
  fun cancel() = result.cancel()
}

/** A file the card site started to download (이용내역 엑셀 저장). */
data class SiteDownload(val url: String, val userAgent: String, val contentDisposition: String?, val mimeType: String?)

/**
 * The card company page and its login, kept outside the screen. The screen goes away while the
 * app lock is shown (after approving the login in the card company's app) and while the preview
 * is open; the page and login stay, so the user doesn't have to start over. Everything is wiped
 * in [close] (leaving the card browser).
 */
class CardWebSession(private val appContext: Context, desktop: Boolean) {
  private val _loading = MutableStateFlow(true)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()
  private val _title = MutableStateFlow("")
  val title: StateFlow<String> = _title.asStateFlow()
  /** Pop-up windows the site opened (top = last), shown over the page. */
  private val _popups = MutableStateFlow<List<WebView>>(emptyList())
  val popups: StateFlow<List<WebView>> = _popups.asStateFlow()
  private val _jsDialog = MutableStateFlow<JsDialog?>(null)
  val jsDialog: StateFlow<JsDialog?> = _jsDialog.asStateFlow()
  /** The site tried to switch to its app by itself (blocked); the user may still open it. */
  private val _blockedApp = MutableStateFlow<String?>(null)
  val blockedApp: StateFlow<String?> = _blockedApp.asStateFlow()
  private val _download = MutableStateFlow<SiteDownload?>(null)
  val download: StateFlow<SiteDownload?> = _download.asStateFlow()
  /** Counts finished page loads (the screen checks each new page). */
  private val _pageLoads = MutableStateFlow(0)
  val pageLoads: StateFlow<Int> = _pageLoads.asStateFlow()

  var desktop: Boolean = desktop
    private set
  private var main: WebView? = null
  private var closed = false

  /** Sets PC / phone mode before the page is first opened. */
  fun prepare(desktop: Boolean) { if (main == null) this.desktop = desktop }

  /** The page in front: the top pop-up, else the main page. */
  val front: WebView? get() = _popups.value.lastOrNull() ?: main

  /** The main page view for the screen, created once and moved to the new screen afterwards. */
  fun mainView(screenContext: Context, startUrl: String): WebView {
    main?.let { wv -> attach(wv, screenContext); return wv }
    val wv = newWebView(screenContext)
    main = wv
    wv.loadUrl(startUrl)
    return wv
  }

  /** Moves a kept view to the screen now shown (its old parent is gone). */
  fun attach(wv: WebView, screenContext: Context) {
    (wv.parent as? ViewGroup)?.removeView(wv)
    (wv.context as? MutableContextWrapper)?.baseContext = screenContext
  }

  /** The screen went away: keep the view, but not the screen. */
  fun detach(wv: WebView) {
    (wv.parent as? ViewGroup)?.removeView(wv)
    (wv.context as? MutableContextWrapper)?.baseContext = appContext
  }

  /** Switches between the PC page and the phone page and opens [url] in that mode. */
  fun setDesktop(on: Boolean, url: String) {
    desktop = on
    closePopups()
    main?.let { wv -> applyUserAgent(wv); wv.loadUrl(url) }
  }

  fun closeTopPopup() {
    val top = _popups.value.lastOrNull() ?: return
    _popups.value = _popups.value.dropLast(1)
    top.destroyQuietly()
  }

  private fun closePopups() { while (_popups.value.isNotEmpty()) closeTopPopup() }

  /** Before reading remembered pages one by one in the main page. */
  fun closeTopPopupsForTour() = closePopups()

  fun answeredDialog() { _jsDialog.value = null }
  /** App links the user chose to stay on the web for (stopped silently from now on). */
  private val declinedApps = mutableSetOf<String>()

  fun dismissBlockedApp() {
    _blockedApp.value?.let { declinedApps += CardBrowsing.appLinkKind(it) }
    _blockedApp.value = null
  }
  fun downloadHandled() { _download.value = null }

  /** Opens the app the site wanted (the user agreed). */
  fun openBlockedApp(context: Context) {
    val url = _blockedApp.value ?: return
    _blockedApp.value = null
    openExternal(context, url, front)
  }

  /** Leaves the card site: closes the pages and removes the login (cookies, storage, cache). */
  fun close() {
    if (closed) return
    closed = true
    closePopups()
    _jsDialog.value?.cancel()
    _jsDialog.value = null
    main?.let { wv ->
      wv.stopLoading()
      wv.clearHistory()
      wv.clearCache(true)
      wv.clearFormData()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.destroy()
    }
    main = null
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
  }

  private fun WebView.destroyQuietly() {
    runCatching { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }
  }

  private fun applyUserAgent(wv: WebView) {
    val base = WebSettings.getDefaultUserAgent(appContext)
    wv.settings.userAgentString = if (desktop) CardBrowsing.desktopUserAgent(base) else CardBrowsing.mobileUserAgent(base)
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun newWebView(screenContext: Context): WebView = WebView(MutableContextWrapper(screenContext)).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // Login and statement pages often open in a pop-up window (shown over the page).
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.textZoom = 100
    applyUserAgent(this)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    webViewClient = client
    webChromeClient = chrome
    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
      _download.value = SiteDownload(url, userAgent, contentDisposition, mimetype)
    }
  }

  private val client = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url.toString()
      return when (CardBrowsing.linkAction(url, request.hasGesture())) {
        CardBrowsing.LinkAction.LOAD_IN_PAGE -> false
        CardBrowsing.LinkAction.OPEN_APP -> { openExternal(view.context, url, view); true }
        CardBrowsing.LinkAction.BLOCK -> {
          // The site moved to its app by itself: stay on the web page (its web version if it has one).
          val fallback = CardBrowsing.fallbackUrl(url)
          if (fallback != null) view.loadUrl(fallback)
          else if (!url.startsWith("javascript:", ignoreCase = true) && !url.startsWith("about:") &&
            CardBrowsing.appLinkKind(url) !in declinedApps && _blockedApp.value == null
          ) _blockedApp.value = url
          true
        }
      }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
      if (view == main) _loading.value = true
    }

    override fun onPageFinished(view: WebView, url: String?) {
      if (view == main) _loading.value = false
      if (view == front) {
        _title.value = view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: ""
        _pageLoads.value += 1
      }
    }
  }

  private val chrome = object : WebChromeClient() {
    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
      _jsDialog.value?.cancel()
      _jsDialog.value = JsDialog(message.orEmpty(), isConfirm = false, result = result)
      return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
      _jsDialog.value?.cancel()
      _jsDialog.value = JsDialog(message.orEmpty(), isConfirm = true, result = result)
      return true
    }

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
      result.confirm(defaultValue.orEmpty())
      return true
    }

    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
      result.confirm()
      return true
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
      if (closed) return false
      val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
      val popup = newWebView((view.context as? MutableContextWrapper)?.baseContext ?: view.context)
      transport.webView = popup
      resultMsg.sendToTarget()
      _popups.value = _popups.value + popup
      return true
    }

    override fun onCloseWindow(window: WebView) {
      if (window in _popups.value) {
        _popups.value = _popups.value - window
        window.destroyQuietly()
      }
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
      if (view == front) _title.value = title?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: _title.value
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
      callback.invoke(origin, false, false)
    }

    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
  }

  /** Runs [js] on the front page and returns its string result ("" on failure). */
  suspend fun evaluate(js: String): String {
    val wv = front ?: return ""
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
      wv.evaluateJavascript(js) { raw ->
        val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
        if (cont.isActive) cont.resumeWith(Result.success(text))
      }
    }
  }

  fun loadInFront(url: String) { front?.loadUrl(url) }

  /** Cookies of the site (to download the file the site offered with the same login). */
  fun cookiesFor(url: String): String? = CookieManager.getInstance().getCookie(url)

  companion object {
    /** Card sites hand off to their apps (approval, 간편인증) with intent:// or custom schemes. */
    fun openExternal(context: Context, url: String, view: WebView?) {
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
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
          context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
          val fallback = intent.getStringExtra("browser_fallback_url")
          if (fallback != null && fallback.startsWith("https://")) view?.loadUrl(fallback)
          else intent.`package`?.let { pkg ->
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              })
            }
          }
        }
      } catch (_: Exception) {
        // Malformed or unsupported link: ignore.
      }
    }
  }
}
