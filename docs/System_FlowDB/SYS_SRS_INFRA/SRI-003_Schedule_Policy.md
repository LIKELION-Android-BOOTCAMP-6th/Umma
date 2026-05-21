# [Infra] SRI-003 Review 스케줄 정책 및 완료 파이프라인

## User Story

SRS User Flow 작업자는 사용자가 SM-2 기반 4단계 평가 버튼을 눌렀을 때,
다음 복습 시점과 요약값이 어떤 순서와 정책으로 갱신되는지 명확히 알고 작업할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] SM-2 기반 4단계 복습 평가 정책이 `Again / Hard / Good / Easy`로 문서화된다.
- [ ] MVP에서는 SM-2 전체 알고리즘을 1:1로 복제하지 않고, `interval`, `easeFactor`, `nextReviewAt` 중심의 단순화 정책을 사용한다.
- [ ] `ReviewSchedulePolicy`가 `ReviewDecision`을 받아 `ReviewScheduleResult`를 계산한다.
- [ ] `interval`, `easeFactor`, `nextReviewAt` 갱신 기준이 확정된다.
- [ ] Flashcard review schedule local 갱신과 Summary 업데이트는 하나의 로컬 완료 파이프라인으로 묶는다.
- [ ] `FlashcardSummary.dueFlashcards`와 `DashSummary.dueFlashcards`는 같은 완료 흐름 안에서 함께 갱신한다.
- [ ] 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 남긴다.
- [ ] Firestore background sync 실패는 로컬 완료 실패로 보지 않고 pending sync로 관리한다.

---

## 구현 범위

### 포함 범위

- `ReviewSchedulePolicy`
- SM-2 기반 4단계 평가 정책
- `interval`, `easeFactor`, `nextReviewAt` 계산 기준
- local first 완료 파이프라인
- `FlashcardSummary` / `DashSummary` 갱신 경계
- pending sync / rollback 기준

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

`SRI-003`의 UseCase는 `SRI-002`에서 정의한 Repository review schedule 갱신 계약을 호출해 완료 파이프라인을 조립한다.

---

## 스케줄 정책

MVP에서는 SM-2를 기반으로 하되, Anki식 `Again / Hard / Good / Easy` 4단계 버튼으로 단순화한다.
이 정책은 SM-2의 핵심 값인 `interval`, `easeFactor`, `nextReviewAt`을 사용한다.
Anki 전체 알고리즘을 1:1로 복제하지 않고, MVP에 필요한 복습 간격 계산만 고정한다.

| 버튼 | 의미 | interval / easeFactor 갱신 | 다음 노출 |
| --- | --- | --- | --- |
| Again | 기억하지 못함 | interval = 0, easeFactor -0.20 | 당일 재노출 |
| Hard | 힘겹게 기억함 | max(1일, 현재 interval x 1.2), easeFactor -0.15 | 짧은 간격 후 재노출 |
| Good | 적절히 기억함 | max(1일, 현재 interval x easeFactor), easeFactor 유지 | 표준 간격 후 재노출 |
| Easy | 쉽게 기억함 | max(4일, 현재 interval x easeFactor x 1.3), easeFactor +0.15 | 더 긴 간격 후 재노출 |

신규 카드처럼 현재 interval이 없거나 0인 경우에는 기본 interval을 `1일`로 보고 `Hard / Good / Easy` 계산을 시작한다.
`easeFactor`, `interval`, `nextReviewAt`은 이 정책을 반영해 갱신한다.
세부 계산은 `ReviewSchedulePolicy` 또는 동등한 domain policy가 책임진다.

---

## 완료 파이프라인

```text
ReviewDecision
→ ReviewSchedulePolicy로 ReviewScheduleResult 계산
→ Flashcard schedule local update
→ FlashcardSummary / DashSummary due count 갱신
→ local commit marker 기록
→ Firestore background sync 예약
```

- review schedule local 갱신이 성공해야 사용자가 완료된 것으로 본다.
- Flashcard schedule update와 summary 갱신은 하나의 로컬 완료 단위로 묶는다.
- 로컬 완료 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 남긴다.
- Firestore background sync 실패는 로컬 완료 실패로 보지 않는다.
- sync 실패 카드는 pending sync 또는 dirty flag로 후속 재시도 대상에 남긴다.

---

## 상태 기준

- MVP에서는 AI가 발화를 자동 평가하지 않는다.
- 사용자가 직접 버튼을 눌러 기억 정도를 선택한다.
- 평가 결과가 반영된 카드는 다음 카드 진행 기준이 되며, `Again`은 당일 재노출을 위해 due 상태로 남을 수 있다.
- `nextReviewAt`이 지나지 않은 카드는 due deck에 포함하지 않는다.
- 현재 카드 review 결과 저장 후 Dashboard의 due count는 로컬 완료 결과를 기준으로 갱신된다.

---

## 예외 처리

- 중복 평가 요청은 하나의 card update로 합친다.
- 갱신 중 네트워크 실패가 나도 로컬 완료를 먼저 보존한다.
- review schedule local 갱신 실패 또는 summary 갱신 실패는 완료로 보지 않고 Retry 상태로 남긴다.
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
