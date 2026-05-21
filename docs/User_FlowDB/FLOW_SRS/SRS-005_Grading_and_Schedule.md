# [Feature] SRS-005 복습 평가 및 스케줄 반영

## User Story

사용자는 Flashcard를 보고 난 뒤 자신의 기억 정도를 평가하고,
시스템은 그 결과를 바탕으로 다음 복습 시점을 조정해야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] SM-2 기반 4단계 평가 버튼이 제공된다.
- [ ] 버튼 의미가 `Again / Hard / Good / Easy`로 고정된다.
- [ ] 사용자는 한 번에 하나의 평가만 선택할 수 있다.
- [ ] 평가 결과에 따라 `interval`, `easeFactor`, `nextReviewAt`이 갱신된다.
- [ ] 현재 카드의 review 결과가 local first로 저장된다.
- [ ] 평가 중 중복 입력이 방지된다.
- [ ] review 결과 저장 실패 시 재시도 가능한 상태가 된다.
- [ ] `Again` 선택 시 세션 내 재등장은 UI/ViewModel이 관리한다.

---

## Flow (링크)

- FLOW-SRS
- SRS-005 → 복습 평가 및 스케줄 반영

---

## 구현 범위

### 포함 범위

- SM-2 기반 4단계 복습 버튼 UI
- review decision 생성
- SRS 스케줄 계산
- 현재 카드 review 결과 local first 저장
- 저장 성공/실패 상태 처리

### 제외 범위 (Out of Scope)

- 카드 앞/뒤 렌더링
- 발음 재생
- deck 로드
- 다음 카드 이동 / 덱 완료 화면
- Dashboard summary 표시 갱신
- Dashboard 카드 렌더링
- AI 자동 평가

---

## Details

## 평가 정책

MVP에서는 SM-2를 기반으로 하되, Anki식 `Again / Hard / Good / Easy` 4단계 버튼으로 단순화한다.
이 이슈는 별도의 스케줄 공식을 새로 만들지 않고 `SRI-003`의 `ReviewSchedulePolicy`를 적용한다.

| 버튼 | 의미 | interval / easeFactor 갱신 | 다음 노출 |
| --- | --- | --- | --- |
| Again | 기억하지 못함 | interval = 0, easeFactor -0.20 | 당일 재노출 |
| Hard | 힘겹게 기억함 | max(1일, 현재 interval x 1.2), easeFactor -0.15 | 짧은 간격 후 재노출 |
| Good | 적절히 기억함 | max(1일, 현재 interval x easeFactor), easeFactor 유지 | 표준 간격 후 재노출 |
| Easy | 쉽게 기억함 | max(4일, 현재 interval x easeFactor x 1.3), easeFactor +0.15 | 더 긴 간격 후 재노출 |

---

## 스케줄 정책

- `ReviewDecision`은 현재 카드와 버튼 선택을 연결한다.
- `ReviewSchedulePolicy`는 SM-2 기반으로 interval(분 단위)과 차기 복습 시점(밀리초)을 계산한다.
- `easeFactor`는 반복 성과에 맞게 갱신한다.
- **Again 처리**: `Again` 선택 시 해당 카드는 즉시 다시 봐야 하는 카드로 취급한다. 세션 내 재등장 순서는 UI/ViewModel이 관리한다.
- 신규 카드처럼 현재 interval이 없거나 0인 경우에는 기본 interval을 `1440분`으로 보고 `Hard / Good / Easy` 계산을 시작한다.
- 계산 결과는 `ApplyReviewDecisionUseCase`를 통해 카드 원본에 반영된다.
- summary 반영은 `ApplyReviewDecisionUseCase`가 LS의 `ApplyFlashcardSummaryUpdateUseCase`를 호출해 함께 조율한다.
- review 결과 local 갱신과 sync pending 응답 계약은 `SRI-002`를 따르고, 스케줄 계산 정책은 `SRI-003`을 따른다.
- 저장 성공 이후 다음 카드 이동과 덱 완료 상태는 `SRS-006`에서 처리한다.

---

## 권장 구현 경계

- `domain/model/flashcard/ReviewDecision.kt`
- `domain/usecase/flashcardreview/ApplyReviewDecisionUseCase.kt`
- `domain/usecase/flashcardreview/ReviewSchedulePolicy.kt`
- `domain/usecase/flashcardreview/SaveReviewResultUseCase.kt`
- `presentation/srsstudy/components/SrsStudyGradeButtons.kt`

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── domain/model/flashcard/
│   ├── ReviewDecision.kt
│   └── ReviewScheduleResult.kt
├── domain/usecase/flashcardreview/
│   ├── ApplyReviewDecisionUseCase.kt
│   ├── ReviewSchedulePolicy.kt
│   └── SaveReviewResultUseCase.kt
└── presentation/srsstudy/components/
    └── SrsStudyGradeButtons.kt
```

> grade 버튼은 UI 입력을 domain 정책으로 바꾸는 마지막 관문이다.
> 여기서 스케줄 규칙을 직접 하드코딩하지 않으면 이후 조정이 쉬워진다.

---

## Edge Cases

- 사용자가 카드 뒤를 보기 전에 평가 버튼을 누르려는 경우
- 같은 버튼을 연속으로 누르는 경우
- 현재 카드와 다른 `flashcardId`의 평가 요청이 들어오는 경우
- 현재 interval 값이 아직 없는 새 카드인 경우
- 저장 도중 화면이 사라지는 경우
- 네트워크 실패로 remote sync가 지연되는 경우
