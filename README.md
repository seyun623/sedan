# 머니플로우 (MoneyFlow)

가계부와 자산 관리를 한 곳에서 하는 Android 앱입니다. Kotlin, Jetpack Compose, Room으로 만들었습니다.

## 주요 기능
- **자산**: 계좌별 잔액, 순자산(자산 − 부채), 계좌 간 이체
- **가계부**: 수입·지출·이체 기록, 월별·캘린더 보기, 검색·필터, CSV 내보내기
- **소비 분석**: 카테고리별 비중, 월별 추이
- **예산·목표**: 월 예산과 카테고리별 예산, 매월 반복되는 수입·지출(고정 거래) 자동 기록, 저축 목표
- **보안**: 생체 인증 또는 PIN으로 앱 잠금
- **백업·복원**: 비밀번호로 암호화된 백업 파일을 저장하거나 카카오톡 등으로 보내고, 새 휴대폰에서 불러오기
- **결제 자동 등록**: 카드·은행 결제 알림과 SMS를 읽어 거래를 자동으로 기록

## 구조
- **UI**: Jetpack Compose + Navigation-Compose. 화면마다 ViewModel이 따로 있습니다 (`ui/<화면>/`).
- **DI**: Hilt (`di/AppModule.kt`)
- **데이터**: Room v3 (SQLCipher로 암호화), 스키마는 `app/schemas/`. 잔액이 바뀌는 쓰기는 모두 하나의 DB 트랜잭션으로 처리합니다 (`LedgerRepository`).
- **도메인**: 월별 통계(`MonthlyStatsCalculator`), 고정 거래 엔진(`RecurringRepository`), 결제 문자 파서(`domain/capture`)
- **설정**: DataStore (`SettingsRepository`)

## 보안과 개인정보
- 거래·계좌 데이터는 SQLCipher로 암호화합니다. 키는 Android Keystore로 보호됩니다.
- 앱 잠금 PIN은 PBKDF2 해시로만 저장하고, 5회 이상 틀리면 입력을 잠시 막습니다. 백그라운드에 있다가 돌아오면 다시 잠깁니다.
- 화면 캡처 방지와 최근 앱 화면 가리기를 기본으로 켭니다.
- 시스템 클라우드 백업은 꺼져 있습니다 (암호화 키가 기기에만 있어 복원해도 열 수 없기 때문). 대신 앱의 **백업 파일**(비밀번호로 AES-256-GCM 암호화, `.mfbackup`)로 저장·복원합니다.
- 결제 알림·문자 내용은 로그에 남기지 않고 기기 밖으로 보내지 않습니다. AI 카테고리 추천을 쓰면 메모 내용이 Firebase AI로 전송됩니다.

## 빌드 요구 사항
- Android Studio 최신 버전 (AGP 9.1 지원)
- JDK 21
- Android SDK Platform 36.1

```bash
./gradlew :app:assembleDebug        # 디버그 APK
./gradlew :app:testDebugUnitTest    # 단위 테스트
./gradlew :app:lintDebug            # 린트
```

`ScreenshotTest`는 주요 화면을 라이트/다크 모드로 그려 `app/build/screenshots/`에 PNG로 저장합니다 (눈으로 확인하는 용도).

### 설치와 업데이트 (가족용, Play 스토어 미사용)
[`docs/family-install-guide.md`](docs/family-install-guide.md)에 정리되어 있습니다.
- 서명 키 만들기: `scripts/create-release-keystore.sh` (키를 잃어버리면 업데이트 불가 — 꼭 백업)
- `v1.0.0` 같은 태그를 push하면 GitHub Actions(`release.yml`)가 서명된 APK를 만듭니다.

### 릴리스 서명 (로컬 빌드)
아래 환경 변수가 있을 때만 릴리스 APK에 서명합니다. 없으면 서명되지 않은 APK가 만들어집니다.

| 변수 | 설명 |
|---|---|
| `KEYSTORE_PATH` | 키스토어 파일 경로 |
| `STORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (기본값 `upload`) |
| `KEY_PASSWORD` | 키 비밀번호 |

### Firebase (AI 카테고리 추천, 현재 보류)
AI 카테고리 추천은 현재 꺼져 있습니다 (`TransactionFormScreen.kt`의 `SHOW_AI_SUGGESTION`). 다시 켜려면 Firebase AI Logic을 쓰는 Firebase 프로젝트에 앱 ID `io.github.seyun623.moneyflow`를 등록하고 받은 `google-services.json`을 `app/`에 넣으세요. 이 파일은 저장소에 커밋하지 않습니다. 파일이 없어도 빌드는 되고, AI 추천만 동작하지 않습니다.

## CI
GitHub Actions(`.github/workflows/android.yml`)가 push할 때마다 단위 테스트, 린트, 디버그·릴리스 빌드를 실행합니다.
