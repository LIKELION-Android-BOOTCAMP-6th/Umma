# [Improvement] CHAT-UX-002 사용자/AI 자막 출력 타이밍

## User Story

사용자는 자막을 켠 상태에서 자신의 발화가 먼저 화면에 표시되고, 이후 AI 응답 자막이 별도로 표시되는 흐름을 경험할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 사용자 발화 종료 후 user transcription completed 결과가 AI 응답 자막보다 먼저 표시된다.
- [ ] AI 자막은 사용자 자막과 역할이 구분되어 표시된다.
- [ ] 사용자 자막은 서버 USER transcript 이벤트 기준으로 표시되며 임의 추정 문자열을 만들지 않는다.
- [ ] 사용자 자막 표시 이후 AI 자막과 음성이 이어지는 흐름을 유지한다.
- [ ] `correctionAvailable` 신호는 기존처럼 USER final 저장 성공 기준으로만 발생한다.
- [ ] AI transcript delta는 현재 응답 자막으로만 표시되고 전체 대화 로그처럼 누적되지 않는다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-006_Subtitle.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-007_Turn_Commit.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- OpenAI PoC: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-POC-001_OpenAI_Realtime_Push_to_Talk_PoC.md`
- Realtime 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-002_Stream.md`
- Turn 저장 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-003_Turn_Commit.md`

---

# 구현 범위

## 포함 범위

- 사용자 자막과 AI 자막 표시 타이밍 분리
- 사용자 발화 종료 후 server transcription completed 결과 표시
- 사용자 transcript 표시 이후 AI 응답 생성
- AI transcript delta 기반 응답 자막 표시
- 자막 표시 상태와 turn 저장 상태 분리
- subtitle UI state 정리

## 제외 범위 (Out of Scope)

- 전체 대화 로그 UI
- SessionMemory 저장 정책 변경
- correction candidate 추출 정책 변경
- AI 응답 생성 품질 개선
- OpenAI Realtime transport event 순서 보장

---

# Details

## 자막 정책

- 자막은 저장 정책이 아니라 화면 표시 정책이다.
- 이번 범위에서는 사용자가 말하는 동안 글자가 타이핑되듯 생성되는 실시간 사용자 자막은 다루지 않는다.
- 사용자 자막은 사용자가 정지 버튼을 눌러 turn을 종료한 뒤, 서버가 반환한 USER transcript를 기준으로 표시한다.
- `CHAT-ENGINE-001`에서 USER transcript를 먼저 받고 AI 응답을 시작하는 transport 순서가 선행 정리되어야 한다.
- USER transcript가 도착하기 전에는 임의 문장을 만들지 않고, 필요한 경우 "인식 중"에 해당하는 대기 상태만 표시한다.
- 실제 저장과 correction handoff는 서버 final transcript 이벤트 기준을 유지한다.

## 표시 흐름

```text
사용자 발화 시작
→ 사용자 정지 버튼 클릭
→ input_audio_buffer.commit
→ USER transcript completed 수신
→ 사용자 자막 먼저 표시
→ AI outputTranscription / audio response 수신
→ AI 자막 표시
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

- `ChatUiState`는 USER transcript 표시 상태, AI transcript delta 표시 상태, transcript 대기 상태를 역할별로 구분할 수 있어야 한다.
- `ChatViewModel`은 화면 표시용 자막 상태와 SessionMemory 저장 트리거를 분리한다.
- `ChatRepository`는 transport별 이벤트 차이를 숨기고 USER transcript, AI transcript, AI audio를 역할별 `AIEvent`로 전달한다.
- transport의 USER/AI 이벤트 순서 보장은 `CHAT-ENGINE-001` 책임이며, UX-002는 전달받은 이벤트를 화면 상태로 분리한다.

## 현재 코드 확인 지점

- `ChatRepositoryImpl`은 OpenAI Realtime event를 USER transcript, AI transcript, AI audio로 역할별 매핑한다.
- 현재 USER final transcript는 user transcription completed 시점에 먼저 발행되고, AI response는 그 이후 `response.create`로 시작된다.
- `ChatRepositoryImpl`은 audio commit 이후 user transcription completed를 먼저 받고, 그 다음 `response.create`를 호출하는 방식으로 `user_before_ai=true`를 확인할 수 있다.
- `ChatScreen`은 현재 `lastFinalUserTranscript`, `lastFinalAITranscript`만 표시한다.
- `ChatViewModel.handleFinalTranscription()`은 final event 수신 시 저장까지 트리거한다.

---

# 검증 기준

- 사용자 자막이 AI 자막과 같은 시점에 한꺼번에 표시되지 않는다.
- USER transcript 표시와 SessionMemory 저장 트리거가 중복 저장을 만들지 않는다.
- 서버 final USER transcript 도착 후 correctionAvailable 신호는 1회만 발생한다.
- 자막 On/Off를 반복해도 현재 흐름 중심의 자막 표시가 유지된다.
- Logcat 또는 테스트 fake에서 `user_before_ai=true` 순서를 재현할 수 있다.

---

# Edge Cases

- USER transcript가 늦게 도착함
- USER transcript가 비어 있음
- AI transcript 이벤트가 USER transcript보다 먼저 도착함
- 자막 Off 상태에서 USER transcript 이벤트 발생
- 동일 final turn 중복 수신
- 긴 사용자 발화 자막
