# [Improvement] CHAT-UX-001 토글형 음성 입력 UX

## User Story

사용자는 AI Chat에서 마이크 버튼을 한 번 눌러 발화를 시작하고, 말을 마친 시점에 다시 눌러 user turn을 명확히 종료할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 마이크 버튼 첫 클릭 시 녹음이 시작된다.
- [ ] 녹음 중에는 버튼이 정지 동작임을 사용자가 이해할 수 있게 표시된다.
- [ ] 마이크 버튼 재클릭 시 현재 user turn이 종료된다.
- [ ] user turn 종료 후 사용자 발화 확정 대기 상태를 거쳐 AI 응답 대기 또는 응답 상태로 자연스럽게 전환된다.
- [ ] 세션 준비 전, AI 응답 중, 빠른 중복 클릭 상황에서 중복 녹음이 시작되지 않는다.
- [ ] 기존 SessionMemory 저장 흐름은 final transcript 기준을 유지한다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-003_PTT_Input.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- OpenAI PoC: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-POC-001_OpenAI_Realtime_Push_to_Talk_PoC.md`
- Realtime 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-002_Stream.md`

---

# 구현 범위

## 포함 범위

- toggle-to-talk 기준의 마이크 버튼 상태 정리
- 녹음 시작/정지 UI 피드백
- 사용자 turn 종료 후 transcript 확정 대기 상태 표시
- 중복 클릭 방어
- AI 응답 중 입력 방어
- 기존 녹음 duration 계산 흐름 유지

## 제외 범위 (Out of Scope)

- press-and-hold 방식 PTT 구현
- AI 응답 prompt tuning
- 자막 타이밍 분리
- SessionMemory 저장 정책 변경
- OpenAI Realtime transport 전환과 token endpoint 보안 정리

---

# Details

## 입력 정책

- 이번 Sprint3 MVP에서는 press-and-hold가 아니라 toggle-to-talk를 기준으로 한다.
- 첫 클릭은 사용자 발화 시작, 두 번째 클릭은 사용자 발화 종료로 해석한다.
- 사용자가 직접 정지 버튼을 누른 시점을 user turn 종료 의도로 본다.
- `CHAT-POC-001` 검증 결과, OpenAI Realtime 경로에서는 두 번째 클릭 이후 `input_audio_buffer.commit`으로 사용자 발화를 확정하고, user transcription completed 이후 AI 응답을 시작하는 흐름을 사용할 수 있다.
- AI가 응답 중이거나 세션이 준비되지 않은 경우에는 새 입력을 시작하지 않는다.

## 사용자 흐름

```text
AI Chat READY
→ 마이크 버튼 클릭
→ 사용자 발화 중(녹음/오디오 전송 중)
→ 마이크 버튼 재클릭
→ user turn 종료 요청
→ 사용자 발화 확정 대기
→ AI 응답 대기 / AI 응답
→ 다시 입력 가능 상태
```

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatScreen
→ ChatViewModel
→ ChatUiState.canStartUserTurn
→ ChatUiState.canEndUserTurn
```

- `ChatScreen`은 버튼 표시와 클릭 이벤트만 담당한다.
- `ChatViewModel`은 현재 세션 상태와 AI 상태를 기준으로 입력 시작/종료를 결정한다.
- `ChatRepository`는 오디오 frame 전송과 realtime transport 이벤트 수신만 담당한다.

## 현재 코드 확인 지점

- `ChatScreen`의 마이크 버튼은 이미 `clickable` 기반으로 동작한다.
- `ChatViewModel.startUserTurn()`은 권한 확인 이후 `beginUserTurn()`으로 녹음을 시작한다.
- `ChatViewModel.endUserTurn()`은 녹음을 중단하고 `EndUserTurnUseCase`로 user turn 종료 의도와 duration metadata를 전달한다.
- OpenAI Realtime 경로에서는 `ChatRepository.endUserTurn()` 호출이 user turn 종료 신호로 쓰이며, repository가 audio commit 이후 user transcription completed를 기다린다.
- `ChatUiState.canStartUserTurn` / `canEndUserTurn`이 toggle 동작의 핵심 guard다.

## 선행 의존성

- OpenAI Realtime transport 전환과 token endpoint 보안 정리는 `CHAT-ENGINE-001`에서 먼저 다룬다.
- UX-001은 이미 정리된 transport 계약 위에서 버튼 상태와 입력 방어를 구현한다.

---

# 검증 기준

- 클릭 시작/클릭 종료 방식이 문서와 화면에서 일치한다.
- 녹음 중 버튼 상태가 시작 상태와 구분된다.
- 두 번째 클릭 전에는 AI 응답이 시작되지 않는다.
- 두 번째 클릭 후 사용자 발화 확정 대기 상태를 사용자가 이해할 수 있다.
- 빠른 연속 클릭으로 녹음 job이 중복 생성되지 않는다.
- AI 응답 중에는 사용자 녹음이 시작되지 않는다.
- 기존 final turn 저장과 correction handoff가 깨지지 않는다.

---

# Edge Cases

- 세션 준비 전 마이크 클릭
- 마이크 권한 거부 후 재시도
- 녹음 시작 직후 빠른 정지
- 녹음 중 AI 응답 이벤트 수신
- AI 응답 중 마이크 클릭
- 네트워크 오류로 녹음 중단
