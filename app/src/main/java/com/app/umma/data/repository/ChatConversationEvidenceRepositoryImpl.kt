package com.app.umma.data.repository

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.SupportRequiredEvidence
import com.app.umma.domain.model.chat.UserContributionEvidence
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
 * Firestore의 Chat conversation evidence snapshot을 읽는 구현체.
 *
 * 저장 경로는 `users/{uid}/chat_conversation_evidence/{selectedLang}`이다.
 * 이 repository는 band를 계산하지 않고, Firestore 문자열 값을 domain evidence 모델로만 변환한다.
 */
@Singleton
class ChatConversationEvidenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : ChatConversationEvidenceRepository {

    override suspend fun getEvidence(selectedLang: LangCode): Result<ChatConversationEvidence?> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid()
                ?: return@runCatching null

            // evidence는 세션 시작을 막으면 안 되는 보조 입력이다.
            // 서버 조회가 지연되면 null로 빠져 기존 LangState/fallback 경로를 유지한다.
            val data = withTimeoutOrNull(OPTIONAL_READ_TIMEOUT_MS) {
                firestore.collection("users")
                    .document(uid)
                    .collection(COLLECTION)
                    .document(selectedLang.code)
                    .get(Source.SERVER)
                    .await()
                    .data
            }
                ?: return@runCatching null

            data.toChatConversationEvidence(defaultLang = selectedLang)
        }
    }

    override suspend fun saveEvidence(evidence: ChatConversationEvidence): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid()
                ?: return@runCatching

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
        val conversationSustainability =
            enumValue<ConversationSustainabilityEvidence>("conversationSustainability") ?: return null
        val supportRequiredToContinue =
            enumValue<SupportRequiredEvidence>("supportRequiredToContinue") ?: return null
        val userContributionLevel =
            enumValue<UserContributionEvidence>("userContributionLevel") ?: return null
        val responseDifficultyFit =
            enumValue<ResponseDifficultyFitEvidence>("responseDifficultyFit") ?: return null
        val confidence = enumValue<ProfileConfidence>("confidence") ?: return null
        val source = enumValue<ChatConversationEvidenceSource>("source") ?: return null

        return ChatConversationEvidence(
            selectedLang = lang,
            conversationSustainability = conversationSustainability,
            supportRequiredToContinue = supportRequiredToContinue,
            userContributionLevel = userContributionLevel,
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
            "conversationSustainability" to conversationSustainability.name,
            "supportRequiredToContinue" to supportRequiredToContinue.name,
            "userContributionLevel" to userContributionLevel.name,
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
        const val COLLECTION = "chat_conversation_evidence"
        const val OPTIONAL_READ_TIMEOUT_MS = 1_500L
    }
}
