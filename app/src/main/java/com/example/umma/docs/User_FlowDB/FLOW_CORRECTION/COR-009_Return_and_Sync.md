# [Feature] COR-009 Dashboard 복귀 및 후처리

## User Story

사용자는 교정 완료 후 Dashboard로 돌아가고, Dashboard 교정 대기 상태가 최신 Summary 기준으로 반영되기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `COR-008` 로컬 완료 파이프라인 성공 후 Dashboard로 복귀한다.
- [ ] Dashboard에서 진입했다면 Dashboard로 복귀한다.
- [ ] 하단 탭으로 진입했더라도 완료 후 Dashboard로 복귀한다.
- [ ] 복귀 후 Dashboard 교정 대기 카드는 최신 Summary를 기준으로 다시 렌더링된다.
- [ ] Firestore background sync는 로컬 완료 이후 비동기로 예약한다.
- [ ] Firestore sync 실패는 로컬 완료 실패로 보지 않고 pending sync 상태로 남긴다.
- [ ] sync pending 상태가 있어도 사용자의 교정 완료는 성공으로 본다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-008 → 교정 완료 파이프라인
- COR-009 → Dashboard 복귀 및 후처리
- FLOW-DASHBOARD → Dashboard Summary 렌더링

---

# 구현 범위

## 포함 범위

- 완료 성공 후 Dashboard 복귀 처리
- Dashboard 최신 Summary 재렌더링 확인
- Firestore pending sync 상태 처리
- Done / PendingSync 상태 정리

## 제외 범위 (Out of Scope)

- 로컬 완료 파이프라인 내부 구현
- Flashcard 학습 화면
- Dashboard 카드 UI 자체 구현

---

# Details

## 복귀 역할

복귀 및 후처리는 로컬 완료 파이프라인 성공 후 사용자를 Dashboard로 돌려보내고,
Dashboard가 최신 Summary 기준으로 다시 보이도록 하는 단계다.

## 사용 데이터

- `SessionSummary`
- `DashSummary`
- pending sync 상태

## sync 정책

- Firestore sync는 로컬 완료 이후 background로 수행한다.
- sync 실패는 로컬 완료 실패로 보지 않는다.
- pending sync 상태가 있어도 사용자는 교정 완료로 인식한다.
- pending sync는 UI 상태가 아니라 repository/local sync metadata 또는 sync queue에 남긴다.

## 복귀 정책

```text
COR-008 local completion success
→ Firestore background sync 예약
→ Dashboard 복귀
→ Dashboard Summary 기준 교정 대기 카드 재렌더링
```

Firestore sync 실패는 pending sync로 남긴다.
이 상태는 사용자에게 저장 실패로 안내하지 않는다.

---

## 작업 지시

- Dashboard 복귀는 `COR-008` 로컬 완료 성공 이후에만 수행한다.
- 복귀 후 Dashboard는 기존 카드 상태를 재사용하지 말고 최신 Summary 기준으로 다시 렌더링한다.
- Firestore sync 실패는 사용자에게 저장 실패로 표시하지 않는다.
- pending sync 상태가 필요하면 내부 상태로만 남기고, 사용자는 완료된 흐름으로 인식하게 한다.
- 복귀 실패 시에는 완료 결과를 잃지 않고 Retry 또는 Dashboard 재이동이 가능해야 한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ completion success event
→ navigateToDashboard()

presentation/dashboard/
→ observe latest DashSummary
```

## 후처리 원칙

- Dashboard는 Correction 화면의 이전 UI 상태를 재사용하지 않는다.
- Dashboard는 최신 Summary observe 결과를 기준으로 다시 렌더링한다.
- pending sync는 사용자 완료 실패로 노출하지 않는다.

## pending sync 저장 위치

```text
local saved record syncStatus = pending
또는
local sync queue에 pending event 기록
```

MVP에서는 사용자가 볼 수 있는 Error UI로 노출하지 않는다.
필요한 경우 내부 로그나 개발자 확인용 상태로만 남긴다.

---

## 검증 기준

- 로컬 완료 성공 후 Dashboard로 이동한다.
- Dashboard 교정 대기 카드는 최신 Summary 기준으로 갱신된다.
- Firestore sync 실패 상태에서도 사용자의 저장 완료 상태는 유지된다.
- pending sync는 CorrectionUiState가 아니라 local sync metadata 또는 sync queue에 남는다.

---

# Edge Cases

- Dashboard navigation 실패
- Dashboard 복귀 직후 Summary observe가 아직 이전 값을 보여줌
- Firestore sync pending 상태가 남아 있음
- 사용자가 완료 직후 앱을 종료함
- 복귀 후 교정 대기 카드가 계속 활성 상태로 보임
