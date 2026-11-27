package com.moneyflow.ui.common

import com.moneyflow.domain.capture.FormPrefill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An add-form request from outside the app (notification, widget). The nav host opens the form
 * (after unlocking) and the form takes the values once.
 */
@Singleton
class PrefillStore @Inject constructor() {
  private val _pending = MutableStateFlow<FormPrefill?>(null)
  /** Set until the nav host has opened the form. */
  val pending: StateFlow<FormPrefill?> = _pending.asStateFlow()
  private var forForm: FormPrefill? = null

  fun request(p: FormPrefill) { _pending.value = p }

  /** Called by the nav host when it navigates to the form. */
  fun opened() {
    forForm = _pending.value
    _pending.value = null
  }

  /** Called once by the form. */
  fun take(): FormPrefill? = forForm.also { forForm = null }
}
