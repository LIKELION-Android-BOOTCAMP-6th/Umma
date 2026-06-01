# [Feature] CHAT-005 AI 응답 출력

## User Story

사용자는 자신의 발화를 보낸 뒤 AI의 음성 응답을 들으며 대화를 이어갈 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] user turn 종료 후 AI 응답 대기 상태를 표시한다.
- [ ] AI 음성 출력 중 Speaking 상태를 표시한다.
- [ ] AI 응답 실패 시 Error 또는 Retry 상태를 표시한다.
- [ ] AI 응답 출력 상태는 중앙 비주얼에 반영된다.
- [ ] partial transcript와 final transcript를 UI 정책에 맞게 구분한다.
- [ ] AI 응답 이벤트 수신은 `RT-002` 계약을 따른다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-003 → PTT 음성 입력
- CHAT-005 → AI 응답 출력
- CHAT-006 → 마지막 턴 자막
- RT-002 → 음성 스트림

---

# 구현 범위

## 포함 범위

- AI 응답 대기 상태
- AI 음성 출력 상태
- 응답 실패/재시도 상태
- 중앙 비주얼 Speaking 상태 연결
- transcript 이벤트를 화면 상태로 변환

## 제외 범위 (Out of Scope)

- Firebase Live API 직접 구현
- Session Memory append
- 자막 토글 UI
- LangState 업데이트

---

# Details

## 응답 역할

AI 응답 출력은 Realtime 인프라에서 받은 응답 이벤트를 화면 상태로 바꾸는 단계다.
실제 Firebase Live API 연결과 스트림 수신은 `SYS-REALTIME-INFRA`가 담당한다.

## 상태 정책

- `Thinking`: AI 응답 대기
- `Speaking`: AI 음성 출력 중
- `Error`: 응답 실패
- `Ready`: 다음 PTT 입력 가능

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatViewModel
→ ChatUiState

domain/model/realtime/
→ AIEvent
```

`AIEvent`는 최소한 role, partial/final 여부, speaking 상태를 화면에서 구분할 수 있게 전달되어야 한다.

---

## 현재 코드 전환 메모

- 현재 `AIEvent.TextResponse(text)`만으로는 user/assistant, partial/final transcript를 구분하기 어렵다.
- Realtime 이벤트 모델은 role과 final 여부를 표현할 수 있도록 확장한다.
- UI는 부분 transcript를 전체 대화처럼 누적하지 않고, 자막과 저장 흐름에 필요한 final 이벤트를 구분해서 넘긴다.
- `AudioResponse` 수신 시 음성 재생 상태가 중앙 비주얼의 `Speaking` 상태와 연결되어야 한다.

---

# 검증 기준

- user turn 종료 후 AI 응답 대기 상태가 보인다.
- AI 음성 출력 중 중앙 비주얼이 Speaking 상태가 된다.
- 응답 실패 시 앱이 멈추지 않고 재시도 가능한 상태가 된다.

---

# Edge Cases

- AI 응답이 지연됨
- AI 응답 음성은 오지만 transcript가 늦게 옴
- partial transcript만 오고 final transcript가 오지 않음
- 응답 중 화면 이탈
- 네트워크 오류로 응답이 중단됨
