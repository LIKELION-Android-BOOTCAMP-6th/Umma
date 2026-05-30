# User Flow - STATISTICS

## 1. 목표

- 사용자는 Dashboard의 언어 성취율 카드에서 Statistics 화면으로 이동해 현재 선택 언어의 학습 성장 상태를 확인할 수 있다.
- 사용자는 학습 지표 요약 카드를 보고, 각 지표 카드를 클릭해 다이얼로그에서 시간별 변화 line chart를 확인할 수 있다.
- Statistics 화면은 저장된 `StatisticsHistory`를 local cache 우선으로 조회한다.

---

## 2. 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- `selectedLearningLanguage` 존재
- Global Learning State preload 완료 상태
- 현재 선택 언어의 `LangState.external` 조회 가능 상태
- `SYS-STATISTICS-INFRA`의 history 조회 계약 참조 가능 상태
- Dashboard의 언어 성취율 카드가 Statistics 화면으로 이동 가능한 상태

---

## 3. 성공 조건

- Statistics 화면 진입 시 현재 선택 언어 기준 상태를 읽는다.
- 현재 선택 언어의 `ExternalMetrics` 기반 요약 카드가 표시된다.
- MVP 지표는 `vocabularyLevel`, `grammarAccuracy`, `expressionRange`, `fluencyScore`, `naturalnessScore`로 제한된다.
- 사용자가 지표 카드를 클릭하면 해당 지표의 history line chart 다이얼로그가 표시된다.
- line chart는 `StatisticsHistory`에서 만든 `MetricHistoryPoint`를 사용한다.
- history 데이터가 부족하면 Empty chart 상태를 표시한다.
- Firestore sync 실패는 화면 실패로 보지 않고 pending sync로 관리한다.
- local에 남은 `PENDING` history는 재진입/갱신 시 Firestore write-back을 재시도한다.
- Statistics route/screen/package 명칭은 System Flow 선행 작업에서 `Statistics` 기준으로 준비되어 있다.

---

## 4. 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 상세 이슈 |
| --- | --- | --- | --- | --- | --- | --- |
| Statistics 진입 | Dashboard 언어 성취율 카드로 진입 | selectedLearningLanguage와 현재 ExternalMetrics를 확인 | Statistics 화면 초기 상태 구성 | 언어 없음 / 상태 로드 실패 | Loading / Ready / Error | STAT-001 |
| 지표 요약 카드 표시 | 화면 진입 후 지표 확인 | 현재 선택 언어의 ExternalMetrics 5개를 요약 카드로 렌더링 | 지표 카드 표시 | 지표 없음 / 데이터 부족 | Content / Empty | STAT-002 |
| 지표 line chart 표시 | 지표 카드 클릭 | 선택 지표의 StatisticsHistory를 MetricHistoryPoint로 변환해 chart dialog 표시 | 그래프 다이얼로그 표시 | history 부족 / 조회 실패 | ChartLoading / Chart / Empty / Error | STAT-003 |
| 동기화 및 재진입 | 화면 재진입, background refresh 완료 | local cache 우선 렌더링 후 Firestore 보정 결과 반영 | 최신 history 반영 | sync pending / navigation 실패 | Content / PendingSync / Error | STAT-004 |

---

## 5. GitHub Issue (실행 기준 / SSOT)

### User Flow Issues

- [STAT-001 Statistics 화면 진입 및 언어 컨텍스트](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-001_Entry_Context.md)
- [STAT-002 학습 지표 요약 카드 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-002_Metric_Summary_Cards.md)
- [STAT-003 지표 카드 클릭 및 line chart 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-003_Metric_Line_Chart.md)
- [STAT-004 통계 데이터 동기화 및 재진입 처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-004_Sync_and_Reentry.md)

---

## 6. 구현 전 확인할 선행 계약

Statistics 화면 작업자는 아래 계약이 준비되어 있다고 보고 화면 작업을 진행한다.
해당 계약 자체를 새로 설계하거나 다른 Flow의 책임까지 구현하지 않는다.

| 확인 대상 | 화면에서 기대하는 내용 | 관련 문서/이슈 |
| --- | --- | --- |
| `GlobalLangState` | 현재 선택 언어를 observe할 수 있다. | `SYS-LEARNING-STATE-INFRA`, LS-004 |
| `LangState.external` | 현재 선택 언어의 MVP 5개 지표 현재값을 읽을 수 있다. | LS-001, LS-006 |
| Statistics navigation baseline | `Route.Statistics`, `StatisticsScreen`, `presentation/statistics` 기준 진입점이 준비되어 있다. | SYS-STATISTICS-INFRA, STI-003 |
| `StatisticsRepository` | 현재 선택 언어의 `StatisticsHistory`를 local cache 우선으로 조회할 수 있다. | SYS-STATISTICS-INFRA, STI-001 |
| `RecordStatisticsHistoryUseCase` | Correction 완료 파이프라인에서 Flashcard 저장과 Language State 업데이트가 모두 성공한 뒤 history snapshot이 누적된다. | STI-002 |
| `GetStatisticsOverviewUseCase` | 선행 계약을 조합해 화면 초기 상태에 필요한 current metrics와 history 준비 상태를 제공한다. | STAT-001, STAT-002 |
| `GetMetricHistoryPointsUseCase` | 선택 지표의 chart point 목록을 제공한다. | STAT-003 |
| Dashboard 언어 성취율 카드 | Statistics 화면 진입점만 제공한다. | FLOW-DASHBOARD, DASH-005 |

Statistics 화면은 `DashSummary`의 delta 값을 그래프 원본으로 사용하지 않는다.
그래프 원본은 `StatisticsHistory`이며, 현재값 표시는 `LangState.external`을 기준으로 한다.
Dashboard에서 전달되는 진입 정보가 있더라도 Statistics 화면의 현재 언어 기준은 `GlobalLangState`에서 확인한다.

---

## 7. 데모 시나리오

1. Dashboard에서 언어 성취율 카드를 클릭한다.
2. Statistics 화면에 진입해 현재 선택 언어가 반영되는지 확인한다.
3. Vocabulary Level, Grammar Accuracy, Expression Range, Fluency Score, Naturalness Score 요약 카드를 확인한다.
4. Grammar Accuracy 카드를 클릭한다.
5. Grammar Accuracy history line chart 다이얼로그가 표시되는지 확인한다.
6. history 데이터가 부족한 지표를 클릭해 Empty chart 상태를 확인한다.
7. 화면 재진입 시 local cache가 먼저 표시되고, background refresh 결과가 반영되는지 확인한다.

---

## 8. 핵심 정책

### 8.1 현재 언어 정책

```text
GlobalLangState
→ UserLangPref.selectedLang
→ current LangState.external
→ current StatisticsHistory
```

Statistics 화면은 현재 선택 언어의 데이터만 표시한다.
다른 언어의 history를 동시에 렌더링하지 않는다.
Repository / UseCase 계층의 history 조회는 현재 사용자와 현재 언어가 함께 분리된 결과를 사용한다.

### 8.2 지표 정책

MVP 지표는 `ExternalMetrics` 5개로 고정한다.

```text
vocabularyLevel
grammarAccuracy
expressionRange
fluencyScore
naturalnessScore
```

Internal Metrics 전체를 화면에 노출하지 않는다.
Dashboard의 delta 값은 Statistics 그래프의 source of truth가 아니다.

### 8.3 차트 정책

MVP 차트는 모든 지표를 line chart로 통일한다.

```text
StatisticsHistory
→ selected metric
→ MetricHistoryPoint list
→ chart dialog
→ Vico line chart
```

지표 카드는 요약 정보와 클릭 진입점 역할을 유지한다.
차트는 카드 자체를 전환하지 않고 다이얼로그로 표시한다.
카드 영역이 이미 화면에서 차지하는 비중이 크므로, MVP에서는 선택 지표의 상세 변화만 다이얼로그에서 집중해서 보여준다.
line chart 렌더링은 직접 Canvas로 모두 구현하지 않고 Vico 기반 Compose chart를 사용한다.
다만 Vico는 이미 변환된 `MetricHistoryPoint`를 그리는 역할만 맡고, history 조회와 scale 변환은 UseCase / UI state mapper 경계에서 처리한다.

`vocabularyLevel`은 A1~C2 label을 유지하되, line chart에서는 A1=1, A2=2, B1=3, B2=4, C1=5, C2=6 값으로 표시한다.
`grammarAccuracy`, `fluencyScore`, `naturalnessScore`는 저장된 `ExternalMetrics` 원본 스케일을 그대로 화면에 노출하지 않고, 카드/차트 표시 단계에서 0~100 기준으로 환산한다.

### 8.4 저장 정책

Statistics 화면은 history를 생성하지 않는다.
History 생성은 `SYS-STATISTICS-INFRA`의 `STI-002` 계약에 따라 교정 결과 Flashcard 저장과 `LS-006` Language State 업데이트가 모두 성공한 이후 호출되는 기록 UseCase에서 수행된다.
화면 진입, Dashboard 진입, Flashcard 복습 결과 저장만으로는 MVP Statistics history를 생성하지 않는다.
Firestore sync 실패는 사용자 화면 실패로 보지 않는다.
Statistics 화면의 `background refresh`는 Firestore의 최신 history를 local Room에 보정하는 흐름이고, `pending sync`는 local에 먼저 저장된 history가 아직 Firestore에 올라가지 않은 상태다.
이미 local에 저장된 pending history는 재진입 시 재동기화 대상이며, 성공한 row만 `SYNCED`로 정리한다.

---

## 9. 디자인 (필요 시)

- Statistics Screen
- Metric Summary Card
- Metric Line Chart Dialog
- Empty Chart State
- Chart Loading Skeleton
- Pending Sync Indicator

---

## 10. 연결 문서

- [SYS_STATISTICS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [LS-001 Language State Model Structure](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-004 Global Learning State Store](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-004_Global_Learning_State_Store.md)
- [LS-005 Local Cache & Sync Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [LS-006 Language State Update Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [FLOW_DASHBOARD.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-005 Language Progress Card](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_DASHBOARD/DASH-005_Language_Progress_Card.md)
