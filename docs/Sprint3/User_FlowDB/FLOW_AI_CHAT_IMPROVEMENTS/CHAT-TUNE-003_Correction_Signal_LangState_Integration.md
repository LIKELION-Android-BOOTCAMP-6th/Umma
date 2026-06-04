# [Improvement] CHAT-TUNE-003 Correction Signal 기반 LangState 측정 고도화

## 목적

`CHAT-TUNE-001`은 `LangState`를 `LearnerAdaptationProfile`로 해석하는 기반을 만들었다.
`CHAT-TUNE-002`는 Chat이 그 profile을 대화 능력 단계와 turn 보정에 사용하도록 확장했다.
`CHAT-TUNE-003`은 Correction에서 전달되는 `CorrectionLearningSignal v2`를 LearningState 분석 정책에 연결해 사용자 언어능력 측정을 더 의미 있게 만드는 후속 작업이다.

이번 작업의 핵심은 Correction이 최종 능력 점수를 계산하게 만드는 것이 아니다.
Correction은 교정 과정에서 관찰한 오류/개선 신호를 구조화해 넘기고, LearningState가 그 신호를 장기 metric, evidence, active focus, challenge guard로 변환한다.

---

# User Story

사용자는 교정 결과를 저장한 뒤, 다음 AI 대화와 교정에서 자신의 실제 약점과 성장 방향이 더 일관되게 반영되기를 기대한다.
Umma는 단순히 교정 개수만 세지 않고, 어떤 오류가 반복되는지, 어떤 표현이 확장됐는지, 교정이 현재 능력보다 과했는지를 구분해 학습 난이도를 조절한다.

---

# 완료 기준(AC)

- [ ] Correction에서 받은 learning signal은 최종 점수나 레벨이 아니라 관찰 신호로만 저장/해석된다.
- [ ] `CorrectionResult`는 기존 `correctedText`, `correctionCount`, `notes` 흐름을 유지하면서 `learningSignals`를 함께 전달할 수 있다.
- [ ] `sourceTurnId`가 있으면 원본 turn 추적에 사용하고, 없으면 `sourceTurnIndex`로 안전하게 fallback할 수 있다.
- [ ] `LangStateAnalysisPolicy`는 learning signal이 있으면 signal 기반으로 evidence와 active focus를 갱신할 수 있다.
- [ ] learning signal이 없으면 기존 `correctionCount` 기반 fallback 계산이 유지된다.
- [ ] unknown enum이나 잘못된 confidence가 있어도 correction 저장 자체는 실패하지 않고 해당 signal만 제외된다.
- [ ] meaning이 보존되지 않은 signal은 장기 능력 점수에 직접 반영되지 않는다.
- [ ] source/corrected 문장과 edit span을 비교해 교정이 과하게 확장됐는지 판단하는 guard를 적용할 수 있다.
- [ ] Chat과 Correction은 raw metric을 직접 해석하지 않고 계속 `LearnerAdaptationProfile`을 통해 사용자 능력을 재사용한다.
- [ ] 기존 Chat transport, SessionMemory 저장, usage tracking, final transcript, 마이크 상태 흐름은 변경하지 않는다.

---

# 포함 범위

- `CorrectionLearningSignal v2` domain 모델 추가
- `CorrectionResult.learningSignals` 추가
- `CorrectionIssueCategory`, `LanguageFeatureSignal`, `CorrectionImprovementType`, `CorrectionEditSpan`, `SpokenRegister`, `CorrectionSeverity` 모델 추가
- signal validation 정책 추가
- `LangStateAnalysisPolicy` signal 기반 evidence/focus 계산 추가
- 기존 `correctionCount` 기반 계산 fallback 유지
- source/corrected 비교 기반 difficulty guard 추가
- 관련 단위 테스트 추가

---

# 제외 범위

- Correction prompt 실제 작성
- Correction AI 응답 DTO와 mapper 구현
- 교정 화면 UI 변경
- Chat prompt 재튜닝
- 최종 CEFR level을 Correction AI가 직접 반환하는 구조
- speech rate, pause, hesitation, pronunciation 같은 음성 분석
- character offset 기반 edit 위치 검증

---

# 기준 문서

- [CHAT-TUNE-001 Correction Learning Signal 계약](../../../handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md)
- [CHAT-TUNE-001-B LangState AnalysisPolicy](./CHAT-TUNE-001/CHAT-TUNE-001-B_LangState_AnalysisPolicy.md)
- [CHAT-TUNE-001-C LearnerAdaptationProfile](./CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md)
- [CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)
- [LS-001 Language State Model Structure](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 현재 코드 상태

현재 `CorrectionResult`는 아래 최소 필드만 가진다.

```kotlin
data class CorrectionResult(
    val correctedText: String? = null,
    val correctionCount: Int = 0,
    val notes: String? = null
)
```

현재 `LangStateAnalysisPolicy`는 `correctionCount`를 교정 밀도의 proxy로 사용한다.
이 방식은 기존 MVP fallback으로는 유지할 수 있지만, 반복 약점, 교정 확장 정도, 구어체 자연스러움, 10% 성장 guard를 세밀하게 판단하기에는 부족하다.

이미 준비된 구조:

- `LangState.internal`: 장기 metric 저장
- `LangState.external`: 화면/통계 표시용 projection
- `LangState.analysisMeta.metricEvidence`: metric별 누적 근거 저장
- `LangState.analysisMeta.activeFocus`: 다음 Chat/Correction에 쓸 반복 약점 저장
- `LearningSignalSource.CorrectionSignal`: Correction에서 온 신호 출처 구분

따라서 `LangState` 전체 저장 구조를 갈아엎기보다, `CorrectionResult` 입력과 `LangStateAnalysisPolicy` 계산 정책을 확장하는 방식이 적합하다.

---

# 권장 모델 방향

```kotlin
data class CorrectionResult(
    val correctedText: String? = null,
    val correctionCount: Int = 0,
    val notes: String? = null,
    val learningSignals: List<CorrectionLearningSignal> = emptyList()
)
```

```kotlin
data class CorrectionLearningSignal(
    val candidateId: String,
    val sourceTurnId: String?,
    val sourceTurnIndex: Int,
    val sourceText: String,
    val correctedText: String,
    val issueCategories: List<CorrectionIssueCategory>,
    val languageFeatures: List<LanguageFeatureSignal>,
    val improvementTypes: List<CorrectionImprovementType>,
    val editSpans: List<CorrectionEditSpan>,
    val register: SpokenRegister,
    val severity: CorrectionSeverity,
    val meaningPreserved: Boolean,
    val confidence: Double?
)
```

정책:

- `sourceText`와 `correctedText`는 난이도 변화와 의미 보존 판단의 기본 재료다.
- `sourceTurnId`는 SessionMemory 원본 turn 추적에 사용하고, 없으면 `sourceTurnIndex`를 fallback으로 사용한다.
- `issueCategories`는 공통 metric과 active focus의 1차 근거다.
- `languageFeatures`는 언어별 세부 focus 후보로 사용하되 최종 점수에 직접 연결하지 않는다.
- `improvementTypes`는 교정이 단순 수정인지, 구조 확장인지, 구어체 개선인지 판단하는 근거다.
- `editSpans`는 source/corrected 전체 문장보다 더 작은 변경 조각을 제공한다.
- `severity`는 LearningState 반영 weight와 focus 우선순위에 사용한다.
- `meaningPreserved = false`인 signal은 장기 score 이동에는 직접 반영하지 않고 품질/방어 신호로만 사용한다.
- `confidence`는 raw AI 판단이므로 0.0..1.0 검증 후 보정 weight로만 사용한다.
- `CorrectionImprovementType`에는 `MeaningPreserved`를 두지 않고, 의미 보존은 `meaningPreserved` boolean만 사용한다.
- `languageFeatures.featureKey`는 `{LANG}.{FeatureName}` namespace와 초기 allowlist를 기준으로 검증한다.

---

# 분석 정책 방향

## 1. Existing Fallback 유지

learning signal이 없으면 기존 계산을 유지한다.

- `correctionCount / userTurns` 기반 grammar proxy
- `correctionCount / userTurns` 기반 naturalness proxy
- correction density 기반 error recurrence proxy

이 fallback은 Correction v2 계약이 아직 연결되지 않은 화면이나 테스트를 깨지 않기 위해 필요하다.

## 2. Signal 기반 Evidence

learning signal이 있으면 `LangState.analysisMeta.metricEvidence`에 `LearningSignalSource.CorrectionSignal` 근거를 누적한다.

예:

| signal | 갱신 후보 |
| --- | --- |
| `GrammarForm`, `WordOrder`, `SentenceCompleteness` | `GrammarAccuracy`, `SentenceComplexity` evidence |
| `VocabularyChoice`, `Collocation` | `VocabularyAppropriateness`, `NaturalExpressionUsage` evidence |
| `Register`, `SpokenExpressionAdded`, `MadeMoreCasual` | `SpokenNaturalness`, `NaturalExpressionUsage` evidence |
| `MissingContext` | `SentenceComplexity`, `GrammarAccuracy` evidence |

정책:

- 단일 signal은 focus에는 빠르게 반영할 수 있지만 장기 score는 천천히 움직인다.
- 같은 방향 관측이 반복될 때만 `directionCount`를 올린다.
- 서로 충돌하는 signal은 `EvidenceDirection.Mixed`로 기록해 profile confidence를 낮춘다.

## 3. Active Focus

`issueCategories`와 `languageFeatures`를 active focus 후보로 변환한다.

정책:

- focus는 최대 5개까지만 저장한다.
- prompt/profile에는 상위 1~2개만 노출한다.
- confidence가 낮거나 오래 관측되지 않은 focus는 decay 후 제거한다.
- 언어별 feature는 raw 문자열을 prompt에 그대로 넣지 않고 profile builder에서 안전한 설명으로 변환한다.

## 4. Difficulty Guard

`difficultyDelta`는 Correction AI에게 받지 않는다.
LearningState가 source/corrected, editSpans, improvementTypes, register, 기존 LangState를 비교해 guard를 계산한다.

계산 후보:

- source/corrected token 수 차이
- 평균 문장 길이 차이
- editSpans 수
- `StructureExpanded`, `SpokenExpressionAdded`, `BetterCollocation` 여부
- correctedText register
- 기존 grammar/vocabulary/fluency/naturalness stage
- severity
- confidence

초기 guard 방향:

- correctedText가 sourceText보다 짧거나 비슷하고 `GrammarFixed` 중심이면 current ability 범위 안의 수정으로 본다.
- correctedText가 길어지고 `StructureExpanded`, `SpokenExpressionAdded`, `BetterCollocation`이 함께 있으면 stretch 후보로 본다.
- stretch 후보가 반복되더라도 기존 LangState가 낮고 confidence가 낮으면 장기 score를 크게 올리지 않는다.
- meaning이 보존되지 않았거나 confidence가 낮으면 장기 score 이동을 막고 focus 후보로만 제한한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Correction AI | 교정 결과와 관찰 신호를 구조화한다. 최종 점수/레벨은 판단하지 않는다. |
| Correction mapper | AI 응답 DTO를 domain signal로 변환하고 enum/confidence를 검증한다. |
| LearningState domain | signal을 evidence/focus/score/challenge guard로 해석한다. |
| LearningState repo/data source | 계산된 LangState를 저장하고 sync한다. signal의 교육적 의미를 해석하지 않는다. |
| LearnerAdaptationProfile | 저장된 LangState를 Chat/Correction이 재사용할 정책으로 변환한다. |
| Chat | 이미 계산된 profile/prompt/turn policy를 사용한다. Correction signal을 직접 해석하지 않는다. |

---

# 작업 순서

실제 백로그 이슈는 아래처럼 2~3개 단위로 나누는 것을 권장한다.
모델/검증, evidence/focus, difficulty guard를 한 번에 구현하면 회귀 원인을 분리하기 어렵다.

1. 모델/검증 단계
   - `CorrectionLearningSignal v2` domain 모델을 추가한다.
   - `CorrectionResult`에 `learningSignals`를 추가하되 기본값을 `emptyList()`로 둔다.
   - 기존 `CorrectionResult` 생성 경로가 깨지지 않는지 확인한다.
   - signal validation helper를 추가한다.
   - invalid enum/confidence/sourceTurnId fallback 테스트를 추가한다.
2. evidence/focus 단계
   - `LangStateAnalysisPolicy`에 signal 기반 evidence 계산을 추가한다.
   - active focus 갱신 정책을 추가한다.
   - signal이 없을 때 기존 fallback이 유지되는지 테스트한다.
   - meaning not preserved signal이 장기 score를 직접 움직이지 않는지 테스트한다.
3. difficulty guard 단계
   - source/corrected 비교 기반 difficulty guard를 추가한다.
   - editSpans, improvementTypes, register, severity, confidence를 함께 사용한다.
   - `LearnerAdaptationProfile`이 새 evidence/focus를 안전하게 요약하는지 확인한다.

---

# 검증 기준

- `git diff --check`
- 가능하면 `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`
- learning signal이 없는 기존 correction flow가 깨지지 않는다.
- invalid signal이 correction 저장 전체를 실패시키지 않는다.
- confidence 범위 밖 signal은 LangState에 반영되지 않는다.
- meaning not preserved signal은 장기 score를 직접 움직이지 않는다.
- signal 기반 focus가 최대 개수와 confidence 정책을 지킨다.
- difficulty guard가 source/corrected 비교 없이 추정값으로 동작하지 않는다.
- Repository/data layer가 signal의 교육적 의미를 직접 해석하지 않는다.
- Chat prompt, SessionMemory, usage tracking, final transcript, 마이크 상태 흐름에 회귀가 없다.
