package com.app.umma.domain.model.learningstate

/**
 * 교정 AI가 LearningState에 넘기는 관찰 신호 계약 (Domain 계약 v2).
 *
 * 설계 기준: `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md`
 *
 * 핵심 책임 경계:
 *  - Correction은 "사용자 능력 평가자"가 아니라 "교정 과정에서 관찰한 신호 제공자"다.
 *    교정 결과를 만들면서 이미 판단한 오류 범주/언어 feature/개선 유형/edit/register/severity/
 *    의미 보존/confidence를 구조화해 이 계약으로 넘긴다.
 *  - LearningState가 이 신호를 받아 장기 metric/evidence/active focus/challenge guard를 계산한다.
 *    Correction은 최종 grammarAccuracy/vocabularyLevel/fluencyScore/CEFR level/
 *    LearnerAdaptationProfile/difficultyDelta, 음성 유창성(pause/hesitation/pronunciation)을 만들지 않는다.
 *
 * 모델 위치 원칙:
 *  - 이 계약은 LearningState 갱신 입력으로 재사용되므로 presentation 전용 모델로 두지 않는다.
 *  - `CorrectionResult`(learningstate 패키지)가 이 타입을 참조하므로, learningstate→correction
 *    역의존을 피하기 위해 신호 타입을 learningstate 패키지에 둔다.
 */
data class CorrectionLearningSignal(
    // 어떤 교정 후보에서 나온 신호인지. correction candidate당 정확히 1개의 signal만 만든다.
    val candidateId: String,
    // SessionMemory 원본 turnId가 있을 때만 채운다. 없으면 null.
    val sourceTurnId: String?,
    // sourceTurnId가 없어도 후보 추적 fallback으로 반드시 채운다.
    val sourceTurnIndex: Int,
    // 교정 전 원문. AI 분석값이 아니라 후보 추출 단계의 신뢰 원문을 그대로 전달한다.
    val sourceText: String,
    // 교정 후 문장. suggestion.afterText를 그대로 전달한다.
    val correctedText: String,
    // 관찰된 공통 오류 범주(최대 3개). 비어 있으면 LearningState 반영 weight를 낮춘다.
    val issueCategories: List<CorrectionIssueCategory>,
    // 언어별 세부 학습 feature(최대 3개). active focus 후보로 쓰이며 최종 점수로 직접 변환하지 않는다.
    val languageFeatures: List<LanguageFeatureSignal>,
    // 교정에서 개선된 유형(최대 3개).
    val improvementTypes: List<CorrectionImprovementType>,
    // 변경된 fragment 단위 edit 정보(최대 3개). 비어 있어도 signal은 유지한다.
    val editSpans: List<CorrectionEditSpan>,
    // 교정 결과 문장의 말투. 단일 값.
    val register: SpokenRegister,
    // 오류/개선의 심각도. 단일 값. LearningState 반영 weight와 focus 우선순위 보조값이다.
    val severity: CorrectionSeverity,
    // 의미 보존 여부. false면 장기 능력 점수에 직접 반영하지 않는다.
    val meaningPreserved: Boolean,
    // AI 판단 confidence(0.0..1.0) 또는 null. null이면 LearningState가 medium-low로 취급한다.
    val confidence: Double?
)

/**
 * 언어별 세부 학습 포인트 신호.
 *
 * `featureKey`는 `{LANG}.{FeatureName}` namespace를 따른다(예: `EN.Tense`, `JA.Particle`).
 * `lang`은 현재 교정 대상인 selectedLang과 일치해야 한다.
 */
data class LanguageFeatureSignal(
    // feature가 속한 언어. selectedLang과 일치해야 한다.
    val lang: LangCode,
    // `{LANG}.{FeatureName}` 형식의 feature 식별자. 빈 문자열을 허용하지 않는다.
    val featureKey: String
)

/**
 * "어떤 부분이 어떻게 바뀌었는지"를 LearningState가 해석하게 돕는 보조 신호.
 *
 * 정확한 character offset(startIndex/endIndex)은 요구하지 않고, 변경된 fragment만 담는다.
 * candidate 전체 문장을 그대로 반복하지 않는다.
 */
data class CorrectionEditSpan(
    // 변경 전 조각.
    val sourceFragment: String,
    // 변경 후 조각.
    val correctedFragment: String,
    // 이 edit이 속한 오류 범주.
    val issueCategory: CorrectionIssueCategory,
    // 연관된 언어 feature 키(있으면). 형식 위반 시 null로 둔다.
    val languageFeatureKey: String?,
    // 이 edit이 만든 개선 유형.
    val improvementType: CorrectionImprovementType
)

/**
 * 공통 오류 범주. 특정 언어에 묶이지 않는 큰 분류다.
 *
 * 영어의 관사나 일본어의 조사 같은 언어별 세부 항목은 [LanguageFeatureSignal]로 분리한다.
 */
enum class CorrectionIssueCategory {
    // 문법 형태(시제/일치 등) 오류.
    GrammarForm,
    // 어순 오류.
    WordOrder,
    // 문장 완결성(주어/목적어 누락 등) 문제.
    SentenceCompleteness,
    // 어휘 선택 문제.
    VocabularyChoice,
    // 연어(collocation) 문제.
    Collocation,
    // 말투(register) 문제.
    Register,
    // 문맥 누락.
    MissingContext,
    // 의미 불일치.
    MeaningMismatch
}

/**
 * 교정에서 개선된 유형.
 *
 * 의미 보존 여부는 [CorrectionLearningSignal.meaningPreserved] boolean으로만 표현한다.
 * 품질 방어값(MeaningPreserved)을 개선 유형 enum에 섞지 않아, LearningState가 둘을 분리해 다룬다.
 */
enum class CorrectionImprovementType {
    // 문법 오류 수정.
    GrammarFixed,
    // 문장 구조 확장. stretch/challenge guard 계산에 사용된다.
    StructureExpanded,
    // 더 자연스러운 동사 표현.
    MoreNaturalVerb,
    // 더 나은 연어. stretch/challenge guard 계산에 사용된다.
    BetterCollocation,
    // 구어 표현 추가. stretch/challenge guard 계산에 사용된다.
    SpokenExpressionAdded,
    // 명료성을 위해 줄임. 표현 능력 저하로 해석하지 않으며 단독 감점 근거로 쓰지 않는다.
    ShortenedForClarity,
    // 더 캐주얼하게.
    MadeMoreCasual,
    // 더 공손하게.
    MadeMorePolite
}

/**
 * 교정 결과 문장의 말투.
 *
 * 사용자의 전체 register 능력을 직접 판정하지 않는다.
 * [CorrectionIssueCategory.Register]와 [EverydaySpoken]/[NativeLikeCasual]가 함께 나오면
 * naturalness focus 후보가 될 수 있다.
 */
enum class SpokenRegister {
    // 직설적이고 단순한 표현.
    Simple,
    // 자연스러운 일상 구어체.
    EverydaySpoken,
    // 원어민 같은 캐주얼 표현.
    NativeLikeCasual,
    // 격식체.
    Formal
}

/**
 * 오류 또는 개선의 심각도.
 *
 * severity는 최종 점수가 아니라 LearningState 반영 weight와 focus 우선순위를 정하는 보조값이다.
 */
enum class CorrectionSeverity {
    // 의미 전달을 막는 오류.
    BlockingMeaning,
    // 반복 학습 focus가 될 수 있는 주요 문법/문장 패턴 문제.
    MajorPattern,
    // 의미 전달은 되지만 형태 정확도에 영향을 주는 문제.
    MinorForm,
    // 문법 오류보다 자연스러움/register/collocation 개선에 가까운 문제.
    NaturalnessOnly
}
