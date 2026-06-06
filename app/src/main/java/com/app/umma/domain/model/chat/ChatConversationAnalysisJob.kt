package com.app.umma.domain.model.chat

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker

/**
 * Chat 세션 종료 후 Gemini 분석을 재시도하기 위해 로컬에 남기는 최소 job.
 *
 * 대화 종료 직후의 분석 대상 turn snapshot을 함께 보관한다.
 * 이렇게 해야 Correction이 SessionMemory recentFullContext를 압축/초기화해도 재시도할 수 있다.
 */
data class ChatConversationAnalysisJob(
    // 계정 전환 시 다른 사용자의 pending job을 실행하지 않기 위한 경계값.
    val userId: String,
    // SessionMemory와 LangState update 대상 언어.
    val selectedLang: LangCode,
    // 분석 대상이 되는 Realtime chat session id.
    val sessionId: String,
    // 예약 당시 저장됐다고 판단한 final turn 수. 디버깅과 중복 예약 판단 보조값이다.
    val finalTurnCount: Int,
    // Gemini 분석과 LangState update 입력을 재구성하기 위한 불변 turn snapshot.
    val turns: List<ChatConversationAnalysisJobTurn>,
    // job을 처음 만든 시각.
    val createdAt: Long,
    // 마지막 시도 시각. 실패 후 다음 실행에서 재시도 여부를 추적하기 위한 값이다.
    val lastAttemptedAt: Long? = null,
    // 실패 재시도 횟수. 1차 구현에서는 차단에 쓰지 않고 관찰용으로만 유지한다.
    val attemptCount: Int = 0
) {
    val key: String = key(userId = userId, selectedLang = selectedLang, sessionId = sessionId)

    companion object {
        fun key(userId: String, selectedLang: LangCode, sessionId: String): String {
            return "${userId.trim()}|${selectedLang.code}|${sessionId.trim()}"
        }
    }
}

/**
 * Pending analysis job 안에 보존하는 확정 turn snapshot.
 */
data class ChatConversationAnalysisJobTurn(
    // USER/AI 역할.
    val speaker: TurnSpeaker,
    // 확정 transcript 본문.
    val text: String,
    // 정렬 안정성을 위한 확정 시각.
    val createdAt: Long,
    // LangState update 보조값. 없으면 retry 시 null로 유지한다.
    val tokenCount: Int? = null,
    val durationMs: Long? = null,
    val confidence: Double? = null
)
