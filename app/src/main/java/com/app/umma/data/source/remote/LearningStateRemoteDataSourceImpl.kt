package com.app.umma.data.source.remote

import com.app.umma.data.model.learningstate.DashSummaryDto
import com.app.umma.data.model.learningstate.ExternalMetricsDto
import com.app.umma.data.model.learningstate.FlashcardSummaryDto
import com.app.umma.data.model.learningstate.InternalMetricsDto
import com.app.umma.data.model.learningstate.LangStateDto
import com.app.umma.data.model.learningstate.SessionSummaryDto
import com.app.umma.data.model.learningstate.UserLangPrefDto
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore에 저장된 Learning State 묶음을 DTO 단위로 읽는 remote data source.
 *
 * 저장은 여러 subcollection으로 흩어져 있지만, 앱 쪽에서는 GlobalLangState 하나로
 * 복원해야 하므로 이 계층에서 "사용자별 학습 상태 묶음"을 한 번에 가져온다.
 */
@Singleton
class LearningStateRemoteDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : LearningStateRemoteDataSource {

    override suspend fun fetch(userUid: String): LearningStateRemote {
        require(userUid.isNotBlank()) { "userUid must not be blank" }

        // Firestore는 한 번에 전체 snapshot을 읽되, 각 하위 컬렉션은 비어 있을 수 있다고 본다.
        // Source.SERVER: Firestore 자체 로컬 캐시 fallback 없이 항상 서버에서 읽는다.
        //   비행기 모드 등 실제 네트워크 단절 시 예외가 발생해야 ViewModel 에서 Snackbar 를 발화할 수 있다.
        //   Source.DEFAULT(기본값)는 오프라인 시 캐시를 반환해 sync 실패를 은폐한다.
        val userRef = firestore.collection("users").document(userUid)
        val userPref = userRef.collection("user_learning_preference")
            .document("current")
            .get(Source.SERVER)
            .await()
            .data
            ?.toUserLangPrefDto()

        return LearningStateRemote(
            userPref = userPref,
            // LS-001/LS-005/LS-007 문서 기준의 canonical path는 language_states다.
            langStates = userRef.readCollection("language_states") { data ->
                data.toLangStateDto()
            },
            dashSummaries = userRef.readCollection("dashboard_summaries") { data ->
                data.toDashSummaryDto()
            },
            sessionSummaries = userRef.readCollection("session_summaries") { data ->
                data.toSessionSummaryDto()
            },
            flashcardSummaries = userRef.readCollection("flashcard_summaries") { data ->
                data.toFlashcardSummaryDto()
            }
        )
    }

    override suspend fun sync(userUid: String, update: LearningStateRemoteUpdate): Result<Unit> {
        require(userUid.isNotBlank()) { "userUid must not be blank" }
        if (update.isEmpty) return Result.success(Unit)

        return runCatching {
            val userRef = firestore.collection("users").document(userUid)
            val batch = firestore.batch()

            // pending key에 걸린 항목만 batch에 싣는다.
            // 이렇게 해야 local-first 저장 직후 전체 스냅샷을 과하게 덮어쓰지 않는다.
            update.userPref?.let { userPref ->
                batch.set(
                    userRef.collection("user_learning_preference").document("current"),
                    userPref
                )
            }
            update.langStates.forEach { langState ->
                batch.setLanguageDocument(userRef, "language_states", langState.language, langState)
            }
            update.dashSummaries.forEach { summary ->
                batch.setLanguageDocument(userRef, "dashboard_summaries", summary.language, summary)
            }
            update.sessionSummaries.forEach { summary ->
                batch.setLanguageDocument(userRef, "session_summaries", summary.language, summary)
            }
            update.flashcardSummaries.forEach { summary ->
                batch.setLanguageDocument(userRef, "flashcard_summaries", summary.language, summary)
            }

            batch.commit().await()
        }
    }

    private fun WriteBatch.setLanguageDocument(
        userRef: DocumentReference,
        collectionName: String,
        language: String,
        dto: Any
    ) {
        // 문서 id는 설계 표준인 language code를 사용한다.
        set(userRef.collection(collectionName).document(language), dto)
    }

    private suspend fun <T> DocumentReference.readCollection(
        primaryName: String,
        fallbackName: String? = null,
        mapper: (Map<String, Any?>) -> T?
    ): List<T> {
        // Source.SERVER: userPref 와 동일한 이유 — 캐시 은폐 방지.
        val primaryDocs = collection(primaryName).get(Source.SERVER).await().documents
        val docs = if (primaryDocs.isNotEmpty() || fallbackName == null) {
            primaryDocs
        } else {
            // schema migration 중 이름이 다른 컬렉션을 만날 수 있어 fallback을 둔다.
            collection(fallbackName).get(Source.SERVER).await().documents
        }

        return docs.mapNotNull { document ->
            document.data?.let(mapper)
        }
    }

    private fun Map<String, Any?>.toUserLangPrefDto(): UserLangPrefDto? {
        return UserLangPrefDto(
            nativeLanguage = string("nativeLanguage") ?: return null,
            primaryLearningLanguage = string("primaryLearningLanguage") ?: return null,
            selectedLearningLanguage = string("selectedLearningLanguage") ?: return null,
            learningLanguages = stringList("learningLanguages"),
            schemaVersion = int("schemaVersion") ?: 1,
            updatedAt = long("updatedAt")
        )
    }

    private fun Map<String, Any?>.toLangStateDto(): LangStateDto? {
        val internal = map("internalMetrics")?.toInternalMetricsDto() ?: return null
        val external = map("externalMetrics")?.toExternalMetricsDto() ?: return null
        return LangStateDto(
            language = string("language") ?: return null,
            internalMetrics = internal,
            externalMetrics = external,
            schemaVersion = int("schemaVersion") ?: 1,
            createdAt = long("createdAt"),
            updatedAt = long("updatedAt"),
            lastAnalyzedAt = long("lastAnalyzedAt"),
            lastAnalysisEventId = string("lastAnalysisEventId")
        )
    }

    private fun Map<String, Any?>.toInternalMetricsDto(): InternalMetricsDto {
        // 누락된 metric은 MVP 초기값에 가까운 값으로 복원해 전체 fetch가 깨지지 않게 한다.
        return InternalMetricsDto(
            grammarAccuracy = double("grammarAccuracy") ?: 0.0,
            vocabularyAppropriateness = double("vocabularyAppropriateness") ?: 0.0,
            lexicalDiversity = double("lexicalDiversity") ?: 0.0,
            vocabularyLevel = string("vocabularyLevel") ?: "A1",
            sentenceComplexity = double("sentenceComplexity") ?: 0.0,
            speechRate = double("speechRate") ?: 0.0,
            pauseFrequency = double("pauseFrequency") ?: 0.0,
            avgUtteranceLength = double("avgUtteranceLength") ?: 0.0,
            spokenNaturalness = double("spokenNaturalness") ?: 0.0,
            naturalExpressionUsage = double("naturalExpressionUsage") ?: 0.0,
            errorRecurrence = double("errorRecurrence") ?: 0.0,
            reviewRetention = double("reviewRetention") ?: 0.0
        )
    }

    private fun Map<String, Any?>.toExternalMetricsDto(): ExternalMetricsDto {
        return ExternalMetricsDto(
            vocabularyLevel = string("vocabularyLevel") ?: "A1",
            grammarAccuracy = double("grammarAccuracy") ?: 0.0,
            expressionRange = int("expressionRange") ?: 0,
            fluencyScore = double("fluencyScore") ?: 0.0,
            naturalnessScore = double("naturalnessScore") ?: 0.0
        )
    }

    private fun Map<String, Any?>.toDashSummaryDto(): DashSummaryDto? {
        return DashSummaryDto(
            language = string("language") ?: return null,
            recentConversationMinutes = int("recentConversationMinutes") ?: 0,
            recentConversationTopic = string("recentConversationTopic"),
            correctionAvailable = boolean("correctionAvailable") ?: false,
            dueFlashcards = int("dueFlashcards") ?: 0,
            recentSavedFlashcards = int("recentSavedFlashcards") ?: 0,
            grammarScoreDelta = int("grammarScoreDelta") ?: 0,
            fluencyScoreDelta = int("fluencyScoreDelta") ?: 0,
            vocabularyScoreDelta = int("vocabularyScoreDelta") ?: 0,
            naturalnessScoreDelta = int("naturalnessScoreDelta") ?: 0,
            schemaVersion = int("schemaVersion") ?: 1,
            updatedAt = long("updatedAt")
        )
    }

    private fun Map<String, Any?>.toSessionSummaryDto(): SessionSummaryDto? {
        return SessionSummaryDto(
            language = string("language") ?: return null,
            correctionAvailable = boolean("correctionAvailable") ?: false,
            recentConversationMinutes = int("recentConversationMinutes") ?: 0,
            recentConversationTopic = string("recentConversationTopic"),
            updatedAt = long("updatedAt")
        )
    }

    private fun Map<String, Any?>.toFlashcardSummaryDto(): FlashcardSummaryDto? {
        return FlashcardSummaryDto(
            language = string("language") ?: return null,
            dueFlashcards = int("dueFlashcards") ?: 0,
            recentSavedFlashcards = int("recentSavedFlashcards") ?: 0,
            updatedAt = long("updatedAt")
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String): Map<String, Any?>? =
        this[key] as? Map<String, Any?>

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        return (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key] as? Boolean

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
}
