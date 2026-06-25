# [Refactor] CHAT-REF-001 ChatViewModel 책임 분리

## 목적

현재 `ChatViewModel.kt`는 2,000줄을 넘는다.
파일이 커진 직접 원인은 AI Chat 안정화 과정에서 세션 진입, 마이크 입력, AI 이벤트 처리, Watch 연동, usage sync, 대화 분석, 신고, 관심사 설정이 모두 같은 ViewModel에 누적됐기 때문이다.

이번 작업은 AI Chat 동작을 바꾸는 작업이 아니다.
기존 마이크, 자막, 세션 복구, 저장, usage tracking, Watch 연동 흐름을 유지하면서 `ChatViewModel`의 책임을 작게 나누어 이후 수정과 검증이 쉬운 구조로 정리한다.

---

# User Story

개발자는 AI Chat 오류를 수정할 때 2,000줄짜리 ViewModel 전체를 읽지 않고도 관련 책임 영역을 빠르게 찾을 수 있다.
심사위원과 팀원은 Chat 화면의 ViewModel이 UI 상태 조립자 역할을 하고, 세션/오디오/저장/신고/분석 정책이 별도 컴포넌트로 분리되어 있음을 확인할 수 있다.

사용자는 리팩토링 이후에도 기존처럼 마이크로 대화를 시작하고, AI 응답을 듣고, 자막을 보고, 대화 기록과 usage가 저장되는 흐름을 그대로 사용할 수 있다.

---

# 완료 기준(AC)

- [ ] 1차 작업 후 `ChatViewModel`은 usage, 대화 분석, 신고, 관심 주제 설정의 긴 side-effect 로직을 직접 소유하지 않는다.
- [ ] 세션 시작, 복구, 종료 흐름은 기존 동작과 동일하게 유지된다.
- [ ] 마이크 시작/종료, AI 음성 재생, 자막 final-only 표시 흐름은 변경되지 않는다.
- [ ] SessionMemory 저장, correctionAvailable 신호, usage tracking, pending retry 흐름은 유지된다.
- [ ] Watch 연결 상태와 phone/watch 입력·출력 소유권 흐름은 유지된다.
- [ ] AI 콘텐츠 신고와 dev prompt review 신고는 기존 저장 대상과 실패 방식을 유지한다.
- [ ] 관심 주제 설정 로직은 Chat runtime 책임과 분리되거나 별도 helper로 격리된다.
- [ ] 분리된 coordinator는 `MutableStateFlow`를 직접 소유하거나 직접 수정하지 않는다.
- [ ] 각 분리 컴포넌트는 presentation/domain/data 책임 경계를 넘지 않는다.
- [ ] 기존 Chat 관련 테스트가 통과하거나, 정책 변경이 아닌 구조 변경에 맞춰 동등한 테스트로 갱신된다.
- [ ] `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`이 통과한다.

---

# 기준 문서

- [RULES](../../../../../RULES.md)
- [SYS-REALTIME-INFRA](../../../System_FlowDB/SYS_REALTIME_INFRA.md)
- [SYS-WATCH-INFRA](../../../System_FlowDB/SYS_WATCH_INFRA.md)
- [CHAT-ENGINE-001 OpenAI Realtime Transport 전환](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md)
- [CHAT-FIX-001-C 마이크 버튼 상태 UX](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-C_Mic_Button_State_UX.md)
- [CHAT-FIX-001-D final 자막 대화형 표시](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md)
- [CHAT-FIX-001-F AI 음성 재생 연속성](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-F_AI_Audio_Playback_Continuity.md)
- [CHAT-TUNE-008 대화 프레임과 스냅샷 기반 흐름 보조](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-008_Conversation_Frame_Snapshot.md)
- [AI-POLICY-001 Chat Safety Prompt and Report](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)

---

# 현재 문제

`ChatViewModel`은 현재 ViewModel 이상의 역할을 하고 있다.
아래 책임들이 한 파일에 섞이면서 파일 크기와 변경 위험이 커졌다.

| 책임 | 현재 위치 | 문제 |
| --- | --- | --- |
| 세션 진입/복구/새 세션 시작 | `enterChat`, `handleSessionStarted` | 화면 진입 상태와 transport 복구 정책이 한 함수에 길게 섞임 |
| 마이크 입력/오디오 전송 | `startUserTurn`, `beginUserTurn`, `endUserTurn`, `handleAudioInputFrame` | 권한, UI 상태, recorder job, transport 전송이 한 곳에 있음 |
| AI 이벤트 처리 | `startObservingAIEvents`, `handle*` 함수들 | 상태 전이 규칙이 ViewModel 내부 private 함수에 흩어짐 |
| turn 저장/Correction 신호 | `persistFinalTurn`, `handoverCorrectionAvailableSignal` | 저장 실패, turn save count, correction 신호가 UI 상태와 섞임 |
| usage tracking | `handleChatUsageReported`, `sync*Usage*`, `cleanupChatUsageBestEffort` | 운영 데이터 sync가 ViewModel 생명주기 코드에 붙어 있음 |
| 대화 분석 | `launchConversationEvidenceAnalysis`, pending analysis sync | 백그라운드 분석 예약과 retry가 ViewModel의 큰 비중을 차지함 |
| Watch 연동 | `observeWatchSessionState`, `switchWatchToPhone`, stop 예외 분기 | phone/watch 소유권 예외가 세션 정리 코드와 강하게 결합됨 |
| 신고 | `reportPromptReviewSession`, `reportLatestAiContent` | 신고 payload 구성과 UI loading 상태가 한 함수에 섞임 |
| 관심 주제 설정 | `checkInterestTopics`, `toggleTopic`, `saveInterestTopics` | Chat runtime과 직접 관련 없는 초기 설정 책임이 붙어 있음 |

이 상태에서는 작은 정책 수정도 같은 파일을 계속 건드리게 된다.
테스트도 "ChatViewModel 전체 상태" 중심으로 커질 수밖에 없다.

---

# 핵심 결정

- **동작 변경이 아니라 구조 분리 작업으로 진행한다.**
  - 마이크 버튼, 자막, 세션 복구, Watch, 저장, usage, 신고 UX는 유지한다.
  - 기존 방어 로직을 제거하지 않고 이동한다.

- **ChatViewModel은 UI 상태의 최종 조립자 역할로 남긴다.**
  - 화면 이벤트를 받고 `ChatUiState`를 갱신한다.
  - 긴 비동기 처리와 payload 구성은 분리 컴포넌트에 위임한다.

- **위험도가 낮은 영역부터 분리한다.**
  - usage sync, 대화 분석, 신고, 관심 주제 설정은 transport 상태 전이에 직접 관여하지 않아 1차 분리 대상이다.
  - 오디오와 세션 lifecycle은 마이크 비활성화, AI speaking, Watch 소유권과 직접 연결되므로 후순위로 둔다.

- **분리 컴포넌트는 기존 레이어 경계를 지킨다.**
  - presentation coordinator는 UI 생명주기와 상태 callback만 다룬다.
  - domain usecase는 정책과 저장 요청을 담당한다.
  - repository/data source는 저장과 통신만 담당한다.

- **하나의 giant coordinator로 다시 모으지 않는다.**
  - 기능 단위로 작게 나눈다.
  - 각 컴포넌트의 변경 이유가 분명해야 한다.

---

# 목표 구조

## 1차 목표

`ChatViewModel`을 2,000줄대에서 1,200줄 이하로 줄인다.
세션과 오디오의 민감한 흐름은 그대로 두고, side-effect 중심 후처리부터 분리한다.

1차 분리 대상:

- `ChatUsageSyncCoordinator`
- `ChatConversationAnalysisCoordinator`
- `ChatReportCoordinator`
- `ChatTopicSetupCoordinator`

1차 작업의 성공 기준은 "모든 Chat 책임을 완전히 분리"하는 것이 아니다.
마이크, 세션 lifecycle, Watch 소유권처럼 회귀 위험이 큰 부분은 그대로 두고, side-effect 후처리와 payload 구성부터 `ChatViewModel` 밖으로 이동하는 것이다.

## 2차 목표

상태 전이와 오디오 경계를 분리해 `ChatViewModel`을 800줄 안팎까지 줄인다.

2차 분리 대상:

- `ChatEventReducer`
- `ChatAudioTurnController`
- `ChatSessionLifecycleCoordinator`

2차 작업은 1차 분리 후 회귀 테스트가 안정된 뒤 진행한다.

---

# 구현 위치와 공통 계약

## 구현 위치

1차 coordinator는 presentation layer 안에 둔다.

```text
app/src/main/java/com/app/umma/presentation/chat/coordinator/
```

이 위치를 사용하는 이유:

- coordinator는 화면 생명주기, `ChatUiState` 입력값, loading/error callback 같은 presentation 관심사를 다룬다.
- 저장, 분석, 신고 정책 자체는 이미 domain usecase가 갖고 있으므로 coordinator가 domain 정책을 새로 만들지 않는다.
- data/repository 객체를 직접 참조하지 않고 기존 usecase만 호출한다.

## 상태 변경 계약

coordinator는 `_uiState`나 `MutableStateFlow`를 직접 받지 않는다.
ViewModel만 화면 상태의 source of truth를 소유한다.

허용되는 방식:

```kotlin
data class ChatReportUiPatch(
    val isReporting: Boolean? = null,
    val errorMessage: String? = null,
    val reportedTurnId: String? = null
)
```

```kotlin
class ChatReportCoordinator {
    suspend fun reportLatestAiContent(input: ChatReportInput): Result<ChatReportUiPatch>
}
```

ViewModel은 coordinator 결과를 받아 `_uiState.update { ... }`로 반영한다.
이렇게 해야 상태 소유권이 흩어지지 않고, 테스트에서 coordinator와 ViewModel을 분리해 검증할 수 있다.

## Coroutine scope 기준

- 화면 상태와 즉시 연결되는 작업은 `viewModelScope`에서 시작한다.
- 화면 이탈 이후에도 이어져야 하는 usage sync와 conversation analysis는 기존처럼 `applicationScope`를 사용한다.
- coordinator가 `applicationScope`를 직접 주입받을 수는 있지만, 어떤 작업이 background 작업인지 KDoc과 함수명에 명시한다.
- 실패해도 사용자 flow를 막지 않는 작업은 `BestEffort` 이름 또는 주석을 유지한다.

## Hilt 주입 기준

각 coordinator는 `@Inject constructor`를 사용한다.
별도 Hilt module은 필요할 때만 추가한다.
ViewModel 생성자 의존성이 너무 많아지면, 1차 coordinator를 주입해 기존 usecase 직접 주입 수를 줄이는 방향으로 정리한다.

---

# 책임 분리 계획

## 1. ChatUsageSyncCoordinator

대상 코드:

- `handleChatUsageReported`
- `syncCurrentChatSessionUsageBestEffort`
- `launchChatUsageSync`
- `syncPendingChatUsageBestEffort`
- `cleanupChatUsageBestEffort`

책임:

- provider usage event를 `ChatUsageRecord`로 변환한다.
- local-first usage 저장을 호출한다.
- pending usage sync와 cleanup을 best-effort로 수행한다.
- 성공/실패 로그를 남긴다.

ViewModel에 남길 책임:

- `AIEvent.ChatUsageReported`를 coordinator에 전달한다.
- 화면 이탈 시 현재 sessionId 기준 sync를 요청한다.
- coordinator 결과에 따라 사용자 UI를 바꾸지 않는다.

주의:

- usage 저장 실패는 사용자 대화 실패로 전파하지 않는다.
- pending retry는 Chat 진입 flow를 막지 않는다.
- `CHAT-ENGINE-001-B` 로그 태그와 메시지 의미는 유지한다.

## 2. ChatConversationAnalysisCoordinator

대상 코드:

- `launchConversationEvidenceAnalysis`
- `completeConversationAnalysisJobIfDone`
- `syncPendingConversationAnalysisBestEffort`
- `awaitFinalTurnSaveBeforeConversationAnalysis`
- `snapshotStatusForLog`
- `langStateStatusForLog`

책임:

- 세션 종료/화면 이탈 시 대화 능력 분석 job을 예약한다.
- final turn 저장 완료를 짧게 기다린 뒤 분석을 시작한다.
- 분석 결과를 LangState에 반영하고 pending job을 완료한다.
- 이전 앱 실행에서 남은 pending analysis를 재시도한다.

ViewModel에 남길 책임:

- 현재 sessionId, selectedLang, finalTurnCount, turn 저장 대기 상태를 전달한다.
- 화면 상태 변경 없이 background 작업을 요청한다.
- `isSavingTurn` 대기 여부는 ViewModel이 snapshot으로 전달하고, coordinator는 전달받은 supplier 또는 callback으로만 확인한다.

주의:

- 분석 실패는 Chat 종료, 화면 이탈, 새 세션 진입을 막지 않는다.
- 같은 세션/같은 turn 수 중복 분석 방어를 유지한다.
- `AiChatPromptTrace` 로그 의미는 유지한다.

## 3. ChatReportCoordinator

대상 코드:

- `reportPromptReviewSession`
- `reportLatestAiContent` 내부의 payload 구성과 usecase 호출

책임:

- dev prompt review 신고를 처리한다.
- Google Play 운영용 AI 콘텐츠 신고 payload를 구성한다.
- 신고 대상 유효성, note 길이 제한, context turn 제한을 적용한다.

ViewModel에 남길 책임:

- 신고 버튼 loading/success/error UI 상태를 반영한다.
- 현재 `ChatUiState`에서 신고에 필요한 snapshot을 coordinator에 전달한다.
- coordinator가 반환한 결과를 `ChatUiState`에 반영한다.

주의:

- prompt review와 운영 AI 신고는 저장 위치와 목적이 다르므로 분리 유지한다.
- 신고 실패는 Chat transport, 마이크 상태, SessionMemory 저장 흐름에 영향을 주지 않는다.
- 운영 신고는 final transcript 기준만 사용한다.

## 4. ChatTopicSetupCoordinator

대상 코드:

- `checkInterestTopics`
- `toggleTopic`
- `saveInterestTopics`

책임:

- 사용자 닉네임과 관심 주제 설정 필요 여부를 확인한다.
- 관심 주제 5개 선택 정책을 관리한다.
- 관심 주제 저장 usecase를 호출한다.

ViewModel에 남길 책임:

- topic dialog 표시 여부와 선택 상태를 `ChatUiState`에 반영한다.
- `selectedTopic`, `showTopicDialog`, `isTopicSaving`, `topicError` 필드는 1차 작업에서 `ChatUiState`에 유지한다.

주의:

- 장기적으로는 Chat이 아니라 Onboarding/MyPage 책임으로 이동할 수 있다.
- 이번 작업에서는 화면 변경 없이 별도 presentation helper로만 격리한다.

## 5. ChatEventReducer

대상 코드:

- `handleInitializing`
- `handleInitialized`
- `handlePartialTranscription`
- `handleStateChanged`
- `handleSessionInterrupted`
- `handleReconnected`
- `handleReconnectFailed`
- `handleError`

책임:

- `AIEvent`와 현재 `ChatUiState`를 받아 다음 `ChatUiState`를 계산한다.
- 상태 전이 규칙을 순수 함수 중심으로 테스트 가능하게 만든다.

ViewModel에 남길 책임:

- event stream을 collect한다.
- reducer 결과를 `_uiState`에 반영한다.
- 오디오 정지처럼 실제 side effect가 필요한 경우만 별도 호출한다.

주의:

- `SPEAKING` 진입 시 녹음 종료 같은 side effect는 reducer 안에 넣지 않는다.
- reducer는 순수 상태 계산만 담당한다.
- 같은 sessionId 지연 이벤트 방어와 transient mic 상태 보존 규칙을 유지한다.

## 6. ChatAudioTurnController

대상 코드:

- `startUserTurn`
- `beginUserTurn`
- `endUserTurn`
- `stopRecordingForAiSpeaking`
- `handleAudioInputFrame`
- `observeAudioOutputLevel`
- `observeAudioOutputPlayback`
- `captureCurrentUserTurnDuration`

책임:

- recorder job lifecycle을 관리한다.
- audio input frame을 transport로 전달한다.
- AI output playback/level을 관찰한다.
- USER turn 시작/종료 시간을 관리한다.

ViewModel에 남길 책임:

- 권한 결과와 버튼 이벤트를 controller에 전달한다.
- controller callback으로 받은 UI state patch를 반영한다.

주의:

- 마이크 비활성화, AI speaking, output playback tail 방어는 회귀 위험이 높다.
- 1차 분리 후 별도 테스트와 함께 진행한다.

## 7. ChatSessionLifecycleCoordinator

대상 코드:

- `enterChat`
- `handleSessionStarted`
- `requestChatSessionStop`
- `stopChatInternal`
- `onCleared`
- `setChatRouteVisible`
- `onChatRouteHidden`
- `stopChat`

책임:

- Chat route 진입, 복구, 새 세션 시작, 화면 이탈, ViewModel 종료 시 정리 순서를 조율한다.
- Watch가 붙은 세션과 phone-only 세션의 정리 차이를 유지한다.

ViewModel에 남길 책임:

- Composable lifecycle 이벤트를 coordinator에 전달한다.
- coordinator 결과를 UI 상태로 반영한다.

주의:

- 이 영역은 가장 위험하므로 마지막에 분리한다.
- `shouldKeepChatUiOnReentry`, `hasTransientMicStatus` 보존 규칙을 깨면 회전/재진입 UX가 회귀한다.

---

# 작업 순서

1. 현재 `ChatViewModel` 함수 목록, 줄 수, constructor 의존성 수, Chat 관련 테스트 목록을 baseline으로 남긴다.
2. `ChatUsageSyncCoordinator`를 추가하고 usage 관련 함수를 이동한다.
3. usage 관련 기존 테스트를 coordinator 단위 또는 ViewModel 통합 테스트로 맞춘다.
4. `ChatConversationAnalysisCoordinator`를 추가하고 대화 분석 예약/retry를 이동한다.
5. pending analysis 중복 방어와 LangState 반영 로그를 검증한다.
6. `ChatReportCoordinator`를 추가하고 신고 payload 구성을 이동한다.
7. AI 신고와 prompt review 신고 테스트를 갱신한다.
8. `ChatTopicSetupCoordinator`를 추가해 관심 주제 설정 로직을 격리한다.
9. 1차 분리 후 compile과 Chat 주요 흐름 수동 검증을 수행한다.
10. 2차 분리 범위인 reducer/audio/session lifecycle은 별도 PR 또는 후속 이슈로 진행한다.

1차 작업이 끝난 뒤 기록할 값:

- `ChatViewModel.kt` 줄 수
- `ChatViewModel` 생성자 의존성 수
- 새 coordinator 파일 목록
- 이동하지 않고 남긴 고위험 함수 목록
- 통과한 테스트와 수동 검증 결과

---

# 기존 방어로직 보존 체크리스트

1차 리팩토링은 아래 방어로직을 제거하거나 의미 변경하지 않는다.
아래 항목을 건드려야 한다면 1차 작업 범위를 넘어선 것으로 보고 별도 2차 작업에서 처리한다.

- 화면 회전/재진입 방어
  - `shouldKeepChatUiOnReentry`
  - `hasTransientMicStatus`
  - 같은 sessionId의 지연 `Initialized` 이벤트가 녹음/응답 대기 상태를 `IDLE`로 덮지 않는 규칙

- final transcript 중복 방어
  - `lastHandledFinalTurnId`
  - `handledFinalTurnIds`
  - 같은 final event가 늦게 다시 도착해도 자막, 저장, 신고 대상이 중복 반영되지 않는 규칙

- 마이크/AI speaking 방어
  - `stopRecordingForAiSpeaking`
  - AI speaking 진입 시 recorder를 끊고 `isAwaitingUserTranscript`를 정리하는 규칙
  - `isAudioOutputPlaying`이 true이면 서버 `response.done` 이후에도 마이크를 열지 않는 규칙

- Watch 소유권 방어
  - Watch가 연결되면 Phone 녹음을 중단하는 규칙
  - Watch output surface에서는 Phone local audio를 재생하지 않는 규칙
  - `preserveWatchSession=true`일 때 Phone 화면 이탈이 Watch 세션을 끊지 않는 규칙

- 세션 정리 방어
  - `clearAppSession`과 `preserveWatchSession` 분기
  - `onChatRouteHidden`, `stopChat`, `onCleared`의 호출 의미 차이
  - stale session 재사용을 막기 위한 active session 정리 규칙

- background best-effort 방어
  - usage sync 실패가 사용자 대화 실패로 전파되지 않는 규칙
  - conversation analysis 실패가 화면 이탈이나 다음 세션 진입을 막지 않는 규칙
  - pending retry가 다음 Chat 진입에서 다시 실행되는 규칙

- 관심 주제 설정 방어
  - 저장 중 중복 클릭 방지
  - 정확히 5개 선택 검증
  - retry 경로에서도 관심 주제 확인 후 세션 진입을 시도하는 순서

---

# 테스트 계획

## 자동 검증

- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- 기존 Chat 관련 테스트
- 새 coordinator 단위 테스트
- usage 저장/sync 관련 테스트
- AI 콘텐츠 신고 usecase/ViewModel 테스트
- conversation analysis pending job 테스트

현재 ChatViewModel 전용 테스트가 부족하면, 1차 작업에서 모든 ViewModel 통합 테스트를 새로 만들려고 하지 않는다.
대신 이동한 coordinator의 단위 테스트를 먼저 만들고, 마이크/세션/Watch 회귀는 기존 테스트와 수동 검증으로 방어한다.

## 수동 검증

- Chat 화면 진입 시 기존 세션 복구 또는 새 세션 시작이 정상 동작한다.
- Chat 화면 진입 시 관심 주제 다이얼로그 확인과 세션 시작 순서가 기존과 동일하다.
- 마이크 버튼으로 사용자 turn을 시작하고 정지할 수 있다.
- USER final transcript가 자막과 SessionMemory에 반영된다.
- AI final transcript가 자막과 신고 대상에 반영된다.
- 같은 final transcript 이벤트가 늦게 다시 와도 자막, 저장, 신고 대상이 중복 반영되지 않는다.
- AI 음성 재생 중 마이크가 다시 활성화되지 않는다.
- 화면 이탈 후 다시 진입해도 이전 세션 stale 상태가 남지 않는다.
- Watch가 붙은 상태에서 Phone 화면 이탈이 Watch 세션을 끊지 않는다.
- 네트워크 끊김/재연결 실패 시 기존 안내 문구와 버튼 상태가 유지된다.
- usage pending sync와 conversation analysis retry가 실패해도 Chat 진입을 막지 않는다.

---

# 제외 범위

- Realtime transport 교체
- prompt 문구 수정
- LangState 대화 능력 계산 정책 변경
- SessionMemory schema 변경
- Watch protocol 변경
- AI 캐릭터/자막 UI 디자인 변경
- 마이크 버튼 UX 변경
- 신고 Firestore schema 변경
- 관심 주제 설정 화면의 UX 개편

---

# 작업 중 주의사항

기존 동작 보존 여부는 위 체크리스트로 확인한다.
이 섹션은 구현 중 판단해야 할 경계만 짧게 남긴다.

## 1. 1차 범위를 넘기지 않는다

1차 작업은 usage, 대화 분석, 신고, 관심 주제 설정처럼 side-effect 성격이 강한 코드만 분리한다.
`AIEvent` 상태 전이, 마이크 turn, audio playback, session lifecycle, Watch 소유권은 2차 작업으로 남긴다.

## 2. 상태와 Job 소유권을 분산하지 않는다

`ChatViewModel`은 계속 `ChatUiState`의 source of truth로 남는다.
coordinator는 상태 patch나 결과값만 반환하고 `_uiState`를 직접 수정하지 않는다.

`eventJob`, `recordJob`, `enterChatJob`, `stopChatJob`은 1차 작업에서 ViewModel에 유지한다.
usage처럼 이동 대상인 job만 coordinator로 옮기되 기존 중복 실행 방어를 그대로 유지한다.

## 3. helper는 의미를 확인한 뒤 옮긴다

`hasHandledFinalTurn`, `replaceLatestRoleSubtitle`, `toReportContextTurn`, `shouldKeepChatUiOnReentry`, `hasTransientMicStatus`는 단순 유틸이 아니다.
final 중복 방어, 화면 재진입, 신고 context 최소화와 연결되어 있으므로 무리하게 공용 유틸로 빼지 않는다.

## 4. 신고 입력은 필요한 값만 넘긴다

운영용 AI 신고는 전체 `ChatUiState`나 SessionMemory를 넘기지 않는다.
final transcript, 최근 context turn, 언어 코드, 세션/turn 식별자처럼 저장에 필요한 값만 입력 모델로 전달한다.

## 5. 생성자 의존성을 줄인다

coordinator로 이동한 usecase는 `ChatViewModel` 생성자에서 제거한다.
coordinator를 추가했는데 기존 usecase도 그대로 남아 생성자 의존성이 더 커지는 상태는 같은 작업 안에서 정리한다.

## 6. 상수와 로그 태그를 복제하지 않는다

운영 데이터나 디버깅에 쓰이는 상수는 한 곳으로만 이동한다.
기존 Logcat 검색 태그의 의미는 유지하고, 새 coordinator 로그는 기존 태그와 연결해 추적 가능하게 남긴다.

---

# 완료 후 기대 상태

- `ChatViewModel`은 화면 이벤트와 UI state 조립 중심으로 작아진다.
- usage, 분석, 신고, 관심 주제 설정은 각각 독립적으로 테스트하고 수정할 수 있다.
- 세션/오디오/Watch처럼 민감한 흐름은 기존 방어 로직을 유지한 채 후속 분리 대상으로 남긴다.
- 이후 심사나 코드 리뷰에서 "ViewModel이 모든 책임을 가진다"는 지적에 대해, 분리 계획과 1차 개선 결과를 명확히 설명할 수 있다.
