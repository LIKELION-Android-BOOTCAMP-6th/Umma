package com.app.umma.data.repository.correction

import android.util.Log
import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.CorrectionEditSpan
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LanguageFeatureSignal
import com.app.umma.domain.model.learningstate.SpokenRegister
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 실제 AI 교정 응답을 domain 계약인 [CorrectionSuggestion] 목록으로 변환하는 data 계층 mapper 입니다.
 *
 * UI와 domain은 AI 원문 JSON 구조를 알면 안 되므로, 응답 파싱과 필수 필드 검증은 이 계층에 둔다.
 * User Flow에서 실제 AI API를 연결할 때도 Repository는 이 mapper를 거쳐 동일한 결과 계약을 반환한다.
 *
 * 파싱 흐름은 다음 순서를 따른다.
 * 1. AI가 반환한 raw JSON 문자열을 data DTO([CorrectionAiResponseDto])로 디코딩한다.
 * 2. DTO의 candidateId를 이미 추출된 [GenerateSuggestionsInput.candidates]와 다시 매칭한다.
 * 3. 후보의 원문/언어/turn 정보와 AI가 만든 nativeText, afterText, explanation을 합쳐 domain 모델을 만든다.
 * 4. 필수 필드가 비어 있거나 candidateId가 맞지 않으면 실패로 처리해 화면의 Error/Retry 흐름으로 이어지게 한다.
 *
 * COR-TUNE-02: suggestion 마다 [CorrectionLearningSignal] 을 정규화해 함께 싣는다.
 *  - 신호는 LearningState 갱신을 돕는 "보조" 입력이므로, 신뢰할 수 없으면 버린다(drop)를 기본값으로 둔다.
 *  - 오염된 신호 하나가 핵심 4필드(교정 결과 저장) 흐름을 막지 않도록, 신호 정규화는 runCatching 으로 감싼다.
 *  - candidateId/sourceTurnId/sourceTurnIndex/sourceText/correctedText 는 AI 분석값이 아니라
 *    Correction 이 전달하는 신뢰 데이터다. 따라서 AI 응답이 아닌 candidate(원문/turn)와 afterText 에서 채운다.
 *    (beforeText 를 candidate.sourceText 로 신뢰하는 기존 패턴과 동일하다)
 */
class CorrectionAiResponseMapper @Inject constructor() {

    // AI 응답 JSON 디코더. 미래에 schema가 늘어나도 깨지지 않도록 ignoreUnknownKeys=true,
    // null 필드는 직렬화 단계에서 누락되도록 explicitNulls=false 로 둔다.
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun map(
        rawJson: String,
        input: GenerateSuggestionsInput
    ): List<CorrectionSuggestion> {
        // 1) 실제 AI API는 문자열 JSON을 돌려준다. Repository는 그 원문을 이 mapper에 넘긴다.
        // 여기서만 JSON 구조를 알고, domain/usecase/presentation은 raw JSON에 의존하지 않는다.
        val response = json.decodeFromString(CorrectionAiResponseDto.serializer(), rawJson)

        // 2) AI 응답의 candidateId가 어떤 원본 user turn에서 나온 결과인지 확인하기 위해
        // 후보 목록을 id 기준으로 다시 찾을 수 있게 만든다.
        val candidatesById = input.candidates.associateBy { it.id }
        val selectedLang = input.langState.lang

        return response.suggestions.map { item ->
            // 3) AI가 알 수 없는 후보 ID를 돌려주면 beforeText/sourceTurnIndex를 보장할 수 없다.
            // 출처가 깨진 교정 결과는 저장하면 안 되므로 파싱 실패로 다룬다.
            val candidate = requireNotNull(candidatesById[item.candidateId]) {
                "unknown correction candidate id: ${item.candidateId}"
            }

            // 현재 선택 언어와 후보 언어가 다르면 같은 교정 세션 결과로 사용할 수 없다.
            require(candidate.lang == selectedLang) {
                "candidate language must match langState language"
            }

            CorrectionSuggestion(
                // 4) domain 모델 id는 앱 내부 추적용이다. AI 응답의 candidateId를 그대로 쓰지 않고
                // Correction 결과임을 구분할 수 있도록 prefix를 붙인다.
                id = "corr-${candidate.id}",
                lang = candidate.lang,
                sourceCandidateIds = listOf(candidate.id),
                sourceTurnIndex = candidate.sourceTurnIndex,
                // beforeText는 AI가 만든 값이 아니라 후보 추출 단계의 원문을 신뢰한다.
                // 이렇게 해야 AI 응답이 원문을 변형해도 카드의 교정 전 문장이 흔들리지 않는다.
                beforeText = candidate.sourceText,
                // 아래 세 필드는 AI가 생성해야 하는 필수 결과다.
                // 하나라도 공백이면 화면 카드와 Flashcard 저장 모두 불완전해지므로 실패시킨다.
                nativeText = item.nativeText.requireFilled("nativeText"),
                afterText = item.afterText.requireFilled("afterText"),
                explanation = item.explanation.requireFilled("explanation"),
                // COR-TUNE-011: 평가 게이트가 참조할 발화 원문 언어를 candidate 에서 그대로 옮긴다.
                // 여기서는 운반만 한다 — 제외 판정은 단일 지점(CompleteCorrectionUseCase.buildCorrectionResult)에서만 내린다.
                sourceLang = candidate.sourceLang,
                // COR-TUNE-02: 학습 신호는 보조 입력이다. 정규화가 실패해도 suggestion 을 죽이지 않도록
                // runCatching 으로 감싸 실패 시 null 을 싣는다. (핵심 4필드는 위에서 이미 검증 완료)
                learningSignal = runCatching {
                    normalizeLearningSignal(
                        dto = item.learningSignal,
                        candidate = candidate,
                        correctedText = item.afterText.trim(),
                        selectedLang = selectedLang
                    )
                }.getOrNull()
            )
        }
    }

    /**
     * AI 가 보낸 nested learningSignal DTO 를 domain 신호 계약으로 정규화한다.
     *
     * 정규화 정책(CHAT-TUNE-001 핸드오버 / COR-TUNE-02 AC + COR-TUNE-02-FIX):
     *  signal 은 장기 LangState 계산의 입력이므로, 불확실하면 일부만 살리지 않고 signal 전체를 보수적으로 drop 한다.
     *  - 신호 자체가 없으면(null) 신호 누락으로 보고 null 을 돌려준다(실패 아님).
     *  - register/severity/meaningPreserved 는 단일 필수값이다. unknown/누락이면 해당 signal 전체를 drop 한다.
     *    (meaningPreserved 는 false 도 유효한 관찰값이라 살리지만, 누락은 의미 보존이 검증되지 않은 것이므로 drop)
     *  - issueCategories/improvementTypes 는 allowlist 로 파싱하되 unknown 원소가 하나라도 있으면 signal 전체를 drop 한다.
     *    (unknown 이 섞인 리스트는 신뢰할 수 없으므로 부분 제외하지 않는다) 전부 유효하면 최대 3개로 캡한다.
     *  - editSpans 도 issueCategory/improvementType 이 unknown 이면 signal 전체를 drop 한다. 전부 유효하면 최대 3개로 캡한다.
     *  - languageFeatures 는 {LANG}.{Feature} namespace·lang=selectedLang·allowlist 를 어기면 그 feature 만 제외한다(유일한 부분 제외 예외).
     *    editSpan 의 languageFeatureKey 도 형식 위반 시 null 로 비우되 span 자체는 유지한다(보조 정보).
     *  - confidence 는 0.0..1.0 만 허용한다. 범위 밖이면 signal 을 drop, 없으면 null 을 허용한다.
     *  - 전달 데이터(candidateId/sourceTurnId/sourceTurnIndex/sourceText/correctedText)는 신뢰 출처에서 채운다.
     */
    private fun normalizeLearningSignal(
        dto: CorrectionLearningSignalDto?,
        candidate: CorrectionCandidate,
        correctedText: String,
        selectedLang: LangCode
    ): CorrectionLearningSignal? {
        // 신호가 통째로 없으면 누락으로 본다. 핵심 4필드는 이미 통과했으므로 suggestion 은 살아 있다.
        if (dto == null) {
            return null
        }

        val candidateId = candidate.id

        // register/severity 는 signal 의 단일 필수값이다. 알 수 없으면 신호 전체 신뢰도가 떨어지므로 drop 한다.
        val register = parseEnumOrNull<SpokenRegister>(dto.register)
        if (register == null) {
            logDroppedSignal(candidateId, "register", dto.register)
            return null
        }
        val severity = parseEnumOrNull<CorrectionSeverity>(dto.severity)
        if (severity == null) {
            logDroppedSignal(candidateId, "severity", dto.severity)
            return null
        }

        // meaningPreserved 는 "교정 후 문장이 사용자의 원래 의도를 유지했는지"를 나타내는 핵심 방어값이다.
        // true/false 는 모두 유효한 관찰 결과지만(특히 false 는 의미 변형을 알리는 중요한 신호), 누락이면
        // 의미 보존 여부가 검증되지 않은 것이므로 장기 LangState 입력 오염을 막기 위해 signal 전체를 drop 한다.
        val meaningPreserved = dto.meaningPreserved
        if (meaningPreserved == null) {
            logDroppedSignal(candidateId, "meaningPreserved", null)
            return null
        }

        // confidence 는 0.0..1.0 범위만 의미가 있다. 범위 밖 값은 신뢰할 수 없으므로 signal 전체를 drop 한다.
        // 없으면(null) LearningState 가 medium-low 로 취급하므로 그대로 통과시킨다.
        val confidence = dto.confidence
        if (confidence != null && confidence !in 0.0..1.0) {
            logDroppedSignal(candidateId, "confidence", confidence.toString())
            return null
        }

        // issueCategories/improvementTypes: unknown 원소가 하나라도 있으면 signal 전체를 drop 한다(부분 제외 금지).
        // 전부 유효하면 최대 3개로 캡한다.
        val issueCategories = parseEnumListStrictOrDrop<CorrectionIssueCategory>(
            dto.issueCategories, candidateId, "issueCategory"
        )?.take(MAX_SIGNAL_ITEMS) ?: return null

        val improvementTypes = parseEnumListStrictOrDrop<CorrectionImprovementType>(
            dto.improvementTypes, candidateId, "improvementType"
        )?.take(MAX_SIGNAL_ITEMS) ?: return null

        // languageFeatures: namespace/lang/allowlist 를 어기는 feature 만 제외한다. signal 은 유지한다.
        val languageFeatures = dto.languageFeatures
            .orEmpty()
            .mapNotNull { feature -> normalizeLanguageFeature(feature, selectedLang, candidateId) }
            .take(MAX_SIGNAL_ITEMS)

        // editSpans: issueCategory/improvementType 이 unknown 인 span 이 있으면 signal 전체를 drop 한다(부분 제외 금지).
        // 전부 유효하면 최대 3개로 캡한다. 비어 있어도 signal 은 유지한다.
        val editSpans = ArrayList<CorrectionEditSpan>()
        for (span in dto.editSpans.orEmpty()) {
            editSpans.add(normalizeEditSpan(span, selectedLang, candidateId) ?: return null)
        }
        val cappedEditSpans = editSpans.take(MAX_SIGNAL_ITEMS)

        return CorrectionLearningSignal(
            // 전달 데이터는 AI 값이 아니라 신뢰 출처에서 채운다.
            candidateId = candidateId,
            // sourceTurnId 는 원본 turnId 가 있을 때만 채운다.
            sourceTurnId = candidate.sourceTurnId,
            // sourceTurnId 가 없어도 sourceTurnIndex 는 후보 추적 fallback 으로 반드시 채운다.
            sourceTurnIndex = candidate.sourceTurnIndex,
            sourceText = candidate.sourceText,
            correctedText = correctedText,
            issueCategories = issueCategories,
            languageFeatures = languageFeatures,
            improvementTypes = improvementTypes,
            editSpans = cappedEditSpans,
            register = register,
            severity = severity,
            // 누락은 위에서 이미 drop 했으므로 여기서는 검증된 true/false 값만 싣는다.
            meaningPreserved = meaningPreserved,
            confidence = confidence
        )
    }

    /**
     * 언어 feature 하나를 정규화한다. namespace/lang/allowlist 중 하나라도 어기면 null 을 돌려 그 feature 만 제외한다.
     */
    private fun normalizeLanguageFeature(
        dto: LanguageFeatureDto,
        selectedLang: LangCode,
        candidateId: String
    ): LanguageFeatureSignal? {
        val featureKey = dto.featureKey?.trim().orEmpty()
        // lang 은 현재 교정 대상인 selectedLang 과 일치해야 한다.
        val featureLang = dto.lang?.let { LangCode.fromCode(it) }
        if (featureLang != selectedLang) {
            logExcludedItem(candidateId, "languageFeature.lang", dto.lang)
            return null
        }
        if (!isValidFeatureKey(featureKey, selectedLang)) {
            logExcludedItem(candidateId, "languageFeature.featureKey", featureKey)
            return null
        }
        return LanguageFeatureSignal(lang = selectedLang, featureKey = featureKey)
    }

    /**
     * editSpan 하나를 정규화한다. issueCategory/improvementType 이 unknown 이면 null 을 돌려 호출부가
     * signal 전체를 drop 하게 한다(부분 제외 금지 — unknown enum 이 섞이면 신호를 신뢰할 수 없음).
     * languageFeatureKey 는 형식/allowlist 를 어기면 null 로 비우되 span 자체는 유지한다(보조 정보이기 때문).
     */
    private fun normalizeEditSpan(
        dto: EditSpanDto,
        selectedLang: LangCode,
        candidateId: String
    ): CorrectionEditSpan? {
        val issueCategory = parseEnumOrNull<CorrectionIssueCategory>(dto.issueCategory)
            ?: run { logDroppedSignal(candidateId, "editSpan.issueCategory", dto.issueCategory); return null }
        val improvementType = parseEnumOrNull<CorrectionImprovementType>(dto.improvementType)
            ?: run { logDroppedSignal(candidateId, "editSpan.improvementType", dto.improvementType); return null }

        // 형식에 맞는 featureKey 만 살리고, 어기면 null 로 비운다(span 은 유지).
        val featureKey = dto.languageFeatureKey?.trim()?.takeIf { isValidFeatureKey(it, selectedLang) }

        return CorrectionEditSpan(
            sourceFragment = dto.sourceFragment.orEmpty(),
            correctedFragment = dto.correctedFragment.orEmpty(),
            issueCategory = issueCategory,
            languageFeatureKey = featureKey,
            improvementType = improvementType
        )
    }

    /**
     * featureKey 가 `{LANG}.{Feature}` namespace 를 지키고, {LANG} 이 selectedLang 과 일치하며,
     * 초기 allowlist 안에 있는지 검증한다.
     */
    private fun isValidFeatureKey(featureKey: String, selectedLang: LangCode): Boolean {
        if (featureKey.isBlank()) return false
        val namespace = "${selectedLang.code.uppercase()}."
        if (!featureKey.startsWith(namespace)) return false
        return featureKey in ALLOWED_FEATURE_KEYS
    }

    /** allowlist 기반 관대 파싱. 알 수 없는 enum 문자열은 null 을 돌려 호출부가 drop/제외하게 한다. */
    private inline fun <reified T : Enum<T>> parseEnumOrNull(raw: String?): T? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    /**
     * enum 리스트를 엄격 파싱한다. 원소 중 하나라도 unknown 이면 그 값을 Logcat 에 남기고 null 을 돌려
     * 호출부가 signal 전체를 drop 하게 한다. (부분 제외하지 않는다 — unknown 이 섞인 리스트는 신뢰할 수 없음)
     * 전부 유효하면 파싱된 리스트를 그대로 돌려준다(캡은 호출부에서 take 로 적용).
     */
    private inline fun <reified T : Enum<T>> parseEnumListStrictOrDrop(
        raw: List<String>?,
        candidateId: String,
        field: String
    ): List<T>? {
        val result = ArrayList<T>()
        for (item in raw.orEmpty()) {
            val parsed = parseEnumOrNull<T>(item)
            if (parsed == null) {
                logDroppedSignal(candidateId, field, item)
                return null
            }
            result.add(parsed)
        }
        return result
    }

    /** signal 전체 drop 을 Logcat 에 남긴다. candidateId 와 위반 값으로 원인을 추적할 수 있게 한다. */
    private fun logDroppedSignal(candidateId: String, field: String, value: String?) {
        logWarn("learningSignal dropped — candidateId=$candidateId, $field=$value")
    }

    /** signal 은 유지하되 한 원소만 제외한 것을 Logcat 에 남긴다. */
    private fun logExcludedItem(candidateId: String, field: String, value: String?) {
        logWarn("learningSignal item excluded — candidateId=$candidateId, $field=$value")
    }

    /**
     * Logcat 경고. android.util.Log 는 순수 JVM 단위 테스트에서 "not mocked" 로 던지므로,
     * 신호 정규화의 drop/제외 경로(테스트가 직접 타는 경로)가 로깅 때문에 깨지지 않도록 방어한다.
     */
    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun String.requireFilled(fieldName: String): String {
        // 화면 카드와 Flashcard 저장에 필요한 필수 필드는 공백만 있어도 실패로 간주한다.
        return trim().also { value ->
            require(value.isNotEmpty()) {
                "correction AI response field '$fieldName' must not be blank"
            }
        }
    }

    private companion object {
        private const val TAG = "CorrectionAiResponseMapper"

        // candidate 당 issueCategories/languageFeatures/improvementTypes/editSpans 각 최대 개수.
        private const val MAX_SIGNAL_ITEMS = 3

        /**
         * 초기 languageFeature allowlist (COR-TUNE-02).
         *
         * 핸드오버 예시(EN.Article/Preposition/Tense, JA.Particle/Honorific/VerbConjugation)만 시드한다.
         * KO/DE 등 다른 학습 언어 feature 는 LearningState 와 합의 후 확장한다.
         * (allowlist 밖 featureKey 는 그 feature 만 제외되고 signal 자체는 유지된다)
         */
        private val ALLOWED_FEATURE_KEYS = setOf(
            "EN.Article",
            "EN.Preposition",
            "EN.Tense",
            "JA.Particle",
            "JA.Honorific",
            "JA.VerbConjugation"
        )
    }
}

/**
 * Correction AI 응답의 최상위 DTO입니다.
 *
 * 실제 prompt/schema는 User Flow 구현에서 조정하더라도, data 계층은 이 최소 구조를 기준으로
 * 응답을 검증한 뒤 domain 모델로 변환한다.
 *
 * 기대 JSON 예시는 다음과 같다.
 *
 * {
 *   "suggestions": [
 *     {
 *       "candidateId": "en-0-a",
 *       "nativeText": "나는 학교에 간다",
 *       "afterText": "I go to school.",
 *       "explanation": "Use 'go to school' instead of 'go school'."
 *     }
 *   ]
 * }
 */
@Serializable
private data class CorrectionAiResponseDto(
    // 후보별 교정 결과 배열. AI가 모든 후보를 한 번에 돌려주므로 배열로 받는다. 누락 시 빈 배열로 fallback.
    @SerialName("suggestions")
    val suggestions: List<CorrectionAiSuggestionDto> = emptyList()
)

/**
 * AI가 후보 하나에 대해 반환해야 하는 최소 교정 결과 DTO입니다.
 */
@Serializable
private data class CorrectionAiSuggestionDto(
    // 원본 후보 식별자. 프롬프트에서 내려보낸 값을 AI가 그대로 복사해야 매칭이 성립한다.
    @SerialName("candidateId")
    val candidateId: String,
    // primaryLang 기준 앞면 문장. Flashcard 앞면(frontText)으로 쓰인다. 필드명은 호환용으로 유지.
    @SerialName("nativeText")
    val nativeText: String,
    // 교정 후 학습 언어 문장. Flashcard 뒷면(backText)으로 쓰인다.
    @SerialName("afterText")
    val afterText: String,
    // 60자 이내 한국어 교정 사유 설명. Flashcard explanation 필드로 그대로 들어간다.
    @SerialName("explanation")
    val explanation: String,
    // COR-TUNE-02: 교정 과정에서 관찰한 학습 신호. 누락/오염되어도 핵심 4필드 흐름을 막지 않도록
    // nullable 로 둔다(없으면 suggestion.learningSignal=null).
    @SerialName("learningSignal")
    val learningSignal: CorrectionLearningSignalDto? = null
)

/**
 * AI 가 suggestion 당 함께 보내는 관찰 학습 신호 DTO 입니다.
 *
 * mapper 가 "신뢰할 수 없으면 버린다" 정책으로 정규화하므로, 모든 필드를 nullable/기본값으로 둔다.
 * 일부 필드가 누락되거나 형식이 어긋나도 역직렬화 단계에서 깨지지 않게 하기 위함이다.
 */
@Serializable
private data class CorrectionLearningSignalDto(
    @SerialName("candidateId")
    val candidateId: String? = null,
    @SerialName("sourceTurnId")
    val sourceTurnId: String? = null,
    @SerialName("sourceTurnIndex")
    val sourceTurnIndex: Int? = null,
    @SerialName("sourceText")
    val sourceText: String? = null,
    @SerialName("correctedText")
    val correctedText: String? = null,
    @SerialName("issueCategories")
    val issueCategories: List<String>? = null,
    @SerialName("languageFeatures")
    val languageFeatures: List<LanguageFeatureDto>? = null,
    @SerialName("improvementTypes")
    val improvementTypes: List<String>? = null,
    @SerialName("editSpans")
    val editSpans: List<EditSpanDto>? = null,
    @SerialName("register")
    val register: String? = null,
    @SerialName("severity")
    val severity: String? = null,
    @SerialName("meaningPreserved")
    val meaningPreserved: Boolean? = null,
    @SerialName("confidence")
    val confidence: Double? = null
)

/**
 * 언어별 세부 학습 feature DTO. lang/featureKey 모두 nullable 로 받아 mapper 에서 검증한다.
 */
@Serializable
private data class LanguageFeatureDto(
    @SerialName("lang")
    val lang: String? = null,
    @SerialName("featureKey")
    val featureKey: String? = null
)

/**
 * 변경 fragment 단위 edit DTO. enum 문자열은 mapper 에서 allowlist 로 관대 파싱한다.
 */
@Serializable
private data class EditSpanDto(
    @SerialName("sourceFragment")
    val sourceFragment: String? = null,
    @SerialName("correctedFragment")
    val correctedFragment: String? = null,
    @SerialName("issueCategory")
    val issueCategory: String? = null,
    @SerialName("languageFeatureKey")
    val languageFeatureKey: String? = null,
    @SerialName("improvementType")
    val improvementType: String? = null
)
