# [Feature] DASH-001 Dashboard 진입

## User Story

사용자는 Dashboard 진입 시 현재 학습 상태와 최근 학습 활동을 빠르게 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Dashboard 진입 시 LanguageDashboardSummary fetch가 수행된다.
- [ ] Dashboard 진입 시 UserLearningPreference preload가 수행된다.
- [ ] selectedLearningLanguage가 확인된다.
- [ ] Local Cache 기반으로 Dashboard가 빠르게 렌더링된다.
- [ ] 현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.
- [ ] Firebase background sync가 수행된다.
- [ ] Summary fetch 실패 시 fallback 데이터가 사용된다.
- [ ] 신규 사용자는 Empty Dashboard UI가 출력된다.
- [ ] Loading 상태 중 Skeleton UI가 표시된다.
- [ ] Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
- [ ] Summary fetch 중 중복 요청이 방지된다.
- [ ] 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-001 → Dashboard preload 및 Summary fetch

---

# 구현 범위

## 포함 범위

- Dashboard preload 처리
- UserLearningPreference preload
- selectedLearningLanguage 확인
- LanguageDashboardSummary fetch
- Local Cache 조회
- Firebase background sync
- Loading/Error/Empty 상태 처리
- Summary 데이터 렌더링
- Dashboard 초기 상태 구성
- 현재 선택 언어 기준 Summary 렌더링

---

## 제외 범위 (Out of Scope)

- AI Chat 기능
- Flashcard 학습 기능
- 통계 상세 화면
- recentFullContext 전체 조회
- 교정 기능
- Dashboard 카드 클릭 이동 처리
- 학습 언어 변경 처리 (DASH-006에서 구현)
- Firestore realtime snapshot observe

> Global Learning State 및 Summary 구조는 SYS-LEARNING-STATE-INFRA에서 선행 정의된 상태를 전제로 한다.

---

# Details

## Dashboard preload 정책

Dashboard 진입 시:

```text
UserLearningPreference Local preload
→ selectedLearningLanguage 확인
→ DashboardSummary[selectedLearningLanguage] Local Cache preload
→ 현재 선택 언어 기준 Dashboard 즉시 렌더링
→ Firebase background sync
→ 변경사항 존재 시 UI 갱신
```

순서로 동작한다.

오래된 Local Cache가 존재하더라도,
우선 렌더링 후 background sync를 수행한다.

Dashboard는:

```text
속도 우선 화면
```

정책을 사용한다.

---

## Summary 정책

Dashboard는:

- recentFullContext 전체
- Language State 전체

를 직접 계산하지 않는다.

또한 Dashboard는 Session Memory의 `recentFullContext`를 직접 조회하지 않는다.
교정 가능 여부와 최근 대화 길이는 `DashboardSummary[selectedLearningLanguage]`에 저장된 요약값만 사용한다.

대신:

```text
LanguageDashboardSummary[selectedLearningLanguage]
```

데이터만 사용하여 빠르게 렌더링한다.

---

## 현재 선택 언어 정책

Dashboard Summary는 언어별(Language Scoped) 구조를 사용한다.

예:

```text
English Summary
Japanese Summary
Spanish Summary
```

각 언어는 독립적으로 저장 및 집계된다.

하지만 `DASH-001`에서 Dashboard가 렌더링하는 대상은 전체 언어 목록이 아니라 현재 선택 언어의 Summary이다.

```text
selectedLearningLanguage = "en"
→ dashboardSummaries["en"] Local Cache 조회
→ dashboardSummaries["en"] Firebase background sync
→ 영어 Dashboard 카드 렌더링
```

학습 언어를 변경하는 selector 동작은 `DASH-006`에서 다룬다.

---

## Dashboard 카드 구성

### 1. 최근 AI 대화 카드

예시:

```text
오늘 영어로 여행 주제로 대화하셨습니다.
```

사용 데이터:

- selectedLearningLanguage
- recentConversationTopic
- recentConversationMinutes

---

### 2. 교정 대기 카드

예시:

```text
영어 대화 기록이 약 12분 있습니다.
```

사용 데이터:

- correctionAvailable
- activeSessionId
- recentConversationMinutes
- selectedLearningLanguage

---

### 3. Flashcard 학습 카드

예시:

```text
복습해야 할 Flashcard가 14장 있습니다.
```

사용 데이터:

- dueFlashcards
- recentSavedFlashcards

---

### 4. 대표 언어 성취율 카드

예시:

```text
문법 정확도 +8
어휘 다양성 +5
유창성 +3
자연스러움 +4
```

사용 데이터:

- grammarScoreDelta
- vocabularyScoreDelta
- fluencyScoreDelta
- naturalnessScoreDelta

Dashboard는:

```text
절대 점수보다 최근 성장량(delta)을 우선 노출한다.
```

---

## Loading 정책

Summary preload 중:

- Skeleton UI 표시
- 카드 placeholder 표시
- 사용자 Interaction 허용 가능

---

## Error 정책

### Fatal Error

- Local Cache load 실패
- Summary preload 불가능 상태

→ Error UI 표시 및 재시도 버튼 제공

---

### Transient Error

- Firebase background sync 실패
- 일부 Summary fetch 실패

→ fallback 데이터 사용 및 Snackbar 표시

---

## Empty 정책

신규 사용자:

- 최근 대화 없음
- Flashcard 없음
- 통계 없음

→ Empty Dashboard UI 표시

예:

```text
아직 학습 데이터가 없습니다.
AI와 첫 대화를 시작해보세요!
```

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/dashboard/
│   ├── DashboardScreen.kt
│   ├── DashboardViewModel.kt
│   └── components/
├── domain/model/
│   ├── LanguageDashboardSummaryVO.kt
│   └── UserLearningPreferenceVO.kt
├── domain/repository/
│   └── LearningStateRepository.kt
├── domain/usecase/
│   ├── PreloadDashboardUseCase.kt
│   └── ObserveDashboardSummaryUseCase.kt
├── data/repository/
│   └── LearningStateRepositoryImpl.kt
├── data/source/local/
│   └── DashboardLocalDataSource.kt
└── data/source/remote/
    └── DashboardRemoteDataSource.kt
```

> 팀 공통 아키텍처는 레이어 중심 Clean Architecture를 따른다.
> Dashboard 관련 UI/ViewModel은 `presentation/dashboard`에 두고,
> 모델·Repository interface·UseCase는 `domain`, 실제 저장소 구현과 DataSource는 `data`에 둔다.

---

## 권장 상태 구조

```kotlin
data class DashboardUiState(

    val isLoading: Boolean = false,

    val selectedLearningLanguage: Language? = null,

    val summary: LanguageDashboardSummaryVO? = null,

    val errorMessage: UiText? = null,

    val isEmpty: Boolean = false
)
```

---

## 권장 ViewModel 역할

### DashboardViewModel

역할:

- Dashboard preload
- UserLearningPreference preload
- selectedLearningLanguage 확인
- Local Cache fetch
- Firebase background sync
- LanguageDashboardSummary 상태 관리
- Empty/Error 상태 처리
- 현재 선택 언어 기준 Summary 렌더링 관리

---

# LanguageDashboardSummary 예시

```json
{
  "language":"en",

  "recentConversationMinutes":12,

  "recentConversationTopic":"Travel",

  "activeSessionId":"session_en",

  "correctionAvailable":true,

  "dueFlashcards":14,

  "recentSavedFlashcards":3,

  "grammarScoreDelta":8,

  "fluencyScoreDelta":3,

  "vocabularyScoreDelta":5,

  "naturalnessScoreDelta":4
}
```

---

# 데이터 조회 정책

## 조회 우선순위

```text
1. UserLearningPreference Local Cache
2. selectedLearningLanguage 확인
3. DashboardSummary[selectedLearningLanguage] Local Cache
4. Dashboard 즉시 렌더링
5. Firebase fetch
6. Cache update
7. UI refresh
```

---

## Firestore realtime snapshot observe 사용 금지

Dashboard는:

- realtime snapshot observe
- 실시간 sync

를 사용하지 않는다.

대신:

```text
Dashboard 진입 시 refresh
```

전략 사용.

---

# Edge Cases

- Local Cache 데이터 손상
- Firebase fetch 실패
- Summary null 반환
- 신규 사용자 상태
- Dashboard preload 중 앱 종료
- 중복 fetch 요청
- 일부 카드 데이터만 존재
- 오래된 cache 데이터 사용
- 인터넷 없이 앱 실행
- selectedLearningLanguage 없음
- selectedLearningLanguage에 해당하는 Summary 없음
- UserLearningPreference 없음
- 특정 언어 Summary만 존재하지만 현재 선택 언어 Summary는 없는 상태

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore / Room

---

## Design(Figma)

### 필요 화면

- Dashboard Screen
- Skeleton Loading UI
- Empty Dashboard UI
- Error State UI
- Dashboard Summary Card

---

## 와이어프레임 체크

- 언어별 카드 배치 구조 정의
- Skeleton UI 위치 정의
- Empty 상태 메시지 정의
- Error 상태 표시 방식 정의
- 카드별 placeholder 구조 정의
- 현재 선택 언어 Summary 렌더링 방식 정의

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. UserLearningPreference preload
3. selectedLearningLanguage 확인
4. 현재 선택 언어 Summary Local Cache preload
5. 현재 선택 언어 Summary 카드 출력
6. Firebase background sync
7. 최신 데이터 반영 확인

---

## Empty 흐름

1. 신규 사용자 로그인
2. Dashboard 진입
3. selectedLearningLanguage 기준 기본 Summary 확인
4. Empty Dashboard 표시 확인

---

## 실패 흐름

1. Firebase fetch 실패
2. Local Cache 손상
3. Summary null 반환
4. Error UI 표시 확인

---

## 검토 후 수정 메모

- Dashboard Summary는 언어별로 저장하지만, `DASH-001`의 렌더링 대상은 전체 언어 목록이 아니라 `selectedLearningLanguage`에 해당하는 단일 Summary이다.
- Dashboard 진입 시 `UserLearningPreference`를 먼저 preload하고 `selectedLearningLanguage`를 확인한다.
- 학습 언어 변경은 `DASH-006`에서 처리하며, `DASH-001`은 현재 선택 언어 기준 초기 진입과 preload만 담당한다.
- Dashboard는 `DashboardSummary[selectedLearningLanguage]`만 사용하며 recentFullContext 전체나 Language State 전체를 직접 계산하지 않는다.

---

# Labels

```text
type: feature
domain: dashboard
priority: high
sprint: week1
```
