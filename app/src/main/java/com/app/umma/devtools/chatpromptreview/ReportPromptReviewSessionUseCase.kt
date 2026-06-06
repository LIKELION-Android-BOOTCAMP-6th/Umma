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
    suspend operator fun invoke(reportNote: String? = null): Result<Unit> {
        // reportNote는 테스터가 불편을 느낀 상황을 사람이 해석하기 위한 보조 메모다.
        // 운영 저장 모델과 분리된 devtools 경계에서만 전달한다.
        return reporter.reportCurrentSession(reportNote = reportNote)
    }
}
