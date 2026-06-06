package com.app.umma.domain.repository

import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Chat conversation analysis pending job의 로컬 저장소 계약.
 *
 * Gemini 결과는 저장하지 않는다. 대신 분석 대상 세션의 최소 final turn snapshot을 local-first로 남겨,
 * 앱 재시작 후 SessionMemory가 이미 압축되었더라도 같은 payload로 분석을 재시도할 수 있게 한다.
 */
interface ChatConversationAnalysisJobRepository {
    suspend fun enqueue(job: ChatConversationAnalysisJob): Result<Unit>

    suspend fun getPendingJobs(userId: String, limit: Int): Result<List<ChatConversationAnalysisJob>>

    suspend fun markAttempted(job: ChatConversationAnalysisJob, attemptedAt: Long): Result<Unit>

    suspend fun markCompleted(userId: String, selectedLang: LangCode, sessionId: String): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
