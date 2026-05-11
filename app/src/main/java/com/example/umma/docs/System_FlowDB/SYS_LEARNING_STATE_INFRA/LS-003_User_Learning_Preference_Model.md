# [Infra] LS-003 User Learning Preference Model

## User Story

개발자는 사용자의 모국어, 주 학습 언어, 현재 선택된 학습 언어, 학습 중인 언어 목록을 일관되게 저장하고 조회할 수 있도록,
앱 전역 언어 컨텍스트를 관리하는 User Learning Preference 모델을 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [x] `UserLearningPreferenceVO`가 `domain/model`에 정의된다.
- [x] `nativeLanguage` 필드가 정의된다.
- [x] `primaryLearningLanguage` 필드가 정의된다.
- [x] `selectedLearningLanguage` 필드가 정의된다.
- [x] `learningLanguages` 필드가 정의된다.
- [x] `selectedLearningLanguage`는 현재 앱이 바라보는 학습 언어 컨텍스트로 정의된다.
- [x] `language` 필드와 `selectedLearningLanguage`의 차이가 문서화된다.
- [x] Initial Setup 완료 시 `selectedLearningLanguage = primaryLearningLanguage`로 초기화된다.
- [x] Initial Setup 완료 시 `learningLanguages`에는 `primaryLearningLanguage`가 포함된다.
- [x] Dashboard 언어 selector에서 `selectedLearningLanguage`를 변경할 수 있는 모델 구조가 정의된다.
- [x] MVP에서는 `learningLanguages`에 존재하는 언어만 선택 가능하도록 제한한다.
- [x] Firestore 저장 구조가 `users/{uid}/user_learning_preference/current` 기준으로 정의된다.
- [x] Local persist 저장을 고려해 직렬화 가능한 순수 Kotlin 모델로 작성된다.
- [x] 향후 필드 확장을 위해 `schemaVersion` 또는 동등한 버전 관리 필드를 포함한다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- FLOW-ONBOARDING
- AUTH-004 → 초기 사용자 설정
- FLOW-DASHBOARD
- DASH-006 → Dashboard 학습 언어 selector 및 selectedLearningLanguage 변경
- LS-003 → User Learning Preference Model

---

# 구현 범위

## 포함 범위

- User Learning Preference Domain VO 설계
- 지원 언어 표현 방식 정의
- Initial Setup 초기화 정책 정의
- Dashboard 언어 변경 정책 정의
- Firestore Schema 정의
- Local persist 직렬화 고려
- 모델 네이밍 및 패키지 위치 정의
- `language`와 `selectedLearningLanguage` 책임 경계 명시

---

## 제외 범위 (Out of Scope)

- Language State 모델 정의 (LS-001)
- Dashboard Summary 모델 정의 (LS-002)
- Global Learning State Store 구성 (LS-004)
- Local Cache / Firebase Sync 구현 (LS-005)
- Initial Setup UI 구현
- Dashboard Language Selector UI 구현
- 추가 학습 언어 등록 Flow 구현
- 학습 언어 삭제 / primaryLearningLanguage 변경 기능

> LS-003은 “앱이 현재 어떤 언어 컨텍스트를 바라보는가”를 저장하는 모델만 정의한다.
> 실제 저장, observe, sync, UI 변경 처리는 후속 이슈와 User Flow에서 다룬다.

---

# Details

## User Learning Preference 역할

User Learning Preference는 사용자 계정 기준의 언어 설정 데이터이다.

이 데이터는 다음 기능에서 공통으로 사용된다.

- Initial Setup
- Dashboard
- AI Chat
- Correction
- Flashcard
- Statistics

특히 `selectedLearningLanguage`는 Dashboard와 이후 이동하는 기능들이 사용할 현재 학습 언어 컨텍스트를 결정한다.

---

## 핵심 설계 원칙

### 1. 사용자 계정당 하나의 Preference

User Learning Preference는 언어별 문서가 아니라 사용자 계정당 하나의 현재 설정 문서로 저장한다.

```text
users/{uid}/user_learning_preference/current
```

---

### 2. `nativeLanguage`

사용자의 모국어이다.

사용 목적:

- 교정 카드 앞면
- Flashcard 힌트
- 제한적 모국어 보조
- Initial Setup 기본 사용자 설정

MVP 예시:

```text
ko
```

---

### 3. `primaryLearningLanguage`

사용자가 Initial Setup에서 처음 선택한 주 학습 언어이다.

사용 목적:

- 최초 Language State 생성 기준
- 최초 Dashboard Summary 생성 기준
- 최초 Session Memory 생성 기준
- `selectedLearningLanguage` 초기값

MVP 예시:

```text
en
ja
```

---

### 4. `selectedLearningLanguage`

현재 앱이 바라보는 학습 언어 컨텍스트이다.

사용 목적:

- Dashboard 카드 렌더링 기준
- AI Chat 시작 언어
- Correction 조회 언어
- Flashcard 학습 언어
- Statistics 표시 언어

예:

```text
selectedLearningLanguage = "en"
→ DashboardSummary["en"] 렌더링
→ AI Chat 진입 시 "en" 대화 시작
→ Flashcard 진입 시 "en" 카드만 조회
```

---

### 5. `learningLanguages`

사용자가 현재 학습 중인 언어 목록이다.

MVP에서는 주 학습 언어 1개만 선택 가능하므로:

```json
["en"]
```

향후 추가 학습 언어 등록 Flow가 생기면:

```json
["en", "ja"]
```

처럼 확장된다.

---

# `language`와 `selectedLearningLanguage` 차이

| 구분 | 위치 | 역할 |
| --- | --- | --- |
| `language` | Language State / Dashboard Summary / Session / Flashcard / Statistics | 해당 데이터가 어떤 언어에 속하는지 나타내는 소속 필드 |
| `selectedLearningLanguage` | User Learning Preference | 현재 앱 화면과 기능 이동이 어떤 언어를 바라볼지 결정하는 전역 컨텍스트 |

중요:

- `selectedLearningLanguage`를 바꿔도 기존 데이터의 `language`는 바뀌지 않는다.
- `selectedLearningLanguage`는 데이터의 소속을 변경하는 값이 아니다.
- Dashboard는 `selectedLearningLanguage`를 기준으로 해당 언어의 Summary를 선택한다.

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
└── domain/model/
    ├── UserLearningPreferenceVO.kt
    └── LanguageCode.kt
```

> LS-003은 Domain 모델만 정의한다.
> Repository interface, UseCase, DataSource, Sync 구현은 LS-004 / LS-005에서 다룬다.

---

## 권장 모델 구조

```kotlin
data class UserLearningPreferenceVO(
    val nativeLanguage: LanguageCode,
    val primaryLearningLanguage: LanguageCode,
    val selectedLearningLanguage: LanguageCode,
    val learningLanguages: List<LanguageCode>,
    val schemaVersion: Int = 1,
    val updatedAt: Long? = null
)
```

---

## LanguageCode

MVP에서는 단순 문자열 대신 enum 또는 value class를 사용해 오타를 줄인다.

### enum 방식

```kotlin
enum class LanguageCode(val code: String) {
    KO("ko"),
    EN("en"),
    JA("ja")
}
```

### value class 방식

```kotlin
@JvmInline
value class LanguageCode(val value: String)
```

MVP에서는 지원 언어가 제한적이므로 enum 방식이 더 안전하다.
향후 서버 기반 언어 확장이 필요해지면 value class로 전환할 수 있다.

---

# Initial Setup 초기화 정책

Initial Setup 완료 시 User Learning Preference를 생성한다.

```kotlin
fun createInitialUserLearningPreference(
    nativeLanguage: LanguageCode,
    primaryLearningLanguage: LanguageCode
): UserLearningPreferenceVO {
    return UserLearningPreferenceVO(
        nativeLanguage = nativeLanguage,
        primaryLearningLanguage = primaryLearningLanguage,
        selectedLearningLanguage = primaryLearningLanguage,
        learningLanguages = listOf(primaryLearningLanguage)
    )
}
```

초기화 이후 함께 생성되어야 하는 데이터:

- `language_states/{primaryLearningLanguage}`
- `dashboard_summaries/{primaryLearningLanguage}`
- `sessions/{primaryLearningLanguage}`

해당 생성 로직은 AUTH-004와 LS-004 / LS-005에서 다룬다.

---

# Dashboard 언어 변경 정책

Dashboard 학습 언어 selector에서 사용자가 언어를 변경하면:

```text
언어 선택
→ selectedLearningLanguage 변경
→ 해당 언어 Dashboard Summary 조회
→ Dashboard 카드 재렌더링
→ Firebase background sync
```

MVP 제한:

- `learningLanguages`에 이미 포함된 언어만 선택 가능하다.
- 추가 학습 언어 등록은 별도 Flow에서 처리한다.
- `primaryLearningLanguage`는 변경하지 않는다.
- `nativeLanguage`는 변경하지 않는다.

---

# Firestore 저장 구조

## Document Path

```text
users/{uid}/user_learning_preference/current
```

---

## Firestore Document 예시

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

# 네이밍 정책

## Kotlin

Kotlin 모델은 lowerCamelCase를 사용한다.

```kotlin
nativeLanguage
primaryLearningLanguage
selectedLearningLanguage
learningLanguages
```

## Firestore

Firestore 필드명도 Kotlin 모델과 동일한 lowerCamelCase를 사용한다.

---

# 상태 정책

## Loading

- 앱 시작 또는 Dashboard 진입 시 User Learning Preference preload가 수행될 수 있다.
- LS-003은 Loading UI를 구현하지 않는다.

## Error

### Fatal

- User Learning Preference 문서 없음
- `primaryLearningLanguage` 없음
- `learningLanguages` 비어 있음
- `selectedLearningLanguage`가 복구 불가능한 상태
- `schemaVersion` 미지원

→ Initial Setup 필요 상태로 전환한다.

### Transient

- Firebase background sync 실패
- `updatedAt` 누락

→ Local 값 유지 후 재시도 가능하다.

## Empty

신규 사용자 또는 Initial Setup 미완료 사용자는 User Learning Preference가 없을 수 있다.

이 경우:

```text
UserLearningPreference 없음
→ Initial Setup 표시
```

---

# Fallback 정책

## selectedLearningLanguage 없음

```text
selectedLearningLanguage == null
→ primaryLearningLanguage 사용
→ selectedLearningLanguage 복구 저장 시도
```

## selectedLearningLanguage가 learningLanguages에 없음

MVP에서는 다음 순서로 처리한다.

```text
selectedLearningLanguage not in learningLanguages
→ primaryLearningLanguage가 learningLanguages에 있으면 primaryLearningLanguage로 fallback
→ 그래도 불가능하면 Initial Setup 필요 상태
```

---

# Edge Cases

- User Learning Preference 문서 없음
- `nativeLanguage` null
- `primaryLearningLanguage` null
- `selectedLearningLanguage` null
- `learningLanguages` empty
- `selectedLearningLanguage`가 `learningLanguages`에 없음
- `primaryLearningLanguage`가 `learningLanguages`에 없음
- `nativeLanguage`와 `primaryLearningLanguage`가 같은 값
- 지원하지 않는 언어 코드
- `schemaVersion`이 현재 앱에서 지원하지 않는 값
- Firebase 저장 중 앱 종료
- Local Preference와 Firebase Preference 값 불일치
- 여러 기기에서 `selectedLearningLanguage`를 다르게 변경한 경우

---

# 테스트 시나리오

## 정상 흐름

1. Initial Setup에서 `nativeLanguage = "ko"`, `primaryLearningLanguage = "en"` 선택
2. `createInitialUserLearningPreference(KO, EN)` 호출
3. `selectedLearningLanguage = EN`으로 생성
4. `learningLanguages = [EN]`으로 생성
5. Firestore path `users/{uid}/user_learning_preference/current` 저장 가능 확인

---

## Dashboard 언어 변경 흐름

1. `learningLanguages = [EN, JA]`
2. 현재 `selectedLearningLanguage = EN`
3. 사용자가 Dashboard selector에서 `JA` 선택
4. `selectedLearningLanguage = JA`로 변경
5. Dashboard가 `dashboard_summaries/ja`를 조회
6. 기존 EN 데이터의 `language` 필드는 변경되지 않음 확인

---

## fallback 흐름

1. Firestore 문서에서 `selectedLearningLanguage` 누락
2. `primaryLearningLanguage = EN` 확인
3. `selectedLearningLanguage = EN`으로 fallback
4. 복구 저장 시도

---

## 실패 흐름

1. User Learning Preference 문서 없음
2. Initial Setup 필요 상태로 전환 확인
3. `learningLanguages = []`
4. Error 또는 Initial Setup 필요 상태 확인
5. 지원하지 않는 언어 코드 fetch
6. 모델 파싱 실패 또는 fallback 처리 확인

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore(Local persist 정책은 LS-005에서 확정)

---

## Design(Figma)

### 필요 화면

- Initial Setup Dialog
- Native Language Select UI
- Primary Learning Language Select UI
- Dashboard Learning Language Selector

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
