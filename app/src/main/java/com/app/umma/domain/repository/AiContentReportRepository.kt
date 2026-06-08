package com.app.umma.domain.repository

import com.app.umma.domain.model.chat.AiContentReport

/**
 * 운영용 AI 생성 콘텐츠 신고 저장소 계약입니다.
 *
 * Chat 화면은 신고 UI만 담당하고, Firestore 컬렉션명과 저장 포맷은 data repository가 숨깁니다.
 */
interface AiContentReportRepository {
    /**
     * AI 콘텐츠 신고를 저장합니다.
     *
     * 저장 실패는 Chat 세션 실패가 아니므로 호출자는 신고 UI 상태에만 반영해야 합니다.
     */
    suspend fun submitReport(report: AiContentReport): Result<Unit>
}
