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
 */
data class TopicSummarySaveResult(
    val applied: Boolean,
    val displayTitle: String?
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
