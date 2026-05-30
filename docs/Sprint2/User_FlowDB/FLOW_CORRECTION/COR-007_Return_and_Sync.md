# [Feature] COR-007 Dashboard 복귀 및 후처리

## User Story

사용자는 교정 완료 후 Dashboard로 돌아가기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `COR-006` 로컬 완료 성공 후 Dashboard로 복귀한다.
- [ ] 완료 성공 이벤트는 한 번만 소비된다.
- [ ] sync pending 상태가 있어도 Dashboard 복귀를 막지 않는다.
- [ ] Session Memory compression pending 상태가 있어도 저장 완료와 Dashboard 복귀를 막지 않는다.

---

# Flow (링크)

- [FLOW-CORRECTION](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Sprint2/User_FlowDB/FLOW_CORRECTION.md)
- [COR-006 → 교정 완료 결과 연결](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md)
- [COR-007 → Dashboard 복귀 및 후처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-007_Return_and_Sync.md)
- [FLOW-DASHBOARD → Dashboard Summary 렌더링](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Sprint2/User_FlowDB/FLOW_DASHBOARD.md)

---

# 구현 범위

## 포함 범위

- 완료 성공 후 Dashboard 복귀 처리
- 완료 성공 navigation event 처리
- Done / PendingSync 상태 정리

## 제외 범위 (Out of Scope)

- 로컬 완료 파이프라인 내부 구현
- Flashcard 학습 화면
- Dashboard 카드 UI 자체 구현
- Dashboard Summary observe / 카드 재렌더링 구현
- Firestore sync 예약과 재시도 구현

---

# Details

## 복귀 역할

복귀 및 후처리는 로컬 완료 파이프라인 성공 후 사용자를 Dashboard로 돌려보내는 단계다.
Dashboard 화면의 Summary observe와 카드 렌더링은 Dashboard Flow의 책임이다.

## 사용 데이터

- pending sync 상태
- session compression pending 상태
- completion success event

## sync 정책

- Firestore sync 예약과 재시도는 `SYS-CORRECTION-INFRA` / Repository 계약을 따른다.
- pending sync 상태가 있어도 Dashboard 복귀를 막지 않는다.
- Session Memory compression 실패는 저장 완료 실패가 아니며, 후속 재시도 대상으로만 남긴다.

## 복귀 정책

```text
COR-006 local completion success
→ completion success event
→ Dashboard 복귀
```

---

## 작업 지시

- Dashboard 복귀는 `COR-006` 로컬 완료 성공 이후에만 수행한다.
- 완료 성공 이벤트가 recomposition이나 재진입으로 중복 소비되지 않게 한다.
- pending sync 상태가 있어도 사용자는 완료된 흐름으로 인식하게 한다.
- 복귀 실패 시에는 완료 결과를 잃지 않고 Retry 또는 Dashboard 재이동이 가능해야 한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ completion success event
→ navigateToDashboard()
```

## 후처리 원칙

- pending sync와 compression pending은 사용자 완료 실패로 노출하지 않는다.
필요한 경우 내부 로그나 개발자 확인용 상태로만 남긴다.

---

## 검증 기준

- 로컬 완료 성공 후 Dashboard로 이동한다.
- Firestore sync 실패 상태에서도 사용자의 저장 완료 상태는 유지된다.
- Session Memory compression pending 상태에서도 사용자의 저장 완료 상태는 유지된다.
- completion success event가 중복 소비되지 않는다.

---

# Edge Cases

- Dashboard navigation 실패
- Firestore sync pending 상태가 남아 있음
- Session Memory compression pending 상태가 남아 있음
- 사용자가 완료 직후 앱을 종료함
