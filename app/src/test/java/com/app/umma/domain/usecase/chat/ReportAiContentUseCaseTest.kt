package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.AiContentReport
import com.app.umma.domain.model.chat.AiContentReportReasonCategory
import com.app.umma.domain.model.chat.AiContentReportStatus
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.AiContentReportRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAiContentUseCaseTest {

    @Test
    fun `submits complete AI content report to repository`() = runBlocking {
        // 운영 신고는 Chat transport와 분리된 저장 작업이므로 fake repository로 전달 계약만 검증한다.
        val repository = RecordingAiContentReportRepository()
        val useCase = ReportAiContentUseCase(repository)
        val report = report()

        val result = useCase(report)

        assertTrue(result.isSuccess)
        assertEquals(report, repository.savedReport)
    }

    @Test
    fun `rejects report without AI final text`() = runBlocking {
        // 신고 대상 AI final text가 비어 있으면 운영자가 어떤 응답을 검토해야 하는지 알 수 없다.
        // 이 경우 Firestore에 불완전한 문서를 남기지 않고 repository 호출 전 실패시킨다.
        val repository = RecordingAiContentReportRepository()
        val useCase = ReportAiContentUseCase(repository)

        val result = useCase(report(reportedAiText = ""))

        assertTrue(result.isFailure)
        assertNull(repository.savedReport)
    }

    private fun report(
        userId: String = "user-1",
        sessionId: String = "session-1",
        reportedTurnId: String = "ai-turn-1",
        reportedAiText: String = "unsafe response"
    ): AiContentReport {
        // fixture는 정책 신고 저장에 필요한 최소 필드를 모두 채워 정상 경로를 재현한다.
        return AiContentReport(
            reportId = "${userId}_$reportedTurnId",
            userId = userId,
            sessionId = sessionId,
            reportedTurnId = reportedTurnId,
            reportedAiText = reportedAiText,
            previousUserText = "previous user",
            contextTurns = emptyList(),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN,
            reasonCategory = AiContentReportReasonCategory.HarmfulDangerous,
            detailNote = null,
            reportedAt = 1_000L,
            status = AiContentReportStatus.New,
            appVersion = "1.0",
            modelVersion = "gpt-realtime-mini",
            promptVersion = "chat_prompt_v2",
            promptRevision = "N027"
        )
    }

    private class RecordingAiContentReportRepository : AiContentReportRepository {
        var savedReport: AiContentReport? = null

        override suspend fun submitReport(report: AiContentReport): Result<Unit> {
            // 저장소 fake는 성공 경로에서 전달된 report를 그대로 기록해 UseCase 검증 근거로 쓴다.
            savedReport = report
            return Result.success(Unit)
        }
    }
}
