package com.app.umma.domain.model.chat

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker

/**
 * 세션 종료 후 Chat 대화 능력 근거를 분석하기 위한 최소 입력 모델입니다.
 *
 * 신고/리뷰 도구의 prompt trace나 report note와 분리해, 실제 대화에서 확정 저장된 turn만
 * Gemini 분석에 전달한다. 이 모델은 능력 측정 파이프라인의 입력이며 SessionMemory 원문 구조를
 * 외부 AI prompt builder에 직접 노출하지 않기 위한 경계 모델이다.
 */
data class ChatConversationAnalysisSession(
    // 분석 대상 앱 세션 ID.
    val sessionId: String,
    // 분석 대상 학습 언어.
    val selectedLang: LangCode,
    // 세션 안에서 확정 저장된 USER/AI final turn 목록.
    val turns: List<ChatConversationAnalysisTurn>
)

/**
 * Gemini 분석 prompt에 들어갈 단일 확정 turn입니다.
 */
data class ChatConversationAnalysisTurn(
    // USER/AI 역할만 분석에 필요하므로 provider 세부 role은 제거한다.
    val speaker: TurnSpeaker,
    // 확정 transcript 본문.
    val text: String,
    // 정렬 안정성을 위한 확정 시각.
    val createdAt: Long
)
