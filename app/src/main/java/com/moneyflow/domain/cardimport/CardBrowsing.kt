package com.moneyflow.domain.cardimport

import org.json.JSONArray

/** Pure helpers for the in-app card browser (kept apart from the WebView so they can be tested). */
object CardBrowsing {

  /** Collects visible text of the page and its same-origin frames. */
  const val READ_TEXT_JS = """
  (function(){
    function t(d){ try{ var s = d.body ? d.body.innerText : '';
      var f = d.querySelectorAll('iframe,frame');
      for (var i=0;i<f.length;i++){ try{ s += '\n' + t(f[i].contentDocument); }catch(e){} }
      return s; }catch(e){ return ''; } }
    return t(document);
  })()
  """

  /** Page text plus whether a login form is showing (the session ended or the page needs login). */
  const val READ_PAGE_JS = """
  (function(){
    function t(d){ try{ var s = d.body ? d.body.innerText : '';
      var f = d.querySelectorAll('iframe,frame');
      for (var i=0;i<f.length;i++){ try{ s += '\n' + t(f[i].contentDocument); }catch(e){} }
      return s; }catch(e){ return ''; } }
    function pw(d){ try{ var p = d.querySelectorAll('input[type=password]');
      for (var i=0;i<p.length;i++){ if (p[i].offsetParent !== null) return true; }
      var f = d.querySelectorAll('iframe,frame');
      for (var j=0;j<f.length;j++){ try{ if (pw(f[j].contentDocument)) return true; }catch(e){} }
      return false; }catch(e){ return false; } }
    return JSON.stringify({ t: t(document), login: pw(document) });
  })()
  """

  /** A desktop Chrome user agent with the same Chrome version as the phone's WebView. */
  fun desktopUserAgent(mobileUserAgent: String): String {
    val chrome = Regex("Chrome/[\\d.]+").find(mobileUserAgent)?.value ?: "Chrome/131.0.0.0"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
  }

  /** The regular phone browser: the embedded-browser mark "; wv" removed (some sites refuse WebViews). */
  fun mobileUserAgent(defaultUserAgent: String): String = defaultUserAgent.replace("; wv", "")

  enum class LinkAction { LOAD_IN_PAGE, OPEN_APP, BLOCK }

  /**
   * What to do with a link the card site wants to open. Web pages stay in the browser. Another
   * app (card app approval, 간편인증) opens when the user tapped something; a page that switches
   * to its app by itself is stopped, so the user can keep using the web page.
   */
  fun linkAction(url: String, userTapped: Boolean): LinkAction {
    val lower = url.lowercase()
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
    // App-install / deep-link redirectors (롯데카드 opens its app through onelink.me) count as apps.
    val store = lower.startsWith("market:") || host == "play.google.com" ||
      APP_LINK_HOSTS.any { host == it || host.endsWith(".$it") }
    if ((lower.startsWith("https://") || lower.startsWith("http://")) && !store) return LinkAction.LOAD_IN_PAGE
    if (lower.startsWith("javascript:") || lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("file:") || lower.startsWith("content:")) return LinkAction.BLOCK
    return if (userTapped) LinkAction.OPEN_APP else LinkAction.BLOCK
  }

  private val APP_LINK_HOSTS = listOf("onelink.me", "app.link", "page.link", "adj.st", "app.adjust.com")

  /** Which kind of app link this is ("hanacard", "onelink.me" …): a declined kind is then stopped silently. */
  fun appLinkKind(url: String): String {
    val lower = url.lowercase()
    if (lower.startsWith("http")) return runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
    if (lower.startsWith("intent:")) Regex("scheme=([^;]+)").find(lower)?.let { return it.groupValues[1] }
    return lower.substringBefore(':')
  }

  /** Known pages first (they always work), then menu links found on the page. */
  fun withKnownPages(known: List<Pair<String, String>>, found: List<FoundLink>): List<FoundLink> =
    (known.map { (title, url) -> FoundLink("📌 $title", url, "") } + found).distinctBy { it.url.ifEmpty { it.id } }

  /**
   * Hides full-screen layers that block every touch (a dimmed background left behind by a
   * notice pop-up, an app-install banner) and lets the page scroll again. Returns how many.
   */
  const val REMOVE_OVERLAYS_JS = """
(function(){
  var n=0;
  function run(d){ try{
    var w=d.defaultView, vw=w.innerWidth, vh=w.innerHeight;
    var els=d.querySelectorAll('body *');
    for(var i=0;i<els.length;i++){ var e=els[i]; var cs=w.getComputedStyle(e);
      if(cs.position!='fixed' && cs.position!='absolute') continue;
      if(cs.display=='none' || cs.visibility=='hidden') continue;
      var r=e.getBoundingClientRect(); if(r.width<vw*0.9 || r.height<vh*0.9) continue;
      if(e.querySelector('input[type=password]')) continue;
      var txt=(e.innerText||'').replace(/\s+/g,'');
      var name=((e.className&&e.className.toString())||'')+' '+(e.id||'');
      if(txt.length<2 || /dim|mask|overlay|backdrop|layer_?bg|blockUI|modal-bg/i.test(name)){
        e.style.setProperty('display','none','important'); n++; }
    }
    if(d.body) d.body.style.setProperty('overflow','auto','important');
    d.documentElement.style.setProperty('overflow','auto','important');
    var f=d.querySelectorAll('iframe,frame');
    for(var j=0;j<f.length;j++){ try{ run(f[j].contentDocument); }catch(x){} }
  }catch(x){} }
  run(document);
  return ''+n;
})()
"""

  /**
   * Installed on every page: a fixed bottom sheet whose top is on screen but most of it below the
   * bottom edge (it opened too low: only its title shows) is moved up and made scrollable.
   * Checked again after taps, since sheets open on a tap.
   */
  const val LIFT_BOTTOM_SHEETS_JS = """
(function(){
  if(window.__mfLift) return; window.__mfLift=1;
  function lift(){ try{
    var vh=window.innerHeight, els=document.querySelectorAll('body *');
    for(var i=0;i<els.length;i++){ var e=els[i];
      var cs=getComputedStyle(e); if(cs.position!='fixed') continue;
      if(cs.display=='none'||cs.visibility=='hidden') continue;
      var r=e.getBoundingClientRect();
      if(r.height<120 || r.top<0 || r.top>=vh-8) continue;
      var shown=vh-r.top; if(shown>=r.height*0.6) continue;
      e.style.setProperty('top','auto','important');
      e.style.setProperty('bottom','0','important');
      e.style.setProperty('transform','none','important');
      e.style.setProperty('max-height',Math.round(vh*0.85)+'px','important');
      e.style.setProperty('overflow-y','auto','important');
    }
  }catch(x){} }
  document.addEventListener('click',function(){ setTimeout(lift,400); setTimeout(lift,1200); },true);
  setTimeout(lift,800);
})()
"""

  private val CODE_WORD = Regex("코드|인증\\s*번호|숫자|번호")
  private val CODE = Regex("(?<![\\d\\-.,:])(\\d{3}\\s?\\d{4}|\\d(?:\\s\\d){6})(?![\\d\\-.,:]|\\s\\d)")
  private val NOT_CODE = Regex("고객센터|전화|상담|☎|TEL|Tel|문의")

  /**
   * The 7-digit login code a card site shows for its app ("현대카드 앱에 아래 숫자를 입력하세요
   * 123 4567"), or null. Only near words like 코드 / 인증번호, and not phone numbers.
   */
  fun findAppCode(text: String): String? {
    if (!text.contains("앱")) return null
    for (kw in CODE_WORD.findAll(text)) {
      val from = (kw.range.first - 20).coerceAtLeast(0)
      val to = (kw.range.last + 80).coerceAtMost(text.length)
      val window = text.substring(from, to)
      if (NOT_CODE.containsMatchIn(window)) continue
      CODE.find(window)?.let { return it.value.filter(Char::isDigit) }
    }
    return null
  }

  /** "browser_fallback_url" of an intent:// link (the web page to show instead of the app), if it is https. */
  fun fallbackUrl(url: String): String? {
    if (!url.startsWith("intent:")) return null
    val m = Regex("S\\.browser_fallback_url=([^;]+)").find(url) ?: return null
    val decoded = runCatching { java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull() ?: return null
    return decoded.takeIf { it.startsWith("https://") }
  }

  /** A menu link on the card site that leads to the data we need. */
  data class FoundLink(val text: String, val url: String, val id: String)

  private val LINK_WORDS = listOf(
    Regex("(이용|승인|사용|결제)\\s*내역|(주문|구매)\\s*(내역|목록|배송)") to 0,
    Regex("명세서|청구\\s*(내역|금액|서)") to 1,
    Regex("이용\\s*대금|결제\\s*(예정|금액)|결제하실\\s*금액") to 2,
    Regex("실적") to 3,
    Regex("포인트|마일리지|하나머니|포인트리|캐시백\\s*(조회|내역)") to 4
  )

  /** Rank of a link text (lower first), or null when it isn't about card usage. */
  fun linkRank(text: String): Int? {
    val t = text.replace(Regex("\\s+"), " ").trim()
    if (t.isEmpty() || t.length > 45) return null
    // Ads, settings and help pages share these words but don't show the user's data.
    if (Regex("안내|신청|설정|변경|해지|이벤트|혜택\\s*안내|자주|FAQ|문의|약관|해외이용|발급|리볼빙|이월|약정|대출|카드론|현금\\s*서비스|비교|추천|[0-9]\\s*종").containsMatchIn(t)) return null
    return LINK_WORDS.firstOrNull { it.first.containsMatchIn(t) }?.second
  }

  /** Parses the result of [FIND_LINKS_JS]: useful links, best first, without duplicates. */
  fun parseFoundLinks(json: String): List<FoundLink> {
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    val all = (0 until arr.length()).mapNotNull { i ->
      val o = arr.optJSONObject(i) ?: return@mapNotNull null
      val text = o.optString("t").replace(Regex("\\s+"), " ").trim()
      val rank = linkRank(text) ?: return@mapNotNull null
      Triple(rank, i, FoundLink(text, o.optString("u"), o.optString("id")))
    }
    return all.sortedWith(compareBy({ it.first }, { it.second }))
      .map { it.third }
      .distinctBy { it.text.replace(" ", "") + "|" + it.url }
      .take(12)
  }

  /**
   * Marks clickable elements whose text is about card usage (also in frames and closed menus)
   * and returns them as JSON [{t, u, id}]; [CLICK_FOUND_JS] then clicks one by its id.
   */
  const val FIND_LINKS_JS = """
(function(){
  var re=/(이용|승인|사용|결제|주문|구매)\s*(내역|목록|배송)|명세서|청구|이용\s*대금|결제\s*(예정|금액)|결제하실|실적/;
  var out=[], n=0;
  function scan(d){ try{
    var els=d.querySelectorAll('a,button,[onclick],[role=button],[role=menuitem],[role=tab]');
    for(var i=0;i<els.length && out.length<60;i++){ var e=els[i];
      var t=(e.textContent||'').replace(/\s+/g,' ').trim();
      if(!t||t.length>30) continue;
      if(!re.test(t)){
        // "개인고객 바로가기" under an "이용내역조회" heading: name the button after its section.
        if(!/바로\s*가기|조회|보기|확인|go/i.test(t)) continue;
        var h='', p=e.parentElement;
        for(var k=0;k<4 && p && !h;k++,p=p.parentElement){
          var hs=p.querySelectorAll('h1,h2,h3,h4,h5,strong,dt,[class*=tit]');
          for(var q=0;q<hs.length;q++){ var ht=(hs[q].textContent||'').replace(/\s+/g,' ').trim(); if(ht.length<=20 && re.test(ht)){ h=ht; break; } }
        }
        if(!h) continue;
        t=h+' · '+t;
      }
      var h=e.getAttribute('href')||'', u='';
      try{ if(h && !/^(javascript:|#)/i.test(h)) u=new URL(h,d.baseURI).href; }catch(x){}
      var id='mf'+(n++); e.setAttribute('data-mf-find',id);
      out.push({t:t,u:u,id:id});
    }
    var f=d.querySelectorAll('iframe,frame');
    for(var j=0;j<f.length;j++){ try{ scan(f[j].contentDocument); }catch(x){} }
  }catch(x){} }
  scan(document);
  return JSON.stringify(out);
})()
"""

  /** Clicks the element marked by [FIND_LINKS_JS] ('%s' = its id); returns "ok" or "". */
  fun clickFoundJs(id: String): String {
    val safe = id.filter { it.isLetterOrDigit() }
    return """
(function(){
  function find(d){ try{ var e=d.querySelector('[data-mf-find="$safe"]'); if(e) return e;
    var f=d.querySelectorAll('iframe,frame');
    for(var j=0;j<f.length;j++){ try{ var r=find(f[j].contentDocument); if(r) return r; }catch(x){} }
  }catch(x){} return null; }
  var e=find(document); if(!e) return '';
  try{ e.scrollIntoView({block:'center'}); }catch(x){}
  e.click(); return 'ok';
})()
"""
  }

  /** File name from a Content-Disposition header ("attachment; filename*=UTF-8''..."), else from the URL. */
  fun downloadFileName(contentDisposition: String?, url: String, mimeType: String?): String {
    val cd = contentDisposition.orEmpty()
    val star = Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
    val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
    val raw = star ?: plain ?: url.substringBefore('?').substringAfterLast('/')
    val name = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).trim().ifEmpty { "카드내역" }
    if (name.contains('.')) return name
    return name + when {
      mimeType.orEmpty().contains("spreadsheetml") -> ".xlsx"
      mimeType.orEmpty().contains("excel") -> ".xls"
      mimeType.orEmpty().contains("csv") -> ".csv"
      else -> ""
    }
  }
}
