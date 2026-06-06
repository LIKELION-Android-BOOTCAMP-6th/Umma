package com.app.umma.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.chat.ChatConversationAnalysisJobTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatConversationAnalysisJobRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `enqueue replaces same session job and complete removes it`() = runBlocking {
        val repository = createRepository()

        repository.enqueue(job(sessionId = "session-1", finalTurnCount = 2, createdAt = 1_000L)).getOrThrow()
        repository.enqueue(job(sessionId = "session-1", finalTurnCount = 4, createdAt = 2_000L)).getOrThrow()

        val pending = repository.getPendingJobs(userId = "user-1", limit = 10).getOrThrow()

        // 같은 사용자/언어/세션 job은 하나만 유지하고 최신 finalTurnCount로 덮어써야 한다.
        assertEquals(1, pending.size)
        assertEquals(4, pending.single().finalTurnCount)
        assertEquals("hello | delimiter safe", pending.single().turns.first().text)
        assertEquals(2, pending.single().turns.first().tokenCount)
        assertEquals(1_000L, pending.single().turns.first().durationMs)
        assertEquals(0.9, pending.single().turns.first().confidence ?: 0.0, 0.0)

        repository.markCompleted(
            userId = "user-1",
            selectedLang = LangCode.EN,
            sessionId = "session-1"
        ).getOrThrow()

        assertTrue(repository.getPendingJobs(userId = "user-1", limit = 10).getOrThrow().isEmpty())
    }

    @Test
    fun `markAttempted increments attempt metadata without removing job`() = runBlocking {
        val repository = createRepository()
        val job = job(sessionId = "session-1", finalTurnCount = 2, createdAt = 1_000L)

        repository.enqueue(job).getOrThrow()
        repository.markAttempted(job = job, attemptedAt = 3_000L).getOrThrow()

        val pending = repository.getPendingJobs(userId = "user-1", limit = 10).getOrThrow().single()

        // 실패 재시도 관찰을 위해 attempt metadata만 갱신하고, 완료 전까지 job은 남겨둔다.
        assertEquals(1, pending.attemptCount)
        assertEquals(3_000L, pending.lastAttemptedAt)
    }

    @Test
    fun `getPendingJobs returns only requested user jobs`() = runBlocking {
        val repository = createRepository()

        repository.enqueue(job(userId = "user-1", sessionId = "session-1")).getOrThrow()
        repository.enqueue(job(userId = "user-2", sessionId = "session-2")).getOrThrow()

        val pending = repository.getPendingJobs(userId = "user-1", limit = 10).getOrThrow()

        // 계정 전환 후 다른 사용자의 pending 분석이 실행되면 개인정보와 LangState가 섞일 수 있다.
        assertEquals(listOf("session-1"), pending.map { it.sessionId })
    }

    private fun createRepository(): ChatConversationAnalysisJobRepositoryImpl {
        // 각 테스트가 독립된 Preferences 파일을 쓰게 해 pending queue 상태가 테스트 간 섞이지 않도록 한다.
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("chat_conversation_analysis.preferences_pb") }
        )
        return ChatConversationAnalysisJobRepositoryImpl(dataStore = dataStore)
    }

    private fun job(
        userId: String = "user-1",
        sessionId: String,
        finalTurnCount: Int = 2,
        createdAt: Long = 1_000L
    ): ChatConversationAnalysisJob {
        // USER text에 구분자처럼 보이는 문자를 넣어 JSON 직렬화가 transcript를 손상시키지 않는지 검증한다.
        // token/duration/confidence도 함께 넣어 pending retry가 LangState evidence 입력을 재구성할 수 있음을 고정한다.
        return ChatConversationAnalysisJob(
            userId = userId,
            selectedLang = LangCode.EN,
            sessionId = sessionId,
            finalTurnCount = finalTurnCount,
            turns = listOf(
                ChatConversationAnalysisJobTurn(
                    speaker = TurnSpeaker.USER,
                    text = "hello | delimiter safe",
                    createdAt = createdAt + 1,
                    tokenCount = 2,
                    durationMs = 1_000L,
                    confidence = 0.9
                ),
                ChatConversationAnalysisJobTurn(
                    speaker = TurnSpeaker.AI,
                    text = "Hi",
                    createdAt = createdAt + 2
                )
            ),
            createdAt = createdAt
        )
    }
}
