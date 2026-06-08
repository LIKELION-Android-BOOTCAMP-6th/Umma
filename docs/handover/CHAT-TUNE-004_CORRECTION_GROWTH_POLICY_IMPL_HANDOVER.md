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

## LearningState 담당자 정교화 반영 상태

### 위치

`BuildLearnerAdaptationProfileUseCase.kt` → `chooseCorrectionGrowthBand()` 함수

### 구현 완료된 정책

- `ProfileConfidence.Low` → 보수적 band(`MeaningFirst`/`PatternFix`) 반환
- 의미차단 focus(`SentenceFragment`/`MissingContext`) → `MeaningFirst`/`PatternFix` 우선
- `grammarStage` ≤ `Developing` → `PatternFix`/`SentenceShape` 반환
- grammar/vocabulary/fluency 2개 이상 `Stable` 이상 → `EverydayNatural`+
- naturalness=`Refined` + vocabulary ≥ `Expanding` + `High` → `NuanceRefine`

### 추가 정교화 반영 완료

1. **`analysisMeta.metricEvidence` 기반 band 보수 조정**
   - `correctionGrowthEvidenceProfile()`에서 Mixed/Down 방향, 반복 약점, 상승 근거 개수를 함께 본다.
   - `ConnectedExpression` 계열 상승은 최소 2개 이상 근거가 필요하고, `NuanceRefine` 계열 상승은 최소 3개 이상 근거가 필요하다.
   - 이 조건은 하나의 높은 metric만으로 교정 band가 과하게 상승하지 않도록 막는 보수 장치다.

2. **`sentenceComplexity` 독립 방어 조건**
   - grammar와 별도로 `sentenceComplexity` stage를 계산해 상위 band 진입을 제한한다.
   - 높은 grammar/vocabulary 근거가 있어도 문장 구조 근거가 낮으면 `SentenceShape` 계열 방어가 우선된다.
   - 이 조건은 단어·문법 일부 점수만으로 사용자에게 과한 문장 확장 교정을 주지 않기 위한 방어다.

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
