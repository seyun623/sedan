package com.moneyflow.ui.cardimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.cardimport.OrderSession
import com.moneyflow.domain.shop.OrderMatch
import com.moneyflow.domain.shop.OrderMatcher
import com.moneyflow.domain.shop.ShopOrder
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderLinkState(
  val shopName: String = "",
  val matches: List<OrderMatch> = emptyList(),
  val selected: Set<Int> = emptySet(),
  /** Orders with no payment of the same amount (paid another way, or not recorded yet). */
  val unmatched: List<ShopOrder> = emptyList(),
  /** Nothing to show (the app was closed in between): read the order list again. */
  val noOrders: Boolean = false,
  val loading: Boolean = true,
  val busy: Boolean = false
)

@HiltViewModel
class OrderLinkViewModel @Inject constructor(
  private val session: OrderSession,
  private val db: AppDatabase,
  private val ledger: LedgerRepository,
  private val clock: AppClock,
  private val messages: UserMessageBus
) : ViewModel() {
  private val _state = MutableStateFlow(OrderLinkState())
  val state: StateFlow<OrderLinkState> = _state.asStateFlow()
  private val _done = Channel<Unit>(Channel.BUFFERED)
  val done = _done.receiveAsFlow()

  init {
    viewModelScope.launch {
      val (site, orders) = session.current.value?.takeIf { it.second.isNotEmpty() }
        ?: run { _state.value = OrderLinkState(loading = false, noOrders = true); return@launch }
      val from = MonthKeys.dayStartMillis(orders.minOf { it.date }, clock.zone)
      val to = MonthKeys.dayStartMillis(orders.maxOf { it.date }.plusDays(5), clock.zone)
      val payments = db.transactionDao().getBetween(from, to)
      val matches = OrderMatcher.match(orders, payments, clock.zone)
      val matched = matches.map { it.order }.toSet()
      _state.value = OrderLinkState(
        shopName = site.name,
        matches = matches,
        // Payments that don't name the shop are suggestions only: the user ticks them.
        selected = matches.indices.filter { matches[it].namesShop }.toSet(),
        unmatched = orders.filter { it !in matched },
        loading = false
      )
    }
  }

  fun toggle(i: Int) {
    val s = _state.value
    _state.value = s.copy(selected = if (i in s.selected) s.selected - i else s.selected + i)
  }

  fun apply() = viewModelScope.launch {
    val s = _state.value
    if (s.busy) return@launch
    _state.value = s.copy(busy = true)
    var changed = 0
    try {
      s.matches.filterIndexed { i, _ -> i in s.selected }.forEach { m ->
        // Only the memo changes (no balance effect); re-read in case it was edited meanwhile.
        val current = db.transactionDao().getById(m.transaction.id) ?: return@forEach
        if (current.memo == m.transaction.memo &&
          ledger.updateTransaction(current.copy(memo = m.newMemo)) == com.moneyflow.data.repository.LedgerResult.Ok
        ) changed++
      }
    } catch (e: Exception) {
      com.moneyflow.util.ProblemLog.record("쇼핑몰 주문 연결", e)
      messages.show(com.moneyflow.util.UserErrors.message(e, "일부 결제에 상품 이름을 붙이지 못했어요."))
    } finally {
      _state.value = _state.value.copy(busy = false)
    }
    session.clear()
    messages.show(if (changed > 0) "결제 ${changed}건에 상품 이름을 붙였어요." else "바뀐 내역이 없어요.")
    _done.send(Unit)
  }
}

/** Orders from a shopping site matched with ledger payments: pick which get the product names. */
@Composable
fun OrderLinkScreen(onBack: () -> Unit, viewModel: OrderLinkViewModel = hiltViewModel()) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }

  SettingsScaffold(title = if (s.shopName.isEmpty()) "주문 → 상품명 붙이기" else "${s.shopName} 주문 → 상품명 붙이기", onBack = onBack) {
    if (s.noOrders) {
      Text(
        "읽은 주문이 없어요. 쇼핑몰 주문내역 화면에서 '이 화면 가져오기'를 다시 눌러 주세요.",
        style = MaterialTheme.typography.bodyMedium, color = Gray600, modifier = Modifier.padding(16.dp)
      )
    } else if (!s.loading && s.matches.isEmpty()) {
      Text(
        "가계부에서 같은 금액의 결제를 찾지 못했어요. 카드 결제가 먼저 기록돼 있어야 해요 (결제 알림 자동 등록이나 카드사 가져오기). " +
          "주문일부터 3일 안의 같은 금액 결제를 찾아요.",
        style = MaterialTheme.typography.bodyMedium, color = Gray600,
        modifier = Modifier.padding(16.dp)
      )
    }
    if (s.matches.isNotEmpty()) {
      SettingsSection("찾은 결제 ${s.matches.size}건 · 선택 ${s.selected.size}건") {
        s.matches.forEachIndexed { i, m ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
              .toggleable(value = i in s.selected, role = Role.Checkbox) { viewModel.toggle(i) }
              .padding(horizontal = 8.dp, vertical = 4.dp).testTag("order_match_$i")
          ) {
            Checkbox(checked = i in s.selected, onCheckedChange = null)
            Column(Modifier.weight(1f)) {
              Text(m.newMemo, fontWeight = FontWeight.SemiBold, color = Gray900)
              if (!m.namesShop) Text("결제 이름에 ${s.shopName}이(가) 없어요. 맞으면 골라 주세요", style = MaterialTheme.typography.labelSmall, color = com.moneyflow.ui.theme.AccentRedText)
              Text(
                "${m.order.date.monthValue}.${m.order.date.dayOfMonth} 주문 · ${Formatters.formatCurrency(m.order.amount)} · 지금: ${m.transaction.memo}",
                style = MaterialTheme.typography.bodySmall, color = Gray600
              )
            }
          }
        }
      }
      Text(
        "가족 자동 합산을 쓰면 가족에게도 상품 이름이 보여요.",
        style = MaterialTheme.typography.labelSmall, color = Gray600, modifier = Modifier.padding(horizontal = 16.dp)
      )
      Button(
        onClick = viewModel::apply,
        enabled = s.selected.isNotEmpty() && !s.busy,
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("order_apply")
      ) { Text("상품 이름 붙이기 (${s.selected.size}건)", fontWeight = FontWeight.Bold) }
    }
    if (s.unmatched.isNotEmpty()) {
      SettingsSection("맞는 결제가 없는 주문 ${s.unmatched.size}건") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          s.unmatched.take(20).forEach { o ->
            Text(
              "${o.date.monthValue}.${o.date.dayOfMonth} · ${Formatters.formatCurrency(o.amount)} · ${o.summary.ifEmpty { "상품 이름 없음" }}",
              style = MaterialTheme.typography.bodySmall, color = Gray600
            )
          }
          Text("다른 수단으로 결제했거나, 아직 가계부에 없는 결제예요.", style = MaterialTheme.typography.labelSmall, color = Gray600)
        }
      }
    }
  }
}
