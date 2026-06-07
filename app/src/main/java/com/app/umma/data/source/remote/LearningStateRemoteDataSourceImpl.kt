package com.app.umma.data.source.remote

import com.app.umma.data.model.learningstate.DashSummaryDto
import com.app.umma.data.model.learningstate.ChatEvidenceSummaryDto
import com.app.umma.data.model.learningstate.ExternalMetricsDto
import com.app.umma.data.model.learningstate.FlashcardSummaryDto
import com.app.umma.data.model.learningstate.InternalMetricsDto
import com.app.umma.data.model.learningstate.LangStateAnalysisMetaDto
import com.app.umma.data.model.learningstate.LangStateDto
import com.app.umma.data.model.learningstate.LearningFocusDto
import com.app.umma.data.model.learningstate.MetricEvidenceDto
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
        val primaryLanguage = string("primaryLanguage") ?: return null
        val learningLanguages = stringList("learningLanguages")
        return UserLangPrefDto(
            primaryLanguage = primaryLanguage,
            // 원격 preference의 현재 학습 언어가 없으면 설정 문서가 불완전한 상태다.
            // 임의 언어로 복구하면 다른 사용자의 학습 데이터 key처럼 보일 수 있어 기존 흐름처럼 null 처리한다.
            selectedLearningLanguage = string("selectedLearningLanguage") ?: return null,
            learningLanguages = learningLanguages,
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
            // schema v1 remote 문서에는 analysisMeta가 없다. null로 두면 DTO mapper가 initial meta로 복원한다.
            analysisMeta = map("analysisMeta")?.toLangStateAnalysisMetaDto(),
            schemaVersion = int("schemaVersion") ?: 1,
            createdAt = long("createdAt"),
            updatedAt = long("updatedAt"),
            lastAnalyzedAt = long("lastAnalyzedAt"),
            lastAnalysisEventId = string("lastAnalysisEventId")
        )
    }

    private fun Map<String, Any?>.toLangStateAnalysisMetaDto(): LangStateAnalysisMetaDto {
        // analysisMeta는 schema v2부터 존재한다.
        // Firestore에 일부 필드만 있더라도 기본값으로 비어 있는 meta를 만들 수 있어야 v1/v2 혼합 데이터를 읽을 수 있다.
        return LangStateAnalysisMetaDto(
            // metricEvidence는 metric key별 map이다. 개별 evidence가 깨져 있으면 그 entry만 제외한다.
            metricEvidence = map("metricEvidence")?.mapValuesNotNull { (_, value) ->
                (value as? Map<*, *>)?.toStringAnyMap()?.toMetricEvidenceDto()
            }.orEmpty(),
            // activeFocus는 배열 형태다. focus 하나가 깨져도 나머지 focus는 유지한다.
            activeFocus = list("activeFocus").mapNotNull { rawFocus ->
                (rawFocus as? Map<*, *>)?.toStringAnyMap()?.toLearningFocusDto()
            },
            lastSignalAt = long("lastSignalAt"),
            lastChatAnalysisEventId = string("lastChatAnalysisEventId"),
            // chatEvidenceSummary는 CHAT-TUNE-007부터 추가된다. 없거나 깨진 값이면 DTO mapper가 null로 복원한다.
            chatEvidenceSummary = map("chatEvidenceSummary")?.toChatEvidenceSummaryDto()
        )
    }

    private fun Map<String, Any?>.toChatEvidenceSummaryDto(): ChatEvidenceSummaryDto? {
        // Chat band source라 enum 필드가 하나라도 없으면 summary 전체를 쓰지 않는다.
        // 앱은 이 경우 first selectedLang fallback으로 안전하게 시작한다.
        return ChatEvidenceSummaryDto(
            targetLanguageComprehension = string("targetLanguageComprehension") ?: return null,
            targetLanguageProduction = string("targetLanguageProduction") ?: return null,
            supportLanguageDependence = string("supportLanguageDependence") ?: return null,
            aiScaffoldingDependence = string("aiScaffoldingDependence") ?: return null,
            conversationSustainability = string("conversationSustainability") ?: return null,
            consistency = string("consistency") ?: return null,
            responseDifficultyFit = string("responseDifficultyFit") ?: return null,
            confidence = string("confidence") ?: return null,
            observedCount = int("observedCount") ?: 0,
            lastObservedAt = long("lastObservedAt")
        )
    }

    private fun Map<String, Any?>.toMetricEvidenceDto(): MetricEvidenceDto? {
        // confidence/direction은 evidence 판단의 핵심이므로 없으면 해당 evidence만 버린다.
        // enum 값 자체의 유효성은 공통 DTO mapper에서 다시 검증한다.
        return MetricEvidenceDto(
            observedCount = int("observedCount") ?: 0,
            confidence = double("confidence") ?: return null,
            sourceTypes = stringList("sourceTypes"),
            direction = string("direction") ?: return null,
            directionCount = int("directionCount") ?: 0,
            lastObservedAt = long("lastObservedAt")
        )
    }

    private fun Map<String, Any?>.toLearningFocusDto(): LearningFocusDto? {
        // focus는 type/confidence/관측 시각이 있어야 prompt 후보로 쓸 수 있다.
        // 필수 필드가 없으면 해당 focus만 제외해 전체 LangState 복원을 막지 않는다.
        return LearningFocusDto(
            type = string("type") ?: return null,
            observedCount = int("observedCount") ?: 0,
            confidence = double("confidence") ?: return null,
            firstObservedAt = long("firstObservedAt") ?: return null,
            lastObservedAt = long("lastObservedAt") ?: return null
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
            notifiableDueFlashcards = int("notifiableDueFlashcards") ?: 0,
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
            notifiableDueFlashcards = int("notifiableDueFlashcards") ?: 0,
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

    private fun Map<String, Any?>.list(key: String): List<*> {
        return this[key] as? List<*> ?: emptyList<Any?>()
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key] as? Boolean

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<*, *>.toStringAnyMap(): Map<String, Any?> {
        // Firestore nested map은 Map<*, *>로 들어오므로, string key만 살려 DTO mapper에 넘긴다.
        return entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value }
        }.toMap()
    }

    private inline fun <T, R : Any> Map<String, T>.mapValuesNotNull(
        transform: (Map.Entry<String, T>) -> R?
    ): Map<String, R> {
        // metricEvidence는 key별로 일부 항목만 오염될 수 있으므로 전체 meta를 버리지 않고 유효 항목만 살린다.
        return mapNotNull { entry ->
            transform(entry)?.let { entry.key to it }
        }.toMap()
    }
}
