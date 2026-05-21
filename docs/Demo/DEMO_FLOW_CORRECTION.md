# Demo Scenario — FLOW-CORRECTION

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름의 TODO 시나리오.
> 기본은 Real 바인딩 (AI Chat 직후 진입 + 실제 AI 교정 API). 후보 없음 / AI 실패 / 저장 실패 분기는 Mock fixture로 시연.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: AI Chat에서 final turn 1개 이상 저장 완료된 상태 (`SessionSummary.correctionAvailable == true`).
> - Mock 토글: `CorrectionRepository` fake 구현 + `CorrectionSuggestionFixtureBuilder` (Content / Empty / Error 픽스처) — 현재 미존재, DASH 스타일 `RepositoryModule` 토글로 추가 필요.
> - Mock fixture가 필요한 시나리오: 4·5·6·7·8. 그 외는 Real로 시연.
> - 발표 후 Real 바인딩으로 원복.

---

## 시나리오 1 — Correction 진입 + 결과 표시 (COR-001 / COR-002 / COR-003)

> Real 흐름: AI Chat에서 한두 턴 대화한 직후 진입.

1. AI Chat에서 사용자/AI turn 1~2회 확정 → 화면 종료 또는 Dashboard 경유
2. Dashboard에서 "교정 대기" 카드 클릭 (우측 상단 빨간 점이 붙어있는 상태)
3. Correction 화면 진입 → 초기 로드 (Loading)
4. `SessionSummary.correctionAvailable == true` 확인 → Ready
5. 사용자 추가 입력 없이 곧바로 교정 결과 생성 시작 (Loading)
6. 실제 AI 응답 파싱 완료 → `CorrectionSuggestion` 카드 1개 이상 표시 (Content)
7. 카드에 교정 전 문장 / 교정 후 문장 / 짧은 설명이 명확히 구분되어 보이는지 확인

---

## 시나리오 2 — 카드 선택 + Flashcard 저장 (COR-004 / COR-005 / COR-006)

1. 카드 목록에서 1개 카드 탭 → 선택 상태 시각적으로 강조 (Content)
2. 저장 버튼 활성화 확인 (선택 0개일 때는 비활성)
3. 같은 카드 다시 탭 → 선택 해제, 저장 버튼 비활성화 복귀
4. 카드 2개 다시 선택 → 저장 버튼 클릭 (Preparing)
5. `CompleteCorrectionUseCase` 호출 → 새 Flashcard local first 저장 (Completing)
6. 로컬 완료 성공 결과 수신 → Done 상태 (Success)
7. 빠른 연속 클릭 시 중복 완료 요청 차단 확인 (logcat `in-flight blocked`)

---

## 시나리오 3 — 완료 후 Dashboard 복귀 (COR-007)

1. 시나리오 2 Done 직후 Dashboard로 자동 복귀 (Success)
2. Dashboard에서 Flashcard 학습 카드 due 수치 갱신 확인 (저장한 카드 수만큼 증가)
3. 교정 대기 카드의 빨간 점이 사라지거나 갱신되는지 확인
4. completion success 이벤트가 1회만 소비됨 (Compose recomposition 시 중복 navigation 없음)

---

## 시나리오 4 — 교정 불가 Empty (COR-001 Empty)

> Mock 토글 필요: `FakeFixtures.correctionUnavailable` (`SessionSummary.correctionAvailable == false`)

1. Dashboard에서 Correction 진입
2. 초기 로드 후 Empty 상태 표시 (Empty)
3. AI Chat 이동 CTA 노출 → 클릭 시 AI Chat 화면으로 이동 가능

---

## 시나리오 5 — AI 응답 실패 → Retry (COR-002 Error)

> Mock 토글 필요: `FakeFixtures.aiFailure` (AI 요청 실패 / 파싱 실패 / 필수 필드 누락 중 택일)

1. Correction 진입 → 결과 생성 시작 (Loading)
2. AI 호출 실패 → Error 상태 + Retry 액션 노출 (Error + Retry)
3. Retry 클릭 → 같은 Session Memory + 같은 선택 언어 기준으로 재요청
4. 성공 fixture로 토글 변경 후 다시 Retry → 정상 결과 카드 표시 (Success)

---

## 시나리오 6 — 결과 없음 → Empty (COR-002 Empty)

> Mock 토글 필요: `FakeFixtures.emptySuggestions` (AI 응답은 정상이나 결과 0개)

1. Correction 진입 → 결과 생성 (Loading)
2. AI 응답이 빈 `CorrectionSuggestion` 목록으로 반환됨
3. 카드 화면으로 넘어가지 않고 Empty 상태 표시 (Empty)

---

## 시나리오 7 — 저장 실패 → Retry (COR-006 Retry)

> Mock 토글 필요: `FakeFixtures.completeCorrectionFailure`

1. 시나리오 2 흐름을 따라가서 저장 버튼 클릭
2. `CompleteCorrectionUseCase` 실패 결과 반환 (Completing → Retry)
3. 선택 상태와 카드 목록은 유지됨 (Done으로 넘어가지 않음)
4. 재시도 버튼 클릭 → 동일 입력으로 완료 파이프라인 재호출, 성공 시 Dashboard 복귀

---

## 시나리오 8 — Firestore sync 실패만 발생 (COR-006 / COR-007 PendingSync)

> Mock 토글 필요: `FakeFixtures.completeCorrectionLocalOnly` (local 완료 성공 + Firestore sync 실패)

1. 시나리오 2 흐름 + 저장 버튼 클릭
2. local 저장 성공 + Firestore sync 실패 응답 수신
3. 사용자 입장에서는 Done 상태로 인식되고 Dashboard 복귀 정상 동작 (Success)
4. 내부 sync pending 상태는 logcat 또는 개발자 디버그 영역에서만 확인
5. Dashboard의 학습 카드 due 수치는 local 기준으로 갱신됨
