package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.chat.ChatConversationAnalysisJobTurn
import com.app.umma.domain.model.chat.ChatConversationAnalysisTurn
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.learningstate.ChatSignalUpdateInput
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.ChatConversationAnalysisRepository
import com.app.umma.domain.repository.ChatConversationEvidenceRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.learningstate.ApplyChatSignalUpdateUseCase
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
    private val evidenceRepository: ChatConversationEvidenceRepository,
    private val authRepository: AuthRepository,
    private val applyChatSignalUpdateUseCase: ApplyChatSignalUpdateUseCase
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
                    sessionId = sessionId,
                    turnCount = sessionTurns.size,
                    userTurnCount = sessionTurns.count { it.role == TurnSpeaker.USER },
                    aiTurnCount = sessionTurns.count { it.role == TurnSpeaker.AI }
                )
            }

            analyzeTurns(
                selectedLang = selectedLang,
                sessionId = sessionId,
                analysisTurns = sessionTurns.map { it.toAnalysisTurn() },
                userTurns = sessionTurns
                    .filter { turn -> turn.role == TurnSpeaker.USER && turn.text.isNotBlank() }
                    .map { turn -> turn.toConversationTurn() }
            )
        }
    }

    suspend operator fun invoke(
        job: ChatConversationAnalysisJob
    ): Result<ChatConversationSessionAnalysisResult> {
        return runCatching {
            // pending retry는 SessionMemory를 다시 읽지 않는다.
            // Correction이 recentFullContext를 이미 압축했을 수 있으므로 job에 저장된 snapshot만 사용한다.
            val turns = job.turns
                .filter { turn -> turn.text.isNotBlank() }
                .sortedBy { turn -> turn.createdAt }

            if (!turns.hasEnoughJobConversationForAnalysis()) {
                // snapshot 자체가 깨졌거나 너무 짧으면 같은 job을 다시 돌려도 분석 근거가 생기지 않는다.
                // caller는 Skipped를 완료로 처리해 무한 retry를 막는다.
                return@runCatching ChatConversationSessionAnalysisResult.Skipped(
                    reason = "not_enough_snapshot_turns",
                    sessionId = job.sessionId,
                    turnCount = turns.size,
                    userTurnCount = turns.count { it.speaker == TurnSpeaker.USER },
                    aiTurnCount = turns.count { it.speaker == TurnSpeaker.AI }
                )
            }

            analyzeTurns(
                selectedLang = job.selectedLang,
                sessionId = job.sessionId,
                analysisTurns = turns.map { turn -> turn.toAnalysisTurn() },
                userTurns = turns
                    .filter { turn -> turn.speaker == TurnSpeaker.USER && turn.text.isNotBlank() }
                    .map { turn -> turn.toConversationTurn() }
            )
        }
    }

    private suspend fun analyzeTurns(
        selectedLang: LangCode,
        sessionId: String,
        analysisTurns: List<ChatConversationAnalysisTurn>,
        userTurns: List<ConversationTurn>
    ): ChatConversationSessionAnalysisResult.Saved {
        val analysisSession = ChatConversationAnalysisSession(
            sessionId = sessionId,
            selectedLang = selectedLang,
            turns = analysisTurns
        )

        // Gemini 분석은 source of truth가 될 구조화 evidence만 만든다.
        // 이후 snapshot 저장과 LangState update는 서로 막지 않는 best-effort 흐름으로 분리한다.
        val evidence = analysisRepository
            .analyze(analysisSession)
            .getOrThrow()

        val snapshotResult = evidenceRepository.saveEvidence(evidence)
        val langStateUpdateResult = buildChatSignalInputOrNull(
            selectedLang = selectedLang,
            sessionId = sessionId,
            evidence = evidence,
            userTurns = userTurns
        )?.let { input ->
            applyChatSignalUpdateUseCase(input)
        }

        return ChatConversationSessionAnalysisResult.Saved(
            evidence = evidence,
            snapshotSaved = snapshotResult.isSuccess,
            langStateUpdate = langStateUpdateResult?.getOrNull(),
            snapshotFailureMessage = snapshotResult.exceptionOrNull()?.message,
            langStateFailureMessage = langStateUpdateResult?.exceptionOrNull()?.message
        )
    }

    private fun buildChatSignalInputOrNull(
        selectedLang: LangCode,
        sessionId: String,
        evidence: ChatConversationEvidence,
        userTurns: List<ConversationTurn>
    ): ChatSignalUpdateInput? {
        // LangState update는 사용자별 공식 상태를 바꾸는 작업이므로 uid가 없으면 snapshot 저장까지만 남긴다.
        val uid = authRepository.getCurrentUserUid()?.trim()
        if (uid.isNullOrBlank()) return null

        // USER turn이 없으면 대화 능력 근거가 아니라 AI 단독 응답 분석이 되므로 공식 LangState에는 반영하지 않는다.
        if (userTurns.isEmpty()) return null

        return ChatSignalUpdateInput(
            uid = uid,
            lang = selectedLang,
            sessionMemoryKey = "${uid}_${selectedLang.code}",
            sourceSessionId = evidence.sourceSessionId?.takeIf { it.isNotBlank() } ?: sessionId,
            evidence = evidence,
            recentUserTurns = userTurns,
            analyzedAt = evidence.updatedAt ?: System.currentTimeMillis()
        )
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

    private fun SessionTurn.toConversationTurn(): ConversationTurn {
        // LangState update에는 AI turn이 아니라 USER final transcript의 보조 metadata만 넘긴다.
        // token/duration/confidence가 없으면 null로 유지해 retry와 즉시 분석의 입력 계약을 같게 둔다.
        return ConversationTurn(
            speaker = role,
            text = text.trim(),
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    private fun ChatConversationAnalysisJobTurn.toAnalysisTurn(): ChatConversationAnalysisTurn {
        // Gemini 분석 payload는 저장소 DTO가 아니라 domain 분석 입력으로 제한해 전달한다.
        return ChatConversationAnalysisTurn(
            speaker = speaker,
            text = text,
            createdAt = createdAt
        )
    }

    private fun ChatConversationAnalysisJobTurn.toConversationTurn(): ConversationTurn {
        // pending retry에서도 즉시 분석과 같은 LangState evidence 입력을 만들기 위해 snapshot metadata를 복원한다.
        return ConversationTurn(
            speaker = speaker,
            text = text.trim(),
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    private fun List<ChatConversationAnalysisJobTurn>.hasEnoughJobConversationForAnalysis(): Boolean {
        // 최소 USER/AI 왕복이 없으면 "대화 지속 능력"이 아니라 단독 문장 샘플이 된다.
        val hasUserTurn = any { it.speaker == TurnSpeaker.USER }
        val hasAiTurn = any { it.speaker == TurnSpeaker.AI }
        return size >= MIN_FINAL_TURNS && hasUserTurn && hasAiTurn
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
        val evidence: ChatConversationEvidence,
        val snapshotSaved: Boolean,
        val langStateUpdate: LearningStateUpdateResult?,
        val snapshotFailureMessage: String?,
        val langStateFailureMessage: String?
    ) : ChatConversationSessionAnalysisResult

    /**
     * 분석할 충분한 대화가 없어 저장하지 않은 상태입니다.
     */
    data class Skipped(
        val reason: String,
        val sessionId: String,
        val turnCount: Int,
        val userTurnCount: Int,
        val aiTurnCount: Int
    ) : ChatConversationSessionAnalysisResult
}
