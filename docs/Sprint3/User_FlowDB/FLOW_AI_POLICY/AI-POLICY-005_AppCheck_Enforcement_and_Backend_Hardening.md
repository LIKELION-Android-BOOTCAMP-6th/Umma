# [Improvement] AI-POLICY-005 App Check Enforcement와 Backend 보안 강화

## 목적

AI-POLICY-004에서 Android 앱의 App Check provider, Firestore Rules, 직접 Functions 호출 header를 준비했다.
이 후속 작업은 준비 단계에서 끝내지 않고, 실제 차단 정책을 안전하게 적용할 수 있도록 App Check enforcement, Cloud Functions 검증, Firestore Rules 세분화, API key 권한 최소화를 진행한다.

이 문서는 Play Console 정책 문구나 개인정보처리방침을 정리하는 문서가 아니다.
배포 정책과 운영 안내는 [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](./AI-POLICY-003_Legal_Disclosure_and_Operations.md)를 기준으로 한다.

---

# User Story

운영자는 Firebase/App Check/Functions 보안 설정을 실제 차단 단계까지 적용할 수 있다.
정상 사용자는 Chat, Correction, Flashcard, LearningState, Statistics 흐름을 계속 사용할 수 있고, 비정상 앱이나 변조 요청은 핵심 서버 경로를 임의로 호출하기 어렵다.

---

# 완료 기준(AC)

- [ ] App Check enforcement 적용 전 팀원 debug token과 내부 테스트 빌드 검증이 완료된다.
- [ ] 내부 테스트 트랙 설치 앱에서 Play Integrity 기반 verified 요청을 확인할 수 있다.
- [ ] `realtimeToken`과 `submitChatUsageSession`은 Auth와 App Check 실패를 구분해 거절할 수 있다.
- [ ] Firestore Rules는 서버 전용 문서와 클라이언트 직접 수정 문서를 구분한다.
- [ ] Google Cloud API key는 실제 앱 동작에 필요한 API만 허용한다.
- [ ] 보안 설정 후 기존 Chat, Correction, Flashcard, LearningState, Statistics 흐름이 유지된다.
- [ ] enforcement 이후 오류가 발생했을 때 확인할 로그와 되돌릴 순서가 정리된다.

---

# 기준 문서

- [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](./AI-POLICY-003_Legal_Disclosure_and_Operations.md)
- [AI-POLICY-004 Firebase App Check와 접근 규칙 보안 정리](./AI-POLICY-004_Firebase_AppCheck_and_Rules_Hardening.md)
- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](./AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [FLOW-AI-CHAT 개선 문서 묶음](../FLOW_AI_CHAT_IMPROVEMENTS)
- [SYS_REALTIME_INFRA](../../System_FlowDB/SYS_REALTIME_INFRA)

---

# 핵심 결정

- **enforcement는 한 번에 켜지 않는다.**
  - debug token, 내부 테스트, Firebase Console 지표 확인 이후 단계적으로 적용한다.

- **Auth와 App Check는 역할이 다르다.**
  - Auth는 사용자를 확인한다.
  - App Check는 요청 출처가 등록된 앱인지 확인한다.
  - 두 검증 중 하나만으로 서버 보호가 충분하다고 보지 않는다.

- **서버 전용 데이터는 클라이언트 write를 닫는다.**
  - Chat usage처럼 서버가 계산하는 값은 Functions/Admin SDK를 통해서만 저장한다.
  - 사용자가 읽어야 하는 값과 사용자가 수정할 수 있는 값은 Rules에서 분리한다.

- **API key 권한은 단계적으로 줄인다.**
  - Firebase SDK가 내부적으로 의존하는 API가 막히면 정상 기능이 깨질 수 있다.
  - 허용 API 축소 후 로그인, Firestore, Firebase AI Logic, App Check, Messaging 흐름을 함께 확인한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Android app | Auth/App Check 값을 요청에 전달하고, enforcement 실패를 사용자 흐름에 맞게 처리한다. |
| Cloud Functions | Auth/App Check/payload 검증 후 서버 secret과 Admin SDK write를 수행한다. |
| Firestore Rules | 클라이언트 직접 read/write 권한을 최소화한다. |
| Firebase Console | App Check provider, debug token, enforcement, 요청 지표를 관리한다. |
| Google Cloud Console | Android API key 제한과 허용 API 범위를 관리한다. |
| QA/운영 | 내부 테스트 트랙과 로그를 통해 정상/차단 경로를 확인한다. |

---

# 주요 작업

## 1. App Check enforcement 적용 준비

- 팀원 debug token 등록 상태를 확인한다.
- `devDebug`에서 Chat, AI 신고, Correction, Flashcard 저장 흐름을 확인한다.
- Play Console 내부 테스트 트랙으로 설치한 release 앱에서 verified 요청을 확인한다.
- verified 요청이 안정적으로 보이기 전에는 enforcement를 켜지 않는다.

## 2. Cloud Functions 검증 강화

대상:

- `realtimeToken`
- `submitChatUsageSession`

작업:

- Firebase Auth ID token 검증을 유지한다.
- App Check token 검증을 추가한다.
- Auth 실패, App Check 실패, payload 실패 응답을 구분한다.
- 실패 응답과 서버 로그에 OpenAI key, client secret, 민감 payload가 노출되지 않게 한다.

## 3. Firestore Rules 세분화

AI-POLICY-004에서는 사용자 소유권과 서버 전용 usage write 차단을 먼저 적용한다.
후속 작업에서는 실제 컬렉션별로 필요한 권한을 더 좁힌다.

검토 대상:

- `users/{uid}/sessions`
- `users/{uid}/flashcards`
- `users/{uid}/language_states`
- `users/{uid}/dashboard_summaries`
- `users/{uid}/statistics_history`
- `users/{uid}/notification_settings`
- `users/{uid}/notification_devices`
- `users/{uid}/chat_usage_sessions`
- `users/{uid}/chat_usage_monthly`

주의:

- local-first sync와 pending retry가 필요한 컬렉션은 write를 과도하게 좁히지 않는다.
- collection별 field schema 검증은 정상 기능을 깨지 않는 범위에서 단계적으로 추가한다.

## 4. Google Cloud API key 권한 최소화

현재 Android key가 여러 API를 허용하고 있다면 실제 앱에 필요한 API만 남기도록 줄인다.

검토 대상:

- Firebase Auth
- Firestore
- Firebase AI Logic
- Firebase App Check
- Firebase Installations
- Firebase Messaging
- Remote Config 또는 사용 중인 Firebase SDK 의존 API

주의:

- 하나씩 줄이고 앱 기능을 검증한다.
- 어떤 API가 SDK 내부 의존인지 불명확하면 바로 삭제하지 않고 기록 후 테스트한다.

## 5. 로그와 되돌림 절차 정리

enforcement 이후에는 정상 요청도 설정 누락으로 차단될 수 있다.
따라서 적용 전 확인 경로와 되돌림 순서를 정리한다.

확인 경로:

- Logcat App Check warning
- Firebase Console App Check 지표
- Cloud Functions logs
- Firestore permission denied 로그
- 앱 화면의 Chat/Correction/Flashcard 저장 실패

되돌림 원칙:

- 사용자 흐름이 광범위하게 막히면 enforcement를 먼저 끈다.
- 특정 Firestore 경로만 막히면 Rules diff를 우선 확인한다.
- Functions만 실패하면 Functions 로그와 Auth/App Check 실패 응답을 구분한다.
- API key 제한 후 실패하면 직전 제한 변경을 되돌리고 필요한 API를 다시 확인한다.

---

# 예외 처리

- 팀원 debug token이 일부 누락된 상태에서는 전체 enforcement를 켜지 않는다.
- Android Studio로 직접 설치한 release APK는 Play Integrity 검증 기준으로 삼지 않는다.
- App Check token 획득 실패를 무조건 사용자 오류로 표시하지 않는다.
- Firestore Rules를 좁힌 뒤 정상 사용자가 본인 데이터를 읽지 못하면 Rules를 먼저 재검토한다.
- Cloud Functions Admin SDK write 실패는 Firestore Rules 문제가 아니라 Functions 권한, DB ID, payload 문제를 우선 확인한다.

---

# 검증 기준

- 내부 테스트 트랙 설치 앱에서 App Check verified 요청이 발생한다.
- App Check enforcement 후 debug token 등록 기기와 내부 테스트 앱이 정상 동작한다.
- App Check token이 없는 `realtimeToken`, `submitChatUsageSession` 요청은 거절된다.
- Auth token이 없는 요청은 App Check 검증 전후와 관계없이 거절된다.
- Firestore Rules 변경 후 정상 사용자는 본인 데이터에 접근할 수 있고, 서버 전용 usage 문서는 직접 수정할 수 없다.
- API key 제한 후 로그인, Chat, Correction, Flashcard 저장, AI 신고, LearningState/Statistics 흐름이 유지된다.
- 오류 발생 시 App Check, Functions, Firestore Rules, API key 중 어느 경계에서 실패했는지 로그로 구분할 수 있다.

