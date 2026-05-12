# [Infra] LS-007 Initial Learning State Persistence Contract

## User Story

개발자는 Initial Setup 완료 시 사용자 학습 상태의 초기 데이터를 일관되게 생성하고 저장할 수 있도록,
`UserLangPref`, `LangState`, `DashSummary`, `SessionSummary`, `FlashcardSummary`의 초기 저장 계약과 Firestore 매핑 기준을 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [x] Initial Setup 완료 시 생성해야 하는 학습 상태 데이터 묶음이 정의된다.
- [x] `UserLangPref.initial(...)` 생성 기준이 정의된다.
- [x] `LangState.initial(...)` 생성 기준이 정의된다.
- [x] `DashSummary.initial(...)` 생성 기준이 정의된다.
- [x] `SessionSummary.initial(...)` 생성 기준이 정의된다.
- [x] `FlashcardSummary.initial(...)` 생성 기준이 정의된다.
- [x] `LearningStateRepoImpl`의 초기 저장 책임이 정의된다.
- [x] Firestore 저장 필드와 Kotlin Domain 필드의 mapper 규칙이 정의된다.
- [x] Initial Setup 저장 실패 시 재시도 및 부분 생성 복구 정책이 정의된다.
- [x] Session Memory 원문 모델은 `SYS-REALTIME-INFRA`의 RT-003에서 구현한다는 범위가 명시된다.
- [x] AUTH-004가 호출할 학습 상태 초기화 계약이 문서화된다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- LS-001 -> Language State Model Structure
- LS-002 -> Dashboard Summary Model
- LS-003 -> User Learning Preference Model
- LS-004 -> Global Learning State Store
- LS-005 -> Local Cache & Sync Policy
- LS-006 -> Language State Update Policy
- LS-007 -> Initial Learning State Persistence Contract
- AUTH-004 -> 초기 사용자 설정

---

# 구현 범위

## 포함 범위

- Initial Setup 완료 시 생성할 학습 상태 초기 데이터 정의
- 초기값 생성 순서 정의
- Repository 구현체의 초기 저장 책임 정의
- Firestore mapper 필드 매핑 기준 정의
- 저장 실패 / 재시도 / 부분 생성 복구 정책 정의
- Session Memory 원문 모델의 보류 범위 명시

---

## 제외 범위 (Out of Scope)

- Google 로그인 및 Firebase Auth 구현
- `UserProfile` 모델 및 저장소 구현
- Initial Setup UI 구현
- 실제 Session Memory 원문 turn 모델 구현
- Room Entity / DAO 구현
- WorkManager 기반 sync retry 구현
- AI Chat streaming 및 turn append 구현

> LS-007은 온보딩이 학습 상태 초기값을 안전하게 만들 수 있도록 하는 저장 계약 이슈이다.
> 인증과 사용자 프로필은 SYS-COMMON-INFRA / AUTH Flow에서 다루고,
> 대화 원문 Session Memory 모델은 `SYS-REALTIME-INFRA`의 RT-003에서 다룬다.

---

# Details

## 핵심 원칙

### 1. Initial Setup은 학습 상태 초기값을 한 번에 만든다

Initial Setup 완료 시 다음 데이터를 같은 사용자 기준으로 생성한다.

```text
users/{uid}
├── user_learning_preference/current
├── language_states/{primaryLearningLanguage}
├── dashboard_summaries/{primaryLearningLanguage}
├── session_summaries/{primaryLearningLanguage}
└── flashcard_summaries/{primaryLearningLanguage}
```

`users/{uid}` 사용자 프로필 문서는 AUTH / UserProfile 영역에서 생성한다.
LS-007은 사용자 프로필이 아니라 학습 상태 초기값을 담당한다.

---

### 2. 주 학습 언어 기준으로 초기화한다

Initial Setup 입력값:

```text
nativeLang
primaryLang
```

초기 정책:

```text
selectedLang = primaryLang
learningLangs = [primaryLang]
```

따라서 모든 언어별 초기 데이터는 `primaryLang` 기준으로 생성한다.

---

### 3. Kotlin Domain 필드와 Firestore 필드는 mapper에서 변환한다

Kotlin Domain 모델은 짧은 이름을 사용한다.

```text
lang
selectedLang
learningLangs
recentMinutes
recentTopic
savedFlashcards
grammarDelta
vocabDelta
schema
```

Firestore 문서는 설명적인 이름을 사용한다.

```text
language
selectedLearningLanguage
learningLanguages
recentConversationMinutes
recentConversationTopic
recentSavedFlashcards
grammarScoreDelta
vocabularyScoreDelta
schemaVersion
```

Data Layer mapper가 두 명명 체계를 변환한다.

---

# Initial Setup 저장 흐름

```text
Initial Setup 완료
→ uid 확인
→ UserLangPref.initial(nativeLang, primaryLang) 생성
→ LangState.initial(primaryLang) 생성
→ DashSummary.initial(primaryLang) 생성
→ SessionSummary.initial(primaryLang) 생성
→ FlashcardSummary.initial(primaryLang) 생성
→ LearningStateRepo.createInitial(...) 호출
→ Local / Remote 저장
→ GlobalLangState 갱신
→ Dashboard 진입
```

---

# 초기 데이터 생성 기준

## UserLangPref

```kotlin
UserLangPref.initial(
    nativeLang = LangCode.KO,
    primaryLang = LangCode.EN
)
```

생성 결과:

```text
nativeLang = KO
primaryLang = EN
selectedLang = EN
learningLangs = [EN]
```

Firestore 저장 예시:

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "en",
  "learningLanguages": ["en"],
  "schemaVersion": 1,
  "updatedAt": "timestamp"
}
```

---

## LangState

```kotlin
LangState.initial(
    lang = LangCode.EN,
    createdAt = now,
    updatedAt = now
)
```

정책:

- `InternalMetrics`는 MVP 초기값으로 생성한다.
- `ExternalMetrics`는 사용자 통계 Empty 상태를 표현할 수 있는 초기값으로 생성한다.
- `lastAnalyzedAt`, `lastAnalysisEventId`는 아직 분석 전이므로 `null`로 둔다.

Firestore 저장 경로:

```text
users/{uid}/language_states/en
```

---

## DashSummary

```kotlin
DashSummary.initial(lang = LangCode.EN)
```

정책:

- 최근 대화 시간은 `0`
- 최근 대화 주제는 `null`
- 교정 가능 여부는 `false`
- 복습 카드 수는 `0`
- 성취 변화량은 모두 `0`

Firestore 저장 경로:

```text
users/{uid}/dashboard_summaries/en
```

---

## SessionSummary

```kotlin
SessionSummary.initial(lang = LangCode.EN)
```

정책:

- Session Memory 원문 모델이 아직 없어도 Dashboard / Correction 진입 판단용 요약은 만들 수 있다.
- 최근 대화 시간은 `0`
- 최근 대화 주제는 `null`
- 교정 가능 여부는 `false`

Firestore 저장 경로:

```text
users/{uid}/session_summaries/en
```

---

## FlashcardSummary

```kotlin
FlashcardSummary.initial(lang = LangCode.EN)
```

정책:

- 복습 예정 카드 수는 `0`
- 저장된 카드 수는 `0`

Firestore 저장 경로:

```text
users/{uid}/flashcard_summaries/en
```

---

# Repository 계약

## LearningStateRepo

온보딩 초기 저장은 기존 계약을 사용한다.

```kotlin
suspend fun createInitial(
    userUid: String,
    userPref: UserLangPref,
    langState: LangState,
    dashSummary: DashSummary,
    sessionSummary: SessionSummary,
    flashcardSummary: FlashcardSummary
): Result<Unit>
```

---

## LearningStateRepoImpl 책임

`LearningStateRepoImpl`은 다음을 담당한다.

- Domain 모델을 Firestore DTO / Map으로 변환한다.
- Initial Setup 초기 데이터 묶음을 저장한다.
- 저장 성공 후 local cache 또는 in-memory state를 갱신한다.
- 일부 문서가 이미 존재하면 같은 값으로 덮어쓰거나 보정한다.
- 저장 실패 시 `Result.failure`로 반환한다.

정책 계산이나 입력값 검증은 UseCase / ViewModel에서 수행한다.

---

# Mapper 규칙

## UserLangPref

| Kotlin | Firestore |
| --- | --- |
| `nativeLang` | `nativeLanguage` |
| `primaryLang` | `primaryLearningLanguage` |
| `selectedLang` | `selectedLearningLanguage` |
| `learningLangs` | `learningLanguages` |
| `schema` | `schemaVersion` |

---

## LangState

| Kotlin | Firestore |
| --- | --- |
| `lang` | `language` |
| `internal` | `internalMetrics` |
| `external` | `externalMetrics` |
| `schema` | `schemaVersion` |
| `lastAnalyzedAt` | `lastAnalyzedAt` |
| `lastAnalysisEventId` | `lastAnalysisEventId` |

---

## DashSummary

| Kotlin | Firestore |
| --- | --- |
| `lang` | `language` |
| `recentMinutes` | `recentConversationMinutes` |
| `recentTopic` | `recentConversationTopic` |
| `savedFlashcards` | `recentSavedFlashcards` |
| `grammarDelta` | `grammarScoreDelta` |
| `fluencyDelta` | `fluencyScoreDelta` |
| `vocabDelta` | `vocabularyScoreDelta` |
| `naturalnessDelta` | `naturalnessScoreDelta` |
| `schema` | `schemaVersion` |

---

## SessionSummary

| Kotlin | Firestore |
| --- | --- |
| `lang` | `language` |
| `correctionAvailable` | `correctionAvailable` |
| `recentMinutes` | `recentConversationMinutes` |
| `recentTopic` | `recentConversationTopic` |

---

## FlashcardSummary

| Kotlin | Firestore |
| --- | --- |
| `lang` | `language` |
| `dueFlashcards` | `dueFlashcards` |
| `savedFlashcards` | `recentSavedFlashcards` |

---

# 저장 실패 및 복구 정책

## 전체 저장 실패

```text
createInitial 실패
→ Initial Setup 저장 실패로 반환
→ 사용자는 재시도 가능
```

---

## 일부 문서만 생성된 경우

재시도 시 같은 `uid`와 `primaryLang` 기준으로 다음을 수행한다.

- 이미 존재하는 문서는 덮어쓰기 또는 merge
- 누락된 문서는 새로 생성
- `selectedLearningLanguage`는 `primaryLearningLanguage`로 보정
- Dashboard Summary가 없으면 Empty Summary 생성

---

## 중복 요청

Initial Setup 완료 버튼 중복 클릭 시 같은 초기 저장이 여러 번 호출될 수 있다.

정책:

- ViewModel에서 중복 클릭을 막는다.
- Repository는 같은 `uid` / `primaryLang`에 대해 idempotent 하게 동작해야 한다.
- 이미 setup이 완료된 사용자라면 성공으로 간주하거나 Dashboard로 이동한다.

---

# Session Memory 범위 결정

온보딩 단계에서는 실제 원문 Session Memory 문서를 생성하지 않는다.
현재 LS-001~006에서 구현된 것은 `SessionSummary`까지다.

따라서 LS-007 기준에서는:

- `SessionSummary.initial(primaryLang)`은 Initial Setup에서 생성한다.
- 원문 turn list를 담는 실제 `SessionMemory` 모델은 `SYS-REALTIME-INFRA`의 RT-003에서 구현한다.
- Dashboard와 온보딩은 원문 Session Memory에 직접 의존하지 않는다.

이렇게 분리하면 온보딩은 학습 상태 초기화까지 안전하게 처리하고,
AI Chat은 이후 대화 원문 저장 구조를 별도로 준비할 수 있다.

---

# Edge Cases

- `uid`가 비어 있음
- `primaryLang`이 지원하지 않는 언어
- `nativeLang`과 `primaryLang`이 같은 값
- `learningLangs`에 `primaryLang` 누락
- 이미 Initial Setup이 완료된 사용자
- `UserLangPref`만 있고 `LangState`가 없음
- `LangState`는 있으나 `DashSummary`가 없음
- Firebase 저장 중 일부 문서만 성공
- Local cache 저장 실패
- Firebase write 실패
- 앱 종료 중 Initial Setup 저장 미완료

---

# 테스트 시나리오

## 신규 사용자 정상 저장

1. Initial Setup에서 모국어와 주 학습 언어를 선택한다.
2. `UserLangPref.initial(...)`이 호출된다.
3. `LangState.initial(...)`이 호출된다.
4. `DashSummary.initial(...)`이 호출된다.
5. `SessionSummary.initial(...)`이 호출된다.
6. `FlashcardSummary.initial(...)`이 호출된다.
7. `LearningStateRepo.createInitial(...)`이 성공한다.
8. Dashboard가 Empty Summary로 진입 가능하다.

---

## 일부 문서 누락 복구

1. Initial Setup 저장 중 `DashSummary` 저장만 실패한다.
2. 사용자가 다시 저장을 시도한다.
3. 이미 존재하는 문서는 유지 또는 merge된다.
4. 누락된 `DashSummary`가 생성된다.
5. 전체 저장이 성공한다.

---

## 중복 클릭

1. 사용자가 완료 버튼을 빠르게 두 번 누른다.
2. ViewModel이 두 번째 요청을 막는다.
3. Repository에 중복 요청이 도달해도 같은 초기값 저장은 idempotent 하게 처리된다.

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore
- Kotlin Flow / StateFlow
- Hilt

---

## Design(Figma)

해당 없음.

LS-007은 화면 UI가 아니라 Initial Setup 이후 학습 상태 초기 저장 계약 정의 이슈이다.

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
