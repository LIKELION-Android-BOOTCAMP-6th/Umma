# [Feature] COR-004 Flashcard 저장 연계

## User Story

사용자는 교정 결과 중 저장할 만한 표현을 Flashcard로 남겨,
이후 복습 흐름으로 자연스럽게 이어가고 싶다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 사용자가 선택한 교정 결과만 Flashcard 저장 대상으로 만든다.
- [ ] Flashcard는 현재 선택 언어 기준으로 저장된다.
- [ ] 저장은 local first 정책을 따른다.
- [ ] Flashcard 저장을 위한 Repository 계약과 저장 요청 모델을 준비한다.
- [ ] 저장 후 `FlashcardSummary`와 `DashSummary` 갱신 요청이 가능하다.
- [ ] 저장 실패 시 사용자가 다시 시도할 수 있다.
- [ ] 저장 직후 UI가 성공 상태를 사용자에게 보여준다.

---

# Flow (링크)

- SYS-CORRECTION-INFRA
- COR-004 → Flashcard 저장 연계

---

# 구현 범위

## 포함 범위

- 교정 결과 중 저장 대상을 선택하는 UI 계약
- Flashcard 저장 요청 모델
- Flashcard Repository 계약
- local first 저장 흐름
- 저장 성공 / 실패 상태 처리
- `FlashcardSummary` / `DashSummary` 갱신 요청

## 제외 범위 (Out of Scope)

- Flashcard 반복학습 UI
- 복습 스케줄 계산
- SRS 알고리즘
- Session Memory 압축
- AI 교정 요청

> 이 이슈는 저장 자체를 준비하는 일을 담당한다.
> 저장된 Flashcard의 복습 정책은 SRS / Flashcard system flow에서 구체화한다.

---

# Details

## 저장 원칙

교정에서 저장되는 Flashcard는 사용자가 고른 결과만 대상으로 한다.

```text
CorrectionSuggestion
→ 사용자가 저장 항목 선택
→ Flashcard 저장 요청 생성
→ local first 저장
→ Firestore sync
→ Summary 갱신 요청
```

저장 계약은 교정 flow 안에서 준비되어야 한다.

---

## local first 정책

- 우선 Room에 저장한다.
- Firebase sync는 비동기로 이어서 수행한다.
- 저장 중 실패가 나도 사용자가 결과를 잃지 않도록 한다.

---

## 현재 코드 기준 메모

- 현재 코드에 Flashcard 저장 전용 Repository 계약이 없으면, 이 이슈에서 먼저 확정한다.
- 교정 결과 표시 모델과 저장 요청 모델은 분리한다.
- 저장 후 Summary 갱신 경계는 LearningStateRepo와 연계한다.

