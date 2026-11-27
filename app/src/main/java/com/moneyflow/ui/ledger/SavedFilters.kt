package com.moneyflow.ui.ledger

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.MonthKeys
import com.moneyflow.util.Tags
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** A filter the user saved under a name ("큰 지출", "카드 외식" …). Date range isn't saved except the preset. */
data class SavedFilter(
  val name: String,
  val type: TransactionType? = null,
  val category: String? = null,
  val query: String = "",
  val minAmount: Long? = null,
  val maxAmount: Long? = null,
  val accountIds: Set<Long> = emptySet(),
  val tags: Set<String> = emptySet(),
  val preset: DateRangePreset = DateRangePreset.THIS_MONTH
) {
  fun toFilters(isCalendarView: Boolean) = LedgerFilters(
    type = type, category = category, query = query, preset = preset,
    minAmount = minAmount, maxAmount = maxAmount, accountIds = accountIds, tags = tags, isCalendarView = isCalendarView
  )

  companion object {
    fun from(name: String, f: LedgerFilters) = SavedFilter(
      name.trim(), f.type, f.category, f.query.trim(), f.minAmount, f.maxAmount, f.accountIds, f.tags,
      if (f.preset == DateRangePreset.CUSTOM) DateRangePreset.THIS_MONTH else f.preset
    )
  }
}

object SavedFilters {
  const val MAX = 10

  fun encode(list: List<SavedFilter>): String = JSONArray(list.map { f ->
    JSONObject().put("name", f.name).put("type", f.type?.name ?: JSONObject.NULL).put("category", f.category ?: JSONObject.NULL)
      .put("query", f.query).put("min", f.minAmount ?: JSONObject.NULL).put("max", f.maxAmount ?: JSONObject.NULL)
      .put("accounts", JSONArray(f.accountIds.toList())).put("tags", JSONArray(f.tags.toList())).put("preset", f.preset.name)
  }).toString()

  fun decode(json: String?): List<SavedFilter> = runCatching {
    val a = JSONArray(json ?: return emptyList())
    (0 until a.length()).map { i ->
      val o = a.getJSONObject(i)
      SavedFilter(
        name = o.getString("name"),
        type = o.optString("type").takeIf { !o.isNull("type") }?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
        category = if (o.isNull("category")) null else o.optString("category"),
        query = o.optString("query"),
        minAmount = if (o.isNull("min") || !o.has("min")) null else o.getLong("min"),
        maxAmount = if (o.isNull("max") || !o.has("max")) null else o.getLong("max"),
        accountIds = o.optJSONArray("accounts")?.let { arr -> (0 until arr.length()).map { arr.getLong(it) }.toSet() } ?: emptySet(),
        tags = o.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet(),
        preset = runCatching { DateRangePreset.valueOf(o.optString("preset")) }.getOrDefault(DateRangePreset.THIS_MONTH)
      )
    }
  }.getOrDefault(emptyList())

  /** Adds or replaces (same name) a filter, newest first. */
  fun upsert(list: List<SavedFilter>, f: SavedFilter): List<SavedFilter> =
    (listOf(f) + list.filter { it.name != f.name }).take(MAX)
}

/** The ledger's filter test for one transaction (pure). The date range is applied separately. */
object LedgerFilterMatch {
  fun matches(tx: TransactionEntity, f: LedgerFilters, zone: ZoneId): Boolean {
    val q = f.query.trim()
    val txTags = if (f.tags.isEmpty()) emptyList() else Tags.extract(tx.memo)
    return (f.type == null || tx.type == f.type) &&
      (f.category == null || tx.category == f.category) &&
      (f.selectedDate == null || MonthKeys.toLocalDate(tx.timestamp, zone) == f.selectedDate) &&
      (f.minAmount == null || tx.amount >= f.minAmount) &&
      (f.maxAmount == null || tx.amount <= f.maxAmount) &&
      (f.accountIds.isEmpty() || tx.accountId in f.accountIds || tx.toAccountId in f.accountIds) &&
      (f.tags.isEmpty() || f.tags.all { it in txTags }) &&
      (q.isEmpty() || tx.memo.contains(q, true) || tx.category.contains(q, true) ||
        tx.accountName.contains(q, true) || (tx.toAccountName?.contains(q, true) == true) ||
        tx.paymentMethod.contains(q, true) || tx.amount.toString().contains(q.replace(",", "")))
  }
}
