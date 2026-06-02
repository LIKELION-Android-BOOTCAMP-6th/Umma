# [Handover] CHAT-TUNE-001 Correction Learning Signal 계약

## 목적

Correction 담당자가 LearningState 구현 완료를 기다리지 않고 prompt/schema 작업을 진행할 수 있도록, Correction AI가 LearningState에 제공해야 하는 관찰 신호 계약을 정의한다.

Correction은 사용자의 최종 능력 점수나 레벨을 판단하지 않는다.
Correction은 교정 결과를 만들면서 이미 보고 있는 source/corrected 문장과 교정 의도를 구조화해 LearningState에 넘긴다.

이 문서는 Correction 담당자가 먼저 구현해야 하는 출력 계약을 고정한다.
Correction prompt가 사용할 사용자 능력 해석은 `LearnerAdaptationProfile` 계약을 따르며, 이 문서의 learning signal은 교정 후 LearningState가 장기 능력을 갱신하기 위한 입력이다.

언어 기준:

- `primaryLang`은 사용자가 학습을 이해하고 설명을 받을 기준 언어다. 모국어로 단정하지 않는다.
- `selectedLang`은 현재 교정 대상이 되는 학습 언어다.
- Correction 결과의 앞면/보조 설명은 `primaryLang` 기준으로 생성하고, 교정 후 문장은 `selectedLang` 기준으로 생성한다.
- 기존 응답 필드명 `nativeText`는 호환을 위해 유지할 수 있지만, 의미는 “모국어 문장”이 아니라 “`primaryLang` 기준 앞면 문장”이다.

---

# Correction이 제공할 것

- 관찰된 오류 유형
- 교정에서 개선된 유형
- 교정 문장의 register
- 의미 보존 여부
- AI 판단 confidence

# Correction이 제공하지 않을 것

- 최종 `grammarAccuracy`
- 최종 `vocabularyLevel`
- 최종 `fluencyScore`
- 최종 `LearnerAdaptationProfile`
- 사용자의 전체 레벨 판정
- `difficultyDelta`

`difficultyDelta`를 제외하는 이유:

- AI가 난이도 차이를 직접 라벨링하면 응답마다 기준이 흔들릴 수 있다.
- 난이도는 source/corrected 문장의 실제 길이, 구조, 어휘, register 변화와 기존 LangState를 함께 봐야 한다.
- “10% 성장” 판단은 LearningState가 일관된 정책으로 최종 결정해야 한다.

---

# Domain 계약

`CorrectionLearningSignal`은 LearningState가 해석할 관찰 신호 계약이다.
Correction 영역은 AI 응답을 이 계약으로 정규화해 completion pipeline에 넘기지만, 이 모델을 근거로 최종 점수나 레벨을 계산하지 않는다.

```kotlin
data class CorrectionResult(
    val correctedText: String? = null,
    val correctionCount: Int = 0,
    val notes: String? = null,
    val learningSignals: List<CorrectionLearningSignal> = emptyList()
)
```

```kotlin
data class CorrectionLearningSignal(
    val sourceText: String,
    val correctedText: String,
    val issueTypes: List<CorrectionIssueType>,
    val improvementTypes: List<CorrectionImprovementType>,
    val register: SpokenRegister,
    val meaningPreserved: Boolean,
    val confidence: Double?
)
```

계약 제한:

- `issueTypes`는 correction candidate당 최대 3개까지만 반환한다.
- `improvementTypes`도 correction candidate당 최대 3개까지만 반환한다.
- `register`는 하나만 반환한다.
- `confidence`는 `0.0..1.0` 범위의 값만 허용한다.
- `confidence`가 없으면 LearningState는 medium-low confidence로 취급한다.
- `confidence`가 범위를 벗어나면 해당 learning signal만 drop한다.
- unknown enum 문자열은 `LangState`에 저장하지 않는다.

모델 위치 원칙:

- domain 계약은 LearningState 갱신 입력으로 재사용될 수 있어야 하므로 presentation 전용 모델로 두지 않는다.
- Correction mapper는 AI 응답 DTO를 이 domain 계약으로 변환한다.
- LearningState domain은 이 계약을 입력으로 받아 evidence/focus/score 갱신 여부를 판단한다.
- Repository/data source는 변환된 결과를 저장하거나 전달할 뿐, 이 신호의 교육적 의미를 해석하지 않는다.

---

# Issue Type

```kotlin
enum class CorrectionIssueType {
    Article,
    Tense,
    Preposition,
    WordOrder,
    SentenceFragment,
    VocabularyChoice,
    LimitedVerbRange,
    UnnaturalCollocation,
    TooFormal,
    MissingContext
}
```

정책:

- AI 응답에 허용되지 않은 enum 값이 있으면 mapper는 해당 learning signal만 drop한다.
- signal drop이 발생해도 correction result 저장 자체는 막지 않는다.
- drop 시 Logcat에서 candidateId와 unknown value를 확인할 수 있어야 한다.
- 핵심 교정 문장 자체가 파싱 불가능한 경우에만 correction candidate 전체 실패로 처리한다.
- issue type이 비어 있으면 LearningState 반영 weight를 낮춘다.

---

# Improvement Type

```kotlin
enum class CorrectionImprovementType {
    GrammarFixed,
    StructureExpanded,
    MoreNaturalVerb,
    BetterCollocation,
    SpokenExpressionAdded,
    ShortenedForClarity,
    MeaningPreserved,
    MadeMoreCasual,
    MadeMorePolite
}
```

정책:

- `MeaningPreserved`는 correction 품질 확인용 signal이지, 사용자의 능력 점수로 직접 변환하지 않는다.
- `StructureExpanded`, `BetterCollocation`, `SpokenExpressionAdded`는 LearningState의 stretch/challenge guard 계산에 사용된다.

---

# Register

```kotlin
enum class SpokenRegister {
    Simple,
    EverydaySpoken,
    NativeLikeCasual,
    Formal
}
```

정책:

- register는 교정 결과 문장의 말투를 나타낸다.
- 사용자의 전체 register 능력을 직접 판정하지 않는다.
- `TooFormal` issue와 `EverydaySpoken` / `NativeLikeCasual` register가 함께 나오면 naturalness focus 후보가 될 수 있다.

---

# Correction Prompt에서 사용할 Profile

Correction prompt tune은 raw `LangState` metric을 직접 읽지 않는다.
Correction 담당자는 LearningState/Chat 담당자가 제공하는 `LearnerAdaptationProfile.correctionPolicy`를 사용해 교정 난이도와 설명 방식을 조정한다.

사용할 값:

- `challengeLevel`: 교정이 현재 문장을 유지할지, 작은 확장을 제안할지, 더 자연스러운 표현까지 제안할지 결정한다.
- `correctionStyle`: 문법 설명 중심인지, 자연스러운 구어체 제안 중심인지, 뉘앙스 설명까지 포함할지 결정한다.
- `vocabularyStrategy`: 쉬운 단어 유지, 한 개의 새 표현 추가, collocation 개선 같은 어휘 전략을 결정한다.
- `grammarStrategy`: 한 번에 하나의 구조만 고칠지, 문장 확장까지 허용할지 결정한다.
- `spokenRegisterStrategy`: `Simple`, `EverydaySpoken`, `NativeLikeCasual`, `Formal` 중 어떤 말투로 correctedText를 만들지 결정한다.
- `primaryLanguageSupport`: `primaryLang` 기준 보조 설명을 어느 정도 포함할지 결정한다.

금지:

- Correction prompt가 `grammarAccuracy = 0.42` 같은 raw metric을 직접 해석하지 않는다.
- Correction prompt가 사용자의 최종 CEFR level이나 profile을 새로 판정하지 않는다.
- Correction prompt가 `difficultyDelta`를 반환하지 않는다.

---

# JSON 응답 예시

아래 예시는 `primaryLang=KO`, `selectedLang=EN`인 경우다.
`nativeText`라는 필드명은 기존 저장/화면 계약과의 호환명이며, 내용은 `primaryLang` 기준 앞면 문장이다.

```json
{
  "candidateId": "candidate-1",
  "nativeText": "나는 어제 친구를 만났어",
  "afterText": "I met my friend yesterday.",
  "explanation": "시제와 어순을 자연스럽게 정리했어요.",
  "learningSignal": {
    "sourceText": "I meet friend yesterday",
    "correctedText": "I met my friend yesterday.",
    "issueTypes": ["Tense", "Article"],
    "improvementTypes": ["GrammarFixed"],
    "register": "EverydaySpoken",
    "meaningPreserved": true,
    "confidence": 0.82
  }
}
```

---

# 검증 기준

- Correction AI가 최종 능력 점수, 최종 레벨, `difficultyDelta`를 반환하지 않는다.
- learning signal enum 값은 허용 목록 안에 있다.
- unknown enum이 있으면 correction result 저장은 유지하고 해당 learning signal만 drop한다.
- `confidence`는 `0.0..1.0` 범위 안에 있거나 null이다.
- low confidence signal은 correction 저장은 가능하지만 LearningState 반영 weight가 낮다.
- meaning not preserved인 signal은 장기 능력 점수에 직접 반영하지 않는다.
- Correction prompt tune은 `LearnerAdaptationProfile.correctionPolicy`를 사용하고 raw `LangState` metric을 직접 해석하지 않는다.
