# [Feature] COR-001 Correction 화면 진입 경로 정리

## User Story

사용자는 Dashboard 교정 대기 카드나 하단 탭을 통해 Correction 화면에 진입할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 기존 `presentation/feedback` 화면 폴더를 Correction 작업 범위 안에서 정리한다.
- [ ] `FeedbackListScreen` 역할을 `CorrectionScreen` 기준으로 정리한다.
- [ ] `Route.FeedbackList` / `Route.FeedbackGraph` 계열 명칭을 Correction 기준으로 정리한다.
- [ ] `onNavigateToFeedbackList` 계열 콜백을 Correction 기준으로 정리한다.
- [ ] Dashboard 교정 대기 카드에서 Correction 화면으로 이동할 수 있다.
- [ ] 하단 탭에서 Correction 화면으로 이동할 수 있다.
- [ ] 이 이슈에서는 상태 로드와 교정 결과 생성을 구현하지 않는다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-001 → Correction 화면 진입 경로 정리
- SYS-CORRECTION-INFRA → 명칭 정리 기준

---

# 구현 범위

## 포함 범위

- 화면 파일명, 패키지, 라우트, 콜백 명칭 정리
- Correction 화면 shell 연결
- 진입 경로 smoke 확인

## 제외 범위 (Out of Scope)

- Global Learning State 로드
- SessionSummary 판단
- 교정 결과 카드 UI
- Flashcard 저장

---

# Details

## 화면 역할

Correction 화면은 AI Chat 이후 저장된 대화를 기반으로 교정 결과를 보여주는 화면이다.
이 이슈에서는 실제 교정 흐름이 아니라 화면 진입 경로와 명칭 정리를 완료한다.

## 진입 경로

- Dashboard 교정 대기 카드
- 하단 탭 Correction 메뉴

두 경로는 같은 `Route.Correction`으로 연결한다.

## 구현 가이드

```text
Dashboard correction card / Bottom tab
→ Route.Correction
→ CorrectionScreen shell
```

화면 진입만 확인할 수 있으면 이 이슈는 완료로 본다.
초기 상태와 Empty 분기는 `COR-002`에서 다룬다.

---

## 작업 지시

- 기존 Feedback 화면이 실제 교정 화면 역할을 하고 있다면 파일명과 라우트명을 Correction 기준으로 정리한다.
- 화면 내부에는 아직 실제 교정 데이터를 연결하지 말고, 진입 여부를 확인할 수 있는 최소 UI만 둔다.
- Dashboard 카드와 하단 탭이 같은 Correction route로 이동하도록 맞춘다.
- 기존에 연결된 placeholder 동작이 있다면 삭제보다 Correction route로 자연스럽게 교체하는 방향을 우선한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ CorrectionScreen

core/navigation/
→ Route.Correction
→ Correction navigation callback
```

## 구현 포인트

- 기존 `Feedback` 명칭은 Correction 작업 범위 안에서만 정리한다.
- Dashboard 카드와 하단 탭은 같은 route를 바라보게 한다.
- 화면 shell은 이후 `COR-002`에서 ViewModel 상태와 연결할 수 있게 비워둔다.

---

## 검증 기준

- 앱 실행 후 Dashboard 교정 대기 카드에서 Correction 화면으로 이동할 수 있다.
- 하단 탭에서 Correction 화면으로 이동할 수 있다.
- 화면 진입만으로 Global Learning State 조회나 AI 요청이 실행되지 않는다.

---

# Edge Cases

- Dashboard 카드와 하단 탭이 서로 다른 route로 연결됨
- 기존 Feedback route가 남아 중복 화면이 생김
- 화면 진입과 동시에 교정 결과 생성 로직이 실행됨
- 빠르게 여러 번 클릭해 Navigation이 중복 실행됨
