# [Improvement] CHAT-TUNE-007 Chat band source를 LangState summary로 일원화

## 목적

`CHAT-TUNE-006`은 Chat 세션 분석 결과를 `LangState` update 경로에 연결했다.
하지만 Chat evidence를 일반 `metricEvidence`로만 압축하면, Chat band 계산에 필요한 의미가 손실될 수 있다.

`CHAT-TUNE-007`은 다음 Chat 세션 시작 시 적용할 `ConversationAbilityBand`의 공식 source를 `LangState.analysisMeta.chatEvidenceSummary`로 일원화하는 작업이다.
Firestore snapshot은 신고/리뷰 확인용으로 유지하지만, 공식 Chat band 계산에는 사용하지 않는다.

---

# User Story

사용자는 이전 Chat 세션에서 관찰된 실제 대화 지속 능력이 다음 Chat 세션의 말투, 난이도, 기준언어 보조에 반영되기를 기대한다.
Umma는 Chat 대화 능력 판단을 snapshot이나 일반 metric에 섞지 않고, Chat 대화에 필요한 evidence 요약을 읽어 일관된 band를 계산한다.
Correction 흐름은 Chat summary 때문에 교정 강도가 갑자기 바뀌지 않는다.

---

# 완료 기준(AC)

- [ ] `LangState.analysisMeta.chatEvidenceSummary`가 저장/복원된다.
- [ ] 기존 사용자 데이터에 `chatEvidenceSummary`가 없어도 안전하게 `null`로 복원된다.
- [ ] Chat 세션 분석 결과가 `chatEvidenceSummary`로 저장된다.
- [ ] Chat band 계산은 `chatEvidenceSummary`를 공식 source로 사용한다.
- [ ] `chatEvidenceSummary`가 없으면 first selectedLang fallback을 사용한다.
- [ ] 첫 Chat 분석이 `confidence=Low`여도 기존 summary가 없으면 저장된다.
- [ ] 기존 Low summary는 새 Low 분석으로 갱신될 수 있다.
- [ ] 기존 Medium/High summary는 단일 Low 분석으로 덮어쓰이지 않는다.
- [ ] Low summary는 저장되어도 높은 Chat band로 직접 상향되지 않는다.
- [ ] Chat band 계산은 Firestore snapshot, InternalMetrics, ExternalMetrics, ChatSession metricEvidence를 fallback으로 사용하지 않는다.
- [ ] `chat_conversation_evidence` snapshot은 debug/review용으로만 유지된다.
- [ ] Correction band 계산은 `chatEvidenceSummary`의 영향을 받지 않는다.
- [ ] `AiChatPromptTrace`에서 적용된 Chat band와 source를 확인할 수 있다.

---

# 포함 범위

- `ChatEvidenceSummary` domain model 추가
- `LangStateAnalysisMeta.chatEvidenceSummary` 추가
- DTO/Firestore mapper round-trip 연결
- `ApplyChatSignalUpdateUseCase`의 summary 저장 정책 추가
- `ChatEvidenceBandPolicy` 정의
- `BuildLearnerAdaptationProfileUseCase`의 Chat band source 정리
- `StartSessionUseCase`의 snapshot 직접 참조 제거
- `AiChatPromptTrace`의 band source 로그 정리
- 관련 단위 테스트 추가/수정

---

# 제외 범위

- Firestore `chat_conversation_evidence` snapshot 제거
- snapshot cleanup 또는 dev-only 전환
- Statistics 화면 지표 재정의
- Correction prompt 변경
- `CorrectionLearningSignal` 계약 변경
- `InternalMetrics`를 Chat용/Correction용으로 분리
- Cloud Function 회원탈퇴 cleanup 구현

---

# 기준 문서

- [CHAT-TUNE-005 Conversation Band Definition Recalibration](./CHAT-TUNE-005_Conversation_Band_Definition_Recalibration.md)
- [CHAT-TUNE-006 Chat Evidence LangState Integration](./CHAT-TUNE-006_Chat_Evidence_LangState_Integration.md)
- [CHAT-TUNE-001-C LearnerAdaptationProfile](./CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md)
- [LS-001 Language State Model Structure](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 기존 작업과의 관계

## CHAT-TUNE-006

`CHAT-TUNE-006`은 Chat analysis 결과를 LangState update 경로에 연결하고, ChatSession source와 pending retry 기반을 준비한다.
`CHAT-TUNE-007`은 그 결과 중 Chat band 계산에 필요한 대화 능력 summary를 공식 source로 정한다.

## Snapshot 저장소

`chat_conversation_evidence/{selectedLang}` snapshot은 사람이 Gemini 분석 결과와 신고 세션을 검토하기 위한 보조 저장소다.
공식 앱 동작은 `LangState.analysisMeta.chatEvidenceSummary`를 기준으로 한다.

---

# Source Of Truth

| 목적 | Source |
| --- | --- |
| Chat band 계산 | `LangState.analysisMeta.chatEvidenceSummary` |
| Chat evidence debug/review | Firestore `users/{uid}/chat_conversation_evidence/{selectedLang}` |
| Correction policy | `LangState.internal`, `LangState.external`, `analysisMeta.metricEvidence` |
| Statistics/Dashboard | 기존 summary/metric 흐름 |

주의:

- 같은 `LangState` 안에 저장되지만 기능별로 읽는 source가 다르다.
- Chat 대화 지속 능력과 교정 기반 문장 능력은 다를 수 있다.
- Chat이 Correction 수치를 과신하거나, Correction이 Chat 대화 성공을 문법 능력 상승으로 오해하지 않게 읽는 경계를 분리한다.

---

# Chat Evidence Summary

`chatEvidenceSummary`는 band 자체가 아니라 band를 계산할 수 있는 대화 능력 evidence 요약이다.

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

---

# 저장 정책

Chat 세션 종료 후 Gemini 분석 결과는 아래 위치로 나뉠 수 있다.

```text
ChatConversationEvidence
        ├─ LangState.analysisMeta.chatEvidenceSummary   // 공식 Chat band source
        ├─ LangState.analysisMeta.metricEvidence         // 향후 확장용 ChatSession source 근거
        └─ chat_conversation_evidence snapshot           // debug/review source
```

정책:

- `chatEvidenceSummary` 저장은 공식 LangState update 경로를 사용한다.
- `ChatSession metricEvidence` 저장은 유지하되 Chat band 계산에는 쓰지 않는다.
- snapshot 저장은 best-effort로 유지한다.
- snapshot 저장 실패는 `chatEvidenceSummary` 저장을 막지 않는다.
- `chatEvidenceSummary` 저장 실패는 pending retry 대상이다.
- Chat update는 `DashSummary`, `SessionSummary`, `InternalMetrics`, `ExternalMetrics`를 직접 변경하지 않는다.

---

# Low Confidence 정책

`confidence=Low`는 사용자의 능력이 낮다는 뜻이 아니라, 이번 분석을 강하게 믿기 어렵다는 뜻이다.
하지만 첫 세션이나 기존 Low 상태에서는 Low 분석을 버리면 초저숙련 사용자의 공식 Chat 상태가 계속 비어 있을 수 있다.

정책:

- 기존 summary가 없으면 Low confidence 분석도 저장한다.
- 기존 summary도 Low이면 새 Low 분석으로 갱신할 수 있다.
- 기존 summary가 Medium/High이면 단일 Low 분석으로 덮어쓰지 않는다.
- Low summary는 저장되더라도 높은 Chat band로 직접 상향되지 않는다.

---

# Band 계산 정책

```text
LangState.analysisMeta.chatEvidenceSummary
        ↓
ChatEvidenceBandPolicy
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

방어 원칙:

- `InternalMetrics` / `ExternalMetrics`로 Chat band를 대체 계산하지 않는다.
- `ChatSession metricEvidence`로 Chat band를 대체 계산하지 않는다.
- 단일 세션 성공만으로 급상승하지 않는다.
- `supportLanguageDependence=High` 또는 `aiScaffoldingDependence=High`이면 상향을 제한한다.
- `conversationSustainability=RequiresSupport`이면 `IntentOnly` 보호 근거로 본다.
- `debugRecommendedBand`는 비교/검토 로그에만 남긴다.

---

# 작업 계획

1. `ChatEvidenceSummary`와 `LangStateAnalysisMeta.chatEvidenceSummary`를 추가한다.
2. 기존 Firestore 문서에 summary가 없어도 안전하게 복원되도록 DTO/mapper를 연결한다.
3. `ApplyChatSignalUpdateUseCase`에서 Chat evidence를 summary로 저장한다.
4. Low confidence 저장/갱신 정책을 적용한다.
5. `ChatEvidenceBandPolicy`를 만들어 summary에서 `ConversationAbilityBand`를 계산한다.
6. `BuildLearnerAdaptationProfileUseCase`가 Chat band 계산 시 summary만 공식 source로 사용하게 한다.
7. `StartSessionUseCase`가 snapshot repository를 직접 읽지 않게 정리한다.
8. snapshot은 debug/review용으로 유지하고 공식 source에서 제외한다.
9. `AiChatPromptTrace`에서 `LangStateSummary` source와 적용 band를 확인할 수 있게 한다.
10. Chat/Correction band 회귀 테스트를 추가한다.

---

# 예외와 방어

- 기존 사용자에게 summary가 없어도 앱은 안전하게 시작한다.
- 오래된 snapshot 문서는 다음 세션 band 계산에 직접 반영되지 않는다.
- Chat summary 저장 실패는 pending retry 대상으로 남기되, 사용자 Chat 흐름을 막지 않는다.
- Correction 담당 흐름의 저장/계산 계약은 변경하지 않는다.
- Statistics/Dashboard는 기존 summary/metric 흐름을 유지한다.

---

# 테스트 방법

- `git diff --check`
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- `LearningStateRepoImplTest`
- `LearningStateWriteUseCasesTest`
- `BuildLearnerAdaptationProfileUseCaseTest`
- `ChatPromptIntegrationUseCaseTest`
- 실제 Chat 세션 종료 후 `chatEvidenceSummary` 저장 여부 확인
- 새 Chat 세션 시작 시 `AiChatPromptTrace`에서 `conversationEvidence={applied=true,...,source=LangStateSummary}` 확인

---

# 후속 작업

- `chat_conversation_evidence` snapshot 유지, dev-only 전환, 제거 여부를 결정한다.
- 오래된 snapshot과 pending job cleanup 정책을 별도 이슈에서 정리한다.
- Statistics에서 대화 능력을 별도 지표로 보여줄지 검토한다.
