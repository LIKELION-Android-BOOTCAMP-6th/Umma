# [Infra] STI-003 Statistics navigation 명칭 baseline

## User Story

Statistics User Flow 작업자는 화면 구현을 시작하기 전에,
기존 코드에 남아 있는 `Analytics` 통계 placeholder 명칭이 `Statistics` 기준으로 정리된 진입점을 사용할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `Route.Analytics`는 `Route.Statistics` 기준으로 정리된다.
- [ ] `Route.AnalyticsGraph`는 `Route.StatisticsGraph` 기준으로 정리된다.
- [ ] `presentation/analytics` package는 `presentation/statistics` 기준으로 정리된다.
- [ ] `AnalyticsScreen` placeholder는 `StatisticsScreen` 기준으로 정리된다.
- [ ] Dashboard의 통계 카드 navigation은 정리된 Statistics route를 바라본다.
- [ ] 후속 User Flow가 사용할 public route/screen 이름은 `Statistics` 기준으로 고정된다.

---

## 구현 범위

### 포함 범위

- Statistics route / graph 명칭 정리
- Statistics screen placeholder 명칭 정리
- Statistics package 경로 정리
- Dashboard에서 Statistics 화면으로 이동하는 navigation wiring 정리
- 기존 `Analytics` 명칭 제거 또는 호환이 필요한 경우 최소 adapter 정리

### 제외 범위

- Statistics 화면 UI 상세 구현
- Metric summary card 구현
- line chart 구현
- `StatisticsHistory` 저장/조회 구현
- Learning State 계산/저장 정책 구현
- Dashboard 카드 UI 자체 수정

---

## 작업 지시

- 통계 화면의 공식 명칭은 `Statistics`로 통일한다.
- 기존 `Analytics` 명칭은 과거 placeholder의 흔적으로 보고 새 System Flow에서 정리한다.
- User Flow 작업자가 `Analytics`와 `Statistics` 중 어느 명칭을 써야 하는지 고민하지 않도록 route, graph, package, screen 이름을 먼저 맞춘다.
- Dashboard는 Statistics 진입점만 연결하고, history 조회나 chart 계산은 수행하지 않는다.

---

## 권장 변경 방향

```text
Route.Analytics → Route.Statistics
Route.AnalyticsGraph → Route.StatisticsGraph
presentation/analytics → presentation/statistics
AnalyticsScreen → StatisticsScreen
onNavigateToAnalytics → onNavigateToStatistics
```

사용자에게 보이는 화면명과 문서 기준은 모두 Statistics로 통일한다.
기존 호출부가 많아 한 번에 삭제하기 어렵다면, 후속 User Flow가 바라보는 public route/screen 이름부터 Statistics로 고정하고 내부 호환 코드는 최소 범위로 남긴다.

---

## 검증 기준

- Dashboard 통계 카드 클릭 시 정리된 Statistics route로 이동한다.
- `UmmaNavHost`에서 Statistics graph와 Statistics destination이 명확히 보인다.
- 새 Statistics User Flow 구현 파일은 `presentation/statistics` 하위에 생성된다.
- 코드 검색 시 새 작업 기준에서 `AnalyticsScreen`, `Route.Analytics`, `presentation/analytics`가 남지 않거나, 남아 있다면 호환 목적이 주석으로 설명된다.

---

## Edge Cases

- 기존 Dashboard 코드가 `onNavigateToAnalytics` 이름을 계속 참조함
- Bottom navigation 또는 MainActivity route 판정에서 기존 `Route.Analytics`를 참조함
- 파일 이동 후 import path가 남아 build가 실패함
- User Flow 작업자가 `presentation/analytics`와 `presentation/statistics`를 동시에 생성함

---

## 연결 문서

- [SYS_STATISTICS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA.md)
- [FLOW_STATISTICS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
- [FLOW_DASHBOARD.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-005_Language_Progress_Card.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_DASHBOARD/DASH-005_Language_Progress_Card.md)
