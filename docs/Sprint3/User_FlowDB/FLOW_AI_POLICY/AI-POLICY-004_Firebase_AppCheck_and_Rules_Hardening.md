# [Improvement] AI-POLICY-004 Firebase App Check와 접근 규칙 보안 정리

## 목적

Umma는 AI Chat, Correction, Flashcard, LearningState, Statistics 흐름에서 Firebase Auth, Firestore, Cloud Functions, Firebase AI Logic, OpenAI Realtime을 함께 사용한다.
Google Play 배포 전에는 Android 앱에 노출되는 key와 Firebase 직접 접근 경로가 악용되지 않도록, API key 제한, Firestore Rules, App Check, Cloud Functions 인증 경계를 정리해야 한다.

이 작업은 Google Play 심사 대응을 위한 배포 전 보안 준비 작업이다.
AI 안전 신고 기능이나 교정 안전 필터를 새로 구현하는 작업이 아니라, 이미 존재하는 AI/Firebase 호출 경로가 승인 전 기준에 맞게 보호되는지 확인하고 필요한 최소 보완을 적용한다.

---

# User Story

사용자는 로그인한 본인 데이터만 읽고 쓸 수 있다.
Umma는 OpenAI/Gemini/Firebase 관련 key가 Android 앱에서 직접 악용되지 않도록 보호한다.
운영자는 배포 전 Firebase App Check, Firestore Rules, Cloud Functions 인증 경계를 확인해 비정상 앱이나 변조 요청이 핵심 서버 경로를 임의로 호출하지 못하게 준비할 수 있다.

---

# 완료 기준(AC)

- [ ] Android 앱에는 OpenAI/Gemini secret key가 직접 포함되지 않는다.
- [ ] Realtime token과 usage sync 함수는 로그인 사용자 요청만 처리할 수 있다.
- [ ] 앱이 직접 호출하는 Cloud Functions 요청에는 App Check 검증에 필요한 값이 함께 전달된다.
- [ ] Debug와 Release 빌드는 서로 다른 App Check provider를 사용한다.
- [ ] Firestore는 기본 차단 상태이며, 사용자는 본인 데이터만 접근할 수 있다.
- [ ] 운영용 AI 신고는 사용자가 새 신고만 만들 수 있고, 클라이언트에서 조회·수정·삭제할 수 없다.
- [ ] 서버가 관리하는 Chat usage 문서는 클라이언트가 직접 수정할 수 없다.
- [ ] Wear 앱은 Firebase/Functions를 직접 호출하지 않고 phone 앱의 보안 경계를 따른다.
- [ ] 보안 설정 후 기존 Chat, Correction, Flashcard, LearningState, Statistics 흐름이 유지된다.

---

# 기준 문서

- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](./AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [AI-POLICY-002 Correction 후보와 Flashcard 저장 안전 방어](./AI-POLICY-002_Correction_and_Flashcard_Safety_Guard.md)
- [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](./AI-POLICY-003_Legal_Disclosure_and_Operations.md)
- [FLOW-AI-CHAT 개선 문서 묶음](../FLOW_AI_CHAT_IMPROVEMENTS)
- [FLOW-COR 개선 문서 묶음](../FLOW_COR_IMPROVEMENTS)
- [SYS_REALTIME_INFRA](../../System_FlowDB/SYS_REALTIME_INFRA)
- [SYS_LEARNING_STATE_INFRA](../../System_FlowDB/SYS_LEARNING_STATE_INFRA)

---

# 핵심 결정

- **Android 앱은 secret key를 소유하지 않는다.**
  - OpenAI API key는 Cloud Functions/Secret Manager에만 둔다.
  - Android 앱은 Cloud Function이 발급한 short-lived Realtime token만 사용한다.

- **Firebase Web API key는 앱에 포함될 수 있지만 제한이 필요하다.**
  - Android package, SHA 인증서, 허용 API를 제한한다.
  - key 제한만으로 충분하다고 보지 않고 Auth, Rules, App Check를 함께 사용한다.

- **Firestore Rules는 클라이언트 직접 접근만 제한한다.**
  - Cloud Functions Admin SDK는 Rules를 우회하므로, 서버 저장 경로는 Functions 코드에서 별도 검증한다.
  - 클라이언트 write가 필요 없는 서버 계산 문서는 Rules에서 write를 닫는다.

- **App Check enforcement는 단계적으로 적용한다.**
  - 먼저 debug/release provider를 설치하고 token이 정상 발급되는지 확인한다.
  - 팀원 debug token 등록과 내부 테스트 빌드 검증 전에는 enforcement를 바로 켜지 않는다.

- **기존 기능 회귀를 막으며 점진적으로 좁힌다.**
  - 처음부터 모든 하위 컬렉션의 필드 schema를 세밀하게 검증하지 않는다.
  - 우선 사용자 소유권과 서버 전용 write 차단을 적용하고, 이후 collection별 schema 검증은 후속 작업으로 나눈다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Android app | Firebase Auth 상태로 Firestore/Functions를 호출하고, secret key를 직접 소유하지 않는다. |
| `UmmaApplication` | Firebase SDK 인스턴스가 만들어지기 전 App Check provider를 설치한다. |
| build type source set | debug/release App Check provider를 분리한다. |
| Repository/DataSource | 직접 HTTPS 요청에 Firebase Auth ID token과 App Check token을 전달한다. |
| Firestore Rules | 클라이언트 직접 접근 권한을 제한한다. |
| Cloud Functions | OpenAI key와 서버 계산 write를 소유하고, 인증된 요청만 처리한다. |
| Firebase Console / Google Cloud Console | API key 제한, SHA 등록, Auth provider, App Check provider 설정을 관리한다. |
| Wear app | phone 앱과 Wearable Message API로만 통신하고 Firebase/Functions 직접 호출을 소유하지 않는다. |

---

# 주요 작업

## 1. Secret key와 API key 노출 점검

Android 코드와 설정 파일에서 OpenAI/Gemini secret key가 직접 포함되어 있는지 확인한다.
`local.properties`에서 읽는 값 중 Android BuildConfig로 들어가는 값은 endpoint/model처럼 공개되어도 되는 값인지 구분한다.

점검 대상:

- OpenAI secret key
- Gemini secret key
- Firebase Web API key
- Realtime token endpoint
- Usage sync endpoint

정책:

- OpenAI secret key는 Android 앱에 넣지 않는다.
- Realtime token endpoint URL은 앱에 있어도 되지만, 함수는 Firebase Auth와 App Check로 보호한다.
- Firebase Web API key는 package/SHA/API 제한을 걸어 무단 사용 가능성을 낮춘다.

## 2. App Check provider 설치

App Check provider는 Firebase SDK 요청이 만들어지기 전에 설치한다.
Debug와 Release provider를 source set으로 분리해 release 빌드가 debug provider에 의존하지 않게 한다.

권장 구조:

```text
app/src/debug/.../AppCheckProviderInstaller.kt
→ DebugAppCheckProviderFactory

app/src/release/.../AppCheckProviderInstaller.kt
→ PlayIntegrityAppCheckProviderFactory
```

주의:

- debug token은 팀원별로 Firebase Console에 등록해야 한다.
- release 검증은 Android Studio 직접 설치가 아니라 Play Console 내부 테스트 설치 기준으로 판단한다.
- enforcement는 provider 설치와 지표 확인 이후에 별도 단계로 진행한다.

## 3. 직접 HTTPS Cloud Functions 호출 보호

Firebase SDK가 직접 호출하는 Firestore/Functions callable 경로는 SDK가 App Check token을 붙일 수 있다.
하지만 OkHttp로 호출하는 `onRequest` Function은 Android 코드가 직접 `X-Firebase-AppCheck` header를 추가해야 한다.

적용 대상:

- `realtimeToken`
- `submitChatUsageSession`

정책:

- Firebase Auth ID token은 사용자가 누구인지 증명한다.
- App Check token은 요청이 등록된 앱에서 온 것인지 증명한다.
- enforcement 적용 전에는 App Check token 획득 실패가 사용자 흐름을 막지 않게 warning으로 남기고 요청을 보낼 수 있다.
- enforcement 적용 시점에는 서버 검증 실패 정책을 별도로 정한다.

## 4. Firestore Rules 정리

기본 규칙은 deny로 두고, 명시한 경로만 허용한다.
사용자 개인 데이터는 `request.auth.uid == uid`일 때만 접근 가능하게 한다.

권장 정책:

- `users/{uid}` 루트 문서: 본인 read/create/update 허용, delete 차단
- `users/{uid}` 하위 일반 데이터: 본인 read/write 허용
- `ai_content_reports`: 인증 사용자 create만 허용, read/update/delete 차단
- `chat_prompt_review_reports`: 개발용 index create/update만 허용, read/delete 차단
- `chat_usage_sessions`, `chat_usage_monthly`: 본인 read 허용, 클라이언트 write 차단

주의:

- Firestore Rules는 여러 `match`의 allow가 OR로 평가된다.
- 서버 전용 usage 컬렉션은 별도 `allow write: if false`만 추가하면 부족할 수 있다.
- generic `users/{uid}` 하위 write 허용 조건에서도 해당 collection을 제외해야 한다.

## 5. Cloud Functions 인증 경계 점검

Cloud Functions는 URL이 외부에 노출될 수 있으므로 handler 내부에서 인증을 검증해야 한다.

점검 대상:

- `realtimeToken`
- `submitChatUsageSession`
- `deleteAccount`
- scheduled notification functions

정책:

- `realtimeToken`은 Firebase Auth ID token이 유효한 사용자에게만 OpenAI short-lived token을 발급한다.
- `submitChatUsageSession`은 요청 userId와 Firebase Auth uid가 일치할 때만 usage를 저장한다.
- `deleteAccount`는 callable request auth가 없는 경우 실행하지 않는다.
- scheduled functions는 클라이언트 호출 대상이 아니며 Admin SDK 영역으로 둔다.

## 6. Wear 앱 영향 확인

Wear 앱이 Firebase, OpenAI, Cloud Functions를 직접 호출하는지 확인한다.
직접 호출이 없다면 phone 앱의 보안 경계를 따른다.

점검 기준:

- wear 모듈에 Firebase 의존성이 없는지 확인한다.
- wear 모듈이 OkHttp/HTTP 직접 호출을 하지 않는지 확인한다.
- phone 앱과의 통신이 Wearable Message API로만 이루어지는지 확인한다.

## 7. 배포 전 운영 설정 확인

Firebase/Google Cloud/Play Console에서 코드만으로 확인할 수 없는 항목을 점검한다.

확인 항목:

- Firebase Auth Google provider 활성화
- Firebase Android app에 debug/release/upload/Play signing SHA 등록
- Google Cloud API key Android package/SHA 제한
- Google Cloud API key 허용 API 최소화
- Firebase App Check provider 등록
- 팀원 debug token 등록
- 내부 테스트 트랙에서 Play Integrity verified 요청 확인
- Firestore/App Check/Functions enforcement 적용 시점 결정

---

# 예외 처리

- App Check token을 가져오지 못해도 enforcement 전에는 기존 Chat/usage 흐름을 막지 않는다.
- Firestore Rules 변경 후 정상 사용자가 본인 데이터를 읽거나 쓰지 못하면 Rules를 먼저 재검토한다.
- Cloud Functions Admin SDK write는 Firestore Rules 영향을 받지 않으므로, 서버 write 실패는 Functions 코드/권한/DB ID를 별도로 확인한다.
- Android Studio로 직접 설치한 release APK는 Play Integrity의 `PLAY_RECOGNIZED` 조건을 만족하지 못할 수 있다.
- 팀원 debug token을 등록하기 전에는 App Check enforcement를 켜지 않는다.
- API key 허용 API를 줄일 때는 Firebase Auth, Firestore, Firebase AI Logic, App Check, Installations 등 실제 SDK 의존 API가 막히지 않게 단계적으로 확인한다.

---

# 검증 기준

- secret key가 Android 코드와 repository에 직접 포함되어 있지 않은지 확인한다.
- `realtimeToken` 호출이 Firebase Auth ID token 없이 실패하는지 확인한다.
- `realtimeToken`과 `submitChatUsageSession` 요청에 App Check header를 붙일 수 있는지 확인한다.
- App Check debug token 등록 후 debug 빌드에서 Chat과 Firestore 저장이 정상 동작하는지 확인한다.
- 내부 테스트 트랙 설치 앱에서 Play Integrity 기반 App Check verified 요청이 발생하는지 확인한다.
- Firestore Rules가 syntax compile을 통과하는지 확인한다.
- 사용자는 본인 `users/{uid}` 하위 데이터만 접근할 수 있다.
- `ai_content_reports`는 create만 가능하고 클라이언트 read/update/delete는 차단된다.
- `chat_usage_sessions`, `chat_usage_monthly`는 Cloud Function 저장은 가능하지만 클라이언트 직접 write는 차단된다.
- Chat 시작, AI 응답, 대화 종료, usage sync, Correction, Flashcard 저장, LearningState/Statistics 조회가 회귀하지 않는다.
- wear 모듈이 Firebase/Functions 직접 호출을 하지 않는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`, `:app:compileDevReleaseKotlin` 빌드가 통과한다.
- `firebase deploy --only firestore:rules --project umma-6804c --dry-run`이 통과한다.
