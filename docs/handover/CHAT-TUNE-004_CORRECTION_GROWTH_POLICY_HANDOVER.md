# [Handover] CHAT-TUNE-004 Correction Growth Policy 계약

## 목적

Correction 담당자가 LearningState 쪽의 교정 성장 정책을 기다리지 않고도 prompt/schema 작업 방향을 잡을 수 있도록, Correction이 입력으로 받아야 할 성장 정책 계약을 정의한다.

이 문서는 `CHAT-TUNE-001 Correction Learning Signal 계약`을 대체하지 않는다.
`CHAT-TUNE-001` handover가 “교정 후 LearningState에 넘길 관찰 신호”를 정의했다면, 이 문서는 “교정 전 Correction prompt가 참고할 교정 강도 정책”을 정의한다.

Correction은 사용자의 최종 언어능력 점수나 전체 레벨을 계산하지 않는다.
LearningState가 `LangState`를 해석해 `CorrectionGrowthPolicy`를 만들고, Correction은 그 정책을 받아 현재 사용자에게 과하지 않은 교정 결과를 생성한다.
전달 경로는 `LangState` → `LearnerAdaptationProfile.correctionPolicy` → Correction prompt 입력이다.
Correction 담당자는 raw `LangState`를 직접 받거나 해석하지 않고, 이미 해석된 `correctionPolicy`만 사용한다.

---

# 핵심 원칙

Correction은 “가장 완벽한 문장”이 아니라 “현재 사용자가 이해하고 다시 사용할 수 있는 다음 단계 문장”을 만들어야 한다.

- 낮은 단계 사용자는 의미 전달과 핵심 패턴을 먼저 안정화한다.
- 중간 단계 사용자는 짧은 문장 구조와 일상 표현을 조금씩 확장한다.
- 높은 단계 사용자는 자연스러운 구어체, register, 뉘앙스, collocation을 다룬다.
- 모든 단계에서 사용자의 원래 의도와 길이를 과하게 바꾸지 않는다.
- “10% 성장”은 숫자 10%가 아니라, 현재 능력보다 조금 높지만 따라갈 수 있는 작은 성장 폭을 의미한다.

---

# Correction이 입력으로 받을 것

Correction prompt는 raw `LangState` metric을 직접 해석하지 않는다.
Correction은 LearningState/Profile 쪽에서 이미 해석된 정책만 받는다.

권장 입력:

```kotlin
data class CorrectionGrowthPolicy(
    val band: CorrectionGrowthBand,
    val scope: CorrectionScopePolicy,
    val grammar: GrammarCorrectionPolicy,
    val vocabulary: VocabularyGrowthPolicy,
    val sentenceExpansion: SentenceExpansionPolicy,
    val register: RegisterCorrectionPolicy,
    val explanation: CorrectionExplanationPolicy,
    val newExpressionLimit: NewExpressionLimitPolicy,
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy,
    val meaningPreservation: MeaningPreservationPolicy
)
```

정책:

- `band`는 교정 강도의 큰 방향이다.
- 나머지 세부 정책은 같은 band 안에서도 사용자의 약점과 강점을 반영해 교정 방식을 조절한다.
- `primaryLanguageSupport`는 기존 `LearnerAdaptationProfile`의 `PrimaryLanguageSupportPolicy`를 재사용한다.
- 기준언어 보조 정도는 Chat과 Correction에서 같은 의미로 유지해야 하므로 Correction 전용 중복 enum을 만들지 않는다.
- Correction prompt는 이 값을 사용자에게 그대로 노출하지 않고, 내부 지시로만 사용한다.
- Correction AI는 이 정책을 근거로 교정 문장과 설명을 만들되, 최종 점수/레벨은 반환하지 않는다.

`PrimaryLanguageSupportPolicy` 값:

| 값 | 의미 |
| --- | --- |
| `PrimaryLanguageFirst` | 설명을 `primaryLang` 중심으로 제공한다. |
| `BriefPrimaryLanguageHint` | 핵심 힌트만 `primaryLang`으로 짧게 보조한다. |
| `TargetLanguageFirstWithPrimaryFallback` | 기본은 `selectedLang`이지만 이해가 어려운 부분은 `primaryLang`으로 보조한다. |
| `TargetLanguageOnly` | `selectedLang` 중심으로 설명한다. |

---

# Correction이 입력으로 받지 않을 것

- raw `grammarAccuracy`
- raw `vocabularyAppropriateness`
- raw `fluencyScore`
- raw `spokenNaturalness`
- raw `analysisMeta.metricEvidence`
- 최종 CEFR level
- 사용자 전체 능력 평가 문장
- `difficultyDelta`
- 발음, pause, hesitation 같은 음성 지표

제외 이유:

- Correction이 raw metric을 직접 해석하면 Chat과 Correction의 난이도 기준이 갈라질 수 있다.
- 난이도 판단은 LearningState/Profile 정책에서 일관되게 계산해야 한다.
- 발음과 음성 유창성은 Correction 텍스트 교정 흐름이 아니라 Chat/STT/Audio 쪽 입력이다.

---

# Correction Growth Band

나이 비유는 팀 내부 이해를 위한 설명이다.
실제 prompt, 저장 모델, 사용자 화면에는 “아기 수준”, “3세 수준” 같은 표현을 넣지 않는다.

| band | 내부 이해용 비유 | 사용자 상태 | 교정 방향 |
| --- | --- | --- | --- |
| `MeaningFirst` | 아기 수준 | 단어 조각, 기준언어 혼합, 의미 전달이 불완전함 | 의미 전달을 먼저 살리고, 매우 짧은 학습언어 문장으로 정리한다. |
| `PatternFix` | 3세 수준 | 짧은 구/고정 표현은 가능하지만 문장 뼈대가 약함 | 한 번에 하나의 핵심 패턴만 고친다. |
| `SentenceShape` | 6세 수준 | 짧은 문장은 가능하지만 어순/시제/조사/기본 문법이 흔들림 | 문장을 완성된 형태로 다듬고 작은 구조 확장 하나만 허용한다. |
| `EverydayNatural` | 10세 수준 | 기본 일상 대화가 가능함 | 더 자연스러운 일상 표현이나 collocation 하나를 추가할 수 있다. |
| `ConnectedExpression` | 13세 수준 | 이유, 감정, 상황 설명이 가능함 | 연결 표현, 구어체 표현, 문장 연결을 다듬는다. |
| `NuanceRefine` | 성인 수준 | 의미 전달은 안정적이고 뉘앙스/말투가 성장 지점임 | register, 뉘앙스, 원어민식 선택을 세밀하게 다룬다. |

정책:

- `MeaningFirst`와 `PatternFix`는 설명을 `primaryLang`으로 더 적극적으로 보조할 수 있다.
- `SentenceShape`부터는 교정 문장 자체는 `selectedLang` 중심으로 유지한다.
- `EverydayNatural` 이상에서는 학습언어 대화 흐름을 해치지 않는 선에서 자연스러운 표현을 늘린다.
- `NuanceRefine`에서도 사용자의 의도보다 멋진 문장을 새로 창작하지 않는다.

---

# 세부 정책

## CorrectionScopePolicy

교정이 어느 범위까지 사용자의 문장을 바꿀 수 있는지 정한다.

```kotlin
enum class CorrectionScopePolicy {
    PreserveIntentOnly,
    FixOneCoreIssue,
    FixMainIssueWithTinyExpansion,
    NaturalRewriteWithinSameMeaning,
    NuanceRewriteWithinSameMeaning
}
```

정책:

- `PreserveIntentOnly`: 뜻이 통하도록 최소한만 정리한다.
- `FixOneCoreIssue`: 가장 중요한 오류 하나만 고친다.
- `FixMainIssueWithTinyExpansion`: 핵심 오류를 고치고 아주 작은 확장 하나만 허용한다.
- `NaturalRewriteWithinSameMeaning`: 의미를 유지하면서 더 자연스럽게 바꾼다.
- `NuanceRewriteWithinSameMeaning`: 의미를 유지하면서 말투와 뉘앙스를 세밀하게 다듬는다.

## GrammarCorrectionPolicy

문법을 어느 정도까지 고칠지 정한다.

```kotlin
enum class GrammarCorrectionPolicy {
    FixBlockingErrorOnly,
    FixOneMainPattern,
    StabilizeBasicSentence,
    ImproveConnectedStructure,
    RefineAdvancedStructure
}
```

정책:

- 낮은 단계에서는 의미를 막는 오류나 핵심 패턴 하나만 고친다.
- 높은 단계로 갈수록 연결 구조, 절, 시제 일관성, 고급 구조까지 다룰 수 있다.
- 여러 문법 항목을 한 번에 나열하지 않는다.

## VocabularyGrowthPolicy

어휘와 표현을 얼마나 확장할지 정한다.

```kotlin
enum class VocabularyGrowthPolicy {
    KeepUserWords,
    AddOneUsefulWord,
    AddOneEverydayExpression,
    ImproveCollocation,
    RefineNativeChoice
}
```

정책:

- 초급 단계에서는 사용자가 이미 쓴 단어를 최대한 살린다.
- 새 표현은 한 번에 하나만 추가한다.
- 고급 단계에서는 collocation과 원어민식 선택을 다룰 수 있다.

## SentenceExpansionPolicy

문장 길이와 구조 확장을 얼마나 허용할지 정한다.

```kotlin
enum class SentenceExpansionPolicy {
    NoExpansion,
    TinyPhraseOnly,
    OneShortSentence,
    AddSimpleReasonOrDetail,
    FlexibleNaturalDetail
}
```

정책:

- 초급 단계에서는 문장 길이를 거의 늘리지 않는다.
- 중간 단계에서는 짧은 이유나 상황 설명 하나만 추가할 수 있다.
- 고급 단계에서도 sourceText보다 과하게 길어지면 의미 변경 위험으로 본다.

## RegisterCorrectionPolicy

교정 문장의 말투를 정한다.

```kotlin
enum class RegisterCorrectionPolicy {
    Simple,
    EverydaySpoken,
    CasualNatural,
    PoliteWhenUseful,
    NuanceAware
}
```

정책:

- 기본값은 실제 대화에서 쓰기 쉬운 `EverydaySpoken`이다.
- 초급 단계에서는 쉬운 말투를 우선한다.
- 고급 단계에서는 casual, polite, formal 차이를 설명할 수 있다.

## CorrectionExplanationPolicy

교정 설명의 언어와 깊이를 정한다.

```kotlin
enum class CorrectionExplanationPolicy {
    PrimaryLanguageShort,
    PrimaryLanguageOneReason,
    BilingualBrief,
    TargetLanguageWithPrimaryFallback,
    TargetLanguageNuance
}
```

정책:

- 낮은 단계에서는 설명을 `primaryLang` 중심으로 짧게 제공한다.
- 중간 단계에서는 `selectedLang` 표현과 `primaryLang` 설명을 함께 쓸 수 있다.
- 높은 단계에서는 `selectedLang` 중심 설명을 하되, 뉘앙스가 어려우면 `primaryLang` 보조를 허용한다.

## NewExpressionLimitPolicy

새 표현을 얼마나 넣을 수 있는지 정한다.

```kotlin
enum class NewExpressionLimitPolicy {
    None,
    OneTinyWord,
    OneUsefulPhrase,
    OneNaturalExpression,
    OneNuanceChoice
}
```

정책:

- 새 표현을 여러 개 넣으면 교정이 학습 가능한 범위를 넘어갈 수 있다.
- 기본 원칙은 “한 번에 하나”다.
- 낮은 단계에서는 새 표현보다 기존 의도 보존을 우선한다.

## MeaningPreservationPolicy

의미 보존 강도를 정한다.

```kotlin
enum class MeaningPreservationPolicy {
    Strict,
    StrictWithTinyClarification,
    SameMeaningNaturalized,
    SameIntentWithNuance
}
```

정책:

- 모든 band에서 의미 보존은 기본 방어 조건이다.
- 낮은 단계에서는 사용자가 말한 뜻을 임의로 확장하지 않는다.
- 높은 단계에서도 더 멋진 문장을 만들기 위해 사용자 의도를 바꾸지 않는다.

---

# Band별 기본 매핑

| band | scope | grammar | vocabulary | expansion | register | explanation | new expression | meaning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `MeaningFirst` | `PreserveIntentOnly` | `FixBlockingErrorOnly` | `KeepUserWords` | `NoExpansion` | `Simple` | `PrimaryLanguageShort` | `None` | `Strict` |
| `PatternFix` | `FixOneCoreIssue` | `FixOneMainPattern` | `AddOneUsefulWord` | `TinyPhraseOnly` | `Simple` | `PrimaryLanguageOneReason` | `OneTinyWord` | `StrictWithTinyClarification` |
| `SentenceShape` | `FixMainIssueWithTinyExpansion` | `StabilizeBasicSentence` | `AddOneUsefulWord` | `OneShortSentence` | `EverydaySpoken` | `BilingualBrief` | `OneUsefulPhrase` | `StrictWithTinyClarification` |
| `EverydayNatural` | `NaturalRewriteWithinSameMeaning` | `StabilizeBasicSentence` | `AddOneEverydayExpression` | `AddSimpleReasonOrDetail` | `EverydaySpoken` | `BilingualBrief` | `OneNaturalExpression` | `SameMeaningNaturalized` |
| `ConnectedExpression` | `NaturalRewriteWithinSameMeaning` | `ImproveConnectedStructure` | `ImproveCollocation` | `AddSimpleReasonOrDetail` | `CasualNatural` | `TargetLanguageWithPrimaryFallback` | `OneNaturalExpression` | `SameMeaningNaturalized` |
| `NuanceRefine` | `NuanceRewriteWithinSameMeaning` | `RefineAdvancedStructure` | `RefineNativeChoice` | `FlexibleNaturalDetail` | `NuanceAware` | `TargetLanguageNuance` | `OneNuanceChoice` | `SameIntentWithNuance` |

주의:

- 이 표는 기본값이다.
- active focus나 confidence가 낮으면 더 보수적인 세부 정책으로 낮출 수 있다.
- Correction 담당자는 band 이름보다 세부 정책 값을 우선한다.

---

# Correction prompt 적용 책임

Correction 담당자는 이 handover를 기준으로 prompt를 작성하되, 아래 책임 경계를 유지한다.

Correction 담당 영역:

- 전달받은 `CorrectionGrowthPolicy`를 prompt instruction으로 변환한다.
- 교정 문장, 설명, flashcard용 문장을 생성한다.
- 교정 결과가 policy보다 과하게 확장되지 않도록 prompt와 mapper를 조정한다.

LearningState 담당 영역:

- `LangState`와 `LearnerAdaptationProfile`을 해석해 `CorrectionGrowthPolicy`를 만든다.
- learning signal을 받아 장기 metric, evidence, active focus를 갱신한다.
- source/corrected 비교로 과도한 교정 여부를 방어한다.

공통 주의:

- Correction은 raw metric을 다시 계산하지 않는다.
- Correction은 사용자의 최종 band를 저장하지 않는다.
- Correction은 교정 후 `CorrectionLearningSignal`을 통해 관찰 신호만 넘긴다.
- 기존 4단계 `ChallengeLevel` 기반 Correction 정책은 최종적으로 `CorrectionGrowthPolicy`로 대체한다.
- 전환 후 Correction prompt 경로에 4단계 정책과 6단계 성장 정책이 동시에 들어가면 안 된다.

---

# 예시

## MeaningFirst 예시

입력:

```text
sourceText: "I hungry apple"
selectedLang: EN
primaryLang: KO
policy.band: MeaningFirst
```

기대 방향:

- 사용자가 “배고프고 사과를 먹고 싶다”는 뜻을 먼저 보존한다.
- 교정 문장은 짧고 다시 말하기 쉬워야 한다.
- 설명은 한국어로 짧게 가능하다.
- 새 표현을 여러 개 추가하지 않는다.

예:

```text
correctedText: "I'm hungry. I want an apple."
explanation: "배고프고 사과를 원한다는 뜻으로 짧게 나눴어요."
```

## EverydayNatural 예시

입력:

```text
sourceText: "I made language education app."
selectedLang: EN
primaryLang: KO
policy.band: EverydayNatural
```

기대 방향:

- 기본 문법을 고치고 실제로 더 자주 쓰는 표현 하나를 넣을 수 있다.
- 너무 긴 고급 문장으로 바꾸지 않는다.

예:

```text
correctedText: "I built a language learning app."
explanation: "`built`와 `language learning app`이 일상 대화에서 더 자연스러워요."
```

---

# 검증 기준

- Correction prompt는 `CorrectionGrowthPolicy`를 입력으로 받을 수 있다.
- Correction prompt는 raw `LangState` metric을 직접 해석하지 않는다.
- 초급 band에서는 새 표현과 문장 확장이 제한된다.
- 고급 band에서는 뉘앙스와 register 개선이 가능하다.
- 모든 band에서 의미 보존이 우선된다.
- Correction 결과는 교정 후 `CorrectionLearningSignal` 계약으로 관찰 신호를 넘길 수 있다.
- 발음, pause, hesitation 같은 음성 지표는 Correction 계약에 포함하지 않는다.
