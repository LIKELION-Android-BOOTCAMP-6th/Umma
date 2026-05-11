package com.example.umma.domain.model

sealed interface AIEvent {
    // 실시간 자막(텍스트) 응답
    data class TextResponse(val text: String) : AIEvent

    // 실시간 음성(오디오) 응답
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

    // AI의 상태 변화 (예: "생각 중.", "듣는 중..")
    data class StateChanged(val state: AIState) : AIEvent

    // 오류 발생
    data class Error(val message: String) : AIEvent

}

enum class AIState {
    IDLE, LISTENING, THINKING, SPEAKING
}