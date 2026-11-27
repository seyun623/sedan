package com.moneyflow.domain.report

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import kotlin.math.min
import kotlin.math.roundToLong

/** Kinds of spending for the 신용카드 등 소득공제 (each has its own rate). */
enum class DeductionKind(val label: String, val rate: Double) {
  CREDIT("신용카드", 0.15),
  DEBIT("체크카드·현금영수증·지역화폐", 0.30),
  MARKET("전통시장 (온누리 포함)", 0.40),
  TRANSIT("대중교통", 0.40)
}

/**
 * 연말정산 신용카드 등 소득공제 미리보기 (pure, testable). An estimate from the ledger — the real
 * numbers come from 국세청 연말정산 간소화.
 *
 * Rules used (2024~ 기준): spending above 25% of 총급여 counts; the first 25% is taken from credit
 * card spending first (the lowest rate). Rates 15% / 30% / 40% / 40%. Basic limit 300만 원
 * (총급여 7천만 원 초과 250만 원), plus up to 300만 원 (초과 200만 원) more for 전통시장·대중교통.
 */
object CardDeduction {

  data class Result(
    val salary: Long,
    val threshold: Long,
    val byKind: Map<DeductionKind, Long>,
    val total: Long,
    /** Spending still needed to pass the 25% threshold (0 when passed). */
    val toThreshold: Long,
    val deduction: Long,
    /** The year so far scaled to 12 months (for "이 속도면"). */
    val projectedDeduction: Long
  )

  private val EXCLUDED = Regex("보험|국세|지방세|세금|관리비|공과금|상품권\\s*구입|등록금|수업료|현금서비스|카드론|해외|기부|헌금|헌혈")
  private val MARKET_WORD = Regex("전통시장|재래시장|[가-힣]{1,6}시장(?!\\s*마트)")
  private val TRANSIT_WORD = Regex("후불교통|교통카드|티머니|캐시비|이비카드|레일플러스|기후동행|K-?패스|알뜰교통|지하철|버스|코레일|SRT|KTX|공항철도")

  /** Which kind a payment counts as, or null when it doesn't count at all. */
  fun kindOf(tx: TransactionEntity, account: AccountEntity?): DeductionKind? {
    if (tx.type != TransactionType.EXPENSE && tx.type != TransactionType.REFUND) return null
    val text = "${tx.memo} ${tx.category}"
    if (EXCLUDED.containsMatchIn(text)) return null
    if (tx.paymentMethod == "온누리상품권" || MARKET_WORD.containsMatchIn(tx.memo)) return DeductionKind.MARKET
    if (TRANSIT_WORD.containsMatchIn(tx.memo) && !tx.memo.contains("택시")) return DeductionKind.TRANSIT
    val method = tx.paymentMethod
    return when {
      method == "체크카드" || method == "지역화폐" || method == "현금" || method == "계좌이체" -> DeductionKind.DEBIT
      method == "신용카드" -> DeductionKind.CREDIT
      account?.accountType == AccountType.CREDIT_CARD && account.name.contains("체크") -> DeductionKind.DEBIT
      account?.accountType == AccountType.CREDIT_CARD -> DeductionKind.CREDIT
      method == "간편결제" || method == "카드" -> DeductionKind.CREDIT
      else -> null
    }
  }

  /**
   * @param months how many months of the year [txs] cover (for the projection), 1..12
   */
  fun estimate(salary: Long, txs: List<TransactionEntity>, accounts: List<AccountEntity>, months: Int): Result {
    val byId = accounts.associateBy { it.id }
    val sums = DeductionKind.entries.associateWith { 0L }.toMutableMap()
    for (tx in txs) {
      val kind = kindOf(tx, byId[tx.accountId]) ?: continue
      sums[kind] = sums.getValue(kind) + if (tx.type == TransactionType.REFUND) -tx.amount else tx.amount
    }
    val byKind = sums.mapValues { it.value.coerceAtLeast(0) }
    val threshold = salary / 4
    val total = byKind.values.sum()
    val deduction = deduction(salary, threshold, byKind)
    val scale = 12.0 / months.coerceIn(1, 12)
    val projected = deduction(salary, threshold, byKind.mapValues { (it.value * scale).roundToLong() })
    return Result(salary, threshold, byKind, total, (threshold - total).coerceAtLeast(0), deduction, projected)
  }

  private fun deduction(salary: Long, threshold: Long, byKind: Map<DeductionKind, Long>): Long {
    // The 25% threshold uses up credit card spending first, then the others.
    var remaining = threshold
    val over = DeductionKind.entries.associateWith { kind ->
      val amount = byKind[kind] ?: 0
      val used = min(remaining, amount)
      remaining -= used
      ((amount - used) * kind.rate).roundToLong()
    }
    val base = over.values.sum()
    val high = salary > 70_000_000
    val basicLimit = if (high) 2_500_000L else 3_000_000L
    val extraLimit = if (high) 2_000_000L else 3_000_000L
    val extraPart = (over[DeductionKind.MARKET] ?: 0) + (over[DeductionKind.TRANSIT] ?: 0)
    val extra = min(min((base - basicLimit).coerceAtLeast(0), extraPart), extraLimit)
    return min(base, basicLimit) + extra
  }

  /** One line of advice from where the year stands. */
  fun advice(r: Result): String = when {
    r.salary <= 0 -> "총급여(세전 연봉)를 넣으면 계산해요."
    r.toThreshold > 0 -> "총급여의 25%(${won(r.threshold)})를 넘기 전 사용분은 공제되지 않아요. ${won(r.toThreshold)} 더 쓸 때까지는 혜택 좋은 신용카드가 유리해요."
    else -> "25% 문턱을 넘었어요. 지금부터는 공제율이 2배인 체크카드·현금영수증(30%), 전통시장·대중교통(40%)이 유리해요."
  }

  private fun won(v: Long) = com.moneyflow.util.Formatters.formatCurrency(v)
}
