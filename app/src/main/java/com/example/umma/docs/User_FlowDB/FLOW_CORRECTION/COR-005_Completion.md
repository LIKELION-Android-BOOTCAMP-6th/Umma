# [Feature] COR-005 교정 완료 정리

## User Story

사용자는 저장할 Flashcard 항목을 선택한 뒤 교정 완료 파이프라인을 실행하고,
Dashboard로 돌아갔을 때 교정 대기 상태가 최신 상태로 반영되기를 기대한다.

---

## 완료 기준(AC)

- [ ] 저장할 Flashcard 항목이 선택된 이후에만 교정 완료 처리를 시작한다.
- [ ] 완료 시 LangState 업데이트 입력을 생성하고 `LS-006` 정책에 맞춰 적용한다.
- [ ] 완료 시 Flashcard local first 저장이 실행된다.
- [ ] 완료 시 Session Memory 압축 요청이 실행된다.
- [ ] 완료 시 `SessionSummary.correctionAvailable`을 false로 갱신한다.
- [ ] 같은 완료 흐름 안에서 `DashSummary.correctionAvailable`도 함께 반영한다.
- [ ] Flashcard 저장, LangState 업데이트, Session Memory 압축, Summary 갱신은 하나의 로컬 완료 파이프라인으로 처리한다.
- [ ] 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 남긴다.
- [ ] Firestore sync 실패는 로컬 완료 실패로 보지 않고 pending sync 상태로 남긴다.
- [ ] 완료 후 Dashboard로 안전하게 복귀할 수 있다.
- [ ] 완료 처리 중 중복 클릭과 중복 완료 요청이 방지된다.

---

## 구현 범위

### 포함

- CompleteCorrectionSessionUseCase
- LangState 업데이트 입력 생성 및 적용
- Flashcard local first 저장
- Session Memory 압축 요청
- `SessionSummary.correctionAvailable` 갱신
- `DashSummary.correctionAvailable` 동시 반영
- 로컬 완료 파이프라인 rollback / retry 처리
- Firestore pending sync 상태 처리
- 완료 중 Loading 상태
- 완료 실패 Retry 상태
- Dashboard 복귀 처리
- 중복 완료 요청 방지

### 제외

- Session Memory append
- LangState 수치 계산 공식
- Dashboard 카드 UI 자체 구현

---

## 완료 순서

```text
저장할 CorrectionSuggestion 선택 완료
→ CompleteCorrectionSessionUseCase 호출
→ LangState 업데이트 입력 생성 및 적용
→ Flashcard local first 저장
→ Session Memory 압축 요청
→ SessionSummary.correctionAvailable false 갱신
→ DashSummary.correctionAvailable 동시 반영
→ Firestore background sync 예약
→ Dashboard 복귀
```

교정 화면을 단순히 이탈한 것만으로 완료 처리하지 않는다.
LangState 업데이트는 Session Memory 압축과 초기화보다 먼저 수행한다.
압축으로 인해 `recentFullContext`가 비워진 뒤 분석 입력이 사라지는 상황을 막기 위해서다.

로컬 완료 파이프라인은 all-or-nothing으로 처리한다.
Flashcard 저장 이후 단계에서 예외가 발생하면 Flashcard 저장까지 포함해 로컬 변경을 롤백하고 Retry 상태를 유지한다.
저장소가 하나의 transaction으로 묶이지 않는 경우 보상 rollback 또는 commit marker 방식으로 부분 완료 상태를 남기지 않는다.
Firestore background sync는 로컬 완료 이후 비동기로 실행하며, sync 실패는 pending sync로 남긴다.

---

## Summary 동기화 기준

`SessionSummary.correctionAvailable`은 Correction 진입 판단의 기준이다.
`DashSummary.correctionAvailable`은 Dashboard 표시용 파생값이다.

따라서 완료 처리에서는 두 값을 같은 UseCase 흐름 안에서 갱신해야 한다.
둘 중 하나만 갱신하는 구현은 허용하지 않는다.

---

## 복귀 정책

- Dashboard에서 진입했다면 Dashboard로 복귀한다.
- 하단 탭으로 진입했더라도 완료 후 Dashboard로 복귀한다.
- 복귀 후 Dashboard 교정 대기 카드는 최신 Summary를 기준으로 다시 렌더링되어야 한다.
