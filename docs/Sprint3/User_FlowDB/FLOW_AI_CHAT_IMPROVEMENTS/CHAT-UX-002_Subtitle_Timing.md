# [Improvement] CHAT-UX-002 사용자/AI 자막 출력 타이밍

## User Story

사용자는 자막을 켠 상태에서 자신의 발화가 먼저 화면에 표시되고, 이후 AI 응답 자막이 별도로 표시되는 흐름을 경험할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 사용자 발화 종료 후 사용자 자막이 AI 응답 자막보다 먼저 표시된다.
- [ ] AI 자막은 사용자 자막과 역할이 구분되어 표시된다.
- [ ] 사용자 선표시 자막은 화면용 상태이며 SessionMemory 저장 기준으로 직접 사용하지 않는다.
- [ ] 서버 USER final transcript가 도착하면 화면용 사용자 자막을 확정 값으로 교체하거나 유지한다.
- [ ] `correctionAvailable` 신호는 기존처럼 USER final 저장 성공 기준으로만 발생한다.
- [ ] partial transcript가 전체 대화 로그처럼 누적 표시되지 않는다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-006_Subtitle.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-007_Turn_Commit.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- Realtime 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-002_Stream.md`
- Turn 저장 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-003_Turn_Commit.md`

---

# 구현 범위

## 포함 범위

- 사용자 자막과 AI 자막 표시 타이밍 분리
- 사용자 발화 종료 시점의 화면용 provisional subtitle
- 서버 final transcript 도착 시 자막 확정 처리
- 자막 표시 상태와 turn 저장 상태 분리
- subtitle UI state 정리

## 제외 범위 (Out of Scope)

- 전체 대화 로그 UI
- SessionMemory 저장 정책 변경
- correction candidate 추출 정책 변경
- AI 응답 생성 품질 개선

---

# Details

## 자막 정책

- 자막은 저장 정책이 아니라 화면 표시 정책이다.
- 사용자 발화 종료 직후 보여주는 자막은 provisional 상태일 수 있다.
- provisional 자막은 사용자가 “내 말이 인식되었다”는 것을 빠르게 확인하기 위한 UX 장치다.
- 실제 저장과 correction handoff는 서버 final transcript 이벤트 기준을 유지한다.

## 표시 흐름

```text
사용자 발화 시작
→ inputTranscription partial 수신
→ 사용자 정지 버튼 클릭
→ 화면용 사용자 자막 먼저 표시
→ AI outputTranscription / audio response 수신
→ AI 자막 표시
→ 서버 final transcript 수신
→ 저장 및 correction handoff
```

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatUiState
→ ChatViewModel

presentation/chat/components/
→ ChatSubtitle
```

- `ChatUiState`는 partial, provisional, final subtitle을 역할별로 구분할 수 있어야 한다.
- `ChatViewModel`은 화면 표시용 자막과 저장용 final event를 분리한다.
- `ChatRepository`는 Live API에서 받은 input/output transcription 이벤트를 그대로 역할별 이벤트로 전달한다.

## 현재 코드 확인 지점

- `ChatRepositoryImpl`은 `inputTranscription`과 `outputTranscription`을 이미 분리 수신한다.
- 현재 final transcript는 `turnComplete` 시점에 USER와 AI가 거의 연속으로 발행된다.
- `ChatScreen`은 현재 `lastFinalUserTranscript`, `lastFinalAITranscript`만 표시한다.
- `ChatViewModel.handleFinalTranscription()`은 final event 수신 시 저장까지 트리거한다.

---

# 검증 기준

- 사용자 자막이 AI 자막과 같은 시점에 한꺼번에 표시되지 않는다.
- 사용자 선표시 자막으로 인해 SessionMemory 중복 저장이 발생하지 않는다.
- 서버 final USER transcript 도착 후 correctionAvailable 신호는 1회만 발생한다.
- 자막 On/Off를 반복해도 현재 흐름 중심의 자막 표시가 유지된다.

---

# Edge Cases

- inputTranscription partial이 비어 있음
- USER final transcript가 늦게 도착함
- AI outputTranscription이 user final보다 먼저 도착함
- 자막 Off 상태에서 provisional subtitle 이벤트 발생
- 동일 final turn 중복 수신
- 긴 사용자 발화 자막
