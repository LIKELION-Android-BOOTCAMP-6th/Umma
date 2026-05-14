# [Feature] COR-001 교정 진입 및 세션 준비

## User Story

사용자는 Dashboard나 AI Chat 이후 교정 화면에 진입했을 때,
현재 선택 언어의 Session Memory와 LangState snapshot을 기반으로 교정 가능 상태가 준비되기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `selectedLearningLanguage` 기준으로 교정 화면이 초기화된다.
- [ ] `SessionSummary`에서 교정 가능 여부를 확인하고, `DashSummary`는 Dashboard 표시값으로만 참조한다.
- [ ] LangState snapshot을 로드한다.
- [ ] 교정 가능한 Session Memory가 없으면 Empty 상태를 표시한다.
- [ ] Global Learning State에서 확인한 `selectedLearningLanguage`와 Session Memory의 `language`가 다르면 안전하게 중단한다.
- [ ] 교정 진입 중 중복 로딩 또는 중복 초기화가 방지된다.

---

# Flow (링크)

- SYS-CORRECTION-INFRA
- COR-001 → 교정 진입 및 세션 준비

---

# 구현 범위

## 포함 범위

- 현재 선택 언어 확인
- `SessionSummary` 로드
- LangState snapshot 로드
- 교정 가능 여부 판단
- Empty / Loading / Error 상태 처리
- 교정 진입 전 언어 일치성 검증

## 제외 범위 (Out of Scope)

- 교정 후보 추출
- AI 교정 요청
- Flashcard 저장
- Session Memory 압축 실행
- LangState 수치 계산
- 교정 UI 상세 렌더링

> 이 이슈는 교정 화면이 “들어갈 수 있는 상태인지”만 책임진다.
> 교정 화면의 실제 후보 추출과 결과 생성은 COR-002 / COR-003에서 다룬다.

---

# Details

## 진입 원칙

교정 화면은 route 인자에 의존하지 않고,
Global Learning State의 `UserLangPref.selectedLang`를 기준으로 현재 선택 언어를 확인한다.

```text
GlobalLangState
→ userPref.selectedLang
→ currentSessionSummary()
→ currentLangState()
→ 필요 시 currentDashSummary()
```

Dashboard에서 넘어온 상태가 있더라도, 최종 기준은 전역 상태의 selected 언어다.
교정 가능 여부의 기준은 `SessionSummary`이며, `DashSummary`는 Dashboard 표시용 보조값으로만 본다.
두 값은 역할이 다르지만, 교정 완료 이후에는 같은 학습 상태 갱신 흐름 안에서 함께 맞춰져야 한다.

---

## Empty 정책

다음 경우 Empty 상태를 표시한다.

- 현재 선택 언어가 없다
- 현재 선택 언어의 Session Memory가 없다
- 현재 선택 언어의 LangState snapshot이 없다
- 교정 가능 상태가 false다

Empty 상태는 사용자가 교정 기능 자체를 오해하지 않도록,
교정 불가 이유를 짧게 안내할 수 있다.

---

## 현재 코드 기준 메모

- 현재 코드에는 `FeedbackListScreen`, `Route.FeedbackList`, `Route.FeedbackGraph`, `onNavigateToFeedbackList`가 존재한다.
- 이 이슈는 교정 진입 준비를 다루며, 이후 `Correction` 기준 명칭으로 정리되는 화면 구조와 연결된다.
- 언어 컨텍스트는 route 인자가 아니라 Global Learning State에서 읽는다.
