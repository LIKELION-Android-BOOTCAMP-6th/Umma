# [Fix] CHAT-FIX-001 화면 회전 시 세션 안정성

## User Story

사용자는 AI Chat 화면을 회전해도 현재 대화 상태가 불필요하게 초기화되지 않고, 실제 화면을 떠날 때만 녹음과 재생이 정리되는 경험을 해야 한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] READY 상태에서 화면 회전 시 새 LiveSession이 불필요하게 생성되지 않는다.
- [ ] 화면 회전 후 자막 On/Off 상태가 유지된다.
- [ ] 화면 회전 후 마지막 사용자/AI 자막이 유지된다.
- [ ] 화면 회전 후 마이크 버튼 상태가 현재 세션 상태와 일치한다.
- [ ] AI 응답 중 화면 회전이 발생해도 응답 상태가 중복 초기화되지 않는다.
- [ ] 실제 화면 이탈 시에는 기존처럼 녹음과 재생이 정리된다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-008_Exit_Reentry.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- Reconnect 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-004_Reconnect.md`

---

# 구현 범위

## 포함 범위

- 화면 회전 시 `enterChat()` 중복 실행 방어
- READY 상태의 activeSessionId 유지
- subtitle 상태 보존
- AI 응답 중 recomposition 안정화
- 화면 이탈 cleanup 경계 확인

## 제외 범위 (Out of Scope)

- 장기 백그라운드 세션 유지
- 앱 프로세스 kill 이후 LiveSession 복원
- Firebase Live API 세션 자체의 영구 재사용
- Android multi-window 전체 대응

---

# Details

## 상태 유지 정책

- 화면 회전은 configuration change로 보고, 사용자가 대화 화면을 떠난 것으로 간주하지 않는다.
- 이미 READY 상태이고 activeSessionId가 있으면 새 세션 시작을 시도하지 않는다.
- 자막 표시 여부와 마지막 자막은 ViewModel 상태로 유지한다.
- 실제 navigation 이탈에서는 기존처럼 녹음과 재생을 정리한다.

## 기대 흐름

```text
AI Chat READY
→ 화면 회전
→ 기존 ViewModel 상태 유지
→ 새 LiveSession 생성 없음
→ 자막/버튼/AI 상태 유지
→ 대화 계속 가능
```

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatScreen
→ ChatViewModel.enterChat()
→ ChatUiState

domain/usecase/chat/
→ StartSessionUseCase
→ RetryConnectionUseCase
```

- `ChatScreen`의 `LaunchedEffect(Unit)`가 회전 후 호출되더라도 `ChatViewModel.enterChat()`에서 중복 시작을 방어한다.
- `ChatViewModel`은 active session이 준비되어 있으면 READY 상태를 유지한다.
- `stopChat()`은 실제 화면 이탈에서만 리소스 정리 목적으로 호출되어야 한다.

## 현재 코드 확인 지점

- `ChatScreen`은 진입 시 `LaunchedEffect(Unit)`에서 `enterChat()`을 호출한다.
- `enterChat()`은 `enterChatJob` 중복은 막지만, 이미 READY인 상태에서 재호출될 때의 정책 확인이 필요하다.
- `DisposableEffect(Unit)`의 `onDispose`에서 `stopChat()`을 호출하므로 configuration change와 실제 이탈 구분이 필요할 수 있다.
- `ChatRepositoryImpl.startSession()`은 동일 조건 세션이면 재사용하는 guard가 있지만, 화면 상태 초기화는 ViewModel에서 별도 확인이 필요하다.

---

# 검증 기준

- READY 상태에서 회전해도 Loading부터 다시 보이지 않는다.
- 회전 후 activeSessionId가 유지된다.
- subtitle visible 상태와 마지막 자막이 유지된다.
- AI 응답 중 회전해도 응답 상태가 중복 초기화되지 않는다.
- 실제 뒤로가기/화면 이탈 시에는 녹음과 재생이 정리된다.

---

# Edge Cases

- 세션 Loading 중 화면 회전
- Recording 중 화면 회전
- AI Speaking 중 화면 회전
- final turn 저장 중 화면 회전
- 화면 회전 직후 뒤로가기
- selected language 변경 후 재진입
