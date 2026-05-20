# [Feature] SRS-002 복습 카드 덱 로드

## User Story

사용자는 현재 선택 언어의 due Flashcard 목록을 받아,
실제로 반복학습할 카드 덱을 먼저 안정적으로 로드할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 현재 선택 언어의 due Flashcard만 로드된다.
- [ ] 복습 카드 덱은 Flashcard 원본을 기준으로 구성된다.
- [ ] due 카드가 없으면 Empty 상태가 표시된다.
- [ ] deck 로드 실패 시 재시도 가능한 상태가 된다.
- [ ] deck 순서는 매번 같은 기준으로 정렬된다.
- [ ] deck 조회 중에는 UI가 안전한 Loading 상태를 유지한다.

---

## Flow (링크)

- FLOW-SRS
- SRS-002 → 복습 카드 덱 로드

---

## 구현 범위

### 포함 범위

- due Flashcard 조회
- deck 정렬 정책
- Empty / Loading / Retry 처리
- selectedLearningLanguage 기준 deck 분리
- `SRI-002`의 `FlashcardRepository` 조회 계약 연결

### 제외 범위 (Out of Scope)

- 카드 flip
- 발음 재생
- 평가 버튼
- 스케줄 계산 상세
- review 결과 저장
- Dashboard 카드 렌더링

---

## Details

## deck source of truth

복습 덱의 source of truth는 `DashSummary.dueFlashcards`가 아니라 Flashcard 원본이다.

```text
Flashcard
→ language
→ nextReviewAt
→ due deck
```

`dueFlashcards`는 사용자가 대시보드에서 상태를 가늠할 수 있게 해주는 요약값이다.

---

## 조회 정책

- `nextReviewAt <= now`인 카드만 due deck에 포함한다.
- 현재 선택 언어의 카드만 조회한다.
- deck은 Room 또는 local cache를 먼저 사용하고, 필요 시 background sync로 보정한다.
- 조회 계약과 fake/real 교체 기준은 `SRI-002`를 따른다.

---

## 권장 구현 경계

- `domain/usecase/flashcardreview/ObserveReviewDeckUseCase.kt`
- `domain/repository/FlashcardRepository.kt`
- `data/repository/FlashcardRepositoryImpl.kt`
- `presentation/srsstudy/SrsStudyDeckView.kt`

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── domain/usecase/flashcardreview/
│   └── ObserveReviewDeckUseCase.kt
├── domain/model/flashcard/
│   └── Flashcard.kt
└── data/repository/
    └── FlashcardRepositoryImpl.kt
```

> deck은 가능한 한 미리 정렬된 상태로 ViewModel에 전달한다.
> 화면에서 카드 순서를 다시 계산하지 않도록 경계를 두는 편이 유지보수에 유리하다.

---

## Edge Cases

- due 카드가 하나도 없는 경우
- 저장된 카드가 sync 중에 삭제되거나 수정된 경우
- 로컬 cache와 remote sync 값이 잠시 다른 경우
- 카드 정렬 기준이 바뀌어도 같은 카드가 중복 노출되지 않도록 해야 하는 경우
