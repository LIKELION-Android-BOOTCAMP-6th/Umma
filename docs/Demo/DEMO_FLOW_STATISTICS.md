# Demo Scenario — FLOW-STATISTICS

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름의 TODO 시나리오.
> 기본은 Real 바인딩 (시드된 `StatisticsHistory` 보유 계정 + `LangState.external`).
> history 부족 / 진입 실패 / sync 실패 / 언어 변경 분기는 Mock fixture로 시연.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: 현재 선택 언어 기준으로 최소 한 지표(`grammarAccuracy` 권장)에 history point 5개 이상 누적된 상태. 다른 지표는 history 부족 상태로 두면 Empty chart도 자연스럽게 시연 가능.
> - Mock 토글: `StatisticsRepository` fake 구현 + `FakeFixtures` (Empty / FetchFailure / PendingSync / LangMismatch) — 현재 미존재, DASH 스타일 `RepositoryModule` 토글 추가 필요.
> - Mock fixture가 필요한 시나리오: 6·7·8·9. 그 외는 Real로 시연.
> - 발표 후 Real 바인딩으로 원복.

---

## 시나리오 1 — Statistics 진입 + 요약 카드 (STAT-001 / STAT-002)

1. Dashboard에서 "언어 성취율" 카드 클릭
2. Statistics 화면 진입 → Loading skeleton 노출
3. `GetStatisticsOverviewUseCase` 결과 수신 → 5개 지표 요약 카드 표시 (Content)
   - `vocabularyLevel` (예: A2), `grammarAccuracy` (예: 72%), `expressionRange` (예: 34 expressions), `fluencyScore` (예: 68), `naturalnessScore` (예: 61)
4. 각 카드는 클릭 가능한 상태로 렌더링됨 (시각적 affordance 확인)
5. 현재 선택 언어가 AppBar/헤더에 명확히 표시되는지 확인

---

## 시나리오 2 — 지표 카드 클릭 + line chart (STAT-003)

> Real 환경: `grammarAccuracy`에 history 5개 이상 시드된 상태 필요.

1. `Grammar Accuracy` 카드 클릭 → 선택 지표 상태 갱신 (ChartLoading)
2. `StatisticsHistory`에서 해당 지표만 `MetricHistoryPoint` 목록으로 변환
3. line chart 렌더링 — x축은 `recordedAt`, y축은 metric 값 (Chart)
4. 차트 영역에서 다른 언어의 history가 섞이지 않는지 확인

---

## 시나리오 3 — 지표 간 전환 (STAT-003)

1. 시나리오 2 상태에서 `Fluency Score` 카드 클릭
2. 차트가 새로운 지표 기준으로 재렌더링 (ChartLoading → Chart)
3. 빠른 연속 클릭으로 여러 지표를 번갈아 탭 → 마지막 선택 지표만 표시되고 이전 fetch 결과가 덮어쓰지 않음 (logcat `stale fetch dropped`)
4. `vocabularyLevel` 카드 클릭 → A1~C2 라벨은 유지, chart 내부 값은 1~6 ordinal로 변환된 형태로 그려지는지 확인

---

## 시나리오 4 — history 부족 → Empty chart (STAT-003 Empty)

> Real 환경: history point가 0~1개인 지표를 자연스럽게 활용 (예: 갓 진입한 신규 언어의 `expressionRange`).

1. 시나리오 1 상태에서 `Expression Range` 카드 클릭
2. history 부족 (2개 미만) → 선이 그려지지 않음
3. Empty chart 상태와 안내 문구 표시 (Empty)
4. 다른 지표 카드 클릭 시 정상 차트로 복귀

---

## 시나리오 5 — 재진입 + local cache 우선 + background sync (STAT-004)

1. 시나리오 2 상태에서 뒤로가기로 Dashboard 복귀
2. 다시 언어 성취율 카드 클릭 → Statistics 재진입
3. local cache history로 즉시 카드 + 차트 영역 렌더링 (Content, remote fetch 대기 없음)
4. background sync로 새 point가 들어오면 차트가 갱신되는지 확인 (logcat `history merged`)
5. 같은 화면에서 화면 빠른 이탈/재진입에도 깨지지 않는지 확인

---

## 시나리오 6 — Firestore sync 실패 → non-blocking (STAT-004 PendingSync)

> Mock 토글 필요: `FakeFixtures.fetchFailure` (또는 디바이스 비행기 모드 활용)

1. Statistics 진입 → local cache 기반으로 카드 + 차트 즉시 표시 (Content)
2. background fetch 실패 → 화면 실패로 노출되지 않음 (ContentWithPendingSync)
3. 작은 sync 상태 표시기만 노출되거나 logcat에서만 확인
4. 기존 차트 데이터는 사라지지 않음 (Fallback)

---

## 시나리오 7 — 언어 변경 후 통계 갱신 (STAT-004 / DASH-006 연계)

1. Dashboard에서 학습 언어를 영어 → 일본어로 변경
2. Statistics 카드 클릭 → 일본어 기준 ExternalMetrics + history로 화면 재구성
3. 직전 영어 chart point가 일본어 화면에 남지 않는 것 확인 (logcat `lang mismatch dropped`)
4. 일본어 history 부족 시 자연스럽게 시나리오 4 Empty 상태로 표시

---

## 시나리오 8 — ExternalMetrics 비어있음 (STAT-002 Empty)

> Mock 토글 필요: `FakeFixtures.emptyExternalMetrics` (신규 언어 / 활동 0인 상태)

1. Statistics 진입 → 5개 요약 카드는 모두 렌더링되지만 값 영역에 Empty 표시
2. 일부 카드만 값이 있고 나머지는 Empty인 혼합 상태도 확인 (`isAvailable = false`)
3. Empty 값 카드 클릭 → 시나리오 4와 동일한 Empty chart로 전이

---

## 시나리오 9 — 진입 실패 → Retry (STAT-001 Error)

> Mock 토글 필요: `FakeFixtures.overviewLoadFailure` (`selectedLearningLanguage` 없음 또는 overview load 실패)

1. Statistics 진입 시도 → 초기 상태 로드 실패 (Error)
2. 재시도 액션 노출 (Error + Retry)
3. fixture 정상 토글 후 Retry → 정상 진입 (Success)
4. local cache도 없고 기본 화면 구성 불가한 경우만 FatalError로 분기되는지 확인
