# [Feature] COR-006 교정 완료 결과 연결

## User Story

사용자는 저장 요청 이후 교정 완료 결과를 화면에서 확인하고, 실패 시 안전하게 재시도할 수 있기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 저장할 Flashcard 항목이 준비된 이후에만 완료 처리를 시작한다.
- [ ] `SYS-CORRECTION-INFRA`에서 준비한 `CompleteCorrectionUseCase`를 호출한다.
- [ ] 선택된 교정 결과가 새 Flashcard로 local first 저장된다.
- [ ] 로컬 완료 성공 결과를 받으면 Done 상태로 전환한다.
- [ ] 로컬 완료 실패 결과를 받으면 Retry 상태로 남긴다.
- [ ] 완료 처리 중 중복 완료 요청이 방지된다.

---

# Flow (링크)

- [FLOW-CORRECTION](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION.md)
- [COR-005 → Flashcard 저장 요청](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-005_Save_Request.md)
- [COR-006 → 교정 완료 결과 연결](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md)
- [LS-006 → Language State Update Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [LS-005 → Local Cache & Sync Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)

---

# 구현 범위

## 포함 범위

- `CompleteCorrectionUseCase` 호출
- 새 Flashcard 최초 local first 저장 결과 연결
- Completing / Done / Retry 상태 연결
- 완료 요청 in-flight 상태 관리

## 제외 범위 (Out of Scope)

- 로컬 완료 파이프라인 내부 순서 구현
- LangState 업데이트 입력 생성 및 적용
- Session Memory 압축 내부 처리
- `SessionSummary` / `DashSummary` 갱신 내부 처리
- Session Memory append
- LangState 수치 계산 공식
- Dashboard 카드 UI 자체 구현
- Dashboard 복귀 화면 처리

---

# Details

## 완료 결과 연결 역할

이 이슈는 저장 요청 이후 `CompleteCorrectionUseCase`를 호출하고,
그 결과를 Correction 화면 상태로 연결하는 단계다.
`CompleteCorrectionUseCase` 내부에서 선택된 교정 결과는 새 Flashcard로 최초 local first 저장된다.
이 저장은 SRS가 대신 수행하지 않는다.
파이프라인 내부 순서와 rollback 정책은 `SYS-CORRECTION-INFRA` 계약을 따른다.

## 실패 정책

- `CompleteCorrectionUseCase`가 실패 결과를 반환하면 Retry 상태로 남긴다.
- 파이프라인 내부의 rollback / commit marker 정책은 이 이슈에서 재정의하지 않는다.
- Firestore sync 실패는 `CompleteCorrectionUseCase`의 로컬 완료 실패와 분리된 결과로 다룬다.

## 완료 순서

```text
저장할 CorrectionSuggestion 선택 완료
→ CompleteCorrectionUseCase 호출
→ 새 Flashcard local first 저장
→ local completion result 수신
→ Done 또는 Retry 상태 전환
```

---

## 작업 지시

- 완료 파이프라인 내부 순서를 화면/ViewModel에서 직접 구현하지 않는다.
- 저장 요청 입력을 `CompleteCorrectionUseCase`에 전달하고 결과만 화면 상태로 반영한다.
- 새 Flashcard 최초 저장은 Correction 완료 파이프라인 책임이며, SRS의 review 저장 계약으로 넘기지 않는다.
- 같은 완료 요청이 중복 실행되지 않도록 in-flight 상태를 둔다.
- Firestore sync는 이 이슈에서 성공 여부를 사용자 완료 조건으로 삼지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ CorrectionViewModel
→ CorrectionUiState
```

## 완료 처리 원칙

- 완료 순서는 `CompleteCorrectionUseCase`에서 관리한다.
- `CompleteCorrectionUseCase`의 로컬 완료에는 새 Flashcard local first 저장 성공이 포함된다.
- ViewModel은 완료 결과를 Done / Retry 상태로 연결한다.
- Firestore sync 결과는 로컬 완료 성공/실패 판단과 분리한다.

## 로컬 완료 기준

```text
CompleteCorrectionUseCase success
→ CorrectionUiState.Done
```

`CompleteCorrectionUseCase`가 실패 결과를 반환하면 Done 상태로 넘기지 않는다.

---

## 검증 기준

- `CompleteCorrectionUseCase` 성공 결과를 받으면 Done 상태로 전환된다.
- `CompleteCorrectionUseCase` 실패 결과를 받으면 Retry 상태로 전환된다.
- 새 Flashcard local first 저장 실패는 Done 상태로 전환되지 않는다.
- 같은 저장 버튼을 빠르게 여러 번 눌러도 완료 파이프라인은 중복 실행되지 않는다.
- Firestore sync 실패만 발생한 경우 로컬 완료는 성공으로 유지된다.

---

# Edge Cases

- `CompleteCorrectionUseCase` 실패 결과 반환
- 완료 요청이 중복 실행됨
- 로컬 완료 성공 후 Firestore sync pending 상태가 함께 반환됨
