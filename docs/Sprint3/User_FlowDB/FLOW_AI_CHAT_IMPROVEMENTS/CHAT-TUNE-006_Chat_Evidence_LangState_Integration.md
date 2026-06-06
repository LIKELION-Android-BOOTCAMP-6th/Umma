# CHAT-TUNE-006 Chat Evidence LangState Integration

## 배경

`CHAT-TUNE-005`에서는 Chat prompt 구조를 6단계 `ConversationAbilityBand` 중심으로 단순화하고, 세션 종료 후 Gemini가 만든 `ChatConversationEvidence`를 별도 snapshot으로 저장했다.

현재 snapshot은 Chat 시작 prompt/profile 계산에만 쓰인다. `LangState`, Correction, Statistics에는 직접 반영하지 않는다.

이 구조는 프롬프트 테스트를 빠르게 반복하기에는 충분하지만, 장기적으로는 사용자의 대화 능력이 공식 언어 상태에도 누적되어야 한다. 단, 이 통합이 Correction 담당자의 입력 계약이나 교정 강도 산출을 흔들면 안 된다.

## 목표

- Chat 세션 분석 결과를 공식 `LangState` 업데이트 경로에 안전하게 연결한다.
- `ChatSession` source를 `CorrectionSignal`과 분리해 저장 근거를 추적한다.
- Chat evidence가 Chat band 판단에는 도움이 되되, Correction band와 교정 강도를 부당하게 흔들지 않게 한다.
- 공식 `LangState`에 반영되는 순간부터 local-first + pending retry 저장 정책을 따른다.
- 1차 snapshot 저장소는 후속 통합 전환 중에도 debug/review 보조 자료로 유지할 수 있게 한다.

## 범위

### 포함

- `ChatSignalUpdateInput` 또는 동등한 Chat 전용 LangState update input 설계
- `LearningSignalSource.ChatSession` 추가 검토 및 적용
- `ChatConversationEvidence`를 `LangState.analysisMeta.metricEvidence`로 변환하는 policy 설계
- source별 metric 영향 범위와 보수적 가중치 정의
- `LearningStateRepo.updateLanguageState`를 통한 local-first + pending sync 경로 연결
- Chat band / Correction band 회귀 테스트 추가

### 제외

- Correction AI prompt 또는 `CorrectionLearningSignal` 계약 변경
- `CompleteCorrectionUseCase`의 저장 순서 변경
- Statistics 화면 지표 재설계
- Prompt review 신고 도구의 저장 구조 변경
- 회원탈퇴 Cloud Function cleanup 구현
- OpenAI Realtime 대화 생성 로직 변경

## 현재 상태

| 항목 | 현재 상태 |
| --- | --- |
| Chat evidence 저장 | Firestore `users/{uid}/chat_conversation_evidence/{selectedLang}`에 직접 저장 |
| snapshot 저장 실패 처리 | 실패 시 사용자 흐름을 막지 않고 `LangState` update는 별도로 시도 |
| Chat 시작 반영 | `StartSessionUseCase`가 snapshot을 읽고 `ApplyChatConversationEvidenceUseCase`로 세션용 profile 계산 |
| LangState 반영 | `ApplyChatSignalUpdateUseCase`가 `analysisMeta.metricEvidence`에 `ChatSession` source로 누적 |
| Correction 반영 | 직접 반영하지 않음. Chat-only evidence는 Correction band 계산에서 제외 |
| Statistics 반영 | 직접 반영하지 않음. `ExternalMetrics`, `DashSummary`, `SessionSummary`를 보존 |
| pending analysis | 분석 전 local pending job에 최소 final turn snapshot을 저장하고, 다음 Chat 진입 시 재시도 |
| debugRecommendedBand | 저장/검토용이며 앱이 직접 적용하지 않음 |

## 핵심 결정

### 이번 작업의 최종 결정

- `LangState` 최상위 필드는 추가하지 않는다.
- `LearningSignalSource.ChatSession`을 추가해 Chat 대화 세션 분석 source를 분리한다.
- `ChatSignalUpdateInput` 또는 동등한 Chat 전용 update input을 둔다.
- `confidence=Low` chat evidence는 공식 `LangState` metric을 움직이지 않는다.
- MVP에서는 `confidence=Medium`/`High`도 `analysisMeta.metricEvidence` 중심으로 누적한다.
- MVP에서는 `ExternalMetrics`를 직접 변경하지 않는다.
- MVP에서는 `InternalMetrics`도 직접 변경하지 않는다.
- MVP에서는 `InternalMetrics`를 Chat용/Correction용으로 분리하지 않는다.
- `analysisEventId`는 `chat-session:{sourceSessionId}` 형식을 사용한다.
- `LangStateAnalysisMeta.lastChatAnalysisEventId`를 추가해 Chat source 중복 반영을 Correction의 `lastAnalysisEventId`와 분리한다.
- Chat update는 공용 `LangState.lastAnalysisEventId`를 덮지 않고, 기존 Correction 중복 방어 event id를 보존한다.
- `chat_conversation_evidence` snapshot 저장과 `LangState` update는 서로 의존하지 않는 best-effort 흐름으로 분리한다.
- `chat_conversation_evidence` snapshot은 공식 상태가 아니라 debug/review용 보조 저장소로 유지한다.
- 저장된 Chat evidence를 다음 Chat band 계산에 반영하는 작업은 후속 단계로 분리한다.
- Chat evidence update는 `DashSummary`와 `SessionSummary`를 갱신하지 않고 기존 값을 보존한다.
- Chat 세션 분석은 실행 전에 local pending job을 먼저 남기고, LangState 반영 성공 후 job을 제거한다.

### Single LangState 유지

`ConversationLangState`와 `CorrectionLangState`처럼 상태를 분리하지 않는다. 최종 사용자 상태와 통계 흐름은 하나의 `LangState`로 유지한다.

```text
ChatSession
CorrectionSignal
FlashcardReview
        ↓
LangState update policy
        ↓
Single LangState
        ↓
BuildLearnerAdaptationProfileUseCase
        ├─ Chat: ConversationAbilityBand
        └─ Correction: CorrectionGrowthBand
```

### Chat 전용 update input을 둔다

Chat evidence를 기존 `CorrectionResult`에 섞지 않는다. `ChatSignalUpdateInput` 같은 별도 입력 계약을 둔 뒤, 내부에서 `LangState` prepared state를 계산해 기존 저장 경로로 넘긴다.

권장 흐름:

```text
ChatConversationEvidence
        ↓
ChatSignalUpdateInput
        ↓
ApplyChatSignalUpdateUseCase
        ↓
LangStateAnalysisPolicy 또는 Chat 전용 policy
        ↓
LearningStateRepo.updateLanguageState(preparedState)
```

이렇게 해야 Correction 담당자가 사용하는 `CorrectionLearningSignal` 계약과 `CompleteCorrectionUseCase` 흐름을 바꾸지 않는다.

### Summary 갱신 책임 분리

기존 `LearningStateRepo.updateLanguageState`는 Correction batch update를 기준으로 `LangState`, `DashSummary`, `SessionSummary`를 함께 저장한다. Correction 완료 후에는 언어 상태, 대시보드 delta, 교정 가능 상태, 최근 대화 요약이 함께 바뀌어야 하므로 이 계약은 유지한다.

Chat evidence update는 같은 저장 경로를 재사용하되 summary 갱신은 하지 않는다. `CHAT-TUNE-006` 1차에서 Chat source는 `analysisMeta.metricEvidence` 누적만 담당하고 `InternalMetrics` / `ExternalMetrics`를 직접 변경하지 않기 때문이다.

정책:

- Correction update는 summary를 기존처럼 재계산한다.
- Chat evidence update는 기존 `DashSummary` / `SessionSummary`를 보존한다.
- Chat evidence update의 pending sync 대상은 `LangState`만 포함한다.
- `recentMinutes`, `recentTopic`, `correctionAvailable`, summary `updatedAt`은 Chat evidence 저장 때문에 바뀌지 않는다.

이 분리를 두지 않으면 Chat 분석용 turn payload의 duration이나 user turn 존재 여부가 대시보드의 최근 대화 시간과 교정 가능 상태를 덮어쓸 수 있다. Dashboard는 `recentFullContext`를 직접 계산하지 않고 `DashSummary`만 렌더링하므로, Chat evidence 저장이 Summary를 오염시키면 화면 의미가 흔들린다.

### ChatSession source를 분리한다

`LearningSignalSource.CorrectionSignal`에 Chat 세션 분석 결과를 섞지 않는다. `LearningSignalSource.ChatSession`을 추가해 metric evidence의 출처를 분리한다.

```kotlin
enum class LearningSignalSource {
    UserTurn,
    CorrectionSignal,
    ChatSession,
    ReviewEvent,
    TypeBRule,
    TypeCAi
}
```

이 source는 저장 근거 추적용이며, 자체로 점수 상승을 의미하지 않는다. 실제 영향은 policy에서 제한한다.

### Chat 분석 중복 방어 필드

기존 `LangState.lastAnalysisEventId`는 Correction 등 여러 분석 source가 함께 사용하는 마지막 분석 이벤트 ID다. Chat 세션 분석은 중간에 Correction 분석이 끼어들어도 같은 Chat 세션을 다시 반영하지 않도록 별도 필드를 둔다.

권장 위치:

```kotlin
data class LangStateAnalysisMeta(
    val metricEvidence: Map<LearningMetricKey, MetricEvidence>,
    val activeFocus: List<LearningFocus>,
    val lastSignalAt: Long? = null,
    val lastChatAnalysisEventId: String? = null
)
```

역할:

- `lastAnalysisEventId`는 기존 LangState 분석 이벤트 호환성을 위해 유지한다.
- `lastChatAnalysisEventId`는 Chat source의 idempotency 전용이다.
- Chat update의 `analysisEventId`는 `chat-session:{sourceSessionId}` 형식으로 만든다.
- 같은 `lastChatAnalysisEventId`가 다시 들어오면 Chat evidence를 중복 반영하지 않는다.
- Chat update는 `lastAnalysisEventId`를 `chat-session:*` 값으로 덮지 않는다. 이 값을 덮으면 Correction 완료 재시도가 기존 correction event id를 잃어 중복 반영될 수 있다.

### External/Internal metric 의미

`InternalMetrics`는 앱 내부가 사용자의 언어 능력을 해석하는 세부 계산 재료다. 예를 들어 `grammarAccuracy`, `vocabularyAppropriateness`, `lexicalDiversity`, `speechRate`, `pauseFrequency`, `spokenNaturalness` 등이 여기에 해당한다.

`ExternalMetrics`는 Dashboard/Statistics처럼 사용자에게 노출될 수 있는 요약 지표다. 예를 들어 `vocabularyLevel`, `grammarAccuracy`, `expressionRange`, `fluencyScore`, `naturalnessScore`가 여기에 해당한다.

MVP에서 Chat source는 사용자 화면 점수를 직접 바꾸지 않는다. Chat 세션 한 번의 Gemini 분석이 화면의 문법 점수나 유창성 점수를 즉시 바꾸면 Correction/Statistics 흐름이 오염될 수 있기 때문이다.

### Chat/Internal 분리 보류

`InternalMetrics`를 `chatInternalMetrics`와 `correctionInternalMetrics`로 나누는 방식은 장기적으로 검토할 수 있다. 이 구조는 Chat과 Correction의 지표 오염을 더 강하게 막을 수 있지만, 1차 작업 범위를 크게 키운다.

보류 이유:

- `LangState`, DTO, Firestore schema, mapper를 함께 바꿔야 한다.
- 기존 사용자 데이터에 새 internal metric 묶음이 없으므로 복원/migration 방어가 필요하다.
- Correction update policy가 기존 `InternalMetrics` 대신 correction 전용 metric만 갱신하도록 바뀌어야 한다.
- Chat update policy도 chat 전용 metric 갱신 정책을 별도로 가져야 한다.
- Statistics/Dashboard는 Chat metric과 Correction metric을 어떤 비율로 합쳐 사용자에게 보여줄지 새 정책이 필요하다.
- Profile builder는 Chat 시작, Correction 강도, Statistics 표시를 서로 다른 가중치로 해석해야 한다.

따라서 `CHAT-TUNE-006` 1차에서는 internal metric 구조를 나누지 않는다. 대신 `analysisMeta.metricEvidence.sourceTypes`에 `LearningSignalSource.ChatSession`을 남겨, Chat evidence의 출처만 분리한다.

후속 작업에서 Chat evidence를 실제 Chat band 계산에 반영할 때, source 분리만으로 충분하지 않다고 확인되면 `chatInternalMetrics` / `correctionInternalMetrics` 분리를 별도 이슈로 검토한다.

## Chat evidence 반영 원칙

Chat source는 사용자의 문장 정확도를 평가하는 source가 아니다. 대화가 학습언어로 얼마나 유지됐는지, 기준언어/AI scaffold 없이도 감당 가능한지 보는 source다.

| Evidence | 주요 의미 | 반영 방향 |
| --- | --- | --- |
| `targetLanguageComprehension` | AI의 학습언어 발화를 이해하고 반응했는지 | 이해 가능한 Chat prompt 난이도와 fluency guard에 반영 |
| `targetLanguageProduction` | 학습언어로 만든 단어, 구, 문장, 연결 발화 단위 | 낮은 Chat band 경계 판단용 evidence로 저장 |
| `supportLanguageDependence` | 기준언어 없이는 대화가 끊기는지 | Chat band 상향 제한, confidence 하향 |
| `aiScaffoldingDependence` | AI 힌트/선택지/쉬운 재구성 의존도 | Chat band 상향 제한, confidence 하향 |
| `conversationSustainability` | 학습언어 반응만으로 대화가 유지됐는지 | Chat band 핵심 경계 |
| `consistency` | 세션 전체에서 능력 단서가 안정적인지 | evidence confidence와 directionCount 보정 |
| `responseDifficultyFit` | AI 난이도가 사용자에게 맞았는지 | prompt 난이도 mismatch 감지, 급상승 방지 |

## Metric 영향 범위

MVP에서는 Chat source가 `LangState` 점수를 크게 성장시키는 입력이 아니라, Chat band의 과대/과소 판단을 안정화하는 보조 evidence로 동작한다.

| Metric 영역 | Chat source 영향 |
| --- | --- |
| `fluencyScore` | MVP에서는 직접 변경하지 않음. 대화 지속 근거는 `analysisMeta.metricEvidence`에만 남김 |
| `vocabularyLevel` | MVP에서는 직접 변경하지 않음. 단어 몇 개나 따라 말한 표현만으로 CEFR 등급 상승 금지 |
| `naturalnessScore` | MVP에서는 직접 변경하지 않음. 자연스러운 대화처럼 보이더라도 AI scaffold 영향이 클 수 있음 |
| `grammarAccuracy` | 직접 상승 금지. 문법 정확도는 Correction source가 우선 판단 |
| `expressionRange` 또는 유사 내부 지표 | MVP에서는 직접 변경하지 않음. 연결 발화 근거는 `analysisMeta.metricEvidence`에만 남김 |
| `analysisMeta.metricEvidence` | Chat source의 주 저장 위치. 초기 구현은 이곳 중심으로 누적 |

MVP 확정 기준:

- `ExternalMetrics`는 직접 변경하지 않는다.
- `InternalMetrics`도 직접 변경하지 않는 것을 기본값으로 둔다.
- Chat source는 먼저 `analysisMeta.metricEvidence`에 source와 방향 근거를 남기는 방식으로 시작한다.
- 저장된 Chat source evidence를 다음 Chat band 계산에 반영하는 작업은 후속 단계로 분리한다.
- 이후 실제 테스트에서 source 분리만으로 부족하면, `InternalMetrics` 분리 또는 일부 fluency 계열 지표 반영을 별도 이슈에서 결정한다.

### 보수적 반영 규칙

- `supportLanguageDependence=High`이면 Chat band 상향 근거로 쓰지 않는다.
- `aiScaffoldingDependence=High`이면 사용자의 독립 대화 능력으로 과대평가하지 않는다.
- `confidence=Low` evidence는 공식 LangState metric을 움직이지 않고 debug/snapshot으로만 보관한다.
- `conversationSustainability=RequiresSupport`이면 `IntentOnly` 보호 근거로만 사용한다.
- 단일 세션의 한두 문장 성공만으로 `SimpleSentence` 이상 근거를 만들지 않는다.
- Chat source만으로 `CorrectionGrowthBand`가 올라가면 안 된다.

### LangState evidence 저장 제외 조건

아래 조건에 해당하면 `analysisMeta.metricEvidence`에 Chat source 근거를 추가하지 않는다. snapshot은 debug/review용으로 저장할 수 있지만, 공식 LangState 근거 누적에는 쓰지 않는다.

- `confidence=Low`
- `supportLanguageDependence=High`
- `aiScaffoldingDependence=High`
- `consistency=Low`
- `responseDifficultyFit=TooHard`
- `targetLanguageProduction=None`
- `targetLanguageComprehension=None`

위 조건을 통과한 `confidence=Medium`/`High` evidence만 제한된 key 매핑에 따라 `MetricEvidence` 후보로 변환한다.

### Chat evidence 저장 key 정책

`CHAT-TUNE-006` 1차에서는 Chat 전용 `LearningMetricKey`를 추가하지 않는다. Chat 전용 key를 많이 만들면 LangState를 하나로 유지하는 의미가 약해지고, Chat/Correction/Statistics가 각자 다른 상태 저장소처럼 동작할 수 있기 때문이다.

대신 기존 key 중 Chat evidence와 자연스럽게 연결되는 일부 key에만 `sourceTypes=ChatSession` 근거를 남긴다.

권장 매핑:

| Chat evidence | 저장 후보 key | 방향 |
| --- | --- | --- |
| `targetLanguageProduction` | `AvgUtteranceLength` | 학습언어 발화가 단어/구에서 문장/연결 발화로 안정되면 `Up`, 근거 부족이면 저장하지 않음 |
| `targetLanguageProduction` | `SentenceComplexity` | 짧은 자유 문장 이상이 안정적으로 반복될 때만 `Up`, 조각/구 중심이면 `Stable` 또는 저장하지 않음 |
| `conversationSustainability` | `AvgUtteranceLength` | 사용자의 학습언어 반응으로 대화가 유지되면 `Up`, 보조 없이는 끊기면 `Stable` 또는 저장하지 않음 |
| `responseDifficultyFit` | `SpokenNaturalness` | 난이도가 맞고 자연스러운 구어 흐름이 유지될 때만 약하게 `Up` |
| `targetLanguageComprehension` | `SentenceComplexity` | 쉬운 문장 이해가 안정적일 때만 약하게 `Up` |

직접 저장 금지:

- `GrammarAccuracy`: 문법 정확도는 Correction source가 판단한다.
- `ReviewRetention`: SRS/Flashcard source가 판단한다.
- `ErrorRecurrence`: 교정/복습 흐름의 반복 오류 source가 판단한다.
- `VocabularyLevel`: 단일 Chat 세션의 표현 몇 개로 CEFR 등급을 올리지 않는다.

이 매핑은 점수를 즉시 바꾸기 위한 것이 아니라, `analysisMeta.metricEvidence`에 Chat source 근거를 남기기 위한 1차 정책이다.

### Chat evidence merge 정책

기존 `LangStateAnalysisPolicy`의 evidence merge는 Correction source를 기준으로 작성되어 있다. Chat source를 추가할 때는 source를 하드코딩하지 않고, merge 함수가 `LearningSignalSource`를 입력으로 받도록 확장한다.

권장 방향:

```kotlin
mergeEvidence(
    previous = previous,
    direction = direction,
    confidence = confidence,
    source = LearningSignalSource.ChatSession,
    observedAt = observedAt
)
```

정책:

- Correction 경로는 기존처럼 `LearningSignalSource.CorrectionSignal`을 넘긴다.
- Chat 경로는 `LearningSignalSource.ChatSession`을 넘긴다.
- 같은 metric에 Correction과 Chat 근거가 모두 있으면 `sourceTypes`에 둘 다 남긴다.
- `observedCount`는 유효한 Chat evidence가 반영될 때만 증가한다.
- `confidence=Low`는 `observedCount`를 증가시키지 않는다.
- 방향이 충돌하면 기존 `Mixed` 처리 원칙을 유지한다.
- 같은 Chat 세션의 중복 반영은 `lastChatAnalysisEventId`로 막는다.

이렇게 하면 기존 Correction merge 정책을 재사용하되, Chat source가 Correction source로 잘못 저장되는 문제를 막을 수 있다.

### 다음 Chat band 반영은 후속 작업

`CHAT-TUNE-006` 1차는 Chat evidence를 공식 LangState에 안전하게 누적하는 작업까지만 포함한다. 저장된 `LearningSignalSource.ChatSession` evidence를 `BuildLearnerAdaptationProfileUseCase`가 읽어 다음 Chat band 계산에 반영하는 작업은 후속 단계로 분리한다.

후속 단계의 원칙:

- Chat band 계산은 `sourceTypes`에 `ChatSession`이 포함된 evidence를 우선 참고할 수 있다.
- Correction band 계산은 기존 Correction 지표 중심으로 유지한다.
- Chat evidence만으로 `CorrectionGrowthBand`를 올리지 않는다.
- Chat evidence만으로 교정 강도를 낮추지 않는다.
- Correction score가 좋아도 Chat evidence가 낮으면 Chat band는 보수적으로 유지할 수 있다.

## Local-first + pending retry 원칙

현재 `chat_conversation_evidence` snapshot은 Firestore 직접 저장이다. 공식 `LangState`에 반영하는 순간부터는 기존 LearningState 저장 정책을 따라야 한다.

권장 저장 흐름:

```text
Chat analysis completed
        ↓
ChatSignalUpdateInput 생성
        ↓
prepared LangState 계산
        ↓
local snapshot 먼저 반영
        ↓
pending marker 기록
        ↓
Firestore write-back/retry
```

저장 실패 정책:

- 사용자 Chat 종료 흐름을 막지 않는다.
- 로컬에 반영된 공식 `LangState` 변경은 pending sync로 재시도한다.
- 같은 `sourceSessionId`는 중복 반영하지 않는다.
- 분석 대상 final turn snapshot이 pending job에 저장된 뒤 앱이 종료되면, 다음 Chat 진입 시 Gemini 분석부터 재시도할 수 있다.

### Snapshot 저장과 LangState update 분리

Gemini 분석이 성공하면 두 흐름을 서로 의존시키지 않고 각각 best-effort로 수행한다.

```text
Gemini conversation analysis completed
        ├─ chat_conversation_evidence snapshot 저장 시도
        └─ ChatSignalUpdateInput 기반 LangState update 시도
```

분리 이유:

- snapshot은 debug/review용 보조 저장소다.
- 공식 사용자 언어 상태의 source of truth는 `LangState`다.
- snapshot 저장 실패가 공식 `LangState` update를 막으면 안 된다.
- `LangState` update 실패가 snapshot 저장을 막을 필요도 없다.
- 두 실패는 로그에서 분리해 원인을 추적한다.

### Pending analysis queue

Chat evidence가 공식 `LangState` 근거로 반영되므로, 앱 강제 종료나 네트워크 실패 때문에 Gemini 분석 자체가 유실되면 안 된다.

정책:

- Chat 세션 종료/화면 이탈 시 분석을 시작하기 전에 local pending job을 먼저 저장한다.
- pending job에는 Gemini 결과를 저장하지 않는다.
- pending job에는 `userId`, `selectedLang`, `sessionId`, `finalTurnCount`와 Gemini 분석에 필요한 최소 final turn snapshot을 저장한다.
- turn snapshot은 `speaker`, `text`, `createdAt`, `tokenCount`, `durationMs`, `confidence`만 포함한다.
- retry 시에는 `SessionMemory`를 다시 읽지 않고 pending job의 turn snapshot으로 분석 payload를 만든다.
- 따라서 Correction이 `SessionMemory.recentFullContext`를 압축/초기화해도 pending Chat 분석을 재시도할 수 있다.
- Gemini 분석과 LangState update가 끝나면 pending job을 제거한다.
- snapshot 저장 실패는 debug 저장 실패이므로 pending job 완료 기준이 아니다.
- LangState update 실패 또는 Gemini 분석 실패는 pending job을 남겨 다음 Chat 진입 시 재시도한다.
- 계정 전환 후 다른 사용자의 pending job이 실행되지 않도록 `userId` 기준으로 필터링한다.
- 로그아웃/회원탈퇴 로컬 정리 시 pending job도 함께 삭제한다.

이 구조는 사용자 화면 이탈을 기다리게 하지 않으면서도, 공식 LangState 반영 대상이 되는 Chat 분석의 유실 가능성을 줄인다.

### UseCase 책임 경계

`AnalyzeChatConversationSessionUseCase`는 Chat 세션 종료 후 Gemini 분석을 실행하고, 분석 성공 시 두 best-effort 후속 작업을 조율한다.

```text
AnalyzeChatConversationSessionUseCase
        ├─ ChatConversationEvidenceRepository.saveEvidence
        └─ ApplyChatSignalUpdateUseCase
```

책임:

- `AnalyzeChatConversationSessionUseCase`: 세션 turn 조회, Gemini 분석 요청, snapshot 저장과 LangState update 호출 조율
- `ApplyChatSignalUpdateUseCase`: `ChatSignalUpdateInput` 검증, `MetricEvidence` 후보 계산, prepared `LangState` 생성, `LearningStateRepo.updateLanguageState` 호출
- `EnqueueChatConversationAnalysisJobUseCase`: 분석 시작 전 최소 final turn snapshot job을 local pending queue에 저장
- `SyncPendingChatConversationAnalysisJobsUseCase`: 다음 Chat 진입 시 pending job을 재시도하고 LangState 반영 성공 후 제거
- `ChatViewModel`: 세션 종료/화면 이탈 시 분석 예약만 담당하고, evidence 해석이나 LangState 계산을 하지 않음
- `LearningStateRepo`: 계산 정책을 해석하지 않고 prepared state를 local-first/pending sync 경로로 저장

이 경계를 지켜야 presentation 계층이 Chat evidence 정책을 알지 않고, Correction 흐름도 기존 계약을 유지할 수 있다.

### Snapshot 유지 정책

`chat_conversation_evidence` snapshot은 최종 제품의 공식 상태 저장소가 아니라, 현재 개발/검증 단계의 관찰성 확보용 저장소다.

유지 이유:

- Gemini가 어떤 evidence를 산출했는지 Firestore에서 바로 확인할 수 있다.
- `confidence`, `supportLanguageDependence`, `debugRecommendedBand`, `reasonSummary`를 사람이 검토할 수 있다.
- LangState 반영 결과가 이상할 때 Gemini 평가 문제인지 LangState 반영 policy 문제인지 분리할 수 있다.
- 프롬프트 개선과 신고 세션 분석 중 원인 추적 비용을 줄인다.

향후 안정화 후 선택지:

- snapshot 저장을 제거하고 `LangState` update만 유지한다.
- 또는 `devDebug`/debug build 전용으로만 snapshot 저장을 남긴다.

운영/테스터 흐름 보호 조건:

- snapshot 저장은 best-effort여야 한다.
- snapshot 저장 실패는 화면 이탈, 대화 종료, 다음 세션 시작을 막지 않는다.
- snapshot은 사용자 UI, Correction, Statistics 공식 흐름에서 읽지 않는다.
- 필요 시 build config/feature flag로 snapshot 저장을 끌 수 있어야 한다.

## Correction 영향 보호

Chat source 통합 시 아래 조건을 반드시 지킨다.

- `CorrectionLearningSignalModels`를 변경하지 않는다.
- `CompleteCorrectionUseCase`의 Flashcard 저장, LangState 업데이트, Summary 업데이트 순서를 변경하지 않는다.
- `CorrectionResult.learningSignals`에 Chat evidence를 섞지 않는다.
- Chat source만으로 `CorrectionGrowthBand`가 상향되지 않는다.
- Chat source만으로 문법 교정 강도가 낮아지지 않는다.
- 기존 Correction prompt 생성 테스트와 CorrectionGrowthPolicy 테스트가 통과해야 한다.

## 구현 계획

### 1. 현재 LearningState update 경로 조사

- `LearningSignalSource` 저장/복원 DTO 경로 확인
- `LangStateAnalysisPolicy`가 `CorrectionSignal` source를 어디에 기록하는지 확인
- `LearningStateRepo.updateLanguageState`의 local-first/pending sync 경계 확인
- Correction 완료 흐름의 `ApplyLanguageStateUpdateUseCase` 호출 경계 확인

### 2. Chat source 모델 추가

- `LearningSignalSource.ChatSession` 추가
- `LangStateAnalysisMeta.lastChatAnalysisEventId` 추가
- DTO enum 복원 방어가 unknown source를 안전하게 무시하는지 확인
- 기존 데이터가 source/idempotency 필드 추가 후에도 복원되는지 테스트

### 3. Chat update input 추가

예상 모델:

```kotlin
data class ChatSignalUpdateInput(
    val uid: String,
    val lang: LangCode,
    val sessionMemoryKey: String,
    val sourceSessionId: String,
    val evidence: ChatConversationEvidence,
    val analyzedAt: Long,
    val forceReanalysis: Boolean = false
)
```

검증:

- `uid`, `sessionMemoryKey`, `sourceSessionId`는 비어 있으면 실패
- `evidence.selectedLang`와 `lang`이 다르면 실패
- `confidence=Low`는 공식 metric 반영 없이 skip 또는 metadata only 처리
- 같은 `sourceSessionId` 재시도는 `lastChatAnalysisEventId`로 idempotent 해야 함
- `analysisEventId`는 `chat-session:{sourceSessionId}` 형식으로 만든다.

### 4. Chat evidence policy 작성

- `ChatConversationEvidence`를 `MetricEvidence` 변화 후보로 변환
- 초기 구현은 `analysisMeta.metricEvidence` 중심으로 저장
- MVP에서는 `ExternalMetrics`를 직접 변경하지 않는다.
- MVP에서는 `InternalMetrics`도 직접 변경하지 않는다.
- `grammarAccuracy` 직접 상승은 금지
- `CorrectionGrowthBand` 계산에 영향을 주지 않는지 테스트로 방어

### 5. UseCase 연결

- `ApplyChatSignalUpdateUseCase` 추가
- `AnalyzeChatConversationSessionUseCase`에서 Gemini 분석 성공 후 snapshot 저장과 LangState update를 각각 best-effort로 요청
- Chat evidence update는 `LangState`만 pending sync 대상으로 남기고 `DashSummary` / `SessionSummary`는 보존
- 분석 시작 전 pending job 저장, 다음 Chat 진입 시 pending job retry 연결
- 실패해도 Chat 종료, 화면 이동, Correction 진입을 막지 않음
- snapshot 저장과 LangState update의 실패를 분리해 로그로 남김

### 6. 테스트 추가

- `LearningSignalSource.ChatSession` DTO round-trip
- `LangStateAnalysisMeta.lastChatAnalysisEventId` DTO round-trip
- `ChatSignalUpdateInput` validation
- 같은 `sourceSessionId`가 다시 들어오면 `lastChatAnalysisEventId` 기준으로 skip되는지
- `confidence=Low` evidence가 metric을 움직이지 않는지
- `confidence=Medium`/`High` evidence가 MVP에서 `ExternalMetrics`를 직접 움직이지 않는지
- `supportLanguageDependence=High` evidence가 `IntentOnly` 보호 근거로만 작동하는지
- Chat source만으로 `CorrectionGrowthBand`가 올라가지 않는지
- 기존 Correction flow 테스트 회귀 확인
- ChatViewModel이 evidence 해석 없이 분석 예약만 담당하는지

## Acceptance Criteria

- Chat evidence를 LangState update 경로에 넣어도 Correction 입력 계약은 변경되지 않는다.
- `LearningSignalSource.ChatSession`이 `metricEvidence.sourceTypes`에 저장/복원될 수 있다.
- `LangStateAnalysisMeta.lastChatAnalysisEventId`가 저장/복원되고 Chat source 중복 반영을 막을 수 있다.
- `confidence=Low` chat evidence는 공식 LangState metric을 움직이지 않는다.
- MVP에서 chat evidence는 `ExternalMetrics`를 직접 변경하지 않는다.
- MVP에서 chat evidence는 `analysisMeta.metricEvidence` 중심으로 누적된다.
- `supportLanguageDependence=High` 또는 `aiScaffoldingDependence=High` evidence는 Chat band 상향 근거로 사용되지 않는다.
- Chat source만으로 `CorrectionGrowthBand`가 부당하게 상향되지 않는다.
- Chat source만으로 `grammarAccuracy`가 직접 상승하지 않는다.
- LangState에 반영되는 chat update는 local-first + pending retry 경로를 따른다.
- Chat evidence update는 기존 `DashSummary`와 `SessionSummary`를 변경하지 않는다.
- Chat 세션 분석 pending job은 앱 강제 종료 후 다음 Chat 진입에서 재시도될 수 있다.
- 같은 `sourceSessionId`는 중복 반영되지 않는다.
- Chat 종료/화면 이탈 흐름은 Gemini 분석 또는 LangState update 실패 때문에 막히지 않는다.
- `chat_conversation_evidence` snapshot은 debug/review용으로 유지되며 공식 상태 흐름을 막지 않는다.

## 검증 기준

- `git diff --check`
- `:app:testDevDebugUnitTest --tests com.app.umma.domain.usecase.chat.ApplyChatConversationEvidenceUseCaseTest`
- `:app:testDevDebugUnitTest --tests com.app.umma.domain.usecase.learningstate.*`
- 관련 Correction usecase/policy unit test
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`

## 미결정 사항

- snapshot 저장 성공 후 LangState update 실패 시 사용자/개발자 로그 형식
- 회원탈퇴 Cloud Function cleanup에서 `chat_conversation_evidence` 원격 snapshot을 삭제할 책임 위치
