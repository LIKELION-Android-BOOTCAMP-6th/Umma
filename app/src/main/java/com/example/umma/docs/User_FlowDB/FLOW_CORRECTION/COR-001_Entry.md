# [Feature] COR-001 Correction 진입 및 초기 상태

## User Story

사용자는 Dashboard의 교정 대기 카드나 하단 탭을 통해 Correction 화면에 진입했을 때,
현재 선택 언어 기준으로 교정 가능한 대화가 있는지 즉시 확인할 수 있다.

---

## 완료 기준(AC)

- [ ] 기존 `feedback` 화면/라우트 명칭을 Correction 작업 범위 안에서 정리한다.
- [ ] Correction 화면 진입 시 Global Learning State에서 `selectedLearningLanguage`를 확인한다.
- [ ] `SessionSummary.correctionAvailable`을 기준으로 교정 가능 여부를 판단한다.
- [ ] `DashSummary.correctionAvailable`은 Dashboard 표시용 값으로만 참조한다.
- [ ] 현재 선택 언어의 `LangState` snapshot을 로드한다.
- [ ] 현재 선택 언어의 Session Memory 조회 준비 상태를 확인한다.
- [ ] 언어 없음, 세션 없음, 교정 불가 상태는 Empty UI로 분기한다.
- [ ] 초기 로딩 중 중복 요청과 중복 초기화가 방지된다.

---

## 구현 범위

### 포함

- `presentation/feedback` → `presentation/correction` 명칭 정리
- `FeedbackListScreen` → `CorrectionScreen` 기준 화면 정리
- `Route.FeedbackList` / `Route.FeedbackGraph` 정리
- `CorrectionViewModel` 초기 상태 구성
- selected language / SessionSummary / LangState snapshot 로드
- Loading / Empty / Error 상태

### 제외

- 교정 결과 생성
- 교정 결과 카드 UI
- Flashcard 저장
- Session Memory 압축
- Dashboard 카드 구현

---

## 구현 가이드

```text
Correction 화면 진입
→ GlobalLangState 관찰
→ selectedLearningLanguage 확인
→ currentSessionSummary() 확인
→ currentLangState() 확인
→ Content 준비 후 조건 미충족 시 Empty 표시
```

---

## Empty 기준

- `selectedLearningLanguage`가 없음
- 현재 선택 언어의 `SessionSummary`가 없음
- `SessionSummary.correctionAvailable == false`
- 현재 선택 언어의 Session Memory가 없음
- `LangState` snapshot이 없음

Empty 상태는 짧은 안내와 AI Chat 이동 CTA를 제공할 수 있다.
