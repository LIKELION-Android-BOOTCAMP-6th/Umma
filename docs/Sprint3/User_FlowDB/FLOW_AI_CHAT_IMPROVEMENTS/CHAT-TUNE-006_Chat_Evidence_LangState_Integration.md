# [Improvement] CHAT-TUNE-006 Chat 대화 근거 LangState 연동

## 목적

`CHAT-TUNE-005`는 Chat 대화 능력 band와 evidence 의미를 다시 정의했다.
다음 단계에서는 세션 종료 후 Gemini가 분석한 Chat 대화 능력 evidence를 공식 `LangState` update 경로에 안전하게 연결해야 한다.

`CHAT-TUNE-006`은 Chat evidence를 Correction signal에 섞는 작업이 아니다.
Chat 세션 분석 결과를 별도 source로 구분하고, 사용자 흐름을 막지 않으면서 `LangState`에 누적할 수 있는 저장 경계를 만드는 작업이다.

---

# User Story

사용자는 대화를 마친 뒤 자신의 대화 능력이 다음 Chat 세션에 더 잘 반영되기를 기대한다.
Umma는 Chat 대화가 끝난 뒤 별도 분석을 통해 대화 지속 능력 단서를 저장하고, 이 저장이 실패하거나 지연되어도 사용자의 대화 종료 흐름을 방해하지 않는다.
Correction 담당 흐름은 Chat 분석 결과 때문에 교정 강도가 갑자기 바뀌지 않는다.

---

# 완료 기준(AC)

- [ ] Chat 세션 분석 결과는 Correction signal과 분리된 Chat 전용 update input으로 처리된다.
- [ ] `LearningSignalSource.ChatSession` source로 Chat 분석 근거를 구분할 수 있다.
- [ ] 같은 Chat 세션 분석은 중복 반영되지 않는다.
- [ ] Chat source는 `LangState.lastAnalysisEventId`를 덮지 않고 Chat 전용 중복 방어 필드를 사용한다.
- [ ] Chat evidence update는 `DashSummary`와 `SessionSummary`를 변경하지 않는다.
- [ ] Chat evidence update는 `InternalMetrics`와 `ExternalMetrics`를 직접 변경하지 않는다.
- [ ] `confidence=Low` evidence는 공식 metric을 움직이지 않는다.
- [ ] Chat analysis pending job은 분석 시작 전에 저장되어 앱 종료/재진입 후 재시도할 수 있다.
- [ ] snapshot 저장 실패와 LangState update 실패는 사용자 Chat 흐름을 막지 않는다.
- [ ] Correction prompt, Correction learning signal 계약, Correction 저장 순서는 변경하지 않는다.
- [ ] 저장된 Chat evidence를 다음 Chat band 계산에 공식 반영하는 작업은 후속 `CHAT-TUNE-007`에서 결정한다.

---

# 포함 범위

- Chat 전용 LangState update input 설계
- `LearningSignalSource.ChatSession` source 적용
- Chat source 중복 방어 필드 설계
- Chat evidence를 `analysisMeta.metricEvidence`로 낮춰 저장하는 policy 설계
- Chat evidence update 시 summary 보존 정책 설계
- Chat analysis pending job 저장/재시도 흐름 설계
- snapshot과 LangState update의 best-effort 분리
- Chat/Correction band 회귀 테스트 추가

---

# 제외 범위

- Correction AI prompt 변경
- `CorrectionLearningSignal` 계약 변경
- `CompleteCorrectionUseCase` 저장 순서 변경
- Statistics 화면 지표 재설계
- Prompt review 신고 도구 저장 구조 변경
- 회원탈퇴 Cloud Function cleanup 구현
- OpenAI Realtime 대화 생성 로직 변경
- Chat band 공식 source를 `chatEvidenceSummary`로 바꾸는 작업

---

# 기준 문서

- [CHAT-TUNE-005 Conversation Band Definition Recalibration](./CHAT-TUNE-005_Conversation_Band_Definition_Recalibration.md)
- [CHAT-TUNE-003 Correction Signal 기반 LangState 측정 고도화](./CHAT-TUNE-003_Correction_Signal_LangState_Integration.md)
- [CHAT-TUNE-004 Correction Growth Policy](./CHAT-TUNE-004_Correction_Growth_Policy.md)
- [CHAT-TUNE-001-C LearnerAdaptationProfile](./CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md)
- [LS-001 Language State Model Structure](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 기존 작업과의 관계

## CHAT-TUNE-005

`CHAT-TUNE-005`는 어떤 대화 능력 단서를 측정해야 하는지 정의한다.
`CHAT-TUNE-006`은 그 단서를 세션 종료 후 분석하고, 공식 `LangState` update 경로에 안전하게 넣는 저장 책임을 다룬다.

## Correction 흐름

Correction은 기존 `CorrectionSignal` source와 correction update contract를 유지한다.
Chat evidence는 Correction 입력으로 직접 들어가지 않고, `LearningSignalSource.ChatSession`으로 분리된다.

## CHAT-TUNE-007

`CHAT-TUNE-006`은 Chat evidence를 `LangState`에 연결하는 기반이다.
다음 세션 Chat band 계산의 공식 source는 `CHAT-TUNE-007`에서 `chatEvidenceSummary` 중심으로 별도 정리한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Chat conversation analysis | 세션 종료 후 저장된 final turn을 분석해 Chat evidence를 만든다. |
| `ApplyChatSignalUpdateUseCase` | Chat evidence를 LangState update에 맞는 prepared state로 변환한다. |
| `LearningStateRepo` | LangState 저장과 pending sync 계약을 담당한다. |
| `LangState.analysisMeta.metricEvidence` | Chat source 근거를 추적하되, MVP에서 점수 자체를 직접 움직이지 않는다. |
| `LangState.lastAnalysisEventId` | 기존 Correction 등 공용 분석 중복 방어를 유지한다. |
| `lastChatAnalysisEventId` | Chat source 전용 중복 방어를 담당한다. |
| `DashSummary` / `SessionSummary` | Chat evidence update로 변경되지 않는다. |
| `chat_conversation_evidence` snapshot | debug/review 보조 자료로만 유지된다. |

---

# Chat Update 흐름

```text
Chat session final turns
        ↓
Gemini conversation analysis
        ↓
ChatSignalUpdateInput
        ↓
ApplyChatSignalUpdateUseCase
        ↓
LangState prepared state
        ↓
LearningStateRepo.updateLanguageState
```

정책:

- Realtime API는 실시간 대화 생성만 담당한다.
- 대화 능력 분석은 세션 종료 또는 화면 이탈 이후 별도 Gemini 분석으로 수행한다.
- 분석 전에 pending job을 먼저 남겨 앱 종료/재진입 후 재시도할 수 있게 한다.
- 분석 성공 후 LangState update와 snapshot 저장은 서로를 막지 않는 best-effort 흐름으로 처리한다.

---

# Chat Source 반영 원칙

Chat source는 사용자의 문장 정확도를 평가하는 source가 아니다.
학습언어로 대화가 얼마나 유지됐는지, 기준언어/AI scaffold 없이도 감당 가능한지를 보는 source다.

| Evidence | 주요 의미 | LangState 반영 방향 |
| --- | --- | --- |
| `targetLanguageComprehension` | 학습언어 입력 이해와 반응 수준 | 이해 가능한 prompt 난이도 근거 |
| `targetLanguageProduction` | 학습언어로 만든 단어, 구, 문장, 연결 발화 단위 | 낮은 Chat band 경계 근거 |
| `supportLanguageDependence` | 기준언어 없이는 대화가 끊기는지 | 상향 제한 근거 |
| `aiScaffoldingDependence` | AI 힌트와 선택지 의존도 | 독립 대화 능력 과대평가 방지 |
| `conversationSustainability` | 학습언어 반응만으로 대화가 유지됐는지 | Chat 지속 능력 핵심 근거 |
| `consistency` | 세션 전체에서 단서가 안정적인지 | confidence 보정 |
| `responseDifficultyFit` | AI 난이도가 사용자에게 맞았는지 | 급상승/난이도 mismatch 방지 |

MVP 반영 원칙:

- `ExternalMetrics`는 직접 변경하지 않는다.
- `InternalMetrics`도 직접 변경하지 않는다.
- Chat source는 먼저 `analysisMeta.metricEvidence`와 Chat 전용 중복 방어 정보에만 남긴다.
- Chat source만으로 Correction band가 올라가면 안 된다.

---

# Pending Analysis 정책

Chat 분석은 세션 종료 후 비동기로 실행되므로 앱 종료, 네트워크 실패, Gemini 실패에 대비해야 한다.

계획:

1. 분석 시작 전에 현재 세션의 최소 final turn snapshot을 local pending job으로 저장한다.
2. Gemini 분석이 성공하면 LangState update를 시도한다.
3. LangState 반영이 성공하면 pending job을 완료 처리한다.
4. 실패하면 다음 Chat 진입 시 pending job을 재시도한다.
5. pending 재시도는 사용자 Chat 화면 진입을 막지 않는다.

주의:

- pending job은 prompt review 신고 자료가 아니다.
- pending job은 사용자에게 별도 UI로 노출하지 않는다.
- 오래된 pending cleanup 정책은 후속 이슈에서 정리할 수 있다.

---

# Summary 보존 정책

Chat evidence update는 대시보드와 교정 가능 상태를 바꾸는 작업이 아니다.

따라서:

- 기존 `DashSummary`를 보존한다.
- 기존 `SessionSummary`를 보존한다.
- `recentMinutes`, `recentTopic`, `correctionAvailable`, summary `updatedAt`을 Chat evidence 저장 때문에 바꾸지 않는다.
- Correction 완료 흐름의 summary update 계약은 유지한다.

---

# 작업 계획

1. Chat 전용 update input과 analysis event id 형식을 정의한다.
2. `LearningSignalSource.ChatSession`을 LangState evidence source로 추가한다.
3. `LangStateAnalysisMeta.lastChatAnalysisEventId`를 추가해 Chat source 중복 방어를 분리한다.
4. `ApplyChatSignalUpdateUseCase`를 만들어 Chat evidence를 LangState prepared state로 변환한다.
5. Chat source metric 반영은 보수적으로 제한하고 `InternalMetrics` / `ExternalMetrics`를 직접 변경하지 않는다.
6. Chat evidence update가 `DashSummary` / `SessionSummary`를 보존하도록 repository 저장 경계를 확인한다.
7. Gemini 분석 시작 전 pending job을 저장하고, 실패 시 다음 Chat 진입에서 재시도한다.
8. `chat_conversation_evidence` snapshot은 debug/review용 best-effort 저장소로 유지한다.
9. Chat/Correction band 회귀 테스트를 추가한다.

---

# 예외와 방어

- 같은 Chat session id가 다시 들어오면 중복 반영하지 않는다.
- `confidence=Low`는 metric 이동 근거로 쓰지 않는다.
- `supportLanguageDependence=High`와 `aiScaffoldingDependence=High`는 상향 근거로 쓰지 않는다.
- Chat update가 실패해도 사용자의 대화 종료 흐름은 실패 처리하지 않는다.
- Correction 담당자가 사용하는 입력 모델과 저장 순서는 변경하지 않는다.

---

# 테스트 방법

- `git diff --check`
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- `LearningStateRepoImplTest`
- `LearningStateWriteUseCasesTest`
- `BuildLearnerAdaptationProfileUseCaseTest`
- 실제 Chat 종료 후 `AiChatPromptTrace`에서 pending enqueue, analysis start, LangState save 로그 확인
- 같은 session id 재시도 시 중복 반영되지 않는지 확인
