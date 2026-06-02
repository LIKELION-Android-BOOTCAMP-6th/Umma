# [Implementation] CHAT-TUNE-001-C LearnerAdaptationProfile

## 목적

`LearnerAdaptationProfile`은 저장된 `LangState`를 Chat/Correction prompt에서 재사용할 수 있는 교육 전략으로 압축하는 domain read model이다.
Chat과 Correction이 raw metric을 각자 해석하면 난이도 정책이 갈라지므로, 공통 profile을 먼저 만든다.

---

# 포함 범위

- `LearnerAdaptationProfile` 모델 추가
- `BuildLearnerAdaptationProfileUseCase` 추가
- `core`, `chatPolicy`, `correctionPolicy` 분리
- 초기/저신뢰 상태 conservative 처리
- active focus 상위 1~2개 선택

# 완료 기준(AC)

- [ ] Chat과 Correction이 같은 profile을 통해 사용자의 언어능력을 해석할 수 있다.
- [ ] 저장된 raw metric 숫자는 Chat/Correction에 직접 노출되지 않는다.
- [ ] `LangState`가 없거나 아직 분석 근거가 부족하면 보수적인 beginner-safe 정책이 만들어진다.
- [ ] 문법, 어휘, 유창성, 자연스러움은 하나의 총점이 아니라 각각의 단계로 해석된다.
- [ ] 반복 학습 초점이 여러 개 있어도 profile에는 중요한 1~2개만 요약된다.
- [ ] Chat용 대화 정책과 Correction용 교정 정책은 같은 core 능력 판단을 공유한다.
- [ ] `primaryLang`과 `selectedLang`은 profile에 저장하지 않고, prompt 문장을 만들 때만 함께 사용한다.

# 제외 범위

- 저장 모델로 profile 저장
- prompt 문장 저장
- Correction prompt 실제 튜닝
- Chat transport 변경

---

# 모델 방향

```kotlin
data class LearnerAdaptationProfile(
    val core: LearnerAbilityProfile,
    val chatPolicy: ChatAdaptationPolicy,
    val correctionPolicy: CorrectionAdaptationPolicy
)
```

```kotlin
data class LearnerAbilityProfile(
    val cefrLevel: VocabLevel,
    val levelConfidence: ProfileConfidence,
    val grammarStage: SkillStage,
    val vocabularyStage: SkillStage,
    val fluencyStage: SkillStage,
    val naturalnessStage: SkillStage,
    val focus: LearningFocusSummary
)
```

```kotlin
data class LearningFocusSummary(
    val primaryFocus: LearningFocusType?,
    val secondaryFocus: LearningFocusType?,
    val confidence: ProfileConfidence,
    val observedCount: Int
)
```

정책:

- `LearningFocusSummary`는 저장 모델이 아니라 profile 요약 모델이다.
- 저장 모델의 `analysisMeta.activeFocus` 중 confidence와 최근성이 높은 상위 1~2개만 노출한다.
- focus가 없거나 confidence가 낮으면 `primaryFocus = null`, `secondaryFocus = null`로 둔다.
- Chat/Correction prompt builder는 이 요약만 보고 학습 초점을 반영하고, `analysisMeta.activeFocus` 전체를 직접 순회하지 않는다.

```kotlin
data class ChatAdaptationPolicy(
    val challengeLevel: ChallengeLevel,
    val responseLength: ResponseLengthPolicy,
    val questionStyle: QuestionStylePolicy,
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy
)
```

```kotlin
data class CorrectionAdaptationPolicy(
    val challengeLevel: ChallengeLevel,
    val correctionStyle: CorrectionStylePolicy,
    val vocabularyStrategy: VocabularyStrategyPolicy,
    val grammarStrategy: GrammarStrategyPolicy,
    val spokenRegisterStrategy: SpokenRegisterStrategy,
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy
)
```

`CorrectionAdaptationPolicy`는 Correction prompt builder가 직접 사용하는 계약이다.
Correction 담당자는 이 policy 값을 instruction text로 바꾸되, raw `LangState` metric을 다시 해석하지 않는다.
`primaryLanguageSupport`는 설명과 힌트를 사용자의 학습 기준 언어(`primaryLang`)로 얼마나 제공할지 결정한다.
`primaryLang`은 모국어로 단정하지 않으며, 현재 학습 대상 언어(`selectedLang`)와 다를 수 있다.

---

# Stage / Challenge

```kotlin
enum class SkillStage {
    Foundation,
    Developing,
    Stable,
    Expanding,
    Refined
}
```

```kotlin
enum class ChallengeLevel {
    Support,
    Match,
    Stretch,
    Refine
}
```

정책:

- `Foundation`: 짧고 쉬운 패턴, 한 번에 하나만
- `Developing`: 기본 문장 유지 + 작은 확장 하나
- `Stable`: 자연스러운 follow-up, 이유/경험 질문
- `Expanding`: 다양한 표현, 연결어, 구어체 표현 추가
- `Refined`: 뉘앙스, register, collocation, 원어민식 표현

```kotlin
enum class ProfileConfidence {
    Low,
    Medium,
    High
}
```

```kotlin
enum class ResponseLengthPolicy {
    OneShortSentence,
    ShortTwoStep,
    NaturalBrief,
    Flexible
}
```

```kotlin
enum class QuestionStylePolicy {
    OneConcreteQuestion,
    GuidedChoiceQuestion,
    OpenFollowUp,
    NuanceFollowUp
}
```

정책:

- `ProfileConfidence.Low`: 분석 이력이 없거나 지표가 서로 충돌하는 상태다. Chat/Correction은 conservative 하게 동작한다.
- `ProfileConfidence.Medium`: 반복 관측은 있지만 아직 level 확정에는 부족한 상태다. 작은 challenge만 허용한다.
- `ProfileConfidence.High`: 여러 근거가 같은 방향으로 누적된 상태다. profile의 stage/challenge 정책을 그대로 사용할 수 있다.
- `OneShortSentence`: Foundation/low confidence 상태에서 한 번에 하나의 짧은 응답만 제공한다.
- `ShortTwoStep`: 짧은 답변과 쉬운 후속 질문 하나를 제공한다.
- `NaturalBrief`: 일반 대화처럼 자연스럽지만 장황하지 않게 답한다.
- `Flexible`: 고급 사용자에게 설명, 예시, 뉘앙스를 필요에 따라 조금 더 허용한다.
- `OneConcreteQuestion`: yes/no 또는 짧은 답이 가능한 구체 질문을 우선한다.
- `GuidedChoiceQuestion`: 선택지를 주어 사용자가 다음 발화를 만들 수 있게 돕는다.
- `OpenFollowUp`: 이유, 경험, 선호를 묻는 자연스러운 follow-up을 사용한다.
- `NuanceFollowUp`: register, 뉘앙스, 더 자연스러운 표현 선택을 유도한다.

```kotlin
enum class CorrectionStylePolicy {
    MinimalFix,
    ExplainOneReason,
    NaturalSpokenRewrite,
    NuanceAndRegister
}
```

```kotlin
enum class VocabularyStrategyPolicy {
    KeepSimpleWords,
    AddOneUsefulExpression,
    ImproveCollocation,
    RefineNativeChoice
}
```

```kotlin
enum class GrammarStrategyPolicy {
    FixBlockingErrorOnly,
    FixOneMainPattern,
    ExpandSentenceStructure,
    RefineAdvancedStructure
}
```

```kotlin
enum class SpokenRegisterStrategy {
    Simple,
    EverydaySpoken,
    NativeLikeCasual,
    FormalWhenNeeded
}
```

```kotlin
enum class PrimaryLanguageSupportPolicy {
    PrimaryLanguageFirst,
    BriefPrimaryLanguageHint,
    TargetLanguageFirstWithPrimaryFallback,
    TargetLanguageOnly
}
```

정책:

- `PrimaryLanguageFirst`: low confidence 또는 Foundation 단계에서 설명을 `primaryLang` 중심으로 제공한다.
- `BriefPrimaryLanguageHint`: 핵심 힌트만 `primaryLang`으로 짧게 보조한다.
- `TargetLanguageFirstWithPrimaryFallback`: 기본 설명은 `selectedLang`으로 하되, 이해가 어려운 경우에만 `primaryLang` 보조를 허용한다.
- `TargetLanguageOnly`: 고신뢰/고급 단계에서 `selectedLang`만 사용한다.

---

# 생성 원칙

- `LangState == null`이면 conservative beginner-safe profile
- `LangState.initial()`처럼 분석 이력이 없으면 A1 실력으로 단정하지 않고 low-confidence profile
- 일부 점수만 높고 나머지가 초기값이면 confidence를 낮게 유지
- stage는 전체 CEFR만 보지 않고 grammar/vocabulary/fluency/naturalness를 각각 계산
- challenge는 가장 약한 영역을 무시하지 않고, 현재 사용자가 이해 가능한 범위 안에서만 올린다
- `core` confidence가 낮으면 `chatPolicy`와 `correctionPolicy` 모두 conservative 하게 생성한다
- active focus는 confidence/최근성 기준 상위 1~2개만 profile에 노출한다

Correction policy 생성 방향:

- `Foundation` 또는 low confidence 상태에서는 `MinimalFix`, `KeepSimpleWords`, `FixBlockingErrorOnly`, `PrimaryLanguageFirst`를 우선한다.
- `Developing` 상태에서는 한 번에 하나의 주요 이유만 설명하고, 필요한 경우 유용한 표현 하나만 추가한다.
- `Stable` 상태에서는 문장을 조금 더 자연스럽게 만들 수 있으나, 사용자의 의도와 길이를 과하게 바꾸지 않는다.
- `Expanding` 상태에서는 collocation, 연결어, 구어체 표현을 제안할 수 있다.
- `Refined` 상태에서는 뉘앙스, register, 원어민식 선택지를 설명할 수 있다.
- active focus가 있으면 correction explanation은 상위 1개 focus를 우선 설명하고, 한 번에 여러 약점을 나열하지 않는다.
- `primaryLanguageSupport`는 `primaryLang`이 어느 언어인지 직접 판단하지 않고, prompt builder가 `primaryLang`/`selectedLang` 값을 받아 최종 instruction text로 변환한다.

입력 경계:

- `BuildLearnerAdaptationProfileUseCase`는 `LangState?`만 보고 능력/profile 정책을 계산한다.
- `primaryLang`과 `selectedLang`은 profile 자체에 저장하지 않고, prompt builder가 policy를 문장으로 바꿀 때 함께 받는다.
- 이 분리를 유지해야 같은 profile을 Chat과 Correction에서 재사용하면서도, 화면/프롬프트별 언어 표현만 다르게 만들 수 있다.

---

# 금지 방식

```text
grammarAccuracy is 0.42.
fluencyScore is 0.31.
naturalnessScore is 0.78.
```

raw 수치를 prompt에 직접 넣지 않는다.

---

# 권장 방식

```text
The learner can handle simple sentence patterns but still needs support with word order.
Ask one short follow-up question at a time.
Introduce only one new spoken expression when it clearly improves the conversation.
```

실제 prompt text는 profile enum을 해석하는 builder가 만든다.
Profile 자체는 자유 텍스트가 아니라 테스트 가능한 정책값을 제공한다.

---

# 검증 기준

- null/initial LangState에서 conservative profile이 생성된다.
- 저신뢰 고점수 혼합 상태에서 challenge가 과하게 올라가지 않는다.
- grammar/vocabulary/fluency/naturalness stage가 각각 계산된다.
- Chat/Correction policy가 같은 core를 공유한다.
- prompt builder가 raw metric을 직접 해석하지 않는다.
- Correction prompt builder가 `CorrectionAdaptationPolicy`를 사용해 난이도와 설명 방식을 결정한다.
- 보조 설명 정책이 특정 언어명에 고정되지 않고 `primaryLang` 기준으로 생성된다.
- active focus가 많아도 Correction prompt에는 상위 1개 또는 최대 2개만 반영된다.
