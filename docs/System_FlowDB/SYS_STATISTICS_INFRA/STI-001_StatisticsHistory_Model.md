# [Infra] STI-001 StatisticsHistory 모델 및 Repository 계약

## User Story

Statistics User Flow 작업자는 학습 통계 화면을 구현하기 전에,
시간별 지표 변화 데이터가 어떤 모델과 repository 계약으로 제공되는지 명확히 알고 작업할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 시간별 지표 변화 원본 모델을 `StatisticsHistory`로 정의한다.
- [ ] line chart point 모델을 `MetricHistoryPoint`로 정의한다.
- [ ] 지표 식별자를 `StatisticsMetricType`으로 정의한다.
- [ ] MVP 지표는 `vocabularyLevel`, `grammarAccuracy`, `expressionRange`, `fluencyScore`, `naturalnessScore`로 고정한다.
- [ ] `StatisticsHistory`는 `selectedLearningLanguage`가 아니라 데이터 소속 필드인 `language`를 가진다.
- [ ] `StatisticsRepository`가 전달받은 `language` 기준의 history 조회 계약을 제공한다.
- [ ] `StatisticsRepository`가 local cache 우선 조회 결과를 반환한다.
- [ ] fake repository가 history 있음, history 부족, fetch 실패, pending sync 상태를 재현할 수 있다.

---

## 구현 범위

### 포함 범위

- `StatisticsHistory` domain 모델 계약
- `MetricHistoryPoint` domain 모델 계약
- `StatisticsMetricType` enum 계약
- `StatisticsRepository` interface
- history 조회 UseCase 계약
- 선택 지표별 chart point 변환 UseCase 계약
- fake/real repository 교체 기준
- line chart용 값 정규화 기준

### 제외 범위

- Language State 계산 공식
- External Metrics 재계산
- history snapshot 기록 시점
- Statistics 화면 UI
- line chart Composable 구현
- Dashboard 언어 성취율 카드 수정
- AI 기반 통계 해석

---

## 권장 파일/패키지 방향

```text
domain/model/statistics
→ StatisticsHistory
→ MetricHistoryPoint
→ StatisticsMetricType

domain/repository
→ StatisticsRepository

domain/usecase/statistics
→ ObserveStatisticsHistoryUseCase
→ GetMetricHistoryPointsUseCase

data/repository
→ StatisticsRepositoryImpl
→ FakeStatisticsRepository
```

---

## 핵심 계약

### 1. StatisticsHistory

```text
StatisticsHistory
→ id
→ userId
→ language
→ recordedAt
→ vocabularyLevel
→ grammarAccuracy
→ expressionRange
→ fluencyScore
→ naturalnessScore
→ sourceEventId
→ syncStatus
```

- `language`: 이 history가 어떤 학습 언어의 데이터인지 나타내는 소속 필드
- `recordedAt`: line chart x축 기준 시점
- `sourceEventId`: 같은 분석 이벤트의 중복 기록 방지용 id
- `syncStatus`: `synced`, `pending`, `failed` 등 local/remote sync 상태

`StatisticsHistory`는 current value가 아니라 시간별 snapshot이다.
현재 상태는 `LangState.external`에서 읽고, 변화 그래프는 `StatisticsHistory`에서 읽는다.

### 2. MetricHistoryPoint

```text
MetricHistoryPoint
→ metricType
→ recordedAt
→ value
→ displayValue
```

- `value`: line chart 계산에 사용하는 숫자
- `displayValue`: 사용자가 보는 라벨

`vocabularyLevel`은 line chart 표시를 위해 A1=1, A2=2, B1=3, B2=4, C1=5, C2=6으로 변환한다.
화면 라벨은 A1~C2 원문을 유지한다.

### 3. StatisticsMetricType

```text
StatisticsMetricType
→ VocabularyLevel
→ GrammarAccuracy
→ ExpressionRange
→ FluencyScore
→ NaturalnessScore
```

MVP에서는 위 5개 외의 지표를 표시하지 않는다.
Internal Metrics 전체를 화면 지표로 노출하지 않는다.

### 4. Repository 조회 계약

```text
language input
→ StatisticsRepository.observeHistory(language)
→ StatisticsHistory list
→ MetricHistoryPoint list
```

- Repository는 local cache를 먼저 반환한다.
- remote refresh는 background sync 기반 stale cache 보정 용도로 수행한다.
- Repository는 history 원본 목록과 sync 상태를 반환하고, Empty chart 판정은 `GetMetricHistoryPointsUseCase` 또는 ViewModel에서 수행한다.

---

## 저장 위치

```text
Local: Room
Remote: users/{uid}/statistics_history/{historyId}
```

Statistics history는 시간별 데이터이므로 MVP에서는 Room을 local source로 고정한다.
DataStore는 current summary 저장에는 적합하지만 line chart용 다건 history 저장에는 사용하지 않는다.

---

## 검증 기준

- 전달받은 `language`의 history만 조회된다.
- 다른 언어의 history가 섞이지 않는다.
- local cache만 있어도 line chart point를 만들 수 있다.
- history가 0개이거나 1개일 때 Empty chart로 분기할 수 있도록 UseCase가 point 개수를 판단한다.
- `vocabularyLevel`은 chart value와 display value가 분리된다.

---

## Edge Cases

- `language` 입력이 없음
- 현재 화면 언어의 history가 없음
- history가 1개뿐이라 변화 그래프를 그리기 부족함
- 일부 metric 값이 null이거나 비정상 범위임
- `sourceEventId`가 중복됨
- local cache는 있으나 remote refresh가 실패함
- 다른 언어의 history가 잘못 섞임

---

## 연결 문서

- [SYS_STATISTICS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA.md)
- [STI-002_History_Record_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)
- [FLOW_STATISTICS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
- [LS-001_Language_State_Model_Structure.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
