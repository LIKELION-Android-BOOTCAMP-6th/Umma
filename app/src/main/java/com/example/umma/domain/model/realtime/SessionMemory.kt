package com.example.umma.domain.model.realtime

import com.example.umma.domain.model.learningstate.LangCode


/**
 * 특정 학습 언어별 대화 원문 맥락과 AI 압축 메타데이터를 통합 보관하는 세션 메모리 도메인 모델입니다.
 *
 * @property userId 사용자 고유 식별 UID
 * @property language 현재 세션의 학습 대상 외국어 코드
 * @property recentFullContext 교정 및 분석 대상이 되는 확정 완료된 최근 턴 목록 (원문 버퍼)
 * @property recentTopics 최근 대화 주제 목록
 * @property topicSummaries 압축 후 남긴 요약 목록
 * @property topicKeySentences 대화 히스토리에서 추천되는 학습용 핵심 문장 목록
 * @property correctionAvailable 교정 대기 중인 유효 사용자 발화가 존재하는지 여부 (유저 턴 append 시 true, 압축 시 false)
 * @property lastCompressedAt 최근에 원문 버퍼를 요약으로 교체/압축한 Unix 타임스탬프 (ms, 최초 설정 전에는 null)
 * @property updatedAt 세션 정보가 최근에 수정된 Unix 타임스탬프 (ms)
 * @property isPendingTurnSync append 결과의 firestore sync 여부
 * @property isPendingCompressionSync compress 결과의 firestore sync 대기 여부
 */
data class SessionMemory(
    val userId: String,
    val language: LangCode,
    val recentFullContext: List<SessionTurn>,
    val recentTopics: List<String> = emptyList(),
    val topicSummaries: List<String> = emptyList(),
    val topicKeySentences: List<String> = emptyList(),
    val correctionAvailable: Boolean = false,
    val lastCompressedAt: Long? = null,
    val updatedAt: Long,
    val isPendingTurnSync: Boolean = false,
    val isPendingCompressionSync: Boolean = false
)