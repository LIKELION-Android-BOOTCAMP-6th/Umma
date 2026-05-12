# [Feature] DASH-005 언어 성취율 카드

## User Story

사용자는 Dashboard에서 현재 언어 학습 성장 상태를 빠르게 확인하고,
통계 화면으로 이동할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 언어 성취율 카드가 정상 출력된다.
- [ ] 대표 학습 성장 지표(delta)가 표시된다.
- [ ] 현재 선택 언어 기준 성취율 데이터가 정상 렌더링된다.
- [ ] 카드 클릭 시 Statistics 화면으로 이동한다.
- [ ] 현재 선택된 언어가 Statistics 초기 상태에 반영된다.
- [ ] 통계 데이터가 부족할 경우 Empty 상태가 표시된다.
- [ ] 카드 클릭 중 중복 Navigation이 방지된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-005 → 언어 성취율 카드

---

# 구현 범위

## 포함 범위

- 언어 성취율 카드 UI
- External Metrics delta 렌더링
- 대표 성장 지표 출력
- Statistics 화면 이동 처리
- Empty 상태 처리
- Navigation Loading 처리

---

## 제외 범위 (Out of Scope)

- 상세 통계 그래프
- 전체 Statistics 화면 구현
- Language State 분석 기능
- AI 기반 통계 계산
- 장기 성장 분석
- 통계 비교 기능

> Dashboard preload 및 Summary fetch는 DASH-001에서 선행 처리된 상태를 전제로 한다.

---

# Details

## 카드 역할

언어 성취율 카드는:

```text
현재 선택 언어의 최근 학습 성장 상태
```

를 빠르게 보여준다.

또한:

```text
Statistics 화면 진입 CTA
```

역할을 수행한다.

---

## 표시 예시

```text
문법 정확도 +8
어휘 다양성 +5
유창성 +3
자연스러움 +4
```

---

## CTA

```text
자세한 통계 보러 가기
```

---

## 사용 데이터

### DashSummary

`DASH-001`에서 로드된 `DashSummary[selectedLearningLanguage]`를 사용한다.

```json
{
  "language":"en",

  "grammarScoreDelta":8,

  "vocabularyScoreDelta":5,

  "fluencyScoreDelta":3,

  "naturalnessScoreDelta":4
}
```

---

## 사용 필드

- selectedLearningLanguage
- language
- grammarScoreDelta
- vocabularyScoreDelta
- fluencyScoreDelta
- naturalnessScoreDelta

---

## External Metrics 정책

Dashboard는:

```text
External Metrics 기반 성장량(delta)
```

만 노출한다.

Dashboard는:

- Internal Metrics 전체
- Language State 전체

를 직접 렌더링하지 않는다.

---

## delta 표시 정책

Dashboard는:

```text
절대 능력치
```

보다:

```text
최근 성장량(delta)
```

을 우선적으로 표시한다.

이유:

```text
사용자의 성장 체감 강화
```

를 목표로 하기 때문.

---

## 현재 선택 언어 정책

언어 성취율 카드는:

```text
DashSummary[selectedLearningLanguage]
```

기반으로 렌더링된다.

예:

```text
selectedLearningLanguage = "en"
→ English LanguageProgressCard 렌더링
```

여러 언어 성취율 카드를 동시에 렌더링하지 않는다.
학습 언어 변경은 `DASH-006`에서 처리한다.

---

## Navigation 정책

카드 클릭 시:

```text
Dashboard
→ Statistics
```

이동 수행.

---

## Statistics 초기 상태 정책

Statistics 화면 진입 시:

```text
selectedLearningLanguage
```

를 초기 통계 언어로 전달한다.

예:

```text
selectedLearningLanguage = "en"
→ English Statistics 화면 진입
```

---

## Empty 정책

통계 데이터가 부족한 경우:

```text
아직 충분한 학습 데이터가 없습니다.
AI와 더 많은 대화를 진행해보세요!
```

출력 가능.

---

## Loading 정책

카드 클릭 중:

- 중복 클릭 방지
- Navigation Loading 상태 가능

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/dashboard/
│   ├── components/
│   │   └── LanguageProgressCard.kt
│   └── DashboardScreen.kt
├── core/navigation/
│   └── Route.kt
└── domain/model/learningstate/
    └── LearningSummaryModels.kt
```

> Dashboard 카드는 `presentation/dashboard/components`에 둔다.
> Statistics 화면 이동 route는 기존 팀 구조에 맞춰 `core/navigation`에서 관리한다.

---

## 권장 컴포넌트 구조

```text
LanguageProgressCard
```

---

## 권장 파라미터 예시

```kotlin
@Composable
fun LanguageProgressCard(

    summary: DashSummary,

    selectedLearningLanguage: String,

    onClick: (String) -> Unit
)
```

---

## 권장 Navigation 전달 값

```kotlin
language: String // selectedLearningLanguage
```

---

# LanguageProgressCard 예시 데이터

```json
{
  "language":"en",

  "grammarScoreDelta":8,

  "vocabularyScoreDelta":5,

  "fluencyScoreDelta":3,

  "naturalnessScoreDelta":4
}
```

---

# UI 정책

## 카드 우선순위

Dashboard 하단 영역 배치 권장.

이유:

```text
장기 성장 확인 유도
```

역할 수행.

---

## 카드 클릭 영역

카드 전체 clickable 처리 권장.

---

## delta 표시 정책

delta 값은:

```text
+8
+5
+3
```

형태로 단순 표시 가능.

MVP 단계에서는:

- 퍼센트 변화율
- 장기 그래프

를 표시하지 않는다.

---

## Skeleton 정책

Dashboard preload 중:

- 카드 placeholder 표시 가능
- delta placeholder 표시 가능

---

# Error 정책

## Fatal Error

- summary null
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치

→ 카드 렌더링 생략 가능.

---

## Transient Error

- Navigation 실패

→ Snackbar 표시 가능.

---

# Edge Cases

- delta 값 전부 0
- 통계 데이터 부족
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치
- 카드 클릭 연타
- Navigation 실패
- 특정 언어 통계만 존재하지만 현재 선택 언어 통계는 없는 상태
- 일부 delta 데이터만 존재

---

# Related

## PR

- PR: #

---

## API / SDK

- Navigation Compose

---

## Design(Figma)

### 필요 화면

- Language Progress Card
- Card Loading Skeleton
- Empty Statistics Card

---

## 와이어프레임 체크

- 카드 배치 위치
- delta 강조 방식
- 카드 클릭 영역
- Empty 상태 메시지
- Skeleton placeholder 구조

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. 언어 성취율 카드 출력
3. 카드 클릭
4. Statistics 화면 이동
5. selectedLearningLanguage 전달 확인

---

## Empty 흐름

1. 통계 데이터 부족
2. Empty 카드 출력 확인

---

## 실패 흐름

1. Navigation 실패
2. selectedLearningLanguage null
3. summary null
4. 카드 중복 클릭

---

## 검토 후 수정 메모

- `DASH-005`는 여러 언어의 성취율 카드를 동시에 렌더링하지 않는다.
- 카드 데이터는 `DASH-001`에서 로드된 `DashSummary[selectedLearningLanguage]`를 사용한다.
- Statistics 화면에는 현재 앱 컨텍스트인 `selectedLearningLanguage`를 전달한다.
- Dashboard는 External Metrics delta만 노출하고, Internal Metrics 전체나 Language State 전체를 직접 렌더링하지 않는다.
- 학습 언어 변경은 `DASH-006`에서 처리한다.

---

# Labels

```text
type: feature
domain: dashboard
priority: medium
sprint: week1
```
