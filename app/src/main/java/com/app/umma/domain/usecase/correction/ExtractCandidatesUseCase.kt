package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import javax.inject.Inject

/**
 * recentFullContext 에서 Correction 후보를 추출한다.
 *
 * 이 UseCase는 화면과 무관하게 후보 후보군만 만들어 주며,
 * 실제 교정 결과 생성은 이후 단계에서 담당한다.
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

        recentTurns.forEachIndexed { index, turn ->
            // 교정 후보는 사용자 발화만 대상으로 만든다.
            if (turn.speaker != TurnSpeaker.USER) return@forEachIndexed

            // 공백만 있는 발화는 교정 후보로 남기지 않는다.
            val sourceText = turn.text.trim()
            if (sourceText.isBlank()) return@forEachIndexed

            // 앞뒤의 assistant 발화는 문맥 보조용으로만 붙인다.
            val assistantContext = buildAssistantContext(recentTurns, index)
            val sourceTurnIndex = baseIndex + index
            // 같은 후보를 이후 단계에서 안정적으로 추적할 수 있도록 식별자를 만든다.
            val candidateId = buildCandidateId(
                lang = input.selectedLang,
                sourceTurnIndex = sourceTurnIndex,
                sourceText = sourceText
            )

            candidates += CorrectionCandidate(
                id = candidateId,
                lang = input.selectedLang,
                sourceTurnId = null,
                sourceTurnIndex = sourceTurnIndex,
                sourceText = sourceText,
                assistantContext = assistantContext
            )
        }

        return candidates
    }

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
        sourceText: String
    ): String {
        // 언어 + 원본 순서 + 문장 조합으로 후보 추적용 ID를 만든다.
        return buildString {
            append(lang.code)
            append('-')
            append(sourceTurnIndex)
            append('-')
            append(sourceText.hashCode().toString(16))
        }
    }

    companion object {
        // MVP 후보 추출 범위는 최근 100턴으로 제한한다.
        const val MAX_SOURCE_TURNS = 100
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
