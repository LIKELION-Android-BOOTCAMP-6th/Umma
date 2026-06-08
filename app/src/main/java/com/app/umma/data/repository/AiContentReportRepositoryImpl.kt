package com.app.umma.data.repository

import com.app.umma.domain.model.chat.AiContentReport
import com.app.umma.domain.model.chat.AiContentReportContextTurn
import com.app.umma.domain.repository.AiContentReportRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 운영용 AI 콘텐츠 신고 저장소입니다.
 *
 * 개발용 `chat_prompt_review_reports`와 저장소를 분리해 Google Play 대응용 신고 데이터를
 * release 빌드에서도 같은 계약으로 남길 수 있게 합니다.
 */
@Singleton
class AiContentReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : AiContentReportRepository {

    override suspend fun submitReport(report: AiContentReport): Result<Unit> {
        return runCatching {
            // reportId를 문서 id로 쓰면 같은 AI turn 중복 신고가 들어와도 하나의 운영 문서로 수렴한다.
            firestore.collection(AI_CONTENT_REPORTS_COLLECTION)
                .document(report.reportId)
                .set(report.toMap(), SetOptions.merge())
                .await()
        }
    }

    private fun AiContentReport.toMap(): Map<String, Any?> {
        // enum은 문자열로 저장해 Firestore Console에서 바로 읽을 수 있게 한다.
        // LangCode도 내부 enum 객체가 아니라 코드 문자열만 저장해야 cross-platform 운영 도구가 다루기 쉽다.
        return mapOf(
            "reportId" to reportId,
            "userId" to userId,
            "sessionId" to sessionId,
            "reportedTurnId" to reportedTurnId,
            "reportedAiText" to reportedAiText,
            "previousUserText" to previousUserText,
            "contextTurns" to contextTurns.map { it.toMap() },
            "primaryLang" to primaryLang.code,
            "selectedLang" to selectedLang.code,
            "reasonCategory" to reasonCategory.name,
            "detailNote" to detailNote,
            "reportedAt" to reportedAt,
            "status" to status.name,
            "appVersion" to appVersion,
            "modelVersion" to modelVersion,
            "promptVersion" to promptVersion,
            "promptRevision" to promptRevision
        )
    }

    private fun AiContentReportContextTurn.toMap(): Map<String, Any?> {
        // context turn은 운영 검토용 snapshot이므로 원문 text와 최소 식별자만 저장한다.
        // SessionMemory 전체를 복제하지 않아 개인정보 노출 범위를 줄인다.
        return mapOf(
            "turnId" to turnId,
            "sessionId" to sessionId,
            "role" to role.name,
            "text" to text,
            "createdAt" to createdAt
        )
    }

    private companion object {
        const val AI_CONTENT_REPORTS_COLLECTION = "ai_content_reports"
    }
}
