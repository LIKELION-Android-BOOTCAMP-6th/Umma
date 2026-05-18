# [Feature] STAT-001 Statistics 화면 진입 및 언어 컨텍스트

## User Story

사용자는 Statistics 화면에 진입한 뒤,
현재 선택 언어 기준의 통계 컨텍스트가 적용된 초기 화면을 볼 수 있다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] Statistics route에 진입하면 `StatisticsScreen`이 렌더링된다.
- [ ] `StatisticsViewModel`이 `GlobalLangState`의 현재 선택 언어를 observe한다.
- [ ] `selectedLearningLanguage`가 Statistics 초기 언어 컨텍스트에 반영된다.
- [ ] `GetStatisticsOverviewUseCase`가 현재 선택 언어의 `LangState.external` 로드 상태를 반환한다.
- [ ] `GetStatisticsOverviewUseCase`가 현재 선택 언어의 `StatisticsHistory` 조회 가능 상태를 반환한다.
- [ ] 언어 정보가 없으면 Error 상태로 안전하게 분기한다.
- [ ] 초기 상태 로드 실패 시 재시도 가능한 상태가 된다.
- [ ] 화면 재진입 중 초기화 요청이 중복 실행되지 않는다.

---

## Flow (링크)

- FLOW-STATISTICS
- DASH-005 → 언어 성취율 카드
- STAT-001 → Statistics 화면 진입 및 언어 컨텍스트

---

## 구현 범위

### 포함 범위

- Statistics route destination
- 현재 선택 언어 observe
- `StatisticsViewModel` 초기 상태 구성
- Loading / Error 상태
- 초기화 중복 실행 방지

### 제외 범위

- 지표 요약 카드 UI
- line chart 표시
- `MetricHistoryPoint` 변환
- StatisticsHistory 저장
- Firestore sync 세부 처리
- AI 기반 통계 분석

---

## Details

## 진입 기준

Statistics 화면은 항상 현재 선택 언어 기준으로 시작한다.

```text
GlobalLangState
→ selectedLearningLanguage
→ GetStatisticsOverviewUseCase
→ Statistics 초기 언어 컨텍스트
```

Dashboard의 delta 값은 진입 힌트이며, Statistics 화면의 그래프 source of truth가 아니다.

---

## 작업 지시

- `StatisticsScreen`과 `StatisticsViewModel`을 준비한다.
- `StatisticsViewModel`이 현재 선택 언어를 기준으로 초기 상태를 구성하게 한다.
- `GetStatisticsOverviewUseCase`로 현재값 로드 상태와 history 조회 가능 상태를 초기 UI state에 반영한다.
- 언어 컨텍스트가 없으면 화면이 crash하지 않고 Error 상태를 표시한다.

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/statistics/
│   ├── StatisticsScreen.kt
│   ├── StatisticsViewModel.kt
│   └── StatisticsUiState.kt
├── domain/usecase/statistics/
│   └── GetStatisticsOverviewUseCase.kt
└── core/navigation/
    └── Route.kt
```

> Statistics 진입은 현재 언어 컨텍스트를 고정하는 단계다.
> 지표 요약 카드 렌더링은 `STAT-002`, line chart 표시는 `STAT-003`에서 다룬다.

---

## Edge Cases

- `selectedLearningLanguage`가 없음
- 현재 선택 언어의 `LangState`가 없음
- 현재 선택 언어의 history가 아직 없음
- 앱 재진입 중 초기화가 중복 실행됨
- Navigation 실패
