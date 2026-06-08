package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import javax.inject.Inject

/**
 * Correction 완료 이후 RT-003 compression 계약에 넘길 최소 payload 를 만든다.
 *
 * Session Memory 를 실제로 비우고 저장하는 책임은 RT-003에 남겨 둔다.
 * 이 UseCase 는 Correction 이 알고 있는 교정 결과와 분석 대상 turn 으로
 * `recentTopics`, `topicSummaries`, `topicKeySentences`만 구성한다.
 *
 * COR-TUNE-010: `recentTopics`/`topicSummaries`는 같은 완료 흐름에서 이미 호출된
 * `SummarizeRecentTopicsUseCase` (AI 매핑 결과)를 SSOT 로 우선 사용한다. AI 매핑이 비어 있으면
 * (실패·turn 부족 등) 기존 코드 기반 추출로 폴백한다 — 새 AI 호출을 추가하지 않고 압축 흐름도 막지 않는다.
 */
class BuildSessionCompressionPayloadUseCase @Inject constructor() {

    operator fun invoke(
        language: LangCode,
        selectedSuggestions: List<CorrectionSuggestion>,
        recentUserTurns: List<ConversationTurn>,
        compressedAt: Long,
        // COR-TUNE-010: CompleteCorrectionUseCase 2단계(SummarizeRecentTopicsUseCase)가 같은 완료 흐름에서
        // 이미 만들어 둔 AI 매핑 결과. 비어 있으면(AI 실패/미적용) 코드 기반 폴백으로 진행한다.
        aiRecentTopics: List<String> = emptyList(),
        aiTopicSummaries: List<String> = emptyList()
    ): Result<CompressSessionMemoryCommand?> {
        return runCatching {
            // topicSummaries SSOT 는 AI 매핑 결과다(COR-TUNE-010). AI 가 비었을 때만
            // 코드 before -> after 압축 요약(buildTopicSummary)으로 폴백한다.
            val topicSummaries = aiTopicSummaries
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SUMMARY_COUNT)
                .ifEmpty {
                    selectedSuggestions
                        .mapNotNull(::buildTopicSummary)
                        .distinct()
                        .take(MAX_SUMMARY_COUNT)
                }

            // key sentence 는 이후 AI Chat 이 재사용하기 좋은 정답 문장 중심으로 남긴다.
            // Flashcard 뒷면과 같은 afterText 를 쓰면 SRS 와 Session Memory 의 기준 문장이 맞는다.
            val topicKeySentences = selectedSuggestions
                .map { it.afterText.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_KEY_SENTENCE_COUNT)

            // recentTopics 도 AI 매핑(자연 주제 라벨)을 우선한다(COR-TUNE-010, 팀장 요청).
            // AI 가 비었을 때만 코드 단어빈도(extractRecentTopics)로 폴백한다.
            val recentTopics = aiRecentTopics
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_TOPIC_COUNT)
                .ifEmpty {
                    extractRecentTopics(
                        recentUserTurns = recentUserTurns,
                        selectedSuggestions = selectedSuggestions
                    )
                }

            // 빈 payload 로 RT-003 compression 을 호출하면 원문 buffer 만 비워질 수 있다.
            // 남길 요약이나 핵심 문장이 없으면 호출하지 않도록 null 을 반환한다.
            if (topicSummaries.isEmpty() && topicKeySentences.isEmpty()) {
                return@runCatching null
            }

            CompressSessionMemoryCommand(
                language = language,
                recentTopics = recentTopics,
                topicSummaries = topicSummaries,
                topicKeySentences = topicKeySentences,
                compressedAt = compressedAt
            )
        }
    }

    /**
     * COR-TUNE-010: AI 매핑(`aiTopicSummaries`)이 비었을 때만 쓰는 코드 기반 폴백이다.
     * SSOT 는 `SummarizeRecentTopicsUseCase` 의 AI 요약이며, 이 함수는 AI 실패 시에도
     * 압축 흐름이 빈 손으로 끝나지 않도록 남겨 둔다.
     */
    private fun buildTopicSummary(
        suggestion: CorrectionSuggestion
    ): String? {
        val before = suggestion.beforeText.trim()
        val after = suggestion.afterText.trim()
        val explanation = suggestion.explanation.trim()

        if (before.isBlank() && after.isBlank()) return null

        // 압축 summary 는 사용자가 틀린 표현과 교정된 표현을 함께 남긴다.
        // explanation 은 짧게 붙여 두어 나중에 문맥을 다시 펼칠 때 학습 의도까지 복원할 수 있게 한다.
        val summary = buildString {
            if (before.isNotBlank() && after.isNotBlank()) {
                append(before)
                append(" -> ")
                append(after)
            } else {
                append(before.ifBlank { after })
            }

            if (explanation.isNotBlank()) {
                append(" (")
                append(explanation)
                append(")")
            }
        }

        return summary.limitLength(MAX_TEXT_LENGTH)
    }

    /**
     * COR-TUNE-010: AI 매핑(`aiRecentTopics`)이 비었을 때만 쓰는 코드 단어빈도 폴백이다.
     * 팀장 요청에 따라 정상 경로의 SSOT 는 AI 매핑(`SummarizeRecentTopicsUseCase`)으로 옮겼고,
     * 이 함수는 AI 실패 시에도 `recentTopics` 가 완전히 비지 않도록 보존한다.
     */
    private fun extractRecentTopics(
        recentUserTurns: List<ConversationTurn>,
        selectedSuggestions: List<CorrectionSuggestion>
    ): List<String> {
        val sourceTexts = buildList {
            // 최근 user turn 은 실제 대화 주제를 가장 잘 나타낸다.
            addAll(recentUserTurns.map { it.text })
            // nativeText / beforeText 는 correction 결과와 원 대화의 의미 단서를 보강한다.
            addAll(selectedSuggestions.map { it.nativeText })
            addAll(selectedSuggestions.map { it.beforeText })
        }

        return sourceTexts
            .flatMap { text -> WORD_REGEX.findAll(text.lowercase()).map { it.value }.toList() }
            .filter { token -> token.length >= MIN_TOPIC_LENGTH && token !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()
            .entries
            // 많이 등장한 단어를 우선하되, 같은 빈도에서는 알파벳 순으로 고정해 테스트와 결과를 안정화한다.
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key.limitLength(MAX_TOPIC_LENGTH) }
            .distinct()
            .take(MAX_TOPIC_COUNT)
    }

    private fun String.limitLength(maxLength: Int): String {
        val trimmed = trim()
        return if (trimmed.length <= maxLength) {
            trimmed
        } else {
            trimmed.take(maxLength).trim()
        }
    }

    private companion object {
        const val MAX_TOPIC_COUNT = 5
        const val MAX_SUMMARY_COUNT = 5
        const val MAX_KEY_SENTENCE_COUNT = 5
        const val MAX_TOPIC_LENGTH = 40
        const val MAX_TEXT_LENGTH = 180
        const val MIN_TOPIC_LENGTH = 3

        val WORD_REGEX = Regex("[\\p{L}\\p{N}]+")
        val STOP_WORDS = setOf(
            "the",
            "and",
            "for",
            "that",
            "this",
            "with",
            "you",
            "your",
            "are",
            "was",
            "were",
            "have",
            "has",
            "had",
            "to",
            "of",
            "in",
            "on",
            "at",
            "is",
            "it"
        )
    }
}
