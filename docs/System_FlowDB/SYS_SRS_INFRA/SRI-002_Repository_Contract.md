# [Infra] SRI-002 Flashcard Repository 조회/복습 결과 갱신 계약

## User Story

SRS User Flow 작업자는 반복학습 화면에서 due card를 조회하고 평가 결과를 저장할 때,
fake와 real 구현이 같은 `FlashcardRepository` 계약을 사용한다는 점을 신뢰할 수 있어야 한다.
여기서 저장은 새 Flashcard 생성이 아니라, 이미 저장된 Flashcard의 복습 결과와 schedule 갱신을 의미한다.
Correction 쪽에서 새 Flashcard 최초 저장에 사용하는 Room 원본은 이미 `SYS-CORRECTION-INFRA`가 제공한다.
SRI-002에서는 같은 Flashcard 원본을 조회하고 review schedule을 갱신하는 `FlashcardRepository` 계약을 준비하는 것을 목표로 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `FlashcardRepository`가 due card 조회 결과를 `ReviewDeckState`로 노출하고 review 결과 schedule 갱신 요청을 함께 다루는 계약으로 준비된다.
- [ ] Correction이 저장한 Flashcard Room 원본을 조회/갱신하는 repository 계약이 준비된다.
- [ ] 복습 대상 카드의 source of truth가 `nextReviewAt` 기반 Flashcard 원본임을 확정한다.
- [ ] `FlashcardSummary.dueFlashcards`는 Dashboard 표시용 요약값임을 확정한다.
- [ ] 현재 선택 언어의 Flashcard deck만 조회한다.
- [ ] Repository 갱신 응답은 local 갱신 성공과 background sync pending 상태를 구분할 수 있어야 한다.
- [ ] mock/real 교체가 Hilt binding 기준으로 가능해야 한다.
- [ ] fake repository는 due deck, empty, retry, sync pending 시나리오를 재현할 수 있어야 한다.

---

## 구현 범위

### 포함 범위

- `domain/repository/FlashcardRepository`
- `domain/model/flashcard/ReviewDeckState`
- Correction-infra가 제공한 Flashcard Room 원본 조회/갱신 계약
- due deck 조회 계약
- review 결과 schedule 갱신 요청 계약
- fake repository / real repository 교체 기준
- Hilt binding 기준
- Empty / Retry / PendingSync 테스트 시나리오

### 제외 범위

- `Flashcard` 모델 필드 설계
- Correction 교정 결과에서 새 Flashcard를 최초 생성/저장하는 흐름
- SM-2 기반 4단계 스케줄 계산 공식
- 카드 UI 구현
- 발음 재생 동작
- LangState 업데이트
- AI Chat / Correction 기능

---

## 권장 파일/패키지 방향

```text
domain/repository
→ FlashcardRepository

domain/usecase/flashcardreview
→ ObserveReviewDeckUseCase
→ SaveReviewResultUseCase

data/repository
→ FlashcardRepositoryImpl
→ FakeFlashcardRepository

data/source/local
→ Correction이 제공한 Flashcard Room 원본 DAO/Entity 재사용
→ SRS 조회/갱신 adapter 또는 local data source

di
→ FlashcardRepository binding
```

SRI-002에서 같은 필드의 `FlashcardDao` / `FlashcardEntity`를 새로 만들지 않는다.
새 Flashcard 최초 저장용 Room 원본은 `SYS-CORRECTION-INFRA`가 제공하며,
SRS는 그 원본을 읽고 schedule/review 필드만 갱신하는 경계를 가진다.
`SYS-CORRECTION-INFRA`가 제공한 local source의 due 조회와 review schedule 갱신 통로를 사용해 `FlashcardRepository`를 구현한다.

---

## 핵심 계약

### 1. 덱 조회 계약

```text
selectedLearningLanguage
→ Correction-infra가 저장한 Room Flashcard 원본 조회
→ language-scoped Flashcard deck 조회
→ nextReviewAt <= now 카드만 due deck 포함
→ ReviewDeckState.Content / Empty / Retry / Error
```

- 복습 덱은 Dashboard summary 숫자가 아니라 Flashcard 원본에서 계산한다.
- `dueFlashcards`는 표시용 결과이며, 카드 순서의 source of truth가 아니다.
- Repository는 현재 선택 언어 밖의 카드를 섞어 반환하지 않는다.
- Flashcard Room Entity / DAO의 최초 저장 책임은 `SYS-CORRECTION-INFRA`에 둔다.
- SRI-002 작업에서는 같은 필드 의미를 가진 Room 원본을 조회해 SRS deck과 review update에 사용한다.
- due deck 조회는 `userId`, `language`, `nextReviewAt <= now` 기준으로 수행한다.
- 정렬은 `nextReviewAt`, `createdAt`, `id` 기준으로 고정해 재진입 시 순서가 흔들리지 않게 한다.
- Loading 같은 화면 상태는 저장소 계약이 아니라 ViewModel/UI가 관리한다.

### 2. 복습 결과 schedule 갱신 요청 계약

```text
ReviewDecision
→ ReviewScheduleResult
→ 기존 Flashcard schedule/review 상태 update 요청
→ local first 갱신
→ local update 결과 반환
→ sync pending 여부 반환
```

- Repository는 저장과 외부 통신을 담당한다.
- 이 저장은 Correction에서 생성한 새 Flashcard를 최초 저장하는 작업이 아니라, 이미 저장된 Flashcard의 복습 schedule을 갱신하는 작업이다.
- 스케줄 계산 공식은 Repository가 직접 결정하지 않는다.
- SM-2 기반 스케줄 계산은 `SRI-003`의 `ReviewSchedulePolicy`가 책임진다.
- Repository는 local 갱신 결과와 sync pending 여부만 반환하고, Retry 화면 상태나 세션 진행 제어는 User Flow의 UI/ViewModel에서 처리한다.
- Repository 응답은 local 갱신 성공 여부와 sync pending 여부를 분리해서 표현한다.
- SRS가 schedule을 갱신한 카드는 Firestore sync 전까지 dirty 상태로 남겨 후속 sync 대상임을 표시한다.

### 3. mock/real 계약

- UI와 ViewModel은 `FlashcardRepository` interface만 바라본다.
- fake 구현체는 실제 화면 작업자가 빠르게 조회/갱신 상태를 재현할 수 있어야 한다.
- real 구현체는 Room 우선, Firestore background sync 후처리 구조를 따른다.
- prompt builder 같은 AI 계층은 이 흐름에 들어오지 않는다.
- 새 Flashcard 생성 저장은 `SYS-CORRECTION-INFRA`의 `CorrectionRepository.saveFlashcards(...)` 계약을 따른다.
- SRS는 새 카드 최초 저장을 다시 구현하지 않고, 저장된 원본의 schedule/review 상태만 갱신한다.

---

## 상태 기준

- due deck이 비어 있으면 `ReviewDeckState.Empty`를 반환한다.
- 조회 실패는 `ReviewDeckState.Retry` 또는 `ReviewDeckState.Error`로 반환한다.
- 갱신 실패는 Retry 가능한 상태로 반환한다.
- Firestore sync 실패는 Repository 응답에서 sync pending 상태로 표현한다.
- `FlashcardSummary`와 `DashSummary` 반영은 `SRI-003`의 후속 연동 기준을 따른다.

---

## 예외 처리

- due deck 조회 실패 시 Error 또는 Retry 상태를 반환한다.
- 중복 review 갱신 요청은 같은 card update로 합칠 수 있어야 한다.
- 갱신 중 네트워크 실패가 나도 local 갱신 성공 여부와 sync pending 상태를 분리해서 반환한다.
- 동기화 충돌이 있는 카드는 최신 local 또는 server 정책에 따라 하나의 저장값으로 정리한다.

---

## 연결 문서

- [SYS_SRS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA.md)
- [SRI-001_Flashcard_Model.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-001_Flashcard_Model.md)
- [SRI-003_Schedule_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-003_Schedule_Policy.md)
- [FLOW_SRS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)
