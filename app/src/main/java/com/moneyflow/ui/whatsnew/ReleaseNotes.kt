package com.moneyflow.ui.whatsnew

/** What changed in each version, newest first. Add an entry for every release. */
data class ReleaseNote(val version: String, val date: String, val items: List<String>)

object ReleaseNotes {
  val all = listOf(
    ReleaseNote(
      "2.0.0", "2026.11",
      listOf(
        "홈 화면 위젯: 오늘·이번 달 지출과 남은 예산을 보고 '+ 기록'으로 바로 입력해요 (앱 잠금 중에는 금액을 가려요)",
        "가족 합산: 설정 → 가족 합산 보기에서 가족의 백업 파일을 불러와 함께 쓴 돈을 봐요 (서버 없음, 보기 전용)",
        "엑셀·CSV 가져오기: 카드사·은행에서 받은 이용내역 파일로 예전 내역을 한 번에 넣어요 (은행 입금은 수입으로)",
        "태그: 메모에 #여행 처럼 붙이면 소비분석에서 태그별로 모아 봐요. 입력 화면에서 최근 태그를 눌러 붙일 수 있어요"
      )
    ),
    ReleaseNote(
      "1.6.0", "2026.11",
      listOf(
        "할부 나눠 기록: 지출 입력에서 할부 개월을 고르면 매달 지출로 나눠 기록해요. 설정에서 켜면 자동 기록·카드사 가져오기도 나눠요",
        "할부 내역을 지울 때 이 달 것만 또는 모두 지울 수 있어요. 할부 결제가 취소되면 나눠 기록한 내역이 모두 지워져요",
        "해외 결제 알림: 외화 결제 문자가 오면 '원화 금액을 입력해 주세요' 알림을 보내고, 누르면 가맹점·카드가 채워진 입력 화면이 열려요",
        "설정 → 자동 분류 규칙: '스타벅스 → 카페/간식'처럼 규칙을 정하거나, 기록하지 않을 알림을 정할 수 있어요. 앱이 기억한 분류도 볼 수 있어요"
      )
    ),
    ReleaseNote(
      "1.5.0", "2026.11",
      listOf(
        "자동 백업: 설정 → 백업 및 복원에서 폴더(구글 드라이브 등)를 고르면 매주 또는 매일 백업 파일을 저장해요 (최근 5개 보관)",
        "예산 알림: 이번 달 예산의 80%, 100%를 넘거나 카테고리 예산을 넘으면 알려 드려요",
        "매월 1일 지난달 요약 알림 (지출·수입, 예산 대비, 가장 많이 쓴 곳)",
        "2주 넘게 백업하지 않았거나 자동 백업이 실패하면 알려 드려요",
        "앱을 켜 둔 채 달이 바뀌면 홈 화면이 지난달을 보여 주던 문제를 고쳤어요"
      )
    ),
    ReleaseNote(
      "1.4.0", "2026.11",
      listOf(
        "카드 혜택을 AI로 등록: 카드 계좌 → 카드 혜택 → 'AI로 등록'에 혜택 안내 글을 붙여 넣으면 할인율·한도·실적 조건을 한 번에 등록해요",
        "카드사에서 가져오기로 혜택 안내 화면을 'AI로 읽기'하면 혜택도 함께 등록할 수 있어요",
        "홈 화면 '어디서 어떤 카드?': 카페·편의점처럼 혜택이 있는 곳마다 가장 유리한 카드를 알려 줘요",
        "지출을 기록할 때 '○○카드로 내면 약 500원 혜택'처럼 유리한 카드를 알려 주고, 한 번에 바꿀 수 있어요",
        "실적 조건 없이 혜택만 등록한 카드도 받은 혜택을 계산해요"
      )
    ),
    ReleaseNote(
      "1.3.1", "2026.11",
      listOf(
        "업데이트 후 처음 실행하면 이렇게 바뀐 점을 알려 드려요",
        "설정 → 앱 정보 → 업데이트 내역에서 지난 내용도 볼 수 있어요"
      )
    ),
    ReleaseNote(
      "1.3.0", "2026.11",
      listOf(
        "카드 실적 관리: 카드 계좌에서 전월 실적 기준을 설정하면 이번 기간 실적과 남은 금액을 보여 줘요",
        "카드 혜택 관리: 카페 10% 할인처럼 혜택을 등록하면 받은 혜택과 남은 한도를 계산해요",
        "홈 화면에 카드별 실적 요약이 생겼어요",
        "설정 → 카드 실적·혜택 알림: 실적이 모자라거나 혜택 한도를 다 쓰면 알려 드려요"
      )
    ),
    ReleaseNote(
      "1.2.0", "2026.11",
      listOf(
        "설정 → 카드사에서 가져오기: 카드사 홈페이지에 직접 로그인해 이용내역·실적을 불러와요",
        "AI 서비스를 고를 수 있어요: Gemini, OpenRouter, Groq, 직접 입력",
        "자주 쓰는 가맹점은 AI 없이 바로 분류해서 무료 사용량을 아껴요"
      )
    ),
    ReleaseNote(
      "1.1.0", "2026.11",
      listOf("설정 → AI 설정: 내 API 키를 등록해 메모로 카테고리를 추천받을 수 있어요")
    ),
    ReleaseNote(
      "1.0.2", "2026.11",
      listOf(
        "은행 입금 문자에서 보낸 곳(회사명)과 은행을 정확히 인식해요",
        "같은 곳에서 온 입금은 지난번에 고른 카테고리로 자동 분류해요 (급여 등)"
      )
    ),
    ReleaseNote(
      "1.0.1", "2026.11",
      listOf("기본판(바로 설치)과 자동 등록판으로 나뉘었어요")
    ),
    ReleaseNote(
      "1.0.0", "2026.11",
      listOf("머니플로우 첫 버전: 가계부, 자산, 소비 분석, 예산·목표, 앱 잠금, 백업·복원")
    )
  )

  /** Notes newer than [lastSeen] up to [current]; only the current one if [lastSeen] is unknown. */
  fun since(lastSeen: String?, current: String): List<ReleaseNote> {
    val upToCurrent = all.filter { compare(it.version, current) <= 0 }
    if (lastSeen.isNullOrBlank()) return upToCurrent.take(1)
    return upToCurrent.filter { compare(it.version, lastSeen) > 0 }
  }

  /** Compares "1.10.2" style versions numerically. */
  fun compare(a: String, b: String): Int {
    val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
    val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
      val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
      if (d != 0) return d
    }
    return 0
  }
}
