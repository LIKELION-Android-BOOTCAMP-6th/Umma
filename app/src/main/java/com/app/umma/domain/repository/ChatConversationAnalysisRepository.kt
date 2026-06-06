package com.app.umma.domain.repository

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationEvidence

/**
 * Chat 세션 종료 후 대화 능력 근거를 외부 분석기로 생성하는 repository 계약입니다.
 *
 * Domain usecase는 어떤 AI provider를 쓰는지 알지 않고, data layer 구현체가 Gemini prompt 생성,
 * JSON 호출, 응답 매핑을 담당한다.
 */
interface ChatConversationAnalysisRepository {
    /**
     * 확정 저장된 한 세션의 turn 목록을 분석해 conversation evidence를 생성합니다.
     */
    suspend fun analyze(session: ChatConversationAnalysisSession): Result<ChatConversationEvidence>
}
