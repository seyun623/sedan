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

/** Orders read from a shopping site, handed to the order-link screen (memory only). */
@Singleton
class OrderSession @Inject constructor() {
  private val _current = MutableStateFlow<Pair<CardSite, List<com.moneyflow.domain.shop.ShopOrder>>?>(null)
  val current: StateFlow<Pair<CardSite, List<com.moneyflow.domain.shop.ShopOrder>>?> = _current.asStateFlow()

  fun put(site: CardSite, orders: List<com.moneyflow.domain.shop.ShopOrder>) { _current.value = site to orders }
  fun clear() { _current.value = null }
}
