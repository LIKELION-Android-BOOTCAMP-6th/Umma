# [Feature] COR-002 교정 결과 생성

## User Story

사용자는 Correction 화면이 준비된 뒤 현재 언어 상태에 맞는 교정 결과와 설명을 받을 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `COR-001` Ready 상태 이후 사용자 추가 입력 없이 교정 결과 생성을 시작한다.
- [ ] `SYS-CORRECTION-INFRA`의 후보 추출 계약과 교정 결과 생성 계약을 호출한다.
- [ ] 실제 AI 교정 API를 호출해 교정 결과를 생성한다.
- [ ] AI 응답은 정해진 응답 계약으로 파싱되어 `CorrectionSuggestion` 목록으로 변환된다.
- [ ] 실제 AI 성공 응답에서 `nativeText`, `afterText`, `explanation`이 채워진 `CorrectionSuggestion` 1개 이상을 생성하고 Content 상태로 전환한다.
- [ ] 결과가 비어 있으면 Empty 상태를 반환한다.
- [ ] AI 요청 실패, 응답 파싱 실패, 필수 필드 누락 시 Error 상태로 전환하고 Retry 액션을 제공한다.
- [ ] Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.
- [ ] fake/mock 또는 `CorrectionSuggestionFixtureBuilder`로도 Content / Empty / Error 상태를 검증할 수 있다.

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
- 실제 AI API 호출 및 응답 파싱 결과 연결
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
- MVP 최종 구현에서는 실제 AI 응답을 `CorrectionSuggestion`으로 변환하는 흐름이 동작해야 한다.
- 결과가 있으면 `COR-003` 카드 표시로 이어진다.
- 결과가 없으면 Empty 상태로 분기한다.

## 실패 정책

- AI 요청 실패, 응답 파싱 실패, 필수 필드 누락은 Error 상태로 분기한다.
- Error 상태에서는 같은 Session Memory와 현재 선택 언어 기준의 Retry 액션을 제공한다.

## 구현 가이드

```text
COR-001 Ready
→ SYS 후보 추출 계약 호출
→ LangState snapshot
→ CorrectionRepository 결과 생성 계약 호출
→ 실제 AI API 요청
→ AI 응답 파싱 및 필수 필드 검증
→ CorrectionSuggestion 변환
→ CorrectionSuggestion list
```

이 이슈는 화면 카드 배치를 완성하지 않는다.
화면 표시는 `COR-003`에서 다룬다.

---

## 작업 지시

- 후보 추출 규칙과 교정 결과 생성 계약을 이 이슈에서 다시 정의하지 않는다.
- AI 원문 응답과 JSON 파싱은 ViewModel/Composable에서 직접 처리하지 않고 `CorrectionRepository`의 data 계층 구현을 통해 처리한다.
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
→ AI response mapper
→ CorrectionSuggestion 화면 상태
```

---

## 검증 기준

- `COR-001` Ready 상태 이후 교정 결과 생성이 자동으로 시작된다.
- 실제 AI 성공 응답 기준으로 필수 필드가 채워진 `CorrectionSuggestion`을 1개 이상 생성할 수 있다.
- fake/mock 또는 `CorrectionSuggestionFixtureBuilder`로도 Content / Empty / Error 상태를 검증할 수 있다.
- `CorrectionSuggestion` 목록을 받으면 Content 상태로 전환된다.
- 결과가 비어 있으면 Empty 상태로 전환된다.
- AI 실패, 파싱 실패, 필수 필드 누락 시 카드 화면으로 넘어가지 않고 Error 상태가 된다.
- Retry 시 같은 언어와 같은 세션 기준으로 다시 요청한다.

---

# Edge Cases

- SYS 후보 추출 결과가 비어 있음
- 후보는 있으나 AI 응답이 비어 있음
- AI 응답 JSON 구조가 깨짐
- 일부 후보만 교정 결과로 변환됨
- 네트워크 실패로 교정 결과 생성이 중단됨
- Retry 중 선택 언어가 변경됨
