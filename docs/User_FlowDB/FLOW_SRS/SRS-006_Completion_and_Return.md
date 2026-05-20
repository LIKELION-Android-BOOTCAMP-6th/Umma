# [Feature] SRS-006 완료, 복귀, 동기화

## User Story

사용자는 현재 카드의 review 결과 저장이 끝난 뒤 다음 카드 또는 완료 상태로 자연스럽게 이동하고,
필요하면 Dashboard로 자연스럽게 돌아갈 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 현재 카드 review 결과 저장 성공 이후 다음 카드 또는 완료 상태로 자연스럽게 넘어간다.
- [ ] 덱이 끝나면 완료 상태가 표시된다.
- [ ] review 결과 저장 완료 파이프라인에서 갱신된 FlashcardSummary와 DashSummary의 due 수치를 관찰한다.
- [ ] Firestore background sync 실패는 로컬 완료 실패로 보지 않는다.
- [ ] 재진입 시 현재 진행 상태가 안전하게 복원된다.

---

## Flow (링크)

- FLOW-SRS
- SRS-006 → 완료, 복귀, 동기화

---

## 구현 범위

### 포함 범위

- current card 저장 성공 이후 진행 상태 반영
- next card 이동
- 완료 상태
- summary observe
- background sync pending 상태 표시

### 제외 범위 (Out of Scope)

- 카드 앞/뒤 표시
- 발음 재생
- 평가 정책 상세
- 현재 카드 review 결과 저장
- Dashboard 카드 UI
- AI Chat / Correction 연동

---

## Details

## 완료 흐름

```text
현재 카드 review 결과 local first 저장 성공
→ FlashcardSummary / DashSummary 갱신
→ Firestore background sync pending 상태 관찰
→ 다음 카드로 이동
→ 덱 종료 시 완료 상태 표시
```

`dueFlashcards`는 현재 카드가 저장될 때마다 최신 상태로 다시 관찰 가능해야 한다.
Firestore background sync는 덱 종료를 기다리지 않고 review 결과 저장 완료 시점마다 후속 처리된다.
현재 카드의 평가 저장과 summary 갱신 파이프라인은 `SRS-005`가 호출하는 `SRI-003` 정책의 책임이며, 이 문서는 저장 성공 이후의 진행/완료/복귀 상태를 다룬다.

---

## 복귀 정책

- 사용자가 뒤로 가기를 누르면 현재 진행 상태는 깨지지 않아야 한다.
- 덱이 끝났을 때는 완료 메시지 또는 Dashboard 복귀 CTA를 보여줄 수 있다.
- 재진입 시 현재 선택 언어와 진행 중이던 세션 상태를 복원한다.

---

## 권장 구현 경계

- `domain/usecase/flashcardreview/CompleteReviewSessionUseCase.kt`
- `presentation/srsstudy/SrsStudyCompletionState.kt`
- `System_FlowDB/SYS_LEARNING_STATE_INFRA`의 summary observe 정책

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── domain/usecase/flashcardreview/
│   └── CompleteReviewSessionUseCase.kt
└── presentation/srsstudy/
    └── SrsStudyCompletionState.kt
```

> 완료 단계는 저장 성공 이후 진행 상태와 Dashboard 갱신 관찰을 함께 다룬다.
> 카드 학습이 끝났는데 summary가 늦게 갱신되면 사용자가 바로 혼란을 느낀다.

---

## Edge Cases

- 마지막 카드 review 결과 저장 직후 앱이 종료되는 경우
- 로컬 저장은 성공했지만 remote sync가 실패하는 경우
- Dashboard 요약 수치가 잠시 늦게 반영되는 경우
- 동일 카드에 대해 완료 요청이 중복으로 들어오는 경우
- 복귀 시 현재 카드 인덱스가 사라지는 경우
