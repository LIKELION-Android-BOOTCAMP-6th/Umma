# Demo Scenario — FLOW-STATISTICS — FakeRepository 테스트

> Real 통합 데모에서 만들기 어려운 통계 화면의 방어로직을 `mockDebug` + FakeRepository로 확인하기 위한 데모 시나리오.
> 각 시나리오는 발표 순서가 아니라 QA 확인 단위이며, AC는 화면에서 확인 가능한 결과만 적는다.

---

## 사전 준비

테스트는 `mockDebug`에서 FakeRepository preset을 바꿔가며 진행한다.

기본 실행 순서:

1. 테스트할 시나리오의 preset을 확인한다.
2. `StatisticsDemoPresetConfig.activePreset`을 해당 preset으로 변경한다.
3. Android Studio에서 Build Variant를 `mockDebug`로 변경한다.
4. 앱을 다시 빌드/실행한다.
5. Dashboard에서 통계 카드로 진입한다.
6. 시나리오별 AC를 화면에서 확인한다.
7. 확인이 끝나면 활성 preset을 기본값으로 되돌린다.

사용 repository:

- `StatisticsRepository` → `FakeStatisticsRepository`
- `LearningStateRepo` → `FakeLearningStateRepo`

Repository 역할:

| Repository | 테스트 역할 |
| --- | --- |
| `FakeStatisticsRepository` | history seed, chart source, pending sync, refresh, 조회 실패 재현 |
| `FakeLearningStateRepo` | selected language 없음, LangState 없음, `ExternalMetrics.initial()` 등 overview 분기 재현 |

Preset은 테스트 상태를 한 번에 바꾸기 위한 묶음이다.

- 단순 데이터 케이스: seed만 포함
- 실패 케이스: seed + failure hook 포함
- 지연 케이스: seed + delayed flow 포함
- overview 케이스: LearningState fake state 포함

Preset 전환 방법:

- 활성 preset은 `app/src/main/java/com/app/umma/data/repository/fake/demo/statistics/StatisticsDemoPreset.kt`의 `StatisticsDemoPresetConfig.activePreset`에서 하나만 선택한다.
- 한 번의 앱 실행에서는 하나의 preset만 활성화한다.
- preset을 바꾼 뒤에는 앱을 다시 빌드/실행한다.
- 확인 후에는 활성 preset을 기본값으로 되돌린다.

예시:

```kotlin
object StatisticsDemoPresetConfig {
    val activePreset: StatisticsDemoPreset = StatisticsDemoPreset.NormalStatistics
}
```

`지표별 축/라벨 확인`, `Statistics overview 구성 실패`처럼 두 개 이상의 preset을 확인하는 시나리오는
각 preset마다 값을 바꾸고 앱을 다시 실행해 별도 회차로 확인한다.

---

## Preset 구성 기준

아래 표는 데모 시나리오 순서대로 정렬되어 있다.

| 순서 | Preset | 적용 repository | 포함 설정 | 검증 목적 |
| --- | --- | --- | --- | --- |
| 1 | `NormalStatistics` | `FakeStatisticsRepository` | `normalHistories()` | 기본 진입, 지표 카드, 일반 차트 표시 |
| 2 | `ExpressionRangeOverflow` | `FakeStatisticsRepository` | `expressionRangeOverflowHistories()` | 표현 폭 값이 10을 넘어도 y축이 확장되는지 확인 |
| 3 | `DelayedLanguageSwitch` | `FakeStatisticsRepository` + `FakeLearningStateRepo` | EN delayed flow + JA empty history + selected language 변경 | 이전 언어의 늦은 응답이 새 언어 화면을 덮지 않는지 확인 |
| 4 | `ShortHistory` | `FakeStatisticsRepository` | 현재 사용자/현재 언어 history 1건 | history 1건일 때 Empty chart 방어 확인 |
| 5 | `EmptyHistory` | `FakeStatisticsRepository` | `seedHistories(emptyList())` | history가 없을 때 Empty chart 방어 확인 |
| 6 | `PendingSyncFailure` | `FakeStatisticsRepository` | `pendingHistories()` + `setPendingSyncFailure(...)` + refresh no-op | pending sync 실패가 화면을 차단하지 않는지 확인 |
| 7 | `FetchFailure` | `FakeStatisticsRepository` | `normalHistories()` + `setFetchFailure(...)` | history 조회 실패가 Error UI로 처리되는지 확인 |
| 8 | `RefreshFailure` | `FakeStatisticsRepository` | `normalHistories()` + `setRefreshFailure(...)` | background refresh 실패가 기존 화면을 지우지 않는지 확인 |
| 9 | `InitialExternalMetrics` | `FakeLearningStateRepo` | `ExternalMetrics.initial()` state | 지표 값 초기 상태에서도 카드 영역이 깨지지 않는지 확인 |
| 10-A | `MissingSelectedLanguage` | `FakeLearningStateRepo` | selected language 없음 state | overview 조립 실패가 Error UI로 처리되는지 확인 |
| 10-B | `MissingCurrentLangState` | `FakeLearningStateRepo` | 현재 언어 LangState 없음 state | overview 조립 실패가 Error UI로 처리되는지 확인 |
| 11 | `DelayedMetricSwitch` | `FakeStatisticsRepository` | 이전 chart 요청이 늦게 완료되는 delayed history flow | 빠른 지표 전환 시 이전 지표 결과가 마지막 선택을 덮지 않는지 확인 |

---

## 시나리오 — 통계 화면 기본 진입

Fake 준비:

- 활성 preset: `NormalStatistics`
- 포함 설정: `normalHistories()`
- 실행 방법: `NormalStatistics` preset 활성화 후 `mockDebug` 앱 재실행

1. Dashboard에서 통계 카드 클릭
2. Statistics 화면 진입 확인
3. 현재 선택 언어 표시 확인
4. 지표 요약 카드 5개 확인

**AC**

- [ ] Statistics 화면에 정상 진입한다.
- [ ] 현재 선택 언어가 화면에 표시된다.
- [ ] 어휘 레벨, 문법 정확도, 표현 폭, 유창성, 자연스러움 카드가 표시된다.

---

## 시나리오 — 지표 차트 표시

Fake 준비:

- 활성 preset: `NormalStatistics`
- 포함 설정: `normalHistories()`
- 실행 방법: `NormalStatistics` preset 활성화 후 `mockDebug` 앱 재실행

1. Statistics 화면에서 표현 폭 카드 클릭
2. 차트 다이얼로그 표시 확인
3. 차트 요약 정보 확인
4. line chart 표시 확인
5. 차트 포인트 클릭
6. marker 정보 표시 확인
7. 차트 닫기

**AC**

- [ ] 지표 카드 클릭 시 차트 다이얼로그가 열린다.
- [ ] 선택한 지표명이 차트에 반영된다.
- [ ] 처음, 지금, 총성장, 히스토리 요약 정보가 표시된다.
- [ ] history가 2개 이상이면 line chart가 표시된다.
- [ ] 포인트 클릭 시 해당 포인트의 값과 변화량 marker가 표시된다.
- [ ] 닫기 버튼으로 차트 다이얼로그를 닫을 수 있다.

---

## 시나리오 — 지표별 축/라벨 확인

Fake 준비:

- 1차 활성 preset: `NormalStatistics`
- 2차 활성 preset: `ExpressionRangeOverflow`
- 포함 설정: `normalHistories()` / `expressionRangeOverflowHistories()`
- 실행 방법: 기본 축 확인 후 `ExpressionRangeOverflow` preset으로 앱을 다시 실행해 표현 폭 확장 상태 확인

1. `NormalStatistics` 상태에서 어휘 레벨 카드 클릭
2. y축 label이 `A1 ~ C2` 기준으로 표시되는지 확인
3. 차트 닫기
4. 문법 정확도 카드 클릭
5. y축 label이 `%` 기준으로 표시되는지 확인
6. 차트 닫기
7. 표현 폭 카드 클릭
8. y축 label이 숫자 기준으로 표시되는지 확인
9. `ExpressionRangeOverflow` preset으로 앱 재실행
10. 표현 폭 카드 클릭
11. 표현 폭 차트의 y축 상한이 최대값에 맞게 확장되는지 확인

**AC**

- [ ] 어휘 레벨 차트는 사용자가 `A1 ~ C2` 단계로 읽을 수 있다.
- [ ] 문법 정확도 차트는 `%` 기준으로 읽을 수 있다.
- [ ] 표현 폭 차트는 숫자 기준으로 읽을 수 있다.
- [ ] 표현 폭 값이 기본 범위를 넘어도 차트 선이 잘리지 않는다.
- [ ] 표현 폭 y축 상한이 데이터 최대값에 맞게 확장된다.
- [ ] 같은 차트 UI에서 지표별 label 정책이 섞이지 않는다.

---

## 시나리오 — 학습 언어 변경 시 이전 언어 통계가 남지 않음

Fake 준비:

- 활성 preset: `DelayedLanguageSwitch`
- 포함 설정: EN delayed flow + JA empty history + LearningState selected language 변경 가능 상태
- 실행 방법: `DelayedLanguageSwitch` preset 활성화 후 EN 차트 요청 중 학습 언어를 JA로 변경

1. EN 기준 Statistics 화면 진입
2. 지표 카드 클릭 후 EN 차트 요청
3. EN history 응답이 지연되는 동안 Dashboard로 복귀
4. 학습 언어 selector에서 JA 선택
5. Statistics 화면 재진입
6. 현재 선택 언어가 JA로 표시되는지 확인
7. 늦게 도착한 EN 차트 결과가 JA 화면을 덮어쓰지 않는지 확인
8. JA 기준 Empty 상태가 표시되는지 확인

**AC**

- [ ] 언어 변경 후 Statistics 화면의 현재 선택 언어가 갱신된다.
- [ ] 언어 변경 후 이전 언어의 지표 카드 값이 남지 않는다.
- [ ] 이전 언어의 지연된 차트 결과가 새 언어 화면을 덮어쓰지 않는다.
- [ ] 새 언어의 Empty 상태가 표시된다.

---

## 시나리오 — history 부족 Empty chart

Fake 준비:

- 활성 preset: `ShortHistory`
- 포함 설정: 현재 사용자/현재 언어 history 1건
- 실행 방법: `ShortHistory` preset 활성화 후 `mockDebug` 앱 재실행

1. Statistics 화면 진입
2. 지표 카드 클릭
3. 차트 다이얼로그 표시 확인
4. Empty chart 안내 확인

**AC**

- [ ] Statistics 화면 자체는 실패하지 않는다.
- [ ] 지표 카드는 표시된다.
- [ ] history가 2개 미만이면 line chart 대신 Empty chart 상태가 표시된다.
- [ ] Empty chart 상태에서도 다이얼로그를 닫을 수 있다.

---

## 시나리오 — 통계 history 없음

Fake 준비:

- 활성 preset: `EmptyHistory`
- 포함 설정: `seedHistories(emptyList())`
- 실행 방법: `EmptyHistory` preset 활성화 후 `mockDebug` 앱 재실행

1. Statistics 화면 진입
2. 지표 요약 카드 표시 확인
3. 지표 카드 클릭
4. 차트 Empty 상태 확인

**AC**

- [ ] history가 없어도 Statistics 화면 진입은 실패하지 않는다.
- [ ] 지표 요약 카드 영역은 유지된다.
- [ ] chart source가 없으면 Empty chart 상태가 표시된다.
- [ ] 앱이 크래시 없이 유지된다.

---

## 시나리오 — pending sync 실패

Fake 준비:

- 활성 preset: `PendingSyncFailure`
- 포함 설정: `pendingHistories()` + `setPendingSyncFailure(IllegalStateException("pending sync 실패"))` + refresh no-op
- 실행 방법: `PendingSyncFailure` preset 활성화 후 `mockDebug` 앱 재실행

1. pending history가 있는 상태로 Statistics 화면 진입
2. 지표 카드와 차트 표시 확인
3. pending sync 실패 후 화면 유지 확인
4. 다시 화면에 진입했을 때 pending 상태가 계속 보이는지 확인

**AC**

- [ ] pending sync 실패가 발생해도 Statistics 화면은 차단되지 않는다.
- [ ] 기존 지표 카드와 차트는 계속 표시된다.
- [ ] pending 상태는 사라진 것처럼 표시되지 않는다.
- [ ] 사용자는 차트 열기/닫기 등 기본 조작을 계속할 수 있다.

---

## 시나리오 — history 조회 실패

Fake 준비:

- 활성 preset: `FetchFailure`
- 포함 설정: `normalHistories()` + `setFetchFailure(IllegalStateException("history 조회 실패"))`
- 실행 방법: `FetchFailure` preset 활성화 후 `mockDebug` 앱 재실행

1. Statistics 화면 진입
2. 동기화 보조 영역에 history 조회 실패 문구가 표시되는지 확인
3. 지표 카드 클릭
4. 차트 Error dialog 표시 확인
5. 차트 Error dialog의 다시 시도 버튼 확인

**AC**

- [ ] history 조회 실패 시 앱이 크래시되지 않는다.
- [ ] 사용자가 실패 상태를 인지할 수 있는 문구가 표시된다.
- [ ] 차트 Error dialog에서 다시 시도 버튼이 표시된다.
- [ ] 실패 상태에서도 화면 이동이 막히지 않는다.

---

## 시나리오 — background refresh 실패

Fake 준비:

- 활성 preset: `RefreshFailure`
- 포함 설정: `normalHistories()` + `setRefreshFailure(IllegalStateException("refresh 실패"))`
- 실행 방법: `RefreshFailure` preset 활성화 후 `mockDebug` 앱 재실행

1. Statistics 화면 진입
2. local history 기반 지표 카드 표시 확인
3. 지표 카드 클릭
4. local history 기반 차트 표시 확인
5. refresh 실패 후에도 기존 화면 유지 확인

**AC**

- [ ] refresh 실패가 발생해도 기존 지표 카드가 사라지지 않는다.
- [ ] refresh 실패가 발생해도 기존 차트가 사라지지 않는다.
- [ ] 실패는 차단 화면이 아니라 보조 상태로만 표현된다.
- [ ] 사용자는 Statistics 화면을 계속 조작할 수 있다.

---

## 시나리오 — Statistics overview Empty 상태

Fake 준비:

- 활성 preset: `InitialExternalMetrics`
- 포함 설정: `FakeLearningStateRepo`의 `ExternalMetrics.initial()` state
- 실행 방법: `InitialExternalMetrics` preset 활성화 후 `mockDebug` 앱 재실행

1. Dashboard에서 통계 카드 클릭
2. Statistics 화면 진입
3. 지표 카드 값 확인
4. 지표 카드 클릭

**AC**

- [ ] overview 값이 초기 상태여도 Statistics 화면은 진입된다.
- [ ] 5개 지표 카드 영역은 유지된다.
- [ ] 값이 없는 지표는 Empty 상태로 표시된다.
- [ ] Empty 값 상태에서도 지표 카드 클릭이 앱 크래시로 이어지지 않는다.

---

## 시나리오 — Statistics overview 구성 실패

Fake 준비:

- Case A 활성 preset: `MissingSelectedLanguage`
- Case A 포함 설정: selected language 없음 state
- Case B 활성 preset: `MissingCurrentLangState`
- Case B 포함 설정: 현재 언어 LangState 없음 state
- 실행 방법: Case A와 Case B를 각각 한 번씩 활성화해 `mockDebug` 앱 재실행

1. Case A preset으로 앱 실행
2. Dashboard에서 통계 카드 클릭
3. Statistics 화면의 Error 상태 확인
4. 다시 시도 버튼 확인
5. Case B preset으로 앱 재실행
6. Dashboard에서 통계 카드 클릭
7. Statistics 화면의 Error 상태 확인
8. 다시 시도 버튼 확인

**AC**

- [ ] overview를 구성할 수 없으면 Error 상태가 표시된다.
- [ ] 사용자가 실패 상태를 인지할 수 있는 문구가 표시된다.
- [ ] 다시 시도 버튼이 표시된다.
- [ ] 앱이 크래시 없이 유지된다.

---

## 시나리오 — 빠른 지표 전환

Fake 준비:

- 활성 preset: `DelayedMetricSwitch`
- 포함 설정: 이전 chart 요청이 늦게 완료되는 delayed history flow
- 실행 방법: `DelayedMetricSwitch` preset 활성화 후 여러 지표 카드를 빠르게 연속 클릭

1. Statistics 화면 진입
2. 문법 정확도 카드 클릭
3. 곧바로 유창성 카드 클릭
4. 곧바로 자연스러움 카드 클릭
5. 마지막으로 선택한 지표의 차트가 표시되는지 확인

**AC**

- [ ] 여러 지표를 빠르게 눌러도 앱이 크래시되지 않는다.
- [ ] 마지막으로 선택한 지표명이 차트 다이얼로그에 표시된다.
- [ ] 이전 지표의 늦은 결과가 마지막 선택 지표 화면을 덮어쓰지 않는다.
- [ ] 차트 닫기 후 다시 지표를 선택할 수 있다.
