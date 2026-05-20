# [Infra] SRI-003 Review 스케줄 정책 및 Summary 연동 경계

## User Story

SRS User Flow 작업자는 사용자가 SM-2 기반 4단계 평가 버튼을 눌렀을 때,
다음 복습 시점과 이후 요약 연동이 어떤 순서와 정책으로 이어지는지 명확히 알고 작업할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] SM-2 기반 4단계 복습 평가 정책이 `Again / Hard / Good / Easy`로 문서화된다.
- [ ] MVP에서는 SM-2 전체 알고리즘을 1:1로 복제하지 않고, `interval`, `easeFactor`, `nextReviewAt` 중심의 단순화 정책을 사용한다.
- [ ] `ReviewSchedulePolicy`가 `ReviewDecision`을 받아 `ReviewScheduleResult`를 계산한다.
- [ ] `interval`, `easeFactor`, `nextReviewAt` 갱신 기준이 확정된다.
- [ ] Flashcard review schedule local 갱신과 Summary 연동 지점은 분리해서 다룬다.
- [ ] `FlashcardSummary.dueFlashcards`와 `DashSummary.dueFlashcards`는 후속 LearningState 연동에서 함께 반영한다.

---

## 구현 범위

### 포함 범위

- `ReviewSchedulePolicy`
- SM-2 기반 4단계 평가 정책
- `interval`, `easeFactor`, `nextReviewAt` 계산 기준
- local first review schedule 갱신
- `FlashcardSummary` / `DashSummary` 후속 연동 경계

### 제외 범위

- Flashcard 모델 필드 설계
- Repository interface 세부 정의
- 카드 UI 구현
- 발음 재생 동작
- Dashboard 카드 렌더링
- 통계 그래프 구현

---

## 권장 파일/패키지 방향

```text
domain/usecase/flashcardreview
→ ReviewSchedulePolicy
→ ApplyReviewDecisionUseCase
→ CompleteReviewSessionUseCase
```

`SRI-003`의 UseCase는 `SRI-002`에서 정의한 Repository review schedule 갱신 계약을 호출해 review 단계만 완결한다.
summary 반영은 `SYS-LEARNING-STATE-INFRA`의 후속 연동으로 분리한다.

---

## 스케줄 정책

MVP에서는 SM-2를 기반으로 하되, Anki식 `Again / Hard / Good / Easy` 4단계 버튼으로 단순화한다.
이 정책은 SM-2의 핵심 값인 `interval`(단위: 분), `easeFactor`, `nextReviewAt`(밀리초 타임스탬프)을 사용한다.
Anki 전체 알고리즘을 1:1로 복제하지 않고, MVP에 필요한 복습 간격 계산만 고정한다.

| 버튼 | 의미 | interval / easeFactor 갱신 | 다음 노출 |
| --- | --- | --- | --- |
| Again | 기억하지 못함 | interval = 0, easeFactor -0.20 | 세션 내 즉시/지연 재노출 및 당일 재노출 |
| Hard | 힘겹게 기억함 | max(1440, 현재 interval x 1.2), easeFactor -0.15 | 약 1일 후 (1440분) |
| Good | 적절히 기억함 | max(1440, 현재 interval x easeFactor), easeFactor 유지 | 표준 간격 후 재노출 |
| Easy | 쉽게 기억함 | max(5760, 현재 interval x easeFactor x 1.3), easeFactor +0.15 | 약 4일 후 (5760분) |

- **Again 처리**: `Again` 선택 시 해당 카드는 즉시 다시 봐야 하는 카드로 취급한다. 세션 내 재등장 순서는 UI/ViewModel이 관리한다.
- **단위**: 모든 `interval` 계산은 '분(Minutes)' 단위를 기본으로 하며, `nextReviewAt`은 현재 시각에 해당 분을 더한 밀리초 타임스탬프로 저장한다.
- 신규 카드처럼 현재 interval이 없거나 0인 경우에는 기본 interval을 `1440분`(1일)으로 보고 `Hard / Good / Easy` 계산을 시작한다.

---

## Review Schedule 갱신 흐름

```text
ReviewDecision
→ ReviewSchedulePolicy로 ReviewScheduleResult 계산
→ [ApplyReviewDecisionUseCase] 호출
  1. FlashcardRepository.updateFlashcardSchedule 호출 (카드 원본 갱신)
  2. summary 반영은 후속 LearningState 연동 지점으로 분리
```

- **책임 경계**: `FlashcardRepository`는 카드 원본 데이터의 정합성만 책임지며, 요약 정보(Summary) 갱신은 후속 `LearningState` 연동에서 처리한다.
- review schedule local 갱신이 성공해야 사용자가 완료된 것으로 본다.
- review schedule local 갱신 실패는 `ApplyReviewDecisionUseCase`가 실패 결과로 반환하고, Retry 화면 상태는 User Flow의 UI/ViewModel에서 처리한다.

---

## 상태 기준

- MVP에서는 AI가 발화를 자동 평가하지 않는다.
- 사용자가 직접 버튼을 눌러 기억 정도를 선택한다.
- 평가 결과가 반영된 카드는 다음 카드 진행 기준이 되며, `Again`은 당일 재노출을 위해 due 상태로 남을 수 있다.
- `nextReviewAt`이 지나지 않은 카드는 due deck에 포함하지 않는다.
- 현재 카드 review 결과 저장 후 Dashboard의 due count는 이후 summary 연동 결과를 기준으로 갱신된다.

---

## 예외 처리

- 중복 평가 요청은 하나의 card update로 합친다.
- review schedule local 갱신 실패는 완료로 보지 않고 실패 결과로 반환한다.
- summary 연동 실패는 `SYS-LEARNING-STATE-INFRA`의 후속 처리에서 별도 상태로 다룬다.
- 앱 종료 중 평가가 진행 중이면 commit marker 기준으로 완료/미완료를 복구한다.
- 삭제되었거나 동기화 충돌이 있는 카드는 최신 저장값을 우선한다.

---

## 연결 문서

- [SYS_SRS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA.md)
- [SRI-001_Flashcard_Model.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-001_Flashcard_Model.md)
- [SRI-002_Repository_Contract.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-002_Repository_Contract.md)
- [FLOW_SRS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS.md)
- [SRS-005_Grading_and_Schedule.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-005_Grading_and_Schedule.md)
- [SRS-006_Completion_and_Return.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-006_Completion_and_Return.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
