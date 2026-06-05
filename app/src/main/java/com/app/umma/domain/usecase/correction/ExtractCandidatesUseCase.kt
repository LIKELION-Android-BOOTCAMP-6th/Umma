package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import javax.inject.Inject

/**
 * recentFullContext 에서 Correction 후보를 추출한다.
 *
 * 이 UseCase는 화면과 무관하게 후보군만 만들어 주며,
 * 실제 교정 결과 생성은 이후 단계에서 담당한다.
 *
 * ### 입력 정제 규칙 (COR-TUNE-004)
 * - **사소한 발화 제외**: [TRIVIAL_UTTERANCE_TOKENS] 에 정확 일치하거나 [MIN_CANDIDATE_CHAR_LENGTH] 미만이면 제외.
 *   보수적 기준으로, 교정 가치가 있는 짧은 발화는 최대한 보존한다.
 * - **장문 분할**: 종결부호 기준으로 문장 단위로 나눠 각 문장을 별도 후보로 만든다.
 *   분할된 후보도 사소한 발화 기준을 통과해야 후보로 등록된다.
 * - **중복 제외**: 정규화(소문자 + 공백 collapse) 후 동일 텍스트는 배치 전체에서 한 번만 후보로 만든다.
 */
class ExtractCandidatesUseCase @Inject constructor() {

    operator fun invoke(input: ExtractCandidatesInput): List<CorrectionCandidate> {
        // 현재 선택 언어와 세션 메모리의 언어가 같을 때만 후보를 추출한다.
        if (input.selectedLang != input.sessionLang) return emptyList()

        // 후보 추출 범위를 최근 문맥으로 제한해 불필요한 계산과 입력 확장을 막는다.
        val recentTurns = input.recentFullContext.takeLast(MAX_SOURCE_TURNS)
        if (recentTurns.isEmpty()) return emptyList()

        // 잘라낸 최근 문맥 안에서도 원본 turn 순서를 추적할 수 있도록 시작 인덱스를 보정한다.
        val baseIndex = input.recentFullContext.size - recentTurns.size
        val candidates = mutableListOf<CorrectionCandidate>()

        // 배치 전체에서 중복을 제거하기 위한 정규화 텍스트 추적 집합.
        val seenNormalized = mutableSetOf<String>()

        recentTurns.forEachIndexed { index, turn ->
            // 교정 후보는 사용자 발화만 대상으로 만든다.
            if (turn.speaker != TurnSpeaker.USER) return@forEachIndexed

            // 공백만 있는 발화는 교정 후보로 남기지 않는다.
            val trimmedText = turn.text.trim()
            if (trimmedText.isBlank()) return@forEachIndexed

            val sourceTurnIndex = baseIndex + index

            // 앞뒤의 assistant 발화는 문맥 보조용으로만 붙인다.
            // turn 단위로 1번만 계산해 같은 원본에서 나온 분할 후보 전체에 동일하게 부여한다.
            val assistantContext = buildAssistantContext(recentTurns, index)

            // 장문 발화는 문장 단위로 분할해 각 문장을 별도 후보로 만든다.
            // 단일 문장이면 원문 그대로 1개 조각으로 반환된다.
            val fragments = splitIntoSentences(trimmedText)

            fragments.forEachIndexed { splitIndex, fragment ->
                // 교정 가치가 없는 사소한 조각(인사말, 너무 짧은 발화 등)은 건너뛴다.
                if (isTrivial(fragment)) return@forEachIndexed

                // 배치 내 중복 텍스트는 처음 등장한 위치의 후보만 남긴다.
                val normalized = normalize(fragment)
                if (normalized in seenNormalized) return@forEachIndexed
                seenNormalized += normalized

                // 같은 원본 turn에서 나온 분할 후보들의 ID가 충돌하지 않도록 splitIndex를 반영한다.
                val candidateId = buildCandidateId(
                    lang = input.selectedLang,
                    sourceTurnIndex = sourceTurnIndex,
                    splitIndex = splitIndex,
                    sourceText = fragment
                )

                candidates += CorrectionCandidate(
                    id = candidateId,
                    lang = input.selectedLang,
                    sourceTurnId = null,
                    sourceTurnIndex = sourceTurnIndex,
                    sourceText = fragment,
                    assistantContext = assistantContext
                )
            }
        }

        return candidates
    }

    /**
     * 발화를 문장 경계 기준으로 분할한다.
     *
     * 종결부호(.!? 및 CJK 。！？) 뒤에서 끊되, best-effort 로만 동작한다.
     * 종결부호가 없거나 단일 문장이면 원문을 trim 한 그대로 1개 조각으로 돌려준다.
     * 조각의 텍스트는 trim 외에 변형하지 않는다(candidateId·sourceText 정합성 유지).
     */
    private fun splitIntoSentences(text: String): List<String> {
        // 종결부호를 포함한 문장 단위와, 종결부호 없이 끝나는 마지막 조각을 각각 잡는다.
        val fragments = SENTENCE_BOUNDARY_REGEX.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

        // 경계가 잡히지 않으면(종결부호 없는 짧은 발화 등) 원문을 그대로 보존한다.
        return if (fragments.isEmpty()) listOf(text.trim()) else fragments
    }

    /**
     * 정규화 후 인사말/필러와 정확히 일치하거나 최소 글자수 미만이면 사소한 발화로 본다.
     *
     * 보수적 기준을 적용해, 애매하면 후보를 유지하는 방향으로 처리한다.
     * 교정 가치가 있는 짧은 발화를 잘못 걸러내지 않도록 부분 포함은 제외하지 않는다.
     */
    private fun isTrivial(text: String): Boolean {
        val normalized = normalize(text)
        // 정규화 후 빈 문자열이거나 최소 글자수 미만이면 제외한다. CJK 대응을 위해 단어 수 대신 글자 수 기준을 사용한다.
        if (normalized.length < MIN_CANDIDATE_CHAR_LENGTH) return true
        // 인사말/필러 집합에 정확히 일치하는 발화만 제외한다(보수적).
        return normalized in TRIVIAL_UTTERANCE_TOKENS
    }

    /**
     * trivial 판정과 dedup 에 공용으로 사용하는 정규화.
     *
     * 소문자화 + 양끝 구두점/공백 제거 + 내부 공백 collapse 를 적용한다.
     * "Thank  you!" 같은 변형도 "thank you" 로 동일하게 매칭되도록 한다.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trimEnd { it.isWhitespace() || it in TRIM_PUNCTUATION }
            .trimStart { it.isWhitespace() || it in TRIM_PUNCTUATION }

    private fun buildAssistantContext(
        turns: List<ConversationTurn>,
        userTurnIndex: Int
    ): String? {
        val snippets = mutableListOf<String>()

        // 사용자 발화 직전의 최근 assistant 턴을 먼저 찾아 맥락을 보강한다.
        val previousAssistant = turns
            .subList(0, userTurnIndex)
            .asReversed()
            .firstOrNull { it.speaker == TurnSpeaker.AI && it.text.isNotBlank() }
            ?.text
            ?.trim()

        // 필요하면 사용자 발화 직후의 assistant 턴도 함께 붙여 문맥을 넓힌다.
        val nextAssistant = turns
            .drop(userTurnIndex + 1)
            .firstOrNull { it.speaker == TurnSpeaker.AI && it.text.isNotBlank() }
            ?.text
            ?.trim()

        previousAssistant?.let { snippets += it }
        nextAssistant?.takeIf { it != previousAssistant }?.let { snippets += it }

        return snippets.joinToString(separator = "\n").takeIf { it.isNotBlank() }
    }

    private fun buildCandidateId(
        lang: LangCode,
        sourceTurnIndex: Int,
        splitIndex: Int,
        sourceText: String
    ): String {
        // 언어 + 원본 turn 순서 + 분할 순번 + 문장 해시로 충돌 없는 추적용 ID를 만든다.
        // splitIndex 추가로 같은 turn에서 나온 분할 후보들도 고유하게 구분된다.
        return buildString {
            append(lang.code)
            append('-')
            append(sourceTurnIndex)
            append('-')
            append(splitIndex)
            append('-')
            append(sourceText.hashCode().toString(16))
        }
    }

    companion object {
        // MVP 후보 추출 범위는 최근 100턴으로 제한한다.
        const val MAX_SOURCE_TURNS = 100

        // 정규화 후 이 글자수 미만이면 교정 후보에서 제외한다.
        // 단어 수 대신 글자 수 기준을 사용해 CJK(공백 없는 언어)에도 동일하게 적용한다.
        const val MIN_CANDIDATE_CHAR_LENGTH = 2

        // 정규화(소문자 + 양끝 구두점 제거 + 내부 공백 collapse) 후 정확히 일치하면 제외하는 인사말/필러.
        // 보수적으로 EN 중심 + 흔한 JA 인사말만 포함한다. 부분 포함은 제외하지 않는다.
        internal val TRIVIAL_UTTERANCE_TOKENS = setOf(
            "hi", "hey", "hello", "ok", "okay",
            "yes", "no", "yeah", "nope",
            "thanks", "thank you", "bye", "sure",
            // 흔한 JA 인사말/필러
            "はい", "いいえ", "こんにちは", "ありがとう", "どうも"
        )

        // trivial 판정 시 양끝에서 제거할 구두점. 문장 내부 구두점은 건드리지 않는다.
        private val TRIM_PUNCTUATION = setOf('.', ',', '!', '?', '。', '！', '？', '、', '…')

        // 문장 경계 정규식: 종결부호를 포함한 문장과 종결부호 없이 끝나는 마지막 조각을 모두 잡는다.
        private val SENTENCE_BOUNDARY_REGEX = Regex("[^.!?。！？]*[.!?。！？]+|[^.!?。！？]+$")

        // 내부 공백 collapse 용 정규식.
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

/**
 * Correction 후보 추출에 필요한 입력.
 */
data class ExtractCandidatesInput(
    // 현재 선택된 언어.
    val selectedLang: LangCode,
    // 현재 Session Memory 가 속한 언어.
    val sessionLang: LangCode,
    // 오래된 턴부터 최신 턴 순으로 정렬된 전체 문맥. RT-003의 createdAt ASC read model과 맞춘다.
    val recentFullContext: List<ConversationTurn>
)
