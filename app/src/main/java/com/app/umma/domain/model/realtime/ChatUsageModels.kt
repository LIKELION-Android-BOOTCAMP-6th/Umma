package com.app.umma.domain.model.realtime

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus

/**
 * OpenAI Realtime usage가 어떤 이벤트에서 나온 값인지 구분합니다.
 *
 * response usage와 transcription usage는 비용 중복 여부를 나중에 정책적으로 판단해야 하므로,
 * 저장 단계에서는 하나로 합산하지 않고 원천 이벤트별로 분리합니다.
 */
enum class ChatUsageKind {
    RESPONSE,
    TRANSCRIPTION
}

/**
 * OpenAI Realtime usage payload의 token breakdown입니다.
 *
 * 가격 계산은 text/audio, input/output 단가가 달라질 수 있으므로 total만 저장하지 않고
 * 가능한 세부 필드를 그대로 보존합니다. 값이 없는 필드는 API 이벤트가 제공하지 않은 값으로 보고 null을 유지합니다.
 */
data class ChatTokenUsage(
    val totalTokens: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val inputTextTokens: Long? = null,
    val inputAudioTokens: Long? = null,
    val inputCachedTokens: Long? = null,
    val outputTextTokens: Long? = null,
    val outputAudioTokens: Long? = null
)

/**
 * AI Chat 한 usage 이벤트를 저장 경로로 넘기는 domain 모델입니다.
 *
 * turnId는 정상 final transcript와 연결될 때 채워지지만, provider가 usage만 보내고 transcript가 비는
 * 예외도 있을 수 있어 nullable로 둡니다. 비용 분석은 sessionId 기준으로도 가능해야 합니다.
 */
data class ChatUsageRecord(
    val id: String,
    val userId: String,
    val sessionId: String,
    val turnId: String?,
    val language: LangCode,
    val kind: ChatUsageKind,
    val model: String,
    val transcriptionModel: String?,
    val createdAt: Long,
    val usage: ChatTokenUsage,
    val pricingVersion: String?,
    val syncStatus: SyncStatus
)

/**
 * Firestore에 올릴 세션 단위 usage aggregate입니다.
 *
 * Firestore write 비용을 줄이기 위해 개별 usage 이벤트를 매번 올리지 않고, 같은 세션의 pending usage를
 * 합산한 snapshot을 mirror합니다. local record는 원본 보존, aggregate는 원격 관측/분석용입니다.
 */
data class ChatUsageSessionAggregate(
    val id: String,
    val userId: String,
    val sessionId: String,
    val language: LangCode,
    val model: String,
    val startedAt: Long,
    val endedAt: Long,
    val recordCount: Int,
    val responseCount: Int,
    val transcriptionCount: Int,
    val responseUsage: ChatTokenUsage,
    val transcriptionUsage: ChatTokenUsage,
    val pricingVersion: String?,
    val syncedAt: Long
)

