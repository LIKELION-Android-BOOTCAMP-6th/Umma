package com.app.umma.devtools.correctionpromptreview

import android.util.Log
import com.app.umma.BuildConfig
import com.app.umma.domain.model.learningstate.LangCode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Correction prompt review repository.
 *
 * The report index lets testers and developers find suspicious Correction outputs quickly, while
 * the user-scoped review document keeps the full snapshot separate from production data.
 */
@Singleton
class CorrectionPromptReviewRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : CorrectionPromptReviewRepository {

    @Suppress("KotlinConstantConditions")
    override fun isEnabled(): Boolean {
        return BuildConfig.DEBUG &&
            BuildConfig.FLAVOR == DEV_FLAVOR &&
            BuildConfig.CORRECTION_PROMPT_REVIEW_ENABLED
    }

    override suspend fun report(snapshot: CorrectionPromptReviewSnapshot): Result<Unit> {
        if (!isEnabled()) return Result.failure(IllegalStateException("correction prompt review is disabled"))
        if (snapshot.uid.isBlank()) return Result.failure(IllegalStateException("uid is required"))

        return runCatching {
            val reportId = reportDocumentId(
                reportedAt = snapshot.reportedAt,
                language = snapshot.language,
                sourceKey = snapshot.sourceKey
            )
            val reviewPath = "users/${snapshot.uid}/$CORRECTION_PROMPT_REVIEWS_COLLECTION/$reportId"
            val updatedAt = System.currentTimeMillis()
            val reviewMap = mapOf(
                "reportId" to reportId,
                // COR-FIX-013: userId 추가 — reportMap과 동일한 owner 필드를 리뷰 문서에도 유지해 도구 일관성 확보.
                "userId" to snapshot.uid,
                "uid" to snapshot.uid,
                "language" to snapshot.language.code,
                "primaryLanguage" to snapshot.primaryLanguage?.code,
                "phase" to snapshot.phase,
                "reportNote" to sanitizeReportNote(snapshot.reportNote),
                "reportedAt" to snapshot.reportedAt,
                "updatedAt" to updatedAt,
                "sourceKey" to snapshot.sourceKey,
                // COR-FIX-013: 적용된 교정 성장 band — 리뷰 문서에도 기록해 export 도구에서 바로 읽을 수 있게 한다.
                "promptBand" to snapshot.promptBand,
                "suggestionCount" to snapshot.suggestions.size,
                "selectedSuggestionIds" to snapshot.selectedSuggestionIds.toList().sorted(),
                "suggestions" to snapshot.suggestions.map(::suggestionMap),
                "contextTurns" to snapshot.contextTurns.map(::contextTurnMap),
                "errorReason" to snapshot.errorReason,
                "saveErrorReason" to snapshot.saveErrorReason,
                "completionErrorReason" to snapshot.completionErrorReason,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR
            )
            val reportMap = mapOf(
                "reportId" to reportId,
                // COR-FIX-013: Firestore rules validate top-level index with userId (== request.auth.uid).
                // Keep uid for existing tooling compatibility.
                "userId" to snapshot.uid,
                "uid" to snapshot.uid,
                "reviewId" to reportId,
                "reviewPath" to reviewPath,
                "language" to snapshot.language.code,
                "primaryLanguage" to snapshot.primaryLanguage?.code,
                "phase" to snapshot.phase,
                "status" to "ready",
                "reportNote" to sanitizeReportNote(snapshot.reportNote),
                "reportedAt" to snapshot.reportedAt,
                "updatedAt" to updatedAt,
                "sourceKey" to snapshot.sourceKey,
                // COR-FIX-013: 적용된 교정 성장 band — 콘솔에서 프롬프트 튜닝 효과를 band별로 추적한다.
                "promptBand" to snapshot.promptBand,
                "suggestionCount" to snapshot.suggestions.size,
                "selectedCount" to snapshot.selectedSuggestionIds.size,
                "errorReason" to snapshot.errorReason,
                "saveErrorReason" to snapshot.saveErrorReason,
                "completionErrorReason" to snapshot.completionErrorReason,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR
            )

            firestore.collection(USERS_COLLECTION)
                .document(snapshot.uid)
                .collection(CORRECTION_PROMPT_REVIEWS_COLLECTION)
                .document(reportId)
                .set(reviewMap, SetOptions.merge())
                .await()
            firestore.collection(CORRECTION_PROMPT_REVIEW_REPORTS_COLLECTION)
                .document(reportId)
                .set(reportMap, SetOptions.merge())
                .await()

            Log.i(
                LOG_TAG,
                "correctionReviewSaved uid=${snapshot.uid} reportId=$reportId " +
                    "lang=${snapshot.language.code} suggestions=${snapshot.suggestions.size} " +
                    "promptBand=${snapshot.promptBand ?: "unknown"} reviewPath=$reviewPath"
            )
        }
    }

    private companion object {
        private const val LOG_TAG = "CorrectionPromptReview"
        private const val DEV_FLAVOR = "dev"
        private const val USERS_COLLECTION = "users"
        private const val CORRECTION_PROMPT_REVIEWS_COLLECTION = "correction_prompt_reviews"
        private const val CORRECTION_PROMPT_REVIEW_REPORTS_COLLECTION = "correction_prompt_review_reports"
    }
}

private const val MAX_REPORT_NOTE_LENGTH = 500
private const val REPORT_ID_SOURCE_PREFIX_LENGTH = 8
private const val REPORT_ID_TIME_ZONE = "Asia/Seoul"
private const val REPORT_ID_TIME_PATTERN = "yyyyMMdd_HHmmss"

private fun suggestionMap(suggestion: CorrectionPromptReviewSuggestion): Map<String, Any?> =
    mapOf(
        "id" to suggestion.id,
        "sourceCandidateIds" to suggestion.sourceCandidateIds,
        "sourceTurnIndex" to suggestion.sourceTurnIndex,
        "beforeText" to suggestion.beforeText,
        "nativeText" to suggestion.nativeText,
        "afterText" to suggestion.afterText,
        "explanation" to suggestion.explanation,
        "sourceLang" to suggestion.sourceLang?.code
    )

private fun contextTurnMap(turn: CorrectionPromptReviewContextTurn): Map<String, Any?> =
    mapOf(
        "speaker" to turn.speaker,
        "text" to turn.text
    )

private fun sanitizeReportNote(reportNote: String?): String? {
    return reportNote
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_REPORT_NOTE_LENGTH)
        ?.takeIf { it.isNotBlank() }
}

private fun reportDocumentId(
    reportedAt: Long,
    language: LangCode,
    sourceKey: String
): String {
    val timestamp = SimpleDateFormat(REPORT_ID_TIME_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone(REPORT_ID_TIME_ZONE) }
        .format(Date(reportedAt))
    val sourcePrefix = sourceKey
        .take(REPORT_ID_SOURCE_PREFIX_LENGTH)
        .ifBlank { "unknown" }
    return "${timestamp}_${language.code}_$sourcePrefix"
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
}
