# [Infra] LS-002 Dashboard Summary Model

## User Story

개발자는 Dashboard가 Session Memory, Flashcard, Language State 원본 데이터를 직접 계산하지 않고도,
현재 선택 언어의 최근 학습 상태를 빠르게 렌더링할 수 있도록 언어별 Dashboard Summary 모델을 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [x] `LanguageDashboardSummaryVO`가 `domain/model`에 정의된다.
- [x] `LanguageDashboardSummaryVO`는 반드시 `language` 필드를 포함한다.
- [x] Dashboard Summary는 언어별(Language Scoped)로 저장된다.
- [x] 최근 AI 대화 카드에 필요한 필드가 포함된다.
- [x] 교정 대기 카드에 필요한 필드가 포함된다.
- [x] Flashcard 학습 카드에 필요한 필드가 포함된다.
- [x] 언어 성취율 카드에 필요한 delta 필드가 포함된다.
- [x] Dashboard는 `DashboardSummary[selectedLearningLanguage]`만으로 렌더링 가능해야 한다.
- [x] Dashboard Summary는 `recentFullContext` 전체를 포함하지 않는다.
- [x] Dashboard Summary는 Language State Internal Metrics 전체를 포함하지 않는다.
- [x] 신규 사용자용 Empty Dashboard 기본값을 생성할 수 있다.
- [x] Firestore 저장 구조가 `users/{uid}/dashboard_summaries/{language}` 기준으로 정의된다.
- [x] Local Cache 저장을 고려해 직렬화 가능한 순수 Kotlin 모델로 작성된다.
- [x] 향후 summary 구조 변경을 위해 `schemaVersion` 또는 동등한 버전 관리 필드를 포함한다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- FLOW-DASHBOARD
- DASH-001 → Dashboard preload 및 Summary fetch
- DASH-002 → 최근 AI 대화 카드
- DASH-003 → 교정 대기 카드
- DASH-004 → Flashcard 학습 카드
- DASH-005 → 언어 성취율 카드
- LS-002 → Dashboard Summary Model

---

# 구현 범위

## 포함 범위

- Dashboard Summary Domain VO 설계
- Dashboard 카드별 필요 필드 정의
- 신규 사용자 Empty Summary 초기값 정책 정의
- Firestore Schema 정의
- Local Cache 직렬화 고려
- 모델 네이밍 및 패키지 위치 정의
- `selectedLearningLanguage`와 `language`의 책임 경계 명시

---

## 제외 범위 (Out of Scope)

- Language State 모델 정의 (LS-001)
- User Learning Preference 모델 정의 (LS-003)
- Global Learning State Store 구성 (LS-004)
- Local Cache / Firebase Sync 구현 (LS-005)
- Dashboard Summary 재계산 로직 구현
- AI Chat, Correction, Flashcard, Statistics 기능 구현
- Dashboard 화면 UI 구현

> LS-002는 “Dashboard가 빠르게 읽을 요약 데이터의 모양”만 정의한다.
> 실제 fetch, sync, observe, update 로직은 후속 이슈와 User Flow에서 다룬다.

---

# Details

## Dashboard Summary 역할

Dashboard Summary는 Dashboard preload와 카드 렌더링을 위한 언어별 요약 데이터이다.

Dashboard는 다음 원본 데이터를 직접 계산하지 않는다.

- Session Memory의 `recentFullContext`
- Flashcard 전체 목록
- Language State Internal Metrics 전체
- Statistics 전체 히스토리

대신 현재 선택 언어 기준 Summary만 읽는다.

```text
selectedLearningLanguage = "en"
→ dashboard_summaries/en 조회
→ 영어 Dashboard 카드 렌더링
```

---

## 핵심 설계 원칙

### 1. 언어별 저장

Dashboard Summary는 학습 언어별로 분리한다.

```text
users/{uid}/dashboard_summaries/en
users/{uid}/dashboard_summaries/ja
users/{uid}/dashboard_summaries/es
```

예:

```json
{
  "language": "en"
}
```

---

### 2. Dashboard는 Summary 기반으로만 렌더링

Dashboard는 속도 우선 화면이다.

```text
Local Cache Summary
→ 즉시 렌더링
→ Firebase background sync
→ 변경사항 존재 시 UI 갱신
```

Dashboard 진입 시 원본 데이터를 직접 조회하거나 계산하지 않는다.

---

### 3. `language`와 `selectedLearningLanguage` 분리

| 구분 | 역할 |
| --- | --- |
| `language` | Summary 데이터가 어떤 학습 언어에 속하는지 나타내는 필드 |
| `selectedLearningLanguage` | 현재 앱이 어떤 언어의 Dashboard를 바라보는지 나타내는 전역 컨텍스트 |

`selectedLearningLanguage`는 Dashboard Summary 문서 안에 저장하지 않는다.
그 값은 User Learning Preference에서 관리한다.

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
└── domain/model/
    └── LanguageDashboardSummaryVO.kt
```

> LS-002는 Domain 모델만 정의한다.
> Repository interface, UseCase, DataSource, Sync 구현은 LS-004 / LS-005에서 다룬다.

---

## 권장 모델 구조

```kotlin
data class LanguageDashboardSummaryVO(
    val language: String,

    // Recent AI Conversation Card
    val recentConversationMinutes: Int,
    val recentConversationTopic: String?,

    // Correction Pending Card
    val correctionAvailable: Boolean,

    // Flashcard Study Card
    val dueFlashcards: Int,
    val recentSavedFlashcards: Int,

    // Language Progress Card
    val grammarScoreDelta: Int,
    val fluencyScoreDelta: Int,
    val vocabularyScoreDelta: Int,
    val naturalnessScoreDelta: Int,

    val schemaVersion: Int = 1,
    val updatedAt: Long? = null
)
```

---

# 카드별 필드 정책

## 1. 최근 AI 대화 카드

사용 필드:

```kotlin
recentConversationMinutes
recentConversationTopic
language
```

표시 예시:

```text
오늘 영어로 여행 주제로 대화하셨습니다.
```

정책:

- `recentConversationTopic`이 있으면 주제를 표시한다.
- `recentConversationTopic`이 없고 `recentConversationMinutes > 0`이면 최근 대화 기록만 표시한다.
- `recentConversationMinutes == 0`이면 Empty 상태를 표시한다.

---

## 2. 교정 대기 카드

사용 필드:

```kotlin
correctionAvailable
recentConversationMinutes
language
```

표시 예시:

```text
영어 대화 기록이 약 12분 있습니다.
```

정책:

- `correctionAvailable == true`이면 교정 CTA를 활성화한다.
- Correction 화면은 `selectedLearningLanguage` 기준으로 `users/{uid}/sessions/{language}`를 조회한다.
- Dashboard는 `recentFullContext`를 직접 조회하지 않는다.

---

## 3. Flashcard 학습 카드

사용 필드:

```kotlin
dueFlashcards
recentSavedFlashcards
language
```

표시 예시:

```text
복습해야 할 Flashcard가 14장 있습니다.
```

정책:

- `dueFlashcards > 0`이면 복습 CTA를 활성화한다.
- `dueFlashcards == 0`이면 Empty 상태를 표시한다.
- `recentSavedFlashcards > 0`이면 보조 문구로 최근 저장된 표현 수를 표시할 수 있다.

---

## 4. 언어 성취율 카드

사용 필드:

```kotlin
grammarScoreDelta
fluencyScoreDelta
vocabularyScoreDelta
naturalnessScoreDelta
language
```

표시 예시:

```text
문법 정확도 +8
어휘 다양성 +5
유창성 +3
자연스러움 +4
```

정책:

- Dashboard는 절대 점수보다 최근 성장량(delta)을 우선 노출한다.
- delta는 Dashboard Summary에 저장된 값만 사용한다.
- 상세 Statistics 그래프는 Statistics Flow에서 처리한다.

---

# 초기값 정책

Initial Setup 완료 시 주 학습 언어 기준 Dashboard Summary 기본값을 생성한다.

```kotlin
fun createInitialDashboardSummary(
    language: String
): LanguageDashboardSummaryVO {
    return LanguageDashboardSummaryVO(
        language = language,
        recentConversationMinutes = 0,
        recentConversationTopic = null,
        correctionAvailable = false,
        dueFlashcards = 0,
        recentSavedFlashcards = 0,
        grammarScoreDelta = 0,
        fluencyScoreDelta = 0,
        vocabularyScoreDelta = 0,
        naturalnessScoreDelta = 0
    )
}
```

신규 사용자에게는 이 Summary를 기반으로 Empty Dashboard를 렌더링한다.

---

# Firestore 저장 구조

## Collection Path

```text
users/{uid}/dashboard_summaries/{language}
```

예:

```text
users/user_001/dashboard_summaries/en
users/user_001/dashboard_summaries/ja
```

---

## Firestore Document 예시

```json
{
  "language": "en",
  "recentConversationMinutes": 12,
  "recentConversationTopic": "Travel",
  "correctionAvailable": true,
  "dueFlashcards": 8,
  "recentSavedFlashcards": 3,
  "grammarScoreDelta": 4,
  "fluencyScoreDelta": 2,
  "vocabularyScoreDelta": 5,
  "naturalnessScoreDelta": 1,
  "schemaVersion": 1,
  "updatedAt": "timestamp"
}
```

---

# 네이밍 정책

## Kotlin

Kotlin 모델은 lowerCamelCase를 사용한다.

```kotlin
recentConversationMinutes
recentConversationTopic
correctionAvailable
dueFlashcards
recentSavedFlashcards
grammarScoreDelta
```

## Firestore

Firestore 필드명도 Kotlin 모델과 동일한 lowerCamelCase를 사용한다.

---

# 상태 정책

## Loading

- Dashboard Summary local preload 중 Skeleton UI를 표시할 수 있다.
- LS-002는 Loading UI를 구현하지 않는다.

## Error

### Fatal

- Summary 모델 파싱 실패
- `language` 필드 누락
- `schemaVersion` 미지원

→ Empty Summary fallback 또는 재생성 플로우로 연결한다.

### Transient

- Firebase background sync 실패
- 일부 nullable 필드 누락

→ Local Cache 데이터를 유지하고 Snackbar로 안내할 수 있다.

## Empty

신규 사용자는 다음 값으로 Empty Dashboard를 렌더링한다.

```text
recentConversationMinutes = 0
correctionAvailable = false
dueFlashcards = 0
all delta = 0
```

---

# Edge Cases

- `language`가 null 또는 empty
- `language`가 `selectedLearningLanguage`와 다름
- `recentConversationMinutes`가 음수
- `dueFlashcards`가 음수
- `recentSavedFlashcards`가 음수
- delta 값이 비정상적으로 큰 값
- Summary 문서는 있으나 해당 Session Memory가 없음
- Summary 문서는 있으나 해당 Language State가 없음
- Local Cache Summary가 오래된 상태
- Firebase Summary fetch 실패
- `schemaVersion`이 현재 앱에서 지원하지 않는 값
- 추가 학습 언어 등록 후 해당 언어의 Summary가 아직 없음

---

# 테스트 시나리오

## 정상 흐름

1. Initial Setup에서 `primaryLearningLanguage = "en"` 선택
2. `createInitialDashboardSummary("en")` 호출
3. `language = "en"`인 Summary 생성
4. Dashboard가 `DashboardSummary["en"]`만으로 Empty 상태 렌더링 가능
5. AI Chat 후 `recentConversationMinutes`, `recentConversationTopic`, `correctionAvailable` 갱신 가능
6. Flashcard 저장 후 `recentSavedFlashcards` 갱신 가능
7. 복습 예정 카드 발생 시 `dueFlashcards` 갱신 가능

---

## 언어 변경 흐름

1. UserLearningPreference의 `selectedLearningLanguage = "ja"`로 변경
2. Dashboard가 `dashboard_summaries/ja`를 조회
3. 영어 Summary가 아닌 일본어 Summary만 렌더링
4. AI Chat / Correction / Flashcard / Statistics 이동 시 `"ja"` 컨텍스트 전달

---

## 실패 흐름

1. `selectedLearningLanguage = "en"`인데 `dashboard_summaries/en` 없음
2. Empty Summary 생성 또는 fallback 처리 확인
3. `correctionAvailable == true`인 Summary 로드
4. Correction 화면이 `users/{uid}/sessions/en` 조회
5. Session Memory가 없으면 Summary 복구 또는 Empty 상태 처리
6. Firebase background sync 실패
7. Local Cache Summary 유지 및 non-blocking error 표시 확인

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore 또는 Room(Local Cache 정책은 LS-005에서 확정)

---

## Design(Figma)

### 필요 화면

- Dashboard Screen
- Recent AI Conversation Card
- Correction Pending Card
- Flashcard Study Card
- Language Progress Card
- Empty Dashboard State
- Loading Skeleton State

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
