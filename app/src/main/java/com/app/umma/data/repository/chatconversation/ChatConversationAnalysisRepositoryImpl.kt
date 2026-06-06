package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.repository.ChatConversationAnalysisRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini를 사용해 Chat conversation evidence를 생성하는 data layer 구현체입니다.
 *
 * prompt 생성, 외부 AI 호출, JSON 응답 매핑은 모두 data layer 책임으로 묶고, domain usecase에는
 * 분석 결과인 [ChatConversationEvidence]만 반환한다.
 */
@Singleton
class ChatConversationAnalysisRepositoryImpl @Inject constructor(
    private val promptBuilder: ChatConversationEvidencePromptBuilder,
    private val aiClient: ChatConversationEvidenceAiClient,
    private val responseMapper: ChatConversationEvidenceResponseMapper
) : ChatConversationAnalysisRepository {

    override suspend fun analyze(session: ChatConversationAnalysisSession): Result<ChatConversationEvidence> {
        return runCatching {
            // 확정 turn만 담긴 domain 입력을 Gemini JSON prompt로 변환한다.
            val prompt = promptBuilder.build(session)
            // 외부 호출 실패는 Result failure로 올려, 세션 종료 UI 흐름과 분리된 best-effort 처리를 가능하게 한다.
            val rawJson = aiClient.generateJson(prompt)
            // Gemini의 추천 band는 debug 필드에만 남기고 실제 band 계산은 domain policy가 담당한다.
            responseMapper.map(
                rawJson = rawJson,
                selectedLang = session.selectedLang,
                sourceSessionId = session.sessionId
            )
        }
    }
}
