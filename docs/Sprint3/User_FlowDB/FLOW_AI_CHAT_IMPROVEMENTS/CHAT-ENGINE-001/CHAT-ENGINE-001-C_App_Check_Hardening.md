# [Engine] CHAT-ENGINE-001-C App Check Hardening

## User Story

사용자는 정상 앱에서 AI Chat을 사용하고, 서비스는 Firebase Auth 검증에 더해 비정상 클라이언트의 token endpoint 호출을 줄여야 한다.

App Check는 로그인 사용자 여부를 판단하는 기능이 아니라, 요청이 신뢰 가능한 앱 인스턴스에서 왔는지 확인하는 보강 경계다.

---

## 완료 기준(AC)

- [ ] Android 앱은 `realtimeToken` 요청에 App Check token을 함께 전달한다.
- [ ] Cloud Function은 Firebase Auth ID token 검증 후 App Check token을 검증한다.
- [ ] App Check token이 없거나 잘못된 요청은 OpenAI client secret 발급 전에 거부된다.
- [ ] debug build 테스트를 위한 App Check debug token 절차가 정리된다.
- [ ] App Check 실패가 OpenAI API key 또는 client secret을 노출하지 않는다.

---

## 기준 문서

- 상위 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- Auth hardening: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001/CHAT-ENGINE-001-A_Token_Endpoint_Auth_Hardening.md`
- Usage tracking: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001/CHAT-ENGINE-001-B_Realtime_Usage_Tracking.md`

---

## 범위

### 포함

- Android App Check token 발급 및 header 전달
- Cloud Function App Check token 검증
- debug build 테스트 절차 정리
- 인증 실패와 App Check 실패의 응답 경계 분리

### 제외

- 사용자별 사용량 제한
- 플랜 정책
- OpenAI usage 저장
- Cloud Run IAM 인증 전환

---

## 검증 순서

1. Firebase Auth ID token 검증이 먼저 통과하는지 확인한다.
2. Android 앱이 App Check token을 요청 header로 전달하는지 확인한다.
3. Cloud Function이 전달된 App Check token을 Admin SDK로 검증하는지 확인한다.
4. App Check token이 없거나 잘못된 요청이 거부되는지 확인한다.
5. 정상 debug token을 등록한 개발 앱 요청이 통과하는지 확인한다.
6. 정상 앱에서 AI Chat 대화가 기존과 동일하게 연결되는지 확인한다.

---

## 테스트 방법

- App Check 검증 로직이 활성화된 상태에서, debug token 등록 전 요청이 거부되는지 확인한다.
- debug token 등록 후 `devDebug` AI Chat 연결이 성공하는지 확인한다.
- Authorization header가 없으면 App Check 이전에 `401 Unauthorized`로 거부되는지 확인한다.
- App Check 실패 응답에 OpenAI key 또는 client secret이 포함되지 않는지 확인한다.
