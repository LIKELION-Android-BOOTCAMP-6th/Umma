# Test Sheet — FLOW-STATISTICS

> 데모 시나리오: [`docs/Sprint2/Demo/DEMO_FLOW_STATISTICS.md`](../DEMO_FLOW_STATISTICS.md)
> 사용법: 각 행의 "수행 절차"대로 실행 후 "기대 결과" 만족 여부를 **Pass / Fail** 표기. 실패 시 비고에 1~2줄 재현 메모 + GitHub Issue 링크.
>
> **검증 범위**:
> - `mockDebug`: `StatisticsDemoPreset`을 바꿔 Normal / Empty / Error / Pending / Delay 상태를 재현한다.
> - 이 문서는 `DEMO_FLOW_STATISTICS`의 FakeRepository 기반 데모/QA 검증용이다.
> - 실제 LearningState 계산 품질, remote sync, real 데이터 저장 흐름은 이 문서에서 판정하지 않는다.

---

## 사전 준비

- Android Studio Build Variant: `mockDebug`
- preset 변경 위치:

```kotlin
// app/src/main/java/com/app/umma/data/repository/fake/demo/statistics/StatisticsDemoPreset.kt
object StatisticsDemoPresetConfig {
    val activePreset: StatisticsDemoPreset = StatisticsDemoPreset.NormalStatistics
}
```

- preset 변경 후 앱 재빌드/재실행
- Dashboard에서 통계 카드로 진입
- 필요 시 logcat에서 Statistics / StatisticsViewModel 관련 태그 확인

### Mock preset 기준

| Preset | 목적 |
| --- | --- |
| `NormalStatistics` | 기본 진입, 지표 카드, 일반 차트 표시 |
| `ExpressionRangeOverflow` | 표현 폭 값이 기본 y축 범위 10을 넘는 상태 |
| `DelayedLanguageSwitch` | 언어 변경 중 이전 언어 history 응답이 늦게 도착하는 상태 |
| `ShortHistory` | 현재 언어 history가 1건뿐인 chart Empty 상태 |
| `EmptyHistory` | history가 전혀 없는 chart Empty 상태 |
| `PendingSyncFailure` | pending write-back 실패가 화면을 차단하지 않는 상태 |
| `FetchFailure` | history 조회 실패로 chart Error / Retry가 표시되는 상태 |
| `RefreshFailure` | background refresh 실패 후 기존 local 화면이 유지되는 상태 |
| `InitialExternalMetrics` | LearningState external metrics가 초기값인 overview Empty 상태 |
| `MissingSelectedLanguage` | selected language가 없어 overview 조립이 실패하는 상태 |
| `MissingCurrentLangState` | selected language는 있지만 현재 LangState가 없어 overview 조립이 실패하는 상태 |
| `DelayedMetricSwitch` | 빠른 지표 전환 중 이전 chart 응답이 늦게 도착하는 상태 |

---

## 커버되는 AC 체크리스트

### SC-STAT-01 통계 화면 기본 진입

- [ ] AC1: Statistics 화면에 정상 진입한다.
- [ ] AC2: 현재 선택 언어가 화면에 표시된다.
- [ ] AC3: 어휘 레벨, 문법 정확도, 표현 폭, 유창성, 자연스러움 카드가 표시된다.

### SC-STAT-02 지표 차트 표시

- [ ] AC1: 지표 카드 클릭 시 차트 다이얼로그가 열린다.
- [ ] AC2: 선택한 지표명이 차트에 반영된다.
- [ ] AC3: 처음, 지금, 총성장, 히스토리 요약 정보가 표시된다.
- [ ] AC4: history가 2개 이상이면 line chart가 표시된다.
- [ ] AC5: 포인트 클릭 시 해당 포인트의 값과 변화량 marker가 표시된다.
- [ ] AC6: 닫기 버튼으로 차트 다이얼로그를 닫을 수 있다.

### SC-STAT-03 지표별 축/라벨 확인

- [ ] AC1: 어휘 레벨 차트는 사용자가 `A1 ~ C2` 단계로 읽을 수 있다.
- [ ] AC2: 문법 정확도 차트는 `%` 기준으로 읽을 수 있다.
- [ ] AC3: 표현 폭 차트는 숫자 기준으로 읽을 수 있다.
- [ ] AC4: 표현 폭 값이 기본 범위를 넘어도 차트 선이 잘리지 않는다.
- [ ] AC5: 표현 폭 y축 상한이 데이터 최대값에 맞게 확장된다.
- [ ] AC6: 같은 차트 UI에서 지표별 label 정책이 섞이지 않는다.

### SC-STAT-04 학습 언어 변경 시 이전 언어 통계가 남지 않음

- [ ] AC1: 언어 변경 후 Statistics 화면의 현재 선택 언어가 갱신된다.
- [ ] AC2: 언어 변경 후 이전 언어의 지표 카드 값이 남지 않는다.
- [ ] AC3: 이전 언어의 지연된 차트 결과가 새 언어 화면을 덮어쓰지 않는다.
- [ ] AC4: 새 언어의 지표 카드와 line chart가 표시된다.

### SC-STAT-05 history 부족 Empty chart

- [ ] AC1: Statistics 화면 자체는 실패하지 않는다.
- [ ] AC2: 지표 카드는 표시된다.
- [ ] AC3: history가 2개 미만이면 line chart 대신 Empty chart 상태가 표시된다.
- [ ] AC4: Empty chart 상태에서도 다이얼로그를 닫을 수 있다.

### SC-STAT-06 통계 history 없음

- [ ] AC1: history가 없어도 Statistics 화면 진입은 실패하지 않는다.
- [ ] AC2: 지표 요약 카드 영역은 유지된다.
- [ ] AC3: chart source가 없으면 Empty chart 상태가 표시된다.
- [ ] AC4: 앱이 크래시 없이 유지된다.

### SC-STAT-07 pending sync 실패

- [ ] AC1: pending sync 실패가 발생해도 Statistics 화면은 차단되지 않는다.
- [ ] AC2: 기존 지표 카드와 차트는 계속 표시된다.
- [ ] AC3: pending 상태는 사라진 것처럼 표시되지 않는다.
- [ ] AC4: 사용자는 차트 열기/닫기 등 기본 조작을 계속할 수 있다.

### SC-STAT-08 history 조회 실패

- [ ] AC1: history 조회 실패 시 앱이 크래시되지 않는다.
- [ ] AC2: 사용자가 실패 상태를 인지할 수 있는 문구가 표시된다.
- [ ] AC3: 차트 Error dialog에서 다시 시도 버튼이 표시된다.
- [ ] AC4: 실패 상태에서도 화면 이동이 막히지 않는다.

### SC-STAT-09 background refresh 실패

- [ ] AC1: refresh 실패가 발생해도 기존 지표 카드가 사라지지 않는다.
- [ ] AC2: refresh 실패가 발생해도 기존 차트가 사라지지 않는다.
- [ ] AC3: 실패는 차단 화면이 아니라 보조 상태로만 표현된다.
- [ ] AC4: 사용자는 Statistics 화면을 계속 조작할 수 있다.

### SC-STAT-10 Statistics overview Empty 상태

- [ ] AC1: overview 값이 초기 상태여도 Statistics 화면은 진입된다.
- [ ] AC2: 5개 지표 카드 영역은 유지된다.
- [ ] AC3: 값이 없는 지표는 Empty 상태로 표시된다.
- [ ] AC4: Empty 값 상태에서도 지표 카드 클릭이 앱 크래시로 이어지지 않는다.

### SC-STAT-11 Statistics overview 구성 실패

- [ ] AC1: overview를 구성할 수 없으면 Error 상태가 표시된다.
- [ ] AC2: 사용자가 실패 상태를 인지할 수 있는 문구가 표시된다.
- [ ] AC3: 다시 시도 버튼이 표시된다.
- [ ] AC4: 앱이 크래시 없이 유지된다.

### SC-STAT-12 빠른 지표 전환

- [ ] AC1: 여러 지표를 빠르게 눌러도 앱이 크래시되지 않는다.
- [ ] AC2: 마지막으로 선택한 지표명이 차트 다이얼로그에 표시된다.
- [ ] AC3: 이전 지표의 늦은 결과가 마지막 선택 지표 화면을 덮어쓰지 않는다.
- [ ] AC4: 차트 닫기 후 다시 지표를 선택할 수 있다.

---

## 테스트 시트

| Test ID | 구분 | 시나리오 | 연결 AC | 사전 조건 | 수행 절차 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-STAT-01 | Mock | 통계 화면 진입 | SC-STAT-01 AC1 | `mockDebug`, `activePreset = NormalStatistics` | Dashboard -> 통계 카드 클릭 | Statistics 화면으로 이동 |  |  |
| TC-STAT-02 | Mock | 선택 언어 표시 | SC-STAT-01 AC2 | TC-STAT-01 완료 | 화면 상단 또는 언어 표시 영역 확인 | 현재 선택 언어가 표시됨 |  |  |
| TC-STAT-03 | Mock | 지표 카드 표시 | SC-STAT-01 AC3 | TC-STAT-01 완료 | 지표 카드 영역 확인 | 어휘 레벨, 문법 정확도, 표현 폭, 유창성, 자연스러움 카드 5개 표시 |  |  |
| TC-STAT-04 | Mock | 차트 다이얼로그 열림 | SC-STAT-02 AC1·2 | `mockDebug`, `activePreset = NormalStatistics` | Statistics 화면 -> 표현 폭 카드 클릭 | 차트 다이얼로그가 열리고 선택 지표명이 반영됨 |  |  |
| TC-STAT-05 | Mock | 차트 요약 정보 | SC-STAT-02 AC3 | TC-STAT-04 진행 중 | 차트 상단 요약 영역 확인 | 처음, 지금, 총성장, 히스토리 정보 표시 |  |  |
| TC-STAT-06 | Mock | line chart 표시 | SC-STAT-02 AC4 | TC-STAT-04 진행 중 | 차트 영역 확인 | history 2건 이상 기준 line chart 표시 |  |  |
| TC-STAT-07 | Mock | point marker 표시 | SC-STAT-02 AC5 | TC-STAT-04 진행 중 | 차트 포인트 클릭 | 해당 포인트 값과 변화량 marker 표시 |  |  |
| TC-STAT-08 | Mock | 차트 닫기 | SC-STAT-02 AC6 | TC-STAT-04 진행 중 | 닫기 버튼 클릭 | 차트 다이얼로그 닫힘 |  |  |
| TC-STAT-09 | Mock | 어휘 레벨 축 label | SC-STAT-03 AC1·6 | `mockDebug`, `activePreset = NormalStatistics` | 어휘 레벨 카드 클릭 | y축 label이 `A1 ~ C2` 단계 기준으로 표시되고 다른 label 정책과 섞이지 않음 |  |  |
| TC-STAT-10 | Mock | 점수형 축 label | SC-STAT-03 AC2·6 | `mockDebug`, `activePreset = NormalStatistics` | 문법 정확도 카드 클릭 | y축 label이 `%` 기준으로 표시되고 다른 label 정책과 섞이지 않음 |  |  |
| TC-STAT-11 | Mock | 표현 폭 기본 축 label | SC-STAT-03 AC3·6 | `mockDebug`, `activePreset = NormalStatistics` | 표현 폭 카드 클릭 | y축 label이 숫자 기준으로 표시되고 다른 label 정책과 섞이지 않음 |  |  |
| TC-STAT-12 | Mock | 표현 폭 y축 확장 | SC-STAT-03 AC4·5 | `mockDebug`, `activePreset = ExpressionRangeOverflow` | 앱 재실행 -> 표현 폭 카드 클릭 | 표현 폭 값이 10을 넘어도 차트 선이 잘리지 않고 y축 상한이 확장됨 |  |  |
| TC-STAT-13 | Mock | 언어 변경 표시 갱신 | SC-STAT-04 AC1 | `mockDebug`, `activePreset = DelayedLanguageSwitch` | EN 기준 Statistics 진입 -> 지표 카드 클릭으로 EN 차트 요청 -> 응답 지연 중 Dashboard 복귀 -> 학습 언어 JA 선택 -> Statistics 재진입 | Statistics 화면의 현재 선택 언어가 JA로 갱신됨 |  |  |
| TC-STAT-14 | Mock | 이전 언어 카드 stale 방어 | SC-STAT-04 AC2 | TC-STAT-13 진행 후 | JA Statistics 지표 카드 확인 | 이전 EN 지표 카드 값이 남지 않음 |  |  |
| TC-STAT-15 | Mock | 이전 언어 차트 stale 방어 | SC-STAT-04 AC3·4 | TC-STAT-13 진행 후 | JA 지표 카드 클릭 후 차트 확인 | 늦게 도착한 EN 차트 결과가 JA 화면을 덮지 않고 JA line chart가 표시됨 |  |  |
| TC-STAT-16 | Mock | history 1건 화면 유지 | SC-STAT-05 AC1·2 | `mockDebug`, `activePreset = ShortHistory` | Statistics 진입 | Statistics 화면과 지표 카드가 표시됨 |  |  |
| TC-STAT-17 | Mock | history 1건 Empty chart | SC-STAT-05 AC3·4 | TC-STAT-16 완료 | 지표 카드 클릭 -> Empty chart 확인 -> 닫기 | line chart 대신 Empty chart가 표시되고 다이얼로그를 닫을 수 있음 |  |  |
| TC-STAT-18 | Mock | history 없음 화면 유지 | SC-STAT-06 AC1·2·4 | `mockDebug`, `activePreset = EmptyHistory` | Statistics 진입 | Statistics 화면과 지표 요약 카드 영역이 크래시 없이 유지됨 |  |  |
| TC-STAT-19 | Mock | history 없음 Empty chart | SC-STAT-06 AC3 | TC-STAT-18 완료 | 지표 카드 클릭 | chart source 없음 상태가 Empty chart로 표시됨 |  |  |
| TC-STAT-20 | Mock | pending sync 비차단 | SC-STAT-07 AC1·2 | `mockDebug`, `activePreset = PendingSyncFailure` | Statistics 진입 -> 지표 카드와 차트 확인 | pending sync 실패가 화면을 차단하지 않고 기존 카드/차트가 표시됨 |  |  |
| TC-STAT-21 | Mock | pending 상태 유지 | SC-STAT-07 AC3·4 | TC-STAT-20 진행 후 Statistics 재진입 | pending 상태와 기본 조작 확인 | pending 상태가 사라진 것처럼 표시되지 않고 차트 열기/닫기 조작 가능 |  |  |
| TC-STAT-22 | Mock | history 조회 실패 문구 | SC-STAT-08 AC1·2·4 | `mockDebug`, `activePreset = FetchFailure` | Statistics 진입 | 앱이 크래시되지 않고 조회 실패 문구가 표시되며 화면 이동이 막히지 않음 |  |  |
| TC-STAT-23 | Mock | history 조회 실패 Retry | SC-STAT-08 AC3 | TC-STAT-22 완료 | 지표 카드 클릭 | chart Error dialog와 다시 시도 버튼 표시 |  |  |
| TC-STAT-24 | Mock | refresh 실패 카드 유지 | SC-STAT-09 AC1·3·4 | `mockDebug`, `activePreset = RefreshFailure` | Statistics 진입 -> refresh 실패 후 화면 확인 | 기존 지표 카드가 유지되고 실패는 보조 상태로만 표시되며 화면 조작 가능 |  |  |
| TC-STAT-25 | Mock | refresh 실패 차트 유지 | SC-STAT-09 AC2·4 | TC-STAT-24 완료 | 지표 카드 클릭 | 기존 local history 기반 차트가 유지되고 조작 가능 |  |  |
| TC-STAT-26 | Mock | overview Empty 화면 유지 | SC-STAT-10 AC1·2·3 | `mockDebug`, `activePreset = InitialExternalMetrics` | Dashboard -> 통계 카드 클릭 | Statistics 화면에 진입하고 5개 카드 영역과 Empty 값 상태가 표시됨 |  |  |
| TC-STAT-27 | Mock | overview Empty 카드 클릭 방어 | SC-STAT-10 AC4 | TC-STAT-26 완료 | Empty 값 지표 카드 클릭 | 앱 크래시가 발생하지 않음 |  |  |
| TC-STAT-28 | Mock | selected language 없음 Error | SC-STAT-11 AC1·2·3·4 | `mockDebug`, `activePreset = MissingSelectedLanguage` | Dashboard -> 통계 카드 클릭 | Error 상태, 실패 문구, 다시 시도 버튼이 표시되고 앱이 크래시되지 않음 |  |  |
| TC-STAT-29 | Mock | current LangState 없음 Error | SC-STAT-11 AC1·2·3·4 | `mockDebug`, `activePreset = MissingCurrentLangState` | Dashboard -> 통계 카드 클릭 | Error 상태, 실패 문구, 다시 시도 버튼이 표시되고 앱이 크래시되지 않음 |  |  |
| TC-STAT-30 | Mock | 빠른 지표 전환 크래시 방어 | SC-STAT-12 AC1 | `mockDebug`, `activePreset = DelayedMetricSwitch` | 문법 정확도 -> 유창성 -> 자연스러움 카드 빠르게 클릭 | 앱이 크래시되지 않음 |  |  |
| TC-STAT-31 | Mock | 빠른 지표 전환 최신 선택 유지 | SC-STAT-12 AC2·3 | TC-STAT-30 진행 후 | 차트 다이얼로그의 지표명과 결과 확인 | 마지막 선택 지표명이 표시되고 이전 지표 결과가 덮어쓰지 않음 |  |  |
| TC-STAT-32 | Mock | 빠른 지표 전환 후 재선택 | SC-STAT-12 AC4 | TC-STAT-31 완료 | 차트 닫기 -> 다른 지표 카드 클릭 | 차트 닫기 후 다시 지표 선택 가능 |  |  |

---

## 판정 기준

- Mock preset은 상태 전이와 예외 재현용이다. 실제 지표 계산 정확도나 real 데이터 품질은 이 문서에서 판정하지 않는다.
- preset 변경 후에는 앱을 재빌드/재실행한다.
- `NormalStatistics`는 기본 정상 흐름 기준이다. 시나리오 완료 후에는 `activePreset`을 `NormalStatistics`로 되돌린다.
- Empty / Error / Retry / Pending 상태는 앱이 크래시되지 않고 사용자가 현재 상태를 이해할 수 있으면 Pass로 본다.
- `PendingSyncFailure`와 `RefreshFailure`는 화면 전체 Error가 아니라 보조 상태로만 표시되어야 한다.
- `DelayedLanguageSwitch`와 `DelayedMetricSwitch`에서 이전 요청 결과가 최신 화면을 덮어쓰면 Fail이다.
- 차트 재시도 버튼은 chart Error dialog에서만 확인한다. pending sync / refresh 실패 보조 문구는 버튼 재시도 대상이 아니다.
