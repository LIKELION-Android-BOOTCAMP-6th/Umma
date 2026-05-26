# [LS-009] Correction / Dashboard 담당자 전달 사항

> 대상: Correction / Dashboard 후속 작업자
> 관련 이슈: #150 (`LS-009 AI Chat turn 확정 후 correctionAvailable 시그널 전파`)
> 작성자: `SYS-LEARNING-STATE-INFRA` 작업자

---

## 1. 요약

AI Chat의 `USER final turn` 이후 교정 가능 신호를 LearningState가 받을 수 있는 경로가 준비되었다.

LS-009 이후에는 같은 신호가 두 summary에 함께 반영된다.

| 소비자 | 읽을 값 |
| --- | --- |
| Correction | `SessionSummary.correctionAvailable` |
| Dashboard | `DashSummary.correctionAvailable` |

Correction과 Dashboard는 이 값을 직접 계산하지 않고 기존처럼 각자 summary를 소비하면 된다.

---

## 2. Correction 담당자 기준

Correction 진입 가능 여부는 계속 `SessionSummary.correctionAvailable`만 기준으로 판단한다.

```text
SessionSummary.correctionAvailable == true
→ Ready
→ 자동 교정 생성 흐름 진입

SessionSummary.correctionAvailable == false
→ Empty / NotAvailable
```

`DashSummary.correctionAvailable`은 Dashboard 표시용 값이므로 Correction Ready 판정에 사용하지 않는다.

교정 완료 후에는 기존 완료 파이프라인에서 `correctionAvailableOverride = false`를 넘겨 `SessionSummary`와 `DashSummary`가 함께 내려가도록 유지한다.

---

## 3. Dashboard 담당자 기준

Dashboard 교정 대기 카드는 계속 `DashSummary.correctionAvailable`을 읽어 표시한다.

```text
DashSummary.correctionAvailable == true
→ 교정 대기 표시 / 빨간 점 표시

DashSummary.correctionAvailable == false
→ 교정 대기 없음
```

Dashboard는 Session Memory나 Correction context를 조회해서 교정 가능 여부를 다시 계산하지 않는다.
LearningState가 제공하는 `DashSummary`를 화면 표시용 source로 사용한다.

---

## 4. 기대 흐름

```text
AI Chat USER final turn 저장
→ LS correction signal update
→ SessionSummary.correctionAvailable = true
→ DashSummary.correctionAvailable = true
→ Dashboard 빨간 점 표시
→ Correction 진입 시 Ready 판정
→ 교정 완료 후 correctionAvailable = false
```

LS-009는 `correctionAvailable=true`로 올리는 신호 경로다.
`false`로 내리는 책임은 Correction 완료 파이프라인에 남아 있다.

---

## 5. 책임 경계

Correction / Dashboard 쪽에서 하지 않아야 할 일:

- AI Chat turn 저장 시점에 직접 LS 신호 호출
- `SessionSummary`와 `DashSummary`를 임의로 동기화
- Dashboard에서 `SessionSummary`를 기준으로 빨간 점 계산
- Correction에서 `DashSummary`를 기준으로 Ready 판정

Correction / Dashboard 쪽에서 확인할 일:

- 각 화면이 올바른 summary를 읽고 있는지
- 교정 완료 후 두 summary의 `correctionAvailable`이 다시 false로 내려가는지

Chat/Realtime 연결이 머지된 뒤 함께 확인할 통합 항목:

- 실기기에서 AI Chat 1턴 이상 후 Dashboard 빨간 점과 Correction Ready가 열리는지

---

## 6. 참고 구현

- `CorrectionSignalUpdateResult`: `app/src/main/java/com/app/umma/domain/model/learningstate/LearningUpdateModels.kt`
- `LearningStateRepoImpl.updateCorrectionSignal`: `app/src/main/java/com/app/umma/data/repository/LearningStateRepoImpl.kt`
- Correction 기준 문서: `docs/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md`
- Dashboard 기준 문서: `docs/User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md`
