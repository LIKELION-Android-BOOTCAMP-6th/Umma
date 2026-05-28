# Demo Scenario - FLOW-CORRECTION

> 2차 스프린트 COR(Correction) 기능 데모/QA 기준 문서.
> 통합 데모(`DEMO_REAL_INTEGRATED_FLOW.md`)에 포함된 기본 흐름을 보완해, COR 백로그 AC 중 누락되기 쉬운 Empty/Error/Retry, mock fixture, 저장 실패, pending sync, Dashboard 토스트/주제 칩까지 검증한다.

현재 Correction 검증은 두 갈래로 나눈다.

- Real 정상 흐름: `devDebug`에서 실제 AI Chat 저장, 실제 AI 교정 생성, Flashcard 저장, Dashboard 복귀까지 확인한다.
- 재현성 있는 상태 전이/장애/빈 상태: `mockDebug`에서 Correction fake preset을 바꿔 확인한다.

`mockDebug` preset은 실제 AI 품질 검증용이 아니다. preset은 Correction 화면 상태, Empty/Error/Retry, 저장 실패, pending sync, Dashboard 완료 안내를 안정적으로 재현하기 위한 도구다.

---

## 1. 실행 기준

Real 정상 교정 흐름은 `devDebug` variant에서 확인한다.

- 실제 로그인 계정
- 실제 AI Chat final turn 저장
- 실제 Correction suggestion 생성
- 실제 Flashcard 저장 및 Dashboard summary 갱신
- Android Studio에서는 `Build Variants > :app > devDebug` 선택 후 실행

재현성 있는 상태 전이 데모는 `mockDebug` variant에서 확인한다.

```bash
./gradlew :app:installMockDebug
```

Android Studio에서는 `Build Variants > :app > mockDebug` 선택 후 실행한다.

Mock preset 변경 위치는 Correction 도메인의 demo preset 설정을 사용한다.

```kotlin
// app/src/main/java/com/app/umma/data/repository/fake/demo/correction/CorrectionDemoPreset.kt
object CorrectionDemoPresetConfig {
    val activePreset: CorrectionDemoPreset = CorrectionDemoPreset.Content
}
```

## 2. Mock preset 목록

| Preset | 목적 | 트리거 | 기대 결과 |
| --- | --- | --- | --- |
| `Content` | 교정 결과 카드 목록 확인 | Correction 진입 | `CorrectionSuggestion` 1개 이상 표시 |
| `EmptyInitial` | 교정 가능 세션 없음 확인 | Correction 진입 | Empty UI와 AI Chat 이동 CTA 표시 |
| `EmptyResult` | 후보/AI 결과 없음 확인 | Correction 진입 | 결과 없음 Empty 상태 표시 |
| `Error` | AI 요청/파싱/필드 누락 실패 확인 | Correction 진입 | Error UI와 Retry 표시 |
| `SaveFail` | 저장 완료 파이프라인 실패 확인 | 카드 선택 후 저장 | Dashboard 이동 없이 Retry 상태 유지 |
| `PendingSync` | sync/compression pending 비차단 확인 | 카드 선택 후 저장 | Dashboard 복귀, 사용자에게 실패 노출 없음 |
| `TopicTitleSuccess` | Dashboard 주제 칩 품질 확인 | 저장 성공 후 Dashboard | 짧은 주제 제목 표시 |
| `TopicTitleEmpty` | 주제 제목 없음/요약 실패 확인 | 저장 성공 후 Dashboard | 기존 topic 보존 또는 fallback 표시 |

---

## 3. Real 정상 교정 흐름

Variant: `devDebug`

1. AI Chat에서 USER final turn이 1개 이상 저장되도록 대화를 진행한다.
2. Dashboard로 복귀한다.
3. 교정 대기 카드가 교정 가능 상태인지 확인한다.
4. 교정 대기 카드를 클릭해 Correction 화면에 진입한다.
5. 중앙 CircularProgressIndicator와 단계별 로딩 문구가 표시되는지 확인한다.
6. 안내 문구가 3초 간격으로 순차 전환되고 말줄임표 애니메이션이 보이는지 확인한다.
7. 최소 로딩 후 교정 결과 카드 목록이 표시되는지 확인한다.
8. 각 카드에서 교정 전 문장, 교정 후 문장, 설명이 구분되어 보이는지 확인한다.
9. 후보 목록 선택 UI가 노출되지 않는지 확인한다.
10. 저장 버튼이 선택 전 비활성 상태인지 확인한다.
11. 교정 카드 2개 이상을 선택한다.
12. 선택한 카드가 시각적으로 구분되고 저장 버튼이 활성화되는지 확인한다.
13. 선택한 카드 중 하나를 다시 눌러 선택 해제가 되는지 확인한다.
14. 다시 2개 이상 선택한 뒤 저장 버튼을 클릭한다.
15. 저장 진행 상태가 표시되는지 확인한다.
16. 저장 성공 후 별도 Done 완료 화면 없이 Dashboard로 복귀하는지 확인한다.
17. Dashboard 위에 `학습 카드 N개가 저장되었어요` 형식의 커스텀 토스트가 표시되는지 확인한다.
18. 토스트가 1.5초 뒤 자동으로 사라지는지 확인한다.
19. Dashboard Flashcard 카드의 저장/복습 수가 즉시 갱신되는지 확인한다.
20. 최근 AI 대화 카드의 주제 칩이 단어 하나가 아닌 짧은 주제 제목으로 표시되는지 확인한다.

합격 기준:

- Correction 진입 시 선택 언어 기준 `SessionSummary.correctionAvailable`을 사용해 교정 가능 여부를 판단한다.
- Ready 이후 사용자 추가 입력 없이 교정 생성이 자동 시작된다.
- AI 응답은 `CorrectionSuggestion` 목록으로 변환되고, 필수 필드가 채워진 카드가 표시된다.
- 선택한 suggestion만 Flashcard 저장 요청에 포함된다.
- 저장 성공 시 Dashboard로 1회만 복귀한다.
- 완료 안내는 Correction 화면이 아니라 Dashboard 커스텀 토스트로 표시된다.
- 저장된 카드 수, due 카드 수, 최근 대화 주제 summary가 Dashboard에 반영된다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-COR-01 | COR-001 / COR-002 | AI Chat 후 Correction 진입 | 선택 언어와 `SessionSummary.correctionAvailable` 기준으로 자동 교정 생성 시작 |  |  |
| TC-COR-02 | COR-FIX-04 | Correction 진입 후 로딩 관찰 | 중앙 로딩, 단계별 문구, 말줄임표 애니메이션 표시 |  |  |
| TC-COR-03 | COR-002 / COR-003 | 교정 카드 확인 | `nativeText`, `afterText`, `explanation`이 구분된 카드 1개 이상 표시 |  |  |
| TC-COR-04 | COR-004 | 카드 선택/해제 | 선택 상태 시각 구분, 재클릭 시 해제, 0개면 저장 비활성 |  |  |
| TC-COR-05 | COR-005 / COR-006 | 카드 2개 이상 저장 | 선택한 suggestion만 저장 요청으로 변환, 저장된 Flashcard ID 반환 |  |  |
| TC-COR-06 | COR-007 / COR-DASH-FIX-01 | 저장 성공 후 Dashboard 확인 | Dashboard 복귀 1회, 완료 토스트 1.5초 표시 후 사라짐 |  |  |
| TC-COR-07 | COR-FIX-01 / COR-FIX-02 / DASH-FIX-01 | Dashboard summary 확인 | saved/due 수 갱신, 최근 대화 주제 칩이 짧은 제목으로 표시 |  |  |

---

## 4. Mock 기본 카드 렌더링 흐름

Preset: `Content`

1. `CorrectionDemoPresetConfig.activePreset = CorrectionDemoPreset.Content`로 설정한다.
2. `mockDebug`로 앱을 실행한다.
3. Dashboard에서 교정 대기 카드 또는 Correction 진입 경로를 연다.
4. Loading 이후 교정 카드 목록이 표시되는지 확인한다.
5. 카드마다 교정 전 문장, 교정 후 문장, 설명이 표시되는지 확인한다.
6. 내부 후보 목록이나 후보 선택 UI가 화면에 노출되지 않는지 확인한다.
7. 카드가 여러 개일 때 스크롤이 가능한지 확인한다.
8. 카드 선택 상태가 `CorrectionSuggestion` id 기준으로 유지되는지 확인한다.

합격 기준:

- fake repository와 fixture만으로 Content 상태를 재현할 수 있다.
- ViewModel과 Composable은 mock/real 구현체를 직접 구분하지 않는다.
- `CorrectionSuggestionFixtureBuilder`의 샘플 데이터가 화면과 ViewModel 검증에 동일하게 사용된다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-COR-08 | COR-000 / COR-003 | `Content` preset 실행 | fixture 기반 카드 목록 표시 |  |  |
| TC-COR-09 | COR-003 | 카드 내용 확인 | 교정 전/후/설명이 구분되어 표시 |  |  |
| TC-COR-10 | COR-003 | 후보 UI 노출 여부 확인 | 내부 후보 목록 선택 UI가 보이지 않음 |  |  |
| TC-COR-11 | COR-004 | 같은 문장 텍스트 카드 선택 | 텍스트가 같아도 id 기준으로 선택 상태 유지 |  |  |

---

## 5. Empty / Error / Retry 흐름

Preset: `EmptyInitial`, `EmptyResult`, `Error`

1. `EmptyInitial` preset으로 실행한다.
2. 선택 언어 없음, 세션 없음, 또는 `correctionAvailable == false` 조건에서 Empty UI가 표시되는지 확인한다.
3. Empty UI에서 AI Chat 이동 CTA가 보이는지 확인한다.
4. 화면 재진입 또는 recomposition 후 초기화 요청이 중복 실행되지 않는지 확인한다.
5. `EmptyResult` preset으로 변경 후 실행한다.
6. 교정 생성 결과가 비어 있을 때 카드 화면이 아닌 결과 없음 상태가 표시되는지 확인한다.
7. `Error` preset으로 변경 후 실행한다.
8. AI 요청 실패, 응답 파싱 실패, 필수 필드 누락, `candidateId` 불일치가 Error UI와 Retry로 연결되는지 확인한다.
9. Retry를 눌렀을 때 같은 Session Memory와 현재 선택 언어 기준으로 다시 요청하는지 확인한다.
10. Retry 성공 fixture로 전환하면 Content 상태로 복구되는지 확인한다.

합격 기준:

- 초기 Empty 조건은 crash 없이 Empty UI로 분기한다.
- 결과 Empty와 초기 Empty는 사용자가 다음 행동을 이해할 수 있게 안내한다.
- Error 상태에는 Retry 액션이 제공된다.
- Retry는 현재 선택 언어와 같은 Session Memory 기준으로 다시 수행된다.
- 초기화/교정 생성 요청은 recomposition으로 중복 실행되지 않는다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-COR-12 | COR-001-B | `EmptyInitial` preset | Empty UI + AI Chat 이동 CTA 표시 |  |  |
| TC-COR-13 | COR-001-B | 화면 재진입/recomposition | 초기 로딩/초기화 요청 중복 없음 |  |  |
| TC-COR-14 | COR-002-B / COR-003-B | `EmptyResult` preset | 결과 없음 Empty 상태 표시 |  |  |
| TC-COR-15 | COR-002-B | `Error` preset | Error UI + Retry 표시 |  |  |
| TC-COR-16 | COR-002-B | Retry 실행 | 같은 Session Memory와 현재 선택 언어 기준으로 재요청 |  |  |
| TC-COR-17 | COR-002 | `candidateId` 불일치 fixture | Error 상태로 전환 |  |  |

---

## 6. 저장 실패 / Pending 비차단 흐름

Preset: `SaveFail`, `PendingSync`

1. `SaveFail` preset으로 실행한다.
2. 교정 카드 1개 이상을 선택한다.
3. 저장 버튼을 클릭한다.
4. 저장 진행 상태가 표시되는지 확인한다.
5. 완료 파이프라인 실패 후 Dashboard로 이동하지 않고 Retry 상태에 남는지 확인한다.
6. 선택 상태와 카드 목록이 유지되어 재시도 가능한지 확인한다.
7. 저장 버튼을 빠르게 여러 번 눌러도 중복 요청이 발생하지 않는지 logcat 또는 fake 호출 횟수로 확인한다.
8. `PendingSync` preset으로 변경 후 실행한다.
9. 저장 성공 후 Firestore sync 또는 Session Memory compression pending이 있어도 Dashboard로 복귀하는지 확인한다.
10. pending 상태가 사용자 Error UI로 노출되지 않는지 확인한다.

합격 기준:

- 저장 요청 변환 실패 또는 완료 실패는 Retry 상태로 남는다.
- 저장 중 버튼 중복 클릭은 차단된다.
- 저장 대상 0개이면 완료 파이프라인을 호출하지 않는다.
- sync/compression pending은 사용자 흐름을 막지 않는다.
- pending은 내부 로그나 개발 확인용 상태로만 남는다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-COR-18 | COR-005-B | 저장 버튼 연타 | 저장/완료 요청 1회만 발생 |  |  |
| TC-COR-19 | COR-005-A | 선택 0개 상태 저장 시도 | 저장 버튼 비활성, 완료 파이프라인 미호출 |  |  |
| TC-COR-20 | COR-006-B | `SaveFail` preset | Dashboard 이동 없이 Retry 상태 유지 |  |  |
| TC-COR-21 | COR-007-B | `PendingSync` preset | pending이 있어도 Dashboard 복귀 |  |  |
| TC-COR-22 | COR-007-B | pending 상태 UI 확인 | 사용자에게 Error로 노출되지 않음 |  |  |

---

## 7. Dashboard 복귀 / 완료 토스트 / Summary 갱신 흐름

Preset: `Content`, `PendingSync`, `TopicTitleSuccess`, `TopicTitleEmpty`

1. `Content` 또는 `TopicTitleSuccess` preset으로 실행한다.
2. 교정 카드 3개를 선택한다.
3. 저장 버튼을 클릭한다.
4. 저장 성공 후 Dashboard로 복귀하는지 확인한다.
5. Dashboard 위에 `학습 카드 3개가 저장되었어요` 토스트가 표시되는지 확인한다.
6. 토스트가 1.5초 뒤 자동으로 사라지는지 확인한다.
7. 화면 회전 또는 recomposition 후 같은 토스트가 다시 표시되지 않는지 확인한다.
8. Dashboard Flashcard 카드의 `savedFlashcards`와 `dueFlashcards`가 새 카드 수만큼 반영되는지 확인한다.
9. 최근 대화 카드의 주제 칩이 `여행 계획`, `카페 주문 연습` 같은 2~5어절 제목으로 표시되는지 확인한다.
10. `TopicTitleEmpty` preset으로 실행한다.
11. AI 요약 실패 또는 빈 title일 때 기존 recentTopic을 이상한 단어로 덮어쓰지 않는지 확인한다.
12. topic title이 없으면 Dashboard의 기존 Empty/fallback 표시 정책을 따르는지 확인한다.

합격 기준:

- 완료 이벤트는 Dashboard에서 한 번만 소비된다.
- 완료 토스트는 실제 저장된 카드 수를 반영한다.
- 저장 카드 수가 0개인 예외 성공 이벤트는 기본 완료 메시지로 fallback한다.
- Dashboard Snackbar와 완료 토스트가 동시에 발생해도 주요 UI가 깨지지 않는다.
- 최근 대화 주제 칩은 단어 키워드가 아니라 세션 요약 기반 짧은 제목을 사용한다.
- AI 요약 실패는 교정 완료 흐름을 실패로 만들지 않는다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-COR-23 | COR-DASH-FIX-01 | 카드 3개 저장 | Dashboard 복귀 후 `학습 카드 3개가 저장되었어요` 토스트 표시 |  |  |
| TC-COR-24 | COR-DASH-FIX-01 | 1.5초 대기 | 토스트 자동 사라짐 |  |  |
| TC-COR-25 | COR-DASH-FIX-01 | 화면 회전/recomposition | 같은 토스트 재노출 없음 |  |  |
| TC-COR-26 | COR-FIX-01 | 저장 직후 Dashboard 카드 확인 | savedFlashcards/dueFlashcards 즉시 갱신 |  |  |
| TC-COR-27 | COR-FIX-01 | 카드 여러 개 저장 | 선택한 모든 suggestion의 `afterText`가 LS 분석 입력에 반영 |  |  |
| TC-COR-28 | DASH-FIX-01 | `TopicTitleSuccess` preset | 단어 하나가 아닌 짧은 주제 제목 표시 |  |  |
| TC-COR-29 | DASH-FIX-01 | `TopicTitleEmpty` preset | 기존 topic 보존 또는 fallback 표시 |  |  |
| TC-COR-30 | COR-FIX-01 / DASH-FIX-01 | 요약 실패 fixture | 교정 완료는 성공, 이상한 fallback 단어로 덮어쓰지 않음 |  |  |

---

## 8. 데모 선택 가이드

Real 통합 발표:

- `devDebug`
- `DEMO_REAL_INTEGRATED_FLOW.md`의 AI Chat -> Correction -> Flashcard -> Dashboard 흐름을 기준으로 진행한다.
- 실제 AI 교정 품질, latency, 저장 결과, Dashboard 갱신을 확인한다.

COR 단독 발표 기본 preset:

- `Content`

빈 상태를 보여줄 때:

- `EmptyInitial`
- `EmptyResult`

AI/파싱 실패와 Retry를 보여줄 때:

- `Error`

저장 실패를 보여줄 때:

- `SaveFail`

pending 비차단을 보여줄 때:

- `PendingSync`

Dashboard topic title 품질을 보여줄 때:

- `TopicTitleSuccess`
- `TopicTitleEmpty`

테스트 체크리스트:

- `docs/Demo/TestSheet/TEST_FLOW_CORRECTION.md`를 기준으로 Pass/Fail을 기록한다.
