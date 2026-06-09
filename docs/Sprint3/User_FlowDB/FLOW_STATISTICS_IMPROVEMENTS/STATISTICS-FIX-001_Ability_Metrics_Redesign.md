# [Improvement] STATISTICS-FIX-001 언어능력 통계 지표 개편

## 목적

현재 Statistics 화면은 `LangState.external`의 기존 5개 지표를 그대로 표시한다.
이 지표들은 MVP 단계의 요약값으로는 유효하지만, 사용자가 자신의 실제 언어능력과 성장 방향을 직관적으로 이해하기에는 부족하다.

이번 작업은 Statistics 화면의 사용자-facing 지표를 Umma의 대화 학습 기획에 맞게 다시 정의한다.
특히 CEFR 기반 레벨을 그대로 노출하지 않고, Chat에서 관찰한 대화 능력 band를 종합 레벨로 사용한다.

---

# User Story

사용자는 통계 화면에서 자신의 현재 학습언어 능력이 어느 단계인지 쉽게 이해할 수 있다.
사용자는 어휘, 말하기, 문법, 이해력, 표현력 중 어떤 영역이 준비되었고 어떤 영역이 아직 측정 준비 중인지 알 수 있다.
Umma는 아직 신뢰할 수 없는 지표를 점수처럼 과장해서 보여주지 않고, 실제 관찰 가능한 데이터에 근거한 지표만 먼저 제공한다.

---

# 완료 기준(AC)

- [ ] 통계 화면의 종합 레벨은 `시작/단어/문장/대화/표현/능숙 Level` 중 하나로 표시된다.
- [ ] 종합 레벨은 별도 평균 점수가 아니라 LangState에 누적된 Chat 대화 능력 band 관측 결과를 기준으로 산출된다.
- [ ] 사용자는 종합 레벨 카드를 눌러 모든 Level의 의미를 한 번에 확인할 수 있다.
- [ ] `어휘`와 `표현력` 카드는 이번 작업에서 점수를 계산하지 않고 `측정 준비 중`으로 표시된다.
- [ ] `말하기`는 발음 점수가 아니라 LangState가 누적한 학습언어 대화 흐름 근거를 기준으로 표시된다.
- [ ] `문법`은 단순 교정 개수가 아니라 LangState가 누적한 교정 오류 범주와 심각도 집계값을 기준으로 표시된다.
- [ ] `이해력`은 LangState가 누적한 학습언어 이해와 보조 의존도 근거를 기준으로 표시된다.
- [ ] 근거가 부족한 지표는 높은 점수처럼 표시하지 않고 `측정 중` 또는 낮은 신뢰도로 처리된다.
- [ ] Statistics는 Chat transcript나 Correction raw signal을 직접 재분석하지 않는다.
- [ ] 기존 Chat, Correction, Flashcard, LearningState 저장 흐름은 변경되지 않는다.
- [ ] 기존 `ExternalMetrics` 저장값이 있어도 앱이 안전하게 동작한다.

---

# 기준 문서

- [SYS_STATISTICS_INFRA](../../../System_FlowDB/SYS_STATISTICS_INFRA.md)
- [STI-001 StatisticsHistory 모델 및 Repository 계약](../../../System_FlowDB/SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [STI-002 History Record Policy](../../../System_FlowDB/SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)
- [CHAT-TUNE-007 Chat band source를 LangState summary로 일원화](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-007_Chat_Evidence_Summary_Band_Source.md)
- [CHAT-TUNE-006 Chat Evidence LangState Integration](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-006_Chat_Evidence_LangState_Integration.md)
- [CHAT-TUNE-003 Correction Signal LangState Integration](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-003_Correction_Signal_LangState_Integration.md)
- [CHAT-TUNE-004 Correction Growth Policy](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-004_Correction_Growth_Policy.md)

---

# 핵심 결정

- **종합 레벨은 Chat 대화 능력 band를 사용자 표시명으로 변환한다.**
  - `IntentOnly` → `시작 Level`
  - `PhraseEmerging` → `단어 Level`
  - `SimpleSentence` → `문장 Level`
  - `BasicConversation` → `대화 Level`
  - `ConnectedExpression` → `표현 Level`
  - `NuanceControl` → `능숙 Level`

- **이번 작업의 화면 카드는 `종합 레벨 + 5개 영역`으로 고정한다.**
  - `종합 레벨`
  - `어휘`
  - `말하기`
  - `문법`
  - `이해력`
  - `표현력`

- **신뢰할 수 있는 데이터가 부족한 지표는 계산하지 않는다.**
  - `어휘`는 최근/누적 구사 단어 ledger가 필요하므로 이번 작업에서는 `측정 준비 중`으로 둔다.
  - `표현력`은 장기적인 문장 확장, 구어체 표현, collocation, register 관측 누적이 필요하므로 이번 작업에서는 `측정 준비 중`으로 둔다.

- **기존 `ExternalMetrics`를 바로 제거하지 않는다.**
  - 기존 저장/동기화/히스토리 호환성을 위해 유지한다.
  - 새 Statistics 화면은 통계 표시용 read model을 통해 사용자-facing 지표를 조립한다.

- **Statistics는 raw evidence를 다시 해석하지 않는다.**
  - Chat transcript, `ChatConversationEvidence`, `CorrectionLearningSignal`의 원문 해석은 LangState 책임이다.
  - Statistics는 LangState가 누적·집계한 능력 요약값을 사용자 표시 모델로 바꾼다.

- **LangState에 통계용 누적 근거를 보강한다.**
  - 종합 레벨은 최신 Chat summary 하나가 아니라 band 관측 누적으로 판단한다.
  - 말하기/이해력/문법은 각각 evidence count, confidence, 최근 관측 시각을 함께 가진다.

- **점수보다 근거와 신뢰도를 우선한다.**
  - 근거가 부족하면 점수를 억지로 만들지 않는다.
  - 신뢰도가 낮은 분석은 종합 레벨 상향에 강하게 쓰지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Statistics UI | 종합 레벨과 지표 카드를 표시한다. 계산 정책을 직접 가지지 않는다. |
| Statistics ViewModel | 화면 상태를 전달하고 사용자의 선택 상태만 관리한다. |
| Statistics UseCase | LangState의 누적 능력 요약을 사용자 표시 모델로 변환한다. raw evidence를 직접 재해석하지 않는다. |
| LearningState domain | Chat/Correction에서 들어온 evidence를 검증·누적·집계해 통계가 읽을 수 있는 능력 요약을 제공한다. |
| Chat domain | 세션 종료 후 대화 능력 evidence와 band source를 제공한다. |
| Correction domain/data | 교정 과정에서 관찰한 오류 범주, severity, edit signal을 LearningState에 전달한다. |
| Repository/DataSource | 저장과 조회만 담당하고 통계 점수 정책을 계산하지 않는다. |

---

# 표시 지표 정의

## 1. 종합 레벨

종합 레벨은 사용자의 말하기 중심 대화 능력을 대표한다.
Umma의 핵심 목표가 “AI와 반복적으로 대화하며 말하기 능력을 성장시키는 것”이므로, 종합 레벨은 별도 평균 점수가 아니라 Chat 대화 능력 band를 기준으로 한다.

표시명:

| 내부 band | 사용자 표시 |
| --- | --- |
| `IntentOnly` | 시작 Level |
| `PhraseEmerging` | 단어 Level |
| `SimpleSentence` | 문장 Level |
| `BasicConversation` | 대화 Level |
| `ConnectedExpression` | 표현 Level |
| `NuanceControl` | 능숙 Level |

계산 원칙:

- 최신 Chat 분석 하나만으로 급격히 올리지 않는다.
- `confidence=Low`는 “낮은 실력 확정”이 아니라 “분석 신뢰도 낮음”으로 해석한다.
- 같은 band 이상이 반복 관측될 때 상향한다.
- 낮은 band도 반복 관측되거나 높은 신뢰도로 확인될 때만 하향한다.
- 관측 수가 부족하면 `측정 중` 상태를 허용한다.
- Statistics는 `chatEvidenceSummary` 최신값을 직접 레벨로 표시하지 않고, LangState가 누적한 band 관측 집계값을 읽는다.

권장 LangState 집계:

```kotlin
data class LangAbilityStats(
    val conversationBand: ConversationBandStats,
    val speaking: SpeakingFlowStats,
    val grammar: GrammarAbilityStats,
    val comprehension: ComprehensionStats,
    val expression: ExpressionReadinessStats?,
    val vocabulary: VocabularyReadinessStats?
)

data class ConversationBandStats(
    val currentBand: ConversationAbilityBand?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
)
```

주의:

- `currentBand`는 단일 세션 결과가 아니라 누적 관측을 통과한 대표 band다.
- `observedCount`가 부족하면 Statistics는 `측정 중`으로 표시할 수 있다.

## 2. 어휘

이번 작업에서는 `측정 준비 중`으로 표시한다.

이유:

- 현재 `expressionRange`는 실제 누적 구사 어휘 수가 아니다.
- 사용자가 직접 말한 selectedLang 단어와 AI가 교정으로 제안한 단어를 분리하는 ledger가 아직 없다.
- 영어 외 언어는 단순 공백 token 기준으로 구사 단어 수를 계산하면 신뢰도가 낮다.

후속 방향:

- USER final transcript에서 selectedLang으로 직접 구사한 단어/표현만 누적한다.
- 최근 window와 누적 count를 분리한다.
- primaryLang과 제3언어는 구사 어휘 count에서 제외한다.
- correction으로 새로 제안된 표현은 “구사 어휘”가 아니라 “학습 후보 표현”으로 별도 관리한다.

목표 표시:

```text
어휘
측정 준비 중
```

후속 완성 후 목표 표시:

```text
어휘
최근 10 / 누적 240
```

## 3. 말하기

말하기는 발음 평가가 아니라 “학습언어로 대화를 이어가는 흐름”을 의미한다.

사용할 근거:

- `targetLanguageProduction`
- `conversationSustainability`
- `aiScaffoldingDependence`
- 사용자 turn의 `durationMs`
- 사용자 turn의 `tokenCount`
- STT `confidence`

계산 원칙:

- 빠르게 말한다고 무조건 높게 보지 않는다.
- 긴 문장 하나보다 여러 turn에서 독립적으로 이어간 근거를 더 중요하게 본다.
- 초급 사용자는 짧은 문장이라도 AI scaffold 없이 직접 말하면 긍정 근거로 본다.
- 발음 품질은 현재 데이터로 직접 측정하지 않는다.
- Statistics는 사용자 turn을 다시 분석하지 않고, LangState에 누적된 말하기 흐름 집계값을 읽는다.

권장 LangState 집계:

```kotlin
data class SpeakingFlowStats(
    val score: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
)
```

주의:

- `score`는 발음 점수가 아니라 학습언어 생산과 대화 지속성 기반의 흐름 점수다.
- `durationMs`, `tokenCount`, STT confidence가 부족하면 `score=null` 또는 낮은 confidence로 둔다.

## 4. 문법

문법은 correction signal의 오류 범주와 심각도를 기준으로 계산한다.
단순히 교정 개수가 많다는 이유만으로 문법이 나쁘다고 보지 않는다.

사용할 근거:

- `CorrectionIssueCategory.GrammarForm`
- `CorrectionIssueCategory.WordOrder`
- `CorrectionIssueCategory.SentenceCompleteness`
- `CorrectionSeverity`
- `CorrectionEditSpan`
- `meaningPreserved`
- `confidence`

계산 원칙:

- 문법 오류 밀도는 분석 가능한 사용자 학습언어 문장 수 대비 weighted error count로 계산한다.
- `BlockingMeaning`은 강한 감점 근거로 본다.
- `MajorPattern`은 중간 감점 근거로 본다.
- `MinorForm`은 약한 감점 근거로 본다.
- `NaturalnessOnly`는 문법 감점으로 직접 쓰지 않는다.
- 의미가 보존되지 않은 signal은 장기 문법 점수에 직접 반영하지 않는다.
- Statistics는 `CorrectionLearningSignal`을 직접 읽지 않고, LangState가 저장한 문법 오류 밀도와 confidence를 읽는다.

권장 LangState 집계:

```kotlin
data class GrammarAbilityStats(
    val score: Double?,
    val weightedErrorDensity: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
)
```

주의:

- 교정 후보가 없다는 사실을 문법이 좋다는 뜻으로 해석하지 않는다.
- `score`는 `weightedErrorDensity`와 evidence count가 충분할 때만 표시한다.

## 5. 이해력

이해력은 학습언어 입력을 이해하고 실제 대화에서 반응할 수 있는 정도를 의미한다.
시험식 청해 점수가 아니라 Umma 대화 흐름 안에서의 이해 근거다.

사용할 근거:

- `targetLanguageComprehension`
- `responseDifficultyFit`
- `supportLanguageDependence`
- `aiScaffoldingDependence`
- `conversationSustainability`
- `confidence`

계산 원칙:

- 학습언어 문장에 반복적으로 적절히 반응한 경우 긍정 근거로 본다.
- 기준언어 설명이 있어야만 반응한 경우 이해력 상향을 제한한다.
- AI가 선택지나 힌트로 강하게 scaffold한 경우 이해력 상향을 제한한다.
- 난이도가 너무 쉬운 대화만 반복된 경우 높은 이해력으로 과대평가하지 않는다.
- Statistics는 Chat evidence 원문을 직접 읽지 않고, LangState가 저장한 이해력 집계값을 읽는다.

권장 LangState 집계:

```kotlin
data class ComprehensionStats(
    val score: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
)
```

주의:

- `score`는 이해 수준, 기준언어 의존도, AI scaffold 의존도, 난이도 적합도를 함께 반영한다.
- 낮은 confidence 세션 하나로 이해력이 급격히 오르거나 내려가지 않게 한다.

## 6. 표현력

이번 작업에서는 `측정 준비 중`으로 표시한다.

이유:

- 표현력은 단어 수가 아니라 문장 확장, 표현 변화, 구어체 자연스러움, collocation, register를 함께 봐야 한다.
- 현재 correction signal만으로 단발 개선은 알 수 있지만, 사용자가 실제로 표현을 재사용하는지는 아직 안정적으로 누적하지 않는다.
- 어휘력과 겹치지 않으려면 별도 장기 표현 사용 근거가 필요하다.

후속 방향:

- `StructureExpanded`, `BetterCollocation`, `SpokenExpressionAdded`, `MadeMoreCasual`, `MadeMorePolite` 관측을 누적한다.
- Chat에서 사용자가 이유, 감정, 상황 설명을 연결해 말하는지를 함께 본다.
- 사용자가 교정 후 표현을 다음 대화에서 재사용하는지 추적한다.

목표 표시:

```text
표현력
측정 준비 중
```

---

# 권장 모델 방향

기존 `ExternalMetrics`를 즉시 대체하지 않고, Statistics 전용 read model을 추가한다.
다만 이 read model은 Chat/Correction raw evidence를 직접 계산하지 않고, LangState의 누적 능력 요약을 읽어 만든다.

예시:

```kotlin
data class StatisticsAbilitySummary(
    val overallLevel: AbilityLevelDisplay,
    val overallConfidence: ProfileConfidence,
    val vocabulary: AbilityMetricCard,
    val speaking: AbilityMetricCard,
    val grammar: AbilityMetricCard,
    val comprehension: AbilityMetricCard,
    val expression: AbilityMetricCard
)
```

```kotlin
enum class AbilityLevelDisplay {
    Start,
    Word,
    Sentence,
    Conversation,
    Expression,
    Fluent
}
```

```kotlin
sealed interface AbilityMetricCard {
    data class Ready(
        val label: String,
        val statusText: String,
        val confidence: ProfileConfidence
    ) : AbilityMetricCard

    data class Preparing(
        val label: String,
        val message: String = "측정 준비 중"
    ) : AbilityMetricCard
}
```

주의:

- 위 모델명은 구현 시 조정할 수 있다.
- 핵심은 UI가 `ExternalMetrics`를 직접 해석하지 않고, domain usecase가 만든 표시 모델만 읽는 것이다.
- domain usecase도 raw Chat/Correction evidence를 재분석하지 않고, LangState 집계값을 표시용으로 변환하는 책임만 가진다.
- `ProfileConfidence`는 새 신뢰도 체계를 추가하지 않고, LangState 집계와 Statistics 표시에서 그대로 재사용한다.

---

# 주요 작업

1. Statistics 표시 카드를 `종합 레벨 + 5개 능력 항목`으로 정리한다.
2. 종합 레벨 표시명을 `시작/단어/문장/대화/표현/능숙 Level`로 정의한다.
3. LangState 기반 종합 레벨, 말하기, 문법, 이해력 값을 읽기 위한 `LangAbilityStats` 묶음을 추가한다.
4. 기존 Chat evidence 경로가 남긴 band/말하기/이해력 근거를 Statistics 표시 기준으로 재사용한다.
5. 기존 Correction signal 경로가 남긴 문법 근거를 Statistics 표시 기준으로 재사용한다.
6. Statistics domain usecase는 LangState 집계값을 읽어 표시용 read model을 만든다.
7. `어휘`, `표현력`은 계산하지 않고 `측정 준비 중` card로 제공한다.
8. 근거 부족/low confidence 상태를 표시 모델에서 표현한다.
9. 기존 `ExternalMetrics`와 `StatisticsHistory` 저장 호환을 유지한다.
10. 관련 테스트에서 기존 데이터가 있어도 새 통계 화면이 안전하게 로드되는지 확인한다.

---

# 제외 범위

- 어휘 최근/누적 ledger 구현
- 표현력 장기 재사용 추적
- CEFR 기반 기존 `VocabLevel` 삭제
- 기존 `ExternalMetrics` 스키마 제거
- 기존 `StatisticsHistory` 전체 마이그레이션
- Correction signal 계약 변경
- Chat evidence prompt 변경
- 발음 평가 또는 pronunciation score 추가
- Statistics에서 Chat/Correction raw evidence를 직접 재분석하는 로직

---

# 예외 처리

- `chatEvidenceSummary`가 없으면 종합 레벨은 `측정 중` 또는 가장 보수적인 시작 상태로 표시한다.
- Chat evidence가 Low confidence이면 레벨 상향 근거로 강하게 사용하지 않는다.
- Correction signal이 없으면 문법 지표를 높은 점수로 추정하지 않는다.
- `meaningPreserved=false`인 교정 signal은 장기 문법 지표에 직접 반영하지 않는다.
- STT metadata가 부족하면 말하기 지표를 발화 속도만으로 계산하지 않는다.
- 기존 저장 데이터에 새 표시 모델이 없어도 앱이 crash 없이 fallback 화면을 보여준다.
- LangState 집계값이 없는 기존 사용자는 새 지표를 `측정 중` 또는 `측정 준비 중`으로 표시한다.

---

# 테스트 방법

- `chatEvidenceSummary`가 없는 사용자에서 통계 화면이 안전하게 열린다.
- `IntentOnly` band는 `시작 Level`로 표시된다.
- `SimpleSentence` band는 `문장 Level`로 표시된다.
- `NuanceControl` band는 `능숙 Level`로 표시된다.
- 종합 레벨 카드를 누르면 차트가 아니라 모든 Level의 의미가 표시된다.
- 어휘 카드는 `측정 준비 중`으로 표시된다.
- 표현력 카드는 `측정 준비 중`으로 표시된다.
- Low confidence Chat 분석만 있는 경우 종합 레벨이 과하게 상향되지 않는다.
- Correction signal이 없는 경우 문법 지표가 높은 점수로 추정되지 않는다.
- 문법 관련 issue와 severity가 있는 correction signal은 문법 지표 계산에 반영된다.
- Statistics usecase는 Chat transcript나 Correction raw signal 없이 LangState 집계값만으로 표시 모델을 만든다.
- 기존 Chat, Correction, Flashcard, LearningState, Statistics 화면 진입 흐름이 유지된다.

---

# 후속 작업 후보

- `STATISTICS-TUNE-001` 어휘 최근/누적 ledger 설계
- `STATISTICS-TUNE-002` 표현력 장기 사용 근거 설계
- `STATISTICS-TUNE-003` 통계 지표 신뢰도와 설명 문구 튜닝
- `STATISTICS-TUNE-004` 기존 `ExternalMetrics` 축소 또는 재정의
