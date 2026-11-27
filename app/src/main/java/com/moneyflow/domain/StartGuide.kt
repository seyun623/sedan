package com.moneyflow.domain

/** One step of the home "시작하기" checklist (3.0). */
data class StartStep(val id: String, val title: String, val hint: String, val done: Boolean)

/** What a new family member should set up first, and what is already done (pure, testable). */
object StartGuide {
  fun steps(
    hasAccounts: Boolean,
    autoCaptureAvailable: Boolean,
    hasAutoRecords: Boolean,
    budgetSet: Boolean,
    familyOn: Boolean,
    backupOn: Boolean
  ): List<StartStep> = listOfNotNull(
    StartStep("account", "계좌·카드 등록", "자산 탭에서 통장·카드·현금을 추가해요", hasAccounts),
    if (autoCaptureAvailable) StartStep("capture", "결제 알림 자동 등록 켜기", "카드·은행 알림으로 지출이 저절로 기록돼요", hasAutoRecords) else null,
    StartStep("budget", "이번 달 예산 정하기", "예산 탭에서 한 달에 쓸 돈을 정해요", budgetSet),
    StartStep("family", "가족 자동 합산 연결", "설정 → 가족 자동 합산 설정 (구글 드라이브)", familyOn),
    StartStep("backup", "백업하기", "설정 → 백업 및 복원에서 자동 백업을 켜요", backupOn)
  )

  /** Shown until every step is done or the card is closed. */
  fun visible(steps: List<StartStep>, dismissed: Boolean, forced: Boolean = false) = !dismissed && (forced || steps.any { !it.done })
}
