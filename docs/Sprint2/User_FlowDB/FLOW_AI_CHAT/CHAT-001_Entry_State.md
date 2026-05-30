# [Feature] CHAT-001 AI Chat 진입 및 초기 상태

## User Story

사용자는 AI Chat 화면에 진입했을 때 현재 선택 언어 기준으로 대화를 시작할 준비 상태를 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] AI Chat 진입 시 Global Learning State에서 `selectedLearningLanguage`를 확인한다.
- [ ] 현재 선택 언어의 `LangState` snapshot을 로드한다.
- [ ] 현재 선택 언어의 Session Memory 조회 준비 상태를 확인한다.
- [ ] `activeSessionId`가 있으면 앱 상태 복원을 시도한다.
- [ ] 복원 가능한 앱 상태가 없으면 새 LiveSession 연결 준비 상태로 전환한다.
- [ ] 자막 기본값을 Off로 초기화한다.
- [ ] 화면 진입 직후 Loading / Ready / Error 상태를 구분한다.
- [ ] 세션 준비 중 중복 초기화가 방지된다.
- [ ] 진입 시 음성 녹음은 자동 시작되지 않는다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-001 → AI Chat 진입 및 초기 상태
- RT-001 → 세션 부트스트랩
- SYS-LEARNING-STATE-INFRA → GlobalLangState / LangState

---

# 구현 범위

## 포함 범위

- AI Chat 화면 진입 상태 구성
- selected language / LangState / Session Memory 준비 상태 확인
- 초기 Loading / Ready / Error UI
- 자막 기본값 Off 처리

## 제외 범위 (Out of Scope)

- 마이크 권한 요청
- push-to-talk 입력
- Firebase Live API 세션 직접 생성 로직
- 자막 렌더링
- turn 저장

---

# Details

## 진입 역할

AI Chat 진입은 사용자가 대화를 시작하기 전에 화면이 어떤 언어와 상태를 기준으로 동작할지 정하는 단계다.
Firebase Live API 연결 생성과 activeSessionId 복원 정책은 `RT-001`의 System Flow 기준을 따른다.

## 사용 데이터

- `GlobalLangState`
- `selectedLearningLanguage`
- `activeSessionId`
- `LangState` snapshot
- Session Memory 준비 상태
- subtitle visibility state

## 상태 정책

- `Loading`: 상태 복원 또는 세션 준비 중
- `Ready`: 사용자가 PTT 버튼을 누를 수 있는 상태
- `Error`: 진입 준비 실패

## 복원 정책

- `activeSessionId`가 있으면 같은 선택 언어의 앱 상태 복원을 먼저 시도한다.
- 복원 가능한 앱 상태가 없으면 새 LiveSession 연결 준비 상태로 전환한다.
- 복원과 새 연결 준비는 동시에 실행하지 않는다.
- 복원 실패가 화면 진입 자체를 막지 않도록 재시도 가능한 상태를 제공한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatScreen
→ ChatViewModel
→ ChatUiState
```

## 상태 예시

```text
Loading
Ready(lang, langState, subtitleVisible = false)
Error(message)
```

ViewModel은 화면 상태 owner가 되고, 실제 LiveSession 부트스트랩은 `RT-001`에서 정의한 repository/usecase 계약을 호출한다.

---

## 현재 코드 전환 메모

- 현재 `ChatScreen`의 placeholder 텍스트는 실제 AI Chat 화면 shell로 교체한다.
- 화면 진입은 세션 준비와 초기 상태 표시까지만 담당한다.
- `ChatViewModel.startChat()`이 세션 시작 직후 자동 녹음을 시작하지 않도록 `CHAT-003`의 PTT 입력 흐름과 분리한다.

---

# 검증 기준

- 선택 언어가 없으면 Error 또는 안내 상태가 표시된다.
- `activeSessionId`가 있으면 앱 상태 복원을 먼저 시도한다.
- 복원 실패 시 새 LiveSession 연결 준비 상태로 전환된다.
- 정상 진입 시 자막이 Off 상태로 시작한다.
- 정상 진입 시 녹음이 자동 시작되지 않는다.
- Ready 상태에서만 PTT 입력으로 넘어갈 수 있다.

---

# Edge Cases

- `selectedLearningLanguage`가 없음
- `LangState` snapshot이 없음
- Session Memory 준비 실패
- 화면 재진입으로 초기화가 중복 실행됨
- 세션 준비 실패 후 다시 시도
