package com.moneyflow.domain.cardimport

import org.json.JSONArray
import org.json.JSONObject

/** A card company page the user asked the app to remember ("이 화면 기억하기"). */
data class SavedPage(val url: String, val title: String, val savedAt: Long)

/** Remembered pages per card site, and the merging of what was read from them (pure, testable). */
object SavedPages {
  const val MAX_PER_SITE = 6

  private val SESSION_PATH = Regex(";jsessionid=[^?#]*", RegexOption.IGNORE_CASE)
  private val SESSION_KEY = Regex("^(jsessionid|sessionid|session|sid|token|access_?token|auth|nonce|ticket|otp|_csrf|csrf.*|t|ts|timestamp|_)$", RegexOption.IGNORE_CASE)

  /** The page address without session ids / one-time tokens (they must not be stored and expire anyway). */
  fun sanitize(url: String): String {
    val noSession = SESSION_PATH.replace(url, "")
    val hashIdx = noSession.indexOf('#')
    val beforeHash = if (hashIdx >= 0) noSession.substring(0, hashIdx) else noSession
    val hash = if (hashIdx >= 0) noSession.substring(hashIdx) else ""
    val q = beforeHash.indexOf('?')
    if (q < 0) return beforeHash + hash
    val kept = beforeHash.substring(q + 1).split('&').filter { p ->
      val key = p.substringBefore('=')
      p.isNotEmpty() && !SESSION_KEY.matches(key)
    }
    return beforeHash.substring(0, q) + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + hash
  }

  /** Why a page can't be remembered, or null when it can. */
  fun problem(url: String?, siteHome: String, existing: List<SavedPage>): String? {
    if (url == null || !url.startsWith("https://")) return "이 화면은 주소가 없어서 기억할 수 없어요."
    val clean = sanitize(url)
    if (clean.trimEnd('/') == siteHome.trimEnd('/')) return "카드사 첫 화면이에요. 이용내역·실적·혜택 화면을 연 뒤 눌러 주세요."
    if (existing.any { it.url == clean }) return "이미 기억한 화면이에요."
    if (existing.size >= MAX_PER_SITE) return "카드사마다 화면은 ${MAX_PER_SITE}개까지 기억할 수 있어요. 안 쓰는 화면을 지운 뒤 다시 눌러 주세요."
    return null
  }

  fun encode(map: Map<String, List<SavedPage>>): String = JSONObject().apply {
    map.filterValues { it.isNotEmpty() }.forEach { (site, pages) ->
      put(site, JSONArray(pages.map { JSONObject().put("url", it.url).put("title", it.title).put("at", it.savedAt) }))
    }
  }.toString()

  fun decode(raw: String?): Map<String, List<SavedPage>> = runCatching {
    val o = JSONObject(raw ?: return emptyMap())
    o.keys().asSequence().associateWith { site ->
      val arr = o.getJSONArray(site)
      (0 until arr.length()).map { arr.getJSONObject(it) }.map { SavedPage(it.getString("url"), it.optString("title"), it.optLong("at")) }
    }
  }.getOrDefault(emptyMap())

  /**
   * Combines readings of several pages. The same payment shown on two pages (e.g. "최근이용내역" and
   * "이용내역 조회") is kept once; performance figures and benefits are de-duplicated too.
   */
  fun merge(readings: List<StatementExtraction>, readBy: String): StatementExtraction {
    val items = readings.flatMap { it.items }
      .distinctBy { listOf(it.date, it.amount, it.isCancel, it.isIncome, it.merchant.replace(" ", "").lowercase(), it.time) }
    return StatementExtraction(
      items = items.sortedByDescending { it.date },
      performance = readings.flatMap { it.performance }.distinct(),
      readBy = readBy,
      benefits = readings.flatMap { it.benefits }.distinctBy { it.title.replace(" ", "") },
      bill = readings.firstNotNullOfOrNull { it.bill }
    )
  }
}
