package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.chat.ChatConversationAnalysisJobTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * Chat 세션 분석 pending job을 로컬에 먼저 남긴다.
 *
 * 앱이 Gemini 분석 시작 전 강제 종료되어도 다음 Chat 진입 때 같은 세션을 다시 분석할 수 있게 한다.
 */
class EnqueueChatConversationAnalysisJobUseCase @Inject constructor(
    private val repository: ChatConversationAnalysisJobRepository,
    private val sessionMemoryRepository: SessionMemoryRepository
) {
    suspend operator fun invoke(
        userId: String,
        selectedLang: LangCode,
        sessionId: String,
        finalTurnCount: Int,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("userId must not be blank"))
        if (sessionId.isBlank()) return Result.failure(IllegalArgumentException("sessionId must not be blank"))
        if (finalTurnCount < 0) return Result.failure(IllegalArgumentException("finalTurnCount must not be negative"))

        // pending job은 session id 포인터가 아니라 분석 payload의 최소 snapshot을 가져야 한다.
        // 그래야 이후 Correction이 SessionMemory를 압축해도 같은 대화로 Gemini 분석을 재시도할 수 있다.
        val turns = sessionMemoryRepository
            .getSessionMemory(selectedLang)
            .getOrElse { error -> return Result.failure(error) }
            .recentFullContext
            .filter { turn -> turn.sessionId == sessionId && turn.text.isNotBlank() }
            .sortedBy { turn -> turn.createdAt }
            .map { turn -> turn.toJobTurn() }

        if (!turns.hasEnoughConversationForAnalysis()) {
            // USER/AI 왕복이 저장되기 전이면 snapshot을 만들지 않는다.
            // 빈 job을 남기면 다음 진입 때도 동일하게 분석할 수 없어 retry queue만 오염된다.
            return Result.failure(IllegalStateException("not enough persisted turns for chat conversation analysis job"))
        }

        return repository.enqueue(
            ChatConversationAnalysisJob(
                userId = userId,
                selectedLang = selectedLang,
                sessionId = sessionId,
                finalTurnCount = finalTurnCount,
                turns = turns,
                createdAt = createdAt
            )
        )
    }

    private fun SessionTurn.toJobTurn(): ChatConversationAnalysisJobTurn {
        // job snapshot에는 Gemini 분석과 LangState evidence 계산에 필요한 확정 transcript 정보만 남긴다.
        // SessionMemory의 전체 구조를 복제하지 않아 pending 저장소가 장기 대화 저장소처럼 커지지 않게 한다.
        return ChatConversationAnalysisJobTurn(
            speaker = role,
            text = text.trim(),
            createdAt = createdAt,
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }
}

/**
 * 분석이 공식 LangState 반영까지 끝난 pending job을 제거한다.
 */
class CompleteChatConversationAnalysisJobUseCase @Inject constructor(
    private val repository: ChatConversationAnalysisJobRepository
) {
    suspend operator fun invoke(
        userId: String,
        selectedLang: LangCode,
        sessionId: String
    ): Result<Unit> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("userId must not be blank"))
        if (sessionId.isBlank()) return Result.failure(IllegalArgumentException("sessionId must not be blank"))

        return repository.markCompleted(
            userId = userId,
            selectedLang = selectedLang,
            sessionId = sessionId
        )
    }
}

/**
 * 이전 앱 실행에서 남은 Chat conversation analysis job을 재시도한다.
 *
 * job이 가진 불변 turn snapshot으로 재시도하므로, Correction이 SessionMemory를 압축해도 분석할 수 있다.
 */
class SyncPendingChatConversationAnalysisJobsUseCase @Inject constructor(
    private val repository: ChatConversationAnalysisJobRepository,
    private val analyzeChatConversationSessionUseCase: AnalyzeChatConversationSessionUseCase
) {
    suspend operator fun invoke(userId: String): Result<Int> {
        return runCatching {
            if (userId.isBlank()) return@runCatching 0

            val jobs = repository.getPendingJobs(userId = userId, limit = PENDING_JOB_RETRY_LIMIT).getOrThrow()
            var completedCount = 0
            var firstFailure: Throwable? = null

            jobs.forEach { job ->
                // retry 시도를 먼저 기록해 같은 job이 반복 실패하는지 로그/디버깅에서 추적할 수 있게 한다.
                // attemptCount는 현재 차단 조건이 아니라 관찰용 metadata다.
                repository.markAttempted(job = job, attemptedAt = System.currentTimeMillis())
                analyzeChatConversationSessionUseCase(
                    job = job
                ).onSuccess { result ->
                    if (result.isAnalysisCompleteForPendingJob()) {
                        // LangState까지 반영됐거나 더 이상 분석할 수 없는 snapshot이면 queue에서 제거한다.
                        // 그렇지 않으면 다음 Chat 진입 때 같은 job을 다시 시도한다.
                        repository
                            .markCompleted(
                                userId = job.userId,
                                selectedLang = job.selectedLang,
                                sessionId = job.sessionId
                            )
                            .getOrThrow()
                        completedCount += 1
                    } else if (firstFailure == null) {
                        firstFailure = IllegalStateException("chat conversation analysis did not update LangState")
                    }
                }.onFailure { failure ->
                    if (firstFailure == null) firstFailure = failure
                }
            }

            if (completedCount == 0) {
                firstFailure?.let { throw it }
            }
            completedCount
        }
    }

    private fun ChatConversationSessionAnalysisResult.isAnalysisCompleteForPendingJob(): Boolean {
        return when (this) {
            // 분석할 최소 turn이 없으면 재시도해도 공식 능력 근거가 만들어지지 않으므로 job을 완료 처리한다.
            is ChatConversationSessionAnalysisResult.Skipped -> true
            is ChatConversationSessionAnalysisResult.Saved -> {
                // snapshot은 debug용 best-effort이므로 실패해도 job 완료 기준이 아니다.
                // 공식 상태 반영인 LangState update가 성공해야 pending을 제거한다.
                langStateUpdate != null && langStateFailureMessage == null
            }
        }
    }

    private companion object {
        const val PENDING_JOB_RETRY_LIMIT = 5
    }
}

private fun List<ChatConversationAnalysisJobTurn>.hasEnoughConversationForAnalysis(): Boolean {
    // Gemini conversation evidence는 최소한 사용자 발화와 AI 응답이 함께 있어야 의미가 있다.
    // USER 단독 turn은 교정 후보일 수는 있어도 대화 지속 능력 근거로는 부족하다.
    val hasUserTurn = any { turn -> turn.speaker == TurnSpeaker.USER }
    val hasAiTurn = any { turn -> turn.speaker == TurnSpeaker.AI }
    return size >= 2 && hasUserTurn && hasAiTurn
}
