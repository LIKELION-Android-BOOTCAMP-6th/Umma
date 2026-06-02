# [Implementation] CHAT-TUNE-001-A LangState AnalysisMeta Schema v2

## 목적

사용자의 장기 언어능력이 단일 교정 결과로 크게 흔들리지 않도록, `LangState`에 evidence/focus 메타데이터를 저장한다.
이 작업은 Correction signal 적용, AnalysisPolicy 분리, LearnerAdaptationProfile 생성의 선행 작업이다.

---

# 포함 범위

- `LangState.analysisMeta` domain model 추가
- `LangStateAnalysisMeta`, `MetricEvidence`, `LearningFocus` 모델 추가
- schema v2 적용
- schema v1 fallback
- DTO / RemoteDataSource / DataStore mapper / fake / fixture 변경

# 제외 범위

- Correction prompt 실제 튜닝
- Chat prompt 적용
- 점수 계산 policy 대규모 변경
- Statistics 화면 표시 변경

---

# Domain 모델

```kotlin
data class LangState(
    val lang: LangCode,
    val internal: InternalMetrics,
    val external: ExternalMetrics,
    val analysisMeta: LangStateAnalysisMeta = LangStateAnalysisMeta.initial(),
    val schema: Int = 2,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val lastAnalyzedAt: Long? = null,
    val lastAnalysisEventId: String? = null
)
```

```kotlin
data class LangStateAnalysisMeta(
    val metricEvidence: Map<LearningMetricKey, MetricEvidence>,
    val activeFocus: List<LearningFocus>,
    val lastSignalAt: Long? = null
)
```

---

# Metric Evidence

```kotlin
enum class LearningMetricKey {
    GrammarAccuracy,
    VocabularyAppropriateness,
    LexicalDiversity,
    VocabularyLevel,
    SentenceComplexity,
    SpeechRate,
    PauseFrequency,
    AvgUtteranceLength,
    SpokenNaturalness,
    NaturalExpressionUsage,
    ErrorRecurrence,
    ReviewRetention
}
```

```kotlin
data class MetricEvidence(
    val observedCount: Int,
    val confidence: Double,
    val sourceTypes: Set<LearningSignalSource>,
    val direction: EvidenceDirection,
    val directionCount: Int,
    val lastObservedAt: Long? = null
)
```

```kotlin
enum class EvidenceDirection {
    Up,
    Down,
    Stable,
    Mixed
}
```

```kotlin
enum class LearningSignalSource {
    UserTurn,
    CorrectionSignal,
    ReviewEvent,
    TypeBRule,
    TypeCAi
}
```

정책:

- `observedCount`는 해당 metric을 움직일 근거가 몇 번 관찰됐는지 나타낸다.
- `confidence`는 evidence 자체의 신뢰도이며, `0.0..1.0` 범위로 clamp된 보정값만 저장한다.
- raw AI confidence를 그대로 저장하지 않고, source type과 enum 검증 결과를 반영한 보정값으로 저장한다.
- `direction`은 현재 관측이 metric을 올리는지, 내리는지, 유지하는지, 혼합인지 나타낸다.
- `directionCount`는 같은 방향 관측이 얼마나 누적됐는지 나타낸다.
- CEFR level 같은 enum metric은 `directionCount`가 충분히 누적될 때만 한 단계 이동한다.

---

# Learning Focus

```kotlin
data class LearningFocus(
    val type: LearningFocusType,
    val observedCount: Int,
    val confidence: Double,
    val firstObservedAt: Long,
    val lastObservedAt: Long
)
```

```kotlin
enum class LearningFocusType {
    Article,
    Tense,
    Preposition,
    WordOrder,
    SentenceFragment,
    VocabularyChoice,
    LimitedVerbRange,
    UnnaturalCollocation,
    TooFormal,
    MissingContext
}
```

정책:

- active focus는 최대 5개까지만 저장한다.
- 같은 focus가 반복 관측되면 `observedCount`와 `confidence`를 올린다.
- 오래 관측되지 않은 focus는 confidence decay 대상이다.
- confidence가 기준 이하로 내려가면 제거한다.
- Chat/Correction prompt에는 상위 1~2개 focus만 사용한다.
- unknown enum이나 confidence 범위 오류로 drop된 signal은 active focus에 저장하지 않는다.

---

# 저장하지 않는 값

- prompt 문장
- raw correction 전체 히스토리
- unknown enum 문자열
- `difficultyDelta`
- AI가 직접 판단한 최종 능력 점수
- AI가 직접 판단한 최종 CEFR level

---

# Schema v1 Fallback

기존 저장 데이터에는 `analysisMeta`가 없다.
따라서 mapper는 `analysisMeta`가 없으면 `LangStateAnalysisMeta.initial()`로 복원해야 한다.

정책:

- `schemaVersion`이 1이면 `analysisMeta = initial()`
- `schemaVersion`이 없으면 1로 간주하고 fallback
- `metricEvidence`가 없으면 empty map
- `activeFocus`가 없으면 empty list
- unknown enum 값은 drop하고 로그를 남긴다
- confidence가 `0.0..1.0` 범위를 벗어난 저장값은 해당 evidence/focus를 drop하거나 initial 값으로 복구한다

---

# 구현 범위

- `domain/model/learningstate/LearningStateModels.kt`
- `data/model/learningstate/LearningStateDtos.kt`
- `data/source/remote/LearningStateRemoteDataSourceImpl.kt`
- `data/repository/LearningStateRepoImpl.kt`
- fake repository / demo fixture / statistics fixture
- 초기 사용자 생성 경로

---

# 검증 기준

- schema v1 데이터가 앱에서 정상 로딩된다.
- 신규 생성 LangState는 schema v2와 initial analysisMeta를 가진다.
- DTO round-trip에서 metricEvidence와 activeFocus가 보존된다.
- unknown enum은 저장 모델로 복원되지 않는다.
- confidence 범위 오류가 있는 evidence/focus는 안전하게 drop되거나 initial 값으로 복구된다.
- Statistics/Dashboard가 analysisMeta 추가로 깨지지 않는다.
