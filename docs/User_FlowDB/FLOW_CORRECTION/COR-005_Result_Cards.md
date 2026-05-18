# [Feature] COR-005 교정 결과 카드 표시

## User Story

사용자는 교정 결과 카드에서 교정 전 문장, 교정 후 문장, 쉬운 설명을 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 교정 결과는 `CorrectionSuggestion` 계약으로 화면에 표시한다.
- [ ] 교정 전 문장과 교정 후 문장을 구분해 보여준다.
- [ ] 생성된 설명을 화면에 표시한다.
- [ ] 카드 목록은 Loading / Content / Empty / Error 상태에 맞춰 렌더링된다.
- [ ] 목업 결과와 실제 API 결과는 같은 화면 모델을 사용한다.
- [ ] 후보 목록 선택 UI를 만들지 않는다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-004 → 교정 결과 생성
- COR-005 → 교정 결과 카드 표시

---

# 구현 범위

## 포함 범위

- 교정 결과 카드 UI
- 교정 전/후 문장 표시
- 설명 표시
- 카드 목록 Empty / Error 표시
- mock 결과 기반 화면 확인

## 제외 범위 (Out of Scope)

- 카드 선택 상태
- Flashcard 실제 저장
- Session Memory 압축
- LangState 수치 계산
- Dashboard Summary 갱신

---

# Details

## 카드 역할

교정 결과 카드는 사용자가 교정 전 문장과 교정 후 문장을 비교하고,
왜 교정되었는지 짧은 설명으로 이해할 수 있게 한다.

## 사용 데이터

- `CorrectionSuggestion`
- 교정 전 문장
- 교정 후 문장
- 설명

## 카드 표시 기준

예시:

```text
Before: I went museum yesterday.
After: I went to the museum yesterday.
Why: 장소 앞에는 보통 전치사 to를 사용합니다.
```

실제 UI 문구는 디자인 톤에 맞게 조정할 수 있다.
다만 카드가 담아야 할 정보는 교정 전 문장, 교정 후 문장, 설명이다.

---

## 작업 지시

- 이 이슈에서는 카드의 표시만 완성하고 선택 상태는 `COR-006`에서 다룬다.
- 카드에는 교정 전 문장, 교정 후 문장, 설명이 명확히 구분되어야 한다.
- 설명은 `CorrectionSuggestion`에 포함된 값을 그대로 렌더링한다.
- 후보가 없는 Empty와 결과가 있는 Content를 화면에서 구분한다.
- mock 데이터로 카드 목록을 확인할 수 있어야 한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/components/
→ CorrectionResultCard

presentation/correction/
→ CorrectionScreen
→ CorrectionUiState
```

## UI 상태

```text
Loading
Content(suggestions)
Empty
Error
```

카드 표시는 `CorrectionSuggestion`만 사용하고 `CorrectionCandidate`를 직접 렌더링하지 않는다.

---

## 검증 기준

- `CorrectionSuggestion` 1개 이상이면 카드 목록이 표시된다.
- Empty 상태에서는 카드가 표시되지 않는다.
- 카드 선택 UI나 저장 버튼 동작은 이 이슈에서 구현하지 않는다.

---

# Edge Cases

- `CorrectionSuggestion` 목록이 비어 있음
- 교정 전 문장 또는 교정 후 문장이 비어 있음
- 설명이 비어 있거나 너무 김
- 카드가 많아져 스크롤이 필요한 상태
- Error 상태에서 이전 결과 카드가 남아 보임
