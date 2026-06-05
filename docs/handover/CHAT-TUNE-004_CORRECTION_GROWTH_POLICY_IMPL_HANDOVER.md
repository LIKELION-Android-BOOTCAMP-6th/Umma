# [Handover] CHAT-TUNE-004 Correction Growth Policy 구현 인계

## 목적

COR-TUNE-003 구현 완료 후 다음 작업자에게 전달하는 인계 문서다.
설계 계약 문서(`CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md`)의 내용이 실제로 어떻게 코드로 구현되었는지,
그리고 LearningState 담당자가 정교화해야 할 부분을 명시한다.

---

## 구현된 계약 위치

| 계약 | 파일 경로 |
| --- | --- |
| `CorrectionGrowthBand` + `CorrectionGrowthPolicy` + 세부 enum 8종 | `domain/model/learningstate/CorrectionGrowthPolicyModels.kt` |
| Band별 기본 정책 매핑 (`defaultsForBand`) | `CorrectionGrowthPolicyModels.kt` companion object |
| `LearnerAdaptationProfile.correctionPolicy` 타입 | `domain/model/learningstate/LearnerAdaptationModels.kt` |
| Band 산출 + 정책 생성 | `domain/usecase/learningstate/BuildLearnerAdaptationProfileUseCase.kt` |
| Prompt 소비부 | `data/repository/correction/CorrectionPromptBuilder.kt` |

### 타입 구조

```kotlin
data class CorrectionGrowthPolicy(
    val band: CorrectionGrowthBand,
    val scope: CorrectionScopePolicy,
    val grammar: GrammarCorrectionPolicy,
    val vocabulary: VocabularyGrowthPolicy,
    val sentenceExpansion: SentenceExpansionPolicy,
    val register: RegisterCorrectionPolicy,
    val explanation: CorrectionExplanationPolicy,
    val newExpressionLimit: NewExpressionLimitPolicy,
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy,  // 기존 enum 재사용
    val meaningPreservation: MeaningPreservationPolicy
)
```

---

## 기본 매핑 표 코드 위치

`CorrectionGrowthPolicyModels.kt`의 `CorrectionGrowthPolicy.defaultsForBand(band)` companion 함수.
핸드오버 문서(`CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md`)의 "Band별 기본 매핑" 표를 1:1로 구현한다.

이 함수는 계약의 일부이므로, 핸드오버 표가 변경되면 여기도 함께 갱신해야 한다.

---

## LearningState 담당자 정교화 지점

### 위치

`BuildLearnerAdaptationProfileUseCase.kt` → `chooseCorrectionGrowthBand()` 함수

### 현재 인터림 로직

- `ProfileConfidence.Low` → 보수적 band(`MeaningFirst`/`PatternFix`) 반환
- 의미차단 focus(`SentenceFragment`/`MissingContext`) → `MeaningFirst`/`PatternFix` 우선
- `grammarStage` ≤ `Developing` → `PatternFix`/`SentenceShape` 반환
- grammar/vocabulary/fluency 2개 이상 `Stable` 이상 → `EverydayNatural`+
- naturalness=`Refined` + vocabulary ≥ `Expanding` + `High` → `NuanceRefine`

### 정교화가 필요한 부분

1. **`analysisMeta.metricEvidence` 기반 band 보수 조정**
   - 현재는 `LearningFocusSummary`의 요약(primaryFocus confidence)만 본다.
   - 실제 evidence 반복 횟수·방향(EvidenceDirection)을 보고 band 상·하향 조정이 필요하다.
   - `CHAT-TUNE-004` FlowDB 스펙 "Band별 기준" 표 참조.

2. **`sentenceComplexity` 독립 방어 조건**
   - 현재는 `langState?.internal.sentenceComplexity`를 grammarStage 대리로 쓴다.
   - 높은 grammarStage + 낮은 sentenceComplexity 조합에서 `SentenceShape` 방어가 필요하다.
   - `CHAT-TUNE-004` FlowDB 스펙 "방어 원칙" 참조.

---

## 변경하지 않은 것

| 항목 | 이유 |
| --- | --- |
| `CorrectionAiResponseMapper` | COR-TUNE-002-FIX 강화판 포함, 이번 작업 범위 밖 |
| `CorrectionLearningSignal v2` 출력 계약 | 신호 생산 방향(Correction→LearningState)은 이번 작업과 직교 |
| 응답 schema 핵심 4필드 | 계약 고정값 |
| `CorrectionAiResponseMapper` learningSignal 규칙 | COR-TUNE-002-FIX 강화판(`meaningPreserved` 필수, unknown enum → signal drop) 보존 |

---

## 제거된 것

- `CorrectionAdaptationPolicy` data class (4단계)
- `ChallengeLevel` enum
- `CorrectionStylePolicy` / `VocabularyStrategyPolicy` / `GrammarStrategyPolicy` / `SpokenRegisterStrategy` enum
- `BuildLearnerAdaptationProfileUseCase`의 `chooseChallengeLevel` / `correctionStyleFor` / `vocabularyStrategyFor` / `grammarStrategyFor` / `registerStrategyFor` / `supportPolicyFor` 함수

위 항목을 참조하던 모든 테스트 fixture는 `CorrectionGrowthPolicy.defaultsForBand(...)` 기반으로 갱신되었다.

---

## 검증 방법

```bash
./gradlew :app:compileDevDebugKotlin :app:compileMockDebugKotlin
./gradlew :app:testDevDebugUnitTest \
  --tests "*CorrectionPromptBuilderTest" \
  --tests "*BuildLearnerAdaptationProfileUseCaseTest" \
  --tests "*CorrectionAiResponseMapperTest" \
  --tests "*BuildPromptUseCaseTest" \
  --tests "*BuildChatSpeechSpeedUseCaseTest" \
  --tests "*BuildChatTurnAdaptationPolicyUseCaseTest"
```

확인 사항:
- main 코드에서 `ChallengeLevel`/`CorrectionAdaptationPolicy` grep 0건
- prompt에 raw metric 숫자(`\d\.\d{2}`)·CEFR·`ChallengeLevel` 흔적 없음
- `CorrectionAiResponseMapperTest` COR-TUNE-002-FIX 케이스 전부 통과 (회귀 없음)
