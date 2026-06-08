# [Improvement] CHAT-TUNE-005 대화 능력 단계 정의 재조정

## 목적

`CHAT-TUNE-002`는 Chat prompt를 6단계 `ConversationAbilityBand`에 연결하는 전략을 정의했다.
하지만 실제 저숙련 대화 테스트에서 낮은 단계의 경계가 충분히 선명하지 않았고, 고정 인사나 짧은 따라 말하기만으로 사용자의 대화 능력이 과대 해석될 수 있었다.

`CHAT-TUNE-005`는 기존 6단계 구조를 버리는 작업이 아니다.
Chat의 6단계 band 정의를 다시 고정하고, 단계별 prompt가 교정/훈련이 아니라 자연 대화와 표현 노출에 맞게 동작하도록 기준을 재정리하는 작업이다.

---

# User Story

사용자는 학습 언어를 거의 모르거나 짧은 단어만 알아도 친구와 말하듯 대화를 이어갈 수 있다.
Umma는 사용자의 말을 고쳐주거나 따라 말하게 시키기보다, 기준언어로 의미를 받쳐주고 학습언어의 아주 작은 표현을 자연스럽게 들려준다.
사용자는 자기 수준에 맞지 않는 긴 학습언어 응답이나 직접 대화를 설계해야 하는 부담 없이 앱을 계속 테스트할 수 있다.

---

# 완료 기준(AC)

- [ ] Chat의 6단계 `ConversationAbilityBand` 정의가 문서와 코드에서 일관된다.
- [ ] 낮은 단계는 학습언어 단어를 몇 개 아는지보다, 학습언어만으로 대화가 이어지는지를 우선 기준으로 삼는다.
- [ ] `IntentOnly` 사용자는 기준언어, 단어 하나, 짧은 반응만으로도 대화가 이어질 수 있다.
- [ ] `PhraseEmerging`은 고정 표현 암기가 아니라 아주 짧은 학습언어 표현에 일부 반응할 수 있는 단계로 정의된다.
- [ ] `SimpleSentence` 이상은 짧은 자유 문장을 만들 수 있는 근거가 있을 때만 적용된다.
- [ ] Chat prompt는 사용자의 문장을 직접 교정하거나 반복 훈련시키지 않는다.
- [ ] prompt에는 raw metric, 내부 enum 이름, 나이 비유, 실패 문구 예시, 이모지 지시가 들어가지 않는다.
- [ ] 학습언어별 style reference는 기준언어와 학습언어만 사용하고, 제3언어 fallback 예시를 넣지 않는다.
- [ ] 낮은 단계에서 AI가 주제를 사용자에게 떠넘기지 않고, 부담 낮은 일상 대화를 먼저 리드한다.
- [ ] 후속 `CHAT-TUNE-006`, `CHAT-TUNE-007`에서 LangState 저장/재사용 구조를 확장할 수 있도록 band 정의와 evidence 의미가 분리된다.

---

# 포함 범위

- `ConversationAbilityBand` 6단계 정의 재정리
- 낮은 단계의 경계 조건 재정의
- Chat prompt 공통 원칙과 band별 응답 방향 정리
- Chat conversation evidence가 측정해야 할 의미 단서 정의
- 실제 신고 세션을 검토하기 위한 prompt review 기준 정리
- 후속 LangState 통합을 위한 source of truth 후보 정리

---

# 제외 범위

- Correction prompt 작성
- Correction learning signal 계약 변경
- LangState update 저장 경로 구현
- Statistics/Dashboard 지표 재설계
- 회원탈퇴/초기화 Cloud Function cleanup 구현
- Realtime API transport 변경
- 신고 버튼 UI 변경

---

# 기준 문서

- [CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)
- [CHAT_PROMPT_TUNING_GUIDE](./CHAT_PROMPT_TUNING_GUIDE.md)
- [CHAT-TUNE-001-C LearnerAdaptationProfile](./CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md)
- [CHAT-TUNE-001-D Chat Prompt Integration](./CHAT-TUNE-001/CHAT-TUNE-001-D_Chat_Prompt_Integration.md)
- [LS-001 Language State Model Structure](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 기존 작업과의 관계

## CHAT-TUNE-002

`CHAT-TUNE-002`는 Chat prompt를 6단계 대화 능력으로 세분화하는 큰 방향을 정의했다.
`CHAT-TUNE-005`는 이 구조를 유지하되, 실제 테스트에서 드러난 낮은 단계의 과대평가와 prompt 역할 혼선을 줄이기 위해 단계 정의를 다시 고정한다.

## CHAT-TUNE-006

`CHAT-TUNE-005`는 어떤 대화 단서를 봐야 하는지 정의한다.
`CHAT-TUNE-006`은 그 단서를 세션 종료 후 분석해 `LangState` update 경로에 안전하게 연결하는 후속 작업이다.

## CHAT-TUNE-007

`CHAT-TUNE-005`는 band 의미와 prompt 방향을 정의한다.
`CHAT-TUNE-007`은 다음 Chat 세션 시작 시 어떤 저장 source를 공식 band 계산 기준으로 사용할지 정리하는 후속 작업이다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Chat prompt | 친구처럼 대화하며 수준에 맞는 표현을 들려준다. 교정/훈련을 담당하지 않는다. |
| `ConversationAbilityBand` | Chat 대화 지속 능력의 내부 단계다. prompt에 enum 이름을 직접 노출하지 않는다. |
| `BuildPromptUseCase` | 계산된 band를 짧은 자연어 행동 지시로 압축한다. |
| `BuildLearnerAdaptationProfileUseCase` | 저장된 상태를 Chat/Correction용 정책으로 해석한다. |
| Chat conversation analysis | 세션 종료 후 대화 지속 능력 evidence를 만든다. |
| Prompt review 도구 | 이상 대화 신고와 분석 자료를 모은다. 능력 저장의 공식 source가 아니다. |
| Correction flow | 교정, 설명, 더 나은 표현 제안, 문장 분석을 담당한다. |

---

# 6단계 정의

나이 비유는 팀 내부 이해용이다.
prompt, 저장 모델, 사용자 화면에는 나이 비유를 넣지 않는다.

| Band | 내부 비유 | 핵심 기준 | Chat 응답 방향 |
| --- | --- | --- | --- |
| `IntentOnly` | 0세 | 학습언어만으로는 거의 대화가 불가능하다. 단어를 거의 모르거나 기준언어 반응만 가능하다. | AI가 대화를 거의 전부 리드한다. 기준언어로 의미를 짧게 받치고 학습언어 말 한 조각만 붙인다. |
| `PhraseEmerging` | 3세 | 단어와 짧은 구를 일부 안다. 아주 쉬운 표현은 부분적으로 이해하지만 자유 문장은 불안정하다. | AI가 먼저 흐름을 만들고, 사용자가 단어와 짧은 구로 반응할 수 있게 한다. |
| `SimpleSentence` | 6세 | 짧고 단순한 문장을 어느 정도 이해하고 만들 수 있다. 긴 문장과 빠른 전환은 놓칠 수 있다. | 쉬운 학습언어 중심으로 짧게 말하고, 막히는 지점만 기준언어로 받친다. |
| `BasicConversation` | 10세 | 짧은 일상 왕복 대화가 가능하다. 선호, 경험, 간단한 이유를 말할 수 있다. | 학습언어 중심으로 친구처럼 반응하고, 사용자의 말에서 주제를 확장한다. |
| `ConnectedExpression` | 13세 | 이유, 감정, 상황을 연결해서 말할 수 있다. 표현 선택과 구어체 자연스러움은 성장 중이다. | 자연스러운 학습언어 흐름 안에서 연결 표현과 실제 생활 표현을 들려준다. |
| `NuanceControl` | 성인 | 의미 전달과 대화 유지는 안정적이다. 톤, 뉘앙스, 원어민다운 선택이 성장 지점이다. | 원어민 친구처럼 자연스럽게 대화하고, 교정 없이 실제 생활 표현과 리듬을 보여준다. |

---

# 단계 경계

| 경계 | 판단 기준 |
| --- | --- |
| `IntentOnly` -> `PhraseEmerging` | 단어를 아는 정도가 아니라, 아주 짧은 학습언어 표현에 반응할 수 있는가 |
| `PhraseEmerging` -> `SimpleSentence` | 고정 표현이나 구 수준을 넘어 짧은 자유 문장을 만들 수 있는가 |
| `SimpleSentence` -> `BasicConversation` | 문장 하나를 만드는 수준을 넘어 짧은 왕복 대화를 유지할 수 있는가 |
| `BasicConversation` -> `ConnectedExpression` | 단순 답변을 넘어 이유, 감정, 상황을 연결해 말할 수 있는가 |
| `ConnectedExpression` -> `NuanceControl` | 의미 연결이 과제인가, 자연스러운 톤과 뉘앙스가 과제인가 |

낮은 단계 방어 기준:

- 고정 인사, 따라 말한 표현, 단어 몇 개만으로 `PhraseEmerging` 이상으로 올리지 않는다.
- 사용자가 학습언어-only 응답을 이해하지 못해 대화가 끊기면 `IntentOnly`에 가깝게 본다.
- `SimpleSentence`는 단어와 구가 아니라 짧은 자유 문장 근거가 있어야 한다.
- 낮은 단계에서 AI가 주제를 사용자에게 떠넘기지 않는다.

---

# Prompt 변환 원칙

- Chat은 튜터가 아니라 기준언어를 이해하는 외국인 친구처럼 대화한다.
- 사용자의 문장을 직접 교정하지 않는다.
- 반복 따라 말하기, 문법 분석, 오류 지적을 하지 않는다.
- AI 자신의 답변 안에서 자연스러운 실생활 표현을 들려준다.
- 낮은 단계에서는 학습언어만으로 긴 문장을 만들지 않는다.
- 기준언어와 학습언어만 사용한다. 제3언어를 섞지 않는다.
- band별 예시는 복사할 템플릿이 아니라 길이, 비율, 리듬 참고용이다.

세션 시작 prompt는 기본 persona, 언어 관계, 대화 원칙, 현재 style만 전달한다.
turn override가 필요한 경우에는 세션 기본값과 달라진 이번 응답의 부담만 짧게 전달한다.

---

# Chat Conversation Evidence 방향

`CHAT-TUNE-005` 단계의 핵심은 band 자체를 저장하는 것이 아니라, band를 계산할 수 있는 대화 능력 단서를 정의하는 것이다.

| Evidence | 의미 |
| --- | --- |
| `targetLanguageComprehension` | 사용자가 학습언어 입력을 어느 정도 이해하고 반응했는지 |
| `targetLanguageProduction` | 사용자가 학습언어로 직접 만든 의미 단위 |
| `supportLanguageDependence` | 기준언어가 없으면 대화가 끊기는 정도 |
| `aiScaffoldingDependence` | AI 힌트, 선택지, 쉬운 재구성 의존도 |
| `conversationSustainability` | 학습언어 반응으로 대화가 유지되는 정도 |
| `consistency` | 세션 전체에서 능력 단서가 안정적인지 |
| `responseDifficultyFit` | AI 응답 난이도가 사용자에게 맞았는지 |
| `confidence` | 이 분석을 얼마나 신뢰할지 |

분석 원칙:

- 사용자의 기준언어 발화를 학습언어 유창성으로 평가하지 않는다.
- 학습언어 단어 몇 개를 따라 말한 것만으로 높은 band 근거를 만들지 않는다.
- 기준언어나 AI scaffold 없이는 대화가 끊기면 낮은 band 보호 근거로 본다.
- `debugRecommendedBand`가 있더라도 앱은 그대로 적용하지 않고 domain policy로 해석한다.

---

# 작업 계획

1. 6단계 `ConversationAbilityBand` 정의와 낮은 단계 경계를 문서 기준으로 고정한다.
2. `BuildPromptUseCase`의 band별 prompt를 교정/훈련이 아니라 친구 대화 중심으로 정리한다.
3. 학습언어별 style reference가 제3언어를 섞지 않도록 테스트를 추가한다.
4. 실제 신고 세션 export와 `AiChatPromptTrace`를 기준으로 낮은 단계 회귀를 기록한다.
5. 세션 종료 후 대화 능력 evidence를 분석할 수 있는 입력/출력 의미를 정리한다.
6. 후속 `CHAT-TUNE-006`에서 evidence를 `LangState` update 경로에 연결한다.
7. 후속 `CHAT-TUNE-007`에서 다음 Chat 시작 시 공식 band source를 정리한다.

---

# 예외와 방어

- `LangState`가 없거나 분석 근거가 없으면 사용자를 실제 최저 실력으로 확정하지 않는다.
- 첫 selectedLang 대화는 현재 발화와 최근 맥락을 우선한다.
- 사용자가 fluent하게 말하면 초저숙련 prompt로 고정하지 않는다.
- 낮은 단계 prompt가 설명, 안심, 반복 훈련 루틴으로 수렴하지 않게 한다.
- 신고/리뷰 문서의 실패 사례를 prompt에 그대로 넣지 않는다.

---

# 테스트 방법

- `git diff --check`
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- `BuildPromptUseCaseTest`
- `ChatPromptIntegrationUseCaseTest`
- 실제 `IntentOnly` / `PhraseEmerging` 신고 세션 export 분석
- `AiChatPromptTrace`에서 적용 band, confidence, source 확인
