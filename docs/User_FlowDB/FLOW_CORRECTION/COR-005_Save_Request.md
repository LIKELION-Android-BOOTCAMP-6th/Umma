# [Feature] COR-005 Flashcard 저장 요청

## User Story

사용자는 선택한 교정 결과를 Flashcard 저장 요청으로 변환하고, 완료 파이프라인으로 넘길 준비 상태를 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 선택한 `CorrectionSuggestion`만 Flashcard 저장 요청으로 변환한다.
- [ ] 저장 요청은 현재 선택 언어 기준으로 생성된다.
- [ ] Flashcard 앞면은 모국어 문장으로 구성한다.
- [ ] Flashcard 뒷면은 교정된 외국어 문장과 짧은 설명으로 구성한다.
- [ ] Flashcard 저장 요청은 `SYS-CORRECTION-INFRA`에서 정의한 저장 계약에 맞는 입력으로 구성한다.
- [ ] 저장 대상이 0개이면 완료 파이프라인을 호출하지 않는다.
- [ ] 저장 요청 변환 실패 시 Error 상태를 표시한다.
- [ ] 저장 버튼 중복 클릭을 방지한다.
- [ ] 저장 요청 모델이 준비되면 `COR-006` 완료 파이프라인으로 넘긴다.

---

# Flow (링크)

- [FLOW-CORRECTION](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION.md)
- [COR-004 → 저장 카드 선택 상태](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-004_Card_Selection.md)
- [COR-005 → Flashcard 저장 요청 준비](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-005_Save_Request.md)
- [SYS-CORRECTION-INFRA → Flashcard 저장 요청 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_CORRECTION_INFRA.md)

---

# 구현 범위

## 포함 범위

- 저장 요청 모델 변환
- `COR-006`에서 사용할 저장 입력 구성
- Preparing / Error 상태
- 중복 저장 요청 방지

## 제외 범위 (Out of Scope)

- 카드 선택 UI
- 로컬 완료 파이프라인 내부 처리
- Session Memory 압축 실행
- Dashboard 복귀 처리

---

# Details

## 저장 요청 준비 역할

저장 요청은 사용자가 선택한 `CorrectionSuggestion`을 Flashcard 저장에 필요한 입력으로 변환하는 단계다.
실제 로컬 완료 파이프라인은 `COR-006`에서 처리한다.
새 Flashcard의 최초 local 저장은 SRS가 아니라 Correction 완료 흐름에서 수행되므로,
이 이슈의 요청 모델은 `CorrectionRepository.saveFlashcards(...)`가 바로 저장할 수 있는 형태로 준비되어야 한다.

## 사용 데이터

- 선택된 `CorrectionSuggestion`
- 현재 선택 언어
- 교정 전 문장
- 교정 후 문장
- Flashcard 앞면에 표시할 모국어 문장
- 설명

## 저장 요청 정책

- 선택된 카드만 저장 요청에 포함한다.
- 저장 대상이 0개이면 UseCase를 호출하지 않는다.
- 요청 준비 중 저장 버튼 중복 클릭을 방지한다.

## 저장 요청 흐름

```text
selected CorrectionSuggestion list
→ Flashcard 저장 요청 모델 변환
→ CompleteCorrectionUseCase로 전달
→ COR-006에서 새 Flashcard 최초 local 저장 실행
```

최종 Saved 상태는 `COR-006` 로컬 완료 파이프라인이 성공한 뒤 표시한다.

---

## 작업 지시

- 선택된 `CorrectionSuggestion` 목록을 저장 요청 모델로 변환한다.
- 저장 요청에는 현재 선택 언어, 교정 전 문장, 교정 후 문장, 앞면 모국어 문장, 뒷면 설명이 포함되어야 한다.
- 저장 버튼 클릭 후 요청 준비 중에는 중복 클릭을 막는다.
- 저장 대상이 0개인 경우 UseCase를 호출하지 않는다.
- 이 이슈에서는 실제 local transaction을 완료하지 않고 `COR-006`로 넘길 입력을 준비한다.
- SRS용 `FlashcardRepository` 조회/복습 계약으로 저장 책임을 넘기지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ CorrectionViewModel
→ CorrectionUiState
```

## 요청 모델 필드

- 현재 선택 언어
- 교정 전 문장
- 교정 후 문장
- 앞면 모국어 문장
- 설명
- 원본 `CorrectionSuggestion` id

저장 요청 모델의 세부 계약은 `SYS-CORRECTION-INFRA`를 따른다.
실제 local first 저장 실행은 `COR-006`에서 호출하는 `CompleteCorrectionUseCase`가 담당한다.
이 저장은 새 Flashcard 최초 생성 저장이며, SRS의 review schedule 갱신 저장과 구분한다.

---

## 검증 기준

- 선택된 카드만 저장 요청에 포함된다.
- 선택 항목이 없으면 완료 파이프라인이 호출되지 않는다.
- 저장 요청 변환 실패 시 Error 상태가 표시된다.
- 이 이슈만으로 Room 저장이나 Firestore sync가 실행되지 않는다.

---

# Edge Cases

- 선택 항목이 0개임
- 선택된 카드 id가 현재 결과 목록에 없음
- 저장 버튼을 빠르게 여러 번 누름
- 저장 요청 모델 변환 중 필수 필드가 누락됨
- Flashcard 앞면에 사용할 모국어 문장이 비어 있음
- 현재 선택 언어가 저장 요청 직전에 변경됨
