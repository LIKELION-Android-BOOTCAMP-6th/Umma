package com.app.umma.devtools.chatpromptreview

import javax.inject.Inject

/**
 * Chat 화면의 개발용 신고 버튼에서 현재 세션을 프롬프트 리뷰 대상으로 표시합니다.
 *
 * 이 UseCase는 운영 domain usecase가 아니라 devtools 경계에 둡니다.
 * 프롬프트 튜닝 도구 제거 시 presentation 연결부와 함께 쉽게 제거할 수 있게 하기 위함입니다.
 */
class ReportPromptReviewSessionUseCase @Inject constructor(
    private val reporter: ChatPromptReviewSessionReporter
) {
    suspend operator fun invoke(): Result<Unit> {
        return reporter.reportCurrentSession()
    }
}
