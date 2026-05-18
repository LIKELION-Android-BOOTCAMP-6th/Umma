# [Infra] SRI-002 Flashcard Repository 조회/저장 계약

## User Story

SRS User Flow 작업자는 반복학습 화면에서 due card를 조회하고 평가 결과를 저장할 때,
fake와 real 구현이 같은 `FlashcardRepository` 계약을 사용한다는 점을 신뢰할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `FlashcardRepository`가 due card 조회와 review 결과 저장 요청을 함께 다루는 계약으로 준비된다.
- [ ] 복습 대상 카드의 source of truth가 `next_review_at` 기반 Flashcard 원본임을 확정한다.
- [ ] `FlashcardSummary.dueFlashcards`는 Dashboard 표시용 요약값임을 확정한다.
- [ ] 현재 선택 언어의 Flashcard deck만 조회한다.
- [ ] Repository 저장 응답은 local 저장 성공과 background sync pending 상태를 구분할 수 있어야 한다.
- [ ] mock/real 교체가 Hilt binding 기준으로 가능해야 한다.
- [ ] fake repository는 due deck, empty, retry, sync pending 시나리오를 재현할 수 있어야 한다.

---

## 구현 범위

### 포함 범위

- `domain/repository/FlashcardRepository`
- due deck 조회 계약
- review 결과 저장 요청 계약
- fake repository / real repository 교체 기준
- Hilt binding 기준
- Empty / Retry / PendingSync 테스트 시나리오

### 제외 범위

- `Flashcard` 모델 필드 설계
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

di
→ FlashcardRepository binding
```

---

## 핵심 계약

### 1. 덱 조회 계약

```text
selectedLearningLanguage
→ language-scoped Flashcard deck 조회
→ next_review_at <= now 카드만 due deck 포함
→ 복습 순서 정렬
```

- 복습 덱은 Dashboard summary 숫자가 아니라 Flashcard 원본에서 계산한다.
- `dueFlashcards`는 표시용 결과이며, 카드 순서의 source of truth가 아니다.
- Repository는 현재 선택 언어 밖의 카드를 섞어 반환하지 않는다.

### 2. 저장 요청 계약

```text
ReviewDecision
→ ReviewScheduleResult
→ Flashcard schedule update 요청
→ local first 저장
→ local save 결과 반환
→ sync pending 여부 반환
```

- Repository는 저장과 외부 통신을 담당한다.
- 스케줄 계산 공식은 Repository가 직접 결정하지 않는다.
- SM-2 기반 스케줄 계산은 `SRI-003`의 `ReviewSchedulePolicy`가 책임진다.
- 로컬 완료 파이프라인의 순서와 rollback 기준은 `SRI-003`에서 결정한다.
- Repository 응답은 local 저장 성공 여부와 sync pending 여부를 분리해서 표현한다.

### 3. mock/real 계약

- UI와 ViewModel은 `FlashcardRepository` interface만 바라본다.
- fake 구현체는 실제 화면 작업자가 빠르게 조회/저장 상태를 재현할 수 있어야 한다.
- real 구현체는 Room 우선, Firestore background sync 후처리 구조를 따른다.
- prompt builder 같은 AI 계층은 이 흐름에 들어오지 않는다.

---

## 상태 기준

- due deck이 비어 있으면 Empty 상태를 반환한다.
- 저장 실패는 Retry 가능한 상태로 반환한다.
- Firestore sync 실패는 Repository 응답에서 sync pending 상태로 표현한다.
- `FlashcardSummary`와 `DashSummary` 갱신은 `SRI-003`의 완료 파이프라인 기준을 따른다.

---

## 예외 처리

- due deck 조회 실패 시 Error 또는 Retry 상태를 반환한다.
- 중복 저장 요청은 같은 card update로 합칠 수 있어야 한다.
- 저장 중 네트워크 실패가 나도 local 저장 성공 여부와 sync pending 상태를 분리해서 반환한다.
- 동기화 충돌이 있는 카드는 최신 local 또는 server 정책에 따라 하나의 저장값으로 정리한다.

---

## 연결 문서

- [SYS_SRS_INFRA.md](../SYS_SRS_INFRA.md)
- [SRI-001_Flashcard_Model.md](./SRI-001_Flashcard_Model.md)
- [SRI-003_Schedule_Policy.md](./SRI-003_Schedule_Policy.md)
- [FLOW_SRS.md](../../User_FlowDB/FLOW_SRS.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](../SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](../../User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)
