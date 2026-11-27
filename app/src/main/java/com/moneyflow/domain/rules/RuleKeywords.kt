package com.moneyflow.domain.rules

/**
 * A short keyword for a rule made from a full merchant name: rules match when the memo contains
 * the keyword, so "후라토식당 성수점" → "후라토식당" also covers the other branches.
 */
object RuleKeywords {
  private val COMPANY = Regex("\\(주\\)|㈜|\\(유\\)|주식회사|유한회사|\\(사\\)|사단법인")
  private val BRANCH = Regex("\\s+\\S{1,10}(점|지점|직영점|본점)$|(?<=\\S{2})\\s*\\S{0,6}(점|지점|직영점)$")
  private val TAIL = Regex("\\s+(누적|결제|승인|일시불|체크|신용)$")

  fun suggest(merchant: String): String {
    var s = merchant.replace(COMPANY, " ").replace(Regex("\\s+"), " ").trim()
    s = s.replace(TAIL, "").trim()
    val noBranch = s.replace(BRANCH, "").trim()
    if (noBranch.length >= 2) s = noBranch
    // Very long names: the first word is usually the brand.
    if (s.length > 12 && s.contains(' ')) s = s.substringBefore(' ').takeIf { it.length >= 2 } ?: s
    return s.ifEmpty { merchant.trim() }
  }
}
