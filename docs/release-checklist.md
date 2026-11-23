# 출시 준비 체크리스트 (Google Play)

앱 ID: `io.github.seyun623.moneyflow` (Play에 처음 올린 뒤에는 바꿀 수 없음)

## 1. 업로드 키 만들기 (한 번만)
1. 내 컴퓨터에서 실행: `./scripts/create-upload-keystore.sh`
2. 만들어진 `moneyflow-upload.jks`와 비밀번호를 **비밀번호 관리자 등 안전한 곳에 백업**합니다. 저장소에는 절대 커밋하지 않습니다.
3. GitHub 저장소 → Settings → Secrets and variables → Actions에 4개를 등록합니다.
   - `ANDROID_KEYSTORE_BASE64`: `base64 -w0 moneyflow-upload.jks` 결과 (macOS: `base64 -i moneyflow-upload.jks`)
   - `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`(`upload`), `ANDROID_KEY_PASSWORD`

## 2. 릴리스 빌드
- 태그를 올리면 자동으로 빌드됩니다: `git tag v1.0.0 && git push origin v1.0.0`
- 또는 GitHub → Actions → "Release build" → Run workflow
- 결과물(Actions 실행 화면의 Artifacts): `app-release.aab`(Play 업로드용), `app-release.apk`(직접 설치용), `mapping.txt`(오류 분석용, Play Console에 함께 업로드)
- versionCode는 커밋 수 + 100으로 자동 증가합니다.

## 3. 실기기 확인 (출시 전 필수)
자동 테스트로 확인할 수 없는 부분입니다. 릴리스 APK를 실제 휴대폰에 설치해 확인하세요.
- [ ] 첫 실행, 샘플 데이터 불러오기, 앱 종료 후 다시 열었을 때 데이터 유지 (DB 암호화 동작)
- [ ] 앱 잠금: PIN 설정 → 백그라운드 30초 후 다시 잠김 → PIN 5회 오류 시 잠시 차단
- [ ] 생체 인증 켜기/해제
- [ ] 최근 앱 화면에서 내용이 가려지는지, 스크린샷이 막히는지
- [ ] 결제 알림 자동 등록: 알림 접근 허용 → 실제 카드 결제 → 1건만 기록되는지
- [ ] 문자 자동 등록: 권한 허용 → 실제 카드 문자 → 알림과 중복 기록되지 않는지, 승인취소 시 원거래 삭제
- [ ] 고정 거래가 지정한 날짜에 기록되는지
- [ ] 다크 모드, 화면 회전, 글자 크게
- [ ] CSV 내보내기 후 엑셀에서 열기

## 4. Play Console 등록
- [ ] 개발자 계정 (개인 계정은 출시 전 **비공개 테스트 12명 이상·14일** 필요)
- [ ] 앱 만들기 → Play 앱 서명 사용(기본값) → AAB 업로드
- [ ] 개인정보처리방침 URL: `docs/privacy-policy.md`를 채운 뒤 웹에 게시 (GitHub Pages, 노션 공개 페이지 등)
- [ ] 데이터 보안(Data safety) 양식: `docs/play-data-safety.md` 참고
- [ ] 앱 콘텐츠: 광고 없음, 대상 연령(만 18세 이상 권장 - 금융), 콘텐츠 등급 설문
- [ ] 스토어 등록정보: 앱 이름, 간단한 설명(80자), 자세한 설명, 아이콘 512px, 그래픽 이미지 1024×500, 휴대폰 스크린샷 2장 이상
- [ ] 금융 기능 선언: "개인 자산 관리/가계부"(대출·투자 상품 판매 아님)

## 5. ⚠️ 문자(SMS) 권한 — 출시 전 결정 필요
- `RECEIVE_SMS`는 Play의 **제한된 권한**입니다. 앱의 핵심 기능이 SMS여야 하고, Permissions Declaration 양식을 제출해 승인받아야 합니다.
- 가계부 앱은 예외 목록에 없어 **거절될 가능성이 높고, 거절되면 앱 전체 업데이트가 막힙니다.**
- 선택지
  1. Play 버전에서는 문자 기능을 빼고(알림 자동 등록만 사용) APK 직접 배포 버전에만 문자 기능 유지 — 권장
  2. 그대로 제출하고 선언 양식으로 승인 시도
- 1번을 고르면 빌드 변형(flavor)을 나눠 Play용 빌드에서 SMS 권한과 수신기를 빼도록 바로 작업할 수 있습니다.

## 6. AI 카테고리 추천 (보류 중)
- 현재 꺼져 있고 Firebase 설정 파일도 없어 외부로 데이터가 나가지 않습니다.
- 다시 켜면 메모 내용이 Google(Firebase AI)로 전송되므로 개인정보처리방침과 데이터 보안 양식을 함께 수정해야 합니다.
