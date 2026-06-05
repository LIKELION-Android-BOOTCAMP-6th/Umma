package com.app.umma.devtools.chatpromptreview

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker

/**
 * 개발용 프롬프트 리뷰 자료수집 이벤트입니다.
 *
 * 운영 SessionMemory, usage, correction 저장 모델과 분리하기 위해 devtools 패키지에 둡니다.
 * 이 모델은 프롬프트 튜닝 분석용 원천 자료이며 사용자 기능 판단에 사용하지 않습니다.
 */
data class ChatPromptReviewEvent(
    // 세션 내 시간순 정렬에 쓰는 안정적인 이벤트 식별자입니다.
    val eventId: String,
    // 앱 대화 세션 ID입니다. OpenAI server session id가 아니라 앱 내부 기준을 사용합니다.
    val sessionId: String,
    // 프롬프트 리뷰 문서에서 이벤트 종류를 구분하기 위한 값입니다.
    val type: ChatPromptReviewEventType,
    // 현재 학습 대화 언어입니다.
    val language: LangCode,
    // 이벤트가 발생한 시각입니다.
    val createdAt: Long,
    // USER/AI final turn 이벤트일 때만 채웁니다.
    val role: TurnSpeaker? = null,
    // USER/AI final turn의 turnId입니다.
    val turnId: String? = null,
    // 실제 대화 turn 텍스트 또는 override 계산 기준 USER final transcript입니다.
    val text: String? = null,
    // Logcat AiChatPromptTrace와 같은 요약 문자열입니다. prompt 전문은 저장하지 않습니다.
    val debugTrace: String? = null,
    // response.create.instructions가 실제로 들어갔는지 분석 문서에서 바로 보기 위한 플래그입니다.
    val hasInstructions: Boolean? = null,
    // 이번 turn에서 계산된 audio speed입니다.
    val outputAudioSpeed: Double? = null,
    // 세션 시작/재연결 구분처럼 문서 분석에 필요한 짧은 부가 정보입니다.
    val metadata: String? = null
)

/**
 * 프롬프트 리뷰 이벤트 종류입니다.
 */
enum class ChatPromptReviewEventType {
    // USER final transcript 이후 response.create 직전에 계산된 turn override trace입니다.
    TurnOverrideTrace,
    // SessionMemory에 저장되는 것과 같은 USER/AI final transcript mirror입니다.
    FinalTurn
}
