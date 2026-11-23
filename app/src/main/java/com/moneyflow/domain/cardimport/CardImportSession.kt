package com.moneyflow.domain.cardimport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Hands the latest screen reading from the card browser to the preview screen (memory only). */
@Singleton
class CardImportSession @Inject constructor() {
  private val _current = MutableStateFlow<Pair<CardSite, StatementExtraction>?>(null)
  val current: StateFlow<Pair<CardSite, StatementExtraction>?> = _current.asStateFlow()

  fun put(site: CardSite, extraction: StatementExtraction) { _current.value = site to extraction }
  fun clear() { _current.value = null }
}
