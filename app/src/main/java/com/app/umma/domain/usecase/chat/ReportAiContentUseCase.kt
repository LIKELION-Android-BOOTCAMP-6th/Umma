package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.AiContentReport
import com.app.umma.domain.repository.AiContentReportRepository
import javax.inject.Inject

/**
 * 운영용 AI 생성 콘텐츠 신고를 접수하는 UseCase입니다.
 *
 * ViewModel이 Firestore 저장소를 직접 알지 않게 하여, 신고 저장 실패가 Chat transport나
 * SessionMemory 저장 흐름으로 번지지 않도록 책임 경계를 유지합니다.
 */
class ReportAiContentUseCase @Inject constructor(
    private val repository: AiContentReportRepository
) {
    suspend operator fun invoke(report: AiContentReport): Result<Unit> {
        // 필수 텍스트가 비어 있으면 운영자가 신고 대상을 재현할 수 없으므로 저장 전에 막는다.
        if (report.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("userId is required for AI content report"))
        }
        if (report.sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("sessionId is required for AI content report"))
        }
        if (report.reportedTurnId.isBlank() || report.reportedAiText.isBlank()) {
            return Result.failure(IllegalArgumentException("reported AI final turn is required"))
        }
        return repository.submitReport(report)
    }
}
