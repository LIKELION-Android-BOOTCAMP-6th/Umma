# Demo Scenario — FLOW-STATISTICS

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름. 사용자 여정 관점으로 묶어 애자일 스프린트 작업 배정 단위로도 사용.
> 이슈 단위(STAT-001~004) AC 체크리스트는 `docs/User_FlowDB/FLOW_STATISTICS/` 의 이슈 문서를 참조한다.
>
> 각 시나리오 = 한 명(또는 한 페어)이 스프린트 안에 완결할 수 있는 유저 가치 한 덩어리.
> 시나리오는 의존성 순서로 배열되어 있어 위에서 아래로 차곡차곡 쌓아 올릴 수 있다.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: 현재 선택 언어 기준으로 최소 한 지표(`grammarAccuracy` 권장) 에 history point 5개 이상 누적된 상태. 다른 지표는 history 부족 상태로 두면 Empty chart 도 자연스럽게 시연 가능
> - Mock 토글: `StatisticsRepository` fake 구현 + `FakeFixtures` (Empty / FetchFailure / PendingSync / LangMismatch) — 현재 미존재, DASH 스타일 `RepositoryModule` 토글 추가 필요
> - Mock fixture 가 필요한 분기: 시나리오 1·2 의 Empty / Error 분기, 시나리오 3 의 offline / 언어 변경 분기
> - 발표 후 Real 바인딩으로 원복

---

## 시나리오 1 — 내 학습 지표 한눈에 보기

> **무엇을 하는가** — Dashboard 언어 성취율 카드에서 Statistics 로 들어가면, 현재 선택 언어 기준의 5개 ExternalMetrics 요약 카드가 한 화면에 보인다.
> **유저 가치** — 사용자가 자기 성장 상태를 처음으로 한눈에 가시화하는 단위. "지금 내가 어디쯤 있는가" 라는 가장 기본 질문에 답한다.

**포함 이슈**
- `STAT-001` — Statistics 화면 진입 + 언어 컨텍스트 (selectedLearningLanguage observe, `GetStatisticsOverviewUseCase` 초기 상태)
- `STAT-002` — 학습 지표 요약 카드 표시 (Vocabulary Level / Grammar Accuracy / Expression Range / Fluency Score / Naturalness Score, Empty 값 처리)

**의존성**: 없음. FLOW-DASHBOARD 가 깔려 있으면 병행 가능. 단, history 가 비어 있어도 카드 자체는 그려진다는 전제.
**예상 규모**: M — 5개 카드 컴포넌트 + ExternalMetrics → UI state 변환 + Empty 값 처리.

**데모 흐름**
1. Dashboard "언어 성취율" 카드 클릭 → Statistics 화면 진입 (Loading skeleton)
2. `GetStatisticsOverviewUseCase` 결과 수신 → 5개 카드 표시 (Content)
   - 예: `vocabularyLevel = A2` / `grammarAccuracy = 72%` / `expressionRange = 34 expressions` / `fluencyScore = 68` / `naturalnessScore = 61`
3. AppBar 또는 헤더에 현재 선택 언어가 명확히 표시되는지 확인
4. 각 카드가 클릭 가능한 상태로 렌더링되는지(시각적 affordance) 확인

**핵심 분기**
- **성공**: 5개 카드가 현재 선택 언어 기준으로 모두 정상 렌더링.
- **지표 값 부족 (`STAT-002` Empty)**: 일부 / 전체 지표가 비어 있어도 전체 화면이 실패하지 않고, 해당 카드에만 Empty 값이 표시된다.
- **진입 실패 / 언어 컨텍스트 없음 (`STAT-001` Error)**: Error 상태 + Retry. 정상 복구 시 시나리오 흐름 정상 진입.

---

## 시나리오 2 — 특정 지표 골라 변화 추이 차트로 확인

> **무엇을 하는가** — 사용자가 관심 있는 지표 카드를 클릭하면 해당 지표의 시간별 변화가 line chart 로 표시된다. 다른 지표로 자유롭게 전환 가능.
> **유저 가치** — 단순 현재값에서 "성장 추적" 으로 한 단계 깊어지는 경험. "내가 어디서 어디로 왔는가" 라는 변화 인식을 만든다.

**포함 이슈**
- `STAT-003` — 지표 카드 클릭 + line chart 표시 (선택 지표 상태 관리, `StatisticsHistory` → `MetricHistoryPoint` 변환, line chart Composable, `vocabularyLevel` ordinal 변환)

**의존성**: 시나리오 1
**예상 규모**: M — line chart 컴포넌트 + history 변환 UseCase + ordinal/label 분리 처리.

**데모 흐름**
1. 시나리오 1 상태에서 `Grammar Accuracy` 카드 클릭 → ChartLoading
2. `StatisticsHistory` 에서 해당 지표만 `MetricHistoryPoint` 목록으로 변환 → line chart 렌더링 (x축 `recordedAt`, y축 metric 값)
3. `Fluency Score` 카드 클릭 → 차트가 새 지표 기준으로 재렌더링
4. 빠른 연속 클릭으로 여러 지표를 번갈아 탭 → 마지막 선택 지표만 표시되고 이전 fetch 결과가 덮어쓰지 않음 (`stale fetch dropped` 로그)
5. `Vocabulary Level` 카드 클릭 → 화면 라벨은 A1~C2 그대로 유지, 차트 내부 값은 1~6 ordinal 로 그려지는지 확인

**핵심 분기**
- **성공**: 지표 전환 + ordinal 변환 + stale fetch drop 까지 안정적으로 동작.
- **history 부족 (`STAT-003` Empty)**: history point 가 2개 미만이면 선이 그려지지 않고 Empty chart 상태가 표시된다.
- **chart 조회 실패 (`STAT-003` Error)**: ChartError 상태 + 재시도 가능.

---

## 시나리오 3 — 다시 들어와도 빠르고, 끊겨도 안 망가짐 (재진입 / 동기화 견고화)

> **무엇을 하는가** — 화면을 떠났다 다시 들어와도 local cache 기반으로 즉시 렌더링되고, background sync 로 새 데이터가 자연스럽게 반영된다. 네트워크가 끊겨도, 언어를 바꿔도 사용자 입장에선 매끄러운 경험.
> **유저 가치** — Statistics 는 Dashboard 보다 preload 우선순위가 낮기 때문에 "빠르게 보여주고 뒤에서 보정" 이라는 정책이 사용자 신뢰의 핵심이다.

**포함 이슈**
- `STAT-004` — 통계 데이터 동기화 / 재진입 처리 (local cache 우선 렌더링, background sync 결과 반영, pending sync 비차단, 언어 변경 시 stale fetch drop)

**의존성**: 시나리오 2
**예상 규모**: M — cache 정책 + background fetch + 언어 변경 시 mismatch 처리.

**데모 흐름**
1. 시나리오 2 상태에서 뒤로가기로 Dashboard 복귀
2. 다시 언어 성취율 카드 클릭 → Statistics 재진입 → local cache 로 카드 / 차트 영역 즉시 렌더링 (remote fetch 대기 없음)
3. background sync 로 새 point 가 도착하면 차트가 자동 갱신 (`history merged` 로그)
4. Dashboard 에서 학습 언어를 변경한 뒤 Statistics 재진입 → 새 언어 기준으로 카드 / 차트 재구성, 이전 언어 데이터는 화면에 남지 않음 (`lang mismatch dropped` 로그)
5. 비행기 모드 / 네트워크 끊긴 상태로 진입 → local 데이터로 정상 표시, background fetch 실패는 사용자에게 차단되지 않음

**핵심 분기**
- **성공**: local cache 즉시 표시 + background sync 반영 + 언어 컨텍스트 정합이 모두 매끄럽게 동작.
- **offline (`STAT-004` Non-blocking)**: 기존 차트가 사라지지 않고, sync 상태는 작은 표시기로만 노출된다.
- **언어 변경 직후 stale fetch 도착 (`STAT-004` Edge)**: 새 언어 화면에 이전 언어 결과가 반영되지 않는다.
- **local cache 없음 + remote 도 실패 (`STAT-004` FatalError)**: 매우 드문 경우만 FatalError 로 분기된다.
