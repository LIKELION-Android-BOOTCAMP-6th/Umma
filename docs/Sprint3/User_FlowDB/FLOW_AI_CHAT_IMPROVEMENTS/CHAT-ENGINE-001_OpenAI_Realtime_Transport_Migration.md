# [Engine] CHAT-ENGINE-001 OpenAI Realtime Transport 전환

## User Story

사용자는 AI Chat에서 기존과 같은 대화 흐름을 사용하되, 내부 realtime transport는 OpenAI Realtime 기준으로 동작한다.

이번 작업은 `CHAT-POC-001`에서 검증한 OpenAI Realtime 경로로 AI Chat 음성 대화 엔진을 완전히 교체하는 선행 인프라 작업이다. 이후 화면 UX 보강은 `CHAT-FIX-001-C/D/E`에서 마이크 버튼 상태, final 자막 대화형 표시, 음성 레벨 wave로 나누어 다룬다.

---

## 완료 기준(AC)

- [ ] AI Chat 화면은 기존 진입 흐름 그대로 사용할 수 있으며, OpenAI 경로에서도 별도 테스트 화면 없이 대화 준비 상태에 도달한다.
- [ ] 사용자가 마이크 버튼을 눌러 말하는 동안에는 AI 응답이 먼저 시작되지 않는다.
- [ ] 사용자가 정지 버튼을 누른 뒤 사용자 발화가 먼저 확정되고, 그 이후 AI 응답 자막/음성이 이어진다.
- [ ] 사용자 발화와 AI 응답은 화면 또는 Logcat에서 서로 다른 이벤트로 구분해 확인할 수 있다.
- [ ] 사용자와 AI가 한 차례 이상 대화한 뒤 기존처럼 최근 대화가 저장되고, 교정 대기 흐름으로 이어질 수 있다.
- [ ] 화면 회전 후에도 이미 준비된 대화 상태가 불필요하게 초기화되지 않는다.
- [ ] AI Chat 화면을 실제로 이탈하면 녹음, 재생, realtime 연결이 정리된다.
- [ ] 대화 중 네트워크가 끊기면 사용자는 연결 복구 중 상태를 볼 수 있고, 복구 성공 시 같은 대화 화면에서 이어갈 수 있다.
- [ ] 자동 복구가 실패하면 사용자는 재시도 가능한 오류 상태를 볼 수 있다.

---

## 기준 문서

- Sprint2 AI Chat: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Realtime Infra: `docs/System_FlowDB/SYS_REALTIME_INFRA.md`
- Realtime Overview: `docs/System_FlowDB/SYS_REALTIME_INFRA/SYS_REALTIME_INFRA_OVERVIEW.md`
- OpenAI PoC: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-POC-001_OpenAI_Realtime_Push_to_Talk_PoC.md`
- 마이크 버튼 상태 UX: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-C_Mic_Button_State_UX.md`
- final 자막 대화형 표시: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md`

---

## 범위

### 포함 범위

- OpenAI Realtime repository를 AI Chat 단일 transport 구현으로 정리
- Hilt binding / BuildConfig / variant 기준의 transport 고정 정책 정리
- user turn 종료 전용 계약 또는 use case 정리
- token 발급 Cloud Function 보안 경계 정리
- 기존 `AIEvent`, `AIState`, `ChatViewModel` 방어로직과의 정합성 확인
- AI Chat의 Firebase/Gemini Live 구현 제거

### 제외 범위

- 자막 UI 세부 표현 개선
- LangState prompt tuning
- SessionMemory 저장 정책 변경
- correction / flashcard / statistics 도메인 변경
- 운영 수준의 전체 비용 정책 확정

---

## 핵심 결정

- `CHAT-POC-001` 결과는 `A. 전환 후보`로 확인했고, 이번 작업에서 OpenAI Realtime로 교체한다.
- 엔진 전환 작업은 UX 작업이 아니라 realtime transport 인프라 작업이다.
- `ChatRepository` 추상화는 유지한다. ViewModel과 UI는 provider 이름을 알지 않는다.
- OpenAI Realtime은 수동 turn 제어를 위해 `turn_detection = null`과 명시적 `input_audio_buffer.commit`을 사용한다.
- AI 응답 시작은 user transcription completed 이후 `response.create`로 진행한다.
- partial/provisional transcript는 저장 기준이 아니다. 확정 final transcript만 SessionMemory와 correction handoff의 기준이다.
- Realtime transport 세션은 장기 기억이 아니다. 장기 기억은 기존처럼 앱의 activeSessionId와 SessionMemory가 맡는다.

---

## 현재 코드 확인 지점

- `ChatRepository`는 presentation/domain 레이어가 OpenAI 구현 세부사항을 알지 않도록 유지하는 추상 계약이다.
- `ChatRepositoryImpl`은 OpenAI WebSocket 연결, token 발급, audio append/commit, user transcription completed 이후 response 생성 흐름을 담당한다.
- `EndUserTurnUseCase` / `ChatRepository.endUserTurn()` 계약으로 user turn 종료 의도를 명시한다.
- `DevChatRepositoryModule`은 dev variant에서 OpenAI 기반 `ChatRepositoryImpl`을 단일 구현으로 주입한다.
- `functions/index.js`의 `realtimeToken`은 Secret Manager의 OpenAI key로 Realtime client secret을 발급한다.

---

## 전환 설계

### 1. Transport 고정 정책

- dev에서는 OpenAI Realtime 경로만 사용한다.
- `OPENAI_REALTIME_ENABLED` 선택 플래그는 제거한다.
- production 전환 전에는 token endpoint 보안 강화 이슈 `#193`을 먼저 처리한다.
- mock variant는 계속 fake repository를 사용해 시나리오 테스트 안정성을 유지한다.

### 2. User Turn 종료 계약

- `EndUserTurnUseCase`가 사용자의 정지 버튼 입력을 user turn 종료 의도로 전달한다.
- `ChatRepository.endUserTurn(durationMs: Long?)`은 provider별 transport 종료 처리를 담당한다.
- `CancelPendingUserTurnUseCase`는 화면 이탈, 새 발화 시작 전 초기화처럼 아직 확정되지 않은 user turn을 폐기하는 cleanup 계약으로만 사용한다.
- final USER transcript metadata 보관은 `ChatRepositoryImpl` 내부 상태로 제한하고, ViewModel/domain 레이어에는 별도 pending duration setter를 노출하지 않는다.
- 이 정리는 `CHAT-FIX-001-C`의 마이크 버튼 상태 UX와 직접 연결되므로 ENGINE 작업에서 먼저 경계를 잡는다.

### 3. Event Mapping

- OpenAI event는 기존 `AIEvent`로 매핑한다.
- USER transcript completed는 `AIEvent.FinalTranscription(role = USER)`로 전달한다.
- AI transcript delta는 provider 이벤트로 수신 가능하지만, MVP 화면 표시는 `CHAT-FIX-001-D` 기준에 따라 final transcript를 사용한다.
- AI audio delta는 기존 `AIEvent.AudioResponse`로 전달한다.
- response usage는 운영 판단용 로그 또는 별도 telemetry 후보로 남긴다.

### 4. Security Boundary

- Android 앱은 OpenAI API key를 갖지 않는다.
- OpenAI API key는 Firebase Secret Manager에만 둔다.
- `realtimeToken` Cloud Function은 장기적으로 공개 접근 상태로 두지 않는다.
- 최소 보안 후보:
  - Firebase Auth ID token 검증
  - App Check
  - userId별 rate limit
- PoC에서 만든 개인 소유(`You`) / `All` 권한 OpenAI key는 팀/서비스 계정 소유의 제한 권한 key로 교체한다.
- 운영 수준의 token endpoint 보안 강화는 후속 이슈 `#193`에서 추적한다.

### 4-1. Security Check Items

- Android 앱, git 추적 파일, Logcat에 OpenAI API key가 노출되지 않는지 확인한다.
- PoC용 공개 token endpoint와 개인/All 권한 key 설정은 운영 전 정리 대상으로 관리한다.
- 이번 범위를 넘는 Cloud Function 인증, App Check, rate limit, service account 권한 축소는 후속 이슈 `#193`에서 처리한다.

### 5. Existing Guard Preservation

- 세션 준비 전 입력 방어를 유지한다.
- AI speaking 중 사용자 입력 방어를 유지한다.
- 화면 회전 시 불필요한 세션 재생성을 막는 정책을 유지한다.
- 실제 화면 이탈 시 녹음/재생/transport 정리는 유지한다.
- 네트워크 오류와 reconnect 실패는 사용자에게 재시도 가능한 상태로 노출한다.

---

## 작업 순서

1. 기존 `ChatRepository`, `StartSessionUseCase`, `RetryConnectionUseCase`, `EndUserTurnUseCase`, `CancelPendingUserTurnUseCase`, `StopSessionUseCase`의 계약을 점검한다.
2. 기존 `ChatRepositoryImpl` 자리를 OpenAI Realtime 기반 단일 transport 구현으로 정리한다.
3. `EndUserTurnUseCase`와 `ChatRepository.endUserTurn()`으로 user turn 종료 계약을 명시한다.
4. OpenAI event mapping을 기존 `AIEvent` 계약과 맞춘다.
5. Hilt binding과 BuildConfig 설정을 dev / mock 기준으로 정리한다.
6. Cloud Function token 발급 보안 정리 범위를 결정하고, 운영 보안 강화는 후속 이슈 `#193`으로 남긴다.
7. AI Chat의 Firebase/Gemini Live 구현과 fallback 선택 분기를 제거한다.
8. 컴파일과 최소 수동 로그 검증으로 transport 전환 정합성을 확인한다.

---

## 검증 기준

- `:app:compileDevDebugKotlin` 성공
- `:app:compileMockDebugKotlin` 성공
- OpenAI 경로에서 AI Chat 화면 진입 후 대화 준비 상태 도달
- 정지 버튼 입력 전 AI 응답이 시작되지 않음
- Logcat에서 `user_before_ai=true` 순서가 유지됨
- 대화 완료 후 USER/AI final turn 이 SessionMemory 에 저장되고, USER turn 기준 correctionAvailable 신호가 1회만 반영됨
- 같은 turnId 의 final event 재수신 시 최근 대화/교정 대기 상태가 중복 증가하지 않음
- 화면 회전과 실제 이탈 cleanup 정책이 충돌하지 않음
- 네트워크 끊김 후 `SessionInterrupted` / `Reconnected` / `ReconnectFailed` 상태가 사용자 화면과 Logcat에서 확인됨
- Android 앱 또는 git 추적 파일에 OpenAI API key가 포함되지 않음
- PoC용 공개 token endpoint와 개인/All 권한 key 설정은 후속 보안 이슈 `#193`에서 추적됨

---

## Edge Cases

- token 발급 실패
- WebSocket session update 실패
- user transcript completed 지연 또는 빈 값
- response 생성 실패
- AI audio 수신 중 화면 이탈
- 네트워크 끊김 후 reconnect 실패
- release 빌드에서 token endpoint 설정이 누락됨
