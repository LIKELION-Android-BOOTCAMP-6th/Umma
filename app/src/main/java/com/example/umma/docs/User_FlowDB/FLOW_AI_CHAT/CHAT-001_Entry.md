# [Feature] CHAT-001 AI Chat 진입

## User Story

사용자는 AI Chat 화면에 들어가면 현재 선택 언어의 앱 상태를 이어서 보거나, 없으면 새 LiveSession으로 시작할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] AI Chat 진입 시 selectedLearningLanguage가 사용된다.
- [ ] activeSessionId가 있으면 앱 상태 복원을 시도한다.
- [ ] 복원 가능한 세션이 없으면 새 LiveSession이 생성된다.
- [ ] 세션 진입 중 중복 생성이 방지된다.
- [ ] 앱 상태 복원 실패 시 새 LiveSession으로 안전하게 전환된다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-001 → AI Chat 진입

---

# 구현 범위

## 포함 범위

- AI Chat 첫 진입 처리
- 앱 상태 복원 / 새 LiveSession 생성
- selectedLearningLanguage 전달
- 초기 상태 렌더링

## 제외 범위 (Out of Scope)

- 음성 입력 수집
- 자막 표시
- turn 저장

---

# Details

## 진입 정책

- AI Chat은 `selectedLearningLanguage`를 먼저 읽는다.
- `activeSessionId`가 있으면 앱 상태 복원을 시도한다.
- 복원할 수 없으면 새 `LiveSession`을 연결한다.
- 복원과 생성을 동시에 하지 않는다.

## 초기 상태

- 진입 직후에는 Loading 상태를 보여준다.
- 마이크 권한이 없어도 화면은 열 수 있다.
- 자막 기본값은 Off다.

## 예외 처리

- 앱 상태가 없으면 빈 상태로 시작한다.
- 초기화 실패는 재시도 가능한 상태로 보여준다.
- 앱 진입이 막히지 않아야 한다.

## 데모 확인 포인트

1. AI Chat 진입 시 언어 컨텍스트가 맞는지 확인
2. 이전 상태가 있으면 복원되는지 확인
3. 이전 상태가 없으면 새 LiveSession으로 시작되는지 확인

## 권장 구현 경계

- `presentation/chat/ChatScreen.kt`
- `presentation/chat/ChatViewModel.kt`
- `domain/usecase/chat/StartSessionUseCase.kt`
- `domain/usecase/chat/ObserveAIEventUseCase.kt`
- `data/repository/ChatRepositoryImpl.kt`
- `di/AIModule.kt`

## 현재 테스트 코드 전환 메모

- `ChatScreen.kt`의 placeholder를 실제 AI Chat 화면으로 교체한다.
- 화면 진입은 세션 준비와 초기 상태 표시까지만 담당한다.
- 마이크 입력은 화면 진입 시 자동 시작하지 않고, `CHAT-002`의 push-to-talk 동작에서 시작한다.

## 한 줄 가이드

- 화면은 진입만 트리거하고, 실제 세션 연결은 ViewModel과 Repository가 맡는다.
