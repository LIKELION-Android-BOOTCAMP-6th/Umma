# SYS-STATISTICS-INFRA

## 1. 목표 및 범위

Statistics User Flow가 구현되기 전에, 학습 통계 화면에서 사용할 히스토리 데이터 계약과 조회 경계를 선행 정리한다.

Statistics는 `SYS-LEARNING-STATE-INFRA`에서 이미 정의한 `GlobalLangState`, `UserLangPref`, `LangState`, `ExternalMetrics`, `DashSummary`를 재사용한다.
따라서 기존 Learning State 문서를 크게 수정하지 않고, 시간별 변화 그래프에 필요한 통계 히스토리 저장/조회 계약만 별도 System Flow로 정의한다.

- `StatisticsHistory` / `MetricHistoryPoint` 모델 계약
- `StatisticsRepository` 조회/저장 계약
- Correction 완료 파이프라인에서 `LS-006` Language State 업데이트 결과를 소비하는 history 기록 연결 지점
- local cache 우선 조회와 Firestore background sync 기준
- 지표별 line chart에 필요한 데이터 정규화 기준
- 기존 `Analytics`로 남아 있는 화면/route/package 명칭을 `Statistics`로 통일하는 전환 기준

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. StatisticsHistory 모델 및 Repository 계약 | 시간별 지표 변화 모델, metric type, line chart point, 언어별 history 조회 계약 정의 | Statistics 화면과 data 구현이 같은 history 계약을 바라봄 | 성공: local history 조회 가능 / 실패: current LangState와 history 책임 혼동 | STI-001 |
| 2. Correction 완료 후 history 기록 계약 | 교정 결과가 Flashcard로 저장되고 `LS-006`이 저장한 LangState 결과를 받아 history snapshot을 기록하는 연결 지점, 중복 기록 방지, pending sync 기준 정의 | 지표 변화가 화면 진입 시 추가 AI 호출 없이 표시 가능 | 성공: history 누적 / 실패: 중복 기록, 과도한 fetch, sync 경계 불명확 | STI-002 |
| 3. Statistics navigation 명칭 baseline | 기존 `Analytics` route/screen/package 명칭을 `Statistics` 기준으로 정리하고 User Flow가 사용할 진입점을 준비 | 후속 Statistics 화면 구현자가 동일한 route/screen 기준으로 작업 가능 | 성공: Statistics 진입점 통일 / 실패: Analytics와 Statistics 명칭 혼재 | STI-003 |

`STI-001 ~ STI-003`는 System Flow의 선행 작업이다.
User Flow의 실제 통계 화면 구현은 `FLOW-STATISTICS`에서 진행한다.

---

## 3. GitHub Issue (실행 기준 / SSOT)

### System Flow 선행 이슈

- [STI-001 StatisticsHistory 모델 및 Repository 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [STI-002 Correction 완료 후 history 기록 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)
- [STI-003 Statistics navigation 명칭 baseline](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-003_Statistics_Navigation_Baseline.md)

### User Flow 구현 이슈

- [STAT-001 Statistics 화면 진입 및 언어 컨텍스트](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-001_Entry_Context.md)
- [STAT-002 학습 지표 요약 카드 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-002_Metric_Summary_Cards.md)
- [STAT-003 지표 카드 클릭 및 line chart 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-003_Metric_Line_Chart.md)
- [STAT-004 통계 데이터 동기화 및 재진입 처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS/STAT-004_Sync_and_Reentry.md)

---

## 4. 책임 경계

### System Flow에서 선행 정리할 것

- `StatisticsHistory` 원본 히스토리 모델
- `MetricHistoryPoint` line chart point 모델
- `StatisticsMetricType` 지표 식별자
- `StatisticsRepository` interface와 fake/real 교체 기준
- `StatisticsRepository`의 조회/저장 기준은 `userId + language` 조합으로 분리한다.
- Correction 완료 파이프라인에서 `LS-006` 업데이트 결과 이후 history snapshot 기록 연결 계약
- history 중복 기록 방지 기준
- local first 저장과 Firestore pending sync 기준
- 기존 `AnalyticsScreen`, `Route.Analytics`, `presentation/analytics` 등 통계 진입 명칭을 `Statistics` 기준으로 정리

### User Flow에서 구현할 것

- Statistics 화면 진입과 언어 컨텍스트 확인
- 현재 선택 언어의 학습 지표 요약 카드 표시
- 지표 카드 선택 상태
- 선택 지표의 line chart 표시
- Loading / Empty / Error / Retry / PendingSync 상태
- Dashboard 복귀와 화면 이탈 처리

---

## 5. 기존 계약 영향 범위

Statistics 작업은 새 Flow 문서를 추가하는 것만으로 끝나지 않는다.
다만 기존 Learning State 저장 파이프라인을 Statistics 쪽에서 다시 구현하지 않는다.
Statistics는 Correction 완료 파이프라인에서 Flashcard 저장과 `LS-006` LangState 저장이 모두 성공한 이후 호출될 history 기록 계약과, Statistics 화면이 읽을 전용 조회 계약을 정의한다.

| 구분 | 대상 | 수정/추가 내용 | 책임 이슈 |
| --- | --- | --- | --- |
| LS 선행 계약 | `SYS_LEARNING_STATE_INFRA` / `LS-006` | local `LangState` 저장 성공 이후 Statistics가 사용할 완료 결과(`language`, `ExternalMetrics`, `updatedAt`, `lastAnalysisEventId`)를 제공해야 한다. LS-006 계산/저장 파이프라인 자체는 Statistics에서 구현하지 않는다. | LS 후속 / STI-002 연결 |
| Statistics 연결 | `RecordStatisticsHistoryUseCase` | LS 저장 완료 결과를 입력으로 받아 `StatisticsHistory`를 기록한다. LS 저장소 내부에서 StatisticsRepository를 직접 호출하지 않는다. | STI-002 |
| 기존 문서 | `LS-005` local cache & sync 정책 | 기존 문서는 수정하지 않는다. 이 Flow에서는 시간별 다건 history가 필요하므로 `StatisticsHistory`의 MVP local source를 Room으로 좁혀 구현한다. | STI-001 / STI-002 |
| 기존 문서 | `FLOW_DASHBOARD` / `DASH-005` | Dashboard 언어 성취율 카드는 Statistics 진입점만 제공하고, history 조회나 chart 계산을 책임지지 않음을 맞춘다. | STAT-001 |
| 신규 domain model | `StatisticsHistory` | 언어별 External Metrics snapshot 묶음 모델을 정의한다. | STI-001 |
| 신규 domain model | `MetricHistoryPoint` | line chart가 바로 사용할 단일 지표 point 모델을 정의한다. | STI-001 |
| 신규 domain model | `StatisticsMetricType` | Statistics에서 노출할 MVP 5개 지표 식별자를 정의한다. | STI-001 |
| 신규 repository | `StatisticsRepository` | history local 조회, snapshot 저장, pending sync 상태 조회 계약을 정의한다. | STI-001 |
| 신규 usecase | `RecordStatisticsHistoryUseCase` | Correction 완료 후 LS-006에서 전달받은 저장 완료 결과를 바탕으로 history snapshot을 local에 기록한다. | STI-002 |
| 신규 usecase | `GetStatisticsOverviewUseCase` | Statistics 화면 초기 상태에 필요한 current `ExternalMetrics`와 history 조회 준비 상태를 조립한다. | STAT-001 / STAT-002 |
| 신규 usecase | `GetMetricHistoryPointsUseCase` | `StatisticsHistory`를 선택 지표의 `MetricHistoryPoint` 목록으로 변환한다. | STAT-003 |
| 신규 data | Room entity / Firestore DTO / mapper | `StatisticsHistory` 저장소 구현과 fake/real 교체 기준을 맞춘다. | STI-001 / STI-002 |
| 신규 navigation baseline | `Route.Statistics` / `Route.StatisticsGraph` / `StatisticsScreen` | 기존 `Analytics` 명칭을 제거하고 후속 User Flow가 사용할 Statistics 진입점을 준비한다. | STI-003 |
| 신규 presentation | `StatisticsScreen` / `StatisticsViewModel` | 현재 언어 observe, 요약 카드, 선택 지표, line chart, Empty/Error/PendingSync 상태를 구현한다. | STAT-001 ~ STAT-004 |

기존 `LangState.external`과 `DashSummary`의 의미는 바꾸지 않는다.
Statistics는 기존 지표를 다시 계산하지 않고, `LS-006`이 만든 결과와 이후 기록된 history를 읽는다.
`LS-005`의 Statistics 저장 위치는 `Room 또는 DataStore`로 열려 있지만, 이 Flow에서는 시간별 다건 history 조회가 필요하므로 MVP 구현 기준을 Room으로 좁힌다.
DataStore는 current summary나 단일 설정값에 적합하며, `StatisticsHistory` 원본 저장소로 사용하지 않는다.

---

## 6. 클린 아키텍처 경계

| 계층 | 책임 |
| --- | --- |
| `presentation` | `StatisticsScreen`, `StatisticsViewModel`, 지표 카드, line chart, Loading/Empty/Error 상태 |
| `domain` | `StatisticsHistory`, `MetricHistoryPoint`, metric type, Repository interface, history 조회 UseCase |
| `data` | Room 기반 history local cache, Firestore background sync, DTO/Entity mapper, fake repository |
| `di` | fake/real `StatisticsRepository` binding |

Composable은 `LangState` raw history를 직접 계산하지 않는다.
ViewModel은 UseCase를 통해 current metrics와 history point를 받는다.
Repository는 Statistics history 저장과 조회를 담당한다.
지표 계산 공식과 LangState 저장은 `LS-006`의 Language State 업데이트 정책을 따르며, Statistics는 그 결과를 다시 계산하지 않는다.

---

## 7. 핵심 데이터 흐름

```text
Correction 선택 결과 Flashcard local 저장 성공
→ LS-006 Language State 업데이트 완료
→ Local LangState 저장 성공 결과 수신
→ StatisticsHistory snapshot local 저장
→ Firestore background sync 예약

Statistics 화면 진입
→ selectedLearningLanguage 확인
→ current ExternalMetrics 조회
→ StatisticsHistory local cache 조회
→ 지표 요약 카드 표시
→ 사용자가 지표 카드 클릭
→ 선택 지표 MetricHistoryPoint line chart 표시
→ Firestore background sync로 최신 history 보정
```

Statistics 화면 진입만으로 AI API를 호출하지 않는다.
그래프 데이터는 이미 저장된 `StatisticsHistory`를 조회한다.

### MVP history 기록 기준

MVP에서 `StatisticsHistory`는 모든 `LangState` 변경마다 기록하지 않는다.
우선 기록 대상은 아래 조건을 모두 만족하는 Correction 완료 이벤트로 제한한다.

- 사용자가 교정 결과를 선택해 Flashcard local 저장이 성공했다.
- 같은 완료 흐름에서 `LS-006` Language State 업데이트가 local 저장까지 성공했다.
- 저장 완료 결과에 `language`, `ExternalMetrics`, `updatedAt`, `lastAnalysisEventId`가 포함된다.
- `sourceEventId`는 `lastAnalysisEventId`를 우선 사용하고, 값이 없을 때만 `language + updatedAt` 조합으로 만든다.
- 동일 `language + sourceEventId` 조합은 중복 기록하지 않는다.

Flashcard 복습 결과는 SRS / Dashboard 요약 갱신의 책임으로 유지한다.
복습 결과가 추후 `LangState`의 장기 지표에 반영되더라도, MVP Statistics history 기록 트리거에는 포함하지 않는다.

### Learning State 선행 계약과 Statistics 연결 책임

Statistics history 기록을 안전하게 연결하려면 LS 쪽 선행 계약과 Statistics 쪽 연결 책임을 분리한다.

Learning State 쪽에서 준비되어야 하는 것:

- `updateLanguageState(...)` 성공 결과로 `language`, `ExternalMetrics`, `updatedAt`, `lastAnalysisEventId`를 포함한 완료 결과를 반환한다.
- 동일 `analysisEventId`가 이미 반영된 경우 LangState를 다시 변경하지 않는 idempotent 처리를 구현한다.
- `ExternalMetrics` MVP 5개 지표가 모두 저장 결과에서 일관되게 채워지도록 한다.
- local 저장 성공과 remote sync 실패를 구분해, Statistics history 기록은 local 저장 성공 기준으로만 이어질 수 있게 한다.

Statistics 쪽에서 연결해야 하는 것:

- LS 저장 완료 결과를 입력으로 받아 `RecordStatisticsHistoryUseCase`를 호출한다.
- 동일 `userId + language + sourceEventId` 조합을 중복 기록하지 않는다.
- `StatisticsHistory` 저장 실패를 LangState rollback으로 처리하지 않고, 같은 완료 결과로 재시도 가능한 실패로 반환한다.
- `RecordStatisticsHistoryUseCase` 호출 지점은 LS 저장소 내부에 숨기지 않고, Correction 완료 파이프라인을 감싸는 조합 UseCase에서 명시적으로 연결한다.

---

## 8. 상태 기준

- Statistics 진입 기준은 `selectedLearningLanguage` 존재 여부다.
- Statistics 화면의 current value는 현재 선택 언어의 `LangState.external`을 기준으로 한다.
- 지표 변화 그래프의 source of truth는 `StatisticsHistory`다.
- `DashSummary`의 delta 값은 Dashboard 표시용 요약이며, 그래프 source of truth가 아니다.
- Statistics history는 `userId + language` 기준으로 분리한다.
- 화면 진입 시 local cache를 먼저 렌더링한다.
- remote refresh는 background sync 기반 stale cache 보정 용도로만 수행한다.
- Firestore sync 실패는 화면 진입 실패로 보지 않고 pending sync로 관리한다.
- 통계 데이터가 부족하면 Empty chart 상태를 표시한다.
- MVP 차트는 모든 지표를 line chart로 통일한다.

---

## 9. 모델 기준

| 모델 | 역할 |
| --- | --- |
| `StatisticsHistory` | 특정 언어의 시간별 External Metrics snapshot 묶음 |
| `MetricHistoryPoint` | line chart에 표시할 단일 지표의 시간별 point |
| `StatisticsMetricType` | `vocabularyLevel`, `grammarAccuracy`, `expressionRange`, `fluencyScore`, `naturalnessScore` 식별자 |
| `ExternalMetrics` | 사용자에게 보여줄 현재 학습 지표 |
| `DashSummary` | Dashboard 표시용 delta 요약 |

`StatisticsHistory`는 시간별 변화를 표현하는 모델이므로 `History` 명칭을 유지한다.

---

## 10. MVP 지표와 차트 기준

MVP에서 노출하는 지표는 `ExternalMetrics` 5개로 제한한다.

| 지표 | 표시 | line chart 값 |
| --- | --- | --- |
| `vocabularyLevel` | A1 ~ C2 | 차트용 ordinal 값 A1=1, A2=2, B1=3, B2=4, C1=5, C2=6 |
| `grammarAccuracy` | 100점 환산 | 0 ~ 100 |
| `expressionRange` | 누적 표현/어휘 수 | 정수 count |
| `fluencyScore` | 0 ~ 100 | 0 ~ 100 |
| `naturalnessScore` | 0 ~ 100 | 0 ~ 100 |

`StatisticsHistory`에는 `ExternalMetrics` snapshot의 원본 스케일을 보존한다.
현재 `ExternalMetrics`의 점수형 `Double` 값은 `0.0 ~ 1.0` 기준이므로, 화면 카드와 `MetricHistoryPoint` 변환 단계에서 `0 ~ 100` 표시값으로 환산한다.
차트와 카드가 같은 환산 정책을 쓰도록 변환 로직은 ViewModel/Composable이 아니라 UseCase 또는 UI state mapper에 둔다.

차트는 MVP에서 모두 line chart로 표시한다.
복잡한 비교 차트, 기간 필터, 주간 리포트, AI 기반 통계 해석은 후속 단계로 둔다.

---

## 11. mock/real 정책

- UI 작업자는 `FakeStatisticsRepository`로 지표 카드, Empty chart, line chart, pending sync 상태를 먼저 구현할 수 있다.
- fake와 real 구현체는 같은 `StatisticsHistory` / `MetricHistoryPoint` 계약을 반환한다.
- ViewModel과 Composable은 fake인지 real인지 알지 못해야 한다.
- 교체 방식은 [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)를 따른다.

---

## 12. 연결 문서

- [FLOW_STATISTICS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_STATISTICS.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [LS-001 Language State Model Structure](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-004 Global Learning State Store](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-004_Global_Learning_State_Store.md)
- [LS-005 Local Cache & Sync Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [LS-006 Language State Update Policy](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [FLOW_DASHBOARD.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-005 Language Progress Card](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/Sprint2/User_FlowDB/FLOW_DASHBOARD/DASH-005_Language_Progress_Card.md)

---

## 13. 한 줄 요약

> `SYS-STATISTICS-INFRA`는 기존 Learning State를 크게 바꾸지 않고, Statistics 화면의 지표별 line chart에 필요한 history 저장/조회 계약과 Statistics 진입 baseline을 `STI-001 ~ STI-003`으로 선행 정리한다.
