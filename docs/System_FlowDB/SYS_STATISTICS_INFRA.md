# SYS-STATISTICS-INFRA

## 1. 목표 및 범위

Statistics User Flow가 구현되기 전에, 학습 통계 화면에서 사용할 히스토리 데이터 계약과 조회 경계를 선행 정리한다.

Statistics는 `SYS-LEARNING-STATE-INFRA`에서 이미 정의한 `GlobalLangState`, `UserLangPref`, `LangState`, `ExternalMetrics`, `DashSummary`를 재사용한다.
따라서 기존 Learning State 문서를 크게 수정하지 않고, 시간별 변화 그래프에 필요한 통계 히스토리 저장/조회 계약만 별도 System Flow로 정의한다.

- `StatisticsHistory` / `MetricHistoryPoint` 모델 계약
- `StatisticsRepository` 조회/저장 계약
- `LS-006` Language State 업데이트 이후 history 기록 시점
- local cache 우선 조회와 Firestore background sync 기준
- 지표별 line chart에 필요한 데이터 정규화 기준

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. StatisticsHistory 모델 및 Repository 계약 | 시간별 지표 변화 모델, metric type, line chart point, 언어별 history 조회 계약 정의 | Statistics 화면과 data 구현이 같은 history 계약을 바라봄 | 성공: local history 조회 가능 / 실패: current LangState와 history 책임 혼동 | STI-001 |
| 2. Language State 업데이트 후 history 기록 계약 | `LS-006`의 External Metrics 재계산 이후 history snapshot 기록, 중복 기록 방지, pending sync 정의 | 지표 변화가 화면 진입 시 추가 AI 호출 없이 표시 가능 | 성공: history 누적 / 실패: 중복 기록, 과도한 fetch, sync 경계 불명확 | STI-002 |

`STI-001 ~ STI-002`는 System Flow의 선행 작업이다.
User Flow의 실제 통계 화면 구현은 `FLOW-STATISTICS`에서 진행한다.

---

## 3. GitHub Issue (실행 기준 / SSOT)

### System Flow 선행 이슈

- [STI-001 StatisticsHistory 모델 및 Repository 계약](./SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [STI-002 Language State 업데이트 후 history 기록 계약](./SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)

### User Flow 구현 이슈

- [STAT-001 Statistics 화면 진입 및 언어 컨텍스트](../User_FlowDB/FLOW_STATISTICS/STAT-001_Entry_Context.md)
- [STAT-002 학습 지표 요약 카드 표시](../User_FlowDB/FLOW_STATISTICS/STAT-002_Metric_Summary_Cards.md)
- [STAT-003 지표 카드 클릭 및 line chart 표시](../User_FlowDB/FLOW_STATISTICS/STAT-003_Metric_Line_Chart.md)
- [STAT-004 통계 데이터 동기화 및 재진입 처리](../User_FlowDB/FLOW_STATISTICS/STAT-004_Sync_and_Reentry.md)

---

## 4. 책임 경계

### System Flow에서 선행 정리할 것

- `StatisticsHistory` 원본 히스토리 모델
- `MetricHistoryPoint` line chart point 모델
- `StatisticsMetricType` 지표 식별자
- `StatisticsRepository` interface와 fake/real 교체 기준
- `LS-006` 업데이트 이후 history snapshot 기록 계약
- history 중복 기록 방지 기준
- local first 저장과 Firestore pending sync 기준

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
기존 Learning State 저장 흐름 뒤에 history 기록 지점을 추가하고, Statistics 화면이 읽을 전용 계약을 새로 정의한다.

| 구분 | 대상 | 수정/추가 내용 | 책임 이슈 |
| --- | --- | --- | --- |
| 기존 문서 | `SYS_LEARNING_STATE_INFRA` / `LS-006` | External Metrics 재계산과 local `LangState` 저장 성공 이후 `StatisticsHistory` snapshot을 기록하는 후처리 지점을 명시한다. | STI-002 |
| 기존 문서 | `LS-005` local cache & sync 정책 | `StatisticsHistory`도 local first 저장과 Firestore pending sync 대상임을 맞춘다. | STI-002 |
| 기존 문서 | `FLOW_DASHBOARD` / `DASH-005` | Dashboard 언어 성취율 카드는 Statistics 진입점만 제공하고, history 조회나 chart 계산을 책임지지 않음을 맞춘다. | STAT-001 |
| 신규 domain model | `StatisticsHistory` | 언어별 External Metrics snapshot 묶음 모델을 정의한다. | STI-001 |
| 신규 domain model | `MetricHistoryPoint` | line chart가 바로 사용할 단일 지표 point 모델을 정의한다. | STI-001 |
| 신규 domain model | `StatisticsMetricType` | Statistics에서 노출할 MVP 5개 지표 식별자를 정의한다. | STI-001 |
| 신규 repository | `StatisticsRepository` | history local 조회, snapshot 저장, pending sync 상태 조회 계약을 정의한다. | STI-001 |
| 기존/신규 usecase | `LS-006` 업데이트 흐름 + `RecordStatisticsHistoryUseCase` | Language State 저장 성공 이후 history snapshot을 local에 기록한다. | STI-002 |
| 신규 usecase | `GetStatisticsOverviewUseCase` | 현재 선택 언어의 current `ExternalMetrics`와 history 조회 준비 상태를 화면에 제공한다. | STAT-001 / STAT-002 |
| 신규 usecase | `GetMetricHistoryPointsUseCase` | `StatisticsHistory`를 선택 지표의 `MetricHistoryPoint` 목록으로 변환한다. | STAT-003 |
| 신규 data | Room entity / Firestore DTO / mapper | `StatisticsHistory` 저장소 구현과 fake/real 교체 기준을 맞춘다. | STI-001 / STI-002 |
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
Repository는 저장과 조회를 담당하며, 지표 계산 공식은 `LS-006`의 Language State 업데이트 정책을 따른다.

---

## 7. 핵심 데이터 흐름

```text
LS-006 Language State 업데이트 완료
→ ExternalMetrics 재계산
→ Local LangState 저장 성공
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

---

## 8. 상태 기준

- Statistics 진입 기준은 `selectedLearningLanguage` 존재 여부다.
- Statistics 화면의 current value는 현재 선택 언어의 `LangState.external`을 기준으로 한다.
- 지표 변화 그래프의 source of truth는 `StatisticsHistory`다.
- `DashSummary`의 delta 값은 Dashboard 표시용 요약이며, 그래프 source of truth가 아니다.
- Statistics history는 언어별로 분리한다.
- 화면 진입 시 local cache를 먼저 렌더링한다.
- Firestore fetch는 background sync 기반 stale cache 보정 용도로만 수행한다.
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

차트는 MVP에서 모두 line chart로 표시한다.
복잡한 비교 차트, 기간 필터, 주간 리포트, AI 기반 통계 해석은 후속 단계로 둔다.

---

## 11. mock/real 정책

- UI 작업자는 `FakeStatisticsRepository`로 지표 카드, Empty chart, line chart, pending sync 상태를 먼저 구현할 수 있다.
- fake와 real 구현체는 같은 `StatisticsHistory` / `MetricHistoryPoint` 계약을 반환한다.
- ViewModel과 Composable은 fake인지 real인지 알지 못해야 한다.
- 교체 방식은 [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](../User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)를 따른다.

---

## 12. 연결 문서

- [FLOW_STATISTICS.md](../User_FlowDB/FLOW_STATISTICS.md)
- [SYS_LEARNING_STATE_INFRA.md](./SYS_LEARNING_STATE_INFRA.md)
- [LS-001 Language State Model Structure](./SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-004 Global Learning State Store](./SYS_LEARNING_STATE_INFRA/LS-004_Global_Learning_State_Store.md)
- [LS-005 Local Cache & Sync Policy](./SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [LS-006 Language State Update Policy](./SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [FLOW_DASHBOARD.md](../User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-005 Language Progress Card](../User_FlowDB/FLOW_DASHBOARD/DASH-005_Language_Progress_Card.md)

---

## 13. 한 줄 요약

> `SYS-STATISTICS-INFRA`는 기존 Learning State를 크게 바꾸지 않고, Statistics 화면의 지표별 line chart에 필요한 history 저장/조회 계약만 `STI-001 ~ STI-002`로 선행 정리한다.
