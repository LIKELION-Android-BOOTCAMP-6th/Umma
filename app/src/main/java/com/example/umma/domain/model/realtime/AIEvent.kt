package com.example.umma.domain.model.realtime

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.TurnSpeaker

/**
 * AI 서버와의 실시간 통신 중에 발생하는 모든 이벤트를 정의하는 Sealed Interface입니다.
 */
sealed interface AIEvent {

    /** 세션 초기화가 시작됨을 알리는 이벤트입니다. */
    object Initializing : AIEvent

    /** 세션 연결이 성공적으로 완료되어 통신 준비가 되었음을 알리는 이벤트입니다. */
    data class Initialized(val sessionId: String) : AIEvent

    /** 실시간 음성 인식 중인 미확정 자막 데이터입니다. */
    data class PartialTranscription(val text: String?, val role: TurnSpeaker) : AIEvent

    /** 확정된(Final) 자막 데이터입니다.
     *  이 데이터를 기반으로 턴 저장 로직이 트리거됩니다.
     *  RT-003 정책에 의하여 turn commit 입력에 사용됨.
     *
     * @property turnId 중복 저장 방지를 위한 turn 식별자
     * @property sessionId 현재 turn 이 속한 세션 식별자
     * @property sessionLang 세션이 시작될 때 고정된 학습 언어
     * @property text 확정된 발화 텍스트
     * @property role 발화 주체
     * @property createdAt turn 확정 시각
     * @property durationMs 발화 길이
     * @property tokenCount 토큰 수
     * @property confidence STT 신뢰도
     *  */
    data class FinalTranscription(
        val turnId: String,
        val sessionId: String,
        val text: String,
        val sessionLang: LangCode,
        val role: TurnSpeaker,
        val createdAt: Long,
        val durationMs: Long? = null,
        val tokenCount: Int? = null,
        val confidence: Double? = null
    ) : AIEvent

    /** AI가 생성한 실시간 오디오(PCM) 데이터입니다. */
    data class AudioResponse(val audio: ByteArray) : AIEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioResponse

            return audio.contentEquals(other.audio)
        }

        override fun hashCode(): Int {
            return audio.contentHashCode()
        }
    }

    /** AI의 현재 동작 상태(듣기, 생각, 말하기 등) 변화를 알리는 이벤트입니다. */
    data class StateChanged(val state: AIState) : AIEvent

    /** AI가 시스템 또는 외부 오류로 인해 응답이 방해 받았음을 알리는 이벤트입니다. */
    data class SessionInterrupted(
        val message: String = "Live session interrupted"
    ) : AIEvent

    /** 세션 또는 스트리밍 중 발생한 오류를 알리는 이벤트입니다. */
    data class Error(val message: String) : AIEvent
}

/**
 * AI의 현재 실시간 상태를 나타내는 Enum 클래스입니다.
 */
enum class AIState {
    /** 기본 대기 상태 */
    IDLE,
    /** 사용자 음성을 수집/대기 중인 상태 */
    LISTENING,
    /** 서버에서 응답을 생성 중인 상태 */
    THINKING,
    /** 음성 응답을 출력 중인 상태 */
    SPEAKING,
    /** 연결 유실로 인해 재연결을 시도 중인 상태 */
    RECONNECTING,
    /** 오류 발생 상태 */
    ERROR
}