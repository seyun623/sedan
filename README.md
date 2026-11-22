# 머니플로우 (MoneyFlow)

가계부와 자산 관리를 한 곳에서 하는 Android 앱입니다. Kotlin, Jetpack Compose, Room으로 만들었습니다.

## 주요 기능
- **자산**: 계좌별 잔액, 순자산(자산 − 부채), 계좌 간 이체
- **가계부**: 수입·지출·이체 기록, 월별·캘린더 보기, 검색·필터, CSV 내보내기
- **소비 분석**: 카테고리별 비중, 월별 추이
- **예산·목표**: 월 예산과 카테고리별 예산, 매월 반복되는 수입·지출(고정 거래) 자동 기록, 저축 목표
- **보안**: 생체 인증 또는 PIN으로 앱 잠금
- **결제 자동 등록**: 카드·은행 결제 알림과 SMS를 읽어 거래를 자동으로 기록

## 빌드 요구 사항
- Android Studio 최신 버전 (AGP 9.1 지원)
- JDK 21
- Android SDK Platform 36.1

```bash
./gradlew :app:assembleDebug        # 디버그 APK
./gradlew :app:testDebugUnitTest    # 단위 테스트
./gradlew :app:lintDebug            # 린트
```

### 릴리스 서명
아래 환경 변수가 있을 때만 릴리스 APK에 서명합니다. 없으면 서명되지 않은 APK가 만들어집니다.

| 변수 | 설명 |
|---|---|
| `KEYSTORE_PATH` | 키스토어 파일 경로 |
| `STORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (기본값 `upload`) |
| `KEY_PASSWORD` | 키 비밀번호 |

### Firebase (AI 카테고리 추천, 선택)
AI 카테고리 추천은 Firebase AI Logic을 사용합니다. 쓰려면 직접 만든 Firebase 프로젝트의 `google-services.json`을 `app/`에 넣으세요. 이 파일은 저장소에 커밋하지 않습니다. 파일이 없어도 빌드는 되고, AI 추천만 동작하지 않습니다.

## CI
GitHub Actions(`.github/workflows/android.yml`)가 push할 때마다 단위 테스트, 린트, 디버그·릴리스 빌드를 실행합니다.
