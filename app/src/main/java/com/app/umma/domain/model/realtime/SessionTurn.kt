package com.app.umma.domain.model.realtime

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker

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
 * @property detectedLang 발화 원문 언어 태그(COR-TUNE-011 seam). 캡처 로직/Room 컬럼·마이그레이션은
 *   `docs/handover/COR-TUNE-011_TURN_DETECTED_LANG_CAPTURE_HANDOVER.md` 에 따라 CHAT/Realtime(RT-003) 소관이다.
 *   이 필드는 교정 평가 게이트가 소비할 입력 계약만 미리 열어 두며, 캡처가 머지되기 전까지는 항상 null
 *   이므로 게이트는 "언어 불명 → 종전과 동일 통과"(no-op)로 동작한다.
 */
data class SessionTurn(
    val turnId: String,
    val sessionId: String,
    val text: String,
    val role: TurnSpeaker,
    val createdAt: Long,
    val durationMs: Long? = null,
    val tokenCount: Int? = null,
    val confidence: Double? = null,
    val detectedLang: LangCode? = null
)
