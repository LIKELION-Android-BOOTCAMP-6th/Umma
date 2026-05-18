package com.example.umma.domain.model.realtime

import com.example.umma.domain.model.learningstate.TurnSpeaker

/**
 * 확정된 단일 발화 turn 을 표현하는 저장 모델입니다.
 *
 * @property turnId 중복 저장 방지를 위한 고유 식별자
 * @property sessionId 현재 turn 이 속한 세션 식별자
 * @property text 확정된 발화 텍스트
 * @property role 발화 주체
 * @property createdAt 발화 확정 시각
 * @property durationMs 발화 길이
 * @property tokenCount 토큰 수
 * @property confidence STT 신뢰도
 */
data class SessionTurn(
    val turnId: String,
    val sessionId: String,
    val text: String,
    val role: TurnSpeaker,
    val createdAt: Long,
    val durationMs: Long? = null,
    val tokenCount: Int? = null,
    val confidence: Double? = null
)
