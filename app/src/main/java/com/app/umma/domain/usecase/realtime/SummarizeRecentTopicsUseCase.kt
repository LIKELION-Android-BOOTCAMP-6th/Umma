package com.app.umma.domain.usecase.realtime

import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * 최근 대화 세션의 주제를 AI 로 요약해 Session Memory 에 저장하는 UseCase 입니다.
 *
 * - AI 호출 실패 시 기존 topicSummaries 는 변경하지 않으며 호출자가 pending 으로만 처리한다. (#162-C)
 * - 성공 여부와 pending 여부는 [TopicSummaryResult] 로 반환한다.
 */
class SummarizeRecentTopicsUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    /**
     * 최근 대화 세션 주제를 요약해 저장합니다.
     *
     * @param command 요약 대상 언어와 요청 시각
     * @return 요약 결과 (applied, pending)
     */
    suspend operator fun invoke(command: SummarizeTopicsCommand): TopicSummaryResult {
        val result = repository.summarizeAndSaveTopics(command)
        return if (result.isSuccess) {
            val saveResult = result.getOrThrow()
            TopicSummaryResult(
                applied = saveResult.applied,
                pending = false,
                displayTitle = saveResult.displayTitle,
                recentTopics = saveResult.recentTopics,
                summaries = saveResult.summaries
            )
        } else {
            TopicSummaryResult(applied = false, pending = true, displayTitle = null)
        }
    }
}

/**
 * [SummarizeRecentTopicsUseCase] 실행 결과입니다.
 *
 * @property applied AI 요약이 실제로 저장되었으면 true
 * @property pending 저장에 실패해 재시도가 필요하면 true
 * @property displayTitle Dashboard 최근 대화 카드의 주제 칩에 표시할 짧은 제목.
 *                        null 이면 기존 recentTopic 을 보존한다.
 * @property recentTopics COR-TUNE-010: AI 가 매핑한 주제 라벨 목록(titles). `applied` 가 true 일 때만 채워진다.
 *                        `BuildSessionCompressionPayloadUseCase` 가 코드 단어빈도 대신 이 값을 우선 사용한다.
 * @property summaries COR-TUNE-010: AI 가 생성한 세션별 요약 목록. `topicSummaries` SSOT 의 출처이며,
 *                     `applied` 가 true 일 때만 채워진다. 비어 있으면 호출자가 코드 기반 폴백으로 진행한다.
 */
data class TopicSummaryResult(
    val applied: Boolean,
    val pending: Boolean,
    val displayTitle: String?,
    val recentTopics: List<String> = emptyList(),
    val summaries: List<String> = emptyList()
)
