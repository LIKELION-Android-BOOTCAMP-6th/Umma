# [Infra] LS-001 Language State Model Structure

## User Story

개발자는 사용자 언어 능력 상태를 언어별로 일관되게 저장하고 조회할 수 있도록,
AI 대화 적응용 Internal Metrics와 사용자 통계 표시용 External Metrics를 포함한 Language State 모델을 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [x] `LangState`가 `domain/model`에 정의된다.
- [x] `LangState`는 반드시 `lang` 필드를 포함하고, Firestore 저장 시 `language`로 매핑된다.
- [x] Internal Metrics 구조가 `InternalMetrics`로 정의된다.
- [x] External Metrics 구조가 `ExternalMetrics`로 정의된다.
- [x] MVP Internal Metrics 12개가 모두 포함된다.
- [x] External Metrics 5개가 모두 포함된다.
- [x] `vocabularyLevel`은 CEFR 기반 값(A1~C2)을 표현할 수 있다.
- [x] 신규 사용자용 초기 Language State 기본값을 생성할 수 있다.
- [x] Firestore 저장 구조가 `users/{uid}/language_states/{language}` 기준으로 정의된다.
- [x] Local Cache 저장을 고려해 직렬화 가능한 순수 Kotlin 모델로 작성된다.
- [x] UI / ViewModel 전용 상태와 Domain Model이 섞이지 않는다.
- [x] Language State 모델은 Android `Context`에 의존하지 않는다.
- [x] 향후 지표 확장을 위해 `schemaVersion` 또는 동등한 버전 관리 필드를 포함한다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- LS-001 → Language State Model Structure

---

# 구현 범위

## 포함 범위

- Language State Domain Model 설계
- Internal Metrics Model 설계
- External Metrics Model 설계
- CEFR Vocabulary Level 표현 방식 정의
- 신규 사용자 초기값 정책 정의
- Firestore Schema 정의
- Local Cache 직렬화 고려
- 모델 네이밍 및 패키지 위치 정의

---

## 제외 범위 (Out of Scope)

- Dashboard Summary 모델 정의 (LS-002)
- User Learning Preference 모델 정의 (LS-003)
- Global Learning State Store 구성 (LS-004)
- Local Cache / Firebase Sync 구현 (LS-005)
- Language State 업데이트 알고리즘 구현 (LS-006)
- Type A / B / C 분석 로직 구현
- AI Correction / Statistics 화면 구현

> LS-001은 “언어 능력 상태를 어떤 모양으로 저장할 것인가”만 정의한다.
> 실제 계산, 업데이트, 동기화, 화면 표시 정책은 후속 이슈에서 다룬다.

---

# Details

## Language State 역할

Language State는 사용자의 장기 언어 능력 상태를 언어별로 압축 저장하는 핵심 모델이다.

AI Chat은 Language State를 참고하여:

- 단어 난이도
- 문장 길이
- 대화 속도
- scaffolding 정도
- 교정 문장 난이도

를 조절한다.

Statistics와 Dashboard는 Language State를 직접 모두 렌더링하지 않고,
External Metrics 또는 Dashboard Summary로 가공된 값을 사용한다.

---

## 핵심 설계 원칙

### 1. 언어별 저장

Language State는 반드시 학습 언어별로 분리한다.

```text
users/{uid}/language_states/en
users/{uid}/language_states/ja
users/{uid}/language_states/es
```

예:

```json
{
  "language": "en"
}
```

---

### 2. Internal / External Metrics 분리

Language State는 두 목적을 동시에 가진다.

```text
Language State
├── Internal Metrics
│   └── AI 대화 적응 / 교정 분석용
└── External Metrics
    └── 사용자 통계 표시용
```

Internal Metrics는 세분화되어 있고 사용자에게 직접 노출하지 않는다.
External Metrics는 사용자가 성장을 직관적으로 이해할 수 있도록 집계된 값이다.

---

### 3. 계산 가능한 지표와 AI 분석 지표 분리

LS-001에서는 지표 필드만 정의한다.
각 지표가 어떻게 계산되는지는 LS-006에서 Type A / B / C 정책으로 다룬다.

```text
Type A: 코드 계산
Type B: 규칙 기반 분석
Type C: AI 분석
```

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
└── domain/model/learningstate/
    ├── LearningCoreModels.kt
    └── LearningStateModels.kt
```

> LS-001은 Domain 모델만 정의한다.
> Repository interface, UseCase, DataSource, Sync 구현은 후속 이슈에서 다룬다.

---

## 권장 모델 구조

```kotlin
data class LangState(
    val lang: LangCode,
    val internal: InternalMetrics,
    val external: ExternalMetrics,
    val schema: Int = 1,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
```

> Kotlin Domain 모델은 짧은 필드명을 사용한다.
> Firestore 저장 시에는 mapper에서 `lang → language`, `internal → internalMetrics`, `external → externalMetrics`, `schema → schemaVersion`으로 변환한다.

---

## Internal Metrics

MVP Internal Metrics는 12개로 제한한다.

```kotlin
data class InternalMetrics(
    // Accuracy
    val grammarAccuracy: Double,
    val vocabularyAppropriateness: Double,

    // Range
    val lexicalDiversity: Double,
    val vocabularyLevel: VocabLevel,
    val sentenceComplexity: Double,

    // Fluency
    val speechRate: Double,
    val pauseFrequency: Double,
    val avgUtteranceLength: Double,

    // Naturalness
    val spokenNaturalness: Double,
    val naturalExpressionUsage: Double,

    // Learning
    val errorRecurrence: Double,
    val reviewRetention: Double
)
```

### Accuracy

| 필드 | 의미 |
| --- | --- |
| `grammarAccuracy` | 문법 정확도 통합 지표 |
| `vocabularyAppropriateness` | 문맥에 맞는 어휘 선택 능력 |

### Range

| 필드 | 의미 |
| --- | --- |
| `lexicalDiversity` | 어휘 다양성 |
| `vocabularyLevel` | CEFR 기반 어휘 수준 |
| `sentenceComplexity` | 문장 구조 복잡도 |

### Fluency

| 필드 | 의미 |
| --- | --- |
| `speechRate` | 발화 속도 |
| `pauseFrequency` | 머뭇거림 빈도 |
| `avgUtteranceLength` | 평균 발화 길이 |

### Naturalness

| 필드 | 의미 |
| --- | --- |
| `spokenNaturalness` | 구어체 자연스러움 |
| `naturalExpressionUsage` | 자연스러운 표현 사용 정도 |

### Learning

| 필드 | 의미 |
| --- | --- |
| `errorRecurrence` | 교정 후 반복 오류율 |
| `reviewRetention` | Flashcard 기억 유지율 |

---

## External Metrics

사용자에게 통계 화면에서 직접 보여줄 수 있는 지표는 5개로 제한한다.

```kotlin
data class ExternalMetrics(
    val vocabularyLevel: VocabLevel,
    val grammarAccuracy: Double,
    val expressionRange: Int,
    val fluencyScore: Double,
    val naturalnessScore: Double
)
```

| 필드 | 표시 방식 | 원천 지표 |
| --- | --- | --- |
| `vocabularyLevel` | A1 ~ C2 | internal `vocabularyLevel` |
| `grammarAccuracy` | % 또는 100점 | internal `grammarAccuracy` |
| `expressionRange` | 누적 표현/어휘 수 | lexical diversity / 실제 발화 어휘 집계 |
| `fluencyScore` | 0.0 ~ 1.0 또는 100점 환산 | speechRate, pauseFrequency, avgUtteranceLength |
| `naturalnessScore` | 0.0 ~ 1.0 또는 100점 환산 | spokenNaturalness, naturalExpressionUsage |

---

## Vocabulary Level

CEFR 기반 어휘 수준은 enum 또는 sealed type으로 정의한다.

```kotlin
enum class VocabLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2
}
```

MVP 신규 사용자의 기본값은 `A1`이다.

---

# 초기값 정책

Initial Setup 완료 시 주 학습 언어 기준 Language State 기본값을 생성한다.

```kotlin
LangState.initial(lang = LangCode.EN)
```

---

# Firestore 저장 구조

## Collection Path

```text
users/{uid}/language_states/{language}
```

예:

```text
users/user_001/language_states/en
users/user_001/language_states/ja
```

---

## Firestore Document 예시

```json
{
  "language": "en",
  "schemaVersion": 1,
  "internalMetrics": {
    "grammarAccuracy": 0.0,
    "vocabularyAppropriateness": 0.0,
    "lexicalDiversity": 0.0,
    "vocabularyLevel": "A1",
    "sentenceComplexity": 0.0,
    "speechRate": 0.0,
    "pauseFrequency": 0.0,
    "avgUtteranceLength": 0.0,
    "spokenNaturalness": 0.0,
    "naturalExpressionUsage": 0.0,
    "errorRecurrence": 0.0,
    "reviewRetention": 0.0
  },
  "externalMetrics": {
    "vocabularyLevel": "A1",
    "grammarAccuracy": 0.0,
    "expressionRange": 0,
    "fluencyScore": 0.0,
    "naturalnessScore": 0.0
  },
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

---

# 네이밍 정책

## Kotlin

Kotlin 모델은 lowerCamelCase를 사용한다.

```kotlin
grammarAccuracy
vocabularyAppropriateness
avgUtteranceLength
naturalnessScore
```

## Firestore

Firestore 필드명도 Kotlin 모델과 동일한 lowerCamelCase를 사용한다.

> 기존 문서 일부에 등장하는 `grammar_accuracy` 형태는 개념 설명용 이름이며,
> 실제 Kotlin / Firestore 구현에서는 lowerCamelCase로 통일한다.

---

# 상태 정책

## Loading

- Language State를 preload하는 동안 화면은 Loading 또는 Skeleton을 표시할 수 있다.
- LS-001은 Loading UI를 구현하지 않는다.

## Error

### Fatal

- Language State 모델 파싱 실패
- `language` 필드 누락
- `schemaVersion` 미지원

→ 기본값 fallback 또는 재생성 플로우로 연결한다.

### Transient

- 일부 metric 값 누락
- timestamp 누락

→ 기본값 보정 후 사용 가능하다.

## Empty

신규 사용자 또는 Initial Setup 미완료 사용자는 Language State가 없을 수 있다.

이 경우:

```text
Language State 없음
→ Initial Setup 필요 상태
또는
→ selected primaryLearningLanguage 기준 기본 Language State 생성
```

---

# Edge Cases

- `language`가 null 또는 empty
- `language`가 `learningLanguages`에 포함되지 않음
- `internalMetrics` 일부 필드 누락
- `externalMetrics` 일부 필드 누락
- `vocabularyLevel`이 A1~C2 범위를 벗어남
- 수치형 metric이 0.0~1.0 범위를 벗어남
- `expressionRange`가 음수
- `schemaVersion`이 현재 앱에서 지원하지 않는 값
- Firestore 문서는 있으나 Local Cache 모델과 버전 불일치
- 신규 사용자에게 Language State가 아직 생성되지 않음
- 추가 학습 언어 등록 후 해당 언어의 Language State가 아직 없음

---

# 테스트 시나리오

## 정상 흐름

1. Initial Setup에서 `primaryLearningLanguage = "en"` 선택
2. `LangState.initial(LangCode.EN)` 호출
3. `lang = EN`인 LangState 생성
4. Internal Metrics 12개 기본값 생성 확인
5. External Metrics 5개 기본값 생성 확인
6. Firestore path `users/{uid}/language_states/en` 저장 가능 확인

---

## 다국어 흐름

1. 사용자가 추가 학습 언어 `"ja"`를 등록
2. `LangState.initial(LangCode.JA)` 호출
3. 기존 `"en"` LangState와 별도 문서로 저장
4. `"en"`과 `"ja"`의 metric 값이 서로 섞이지 않음 확인

---

## 실패 흐름

1. Firestore에서 `language` 필드가 없는 문서 fetch
2. 모델 파싱 실패 또는 fallback 처리 확인
3. `vocabularyLevel = "Z9"` 같은 잘못된 값 fetch
4. 기본값 보정 또는 error state 전달 확인
5. `schemaVersion` 미지원 문서 fetch
6. migration 필요 상태로 분기 확인

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- Kotlin Serialization 또는 Firestore mapper

---

## Design(Figma)

해당 없음.

LS-001은 UI 화면이 아니라 Domain Model / Firestore Schema 정의 이슈이다.

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
