package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationAnalysisTurn
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.repository.ChatConversationAnalysisRepository
import com.app.umma.domain.repository.ChatConversationEvidenceRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * Chat 세션 종료 후 대화 능력 근거를 분석하고 저장합니다.
 *
 * 이 usecase는 신고 버튼과 무관한 실제 능력 측정 파이프라인이다. 화면 종료/이탈 시 백그라운드에서
 * 호출되며, 실패해도 사용자 대화 종료 흐름으로 전파하지 않는 best-effort 작업으로 사용한다.
 */
class AnalyzeChatConversationSessionUseCase @Inject constructor(
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val analysisRepository: ChatConversationAnalysisRepository,
    private val evidenceRepository: ChatConversationEvidenceRepository
) {
    suspend operator fun invoke(
        selectedLang: LangCode,
        sessionId: String
    ): Result<ChatConversationSessionAnalysisResult> {
        return runCatching {
            // SessionMemory는 final transcript 저장의 source of truth이므로, 저장에 성공한 turn만 분석한다.
            val sessionTurns = sessionMemoryRepository
                .getSessionMemory(selectedLang)
                .getOrThrow()
                .recentFullContext
                .filter { it.sessionId == sessionId && it.text.isNotBlank() }
                .sortedBy { it.createdAt }

            if (!sessionTurns.hasEnoughConversationForAnalysis()) {
                return@runCatching ChatConversationSessionAnalysisResult.Skipped(
                    reason = "not_enough_final_turns",
                    sessionId = sessionId
                )
            }

            val analysisSession = ChatConversationAnalysisSession(
                sessionId = sessionId,
                selectedLang = selectedLang,
                turns = sessionTurns.map { it.toAnalysisTurn() }
            )

            // Gemini 분석 결과는 바로 LangState에 쓰지 않고 Chat 전용 evidence 저장소에만 남긴다.
            val evidence = analysisRepository
                .analyze(analysisSession)
                .getOrThrow()
            evidenceRepository
                .saveEvidence(evidence)
                .getOrThrow()

            ChatConversationSessionAnalysisResult.Saved(evidence = evidence)
        }
    }

    /**
     * 최소한 한 번의 사용자 반응과 한 번의 AI 응답이 있어야 대화 지속 능력 근거로 해석할 수 있다.
     */
    private fun List<SessionTurn>.hasEnoughConversationForAnalysis(): Boolean {
        val hasUserTurn = any { it.role == TurnSpeaker.USER }
        val hasAiTurn = any { it.role == TurnSpeaker.AI }
        return size >= MIN_FINAL_TURNS && hasUserTurn && hasAiTurn
    }

    /**
     * 저장 모델에서 분석에 필요한 필드만 분리해 외부 AI prompt builder의 입력을 제한한다.
     */
    private fun SessionTurn.toAnalysisTurn(): ChatConversationAnalysisTurn {
        return ChatConversationAnalysisTurn(
            speaker = role,
            text = text,
            createdAt = createdAt
        )
    }

    private companion object {
        const val MIN_FINAL_TURNS = 2
    }
}

/**
 * 세션 후 분석 실행 결과입니다.
 */
sealed interface ChatConversationSessionAnalysisResult {
    /**
     * 분석 결과가 저장되어 다음 세션 시작 profile 계산에 사용할 수 있는 상태입니다.
     */
    data class Saved(
        val evidence: ChatConversationEvidence
    ) : ChatConversationSessionAnalysisResult

    /**
     * 분석할 충분한 대화가 없어 저장하지 않은 상태입니다.
     */
    data class Skipped(
        val reason: String,
        val sessionId: String
    ) : ChatConversationSessionAnalysisResult
}
