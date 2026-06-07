# CHAT-TUNE-007 Chat Evidence Summary Band Source

## 배경

`CHAT-TUNE-006`에서는 Chat 세션 종료 후 Gemini가 만든 `ChatConversationEvidence`를 공식 `LangState` update 경로에 연결했다.

그 과정에서 Chat evidence 일부를 `analysisMeta.metricEvidence`에 `LearningSignalSource.ChatSession` source로 저장했다. 이 방식은 Chat 분석 결과가 LangState에 누적된다는 점에서는 유효하지만, Chat band 계산 source로 그대로 쓰기에는 정보 손실이 크다.

Chat band 판단에 중요한 값은 `supportLanguageDependence`, `aiScaffoldingDependence`, `conversationSustainability`, `responseDifficultyFit`, `targetLanguageProduction`, `targetLanguageComprehension` 같은 대화 지속 능력 evidence다. 이를 `AvgUtteranceLength`, `SentenceComplexity`, `SpokenNaturalness` 같은 일반 metric으로 압축한 뒤 다시 `ConversationAbilityBand`로 복원하면 같은 대화에서 서로 다른 band 정책이 생길 수 있다.

따라서 `CHAT-TUNE-007`에서는 Chat band 계산의 공식 source를 `LangState.analysisMeta.chatEvidenceSummary`로 일원화한다.

## 목표

- Chat band 계산에 필요한 대화 evidence 의미를 정보 손실 없이 `LangState`에 저장한다.
- Chat band 계산은 `chatEvidenceSummary`를 공식 source로 사용한다.
- `chat_conversation_evidence` Firestore snapshot은 테스트/debug/review용 보조 자료로 유지한다.
- Chat band 계산에서 `InternalMetrics`, `ExternalMetrics`, `ChatSession metricEvidence`를 사용하지 않는다.
- Correction policy와 Statistics 흐름은 기존 metric 기반 구조를 유지한다.

## 핵심 결정

- `LangState`는 하나로 유지한다.
- `analysisMeta`에 `chatEvidenceSummary`를 추가한다.
- `chatEvidenceSummary`는 실시간 Chat 대화 지속 능력의 공식 저장 요약이다.
- Chat prompt/speed/style에 쓰는 `ConversationAbilityBand`는 `chatEvidenceSummary`에서 계산한다.
- `chatEvidenceSummary`가 없으면 기존 metric fallback으로 band를 계산하지 않고 first selectedLang fallback을 사용한다.
- `chat_conversation_evidence/{selectedLang}` snapshot은 유지하되, 공식 band 계산 source로 쓰지 않는다.
- `debugRecommendedBand`는 계속 검토용 후보일 뿐 앱이 직접 적용하지 않는다.
- `ChatSession metricEvidence`는 007에서도 저장을 유지한다.
- 단, `ChatSession metricEvidence`는 007의 Chat band 계산에 사용하지 않는다.
- Correction band는 기존처럼 `InternalMetrics`, `ExternalMetrics`, `metricEvidence` 중심으로 계산하고, Chat evidence summary를 읽지 않는다.

## Source Of Truth

| 목적 | Source |
| --- | --- |
| Chat band 계산 | `LangState.analysisMeta.chatEvidenceSummary` |
| Chat evidence debug/review | Firestore `users/{uid}/chat_conversation_evidence/{selectedLang}` |
| Correction policy | `LangState.internal`, `LangState.external`, `analysisMeta.metricEvidence` |
| Statistics/Dashboard | 기존 summary/metric 흐름 |

공식 앱 동작은 `LangState`를 기준으로 한다. Firestore snapshot은 사람이 Gemini 분석 결과를 확인하기 위한 보조 저장소다.

## Chat Evidence Summary

`chatEvidenceSummary`는 band 자체가 아니라 band를 계산할 수 있는 대화 능력 evidence 요약이다.

예상 모델:

```kotlin
data class ChatEvidenceSummary(
    val targetLanguageComprehension: TargetLanguageComprehensionEvidence,
    val targetLanguageProduction: TargetLanguageProductionEvidence,
    val supportLanguageDependence: LanguageDependenceEvidence,
    val aiScaffoldingDependence: LanguageDependenceEvidence,
    val conversationSustainability: ConversationSustainabilityEvidence,
    val consistency: ConversationConsistencyEvidence,
    val responseDifficultyFit: ResponseDifficultyFitEvidence,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
)
```

필드 의미:

| Field | 의미 |
| --- | --- |
| `targetLanguageComprehension` | 사용자가 학습언어 입력을 어느 정도 이해하고 반응했는지 |
| `targetLanguageProduction` | 사용자가 학습언어로 직접 만든 의미 단위 |
| `supportLanguageDependence` | 기준언어가 없으면 대화가 끊기는 정도 |
| `aiScaffoldingDependence` | AI 힌트/선택지/쉬운 재구성 의존도 |
| `conversationSustainability` | 학습언어 반응으로 대화가 유지되는 정도 |
| `consistency` | 세션 전체에서 능력 단서가 안정적인지 |
| `responseDifficultyFit` | AI 응답 난이도가 사용자에게 맞았는지 |
| `confidence` | 이 요약을 얼마나 신뢰할지 |
| `observedCount` | summary에 누적된 유효 Chat 분석 횟수 |
| `lastObservedAt` | 마지막 유효 Chat 분석 시각 |

## 저장 정책

Chat 세션 종료 후 Gemini 분석 결과를 두 위치에 저장할 수 있다.

```text
ChatConversationEvidence
        ├─ LangState.analysisMeta.chatEvidenceSummary   // 공식 Chat band source
        ├─ LangState.analysisMeta.metricEvidence         // 향후 확장용 ChatSession source 근거
        └─ chat_conversation_evidence snapshot           // debug/review source
```

정책:

- `chatEvidenceSummary` 저장은 공식 LangState update 경로를 사용한다.
- `ChatSession metricEvidence` 저장은 006에서 만든 경로를 유지한다.
- `ChatSession metricEvidence`는 향후 Statistics/AI교정에서 opt-in으로 재사용할 수 있는 선택 근거이며, 007의 Chat band source는 아니다.
- `chat_conversation_evidence` snapshot 저장은 best-effort로 유지한다.
- snapshot 저장 실패는 `chatEvidenceSummary` 저장을 막지 않는다.
- `chatEvidenceSummary` 저장 실패는 pending retry 대상이다.
- 기존 summary가 없거나 기존 summary의 `confidence=Low`이면 새 Low confidence 분석도 summary로 저장한다.
- 기존 summary의 `confidence=Medium/High`이면 새 Low confidence 분석은 기존 summary를 덮어쓰지 않는다.
- `lastChatAnalysisEventId`는 계속 Chat source 중복 방어에 사용한다.
- Chat update는 `DashSummary`, `SessionSummary`, `InternalMetrics`, `ExternalMetrics`를 직접 변경하지 않는다.

## Band 계산 정책

Chat band 계산은 아래 흐름을 따른다.

```text
LangState.analysisMeta.chatEvidenceSummary
        ↓
ChatEvidenceSummaryBandPolicy
        ↓
ConversationAbilityBand
        ↓
ChatAdaptationPolicy / prompt / speed
```

fallback:

```text
chatEvidenceSummary 없음
        ↓
first selectedLang fallback
```

주의:

- `InternalMetrics` / `ExternalMetrics`로 Chat band를 대체 계산하지 않는다.
- `ChatSession metricEvidence`로 Chat band를 대체 계산하지 않는다.
- 단일 세션 성공만으로 급상승하지 않는다.
- `confidence=Low` summary는 저장할 수 있지만 높은 band로 올리는 근거로 쓰지 않는다.
- `confidence=Low` summary는 `IntentOnly` 보호 조건이 없더라도 낮은 band 상한을 둔다.
- `supportLanguageDependence=High` 또는 `aiScaffoldingDependence=High`이면 상향을 제한한다.
- `conversationSustainability=RequiresSupport`이면 `IntentOnly` 보호 근거로 본다.
- `debugRecommendedBand`는 비교/검토 로그에만 남긴다.

## 기능별 해석 경계

같은 `LangState` 안에 저장되지만 기능별로 읽는 source는 다르다.

```text
Chat 대화 능력
→ analysisMeta.chatEvidenceSummary
→ ConversationAbilityBand
→ Chat prompt / speed / style

Correction/Statistics 언어 능력
→ internal / external / metricEvidence
→ Correction policy / Dashboard / Statistics
```

이는 같은 사용자의 같은 언어에 대해 서로 다른 능력을 저장한다는 뜻이 아니라, 같은 `LangState` 안에 기능별 evidence source를 분리해 저장하고 기능별 policy가 다르게 해석한다는 뜻이다.

실시간 대화 지속 능력과 교정 기반 문장 능력은 다를 수 있다. 따라서 Chat이 Correction 수치를 과신하거나, Correction이 Chat 대화 성공을 문법 능력 상승으로 오해하지 않도록 읽는 경계를 분리한다.

## 작업 범위

### 포함

- `LangStateAnalysisMeta`에 `chatEvidenceSummary` 추가
- `ChatEvidenceSummary` domain model 추가
- DTO/Firestore mapper round-trip 추가
- `ApplyChatSignalUpdateUseCase`가 `chatEvidenceSummary`를 갱신하도록 변경
- `BuildLearnerAdaptationProfileUseCase`의 Chat band 계산을 `chatEvidenceSummary` 중심으로 변경
- 기존 `ChatSession metricEvidence` 저장 경로는 유지하되, Chat band 계산 source에서 제외
- `StartSessionUseCase`에서 Firestore snapshot을 공식 band 계산 source로 쓰지 않도록 정리
- `AiChatPromptTrace`에서 Chat band source가 `LangStateSummary`인지 확인할 수 있게 로그 정리
- 관련 unit test 추가/수정

### 제외

- Firestore `chat_conversation_evidence` snapshot 제거
- snapshot cleanup / dev-only 전환
- Statistics 화면 지표 재정의
- Correction prompt 또는 `CorrectionLearningSignal` 계약 변경
- `InternalMetrics`를 Chat용/Correction용으로 분리
- Cloud Function 회원탈퇴 cleanup 구현

## AC

- [ ] `LangState.analysisMeta.chatEvidenceSummary`가 저장/복원된다.
- [ ] 기존 사용자 데이터에 `chatEvidenceSummary`가 없어도 안전하게 `null`로 복원된다.
- [ ] Chat 세션 분석 결과가 `chatEvidenceSummary`로 저장된다.
- [ ] 첫 Chat 분석이 `confidence=Low`여도 기존 summary가 없으면 `chatEvidenceSummary`로 저장된다.
- [ ] 기존 `confidence=Low` summary는 새 Low confidence 분석으로 갱신될 수 있다.
- [ ] 기존 `confidence=Medium/High` summary는 새 Low confidence 분석 하나로 덮어쓰이지 않는다.
- [ ] Low confidence summary는 저장되어도 높은 Chat band로 직접 상향되지 않는다.
- [ ] `chatEvidenceSummary` 저장은 `DashSummary`, `SessionSummary`, `InternalMetrics`, `ExternalMetrics`를 변경하지 않는다.
- [ ] Chat band 계산은 `chatEvidenceSummary`를 공식 source로 사용한다.
- [ ] `chatEvidenceSummary`가 없으면 first selectedLang fallback을 사용한다.
- [ ] Chat band 계산은 `InternalMetrics`, `ExternalMetrics`, `ChatSession metricEvidence`를 fallback으로 사용하지 않는다.
- [ ] `ChatSession metricEvidence`는 저장을 유지하되 Chat band와 Correction band 계산에 직접 사용되지 않는다.
- [ ] Firestore `chat_conversation_evidence` snapshot은 유지되지만 공식 band 계산에는 사용되지 않는다.
- [ ] `debugRecommendedBand`는 직접 적용되지 않는다.
- [ ] Correction band 계산은 `chatEvidenceSummary`의 영향을 받지 않는다.
- [ ] Chat 분석 pending retry 흐름은 유지된다.
- [ ] `AiChatPromptTrace`에서 Chat band source와 적용 band를 확인할 수 있다.

## 테스트 방법

- `git diff --check`
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- `LearningStateRepoImplTest`
- `LearningStateWriteUseCasesTest`
- `BuildLearnerAdaptationProfileUseCaseTest`
- `ChatPromptIntegrationUseCaseTest`
- 실제 Chat 세션 종료 후 `chatEvidenceSummary` 저장 여부 확인
- 새 Chat 세션 시작 시 `AiChatPromptTrace`에서 `chatEvidenceSummary` 기반 band 적용 여부 확인

## 후속 작업

- `chat_conversation_evidence` snapshot을 계속 유지할지, dev-only로 전환할지, 제거할지 결정한다.
- 오래된 snapshot과 pending job cleanup 정책을 별도 이슈에서 정리한다.
- Statistics에서 대화 능력을 사용자에게 별도 지표로 보여줄지 검토한다.
