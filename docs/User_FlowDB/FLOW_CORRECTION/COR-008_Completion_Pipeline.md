# [Feature] COR-008 교정 완료 파이프라인

## User Story

사용자는 저장 요청 이후 교정 완료 처리가 한 번에 성공하거나, 실패 시 안전하게 재시도할 수 있기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 저장할 Flashcard 항목이 준비된 이후에만 완료 파이프라인을 시작한다.
- [ ] 완료 시 LangState 업데이트 입력을 생성하고 `LS-006` 정책에 맞춰 적용한다.
- [ ] 완료 시 Flashcard local first 저장이 실행된다.
- [ ] 완료 시 Session Memory 압축 요청이 실행된다.
- [ ] 완료 시 `SessionSummary.correctionAvailable`을 false로 갱신한다.
- [ ] 같은 완료 흐름 안에서 `DashSummary.correctionAvailable`도 함께 반영한다.
- [ ] 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 남긴다.
- [ ] 저장소가 하나의 transaction으로 묶이지 않는 경우 보상 rollback 또는 commit marker 방식으로 부분 완료 상태를 남기지 않는다.
- [ ] 완료 처리 중 중복 완료 요청이 방지된다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-007 → Flashcard 저장 요청
- COR-008 → 교정 완료 파이프라인
- LS-006 → Language State Update Policy
- LS-005 → Local Cache & Sync Policy

---

# 구현 범위

## 포함 범위

- `CompleteCorrectionUseCase`
- LangState 업데이트 입력 생성 및 적용
- Flashcard local first 저장
- Session Memory 압축 요청
- `SessionSummary.correctionAvailable` 갱신
- `DashSummary.correctionAvailable` 동시 반영
- 로컬 완료 파이프라인 rollback / retry 처리

## 제외 범위 (Out of Scope)

- Session Memory append
- LangState 수치 계산 공식
- Dashboard 카드 UI 자체 구현
- Dashboard 복귀 화면 처리

---

# Details

## 완료 파이프라인 역할

완료 파이프라인은 교정 결과 저장 이후 앱 상태를 다음 대화와 Dashboard에 맞게 정리하는 단계다.
부분 완료 상태가 남지 않도록 로컬 작업은 하나의 완료 흐름으로 관리한다.

## 처리 대상

- LangState 업데이트 입력 생성 및 적용
- Flashcard local first 저장
- Session Memory 압축 요청
- `SessionSummary.correctionAvailable` 갱신
- `DashSummary.correctionAvailable` 동시 반영

## 실패 정책

- 로컬 완료 파이프라인 중 하나라도 실패하면 Retry 상태로 남긴다.
- 가능한 경우 전체 로컬 변경을 rollback한다.
- 하나의 transaction으로 묶기 어려운 저장소는 보상 rollback 또는 commit marker 방식으로 부분 완료를 방지한다.
- MVP에서는 Room/DataStore 등 local store 반영이 모두 성공한 시점을 로컬 완료 성공으로 본다.
- Firestore sync 실패는 로컬 완료 실패로 보지 않고 pending sync로 분리한다.

## 완료 순서

```text
저장할 CorrectionSuggestion 선택 완료
→ CompleteCorrectionUseCase 호출
→ LangState 업데이트 입력 생성 및 적용
→ Flashcard local first 저장
→ Session Memory 압축 요청
→ SessionSummary.correctionAvailable false 갱신
→ DashSummary.correctionAvailable 동시 반영
```

LangState 업데이트는 Session Memory 압축과 초기화보다 먼저 수행한다.
압축으로 인해 `recentFullContext`가 비워진 뒤 분석 입력이 사라지는 상황을 막기 위해서다.

---

## 작업 지시

- 완료 파이프라인은 `CompleteCorrectionUseCase` 한 곳에서 순서를 관리한다.
- LangState 업데이트 입력은 압축 전에 만든다.
- Flashcard 저장, Session Memory 압축, Summary 갱신 중 하나라도 실패하면 부분 완료 상태를 남기지 않는다.
- 같은 완료 요청이 중복 실행되지 않도록 in-flight 상태를 둔다.
- Firestore sync는 이 이슈에서 성공 여부를 사용자 완료 조건으로 삼지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
domain/usecase/correction/
→ CompleteCorrectionUseCase

domain/usecase/learningstate/
→ ApplyLanguageStateUpdateUseCase

domain/repository/
→ CorrectionRepository
→ LearningStateRepo
```

## 완료 처리 원칙

- 완료 순서는 `CompleteCorrectionUseCase`에서 관리한다.
- Repository는 저장과 sync 경계를 담당한다.
- 상태 계산과 완료 순서 결정은 UseCase에서 처리한다.
- Firestore sync는 로컬 완료 이후 background로 예약한다.

## 로컬 완료 기준

```text
LangState local update success
→ Flashcard local save success
→ Session Memory compression local success
→ SessionSummary / DashSummary local update success
→ local completion success
```

위 단계 중 하나라도 실패하면 Done 상태로 넘기지 않는다.
같은 저장소 transaction으로 묶을 수 없는 경우에는 완료 marker를 마지막에 기록하고,
marker가 없으면 다음 진입 시 Retry 또는 보정 대상으로 본다.

---

## 검증 기준

- 완료 성공 시 `SessionSummary.correctionAvailable`과 `DashSummary.correctionAvailable`이 함께 false가 된다.
- 중간 단계 실패 시 완료 상태로 넘어가지 않는다.
- 같은 저장 버튼을 빠르게 여러 번 눌러도 완료 파이프라인은 중복 실행되지 않는다.
- Firestore sync 실패만 발생한 경우 로컬 완료는 성공으로 유지된다.

---

# Edge Cases

- LangState 업데이트 입력 생성 실패
- Flashcard local 저장 실패
- Session Memory 압축 실패
- `SessionSummary` 갱신은 성공했지만 `DashSummary` 갱신이 실패함
- 완료 요청이 중복 실행됨
- 로컬 완료 성공 후 Firestore sync가 실패함
