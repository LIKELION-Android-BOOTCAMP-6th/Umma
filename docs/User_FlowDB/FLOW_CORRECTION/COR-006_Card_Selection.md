# [Feature] COR-006 저장 카드 선택 상태

## User Story

사용자는 교정 결과 카드 중 Flashcard로 저장하고 싶은 항목을 선택하거나 선택 해제할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 사용자가 교정 결과 카드를 선택할 수 있다.
- [ ] 선택한 카드는 선택 상태가 시각적으로 구분된다.
- [ ] 선택한 카드를 다시 누르면 선택 해제할 수 있다.
- [ ] 선택된 카드 목록은 ViewModel 상태로 관리한다.
- [ ] 선택 항목이 0개이면 저장 버튼은 비활성화된다.
- [ ] 선택 상태는 `CorrectionSuggestion` 식별자를 기준으로 관리한다.
- [ ] 선택 상태 변경만 다루고 실제 저장 요청은 실행하지 않는다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-005 → 교정 결과 카드 표시
- COR-006 → 저장 카드 선택 상태

---

# 구현 범위

## 포함 범위

- 카드 선택 UI
- 선택/해제 상태 관리
- 저장 버튼 enabled / disabled 상태
- 선택 항목 count 표시가 필요한 경우의 상태값

## 제외 범위 (Out of Scope)

- Flashcard 저장 요청 모델 변환
- Completion pipeline 호출
- Firestore sync 처리

---

# Details

## 선택 상태 역할

선택 상태는 사용자가 어떤 교정 결과를 Flashcard로 저장할지 결정하는 화면 상태다.
이 단계에서는 실제 저장 요청을 보내지 않는다.

## 사용 데이터

- `CorrectionSuggestion`
- `CorrectionSuggestion` stable id
- selected suggestion id 목록

## 선택 정책

- 선택 대상은 내부 후보가 아니라 최종 교정 결과 카드다.
- 선택 항목이 0개이면 저장 버튼은 비활성화한다.
- 같은 카드를 다시 누르면 선택 해제한다.

## 구현 가이드

```text
CorrectionSuggestion card click
→ selectedSuggestionIds update
→ save button enabled state update
```

사용자가 선택하는 대상은 내부 후보가 아니라 최종 교정 결과 카드다.

---

## 작업 지시

- 선택 상태는 화면 local state가 아니라 ViewModel 상태로 관리한다.
- 선택 식별자는 문장 문자열이 아니라 `CorrectionSuggestion`의 stable id를 우선 사용한다.
- 저장 버튼은 선택 항목이 있을 때만 활성화한다.
- 선택 상태 변경은 저장 요청을 발생시키지 않는다.
- 화면 회전이나 recomposition 상황에서도 선택 상태가 의도치 않게 초기화되지 않도록 상태 owner를 명확히 둔다.

---

# 기술 설계 가이드

## 권장 구조

```text
CorrectionUiState
→ suggestions
→ selectedSuggestionIds
→ canSave
```

## 상태 정책

- 선택 상태는 ViewModel에서 관리한다.
- `canSave`는 `selectedSuggestionIds.isNotEmpty()` 기준으로 계산한다.
- 저장 버튼은 `canSave == true`일 때만 활성화한다.

---

## 검증 기준

- 카드를 누르면 선택 상태가 표시된다.
- 같은 카드를 다시 누르면 선택 해제된다.
- 선택 항목이 0개이면 저장 버튼이 비활성화된다.

---

# Edge Cases

- 같은 카드를 빠르게 여러 번 누름
- 선택된 카드가 결과 목록에서 사라짐
- 선택 항목이 0개인 상태에서 저장 버튼 클릭 시도
- recomposition 이후 선택 상태가 초기화됨
- 같은 문장 텍스트를 가진 서로 다른 `CorrectionSuggestion`이 존재함
