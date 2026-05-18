# [Feature] STAT-003 지표 카드 클릭 및 line chart 표시

## User Story

사용자는 Statistics 화면에서 특정 지표 카드를 클릭해,
해당 지표가 시간에 따라 어떻게 변했는지 line chart로 확인할 수 있다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 사용자가 지표 카드를 클릭하면 선택 지표 상태가 갱신된다.
- [ ] 현재 선택 언어의 `StatisticsHistory`를 조회하고 선택 지표만 chart point로 변환한다.
- [ ] `StatisticsHistory`를 `MetricHistoryPoint` 목록으로 변환한다.
- [ ] MVP 차트는 모든 지표를 line chart로 표시한다.
- [ ] `vocabularyLevel`은 A1~C2 라벨을 유지하고, chart 값은 ordinal 값으로 변환한다.
- [ ] history point가 2개 미만이면 Empty chart 상태를 표시한다.
- [ ] chart 조회 실패 시 재시도 가능한 Error 상태를 표시한다.
- [ ] 이 이슈에서는 history 저장이나 sync 정책을 구현하지 않는다.

---

## Flow (링크)

- FLOW-STATISTICS
- STAT-002 → 학습 지표 요약 카드 표시
- STAT-003 → 지표 카드 클릭 및 line chart 표시
- STI-001 → StatisticsHistory 모델 및 Repository 계약

---

## 구현 범위

### 포함 범위

- 선택 지표 상태 관리
- metric type 기반 history 조회
- `MetricHistoryPoint` 변환
- line chart Composable
- ChartLoading / Chart / EmptyChart / ChartError 상태

### 제외 범위

- Metric summary card UI 자체
- StatisticsHistory 저장
- Firestore background sync
- AI 기반 통계 해석
- 복수 지표 비교 chart
- 기간 필터

---

## Details

## chart 흐름

```text
MetricSummaryCard 클릭
→ selectedMetric 변경
→ StatisticsRepository.observeHistory(language)
→ selectedMetric 기준 MetricHistoryPoint list 변환
→ line chart 렌더링
```

Repository는 history 원본을 제공하고, 선택 지표별 chart 변환은 UseCase에서 수행한다.
Composable은 `StatisticsHistory` 원본을 직접 파싱하지 않는다.

---

## line chart 정책

- MVP에서는 모든 지표를 line chart로 통일한다.
- x축은 `recordedAt` 기준이다.
- y축은 metric type별 numeric value를 사용한다.
- 데이터가 2개 미만이면 선을 그리지 않고 Empty chart 상태를 표시한다.
- 차트에 표시하는 값과 사용자 라벨은 분리할 수 있다.

### Vocabulary Level 변환

| Label | Chart value |
| --- | --- |
| A1 | 1 |
| A2 | 2 |
| B1 | 3 |
| B2 | 4 |
| C1 | 5 |
| C2 | 6 |

화면의 값 라벨에는 A1~C2를 유지한다.

---

## 작업 지시

- 선택 지표를 `StatisticsMetricType`으로 관리한다.
- chart 컴포넌트는 이미 변환된 `MetricHistoryPoint`만 입력받는다.
- chart 컴포넌트 안에서 repository를 호출하지 않는다.
- history가 부족한 경우 더미 데이터를 만들어 표시하지 않는다.
- history가 부족한 경우 Empty chart 상태를 유지한다.

---

## 기술 설계 가이드

## 권장 구조

```text
domain/usecase/statistics/
→ GetMetricHistoryPointsUseCase

presentation/statistics/components/
→ MetricLineChart.kt
→ EmptyChartState.kt

presentation/statistics/
→ selectedMetric state
```

## 상태 예시

```text
ChartLoading(metricType)
ChartReady(metricType, points)
EmptyChart(metricType)
ChartError(metricType, message)
```

---

## 검증 기준

- 각 지표 카드를 클릭하면 해당 지표 line chart로 바뀐다.
- 다른 언어의 history가 chart에 섞이지 않는다.
- `vocabularyLevel`은 label과 chart value가 올바르게 분리된다.
- history가 2개 미만이면 Empty chart가 표시된다.

---

## Edge Cases

- 선택 지표에 해당하는 history 값만 없음
- 전체 history는 있으나 특정 metric 값이 비어 있음
- history point가 1개뿐임
- recordedAt 순서가 뒤섞여 있음
- 같은 recordedAt의 중복 point가 있음
- 빠르게 여러 지표를 번갈아 클릭함
- chart render 중 화면 이탈
