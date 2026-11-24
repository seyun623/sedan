package com.moneyflow.ui

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.ledger.DateRangePreset
import com.moneyflow.ui.ledger.LedgerFilterMatch
import com.moneyflow.ui.ledger.LedgerFilters
import com.moneyflow.ui.ledger.SavedFilter
import com.moneyflow.ui.ledger.SavedFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class V28FiltersTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private fun tx(amount: Long, account: Long, memo: String) =
    TransactionEntity(type = TransactionType.EXPENSE, amount = amount, category = "식비", accountId = account, timestamp = 0, memo = memo)

  @Test fun detailConditions() {
    val dinner = tx(80_000, 2, "고깃집 #외식 #가족")
    val coffee = tx(5_000, 1, "커피 #외식")
    val f = LedgerFilters(minAmount = 10_000, accountIds = setOf(2), tags = setOf("외식", "가족"))
    assertTrue(LedgerFilterMatch.matches(dinner, f, zone))
    assertFalse(LedgerFilterMatch.matches(coffee, f, zone))
    assertFalse(LedgerFilterMatch.matches(dinner, f.copy(maxAmount = 50_000), zone))
    assertTrue(LedgerFilterMatch.matches(coffee, LedgerFilters(tags = setOf("외식"), query = "커피"), zone))
    assertEquals(3, f.detailCount)
    assertTrue(f.isAnyActive)
  }

  @Test fun savedFiltersRoundTrip() {
    val a = SavedFilter.from(" 큰 지출 ", LedgerFilters(type = TransactionType.EXPENSE, minAmount = 100_000, preset = DateRangePreset.CUSTOM, tags = setOf("여행")))
    assertEquals("큰 지출", a.name)
    assertEquals(DateRangePreset.THIS_MONTH, a.preset) // a custom date range isn't kept
    val b = SavedFilter("카드", accountIds = setOf(3L, 4L))
    val list = SavedFilters.upsert(SavedFilters.upsert(emptyList(), a), b)
    val back = SavedFilters.decode(SavedFilters.encode(list))
    assertEquals(list, back)
    assertEquals(listOf("큰 지출", "카드"), SavedFilters.upsert(back, a.copy(maxAmount = 1)).map { it.name })
    assertEquals(emptyList<SavedFilter>(), SavedFilters.decode("not json"))
    assertEquals(100_000L, back.first { it.name == "큰 지출" }.toFilters(true).minAmount)
  }
}
