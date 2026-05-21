# [Feature] STAT-002 학습 지표 요약 카드 표시

## User Story

사용자는 Statistics 화면에서 현재 선택 언어의 주요 학습 지표를 카드 형태로 빠르게 확인할 수 있다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 현재 선택 언어의 `ExternalMetrics`를 입력으로 사용한다.
- [ ] `GetStatisticsOverviewUseCase`가 제공한 current metrics를 카드 state로 변환한다.
- [ ] `vocabularyLevel` 요약 카드가 표시된다.
- [ ] `grammarAccuracy` 요약 카드가 표시된다.
- [ ] `expressionRange` 요약 카드가 표시된다.
- [ ] `fluencyScore` 요약 카드가 표시된다.
- [ ] `naturalnessScore` 요약 카드가 표시된다.
- [ ] 각 카드에는 지표명과 현재 값이 표시된다.
- [ ] 각 카드는 클릭 가능한 상태로 렌더링된다.
- [ ] 지표 값이 부족한 경우 해당 카드에 Empty 값을 표시한다.
- [ ] 이 이슈에서는 line chart를 구현하지 않는다.

---

## Flow (링크)

- [FLOW-STATISTICS](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
- [STAT-001 → Statistics 화면 진입 및 언어 컨텍스트](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS/STAT-001_Entry_Context.md)
- [STAT-002 → 학습 지표 요약 카드 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS/STAT-002_Metric_Summary_Cards.md)

---

## 구현 범위

### 포함 범위

- Metric summary card UI
- `ExternalMetrics` 5개 표시
- Empty metric 표시
- 카드 클릭 가능 상태 준비
- Loading skeleton

### 제외 범위

- line chart 표시
- 선택 지표 history 조회
- StatisticsHistory 저장
- Firestore sync
- AI 기반 통계 해석

---

## Details

## 표시 지표

MVP에서는 다음 5개 지표만 표시한다.

| 지표 | 표시 예시 |
| --- | --- |
| `vocabularyLevel` | A1 |
| `grammarAccuracy` | 72% |
| `expressionRange` | 34 expressions |
| `fluencyScore` | 68 |
| `naturalnessScore` | 61 |

표시 문구는 실제 UI 톤에 맞춰 조정할 수 있지만, 지표 종류는 추가하지 않는다.

---

## 작업 지시

- `ExternalMetrics`를 UI 전용 card state로 변환한다.
- `LangState.external` 원본을 Composable에서 직접 파싱하지 않는다.
- 카드 클릭 이벤트는 metric type만 상위로 올린다.
- 카드 클릭 시 chart를 직접 그리지 않는다.
- chart 표시는 `STAT-003`에서 선택 지표 상태를 받아 처리한다.
- Internal Metrics 전체를 카드에 노출하지 않는다.

---

## 기술 설계 가이드

## 권장 구조

```text
presentation/statistics/components/
→ MetricSummaryCard.kt
→ MetricSummaryGrid.kt

presentation/statistics/
→ StatisticsUiState

domain/model/statistics/
→ StatisticsMetricType
```

## UI State 예시

```kotlin
data class MetricSummaryItem(
    val type: StatisticsMetricType,
    val title: String,
    val valueText: String,
    val isAvailable: Boolean
)
```

---

## 검증 기준

- 5개 지표 카드가 현재 선택 언어 기준으로 표시된다.
- 일부 지표 값이 없어도 전체 화면이 실패하지 않는다.
- 카드 클릭 시 선택 지표 type이 전달된다.
- 이 이슈만으로 history 조회나 chart 렌더링이 발생하지 않는다.

---

## Edge Cases

- `ExternalMetrics`가 없음
- 일부 metric 값이 null이거나 비정상 범위임
- `vocabularyLevel` 값이 지원 범위를 벗어남
- 지표 값이 전부 초기값임
- 카드 클릭 연타
