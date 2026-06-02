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
data class ChatAdaptationPolicy(
    val challengeLevel: ChallengeLevel,
    val responseLength: ResponseLengthPolicy,
    val questionStyle: QuestionStylePolicy,
    val nativeSupport: NativeSupportPolicy
)
```

```kotlin
data class CorrectionAdaptationPolicy(
    val challengeLevel: ChallengeLevel,
    val correctionStyle: CorrectionStylePolicy,
    val vocabularyStrategy: VocabularyStrategyPolicy,
    val grammarStrategy: GrammarStrategyPolicy,
    val spokenRegisterStrategy: SpokenRegisterStrategy,
    val nativeSupport: NativeSupportPolicy
)
```

`CorrectionAdaptationPolicy`는 Correction prompt builder가 직접 사용하는 계약이다.
Correction 담당자는 이 policy 값을 instruction text로 바꾸되, raw `LangState` metric을 다시 해석하지 않는다.

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
enum class NativeSupportPolicy {
    KoreanFirst,
    BriefKoreanHint,
    EnglishFirstWithKoreanFallback,
    EnglishOnly
}
```

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

- `Foundation` 또는 low confidence 상태에서는 `MinimalFix`, `KeepSimpleWords`, `FixBlockingErrorOnly`, `KoreanFirst`를 우선한다.
- `Developing` 상태에서는 한 번에 하나의 주요 이유만 설명하고, 필요한 경우 유용한 표현 하나만 추가한다.
- `Stable` 상태에서는 문장을 조금 더 자연스럽게 만들 수 있으나, 사용자의 의도와 길이를 과하게 바꾸지 않는다.
- `Expanding` 상태에서는 collocation, 연결어, 구어체 표현을 제안할 수 있다.
- `Refined` 상태에서는 뉘앙스, register, 원어민식 선택지를 설명할 수 있다.
- active focus가 있으면 correction explanation은 상위 1개 focus를 우선 설명하고, 한 번에 여러 약점을 나열하지 않는다.

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
- active focus가 많아도 Correction prompt에는 상위 1개 또는 최대 2개만 반영된다.
