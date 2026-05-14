package com.example.umma.domain.model.realtime

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

    /** 확정된(Final) 자막 데이터입니다. 이 데이터를 기반으로 턴 저장 로직이 트리거됩니다. */
    data class FinalTranscription(val text: String, val role: TurnSpeaker) : AIEvent

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