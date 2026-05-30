# [Feature] SRS-001 반복학습 진입 및 언어 컨텍스트

## User Story

사용자는 SrsStudy 화면에 진입했을 때,
현재 선택 언어 기준의 복습 컨텍스트를 자연스럽게 이어받을 수 있다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] SrsStudy route로 진입했을 때 화면 초기화가 시작된다.
- [ ] `selectedLearningLanguage`가 SrsStudy 초기 언어 컨텍스트에 반영된다.
- [ ] 진입 중 중복 Navigation이 방지된다.
- [ ] 언어 정보가 없거나 초기 상태가 비어 있어도 화면은 안전하게 진입한다.
- [ ] 초기 상태 로드 실패 시 재시도 가능한 상태가 된다.
- [ ] 복습 카드 수는 표시용 요약값으로만 사용되고, 실제 deck source of truth로는 사용하지 않는다.

---

## Flow (링크)

- FLOW-SRS
- DASH-004 → Flashcard 학습 카드
- SRS-001 → 반복학습 진입 및 언어 컨텍스트

---

## 구현 범위

### 포함 범위

- SrsStudy 진입 route 연결
- 현재 선택 언어 전달
- 진입 중 Loading / Error 상태
- 중복 Navigation 방지
- SrsStudy route entry의 공통 초기화

### 제외 범위 (Out of Scope)

- 카드 목록 조회
- 카드 앞/뒤 표시
- 발음 재생
- 복습 평가 및 스케줄 계산
- review 결과 저장

---

## Details

## 진입 기준

SrsStudy 화면은 항상 현재 선택 언어 기준으로 시작한다.

```text
GlobalLangState
→ selectedLearningLanguage
→ SrsStudy 초기 언어 컨텍스트
```

Dashboard의 `dueFlashcards`는 진입 힌트일 뿐, deck 자체를 대체하지 않는다.

---

## 진입 경로

- SrsStudy route 진입

Dashboard 카드 클릭 처리는 Dashboard Flow의 책임이며,
이 이슈는 SrsStudy route에 도착한 뒤의 초기화 규칙만 다룬다.

---

## 권장 구현 경계

- `presentation/srsstudy/SrsStudyScreen.kt`
- `presentation/srsstudy/SrsStudyViewModel.kt`
- `domain/usecase/flashcardreview/StartReviewSessionUseCase.kt`
- `core/navigation/Route.kt`

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/srsstudy/
│   ├── SrsStudyScreen.kt
│   └── SrsStudyViewModel.kt
├── domain/usecase/flashcardreview/
│   └── StartReviewSessionUseCase.kt
└── core/navigation/
    └── Route.kt
```

> SrsStudy 진입은 화면 렌더링보다 먼저 언어 컨텍스트를 고정하는 것이 중요하다.
> 그렇지 않으면 뒤 단계에서 카드 deck과 요약 수치가 쉽게 어긋난다.
> 실제 due deck 조회는 `SRS-002`에서 `SRI-002`의 Repository 계약을 기준으로 처리한다.

---

## Edge Cases

- `selectedLearningLanguage`가 없는 경우
- Dashboard 카드 수와 실제 deck 수가 잠시 다른 상태에서 진입하는 경우
- 화면을 연속 탭해서 중복 진입이 발생하는 경우
- 앱이 백그라운드로 갔다가 돌아오는 경우
