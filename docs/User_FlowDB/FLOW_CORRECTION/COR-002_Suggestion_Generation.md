# [Feature] COR-002 교정 결과 생성

## User Story

사용자는 Correction 화면이 준비된 뒤 현재 언어 상태에 맞는 교정 결과와 설명을 받을 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `COR-001` Ready 상태 이후 사용자 추가 입력 없이 교정 결과 생성을 시작한다.
- [ ] `SYS-CORRECTION-INFRA`의 후보 추출 계약과 교정 결과 생성 계약을 호출한다.
- [ ] 성공 시 `CorrectionSuggestion` 목록을 화면 상태로 보관한다.
- [ ] 결과가 비어 있으면 Empty 상태를 반환한다.
- [ ] AI 요청 실패 시 Error 상태를 반환한다.
- [ ] 파싱 실패 시 Error 상태를 반환한다.
- [ ] Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.

---

# Flow (링크)

- [FLOW-CORRECTION](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION.md)
- [COR-001 → Correction 초기 상태 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md)
- [COR-002 → 교정 결과 생성](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md)
- [SYS-CORRECTION-INFRA → CorrectionRepository 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_CORRECTION_INFRA.md)

---

# 구현 범위

## 포함 범위

- 교정 결과 생성 UseCase 연결
- `CorrectionRepository` 결과 생성 계약 연결
- `CorrectionSuggestion` 목록 화면 상태 연결
- Loading / Error / Retry 상태

## 제외 범위 (Out of Scope)

- 후보 추출 규칙
- 결과 카드 세부 UI
- Flashcard 저장
- LangState 수치 계산 공식

---

# Details

## 교정 결과 생성 역할

이 이슈는 `SYS-CORRECTION-INFRA`에서 준비한 후보 추출/교정 결과 생성 계약을 호출해,
사용자가 볼 수 있는 `CorrectionSuggestion` 목록을 화면 상태로 만든다.

## 사용 데이터

- SYS 후보 추출 계약 결과
- `LangState` snapshot
- 현재 선택 언어
- `CorrectionRepository`

## 생성 정책

- 교정 결과 생성 세부 정책은 `SYS-CORRECTION-INFRA`의 교정 결과 계약을 따른다.
- 결과가 있으면 `COR-003` 카드 표시로 이어진다.
- 결과가 없으면 Empty 상태로 분기한다.

## 실패 정책

- AI 요청 실패는 Error 상태로 분기한다.
- 응답 파싱 실패도 Error 상태로 분기한다.
- Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.

## 구현 가이드

```text
COR-001 Ready
→ SYS 후보 추출 계약 호출
→ LangState snapshot
→ CorrectionRepository 결과 생성 계약 호출
→ CorrectionSuggestion list
```

이 이슈는 화면 카드 배치를 완성하지 않는다.
화면 표시는 `COR-003`에서 다룬다.

---

## 작업 지시

- 후보 추출 규칙과 교정 결과 생성 계약을 이 이슈에서 다시 정의하지 않는다.
- AI 응답 파싱 실패와 네트워크 실패는 모두 Error 상태로 올리고 Retry가 가능해야 한다.
- Retry는 새 세션을 만들지 않고 현재 선택 언어와 같은 Session Memory 기준으로 다시 수행한다.
- 결과가 비어 있으면 카드 화면으로 넘어가지 않고 Empty 상태를 표시한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ CorrectionViewModel
→ CorrectionUiState
```

## 결과 계약

```text
SYS 후보 추출 계약 결과 + LangState snapshot
→ CorrectionRepository
→ CorrectionSuggestion 화면 상태
```

---

## 검증 기준

- `COR-001` Ready 상태 이후 교정 결과 생성이 자동으로 시작된다.
- `CorrectionSuggestion` 목록을 받으면 Content 상태로 전환된다.
- 결과가 비어 있으면 Empty 상태로 전환된다.
- AI 실패 또는 파싱 실패 시 카드 화면으로 넘어가지 않고 Error 상태가 된다.
- Retry 시 같은 언어와 같은 세션 기준으로 다시 요청한다.

---

# Edge Cases

- SYS 후보 추출 결과가 비어 있음
- 후보는 있으나 AI 응답이 비어 있음
- AI 응답 JSON 구조가 깨짐
- 일부 후보만 교정 결과로 변환됨
- 네트워크 실패로 교정 결과 생성이 중단됨
- Retry 중 선택 언어가 변경됨
