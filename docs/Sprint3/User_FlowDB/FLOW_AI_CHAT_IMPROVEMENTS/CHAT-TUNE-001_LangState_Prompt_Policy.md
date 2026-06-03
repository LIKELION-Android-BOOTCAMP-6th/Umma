# [Improvement] CHAT-TUNE-001 LearningState 기반 AI 적응 정책

## User Story

사용자는 별도 레벨 테스트를 하지 않아도, 실제 발화와 교정 결과를 기반으로 현재 언어능력에 맞는 AI 대화와 교정을 받는다.
Umma는 사용자가 아이처럼 부족하게 말해도 대화를 끊지 않고 리드하며, 현재 능력보다 약간 더 성장할 수 있는 표현과 문장 구조를 자연스럽게 제안한다.

---

# 완료 기준(AC)

- [ ] 신규 사용자와 기존 사용자 모두 `LangState`를 정상 로딩할 수 있다.
- [ ] `LangState`에는 장기 능력 판단에 필요한 근거와 반복 학습 초점만 저장된다.
- [ ] 언어능력 계산 로직은 `LearningState` domain에 있고, Repository/Data layer는 저장과 sync만 담당한다.
- [ ] 기존 LearningState 업데이트 결과는 정책 분리 후에도 유지된다.
- [ ] 같은 분석 이벤트가 중복으로 들어와도 사용자의 능력 점수가 중복 반영되지 않는다.
- [ ] Chat과 Correction은 raw metric 숫자를 직접 해석하지 않고 같은 `LearnerAdaptationProfile`을 사용한다.
- [ ] 분석 근거가 부족한 사용자는 낮은 실력으로 단정하지 않고 보수적인 profile로 처리한다.
- [ ] 반복 학습 초점이 많아도 Chat/Correction에는 중요한 1~2개만 전달된다.
- [ ] Chat 새 대화 시작과 재연결은 같은 profile 기반 prompt 생성 경로를 사용한다.
- [ ] Correction 담당자가 prompt tune을 시작할 수 있도록 learning signal 계약과 profile 사용 방식이 문서화된다.
- [ ] Correction learning signal의 unknown enum/confidence 오류는 correction 저장을 막지 않고 해당 signal만 제외한다.
- [ ] transport, SessionMemory 저장, usage tracking, final transcript, 마이크 버튼 상태는 이번 tune 작업으로 변경하지 않는다.

---

# 문서 구조

Correction 담당자에게 전달할 계약 문서는 구현 문서 번호에서 분리한다.
나머지 문서는 내가 구현해야 할 순서대로 `A~D` 번호를 사용한다.

| 문서 | 목적 | 주 사용자 |
| --- | --- | --- |
| `CHAT-TUNE-001_LangState_Prompt_Policy.md` | 전체 overview, 책임 경계, 작업 순서 | 전체 |
| `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md` | Correction 담당자 전달용 signal 계약 | Correction 담당자 |
| `CHAT-TUNE-001/CHAT-TUNE-001-A_LangState_AnalysisMeta_Schema_v2.md` | `analysisMeta` 저장 모델과 schema v2 | LearningState 담당 |
| `CHAT-TUNE-001/CHAT-TUNE-001-B_LangState_AnalysisPolicy.md` | 기존 계산 로직을 domain policy로 분리하고 이후 evidence/focus 계산을 확장할 위치 정리 | LearningState 담당 |
| `CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md` | LangState를 Chat/Correction 정책으로 해석하는 read model | LearningState/Chat/Correction |
| `CHAT-TUNE-001/CHAT-TUNE-001-D_Chat_Prompt_Integration.md` | Chat prompt 적용 경로 | Chat 담당 |

---

# 기준 문서

- `RULES.md`
- `AGENTS.md`
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md`
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md`
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-007_Initial_Learning_State_Persistence_Contract.md`
- `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION.md`
- `docs/System_FlowDB/SYS_CORRECTION_INFRA.md`
- `docs/Umma_Planning_Notes.md`
- `docs/Umma_Language_State_Review_and_Metrics.md`
- `docs/Umma_Language_State_Optimization.md`

---

# 핵심 결정

- `LearningState`는 학습 상태 시스템 전체를 가리키는 상위 개념이고, 실제 언어별 장기 능력 스냅샷은 `LangState`다.
- `LangState`는 저장 모델이고 prompt instruction 자체가 아니다.
- `LearnerAdaptationProfile`은 저장 모델이 아니라 AI 기능이 사용할 교육 전략 read model이다.
- `primaryLang`은 사용자가 학습을 이해하고 설명을 받을 기준 언어다. 모국어로 단정하지 않으며, 사용자가 익숙한 언어 또는 학습 기준으로 선택한 언어일 수 있다.
- `selectedLang`은 현재 대화, 교정, LangState, Statistics, SRS가 바라보는 학습 대상 언어다.
- `primaryLang`과 `selectedLang`은 같을 수도 다를 수도 있으며, 데이터 소속과 능력 측정은 항상 `selectedLang` 기준으로 유지한다.
- Correction은 교정 결과와 구조화된 learning signal을 제공하지만, 사용자의 최종 점수/레벨/profile을 결정하지 않는다.
- `difficultyDelta`는 Correction signal에 넣지 않는다. 난이도 변화와 “10% 성장” 판단은 LearningState가 source/corrected 문장, improvement type, 기존 LangState를 비교해 계산한다.
- unknown enum이나 confidence 범위 오류가 있는 learning signal은 drop하되, correction result 저장 자체는 막지 않는다.
- `LangState.analysisMeta`에는 metric evidence와 active focus만 compact하게 저장한다.
- `analysisMeta`에는 prompt text, raw correction history, unknown enum, `difficultyDelta`를 저장하지 않는다.
- `MetricEvidence`는 `direction`과 `directionCount`를 포함해 같은 방향 관측이 누적되는지 확인한다.
- `LearningFocus`는 최대 5개까지만 저장하고, 오래 관측되지 않은 focus는 confidence decay 후 제거한다.
- Chat/Correction prompt에는 저장된 active focus 전체가 아니라 상위 1~2개 focus만 사용한다.
- focus는 빠르게, score는 천천히, CEFR level은 가장 천천히 움직인다.
- AI Chat과 Correction은 raw `LangState` metric을 직접 해석하지 않고 같은 `LearnerAdaptationProfile` 계약을 사용한다.
- Correction prompt builder는 `LearnerAdaptationProfile.correctionPolicy`를 instruction text로 바꾸고, raw `LangState` metric을 직접 해석하지 않는다.
- Chat/Correction의 보조 설명 언어 정책은 특정 언어명(Korean/English 등)에 고정하지 않고, `primaryLang`과 `selectedLang`의 상대 관계로 결정한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Correction AI | 교정 결과와 구조화된 learning signal 생성 |
| Correction domain | AI 응답을 domain 모델로 정규화하고 completion pipeline에 전달 |
| LearningState domain | signal과 user turn을 점수화하고 `LangState` 갱신 |
| LangStateAnalysisPolicy | 난이도 변화, evidence, confidence, smoothing, max delta, focus 후보 계산 |
| LearningStateRepo | prepared state 저장, summary 갱신, local/remote sync |
| BuildLearnerAdaptationProfileUseCase | `LangState?`를 prompt 정책용 profile로 해석 |
| Chat BuildPromptUseCase | profile을 Chat system instruction으로 변환 |
| Correction prompt builder | 같은 profile을 교정 난이도/설명 방식에 반영 |
| ChatRepositoryImpl | 완성된 instruction을 OpenAI Realtime에 전달 |

---

# 전체 작업 순서

1. Correction 담당자에게 `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md`를 전달해 병렬 작업 기준을 맞춘다.
2. `CHAT-TUNE-001-A`에서 `LangState.analysisMeta` schema v2와 schema v1 fallback을 구현한다.
3. `CHAT-TUNE-001-B` 1차에서 기존 `prepareNextState()`를 `LangStateAnalysisPolicy`로 보존 이동한다.
4. `CHAT-TUNE-001-B` 2차에서 Correction learning signal이 코드 계약에 들어온 뒤 evidence/focus/difficulty guard를 추가한다.
5. `CHAT-TUNE-001-C`에서 `LearnerAdaptationProfile`과 `BuildLearnerAdaptationProfileUseCase`를 추가한다.
6. `CHAT-TUNE-001-D`에서 Chat `BuildPromptUseCase`, `StartSessionUseCase`, `RetryConnectionUseCase`를 profile 기반으로 연결한다.
7. Correction 실제 prompt tune은 Correction 담당자가 같은 handover/profile 계약 위에서 진행한다.

주의:

- handover 문서는 Correction 담당자에게 전달된 외부 계약이므로, enum/필드 의미를 바꾸지 않는다.
- 우리 구현 문서의 보완은 handover 계약 변경이 아니라, handover signal이 `LangStateUpdateInput`과 `LangStateAnalysisPolicy`로 들어오는 내부 연결 경로를 명확히 하는 작업이다.
- handover 계약 자체를 바꿔야 하는 경우에만 Correction 담당자에게 수정본을 다시 공유한다.

---

# 검증 기준

- Correction signal이 최종 점수가 아니라 관찰 신호만 담는지 확인한다.
- `LangState.analysisMeta`가 schema v2로 저장되고 schema v1 fallback이 안전하게 동작하는지 확인한다.
- `MetricEvidence`가 같은 방향 관측 누적을 추적해 score/level 급변을 막는지 확인한다.
- `LearningFocus`가 최대 개수, decay, 제거 기준을 가져 오래된 focus가 prompt에 남지 않는지 확인한다.
- unknown enum/confidence 오류가 correction 저장 전체를 막지 않고 learning signal만 drop하는지 확인한다.
- `LangStateAnalysisPolicy`가 repository/data layer가 아니라 domain layer에 위치하는지 확인한다.
- 기존 중복 분석 방어와 저장 흐름이 유지되는지 확인한다.
- `LearnerAdaptationProfile`이 raw metric 직접 노출이 아니라 정책 enum 중심으로 구성되는지 확인한다.
- Correction prompt builder가 `LearnerAdaptationProfile.correctionPolicy`를 사용하고 raw `LangState` metric을 직접 해석하지 않는지 확인한다.
- Chat session start/retry가 같은 profile 기반 prompt를 사용하는지 확인한다.
- transport, SessionMemory 저장, usage tracking, final transcript, 마이크 버튼 상태에 회귀가 없는지 확인한다.
- 신규/수정 정책 코드에는 왜 해당 보정과 fallback이 필요한지 충분한 주석이 남는지 확인한다.
