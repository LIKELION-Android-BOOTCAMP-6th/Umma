# [Improvement] CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략

## 목적

`CHAT-TUNE-001`은 `LangState`를 `LearnerAdaptationProfile`로 변환해 Chat prompt에 연결하는 작업이다.
`CHAT-TUNE-002`는 그 다음 단계로, Chat이 사용자의 대화 능력을 더 세밀하게 해석해 자연스러운 일상 대화 안에서 학습을 유도하도록 prompt 전략을 재설계한다.

이번 문서는 이미 구현된 `CHAT-TUNE-001-A~D`의 완료 범위를 바꾸지 않는다.
기존 4단계 정책을 덮어쓰지 않고, 후속 작업에서 검토할 새 Chat 전략으로 분리한다.

---

# User Story

사용자는 외국어를 거의 못하거나 아기처럼 단어 조각만 말해도 AI와 대화를 이어갈 수 있다.
Umma는 사용자의 의도를 먼저 이해하려고 하고, 사용자가 하려던 말을 자연스러운 학습 언어 표현으로 대화 안에서 보여준다.
사용자는 반복 따라 말하기 훈련을 강요받지 않고, 앱을 계속 사용하면서 자연스럽게 표현, 문장 구조, 어휘, 구어체 감각을 조금씩 확장한다.

---

# 완료 기준(AC)

- [x] Chat은 사용자의 대화 가능 단계를 6단계로 해석하고, 단계에 맞게 응답 길이, 기준언어 보조, 표현 확장 폭을 조절한다.
- [x] 사용자가 단어 조각이나 기준언어가 섞인 말로 시작해도 AI는 의도를 먼저 이해하려고 하고 대화를 끊지 않는다.
- [x] 사용자가 잘 말하는 경우에는 초급자처럼 다루지 않고 자연스러운 원어민식 일상 대화로 이어 간다.
- [x] `selectedLang`의 의미 있는 LangState가 없어도 사용자를 실제 최저 실력으로 단정하지 않고, 현재 발화에 따라 첫 대화 fallback을 적용한다.
- [x] `primaryLang`은 사용자가 이해하지 못하거나 직접 요청한 경우에만 짧게 사용하고, `selectedLang` 대화를 대체하지 않는다.
- [x] prompt에는 raw score, 내부 enum 이름, 나이 비유, 이모지 지시가 직접 들어가지 않는다.
- [x] 세션 시작 prompt는 기본 대화 방향만 담고, turn override는 이번 응답에서 달라진 보정값만 짧게 전달한다.
- [x] turn override는 세션 안에서만 사용되고 `LangState`, SessionMemory, Correction 원문, usage 원문에 저장되지 않는다.
- [x] 기존 final transcript, SessionMemory, usage tracking, 마이크 상태 흐름은 유지된다.

---

# 기존 전략과의 관계

`CHAT-TUNE-001-C/D`에는 `LearnerAdaptationProfile` 기반 prompt 연결과 4단계 `challengeLevel` 정책이 포함되어 있다.
이 전략은 이미 구현된 범위이므로 `CHAT-TUNE-002`에서 기존 문서를 다시 덮어쓰지 않는다.

후속 전략:

- Chat 대화 능력은 4단계 challenge만으로 판단하지 않는다.
- Correction의 교정 강도는 기존 4단계 정책을 유지할 수 있다.
- Chat은 기존 `challengeLevel`을 보조 호환 값으로 남기지 않고, 더 세밀한 대화 능력 단계와 세부 행동 정책으로 대체한다.
- 세부 정책은 코드와 테스트에서 충분히 세밀하게 관리하고, 최종 prompt에는 현재 턴에 필요한 짧은 행동 지시만 압축해서 넣는다.
- 새 전략이 확정되면 코드 변경은 `CHAT-TUNE-002` 이슈/문서 기준으로 진행한다.

---

# 대화 능력 단계 전략

나이 비유는 팀 내부가 직관적으로 이해하기 위한 표현이다.
실제 prompt에는 “아기 수준”, “13세 수준” 같은 단어를 넣지 않는다.

| 내부 단계 | 내부 이해용 비유 | 사용자 상태 | Chat 응답 방향 |
| --- | --- | --- | --- |
| `IntentOnly` | 아기 수준 | 단어 조각, 기준언어 섞임, 매우 불완전한 발화 | 의도 추론을 우선하고, 필요하면 `primaryLang`으로 의미를 먼저 잡은 뒤 짧은 `selectedLang` 표현을 자연스럽게 붙인다. |
| `PhraseEmerging` | 3세 수준 | 짧은 구, 고정 표현, 단어 나열 | 매우 쉬운 표현을 대화 안에서 보여주고 사용자가 짧게 답할 수 있는 여지를 남긴다. |
| `SimpleSentence` | 6세 수준 | 짧은 문장 가능, 어순/시제/기본 문법 흔들림 | 틀린 문장을 따로 수업처럼 설명하지 않고 응답 안에서 한 번 자연스럽게 재표현한다. |
| `BasicConversation` | 10세 수준 | 짧은 일상 왕복 대화 가능 | 실제 대화처럼 반응하고 이유, 선호, 경험을 묻는다. |
| `ConnectedExpression` | 13세 수준 | 이유, 감정, 상황 설명 가능 | 연결어, collocation, 구어체 표현을 조금씩 노출한다. |
| `NuanceControl` | 성인 수준 | 의미 전달은 안정적이고 자연스러움/뉘앙스가 성장 지점 | 일반 대화처럼 진행하며 더 원어민다운 선택지를 자연스럽게 섞는다. |

---

# 첫 selectedLang 대화 fallback

첫 selectedLang 대화는 사용자의 실제 외국어 능력이 낮다는 뜻이 아니다.
해당 언어의 LangState 분석 근거가 아직 없으므로, Chat은 사용자가 fluent할 가능성도 열어 두면서 대화 진입 부담을 낮추는 fallback 정책을 적용한다.

`meaningful LangState`가 없는 상태:

- selectedLang의 `LangState`가 없다.
- `LangState.lastAnalyzedAt`이 없다.
- `LangState.analysisMeta.metricEvidence`가 비어 있다.
- 주요 `LangState.internal` metric이 모두 초기값에 가깝다.

첫 대화 fallback 정책:

- AI는 사용자의 첫 발화 수준을 보고 응답 부담을 조절한다.
- 단어 조각이나 기준언어 혼합 발화면 `IntentOnly`에 가까운 행동을 적용한다.
- 단어 조각이나 기준언어 혼합 발화면 실제 음성 속도와 문장 밀도를 함께 낮춘다.
- 속도와 문장 밀도를 낮추더라도 “쉽게 해 보자” 같은 학습 진행 멘트가 아니라 자연스러운 일상 반응을 우선한다.
- 사용자가 fluent하게 말하면 초저숙련으로 고정하지 않고 더 자연스러운 대화로 따라간다.
- 첫 발화 보정은 장기 `LangState` 갱신이 아니라 현재 세션 안에서만 쓰는 임시 turn override로 처리한다.
- prompt에는 `firstSession`, `initial`, `IntentOnly` 같은 내부 상태 이름을 직접 넣지 않는다.
- 첫 대화 fallback은 `CHAT-EMOJI-001`의 자막 이모지 힌트와 연결될 수 있지만, prompt가 이모지를 직접 생성하게 만들지는 않는다.

---

# LangState 조합 기준

`CHAT-TUNE-002`의 conversation band는 raw metric을 prompt에 직접 넣기 위한 값이 아니다.
`LearnerAdaptationProfile` 또는 후속 Chat 전용 profile builder가 `LangState`를 읽어 내부 band를 계산하고, prompt에는 행동 정책만 전달한다.

사용 입력:

- `LangState.internal.grammarAccuracy`: 문장이 기본 구조를 갖추는지 판단한다.
- `LangState.internal.vocabularyAppropriateness`: 단어 선택이 문맥에 맞는지 판단한다.
- `LangState.internal.lexicalDiversity`: 같은 단어만 반복하지 않는지 판단한다.
- `LangState.internal.vocabularyLevel`: CEFR 기반 어휘 난이도 단서로 쓴다.
- `LangState.external.expressionRange`: 현재는 누적 표현 폭의 유일한 source이므로 예외적으로 vocabulary signal에만 사용한다.
- `LangState.internal.sentenceComplexity`: 단문을 넘어 이유, 조건, 연결 설명을 다룰 수 있는지 판단한다.
- `LangState.internal.speechRate`: 발화가 너무 느리거나 끊기지 않는지 판단한다.
- `LangState.internal.pauseFrequency`: 머뭇거림이 대화 지속을 방해하는지 판단한다.
- `LangState.internal.avgUtteranceLength`: 한 번에 이어 말할 수 있는 길이를 판단한다.
- `LangState.internal.spokenNaturalness`: 구어체 응답이 자연스러운지 판단한다.
- `LangState.internal.naturalExpressionUsage`: 원어민이 자주 쓰는 표현 선택이 가능한지 판단한다.
- `LangState.analysisMeta.activeFocus`: 반복 약점이 특정 영역에 쏠리는지 판단하되, prompt에는 상위 1~2개 focus만 반영한다.

파생 signal:

| signal | 계산 기준 | 해석 |
| --- | --- | --- |
| `grammarSignal` | `grammarAccuracy` | 문장 뼈대와 기본 오류 안정성 |
| `vocabularySignal` | `average(vocabularyAppropriateness, lexicalDiversity, max(vocabularyLevelScore, expressionRangeScore))` | 단어 선택, 표현 폭, 어휘 난이도 |
| `fluencySignal` | `average(speechRate, 1 - pauseFrequency, avgUtteranceLength)` | 말의 흐름, 끊김, 이어 말하기 |
| `structureSignal` | `sentenceComplexity` | 이유/감정/상황 설명을 연결하는 능력 |
| `naturalnessSignal` | `average(spokenNaturalness, naturalExpressionUsage)` | 구어체 자연스러움과 표현 선택 |
| `confidenceSignal` | `analysisMeta.metricEvidence`, `lastAnalyzedAt`, stage spread | 현재 band를 얼마나 믿을 수 있는지 |

기본 threshold:

| score range | stage 해석 |
| --- | --- |
| `< 0.25` | `Foundation` |
| `0.25 ~ < 0.45` | `Developing` |
| `0.45 ~ < 0.65` | `Stable` |
| `0.65 ~ < 0.82` | `Expanding` |
| `>= 0.82` | `Refined` |

band 산출 기준:

| band | 필요 조건 | 보수 조정 |
| --- | --- | --- |
| `IntentOnly` | confidence가 낮고 의미 있는 metric이 거의 없거나, grammar/vocabulary/fluency가 모두 `Foundation`에 가깝다. | 사용자를 실제 최저 실력으로 단정하지 않고, 첫 세션 fallback으로도 사용한다. |
| `PhraseEmerging` | vocabulary가 `Foundation~Developing`이고 fluency 또는 grammar가 아직 낮지만, 일부 단어/구 표현 근거가 있다. | pause가 높거나 avg utterance가 낮으면 `IntentOnly`로 낮춘다. |
| `SimpleSentence` | grammar 또는 vocabulary가 `Developing` 이상이고, 짧은 문장 수준의 발화 길이/구조 근거가 있다. | grammar가 `Foundation`이면 문장 확장보다 의도 확인을 우선한다. |
| `BasicConversation` | grammar/vocabulary/fluency 중 2개 이상이 `Stable` 이상이고, avg utterance length가 짧은 왕복 대화를 감당할 수 있다. | active focus가 blocking grammar/error recurrence에 쏠리면 질문 부담을 한 단계 낮춘다. |
| `ConnectedExpression` | vocabulary/structure/naturalness 중 2개 이상이 `Expanding`에 가깝고, 이유/감정/상황 설명 근거가 있다. | fluency가 낮으면 긴 open question 대신 짧은 선택형 follow-up을 유지한다. |
| `NuanceControl` | naturalness와 vocabulary가 `Refined`에 가깝고, grammar/fluency가 최소 `Stable` 이상이다. | confidence가 `High`가 아니면 `ConnectedExpression`으로 유지한다. |

중요한 방어 원칙:

- 하나의 고점 metric만으로 band를 올리지 않는다.
- confidence가 낮으면 `IntentOnly` 또는 `PhraseEmerging`의 행동 정책을 우선 적용한다.
- 가장 약한 영역이 대화 지속을 막는 경우에는 band 자체보다 질문 부담과 응답 길이를 먼저 낮춘다.
- `external` 값은 display projection이므로 직접 능력 판단에 쓰지 않는다. 단, `expressionRange`는 현재 표현 폭 source이므로 vocabulary signal에서만 예외적으로 사용한다.
- `errorRecurrence`와 `reviewRetention`은 대화 band를 직접 올리는 기준이 아니라, focus와 복습/교정 흐름에서 우선순위를 정하는 보조 신호로 둔다.

---

# Prompt 변환 원칙

대화 능력 단계는 prompt에 직접 노출하지 않는다.
`BuildLearnerAdaptationProfileUseCase` 또는 후속 profile builder가 내부 단계를 계산하고, `BuildPromptUseCase`는 아래처럼 짧은 행동 정책만 받는다.

세션 시작 prompt와 turn override는 역할이 다르다.
둘을 같은 내용으로 반복하면 prompt가 장황해지고 모델이 우선순위를 혼동할 수 있으므로, 아래 계층을 유지한다.

| 계층 | 입력 | 역할 | prompt에 담는 내용 |
| --- | --- | --- | --- |
| 세션 시작 prompt | `LangState` 기반 `LearnerAdaptationProfile` | 세션 전체의 기본 대화 방향을 정한다. | persona, 언어 관계, 반복훈련 금지, 기본 난이도, 최근 맥락 |
| turn override | 방금 사용자 final transcript 기반 임시 정책 | 세션 기본값과 다른 이번 AI 응답의 부담만 보정한다. | 기본값과 달라진 기준언어 사용 여부, 응답 길이, 문장 밀도, 질문 부담, 음성 속도 |

세션 시작 prompt는 사용자의 외국어 능력을 전체 방향으로 주입한다.
turn override는 사용자의 외국어 능력 전체를 매번 다시 주입하지 않고, 현재 발화가 보여준 “이번 응답에서 필요한 보정”만 짧게 전달한다.
세션 시작 prompt에 이미 들어간 기본 정책과 같은 값은 turn override에 다시 넣지 않는다.
같은 “천천히/쉽게/짧게” 지시가 매 turn 반복되면 모델이 이를 누적 강화할 수 있으므로, turn override는 delta 방식으로 작성한다.

```text
learner_profile:
- ability_behavior: intent-first / phrase-builder / simple-sentence / daily-conversation / connected-expression / nuance-control
- primary_bridge: active / brief / fallback-only / none
- recast: tiny-inline / simple-inline / natural-inline / nuance-only
- growth: one-tiny-phrase / one-simple-pattern / one-everyday-expression / one-native-like-choice
- question: concrete-choice / one-concrete-follow-up / open-short / nuance-follow-up
- response_length: one-short-sentence / short-two-step / natural-brief / flexible
```

정책:

- `ability_behavior`는 사용자의 발화를 얼마나 적극적으로 해석해야 하는지 결정한다.
- `primary_bridge`는 `primaryLang`을 얼마나 섞을지 결정한다.
- `recast`는 사용자가 하려던 말을 대화 안에서 어떻게 자연스럽게 다시 말할지 결정한다.
- `growth`는 현재 능력보다 조금 높은 표현을 어느 정도 노출할지 결정한다.
- `question`은 질문을 강제하는 값이 아니라 사용자가 다음 발화를 만들 수 있는 부담을 조절한다.
- `response_length`는 한 응답의 길이와 정보량을 제한한다.
- `speech_delivery`는 첫 대화 fallback이나 초급 정책에서 실제 음성 속도뿐 아니라 문장 사이 간격과 정보 밀도까지 낮춘다.
- `turn_space`는 초급자에게도 질문을 강제하지 않고 음식, 장소, 감정, 행동 같은 실제 내용으로 짧게 답할 여지를 만든다.
- `current_turn_override`는 필요한 경우에만 추가하고, 기본 prompt를 대체하지 않는다.

세부 정책은 내부 판단을 안정화하기 위한 장치다.
세부 정책이 많아진다고 prompt 줄 수가 늘어나면 안 된다.
`BuildPromptUseCase`는 여러 정책 조합을 읽고, 최종적으로 4~6줄 정도의 실행 가능한 행동 지시로 압축한다.

## Prompt 비대화 방지 원칙

프롬프트 개선은 실패 사례가 나올 때마다 금지 문장과 요구사항을 계속 덧붙이는 방식으로 진행하지 않는다.
그 방식은 prompt를 길고 모순되게 만들고, 모델이 우선순위를 잃어 자연스러운 대화보다 “규칙을 지키는 답변”에 치우치게 만든다.

수정 순서:

1. 실패 응답을 먼저 관찰한다.
2. 실패를 유도한 기존 지시가 있는지 찾는다.
3. 기존 지시를 제거하거나 더 넓은 행동 원칙으로 바꾼다.
4. 같은 의미의 요구사항이 여러 블록에 반복되면 하나로 합친다.
5. 그래도 해결되지 않을 때만 새 지시를 추가한다.

작성 원칙:

- 실패 문구를 그대로 금지 목록으로 늘리지 않는다.
- `하지 마라`보다 `어떻게 응답하라`를 우선한다.
- 프롬프트에는 실패 사례 자체가 아니라 일반화된 행동 원칙만 넣는다.
- 실패 사례와 회귀 기준은 prompt가 아니라 multi-turn 평가표와 테스트 문서에 남긴다.
- 새 정책을 추가하면 기존 `persona`, `branches`, `learner_policy`, `turn override` 중 어느 블록이 책임질지 먼저 정한다.
- 같은 의미가 두 블록 이상에 들어가면 더 우선순위가 높은 한 블록만 남긴다.
- 세션 시작 prompt는 장기 대화 철학과 기본 profile만 담고, turn override는 이번 응답의 짧은 보정값만 담는다.
- turn override가 세션 prompt의 persona, 목표, 금지사항을 반복하기 시작하면 비대화로 본다.
- 프롬프트 수정 후에는 전체 길이, 중복 표현, 금지문 증가 여부를 함께 검토한다.

예시:

나쁜 수정:

```text
- Do not say "Let's keep it simple."
- Do not say "Let's try."
- Do not ask the user to repeat.
- Do not ask yes/no.
```

좋은 수정:

```text
- 학습 안내 멘트가 아니라 실제 일상 대화처럼 먼저 자연스럽게 반응한다.
- 사용자가 말하려던 뜻을 자연스럽게 받아 주고, 필요할 때만 실제 내용으로 짧게 답할 여지를 남긴다.
```

이 원칙의 목적은 제한사항을 늘리는 것이 아니라, 실패 원인을 더 짧고 강한 행동 원칙으로 치환하는 것이다.

최종 prompt 압축 예시:

```text
learner_policy:
- infer unclear intent before correcting.
- use Korean only for a short intent check when the learner is stuck.
- show one natural English wording inside the reply.
- keep the flow natural and leave a short next-turn opening only when needed.
```

금지:

- `IntentOnly`, `PrimaryBridge.Active`, `RecastStyle.InlineTinyRecast` 같은 내부 enum 이름을 그대로 넣지 않는다.
- `grammarAccuracy=0.42` 같은 raw metric을 넣지 않는다.
- 같은 의미의 지시를 `persona`, `branches`, `learner_profile`에 반복하지 않는다.
- 모든 세부 정책을 1:1로 prompt line에 나열하지 않는다.

turn override 예시:

```text
current_turn_override:
- 이번 응답은 짧게 반응하고 한 가지 의미만 전달한다.
- 한국어로 의미를 먼저 잡고 영어 쉬운 표현을 짧게 붙인다.
- 필요할 때 음식, 장소, 감정, 행동 중 하나로 짧게 답할 여지를 둔다.
```

turn override 작성 원칙:

- 세션 시작 prompt의 persona와 기본 원칙을 반복하지 않는다.
- 이번 응답에서 세션 기본값과 달라진 값만 1~4줄로 둔다.
- 세션 기본값과 같은 turn policy면 `response.create.instructions` 자체를 생략한다.
- `LangState` raw metric, 내부 enum, age label을 넣지 않는다.
- 말투, 응답 길이, 기준언어 사용, 질문 부담은 `response.create.instructions`에 이번 응답용 override로 전달한다.
- 실제 `audio.output.speed`가 바뀌는 경우에만 `session.update`를 사용한다.
- 같은 speed 정책이 유지되면 매 turn `session.update`를 반복하지 않는다.
- override가 없거나 적용 실패하면 세션 시작 prompt의 기본 정책으로 응답을 계속한다.

---

# Chat 세부 정책

Chat 세부 정책은 “사용자에게 어떤 도움을 어떤 방식으로 줄지”를 나누는 내부 기준이다.
Correction의 `challengeLevel`처럼 하나의 단계로 모든 것을 대표하지 않고, 대화 지속에 필요한 요소를 분리한다.

```kotlin
data class ChatAdaptationPolicy(
    val conversationBand: ConversationAbilityBand,
    val intentSupport: IntentSupportPolicy,
    val primaryBridge: PrimaryBridgePolicy,
    val recastStyle: RecastStylePolicy,
    val expressionGrowth: ExpressionGrowthPolicy,
    val questionLoad: QuestionLoadPolicy,
    val responseLength: ResponseLengthPolicy,
    val speechSpeed: SpeechSpeedPolicy
)
```

정책 설명:

| 정책 | 의미 | prompt 변환 방향 |
| --- | --- | --- |
| `conversationBand` | 사용자의 전체 대화 가능 단계를 나타낸다. 조각난 의도 표현부터 뉘앙스 조절까지 6단계로 나눈다. | band 이름을 직접 넣지 않고, 현재 단계에 맞는 행동 지시로만 변환한다. |
| `intentSupport` | 사용자의 불완전한 발화에서 AI가 의도를 얼마나 적극적으로 추론할지 정한다. | `infer unclear intent before correcting`처럼 짧게 압축한다. |
| `primaryBridge` | `primaryLang`을 얼마나 사용할지 정한다. | `use primary language only for a short intent check`처럼 제한적으로 표현한다. |
| `recastStyle` | 사용자가 하려던 말을 대화 안에서 어느 정도 자연스럽게 다시 말할지 정한다. | `show one natural wording inside the reply`처럼 한 줄로 표현한다. |
| `expressionGrowth` | 현재 능력보다 조금 높은 표현을 얼마나 추가할지 정한다. | `add one useful everyday expression only when it fits`처럼 제한한다. |
| `questionLoad` | 사용자가 다음 발화를 만들 때 받는 부담을 정한다. | `ask one concrete question` 또는 `ask a short open follow-up`으로 변환한다. |
| `responseLength` | AI 응답 길이와 정보량을 제한한다. | `keep the reply short` 또는 `natural and brief`로 변환한다. |
| `speechSpeed` | AI 음성 응답 속도를 조절한다. | prompt에 장황하게 쓰지 않고, 가능하면 Realtime audio 설정 값으로 전달한다. |

세션 내 임시 turn 정책은 장기 profile을 대체하지 않는다.
장기 profile이 기본값이고, turn 정책은 이번 응답에서만 더 쉽고 느리게 보정하거나, fluent 발화에서는 과한 보조를 줄이는 역할을 한다.

```kotlin
data class ChatTurnAdaptationPolicy(
    val responseLength: ResponseLengthPolicy,
    val sentenceDensity: SentenceDensityPolicy,
    val primaryBridge: PrimaryBridgePolicy,
    val questionLoad: QuestionLoadPolicy,
    val speechSpeed: SpeechSpeedPolicy
)
```

`SentenceDensityPolicy`는 후속 구현에서 추가할 Chat 전용 임시 정책이다.
Realtime speed만으로는 정보량이 줄지 않으므로, 한 응답에 담는 의미 수를 제한하기 위해 둔다.

초기 구현의 turn override는 아래 항목만 우선한다.
속도만 바꾸는 구조는 효과가 제한적이므로, 사용자가 다음 발화를 만들 수 있는지에 직접 영향을 주는 항목을 함께 조절한다.

| 항목 | 이유 | 보정 예 |
| --- | --- | --- |
| `speechSpeed` | 사용자가 AI 음성을 이해할 수 있어야 다음 말을 할 수 있다. | 매우 낮은 발화 신호면 더 천천히 |
| `responseLength` | 긴 응답은 초저숙련 사용자에게 대화 단절을 만든다. | 짧은 반응으로 한 가지 의미만 전달 |
| `sentenceDensity` | 느린 속도라도 정보량이 많으면 이해가 어렵다. | 한 의미씩, 쉬운 단어와 구체 의미 중심 |
| `primaryBridge` | 의도 확인이나 보조 요청이 있을 때만 기준언어를 짧게 쓴다. | 기준언어로 의미를 먼저 잡고 target 핵심 표현을 짧게 붙임 |
| `questionLoad` | 사용자가 다음 turn을 만들 수 있게 부담을 낮춘다. | 음식, 장소, 감정, 행동 같은 실제 내용으로 짧게 답할 여지 |

`recastStyle`, `expressionGrowth`, `EmojiCue`는 후속 확장으로 둔다.
첫 구현에서는 대화 지속성과 이해 가능성을 우선하고, 표현 확장은 기본 profile prompt의 작은 재표현 원칙으로 처리한다.

세부 enum 방향:

```kotlin
enum class IntentSupportPolicy {
    InferActively,
    ConfirmBriefly,
    TrustMeaning,
    FollowUserLead
}
```

- `InferActively`: 단어 조각이나 기준언어 섞임에서도 먼저 의도를 추론한다.
- `ConfirmBriefly`: 추론이 틀릴 위험이 있으면 짧게 의도만 확인한다.
- `TrustMeaning`: 의미가 충분히 보이면 별도 확인 없이 대화를 이어간다.
- `FollowUserLead`: 고급 사용자는 사용자의 주제와 흐름을 그대로 따라간다.

```kotlin
enum class PrimaryBridgePolicy {
    Active,
    Brief,
    FallbackOnly,
    None
}
```

- `Active`: 초저숙련, 명시적 단절 신호, 단어 조각 발화에서 `primaryLang`으로 짧게 의도를 확인한다.
- `Brief`: 의미는 보이지만 다음 발화를 만들기 어려운 경우 핵심 힌트만 짧게 제공한다.
- `FallbackOnly`: 기본은 `selectedLang`으로 답하고, 오해나 단절이 생길 때만 `primaryLang`을 한 줄 사용한다.
- `None`: 사용자가 안정적으로 대화할 수 있으면 `primaryLang`을 쓰지 않는다.

```kotlin
enum class RecastStylePolicy {
    TinyInline,
    SimpleInline,
    NaturalInline,
    NuanceOnly
}
```

- `TinyInline`: 단어 조각 단계에서 아주 짧은 자연 표현 하나만 보여준다.
- `SimpleInline`: 짧은 문장 단계에서 사용자가 말하려던 뜻을 쉬운 문장으로 한 번 녹인다.
- `NaturalInline`: 기본 대화 이상에서 실제 원어민이 자주 쓰는 자연스러운 표현으로 한 번 재표현한다.
- `NuanceOnly`: 고급 사용자는 필요할 때만 뉘앙스나 register를 미세하게 다듬는다.

```kotlin
enum class ExpressionGrowthPolicy {
    OneTinyPhrase,
    OneSimplePattern,
    OneEverydayExpression,
    OneNativeLikeChoice
}
```

- `OneTinyPhrase`: 바로 이해할 수 있는 짧은 표현 하나만 노출한다.
- `OneSimplePattern`: 다음 발화에 재사용할 수 있는 쉬운 문장 패턴 하나를 노출한다.
- `OneEverydayExpression`: 일상 대화에서 자주 쓰는 구어체 표현 하나를 섞는다.
- `OneNativeLikeChoice`: 고급 사용자에게 더 원어민다운 표현 선택지를 하나만 제공한다.

```kotlin
enum class QuestionLoadPolicy {
    ConcreteChoice,
    OneConcreteFollowUp,
    OpenShort,
    NuanceFollowUp
}
```

- `ConcreteChoice`: 필요할 때 음식, 장소, 감정, 행동처럼 실제 내용으로 짧게 답할 여지를 둔다.
- `OneConcreteFollowUp`: 필요할 때 사용자의 말에서 자연스럽게 이어지는 짧은 여지만 둔다.
- `OpenShort`: 필요할 때 너무 넓지 않은 open-ended 여지를 짧게 둔다.
- `NuanceFollowUp`: 대화가 안정될 때만 뉘앙스나 말투 선택으로 가볍게 넓힌다.

```kotlin
enum class ResponseLengthPolicy {
    OneShortSentence,
    ShortTwoStep,
    NaturalBrief,
    Flexible
}
```

- `OneShortSentence`: 초저숙련 사용자를 위해 한 번에 짧은 한 문장 수준으로 답한다.
- `ShortTwoStep`: 짧은 반응에 필요한 경우 가벼운 후속 여지를 둔다.
- `NaturalBrief`: 자연스럽지만 장황하지 않은 일반 대화 응답을 제공한다.
- `Flexible`: 고급 사용자에게 필요한 경우 조금 더 긴 설명을 허용한다.

```kotlin
enum class SpeechSpeedPolicy {
    SlowBeginner,
    Guided,
    NormalLearning,
    SlightlyFast,
    Advanced
}
```

- `SlowBeginner`: 초저숙련 또는 새 언어 첫 대화에서 천천히 말한다.
- `Guided`: 초급 사용자가 이해할 수 있게 약간 느리게 말한다.
- `NormalLearning`: 일반 학습 대화 속도에 가깝게 말한다.
- `SlightlyFast`: 고급 사용자가 자연스러운 실제 대화 속도에 익숙해질 수 있게 조금 빠르게 말한다.
- `Advanced`: 고급 사용자를 위한 상한 내 빠른 속도를 허용한다.

---

# primaryLang 개입 조건

`primaryLang`은 보조 언어이며 대화의 중심 언어가 아니다.
AI는 기본적으로 `selectedLang`으로 대화를 이어가고, 사용자가 대화를 놓칠 가능성이 높을 때만 `primaryLang`을 짧게 섞는다.

개입 강도:

| policy | 적용 조건 | 응답 방식 |
| --- | --- | --- |
| `active` | `IntentOnly`, 명시적 이해 실패, 발화 대부분이 `primaryLang`, 단어 조각만 있는 경우, `primaryLang` 보조를 직접 요청한 경우 | `primaryLang`으로 의미를 먼저 잡고, 쉬운 `selectedLang` 핵심 표현을 짧게 붙인다. |
| `brief` | `PhraseEmerging~SimpleSentence`, 의미는 보이나 다음 발화를 만들기 어려운 경우 | 핵심 힌트만 `primaryLang`으로 짧게 주고, `selectedLang` 표현은 짧게 둔다. |
| `fallback-only` | `BasicConversation~ConnectedExpression`, 대화는 가능하지만 오해 가능성이 있는 경우 | 기본은 `selectedLang`이고, 사용자가 막히거나 오해가 크면 `primaryLang` 한 줄만 보조한다. |
| `none` | `NuanceControl`, 또는 사용자가 `selectedLang`만으로 안정적으로 대화하는 경우 | `primaryLang`을 쓰지 않고 자연스러운 `selectedLang` 대화를 유지한다. |

개입 trigger:

- 사용자가 `모르겠어`, `못해`, `이해 안 돼`, `no understand`처럼 명시적으로 단절 신호를 보낸다.
- 사용자가 현재 `primaryLang`으로 설명하거나 섞어 달라고 직접 요청한다.
- 사용자 발화가 단어 1~3개 수준이고 문장 관계를 추론해야 한다.
- 사용자 발화의 대부분이 `primaryLang`이고 `selectedLang` 단어가 일부만 섞인다.
- 직전 AI 질문에 사용자가 답하지 못하거나 같은 단절 신호를 반복한다.
- `LangState` confidence가 낮고 첫 세션 또는 새 언어 첫 대화라 실제 능력을 아직 알 수 없다.

개입 금지:

- `primaryLang`을 장문 설명으로 사용하지 않는다.
- `primaryLang`이 `selectedLang` 대화를 대체하지 않는다.
- `primaryLang`과 `selectedLang` 외 제3 언어를 임의로 섞지 않는다.
- 사용자가 `selectedLang`으로 안정적으로 답하고 있는데 습관적으로 번역을 붙이지 않는다.

---

# 10% 성장 해석

`10% 성장`은 사용자를 다음 단계로 급격히 올리는 뜻이 아니다.
사용자가 대화를 이어갈 수 있는 범위 안에서 하나의 요소만 살짝 높인다.

예시:

- 단어 조각 단계에서는 표현 하나만 더 자연스럽게 보여준다.
- 짧은 문장 단계에서는 문장 하나를 자연스럽게 재표현하고 필요할 때만 짧은 후속 여지를 둔다.
- 기본 대화 단계에서는 일상 표현 하나를 섞고 사용자의 경험을 묻는다.
- 고급 단계에서는 뉘앙스나 register를 자연스럽게 조정한다.

금지 방향:

- 한 세션 안에서 반복 따라 말하기 훈련을 시키지 않는다.
- “준비됐으면 yes/no로 답하세요” 같은 훈련 흐름을 만들지 않는다.
- 사용자의 발화를 멈추고 별도 수업처럼 교정 설명을 길게 하지 않는다.
- `primaryLang`을 과하게 섞어 `selectedLang` 대화 경험을 대체하지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| LearningState domain | raw `LangState` metric과 evidence를 관리한다. |
| LearnerAdaptationProfile builder | raw metric을 Chat이 사용할 대화 능력 단계와 행동 정책으로 변환한다. |
| Chat turn adaptation use case | 사용자 final transcript와 기본 profile을 조합해 이번 응답의 임시 보정 정책을 계산한다. |
| BuildPromptUseCase | 세션 시작 prompt와 이번 응답용 `response.create.instructions` override를 각각 짧게 조립한다. |
| BuildChatSpeechSpeedUseCase | 기본 profile 속도와 turn override 속도 중 더 안전한 값을 계산한다. |
| ChatRepositoryImpl | 세션 시작 instruction, response override instruction, speed 값을 OpenAI Realtime에 전달한다. prompt 정책을 해석하지 않는다. |
| Correction | 교정 prompt와 learning signal 계약은 별도 흐름으로 유지한다. |

turn override 적용 시점:

```text
USER final transcript 수신
→ domain/usecase에서 turn override 계산
→ 실제 음성 speed 변경이 필요하면 같은 Realtime 세션에 session.update 전송
→ 말투/길이/기준언어/질문 부담은 response.create.instructions override로 전달
→ response.create 전송
```

방어 원칙:

- turn override는 세션 안에서만 유지하고 장기 `LangState`를 갱신하지 않는다.
- repository는 사용자 발화 수준을 판단하지 않고, 이미 계산된 instruction/speed만 적용한다.
- `response.create.instructions`는 이번 응답에만 적용되는 짧은 override로 사용하고, 세션 시작 prompt 전체를 매 turn 다시 보내지 않는다.
- `session.update`는 실제 speed 값이 바뀐 경우에만 전송한다.
- `session.update` 전송 실패 시 기존 speed와 이번 response override로 `response.create`를 진행한다.
- turn speed 변경은 응답 지연을 만들지 않기 위해 `session.updated` 대기를 강제하지 않는다.
- WebSocket 전송 순서상 `session.update`를 먼저 보내고 `response.create`를 이어 보내되, 서버 적용이 늦으면 기존 speed fallback을 허용한다.
- WebSocket 재연결, SessionMemory 저장, usage tracking, final transcript, 마이크 상태 흐름은 이 정책 계산과 분리한다.

---

# Multi-turn 평가표

`CHAT-TUNE-002` prompt 검증은 단발 응답으로 판단하지 않는다.
초저숙련 사용자가 AI 응답을 듣고 다음 말을 이어갈 수 있는지 보려면 최소 3턴 흐름을 평가해야 한다.

평가 시나리오 형식:

| id | band | primary→selected | 사용자1 | 기대 AI1 | 예상 사용자2 | 기대 AI2 | 예상 사용자3 | 통과 기준 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T01 | `IntentOnly` | KO→EN | `I apple hungry` | 의미 확인 + 쉬운 영어 표현 + 짧게 답할 여지 | `apple` | 표현 반복 강요 없이 자연스럽게 반응 | `home` | 사용자가 단어 하나로도 다음 턴을 이어갈 수 있다. |
| T02 | `IntentOnly` | KO→JA | `나는 일본어 아니다` | 한국어로 의미 먼저 보장 + 아주 쉬운 일본어 표현 | `몰라` 또는 `はい` | 다시 따라 시키지 않고 이해 가능한 반응으로 유지 | `food` | 단절 신호 뒤에도 대화가 유지된다. |
| T03 | `PhraseEmerging` | KO→EN | `friend meet today` | 자연스러운 표현 한 번 + 짧게 답할 여지 | `today` | 장소나 감정으로 자연스럽게 확장 | `cafe` | 표현 조각 선택이 아니라 의미 선택으로 이어진다. |
| T04 | `SimpleSentence` | KO→EN | `I went school yesterday` | 자연 반응 + 한 번만 재표현 + 쉬운 경험 질문 | `hard` | 감정/이유를 짧게 확장 | `homework` | 문법 교정 수업이 아니라 대화가 이어진다. |
| T05 | `BasicConversation` | KO→EN | `I talked with boss, little awkward` | 의미 확인 + 자연스러운 구어체 표현 하나 + 실제 follow-up | `meeting` | 분위기/다음 행동 질문 | `tense` | 새 표현이 과하지 않고 다음 발화를 돕는다. |
| T06 | `ConnectedExpression` | KO→EN | `I was nervous because I had to explain my idea` | 공감 + 연결 표현/구어체 하나 + 이유 질문 | `not clear` | 더 자연스러운 설명 방식 제안 + 짧은 follow-up | `example` | 이유/상황 설명을 한 단계 확장한다. |
| T07 | `NuanceControl` | KO→EN | `I talked with my boss but it was a little awkward` | 자연 반응 + 뉘앙스 선택지 하나 + 일반 대화 질문 | `short` | register/표현 선택을 짧게 다듬고 대화 복귀 | `coffee` | 고급 사용자를 학습자 취급하지 않는다. |

공통 평가 항목:

- `intent_understood`: 사용자 의도를 적절히 추론했다.
- `natural_daily_flow`: 교정봇이나 반복훈련이 아니라 일상 대화처럼 이어졌다.
- `next_turn_possible`: 사용자가 단어/짧은 구로도 다음 턴을 만들 수 있다.
- `selected_expression_helpful`: 올바른 표현은 한 번만 자연스럽게 노출됐다.
- `primary_support_appropriate`: `primaryLang` 보조가 필요한 경우에만 짧게 쓰였다.
- `third_language_safe`: `primaryLang`/`selectedLang` 외 언어가 임의로 섞이지 않았다.
- `not_repetitive`: 따라 말하기, yes/no 준비 확인, 같은 표현 반복 유도가 없다.
- `band_fit`: 응답 길이, 질문 부담, 표현 확장 폭이 band에 맞다.

통과 기준:

- 각 시나리오의 3턴 흐름에서 모든 공통 평가 항목이 통과해야 한다.
- 한 시나리오라도 반복 따라 말하기 루프가 발생하면 prompt는 실패로 본다.
- Spark 텍스트 평가가 통과해도 최종 판단은 실제 Realtime 앱 실행 결과를 우선한다.

---

# 검증 기준

- `CHAT-TUNE-001-A~D` 완료 범위를 변경하지 않는다.
- Chat용 세분화 전략은 `CHAT-TUNE-002` 문서와 이슈 기준으로만 구현한다.
- conversation band는 `LangState.internal`, `analysisMeta`, 예외적 `expressionRange` 기준으로 산출된다.
- 세션 시작 prompt는 기본 profile과 대화 원칙을 담고, turn override는 이번 응답의 짧은 보정값만 담는다.
- turn override는 세션 안에서만 유지되고 `LangState`, SessionMemory, Correction 원문, usage 원문에 저장되지 않는다.
- turn override의 말투/길이/기준언어/질문 부담은 세션 기본값과 다를 때만 `response.create.instructions`에 짧게 들어간다.
- 세션 기본값과 같은 turn policy에서는 `response.create.instructions`를 생략해 같은 지시가 반복 주입되지 않는다.
- 실제 음성 speed 변경이 필요할 때만 `session.update`를 사용하고, WebSocket 세션은 재시작하지 않는다.
- `session.update` 전송 실패 시 기존 speed로 `response.create`가 계속 진행된다.
- turn speed 변경은 `session.updated`를 기다리느라 대화를 지연시키지 않는다.
- `primaryLang` 개입은 명시적 조건이 있을 때만 발생한다.
- multi-turn 평가에서 사용자가 다음 말을 이어갈 수 있어야 한다.
- prompt에 raw metric, 내부 enum, 나이 비유가 직접 포함되지 않는다.
- 초급 사용자의 조각난 발화에서도 AI가 의도를 추론하고 대화를 이어간다.
- 중고급 사용자는 과한 기준언어 설명 없이 자연스러운 일상 대화를 경험한다.
- 응답이 반복 따라 말하기 훈련으로 흐르지 않는다.
- `primaryLang`과 `selectedLang` 의미가 유지된다.
- transport, session, usage, final transcript, mic 상태 흐름에 회귀가 없다.
