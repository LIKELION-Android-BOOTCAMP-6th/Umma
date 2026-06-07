package com.app.umma.data.repository

import android.util.Log
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.ChatConversationEvidenceRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore의 Chat conversation evidence snapshot을 저장/조회하는 구현체.
 *
 * 저장 경로는 `users/{uid}/chat_conversation_evidence/{selectedLang}`이다.
 * 이 snapshot은 debug/review용이며, Chat band 공식 source는 LangState의 chatEvidenceSummary다.
 */
@Singleton
class ChatConversationEvidenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : ChatConversationEvidenceRepository {

    override suspend fun getEvidence(selectedLang: LangCode): Result<ChatConversationEvidence?> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid()
            if (uid == null) {
                Log.d(TAG, "chat_ability evidence_load_skipped lang=${selectedLang.code} reason=missing_uid")
                return@runCatching null
            }

            // snapshot 조회는 debug/review 보조 작업이다.
            // 서버 조회가 지연되면 null로 빠져 caller의 흐름을 막지 않는다.
            val data = withTimeoutOrNull(OPTIONAL_READ_TIMEOUT_MS) {
                firestore.collection("users")
                    .document(uid)
                    .collection(COLLECTION)
                    .document(selectedLang.code)
                    .get(Source.SERVER)
                    .await()
                    .data
            }
            if (data == null) {
                Log.d(
                    TAG,
                    "chat_ability evidence_loaded lang=${selectedLang.code} applied=false reason=empty_or_timeout"
                )
                return@runCatching null
            }

            val evidence = data.toChatConversationEvidence(defaultLang = selectedLang)
            Log.d(
                TAG,
                "chat_ability evidence_loaded lang=${selectedLang.code} applied=${evidence != null} " +
                    "band=${evidence?.debugRecommendedBand ?: "none"}"
            )
            evidence
        }
    }

    override suspend fun saveEvidence(evidence: ChatConversationEvidence): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid()
                ?: throw IllegalStateException("Current user uid is required to save chat conversation evidence")

            firestore.collection("users")
                .document(uid)
                .collection(COLLECTION)
                .document(evidence.selectedLang.code)
                .set(evidence.toMap(), SetOptions.merge())
                .await()
        }
    }

    private fun Map<String, Any?>.toChatConversationEvidence(
        defaultLang: LangCode
    ): ChatConversationEvidence? {
        // 필수 evidence가 하나라도 없으면 band 계산이 흔들리므로 snapshot을 적용하지 않는다.
        val lang = string("selectedLang")?.let(LangCode::fromCode) ?: defaultLang
        val targetLanguageComprehension =
            enumValue<TargetLanguageComprehensionEvidence>("targetLanguageComprehension")
                ?: legacyComprehensionEvidence()
                ?: return null
        val targetLanguageProduction =
            enumValue<TargetLanguageProductionEvidence>("targetLanguageProduction")
                ?: legacyProductionEvidence()
                ?: return null
        val supportLanguageDependence =
            enumValue<LanguageDependenceEvidence>("supportLanguageDependence")
                ?: legacyDependenceEvidence("supportRequiredToContinue")
                ?: return null
        val aiScaffoldingDependence =
            enumValue<LanguageDependenceEvidence>("aiScaffoldingDependence")
                ?: legacyDependenceEvidence("supportRequiredToContinue")
                ?: return null
        val conversationSustainability =
            enumValue<ConversationSustainabilityEvidence>("conversationSustainability")
                ?: legacySustainabilityEvidence()
                ?: return null
        val consistency =
            enumValue<ConversationConsistencyEvidence>("consistency")
                ?: legacyConsistencyEvidence()
                ?: return null
        val responseDifficultyFit =
            enumValue<ResponseDifficultyFitEvidence>("responseDifficultyFit") ?: return null
        val confidence = enumValue<ProfileConfidence>("confidence") ?: return null
        val source = enumValue<ChatConversationEvidenceSource>("source") ?: return null

        return ChatConversationEvidence(
            selectedLang = lang,
            targetLanguageComprehension = targetLanguageComprehension,
            targetLanguageProduction = targetLanguageProduction,
            supportLanguageDependence = supportLanguageDependence,
            aiScaffoldingDependence = aiScaffoldingDependence,
            conversationSustainability = conversationSustainability,
            consistency = consistency,
            responseDifficultyFit = responseDifficultyFit,
            confidence = confidence,
            source = source,
            sourceSessionId = string("sourceSessionId"),
            reasonSummary = string("reasonSummary"),
            debugRecommendedBand = enumValue<ConversationAbilityBand>("debugRecommendedBand"),
            updatedAt = long("updatedAt"),
            expiresAt = long("expiresAt")
        )
    }

    private fun ChatConversationEvidence.toMap(): Map<String, Any?> {
        return mapOf(
            "selectedLang" to selectedLang.code,
            "targetLanguageComprehension" to targetLanguageComprehension.name,
            "targetLanguageProduction" to targetLanguageProduction.name,
            "supportLanguageDependence" to supportLanguageDependence.name,
            "aiScaffoldingDependence" to aiScaffoldingDependence.name,
            "conversationSustainability" to conversationSustainability.name,
            "consistency" to consistency.name,
            "responseDifficultyFit" to responseDifficultyFit.name,
            "confidence" to confidence.name,
            "source" to source.name,
            "sourceSessionId" to sourceSessionId,
            "reasonSummary" to reasonSummary,
            "debugRecommendedBand" to debugRecommendedBand?.name,
            "updatedAt" to (updatedAt ?: System.currentTimeMillis()),
            "expiresAt" to expiresAt
        )
    }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.enumValue(key: String): T? {
        val raw = string(key) ?: return null
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    private fun Map<String, Any?>.legacyProductionEvidence(): TargetLanguageProductionEvidence? {
        // 기존 snapshot의 userContributionLevel은 학습언어 생산 단위에 가장 가까워 새 필드 fallback으로만 사용한다.
        return when (string("userContributionLevel")) {
            "Minimal" -> TargetLanguageProductionEvidence.None
            "WordsOrFragments" -> TargetLanguageProductionEvidence.WordsOrFragments
            "ShortPhrases" -> TargetLanguageProductionEvidence.ShortPhrases
            "SimpleSentences" -> TargetLanguageProductionEvidence.SimpleSentences
            "ConnectedTurns" -> TargetLanguageProductionEvidence.ConnectedTurns
            else -> null
        }
    }

    private fun Map<String, Any?>.legacyComprehensionEvidence(): TargetLanguageComprehensionEvidence? {
        // 기존 schema에는 이해 단서가 없으므로 생산 단위보다 한 단계 높게 보지 않는 보수 fallback만 허용한다.
        return when (legacyProductionEvidence()) {
            TargetLanguageProductionEvidence.None -> TargetLanguageComprehensionEvidence.None
            TargetLanguageProductionEvidence.WordsOrFragments -> TargetLanguageComprehensionEvidence.WordLevel
            TargetLanguageProductionEvidence.ShortPhrases -> TargetLanguageComprehensionEvidence.WordLevel
            TargetLanguageProductionEvidence.SimpleSentences -> TargetLanguageComprehensionEvidence.SimpleSentence
            TargetLanguageProductionEvidence.ConnectedTurns -> TargetLanguageComprehensionEvidence.NaturalFlow
            null -> null
        }
    }

    private fun Map<String, Any?>.legacyDependenceEvidence(key: String): LanguageDependenceEvidence? {
        // 기존 supportRequiredToContinue의 Moderate는 새 dependence schema에서 Medium으로 흡수한다.
        return when (string(key)) {
            "High" -> LanguageDependenceEvidence.High
            "Moderate" -> LanguageDependenceEvidence.Medium
            "Low" -> LanguageDependenceEvidence.Low
            "None" -> LanguageDependenceEvidence.None
            else -> null
        }
    }

    private fun Map<String, Any?>.legacySustainabilityEvidence(): ConversationSustainabilityEvidence? {
        return when (string("conversationSustainability")) {
            "RequiresSupport" -> ConversationSustainabilityEvidence.RequiresSupport
            "SupportedWithHints" -> ConversationSustainabilityEvidence.SupportedShort
            "SupportedShort" -> ConversationSustainabilityEvidence.SupportedShort
            "SustainedSimple" -> ConversationSustainabilityEvidence.SustainedSimple
            "SustainedNatural" -> ConversationSustainabilityEvidence.SustainedNatural
            else -> null
        }
    }

    private fun Map<String, Any?>.legacyConsistencyEvidence(): ConversationConsistencyEvidence? {
        // 과거 snapshot에는 세션 전체 일관성 값이 없어 confidence를 상한으로 둔 보수값만 만든다.
        return when (enumValue<ProfileConfidence>("confidence")) {
            ProfileConfidence.High -> ConversationConsistencyEvidence.Stable
            ProfileConfidence.Medium -> ConversationConsistencyEvidence.Mixed
            ProfileConfidence.Low -> ConversationConsistencyEvidence.Low
            null -> null
        }
    }

    private fun Map<String, Any?>.string(key: String): String? {
        return this[key] as? String
    }

    private fun Map<String, Any?>.long(key: String): Long? {
        val value = this[key] ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            else -> null
        }
    }

    private companion object {
        const val TAG = "AiChatPromptTrace"
        const val COLLECTION = "chat_conversation_evidence"
        const val OPTIONAL_READ_TIMEOUT_MS = 1_500L
    }
}
