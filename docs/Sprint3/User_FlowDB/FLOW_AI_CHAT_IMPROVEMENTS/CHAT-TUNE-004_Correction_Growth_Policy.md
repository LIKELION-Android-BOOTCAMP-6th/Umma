# [Improvement] CHAT-TUNE-004 Correction Growth Policy

## 목적

`CHAT-TUNE-003`은 Correction에서 받은 learning signal을 `LangState` 분석 정책에 연결하는 작업이다.
`CHAT-TUNE-004`는 그 반대 방향으로, 저장된 `LangState`와 `LearnerAdaptationProfile`을 사용해 Correction이 현재 사용자에게 과하지 않은 교정 강도를 적용할 수 있게 만드는 작업이다.

이번 작업은 Correction prompt 자체를 작성하는 작업이 아니다.
LearningState/Profile 쪽에서 교정용 성장 정책을 만들고, Correction 담당자가 그 정책을 prompt에서 사용할 수 있도록 계약을 제공하는 작업이다.

---

# User Story

사용자는 교정 결과가 너무 어렵거나 원래 의도와 다르게 바뀌지 않기를 기대한다.
Umma는 사용자의 현재 언어능력을 기준으로, 의미를 보존하면서 지금보다 조금 더 나은 문장으로 교정한다.
초급자는 짧고 다시 사용할 수 있는 문장을 받고, 고급자는 더 자연스러운 구어체와 뉘앙스까지 교정받을 수 있다.

---

# 완료 기준(AC)

- [ ] Correction은 raw `LangState` metric이 아니라 `CorrectionGrowthPolicy`를 통해 교정 강도를 받을 수 있다.
- [ ] 교정용 성장 단계는 Chat 대화 단계와 개념은 맞추되, Correction 전용 band로 분리된다.
- [ ] 초급 사용자는 의미 보존과 핵심 패턴 수정 중심의 교정을 받을 수 있다.
- [ ] 중급 사용자는 짧은 문장 구조와 일상 표현을 조금씩 확장한 교정을 받을 수 있다.
- [ ] 고급 사용자는 register, collocation, 뉘앙스 중심의 교정을 받을 수 있다.
- [ ] 모든 단계에서 사용자의 원래 의도와 문장 길이가 과하게 바뀌지 않도록 방어할 수 있다.
- [ ] `primaryLang`은 설명 보조 언어로 쓰이고, `selectedLang`은 교정 대상 언어로 유지된다.
- [ ] Correction prompt 실제 문구 작성은 Correction 담당자 영역으로 남긴다.
- [ ] 기존 `CorrectionLearningSignal v2` 출력 계약은 유지된다.
- [ ] 발음, pause, hesitation 같은 음성 지표는 Correction 성장 정책에 포함하지 않는다.

---

# 포함 범위

- `CorrectionGrowthBand` 정의
- `CorrectionGrowthPolicy` 정의
- `LearnerAdaptationProfile.correctionPolicy`를 `CorrectionGrowthPolicy` 중심으로 전환하는 방향 정의
- `LangState`/profile에서 교정용 band를 산출하는 정책 정의
- active focus와 confidence를 교정 강도에 반영하는 정책 정의
- `CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER` 문서 제공

---

# 제외 범위

- Correction prompt 실제 작성
- Correction AI 응답 DTO/mapper 구현
- 교정 화면 UI 변경
- Flashcard UI 변경
- Chat prompt 재튜닝
- 최종 CEFR level을 Correction AI가 직접 반환하는 구조
- 발음, pause, hesitation, speech rate 같은 음성 분석
- `CorrectionLearningSignal v2` 필드 구조의 대규모 변경

---

# 기준 문서

- [CHAT-TUNE-001 Correction Learning Signal 계약](../../../handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md)
- [CHAT-TUNE-004 Correction Growth Policy 계약](../../../handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md)
- [CHAT-TUNE-001-C LearnerAdaptationProfile](./CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md)
- [CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)
- [CHAT-TUNE-003 Correction Signal 기반 LangState 측정 고도화](./CHAT-TUNE-003_Correction_Signal_LangState_Integration.md)
- [LS-001 Language State Model Structure](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 기존 작업과의 관계

## CHAT-TUNE-001

`CHAT-TUNE-001`은 `LangState`를 AI 기능에서 재사용하기 위한 `LearnerAdaptationProfile`을 만들었다.
현재 `CorrectionAdaptationPolicy`는 4단계 `ChallengeLevel` 기반으로 설계되어 있다.

`CHAT-TUNE-004`는 이 Correction 정책을 더 세밀한 교정 성장 정책으로 확장한다.
기존 Chat 정책을 수정하는 것이 아니라, Correction 전용 정책을 분리한다.
Correction 쪽의 기존 4단계 `ChallengeLevel` 기반 정책은 최종적으로 `CorrectionGrowthPolicy` 중심 구조로 대체한다.
전환 완료 후에는 Correction prompt 경로에 4단계 정책과 6단계 성장 정책이 동시에 들어가지 않게 한다.

## CHAT-TUNE-002

`CHAT-TUNE-002`는 Chat 대화 능력을 6단계로 세분화했다.
이 단계는 자연스러운 대화 유지와 turn 보정에 맞춰져 있다.

`CHAT-TUNE-004`는 같은 내부 이해 방식을 참고하되, 교정 결과 생성에 필요한 별도 band를 사용한다.
Chat band를 그대로 Correction에 복사하지 않는다.

## CHAT-TUNE-003

`CHAT-TUNE-003`은 Correction 결과가 LearningState에 어떤 관찰 신호를 넘기는지 정의한다.
방향은 `Correction -> LearningState`다.

`CHAT-TUNE-004`는 LearningState/Profile이 Correction에 어떤 성장 정책을 넘기는지 정의한다.
방향은 `LearningState/Profile -> Correction`이다.

---

# 교육적 판단 기준

CEFR, ACTFL, IELTS Speaking, CAF 같은 외부 기준은 참고 자료로만 사용한다.
Umma는 A1 이전의 매우 낮은 초입 단계까지 다루므로 CEFR level을 그대로 사용자 능력 단계로 쓰지 않는다.

반영 원칙:

- CEFR/ACTFL처럼 능력을 하나의 총점이 아니라 기능 수행, 정확성, 어휘, 담화, 자연스러움으로 나눠 본다.
- CAF 관점처럼 복잡성, 정확성, 유창성을 분리해 해석한다.
- 교정 피드백 연구의 방향처럼 한 번에 모든 오류를 고치지 않고, 학습자가 처리할 수 있는 범위로 제한한다.
- 초급자는 “더 완벽한 문장”보다 “의미가 통하고 다시 말할 수 있는 문장”이 우선이다.
- 고급자는 오류 수정보다 collocation, register, 뉘앙스, 실제 구어체 선택이 성장 지점이다.

주의:

- “아기 수준”, “3세 수준” 같은 표현은 팀 내부 이해용 비유다.
- prompt, 저장 모델, 사용자 화면에는 나이 비유를 넣지 않는다.
- CEFR level을 Correction AI가 직접 반환하지 않는다.

---

# Correction Growth Band

```kotlin
enum class CorrectionGrowthBand {
    MeaningFirst,
    PatternFix,
    SentenceShape,
    EverydayNatural,
    ConnectedExpression,
    NuanceRefine
}
```

| band | 내부 이해용 비유 | 교정 목표 |
| --- | --- | --- |
| `MeaningFirst` | 아기 수준 | 사용자의 의도를 살리고 의미가 통하는 매우 짧은 문장으로 정리한다. |
| `PatternFix` | 3세 수준 | 가장 중요한 패턴 하나만 고쳐 사용자가 다시 말할 수 있게 한다. |
| `SentenceShape` | 6세 수준 | 짧은 문장을 완성된 형태로 만들고 작은 구조 확장 하나만 허용한다. |
| `EverydayNatural` | 10세 수준 | 일상 대화에서 더 자연스러운 표현 하나를 추가할 수 있다. |
| `ConnectedExpression` | 13세 수준 | 이유, 감정, 상황 설명이 더 자연스럽게 연결되도록 다듬는다. |
| `NuanceRefine` | 성인 수준 | register, collocation, 뉘앙스, 원어민식 선택을 세밀하게 다룬다. |

---

# CorrectionGrowthPolicy 모델 방향

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

- `band`는 큰 교정 방향을 나타낸다.
- 세부 정책은 같은 band 안에서 사용자의 약점과 confidence를 반영한다.
- Correction prompt는 이 모델을 받아 사람이 읽기 쉬운 instruction으로 변환한다.
- 이 모델은 저장 모델이 아니라 AI 기능을 위한 domain read model이다.
- `primaryLanguageSupport`는 기존 `LearnerAdaptationProfile`의 `PrimaryLanguageSupportPolicy`를 재사용한다.
- 기준언어 보조 정도는 Chat과 Correction에서 같은 의미로 유지해야 하므로 Correction 전용 enum을 새로 만들지 않는다.

---

# 세부 정책

## CorrectionScopePolicy

```kotlin
enum class CorrectionScopePolicy {
    PreserveIntentOnly,
    FixOneCoreIssue,
    FixMainIssueWithTinyExpansion,
    NaturalRewriteWithinSameMeaning,
    NuanceRewriteWithinSameMeaning
}
```

의미:

- `PreserveIntentOnly`: 뜻이 통하도록 최소한만 정리한다.
- `FixOneCoreIssue`: 가장 중요한 오류 하나만 고친다.
- `FixMainIssueWithTinyExpansion`: 핵심 오류를 고치고 아주 작은 확장 하나만 허용한다.
- `NaturalRewriteWithinSameMeaning`: 의미를 유지하면서 더 자연스러운 문장으로 바꾼다.
- `NuanceRewriteWithinSameMeaning`: 의미를 유지하면서 말투와 뉘앙스까지 다듬는다.

## GrammarCorrectionPolicy

```kotlin
enum class GrammarCorrectionPolicy {
    FixBlockingErrorOnly,
    FixOneMainPattern,
    StabilizeBasicSentence,
    ImproveConnectedStructure,
    RefineAdvancedStructure
}
```

의미:

- `FixBlockingErrorOnly`: 의미 전달을 막는 오류만 고친다.
- `FixOneMainPattern`: 핵심 문법 패턴 하나만 고친다.
- `StabilizeBasicSentence`: 기본 문장 구조를 안정화한다.
- `ImproveConnectedStructure`: 이유, 조건, 상황 설명 연결을 다듬는다.
- `RefineAdvancedStructure`: 고급 구조와 뉘앙스가 연결된 문법을 다룬다.

## VocabularyGrowthPolicy

```kotlin
enum class VocabularyGrowthPolicy {
    KeepUserWords,
    AddOneUsefulWord,
    AddOneEverydayExpression,
    ImproveCollocation,
    RefineNativeChoice
}
```

의미:

- `KeepUserWords`: 사용자가 쓴 단어를 최대한 유지한다.
- `AddOneUsefulWord`: 유용한 단어 하나만 추가한다.
- `AddOneEverydayExpression`: 일상 표현 하나를 추가한다.
- `ImproveCollocation`: 더 자연스러운 단어 조합으로 다듬는다.
- `RefineNativeChoice`: 원어민이 더 자주 쓰는 선택으로 다듬는다.

## SentenceExpansionPolicy

```kotlin
enum class SentenceExpansionPolicy {
    NoExpansion,
    TinyPhraseOnly,
    OneShortSentence,
    AddSimpleReasonOrDetail,
    FlexibleNaturalDetail
}
```

의미:

- `NoExpansion`: 문장을 늘리지 않는다.
- `TinyPhraseOnly`: 아주 짧은 구만 추가할 수 있다.
- `OneShortSentence`: 짧은 문장 하나 수준으로 정리한다.
- `AddSimpleReasonOrDetail`: 간단한 이유나 상황 설명 하나를 추가할 수 있다.
- `FlexibleNaturalDetail`: 자연스러운 세부 설명을 제한적으로 허용한다.

## RegisterCorrectionPolicy

```kotlin
enum class RegisterCorrectionPolicy {
    Simple,
    EverydaySpoken,
    CasualNatural,
    PoliteWhenUseful,
    NuanceAware
}
```

의미:

- `Simple`: 쉽고 단순한 말투를 우선한다.
- `EverydaySpoken`: 일상 대화에서 자연스러운 말투를 우선한다.
- `CasualNatural`: 캐주얼하고 자연스러운 표현을 사용할 수 있다.
- `PoliteWhenUseful`: 필요한 경우 공손한 표현 차이를 다룬다.
- `NuanceAware`: 상황별 말투와 뉘앙스를 세밀하게 다룬다.

## CorrectionExplanationPolicy

```kotlin
enum class CorrectionExplanationPolicy {
    PrimaryLanguageShort,
    PrimaryLanguageOneReason,
    BilingualBrief,
    TargetLanguageWithPrimaryFallback,
    TargetLanguageNuance
}
```

의미:

- `PrimaryLanguageShort`: `primaryLang`으로 아주 짧게 설명한다.
- `PrimaryLanguageOneReason`: `primaryLang`으로 이유 하나만 설명한다.
- `BilingualBrief`: 교정 표현은 `selectedLang`, 설명은 짧게 `primaryLang`으로 보조한다.
- `TargetLanguageWithPrimaryFallback`: 기본은 `selectedLang`이지만 어려운 뉘앙스만 `primaryLang`으로 보조한다.
- `TargetLanguageNuance`: `selectedLang` 중심으로 뉘앙스까지 설명한다.

## NewExpressionLimitPolicy

```kotlin
enum class NewExpressionLimitPolicy {
    None,
    OneTinyWord,
    OneUsefulPhrase,
    OneNaturalExpression,
    OneNuanceChoice
}
```

의미:

- `None`: 새 표현을 추가하지 않는다.
- `OneTinyWord`: 아주 쉬운 단어 하나만 추가한다.
- `OneUsefulPhrase`: 유용한 짧은 표현 하나만 추가한다.
- `OneNaturalExpression`: 일상적으로 자주 쓰는 표현 하나를 추가한다.
- `OneNuanceChoice`: 뉘앙스가 다른 표현 선택지 하나를 다룬다.

## MeaningPreservationPolicy

```kotlin
enum class MeaningPreservationPolicy {
    Strict,
    StrictWithTinyClarification,
    SameMeaningNaturalized,
    SameIntentWithNuance
}
```

의미:

- `Strict`: 원래 뜻을 거의 그대로 유지한다.
- `StrictWithTinyClarification`: 원래 뜻을 유지하되 아주 작은 명확화만 허용한다.
- `SameMeaningNaturalized`: 같은 뜻을 더 자연스럽게 표현한다.
- `SameIntentWithNuance`: 같은 의도를 유지하면서 뉘앙스 차이를 다룬다.

---

# Band 산출 기준

`CorrectionGrowthBand`는 Chat band와 별도로 계산한다.
Chat은 대화 지속 가능성을 우선하지만, Correction은 교정 결과가 사용자의 처리 가능 범위 안에 있는지를 우선한다.

입력 후보:

- `LearnerAbilityProfile.grammarStage`
- `LearnerAbilityProfile.vocabularyStage`
- `LearnerAbilityProfile.fluencyStage`
- `LearnerAbilityProfile.naturalnessStage`
- `LearnerAbilityProfile.focus`
- `ProfileConfidence`
- `LangState.internal.sentenceComplexity`
- `LangState.analysisMeta.activeFocus`
- `LangState.analysisMeta.metricEvidence`

`meaningful LangState` 기준은 `CHAT-TUNE-002`의 첫 selectedLang fallback 기준을 재사용한다.
즉 selectedLang의 `LangState`가 없거나, `lastAnalyzedAt`이 없거나, `analysisMeta.metricEvidence`가 비어 있거나, 주요 internal metric이 초기값에 가까우면 교정 정책도 low-confidence 상태로 본다.

기본 산출 방향:

| band | 기준 |
| --- | --- |
| `MeaningFirst` | confidence가 낮고 grammar/vocabulary/fluency가 모두 매우 낮거나 의미 전달 오류 focus가 강함 |
| `PatternFix` | 단어/구 표현은 있으나 `grammarStage` 또는 `sentenceComplexity`가 낮음 |
| `SentenceShape` | 짧은 문장은 가능하지만 `grammarStage`, `sentenceComplexity`, 반복 문법 focus 중 하나가 아직 불안정함 |
| `EverydayNatural` | grammar/vocabulary가 안정적이고 일상 표현 확장이 가능함 |
| `ConnectedExpression` | structure/naturalness가 확장 단계에 있고 이유/상황 설명을 다룰 수 있음 |
| `NuanceRefine` | grammar/fluency가 안정적이고 naturalness/vocabulary가 높은 신뢰도로 refined에 가까움 |

방어 원칙:

- 하나의 높은 지표만으로 band를 올리지 않는다.
- confidence가 낮으면 교정 강도는 한 단계 낮춘다.
- active focus가 의미 전달 오류나 핵심 문법에 쏠리면 자연스러움 개선보다 패턴 안정화를 우선한다.
- `external` metric은 내부 교정 band 산출에 직접 사용하지 않는다.
- `expressionRange`는 표현 폭 판단에 필요한 경우에만 예외적으로 vocabulary 보조 신호로 사용할 수 있다.

산출 우선순위:

1. meaningful `LangState`가 없거나 `ProfileConfidence.Low`면 낮은 band로 보수 조정한다.
2. 의미 전달을 막는 active focus가 있으면 `MeaningFirst` 또는 `PatternFix`를 우선한다.
3. `grammarStage` 또는 `sentenceComplexity`가 낮으면 자연스러움 개선보다 `SentenceShape` 이하를 우선한다.
4. grammar, vocabulary, fluency가 안정될 때만 `EverydayNatural` 이상을 허용한다.
5. naturalness와 vocabulary가 높고 confidence가 높을 때만 `NuanceRefine`을 허용한다.
6. 이 우선순위는 내부 band 산출에만 사용하고, Correction prompt에는 최종 `CorrectionGrowthPolicy`만 전달한다.

---

# Band별 기본 정책

| band | scope | grammar | vocabulary | expansion | register | explanation | new expression | meaning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `MeaningFirst` | `PreserveIntentOnly` | `FixBlockingErrorOnly` | `KeepUserWords` | `NoExpansion` | `Simple` | `PrimaryLanguageShort` | `None` | `Strict` |
| `PatternFix` | `FixOneCoreIssue` | `FixOneMainPattern` | `AddOneUsefulWord` | `TinyPhraseOnly` | `Simple` | `PrimaryLanguageOneReason` | `OneTinyWord` | `StrictWithTinyClarification` |
| `SentenceShape` | `FixMainIssueWithTinyExpansion` | `StabilizeBasicSentence` | `AddOneUsefulWord` | `OneShortSentence` | `EverydaySpoken` | `BilingualBrief` | `OneUsefulPhrase` | `StrictWithTinyClarification` |
| `EverydayNatural` | `NaturalRewriteWithinSameMeaning` | `StabilizeBasicSentence` | `AddOneEverydayExpression` | `AddSimpleReasonOrDetail` | `EverydaySpoken` | `BilingualBrief` | `OneNaturalExpression` | `SameMeaningNaturalized` |
| `ConnectedExpression` | `NaturalRewriteWithinSameMeaning` | `ImproveConnectedStructure` | `ImproveCollocation` | `AddSimpleReasonOrDetail` | `CasualNatural` | `TargetLanguageWithPrimaryFallback` | `OneNaturalExpression` | `SameMeaningNaturalized` |
| `NuanceRefine` | `NuanceRewriteWithinSameMeaning` | `RefineAdvancedStructure` | `RefineNativeChoice` | `FlexibleNaturalDetail` | `NuanceAware` | `TargetLanguageNuance` | `OneNuanceChoice` | `SameIntentWithNuance` |

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| LearningState domain | `LangState`와 evidence를 기반으로 교정 성장 정책을 계산한다. |
| BuildLearnerAdaptationProfileUseCase | `LearnerAdaptationProfile.correctionPolicy`를 생성한다. |
| Correction 담당 영역 | 전달받은 policy를 prompt 문장으로 바꾸고 교정 결과를 생성한다. |
| Correction mapper | 교정 후 learning signal을 기존 v2 계약으로 정규화한다. |
| Repository/data source | 계산된 상태를 저장하고 sync한다. 교육적 의미를 해석하지 않는다. |
| Chat | Correction growth policy를 직접 사용하지 않는다. |

---

# 작업 순서

1. `CorrectionGrowthBand`와 세부 policy enum을 domain 모델로 정의한다.
2. 기존 `CorrectionAdaptationPolicy`의 4단계 Correction 정책을 `CorrectionGrowthPolicy` 중심 구조로 대체한다.
3. `BuildLearnerAdaptationProfileUseCase`에서 교정용 band와 세부 정책을 산출한다.
4. low confidence, 초기 LangState, focus 편중, 고급 naturalness 케이스를 테스트 fixture로 만든다.
5. `CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER`와 코드 계약이 같은지 검증한다.
6. Correction 담당자에게 prompt 실제 구현은 handover 기준으로 넘긴다.

---

# 검증 기준

- `git diff --check`
- 가능하면 `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`
- 초기/저신뢰 사용자는 낮은 교정 강도 정책을 받는다.
- 고급 사용자는 자연스러움/뉘앙스 교정 정책을 받을 수 있다.
- active focus가 있으면 교정 정책이 해당 약점을 우선 반영한다.
- 하나의 고점 metric만으로 고급 교정 band로 올라가지 않는다.
- `CorrectionGrowthPolicy`에는 raw numeric metric이 들어가지 않는다.
- Correction prompt 경로에 기존 4단계 `ChallengeLevel` 정책과 새 6단계 `CorrectionGrowthBand` 정책이 동시에 들어가지 않는다.
- Correction prompt 실제 문구는 이번 작업에서 구현하지 않는다.
- 기존 `CorrectionLearningSignal v2` 출력 계약은 유지된다.
- 발음/음성 유창성 지표는 Correction 성장 정책에 포함되지 않는다.
