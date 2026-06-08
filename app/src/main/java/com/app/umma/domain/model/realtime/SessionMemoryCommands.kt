package com.app.umma.domain.model.realtime

import com.app.umma.domain.model.learningstate.LangCode

/**
 * 확정된 turn 을 Session Memory 에 append 하기 위한 입력 모델입니다.
 *
 * @property language 현재 선택된 학습 언어
 * @property turn 저장할 확정 turn
 */
data class AppendTurnCommand(
    val language: LangCode,
    val turn: SessionTurn
)

/**
 * 교정 완료 직후 최근 세션 주제 요약 저장을 요청하기 위한 입력 모델입니다.
 *
 * @property language 요약 대상 학습 언어
 * @property requestedAt 요청 시각 (저장 기준 시각)
 */
data class SummarizeTopicsCommand(
    val language: LangCode,
    val requestedAt: Long
)

/**
 * 최근 세션 주제 요약 저장 결과입니다.
 *
 * @property applied 새 topicSummaries 가 실제로 저장되었는지 여부
 * @property displayTitle Dashboard 주제 칩에 표시할 최신 세션의 짧은 제목.
 *                        null 이면 기존 DashSummary.recentTopic 을 보존한다.
 * @property recentTopics AI 가 매핑한 최근 주제 라벨 목록(titles). COR-TUNE-010: 코드 단어빈도
 *                        `BuildSessionCompressionPayloadUseCase.extractRecentTopics` 의 AI 대체 출처다.
 *                        이 호출이 이미 단발 Gemini 응답에서 만들어 둔 값을 노출만 할 뿐, 새 AI 호출을 추가하지 않는다.
 * @property summaries AI 가 생성한 세션별 요약 목록. COR-TUNE-010: `topicSummaries` 이중 쓰기 정리의 SSOT 출처다.
 *                     [applied] 가 true 일 때만 의미 있는 값이 채워진다.
 */
data class TopicSummarySaveResult(
    val applied: Boolean,
    val displayTitle: String?,
    val recentTopics: List<String> = emptyList(),
    val summaries: List<String> = emptyList()
)

/**
 * Session Memory 원문 버퍼를 압축 결과로 교체하기 위한 입력 모델입니다.
 *
 * @property language 압축 대상 학습 언어
 * @property recentTopics 최근 주제 목록
 * @property topicSummaries 주제 요약 목록
 * @property topicKeySentences 핵심 문장 목록
 * @property compressedAt 압축 완료 시각
 */
data class CompressSessionMemoryCommand(
    val language: LangCode,
    val recentTopics: List<String>,
    val topicSummaries: List<String>,
    val topicKeySentences: List<String>,
    val compressedAt: Long
)
