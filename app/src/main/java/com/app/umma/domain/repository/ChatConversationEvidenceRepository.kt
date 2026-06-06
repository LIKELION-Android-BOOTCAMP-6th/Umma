package com.app.umma.domain.repository

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Chat conversation evidence를 읽는 저장소 계약.
 *
 * Chat 시작 시 읽기와 신고 세션 Gemini 분석 결과 저장을 제공한다.
 * 저장값은 band가 아니라 evidence이며, band 계산은 domain policy가 담당한다.
 */
interface ChatConversationEvidenceRepository {
    /**
     * 사용자/학습언어별 conversation evidence snapshot을 조회한다.
     *
     * 조회 실패는 Chat 시작을 막을 오류가 아니므로 caller가 fallback을 결정한다.
     */
    suspend fun getEvidence(selectedLang: LangCode): Result<ChatConversationEvidence?>

    /**
     * Gemini 또는 수동 분석으로 만든 conversation evidence를 사용자/학습언어별 snapshot으로 저장한다.
     *
     * 저장 실패는 신고/대화 흐름을 막지 않는 best-effort 작업으로 처리한다.
     */
    suspend fun saveEvidence(evidence: ChatConversationEvidence): Result<Unit>
}
