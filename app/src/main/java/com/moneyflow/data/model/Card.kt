package com.moneyflow.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spending-goal ("실적") settings of a card account. */
@Entity(tableName = "card_profiles")
data class CardProfileEntity(
  @PrimaryKey val accountId: Long,
  /** Monthly 실적 needed (원); 0 = not tracked. */
  val performanceTarget: Long,
  /** First day of the 실적 period (1 = calendar month, otherwise e.g. 13 → 13th..12th). */
  val cycleStartDay: Int = 1,
  /** Categories that don't count toward 실적, comma separated. */
  val excludedCategories: String = "",
  /** Memo keywords that don't count toward 실적 (관리비, 세금, 상품권 …), comma separated. */
  val excludedKeywords: String = ""
)

enum class BenefitKind(val label: String) { PERCENT("% 할인·적립"), FIXED("건당 금액") }

/** A card benefit, e.g. "카페 10% 할인, 월 1만원 한도, 전월 실적 30만원 이상". */
@Entity(tableName = "card_benefits", indices = [Index("accountId")])
data class CardBenefitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val accountId: Long,
  val title: String,
  /** Applies to this expense category (null = any category). */
  val category: String? = null,
  /** Applies when the memo contains one of these (comma separated); empty = not used. */
  val merchantKeywords: String = "",
  val kind: BenefitKind = BenefitKind.PERCENT,
  /** PERCENT: percent × 100 (10% = 1000). FIXED: 원 per payment. */
  val value: Long,
  /** Monthly benefit cap (원); 0 = no cap. */
  val monthlyCap: Long = 0,
  /** 전월 실적 needed for the benefit (원); 0 = none. */
  val minPerformance: Long = 0,
  /** Minimum payment amount (원); 0 = none. */
  val minAmount: Long = 0,
  val isActive: Boolean = true
)
