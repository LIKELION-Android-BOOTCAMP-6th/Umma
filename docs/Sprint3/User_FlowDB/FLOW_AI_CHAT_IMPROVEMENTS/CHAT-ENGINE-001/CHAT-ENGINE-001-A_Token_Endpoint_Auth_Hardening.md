# [Engine] CHAT-ENGINE-001-A Token Endpoint Auth Hardening

## User Story

사용자는 Google 로그인 상태에서만 AI Chat 대화를 시작할 수 있어야 한다.

OpenAI API key는 Android 앱에 포함하지 않고, `realtimeToken` Cloud Function이 Firebase Auth ID token을 검증한 뒤 인증된 사용자에게만 OpenAI Realtime client secret을 발급한다.

---

## 완료 기준(AC)

- [ ] Android 앱은 OpenAI API key를 직접 보유하지 않는다.
- [ ] Android 앱은 token 요청 시 Firebase Auth ID token을 `Authorization: Bearer` header로 전달한다.
- [ ] `realtimeToken` Cloud Function은 Firebase ID token 검증 성공 시에만 OpenAI client secret을 발급한다.
- [ ] 인증 정보가 없거나 잘못된 요청은 OpenAI 호출 전에 `401 Unauthorized`로 거부된다.
- [ ] PoC용 개인 소유 / All 권한 key는 팀 또는 서비스 계정 기준 key로 교체된다.

---

## 기준 문서

- 상위 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- PoC 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-POC-001_OpenAI_Realtime_Push_to_Talk_PoC.md`
- GitHub Issue: `#193`

---

## 범위

- Firebase Secret Manager의 `OPENAI_API_KEY` 사용
- Android의 Firebase ID token header 전달
- Cloud Function의 Firebase Admin SDK ID token 검증
- 인증 실패 시 `401 Unauthorized` 반환
- OpenAI service account key 교체

---

## 작업 흐름

```text
Android 앱
→ Firebase Auth ID token 조회
→ realtimeToken Cloud Function 호출
→ Cloud Function에서 ID token 검증
→ Secret Manager의 OpenAI API key로 client secret 발급
→ Android 앱이 client secret으로 OpenAI Realtime WebSocket 연결
```

---

## 테스트 방법

- `node --check functions/index.js`로 함수 문법을 확인한다.
- `:app:compileDevDebugKotlin`으로 Android token 요청 코드 컴파일을 확인한다.
- 로그인된 `devDebug` 앱에서 AI Chat 대화가 정상 연결되는지 확인한다.
- Authorization header 없이 `realtimeToken`에 POST 요청 시 `401 Unauthorized`가 반환되는지 확인한다.
- 앱 코드, git 추적 파일, Logcat에 OpenAI API key가 출력되지 않는지 확인한다.
