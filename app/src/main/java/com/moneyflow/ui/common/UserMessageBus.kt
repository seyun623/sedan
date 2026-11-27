package com.moneyflow.ui.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A snackbar message, optionally with an action (e.g. "실행 취소"). */
data class UserMessage(
  val text: String,
  val actionLabel: String? = null,
  val onAction: (suspend () -> Unit)? = null
)

/** App-wide snackbar channel shared by all view models. */
@Singleton
class UserMessageBus @Inject constructor() {
  private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

  fun show(text: String) { _messages.tryEmit(UserMessage(text)) }
  fun show(message: UserMessage) { _messages.tryEmit(message) }
}
