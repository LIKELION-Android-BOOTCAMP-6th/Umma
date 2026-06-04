package com.app.umma.domain.model.realtime

/**
 * USER final transcript 이후, AI response.create 직전에 적용할 이번 응답 전용 설정.
 *
 * 세션을 새로 열지 않고 현재 Realtime 세션 안에서만 사용한다.
 * `responseInstructions`는 response.create.instructions에 들어가고,
 * `outputAudioSpeed`는 실제 speed가 바뀌는 경우 session.update에 사용된다.
 */
data class ChatResponseOverride(
    // 이번 응답의 말투, 길이, 기준언어 사용, 질문 부담을 짧게 지시하는 response 전용 instruction.
    val responseInstructions: String?,
    // 이번 응답에서 필요한 AI 음성 속도. null이면 기존 세션 speed를 유지한다.
    val outputAudioSpeed: Double?,
    // 디버그 추적용 정책 요약. prompt 본문이나 사용자 발화 원문을 담지 않는다.
    val debugTrace: String? = null
)

/**
 * ChatRepository가 response.create 전에 호출하는 domain-owned provider 계약.
 *
 * Repository는 이 provider가 반환한 값을 적용만 하고, 사용자 발화 수준을 직접 판단하지 않는다.
 */
fun interface ChatResponseOverrideProvider {
    /**
     * @param userFinalTranscript 방금 확정된 USER final transcript
     * @return 이번 응답에만 적용할 override. null이면 세션 기본 설정을 그대로 사용한다.
     */
    suspend fun build(userFinalTranscript: String): ChatResponseOverride?
}
