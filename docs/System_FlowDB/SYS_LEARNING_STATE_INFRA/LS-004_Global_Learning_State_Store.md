# [Infra] LS-004 Global Learning State Store

## User Story

개발자는 Dashboard, AI Chat, Correction, Flashcard, Statistics가 동일한 사용자 학습 상태를 일관되게 사용할 수 있도록,
앱 전역에서 현재 사용자와 현재 선택 언어 기준의 학습 상태를 observe할 수 있는 Global Learning State Store 구조를 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [x] `GlobalLangState` 또는 동등한 전역 학습 상태 모델이 정의된다.
- [x] `UserLangPref`를 전역 학습 상태에 포함한다.
- [x] 언어별 `LangState` map 구조를 정의한다.
- [x] 언어별 `DashSummary` map 구조를 정의한다.
- [x] 언어별 Session Summary 참조 구조를 정의한다.
- [x] 언어별 Flashcard Summary 참조 구조를 정의한다.
- [x] 현재 선택 언어 기준 상태를 안전하게 조회할 수 있어야 한다.
- [x] `selectedLearningLanguage` 변경 시 현재 언어별 상태가 함께 전환될 수 있어야 한다.
- [x] Dashboard, AI Chat, Correction, Flashcard, Statistics의 observe 대상이 문서화된다.
- [x] Store는 UI 렌더링 상태가 아니라 Domain/App 상태로 정의된다.
- [x] Store는 원본 대화 전문 또는 Flashcard 전체 목록을 직접 들고 있지 않는다.
- [x] Store 초기 상태, Loading, Error, Empty 정책이 정의된다.
- [x] 로그아웃 시 현재 사용자 학습 상태를 초기화할 수 있어야 한다.
- [x] Local Cache / Firebase Sync 구현은 LS-005로 분리된다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- LS-001 → Language State Model Structure
- LS-002 → Dashboard Summary Model
- LS-003 → User Learning Preference Model
- LS-004 → Global Learning State Store
- AUTH-003 → 로그아웃
- AUTH-004 → 초기 사용자 설정
- DASH-001 → Dashboard preload 및 Summary fetch
- DASH-006 → Dashboard 학습 언어 selector 및 selectedLearningLanguage 변경

---

# 구현 범위

## 포함 범위

- Global Learning State 구조 정의
- Store 책임 경계 정의
- 화면별 observe 대상 정의
- selectedLearningLanguage 변경 시 상태 선택 정책 정의
- 신규 사용자 / 로그아웃 / 데이터 누락 상태 정책 정의
- Store 네이밍 및 패키지 위치 정의
- 초기 상태와 상태 전이 정책 정의

---

## 제외 범위 (Out of Scope)

- 개별 Domain 모델 상세 정의 (LS-001~003)
- Local Cache / Firebase Sync 구현 (LS-005)
- Language State 업데이트 알고리즘 구현 (LS-006)
- Dashboard / AI Chat / Flashcard / Statistics UI 구현
- Session Memory 전체 모델 구현
- Flashcard 전체 목록 관리
- Firestore 실시간 snapshot listener 구현

> LS-004는 “앱이 현재 사용자 학습 상태를 어떤 단위로 공유하고 observe할 것인가”를 정의한다.
> 저장소 구현, 동기화, 네트워크 정책은 LS-005에서 다룬다.

---

# Details

## Global Learning State 역할

Global Learning State는 앱 전역에서 공유되는 학습 상태의 현재 스냅샷이다.

주요 목적:

- 현재 사용자 학습 언어 컨텍스트 관리
- Dashboard 빠른 렌더링
- AI Chat의 현재 언어 상태 참조
- Correction / Flashcard / Statistics 진입 시 언어 컨텍스트 공유
- 로그아웃 시 이전 사용자 상태 제거

---

## Store 설계 원칙

### 1. Store는 원본 데이터 저장소가 아니다

Global Learning State Store는 다음 데이터를 직접 들고 있지 않는다.

- `recentFullContext` 전체 turn list
- Flashcard 전체 목록
- Statistics 전체 히스토리
- AI Chat streaming buffer
- 현재 입력 UI 상태

대신 화면 진입과 요약 렌더링에 필요한 “현재 상태 스냅샷”만 가진다.

---

### 2. 언어별 Map 구조

학습 상태는 언어별로 분리한다.

```text
languageStates["en"]
dashboardSummaries["en"]
sessionSummaries["en"]
flashcardSummaries["en"]
```

`selectedLearningLanguage`가 `"en"`이면 각 화면은 기본적으로 `"en"` 하위 상태를 사용한다.

---

### 3. selectedLearningLanguage는 Preference에서 온다

Global Learning State는 `selectedLearningLanguage`를 직접 별도 저장하지 않고,
Kotlin에서는 `userPref.selectedLang`를 기준으로 현재 언어 상태를 선택한다.
Firestore에서는 같은 값이 `user_learning_preference/current.selectedLearningLanguage`로 저장된다.

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── domain/model/learningstate/
│   ├── LearningCoreModels.kt
│   ├── LearningStateModels.kt
│   ├── LearningSummaryModels.kt
│   └── LearningProfileModels.kt
├── domain/repository/
│   └── LearningStateRepo.kt
└── domain/usecase/learningstate/
    ├── LearningStateReadUseCases.kt
    └── LearningStateWriteUseCases.kt
```

> Store의 실제 구현체 위치는 팀 구현 방식에 따라 `data/repository` 또는 별도 store 구현으로 둘 수 있다.
> 다만 UI가 직접 DataSource를 참조하지 않고 UseCase / Repository interface를 통해 observe하도록 한다.

---

## 권장 모델 구조

```kotlin
data class GlobalLangState(
    val userPref: UserLangPref?,
    val langStates: Map<LangCode, LangState>,
    val dashSummaries: Map<LangCode, DashSummary>,
    val sessionSummaries: Map<LangCode, SessionSummary>,
    val flashcardSummaries: Map<LangCode, FlashcardSummary>,
    val isPreloaded: Boolean = false,
    val schema: Int = 1
)
```

---

## 현재 선택 언어 상태 조회

```kotlin
val GlobalLangState.selectedLang: LangCode?
    get() = userPref?.selectedLang

fun GlobalLangState.currentLangState(): LangState? {
    val lang = selectedLang ?: return null
    return langStates[lang]
}

fun GlobalLangState.currentDashSummary(): DashSummary? {
    val lang = selectedLang ?: return null
    return dashSummaries[lang]
}
```

정책:

- `selectedLang == null`이면 Initial Setup 필요 또는 fallback 상태로 본다.
- 현재 선택 언어의 Summary가 없으면 Empty Summary 생성 또는 fetch 필요 상태로 본다.
- 현재 선택 언어의 Language State가 없으면 초기 Language State 생성 또는 fetch 필요 상태로 본다.

---

# Session Summary

Global Store는 Session Memory 전체가 아니라 요약만 가진다.

```kotlin
data class SessionSummary(
    val lang: LangCode,
    val correctionAvailable: Boolean,
    val recentMinutes: Int,
    val recentTopic: String?,
    val updatedAt: Long? = null
)
```

원본 `recentFullContext`는 Session Memory 저장소에서 관리한다.
Dashboard와 AI Chat 진입 판단에는 Summary만 사용한다.

---

# Flashcard Summary

Global Store는 Flashcard 전체 목록이 아니라 요약만 가진다.

```kotlin
data class FlashcardSummary(
    val lang: LangCode,
    val dueFlashcards: Int,
    val savedFlashcards: Int,
    val updatedAt: Long? = null
)
```

Flashcard 학습 화면에서 실제 카드 목록이 필요할 때 별도 Repository를 통해 조회한다.

---

# 화면별 observe 정책

## Dashboard

observe 대상:

- `userPref`
- `currentDashSummary`
- 현재 선택 언어의 `flashcardSummary`
- 현재 선택 언어의 `sessionSummary`

Dashboard는 원본 데이터를 직접 계산하지 않는다.

---

## AI Chat

observe 대상:

- `userPref.selectedLang`
- 현재 선택 언어의 `languageState`
- 현재 선택 언어의 `sessionSummary`

AI Chat은 대화 시작 시 현재 선택 언어를 초기 대화 언어로 사용한다.

---

## Correction

observe 대상:

- `userPref.selectedLang`
- 현재 선택 언어의 `sessionSummary`

Correction 화면은 `selectedLearningLanguage` 기준으로 Session Memory 원본을 별도 조회한다.
Global Store는 `recentFullContext` 전체를 직접 제공하지 않는다.

---

## Flashcard

observe 대상:

- `userPref.selectedLang`
- 현재 선택 언어의 `flashcardSummary`

실제 복습 카드 목록은 Flashcard Repository에서 별도 조회한다.

---

## Statistics

observe 대상:

- `userPref.selectedLang`
- 현재 선택 언어의 `languageState`
- 현재 선택 언어의 statistics summary

통계 히스토리 전체는 Statistics Repository에서 별도 조회한다.

---

# 상태 전이 정책

## 앱 시작

```text
앱 실행
→ Auth 상태 확인
→ 로그인된 사용자면 Global Learning State preload 시작
→ UserLangPref 로드
→ selectedLearningLanguage 확인
→ 현재 선택 언어의 Summary / Language State 로드
→ isPreloaded = true
```

---

## Initial Setup 완료

```text
Initial Setup 저장 완료
→ UserLangPref 생성
→ LangState 생성
→ DashSummary 생성
→ SessionMemory 기본값 생성
→ GlobalLangState 갱신
```

---

## Dashboard 언어 변경

```text
언어 선택
→ UserLangPref.selectedLearningLanguage 변경
→ GlobalLangState의 current 상태 선택 기준 변경
→ 선택 언어 Summary preload
→ Dashboard 재렌더링
```

---

## 로그아웃

로그아웃 시 현재 사용자 학습 상태를 반드시 초기화한다.

```text
logout
→ GlobalLangState.clear()
→ UserLangPref clear
→ selected user context clear
→ DashSummary in-memory cache clear
```

영구 저장된 Firebase / Local persist 데이터 삭제는 로그아웃 범위가 아니다.
단, 현재 앱 세션에서 이전 사용자 데이터가 화면에 남지 않아야 한다.

---

# 저장 정책

LS-004는 Store 구조와 observe 정책만 정의한다.

저장 위치와 동기화는 LS-005에서 확정한다.

예상 저장 책임:

| 데이터 | Store 보유 | 원본 저장 |
| --- | --- | --- |
| UserLangPref | 현재 스냅샷 | DataStore + Firestore |
| LangState | 언어별 스냅샷 | DataStore + Firestore |
| DashSummary | 언어별 스냅샷 | DataStore + Firestore |
| SessionSummary | 언어별 요약 | DataStore + Firestore |
| FlashcardSummary | 언어별 요약 | DataStore + Firestore |

---

# 상태 정책

## Loading

- `isPreloaded == false`이면 Global Learning State preload 중으로 볼 수 있다.
- 화면별로 Skeleton 또는 Loading UI를 표시할 수 있다.

## Error

### Fatal

- UserLangPref 없음
- selectedLearningLanguage 복구 실패
- 현재 선택 언어의 필수 Summary 생성 실패

→ Initial Setup 필요 또는 Error UI로 분기한다.

### Transient

- Firebase background sync 실패
- 일부 언어 Summary fetch 실패

→ Local 상태 유지 후 Snackbar로 안내할 수 있다.

## Empty

신규 사용자 또는 Initial Setup 미완료 사용자는 Global Learning State가 비어 있을 수 있다.

```text
userPref == null
→ Initial Setup 필요
```

---

# Edge Cases

- `userPref == null`
- `selectedLearningLanguage == null`
- `selectedLearningLanguage`가 `learningLanguages`에 없음
- 현재 선택 언어의 `LangState` 없음
- 현재 선택 언어의 `DashSummary` 없음
- `languageStates`에는 있으나 `dashboardSummaries`에는 없음
- 여러 언어의 Summary 중 일부만 로드됨
- Local Cache와 Firebase 값이 서로 다름
- 로그아웃 후 이전 사용자 상태가 UI에 남아 있음
- 앱 재시작 후 preload 중 화면이 먼저 렌더링됨
- 여러 화면이 동시에 selectedLearningLanguage 변경을 observe함
- 언어 변경 직후 이전 언어 카드 클릭 이벤트가 발생함
- Store가 너무 많은 원본 데이터를 들고 메모리 사용량이 증가함

---

# 테스트 시나리오

## 정상 흐름

1. 로그인된 사용자로 앱 실행
2. UserLangPref preload
3. `selectedLearningLanguage = EN` 확인
4. `languageStates[EN]`, `dashboardSummaries[EN]` 로드
5. `isPreloaded = true`
6. Dashboard가 EN Summary를 렌더링

---

## 언어 변경 흐름

1. `learningLanguages = [EN, JA]`
2. 현재 `selectedLearningLanguage = EN`
3. Dashboard selector에서 JA 선택
4. UserLangPref의 `selectedLearningLanguage = JA`로 변경
5. GlobalLangState가 JA Summary를 current 상태로 선택
6. Dashboard가 JA 기준으로 재렌더링

---

## 로그아웃 흐름

1. 사용자 A 로그인 상태
2. GlobalLangState에 A의 Summary 존재
3. 로그아웃 실행
4. GlobalLangState clear
5. Onboarding route 이동
6. 이전 사용자 A의 학습 데이터가 UI에 남지 않음 확인

---

## 실패 흐름

1. selectedLearningLanguage는 EN
2. `dashboardSummaries[EN]` 없음
3. Empty Summary 생성 또는 fetch 필요 상태로 분기
4. Firebase fetch 실패
5. Local fallback 또는 Error 상태 확인

---

# Related

## PR

- PR: #

---

## API / SDK

- Kotlin Flow / StateFlow
- Hilt
- DataStore / Room / Firebase Firestore 연동은 LS-005에서 확정

---

## Design(Figma)

해당 없음.

LS-004는 화면 UI가 아니라 App State / Store 구조 정의 이슈이다.

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
