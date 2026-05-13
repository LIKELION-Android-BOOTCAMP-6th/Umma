# [Feature] CHAT-004 종료 및 복구

## User Story

사용자는 AI Chat을 나갔다가 다시 들어와도 이전 대화 맥락을 잃지 않거나, 복구가 실패하면 안전하게 새 LiveSession으로 시작할 수 있고, 종료 시 대화 내용이 Session Memory에 저장된다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 화면 이탈 시 현재 세션 상태가 안전하게 정리된다.
- [ ] 화면 이탈 시 현재 turn이 Session Memory 저장 대상에 반영된다.
- [ ] 다시 진입하면 복구 가능한 앱 상태를 먼저 찾는다.
- [ ] 복구 실패 시 새 LiveSession으로 전환된다.
- [ ] 세션 복구 실패가 앱 크래시로 이어지지 않는다.
- [ ] 후속 correction / flashcard 플로우로 연결될 수 있다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-004 → 종료 및 복구

---

# 구현 범위

## 포함 범위

- exit cleanup
- session save on exit
- app-state restore
- fallback to new LiveSession
- next flow handoff

## 제외 범위 (Out of Scope)

- 음성 입력 처리
- 자막 표시
- LangState 계산

---

# Details

## 종료 정책

- 화면을 떠날 때 현재 turn을 Session Memory 저장 대상으로 남긴다.
- 종료 시 correction / flashcard 흐름으로 이어질 수 있어야 한다.

## 복구 정책

- 다시 진입하면 앱 상태 복원을 먼저 시도한다.
- 복구가 실패하면 새 LiveSession을 연결한다.
- 복구는 저장 정책과 충돌하지 않아야 한다.

## 예외 처리

- 종료 중 저장 실패가 있어도 앱이 죽지 않아야 한다.
- 저장 실패 시 로컬 임시 저장 또는 재시도 경로를 남긴다.

## 데모 확인 포인트

1. 화면 이탈 시 저장 대상이 생기는지 확인
2. 다시 진입 시 이전 흐름이 이어지는지 확인
3. 복구 실패 시 새 LiveSession으로 넘어가는지 확인

## 권장 구현 경계

- `presentation/chat/ChatViewModel.kt`
- `domain/usecase/chat/StopSessionUseCase.kt`
- `domain/usecase/chat/StartSessionUseCase.kt`
- `data/repository/ChatRepositoryImpl.kt`

## 현재 테스트 코드 전환 메모

- `stopChat()`은 녹음/재생 중지, Firebase LiveSession 종료, 저장 대상 turn 정리를 분리해서 처리한다.
- `ChatRepositoryImpl.stopSession()`은 연결 종료 후 내부 `session` 참조를 비워야 한다.
- 다시 진입할 때는 이전 Firebase LiveSession을 재사용하지 않고, 앱 상태와 Session Memory를 복원한 뒤 새 LiveSession을 연결한다.

## 한 줄 가이드

- 종료 시 저장과 연결 종료를 분리하고, 복구는 다시 진입할 때 처리한다.
