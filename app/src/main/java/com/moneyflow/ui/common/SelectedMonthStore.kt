package com.moneyflow.ui.common

import com.moneyflow.domain.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The month shown on the ledger / analytics / budget tabs (shared so tabs stay in sync). */
@Singleton
class SelectedMonthStore @Inject constructor(private val clock: AppClock) {
  private val _month = MutableStateFlow(clock.currentMonth())
  val month: StateFlow<YearMonth> = _month.asStateFlow()

  fun next() { _month.value = _month.value.plusMonths(1) }
  fun previous() { _month.value = _month.value.minusMonths(1) }
  fun set(month: YearMonth) { _month.value = month }
  fun resetToCurrent() { _month.value = clock.currentMonth() }
}
