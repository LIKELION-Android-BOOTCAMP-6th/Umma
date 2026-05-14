# [Feature] COR-005 교정 완료 후 세션 정리 및 압축

## User Story

사용자는 교정과 Flashcard 저장이 끝난 뒤,
다음 대화와 Dashboard 상태가 깨끗하게 정리되기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 교정 후 사용자가 Flashcard 저장을 완료한 뒤에만 Session Memory 압축을 요청한다.
- [ ] Flashcard 저장이 완료되기 전에는 `recentFullContext` 원문 buffer를 정리하지 않는다.
- [ ] 압축 완료 후 `recentFullContext` 원문 buffer 정리를 요청한다.
- [ ] `SessionSummary.correctionAvailable`을 완료 상태에 맞게 갱신하고, `DashSummary.correctionAvailable`에 반영한다.
- [ ] LangState 업데이트는 LS-006의 idempotency 정책을 따른다.
- [ ] 정리 실패 시 교정 결과 자체가 유실되지 않도록 재시도 가능 상태를 남긴다.
- [ ] 압축과 상태 갱신의 순서가 문서화된다.

---

# Flow (링크)

- SYS-CORRECTION-INFRA
- COR-005 → 교정 완료 후 세션 정리 및 압축

---

# 구현 범위

## 포함 범위

- Session Memory 압축 요청
- `recentFullContext` buffer 정리 요청
- `SessionSummary.correctionAvailable` 갱신 요청
- `DashSummary.correctionAvailable` 동시 반영 요청
- LangState 업데이트 연계
- 재시도 가능한 정리 상태 처리
- 압축 완료 후 Dashboard 반영 요청
- Flashcard 저장 완료 이후에만 정리 수행

## 제외 범위 (Out of Scope)

- Session Memory append 구현
- AI 교정 후보 추출
- Flashcard 저장 세부 구현
- LangState 수치 계산 정책

> 이 이슈는 교정이 끝난 뒤 원문과 상태를 정리하는 데 집중한다.
> 실제 turn append와 buffer 저장 경계는 RT-003, 상태 수치 계산은 LS-006이 담당한다.

---

# Details

## 정리 순서

교정 완료 후에는 다음 순서를 권장한다.

```text
교정 결과 확정
→ Flashcard 저장 성공 여부 확인
→ Session Memory 압축 요청
→ recentFullContext buffer 정리
→ SessionSummary.correctionAvailable false 갱신
→ DashSummary.correctionAvailable 동시 반영
```

정리 실패가 나더라도 교정 결과와 저장된 Flashcard는 유실되지 않아야 한다.

교정 화면을 나왔다고 해서 바로 `SessionSummary.correctionAvailable`을 해제하지 않는다.
최소 1개 이상의 교정 결과를 Flashcard 저장까지 완료한 뒤 정리한다.

---

## idempotency 정책

LangState 업데이트는 LS-006의 중복 반영 방지 정책을 따른다.

즉,

- 같은 교정 이벤트가 두 번 반영되지 않아야 한다.
- 정리 작업이 재시도되더라도 상태가 중복 갱신되면 안 된다.

---

## 현재 코드 기준 메모

- Session Memory 압축의 실제 저장 경계는 SYS-REALTIME-INFRA와 맞물린다.
- 이 이슈는 압축을 “요청하고 정리 상태를 반영하는 오케스트레이션”에 집중한다.
- 교정 완료 후 Dashboard의 correctionAvailable이 최신 값으로 바뀌고, 그 기준값은 SessionSummary여야 한다.
- 두 값은 분리된 표시값이지만, 같은 교정 완료 배치 안에서 함께 갱신되어야 한다.
