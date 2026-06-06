# CHAT-TUNE-005 Conversation Band Definition Recalibration

## 배경

기존 `CHAT-TUNE-002`는 Chat prompt를 6단계 conversation band에 연결하는 기준을 만들었지만, 실제 대화 테스트에서 낮은 단계의 경계가 충분히 명확하지 않았다.

특히 사용자가 학습 언어를 거의 모르는 경우에도 단어, 고정 인사, 짧은 따라 말하기 근거만으로 `PhraseEmerging`에 가깝게 해석될 수 있었다. 그 결과 AI가 학습 언어만으로 길게 말하거나, 사용자가 직접 대화를 이어가야 하는 부담이 생겼다.

이번 작업은 기존 `CHAT-TUNE-002` 문서를 다시 확장하지 않고, 새 기준 문서에서 6단계 band 정의와 산출 기준을 다시 고정한다.

## 최신 기준 선언

`CHAT-TUNE-002`는 이전 프롬프트 튜닝 구조와 판단 과정을 담은 완료 문서로 유지한다. 이번 시점부터 Chat prompt의 대규모 단순화, 6단계 band 재정의, Chat conversation evidence snapshot, 세션 시작 prompt 기준은 이 문서(`CHAT-TUNE-005`)를 최신 기준으로 삼는다.

구현 기준:

- 세션 prompt는 `persona`, `language_use`, `conversation_principles`, `current_style`, `style_reference`, `context` 중심의 단순 구조를 사용한다.
- 기존 turn override 중심 구조는 이번 재정의의 구현 기준으로 삼지 않는다.
- band별 예시는 학습언어와 기준언어만 사용해야 하며, 학습언어가 영어가 아닌 세션에 영어 예시를 fallback으로 넣지 않는다.
- `CHAT-TUNE-002`와 이 문서가 충돌하면, 이번 작업 범위에서는 이 문서의 기준을 우선한다.

## 목표

- Chat의 6단계 `ConversationAbilityBand`를 다시 명확히 정의한다.
- 낮은 단계는 "학습 언어를 얼마나 아는가"보다 "학습 언어만으로 대화가 이어지는가"를 우선 기준으로 삼는다.
- Chat은 교정이 아니라 자연 대화와 표현 노출을 담당한다.
- 1차에서는 Chat conversation evidence snapshot으로 Chat 시작 band 산출 근거를 안정화하고, 후속 LangState 통합에서는 Correction, LangState, Chat prompt가 같은 능력 정의를 기준으로 움직이게 한다.
- 프롬프트에 실패 문구나 단어 필터를 늘리지 않고, band 산출과 단계별 행동 기준을 먼저 바로잡는다.

## 책임 경계

| 영역 | 책임 |
| --- | --- |
| `LangState` | 사용자의 학습 언어 능력과 분석 근거를 저장한다. |
| Correction 분석 | 사용자의 발화에서 band 산출에 필요한 근거를 만든다. |
| Chat conversation analysis | 세션 종료 후 저장 완료된 Chat final turn을 분석해 대화 지속 능력 evidence를 만든다. |
| Chat conversation evidence snapshot | 1차에서 Chat 시작 profile 계산에만 쓰는 임시 저장 근거다. LangState, Correction, Statistics를 직접 바꾸지 않는다. |
| Prompt review 신고 도구 | 이상 대화 QA와 프롬프트 튜닝 검토 자료를 저장한다. 대화능력 evidence 저장 트리거가 아니다. |
| `BuildLearnerAdaptationProfileUseCase` | `LangState`를 읽어 Chat용 `ConversationAbilityBand`로 해석한다. |
| `BuildPromptUseCase` | 계산된 band를 자연어 행동 지시로 압축한다. raw metric, 내부 enum, 나이 비유는 prompt에 넣지 않는다. |
| Chat prompt | 친구와 대화하면서 자연스럽게 표현을 들려주는 경험을 만든다. 사용자의 말을 고쳐주지 않는다. |
| Correction flow | 교정, 설명, 더 나은 표현 제안, 문장 분석을 담당한다. |

## 공통 원칙

- Chat은 튜터가 아니라 기준언어를 이해하는 외국인 친구처럼 대화한다.
- Chat에서는 사용자의 표현을 직접 고쳐주지 않는다.
- AI는 자기 발화 안에서 자연스러운 실생활 표현을 들려준다.
- 낮은 단계에서는 학습 언어만으로 긴 문장을 만들지 않는다.
- 기준언어와 학습언어만 사용한다. 제3언어를 섞지 않는다.
- 나이 비유는 내부 이해용이며 prompt에는 넣지 않는다.

## 6단계 정의

| Band | 내부 비유 | 핵심 기준 | AI 응답 방식 | 적절한 예시 |
| --- | --- | --- | --- | --- |
| `IntentOnly` | 0세 | 학습언어만으로는 거의 대화가 불가능하다. 단어를 거의 모르거나, 고정 표현, 짧은 소리, 기준언어 반응만 가능하다. | AI가 대화를 대부분 리드한다. 기준언어로 짧은 일상 반응을 만들고, 바로 옆에 학습언어 단어나 아주 짧은 표현을 붙인다. 사용자가 구체적인 문장을 만들지 못해도 기준언어, 단어 하나, 짧은 반응만으로 유대감 있는 대화가 이어지게 한다. | `잘 잤어? Sleep well? 오늘은 천천히 하자. Slowly. 배고파? Food?` |
| `PhraseEmerging` | 3세 | 학습언어 단어와 짧은 구를 일부 안다. 아주 쉬운 표현은 부분적으로 이해하지만 자유 문장 생성은 불안정하다. | AI가 여전히 대화를 리드한다. 기준언어로 짧게 받쳐주고, 쉬운 학습언어 표현을 붙인다. 사용자가 한 단어 또는 짧은 구로 반응할 수 있게 한다. 학습언어만으로 길게 말하지 않는다. | `좋아, 점심 얘기하자. I ate lunch. 너는 lunch 먹었어?` |
| `SimpleSentence` | 6세 | 짧고 단순한 문장을 어느 정도 이해하고 만들 수 있다. 긴 문장, 복잡한 구조, 빠른 전환은 놓칠 수 있다. | 쉬운 학습언어 중심으로 짧게 말한다. 막히는 지점은 기준언어로 짧게 보조한다. 사용자가 짧은 문장으로 답할 수 있도록 구체적이고 부담 낮은 질문을 한다. | `Nice. You ate lunch. Was it good? 맛있었어?` |
| `BasicConversation` | 10세 | 짧은 일상 왕복 대화가 가능하다. 선호, 경험, 간단한 이유를 말할 수 있지만 표현은 아직 단순하고 흔들릴 수 있다. | 학습언어로 자연스럽게 대화한다. 사용자의 답에서 주제를 받아 확장하고, 친구처럼 짧은 follow-up으로 대화를 리드한다. 필요할 때만 기준언어를 짧게 보조한다. | `That sounds nice. What did you eat? Something spicy or light?` |
| `ConnectedExpression` | 13세 | 이유, 감정, 상황을 연결해서 말할 수 있다. 문장 연결은 가능하지만 표현 선택, 구어체 자연스러움, 세부 뉘앙스는 아직 성장 중이다. | 학습언어 중심의 자연 대화를 유지한다. 이유, 감정, 상황을 자연스럽게 더 말할 수 있게 반응한다. 교정하지 않고 AI 자신의 말에서 연결 표현, 구어체 표현, 자연스러운 표현 선택을 보여준다. | `That makes sense. If you were tired, a light lunch was probably better. Did it help you feel better?` |
| `NuanceControl` | 성인 | 의미 전달과 대화 유지는 안정적이다. 더 자연스러운 톤, 뉘앙스, 원어민다운 표현 선택이 성장 지점이다. | 거의 원어민 친구처럼 자연스럽게 대화한다. 사용자의 표현을 고쳐주려 하지 않고, AI 자신의 답변 안에서 실제 생활에서 쓰는 자연스러운 표현, 톤, 리듬을 보여준다. | `Yeah, that kind of lunch can really reset your afternoon. Did the rest of your day get any better?` |

## 단계 경계

| 경계 | 판단 기준 |
| --- | --- |
| `IntentOnly` -> `PhraseEmerging` | 단어를 아는 정도가 아니라, 아주 짧은 학습언어 표현에 반응할 수 있는가 |
| `PhraseEmerging` -> `SimpleSentence` | 고정 표현이나 구 수준을 넘어, 짧은 자유 문장을 만들 수 있는가 |
| `SimpleSentence` -> `BasicConversation` | 문장 하나를 만드는 수준을 넘어, 짧은 왕복 대화를 유지할 수 있는가 |
| `BasicConversation` -> `ConnectedExpression` | 단순 답변을 넘어, 이유, 감정, 상황을 연결해 말할 수 있는가 |
| `ConnectedExpression` -> `NuanceControl` | 의미 연결이 과제인가, 자연스러운 톤과 뉘앙스가 과제인가 |

## 낮은 단계 방어 기준

- 고정 인사, 따라 말한 표현, 단어 몇 개만으로 `PhraseEmerging` 이상으로 올리지 않는다.
- 사용자가 학습언어-only 응답을 이해하지 못해 대화가 끊기면 `IntentOnly`에 가깝게 본다.
- `PhraseEmerging`은 단어를 기억하는 단계가 아니라, 아주 짧은 학습언어 표현에 일부 반응할 수 있는 단계다.
- `SimpleSentence`는 단어와 구가 아니라 짧은 자유 문장을 만들 수 있어야 한다.
- 낮은 단계에서 AI가 주제를 사용자에게 떠넘기지 않는다.

## Chat에서 하지 않는 것

- 사용자의 문장을 직접 교정하지 않는다.
- 더 나은 표현을 설명식으로 제안하지 않는다.
- 문법 분석, 오류 지적, 반복 연습 지시를 하지 않는다.
- "무엇에 대해 이야기하고 싶어?"처럼 사용자가 대화 설계를 떠안는 질문을 반복하지 않는다.
- 낮은 단계 사용자에게 학습언어-only 긴 응답을 하지 않는다.

## 선행 정리 작업

1. `ConversationAbilityBand` enum 주석을 이 문서 기준으로 맞춘다.
2. `BuildLearnerAdaptationProfileUseCaseTest`에 낮은 단계 경계 테스트를 추가한다.
3. `BuildLearnerAdaptationProfileUseCase`의 band 산출 로직을 보수적으로 조정한다.
4. `BuildPromptUseCase`의 band별 자연어 지시를 이 문서 기준으로 조정한다.
5. 실제 신고 세션을 prompt version별로 다시 수집해 `IntentOnly`, `PhraseEmerging`, `SimpleSentence` 경계가 맞는지 검증한다.

위 항목은 6단계 정의를 코드와 prompt에 맞추기 위한 선행 작업이다. 실제 다음 구현 단위는 아래의 Chat conversation evidence snapshot이다.

## 1차 실행 계획: Chat conversation evidence snapshot

현재 단계의 목표는 LangState 전체 통합 구조를 완성하는 것이 아니다. 실제 Chat 테스트에서 사용자의 대화 능력이 세션 시작에 더 잘 반영되고, 앱을 종료했다가 다시 실행해도 그 판단이 재사용되도록 만드는 것이다.

따라서 1차에서는 `LangState`와 Correction 흐름을 직접 수정하지 않고, Chat 시작에만 쓰는 작은 conversation evidence snapshot을 둔다. 이 snapshot에는 band 자체가 아니라 band를 계산할 수 있는 대화능력 근거를 저장한다. 목표는 세션 종료 후 Gemini 기반 분석 결과를 evidence로 저장하고, 다음 Chat 시작에서 domain policy가 이 evidence를 읽어 `ConversationAbilityBand`를 계산하는 것이다.

### 목적

- 현재 `LangState`를 완전히 신뢰하기 어려운 상태에서도 Chat 테스트를 안정적으로 반복한다.
- Correction 담당자의 작업과 기존 교정 흐름에 영향을 주지 않는다.
- 사용자의 언어별 Chat 대화능력 근거를 앱 재실행 후에도 재사용한다.
- Realtime API가 대화 생성과 능력 분석을 동시에 맡지 않게 분리한다.
- Gemini 기반 세션 후 분석 결과를 Chat evidence에 반영해 사람이 매번 Firestore를 수정하지 않아도 되는 방향으로 확장한다.
- 나중에 LangState conversation evidence 통합이 준비되면 이 snapshot을 LangState update input으로 흡수할 수 있게 한다.

### 저장 단위

Firestore에 사용자/학습언어별로 저장한다.

```text
users/{uid}/chat_conversation_evidence/{selectedLang}
```

최소 필드:

| Field | 의미 |
| --- | --- |
| `selectedLang` | evidence가 적용되는 학습언어 |
| `conversationSustainability` | 학습언어 대화가 사용자의 반응으로 유지됐는지, 보조가 있어야 유지됐는지 |
| `supportRequiredToContinue` | 기준언어 보조, 난이도 하향, 짧은 재구성이 있어야 대화가 회복됐는지 |
| `userContributionLevel` | 사용자가 단어, 짧은 구, 짧은 문장, 연결 발화 중 어느 단위로 참여했는지 |
| `responseDifficultyFit` | AI 응답 난이도가 사용자가 감당 가능한 수준이었는지 |
| `confidence` | 이 evidence를 얼마나 믿을지 |
| `source` | `ManualReview`, `ReportedSessionReview`, `GeminiConversationAnalysis` 등 |
| `sourceSessionId` | 분석 근거가 된 대화 세션 ID. 수동 설정이면 생략할 수 있다. |
| `reasonSummary` | evidence 판단 이유 요약. prompt에는 넣지 않고 review/debug에만 사용한다. |
| `debugRecommendedBand` | Gemini/개발자가 참고용으로 남긴 band 후보. 앱은 이 값을 그대로 적용하지 않는다. |
| `updatedAt` | 마지막 갱신 시각 |
| `expiresAt` | 오래된 evidence를 무조건 신뢰하지 않기 위한 선택 필드 |

### 생성/갱신 방식

Realtime API는 실시간 대화 생성만 담당한다. 대화 능력 분석은 세션 종료 후 별도 AI 분석 API가 담당한다.

권장 흐름:

```text
OpenAI Realtime API
  - 실시간 음성 대화 생성

Gemini conversation analysis
  - 세션 종료 또는 화면 이탈 후 저장된 대화 turn 분석
  - conversation evidence / confidence / reasonSummary 산출
  - debugRecommendedBand는 검증용 후보로만 남김
  - Chat conversation evidence snapshot 저장
```

1차 구현은 아래 순서로 작게 나눈다.

1. 앱이 `chat_conversation_evidence`를 읽어 Chat 시작의 band 산출 근거로 사용한다.
2. 세션 종료, 화면 이탈, 앱 background 전환 시 저장 완료된 `SessionMemory.recentFullContext`에서 현재 `sessionId`의 final turn만 추려 Gemini 분석 API에 전달한다.
3. Gemini 분석 결과가 충분히 신뢰 가능하면 `chat_conversation_evidence/{selectedLang}`에 저장한다.
4. 프롬프트 리뷰 신고 기능은 이상 대화 QA 자료 수집만 담당하고, conversation evidence 저장 트리거로 사용하지 않는다.
5. 다음 대화 시작부터 저장된 evidence를 읽고 domain policy가 `ConversationAbilityBand`를 계산한다.

Gemini 분석 결과를 곧바로 `LangState`에 저장하지 않는다. LangState 통합은 후속 계획으로 둔다.

### 1차와 후속의 구분

| 구분 | 1차 Chat conversation evidence snapshot | 후속 LangState evidence 통합 |
| --- | --- | --- |
| 목적 | Chat 시작 band를 계산할 대화능력 근거를 테스트 가능한 수준으로 안정화한다. | 공식 언어능력 상태와 통계 흐름까지 일관되게 연결한다. |
| 분석 주체 | 세션 종료/화면 이탈 후 Gemini conversation analysis | 세션 종료 후 ConversationSession 분석 결과 |
| 저장 위치 | `users/{uid}/chat_conversation_evidence/{selectedLang}` | 기존 `LangState` update 경로 |
| 저장 내용 | band가 아니라 대화능력 evidence | LangState update input 또는 LangState 내부 metric |
| 적용 범위 | Chat session start의 band/profile 산출 근거 | Chat, Correction, Statistics가 함께 읽는 장기 상태 |
| Correction 영향 | 없어야 한다. | source별 영향 범위를 제한해 회귀를 방지해야 한다. |

1차에서 Gemini가 산출한 `debugRecommendedBand`는 분석 품질을 확인하기 위한 참고값이다. 앱은 이 값을 그대로 적용하지 않고, 저장된 evidence를 domain policy로 해석해 `ConversationAbilityBand`를 계산한다.

### 1차 저장 방식과 후속 전환 기준

1차 Chat conversation evidence snapshot은 local-first 저장소가 아니다. 세션 종료/화면 이탈 후 Gemini 분석이 성공하면 `chat_conversation_evidence/{selectedLang}`에 Firestore 직접 저장을 시도한다. 저장 실패를 로컬 pending으로 남기지 않으며, 실패해도 사용자 대화 종료 흐름을 막지 않는다.

이 선택은 1차 snapshot이 공식 언어능력 상태가 아니라 다음 Chat 시작 prompt 보정용 보조 근거이기 때문이다. 따라서 네트워크 실패나 앱 강제종료로 evidence 저장이 누락될 수 있으며, 이 경우 다음 세션은 기존 LangState 또는 fallback 경로를 사용한다.

후속 LangState 통합 단계에서는 이 구조를 그대로 공식 상태로 확장하지 않는다. Chat conversation analysis 결과를 `LangState` update input 또는 동등한 conversation signal input으로 변환하고, 기존 LearningState 저장 정책처럼 local snapshot에 먼저 반영한 뒤 pending marker 기반 Firestore write-back/retry로 전환한다.

후속 전환 원칙:

- Chat evidence가 통계, 공식 능력 상태, 장기 profile 계산에 포함되는 시점부터 local-first + pending retry 구조가 필요하다.
- `chat_conversation_evidence` snapshot은 후속 통합 전까지 임시 보조 저장소로만 취급한다.
- 후속 전환 시 Correction 담당 흐름은 변경하지 않고, LearningState update 경로에서 source별 영향 범위를 제한한다.
- 후속 전환 전에는 `chat_conversation_evidence` 저장 실패를 사용자에게 노출하지 않는다.

### 적용 우선순위

대화 세션 시작 시 아래 순서로 Chat profile을 결정한다.

```text
1. 유효한 Chat conversation evidence snapshot을 domain policy로 해석한 profile
2. meaningful LangState 기반 profile
3. first conversation fallback
```

snapshot이 있더라도 너무 오래됐거나 confidence가 낮으면 LangState 또는 fallback을 사용할 수 있다. evidence에서 band로 가는 판단 기준은 구현 전에 별도 테스트로 고정한다.

### 사용 범위

- Chat session start에서만 사용한다.
- Chat session start의 Chat policy/profile 산출 근거로만 사용한다.
- evidence가 적용되면 domain policy가 `ConversationAbilityBand`를 계산하고, 해당 band에 맞는 `ChatAdaptationPolicy` 전체가 일관되게 적용되어야 한다.
- Correction prompt, Correction band, SRS, Statistics에는 사용하지 않는다.
- `LangState`를 직접 수정하지 않는다.
- 사용자의 공식 언어능력 상태로 노출하지 않는다.

### 유지와 초기화

| 테스트 목적 | 방법 |
| --- | --- |
| 앱 종료 후 유지 확인 | 앱 종료 후 재실행 |
| 앱 삭제 후 유지 확인 | 앱 삭제 후 재설치하고 같은 계정으로 로그인 |
| 완전 첫 사용자 테스트 | 회원탈퇴 후 새 가입 |
| 같은 계정에서 특정 언어 conversation evidence만 초기화 | `users/{uid}/chat_conversation_evidence/{selectedLang}` 삭제 |
| LangState 기반 첫 측정까지 함께 초기화 | `users/{uid}/chat_conversation_evidence/{selectedLang}`와 `users/{uid}/language_states/{selectedLang}`를 함께 삭제 |

Firestore에 저장하므로 앱 삭제만으로는 초기화되지 않는다. 현재 로컬 functions source에서는 `deleteAccount` Cloud Function의 실제 삭제 범위를 확인할 수 없으므로, 회원탈퇴 시 `chat_conversation_evidence`가 자동 삭제된다고 보장하지 않는다. 1차 테스트에서는 Firestore에서 evidence 문서를 수동 삭제해 초기화한다.

### 금지 사항

- conversation evidence snapshot을 통계 출력에 사용하지 않는다.
- conversation evidence snapshot을 Correction flow에 넘기지 않는다.
- conversation evidence snapshot을 장기 공식 능력 상태처럼 취급하지 않는다.
- `debugRecommendedBand`나 Gemini가 추천한 band 후보를 앱이 그대로 적용하지 않는다.
- Gemini 분석 결과를 곧바로 `LangState`에 저장하지 않는다.
- Realtime API에게 대화 중 능력 분석 책임을 맡기지 않는다.
- 이 작업에서 Correction signal 계약을 변경하지 않는다.
- 이 작업에서 LangState update policy를 변경하지 않는다.
- 이 작업에서 회원탈퇴 cleanup 또는 계정 삭제 Cloud Function을 수정하지 않는다.
- 후속 account cleanup 작업에서는 `chat_conversation_evidence`가 삭제 대상에 포함되는지 별도로 확인한다.

### 1차 구현 순서

1. Chat conversation evidence snapshot domain model을 추가한다.
2. Chat conversation evidence repository 계약을 추가한다.
3. Firestore data 구현을 `devtools`가 아니라 Chat 시작에서 읽을 수 있는 경로에 둔다.
4. conversation evidence를 `ConversationAbilityBand`로 해석하는 domain policy 함수를 둔다.
5. `StartSessionUseCase`에서 유효한 evidence가 있으면 Chat policy/profile 산출에 우선 반영한다.
6. `RetryConnectionUseCase`는 1차에서 제외한다. 기존 active session 재연결 중 최신 conversation evidence를 적용하면 같은 세션의 prompt/style이 바뀔 수 있다.
7. prompt trace에 evidence 적용 여부와 domain policy가 계산한 band를 남긴다.
8. 개발자가 특정 언어 evidence를 삭제하거나 Firestore에서 직접 초기화하는 방법을 문서화한다.
9. 세션 종료/화면 이탈 시 `SessionMemory`에 저장된 현재 세션 final turn을 Gemini conversation analysis API로 분석한다.
10. Gemini 분석 결과가 conversation evidence snapshot에 저장되고, 앱은 `debugRecommendedBand`가 아니라 domain policy 계산 결과를 사용하는지 검증한다.
11. 프롬프트 리뷰 신고 버튼은 기존처럼 QA용 `chat_prompt_reviews`/report index 저장만 수행하고, evidence 분석/저장과 결합하지 않는다.
12. Correction 관련 테스트가 영향받지 않는지 확인한다.

## 후속 계획: LangState evidence 통합

Chat band를 안정적으로 나누려면 Correction 결과만으로는 부족하다. Correction은 문장 정확도와 오류 유형을 잘 보지만, Chat에서 필요한 "학습언어만으로 대화가 이어지는가"는 대화 세션 전체에서 관찰해야 한다.

단, 사용자에게 보이는 언어 능력 상태와 통계는 하나의 `LangState`로 유지한다. `ConversationLangState`와 `CorrectionLangState`처럼 상태를 분리하지 않는다.

```text
Conversation Session Analysis
Correction Signal
Flashcard Review
        ↓
LangState Update Policy
        ↓
Single LangState
        ↓
BuildLearnerAdaptationProfileUseCase
        ├─ Chat: ConversationAbilityBand
        └─ Correction: CorrectionGrowthBand
        ↓
Statistics / Prompt / Correction / Dashboard
```

### Source별 역할

| Source | 주로 보는 능력 | 반영 방향 | 보호해야 할 영역 |
| --- | --- | --- | --- |
| `ConversationSession` | 대화 지속, 지원 필요 여부, 사용자가 감당한 응답 난이도, 참여 단위 | 대화 지속 능력과 Chat band 경계 판단에 제한적으로 반영한다. | 문법 정확도, 교정 강도, 복습 유지율을 강하게 흔들지 않는다. |
| `CorrectionSignal` | 문장 정확도, 오류 유형, 문장 구조, 어휘 적절성 | 문장 품질과 Correction band 판단에 반영한다. | 대화 중 말문 막힘이나 세션 흐름을 직접 판단하지 않는다. |
| `FlashcardReview` | 기억 유지, 반복 약점, 복습 성과 | 복습 유지와 반복 약점 판단에 반영한다. | 대화 유창성이나 즉흥 반응 능력을 직접 판단하지 않는다. |

### 통합 원칙

- evidence source는 여러 개일 수 있지만 최종 저장 상태는 하나의 `LangState`다.
- source별로 어떤 능력 판단에 영향을 줄 수 있는지 제한한다.
- `ConversationSession`은 "대화가 이어졌는가"를 중심으로 보며, 문법 정확도를 강하게 바꾸지 않는다.
- `CorrectionSignal`은 "문장이 얼마나 정확하고 재사용 가능한가"를 중심으로 보며, 말문 막힘이나 대화 흐름을 직접 판단하지 않는다.
- `FlashcardReview`는 "기억이 유지되는가"를 중심으로 보며, 대화 유창성을 직접 흔들지 않는다.
- Chat band와 Correction band는 같은 `LangState`를 읽지만, 각 기능에 필요한 metric을 다르게 해석한다.
- 통계 화면은 source별 점수를 따로 보여주지 않고 하나의 언어 성장 흐름으로 유지한다.

### ConversationSession signal 후보

대화 종료 후 비동기로 아래 관찰값을 분석해 `LangState` 업데이트 입력으로 보낸다. 사용자가 종료 화면에서 분석 완료를 기다리게 하지 않는다.

이 값들은 단어 필터나 특정 표현 매칭으로 계산하지 않는다. 사용자가 어떤 단어를 말했는지가 아니라, AI 응답 뒤 대화가 실제로 이어졌는지와 사용자가 감당한 대화 단위를 흐름으로 관찰한다.

| Signal | 의미 | 주 사용처 |
| --- | --- | --- |
| `conversationSustainability` | 대화가 사용자의 반응으로 유지됐는지, 지원이 있어야 유지됐는지, 근거가 부족한지 | Chat band의 기본 경계 |
| `supportRequiredToContinue` | 기준언어 설명, 난이도 하향, 더 짧은 재구성이 있어야 사용자의 반응이 회복됐는지 | `IntentOnly` 방어 |
| `userContributionLevel` | 사용자가 단어, 짧은 구, 짧은 문장, 연결 발화 중 어느 정도 단위로 참여했는지 | 낮은 3단계 구분 |
| `responseDifficultyFit` | AI 응답 난이도가 사용자가 감당 가능한 수준이었는지 | prompt/band mismatch 감지 |

### 제외한 signal

아래 후보는 오분류 위험이 커서 1차 설계에서 제외한다.

| 제외 후보 | 제외 이유 |
| --- | --- |
| `targetLanguageUseRatio` | 사용자가 기준언어를 쓰는 이유가 다양하므로 핵심 능력 지표로 부적절하다. |
| `primaryLanguageDependency` | 기준언어 사용량이 아니라 "기준언어나 난이도 보조 없이는 대화가 이어지지 않았는가"를 봐야 한다. |
| `conversationBreakCount` | 단절을 숫자로 직접 세면 세션 길이, 피로도, 음성 인식 문제에 과하게 흔들릴 수 있다. |
| `targetOnlyComprehensionFailure` | 실패 원인을 target-only 응답으로 단정할 위험이 있다. |
| `freeSentenceEvidence` | 자유 문장 여부를 앱 로직이 직접 판별하면 언어별/음성 transcript별 오분류 위험이 크다. |
| `fixedPhraseOnlyEvidence` | 고정 표현 탐지가 단어 목록 기반 필터로 흐를 위험이 있다. |

### 분석 시점과 사용자 흐름

- 세션 종료 또는 Chat 화면 이탈 시 분석을 요청한다.
- 분석은 비동기로 실행하고 사용자가 결과를 기다리게 하지 않는다.
- 분석 실패는 대화 저장, 화면 이동, 교정, 플래시카드 흐름을 막지 않는다.
- 같은 세션이 중복 분석되지 않도록 `sessionId` 기준 idempotency를 둔다.
- 신고 세션은 prompt review/debug에는 사용하지만, 능력 측정의 기본 트리거로 삼지 않는다.

후속 LangState 통합 단계의 권장 흐름:

```text
Chat session ended or screen left
        ↓
session turns already saved
        ↓
background conversation analysis requested
        ↓
analysis result converted to LangState update input
        ↓
LangState updated if safe
```

위 흐름은 1차 Chat conversation evidence snapshot 구현 범위가 아니다. 1차에서는 분석 결과를 `chat_conversation_evidence/{selectedLang}`에 저장하고, LangState update input으로 변환하지 않는다.

### Correction 영향 보호

대화 세션 evidence를 추가하면 같은 `LangState`를 읽는 Correction에도 간접 영향이 생길 수 있다. 따라서 Conversation source 도입 시 아래 회귀를 반드시 확인한다.

- 기존 Correction prompt 생성 테스트가 깨지지 않는다.
- 기존 `CorrectionGrowthBand` 산출 테스트가 깨지지 않는다.
- Conversation source만으로 `CorrectionGrowthBand`가 부당하게 상향되지 않는다.
- Conversation source만으로 문법 교정 강도가 갑자기 낮아지지 않는다.
- Correction 담당자가 사용하는 `CorrectionSignal` 입력 계약을 변경하지 않는다.
- 대화세션 분석 결과는 Correction flow에서 직접 사용하지 않는다.

### 구현 순서

1. 현재 LangState update 경로와 `MetricEvidence.sourceTypes` 사용 방식을 조사한다.
2. `ConversationSession` source가 기존 enum 또는 계약에 이미 있는지 확인한다.
3. 없으면 `LearningSignalSource.ConversationSession` 추가 여부를 별도 작업으로 결정한다.
4. ConversationSession 분석 결과를 LangState update input으로만 넘길지, review/debug용 snapshot도 남길지 결정한다.
5. source별 metric 반영 가중치와 confidence 계산 기준을 문서화한다.
6. `LangStateUpdatePolicy` 또는 관련 UseCase에 Conversation source 반영을 추가한다.
7. Chat band 테스트에 `IntentOnly`, `PhraseEmerging`, `SimpleSentence` 경계 fixture를 보강한다.
8. Correction 회귀 테스트를 함께 실행한다.
9. 실제 신고 세션에서 band가 prompt trace에 기대대로 찍히는지 확인한다.

## 검증 기준

- 일본어처럼 사용자가 실제로 거의 모르는 언어는 단어 몇 개 근거만으로 `PhraseEmerging` 이상으로 올라가지 않는다.
- `IntentOnly`에서 AI는 기준언어와 학습언어를 짧게 섞어 대화를 리드한다.
- `PhraseEmerging`에서 AI는 학습언어-only 긴 응답을 하지 않는다.
- `SimpleSentence` 이상에서도 Chat은 사용자를 고쳐주지 않고 자연스러운 표현을 자기 발화 안에서 들려준다.
- Prompt에는 raw metric, 내부 enum, 나이 비유, 실패 예시, 단어 필터가 들어가지 않는다.
- Conversation source가 추가되어도 사용자에게 보이는 통계는 하나의 `LangState` 성장 흐름으로 유지된다.
- Conversation source가 추가되어도 기존 Correction 흐름과 `CorrectionGrowthBand` 산출이 오염되지 않는다.
