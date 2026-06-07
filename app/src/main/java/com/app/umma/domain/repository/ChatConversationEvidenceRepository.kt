package com.app.umma.domain.repository

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Chat conversation evidence를 읽는 저장소 계약.
 *
 * 007 이후 앱의 Chat band 계산은 LangState.analysisMeta.chatEvidenceSummary를 공식 source로 사용한다.
 * 이 repository는 Firestore debug/review snapshot을 저장하거나 사람이 확인할 때 읽기 위한 보조 계약이다.
 */
interface ChatConversationEvidenceRepository {
    /**
     * 사용자/학습언어별 conversation evidence snapshot을 조회한다.
     *
     * snapshot은 공식 세션 시작 입력이 아니므로 조회 실패가 사용자 대화 흐름으로 전파되면 안 된다.
     */
    suspend fun getEvidence(selectedLang: LangCode): Result<ChatConversationEvidence?>

    /**
     * Gemini 또는 수동 분석으로 만든 conversation evidence를 사용자/학습언어별 snapshot으로 저장한다.
     *
     * 저장 실패는 신고/대화 흐름을 막지 않는 best-effort 작업으로 처리한다.
     */
    suspend fun saveEvidence(evidence: ChatConversationEvidence): Result<Unit>
}
