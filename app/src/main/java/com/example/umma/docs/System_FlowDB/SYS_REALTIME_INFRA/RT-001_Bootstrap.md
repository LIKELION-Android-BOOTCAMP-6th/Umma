# [Feature] RT-001 세션 부트스트랩

## User Story

사용자는 AI Chat 화면에 진입했을 때 현재 선택 언어 기준의 Firebase Live API 세션이 준비되어 있고,
이전 앱 상태가 유효하면 자연스럽게 이어서 대화를 시작할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] AI Chat 진입 시 selectedLearningLanguage가 세션 초기화에 사용된다.
- [ ] activeSessionId가 새 세션 생성 또는 앱 상태 복원에 사용된다.
- [ ] 이전 앱 상태가 유효하면 대화 컨텍스트가 이어진다.
- [ ] 이전 앱 상태가 유효하지 않으면 새 LiveSession이 생성된다.
- [ ] 세션 부트스트랩 중 중복 생성이 방지된다.
- [ ] 세션 준비 실패 시 재시도 가능한 상태가 된다.
- [ ] 마이크 권한이 없어도 세션 상태 자체는 안전하게 유지된다.

---

# Flow (링크)

- SYS-REALTIME-INFRA
- RT-001 → 세션 부트스트랩

---

# 구현 범위

## 포함 범위

- activeSessionId 생성 / 복원 정책
- selectedLearningLanguage 기반 세션 scope 결정
- 앱 초기 상태 복구
- LiveSession 생성 중복 방지
- 세션 준비 실패 처리

## 제외 범위 (Out of Scope)

- 오디오 스트림 전송
- transcript 렌더링
- turn 확정 및 Session Memory append
- LangState 수치 계산

---

# Details

## activeSessionId 역할

`activeSessionId`는 현재 AI Chat이 바라보는 살아 있는 앱 레벨 세션 식별자다.

세션이 없으면 새로 만들고, 유효하면 재사용한다.

---

## 복원 정책

- 같은 학습 언어의 앱 상태가 남아 있으면 복원을 시도한다.
- 복원 실패 시 새 LiveSession을 생성한다.
- 복원과 생성은 동시에 실행되지 않는다.

## 예외 처리

- `activeSessionId`가 없으면 빈 상태로 새 시작을 허용한다.
- 앱 복원이 실패해도 화면 진입은 막지 않는다.
- 세션 초기화 실패는 재시도 가능한 상태로 남겨둔다.

## 데모 확인 포인트

1. AI Chat 진입 시 앱 상태가 준비되는지 확인
2. 이전 상태가 없을 때 새 LiveSession이 연결되는지 확인
3. 중복 세션 생성이 없는지 확인

## 권장 구현 경계

- `presentation/chat/ChatViewModel.kt`
- `domain/usecase/chat/StartSessionUseCase.kt`
- `domain/repository/ChatRepository.kt`
- `data/repository/ChatRepositoryImpl.kt`
- `di/AIModule.kt`

## 현재 테스트 코드 전환 메모

- `AIModule.kt`의 Firebase Live API 연결 설정은 유지한다.
- 하드코딩된 영어 튜터 system instruction은 이후 selectedLearningLanguage, LangState, Session Memory를 반영하는 prompt builder로 옮긴다.
- `ChatRepositoryImpl.startSession()`은 중복 연결을 막고, 실패 시 재시도 가능한 오류 이벤트를 내보내야 한다.
- `LiveSession`은 앱 장기 세션이 아니므로, 진입할 때 필요한 앱 상태를 복원한 뒤 새 연결을 여는 방식으로 다룬다.

## 한 줄 가이드

- 세션 키는 앱 상태로 관리하고, Firebase Live API 연결은 실행 시점에 새로 연다.

---

## 권장 구조

```text
com.example.umma
├── presentation/chat/
├── domain/model/realtime/
├── domain/repository/
├── domain/usecase/
├── data/repository/
├── data/source/local/
└── di/
```
