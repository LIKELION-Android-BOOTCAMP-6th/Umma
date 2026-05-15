# [Feature] CHAT-008 종료 및 재진입 복구

## User Story

사용자는 AI Chat을 나갔다가 다시 들어와도 앱이 안전하게 상태를 복구하거나, 복구가 어렵다면 새 LiveSession으로 자연스럽게 시작할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 화면 이탈 시 녹음/재생 상태를 안전하게 정리한다.
- [ ] 진행 중인 final turn 저장 요청을 누락하지 않도록 처리한다.
- [ ] 다시 진입하면 현재 선택 언어 기준 앱 상태 복원을 시도한다.
- [ ] 복구 실패 시 새 LiveSession으로 전환한다.
- [ ] 재진입 실패가 앱 크래시로 이어지지 않는다.
- [ ] 후속 Correction / Flashcard 흐름으로 이어질 수 있는 저장 상태를 유지한다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-007 → 확정 turn 저장 연동
- CHAT-008 → 종료 및 재진입 복구
- RT-004 → 재연결/복구

---

# 구현 범위

## 포함 범위

- 화면 이탈 cleanup
- 녹음/재생 중지
- 저장 중 상태 보호
- 재진입 시 상태 복원
- 복구 실패 시 새 LiveSession 전환

## 제외 범위 (Out of Scope)

- 오디오 스트림 구현
- 자막 렌더링
- LangState 계산
- Correction 결과 생성

---

# Details

## 종료 정책

- 화면 이탈 시 녹음과 AI 음성 재생을 정리한다.
- Firebase LiveSession 연결 정리는 `RT-004` 기준을 따른다.
- final turn 저장은 가능하면 완료되도록 보장하고, 실패 시 재시도 가능한 상태를 남긴다.
- 저장 실패가 있더라도 화면 이탈이나 앱 복구가 크래시로 이어지지 않아야 한다.

## 재진입 정책

- 같은 선택 언어의 앱 상태를 먼저 복원한다.
- Firebase Live API 연결 자체는 장기 재사용 대상으로 보지 않는다.
- 복구가 어렵다면 새 LiveSession을 연결한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatViewModel

domain/usecase/chat/
→ StopSessionUseCase
→ StartSessionUseCase
```

종료는 화면 상태 정리와 연결 종료, 저장 보호를 분리해서 처리한다.

---

## 현재 코드 전환 메모

- `stopChat()`은 녹음/재생 중지, Firebase LiveSession 종료, 저장 중 상태 보호를 분리해서 처리한다.
- `ChatRepositoryImpl.stopSession()`은 `LiveSession.close()` 이후 내부 `session` 참조를 비워 중복 연결과 재사용 오해를 막는다.
- `LiveServerGoAway`는 단순 종료로 숨기지 말고, ViewModel이 재연결 또는 새 세션 전환 상태를 알 수 있는 이벤트로 전달한다.
- 다시 진입할 때는 이전 Firebase LiveSession 자체를 장기 재사용하지 않고, 앱 상태와 Session Memory를 복원한 뒤 새 LiveSession을 연결한다.

---

# 검증 기준

- 화면 이탈 시 녹음/재생이 중지된다.
- 다시 진입 시 앱 상태 복원을 먼저 시도한다.
- 복구 실패 시 새 LiveSession으로 전환된다.
- 저장 실패 또는 복구 실패가 앱 크래시로 이어지지 않는다.
- 저장 실패가 있으면 재시도 가능한 상태나 local pending 상태가 남는다.

---

# Edge Cases

- 녹음 중 화면 이탈
- AI 응답 재생 중 화면 이탈
- final turn 저장 중 화면 이탈
- 재진입 시 selected language가 바뀌어 있음
- 재연결 실패가 반복됨
