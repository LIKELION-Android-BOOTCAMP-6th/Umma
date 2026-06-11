package com.app.umma.domain.repository

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import kotlinx.coroutines.flow.Flow

/**
 * Session Memory 의 로컬/원격 저장 경계를 정의하는 Repository 계약입니다.
 */
interface SessionMemoryRepository {

    /**
     * 확정된 turn 을 Session Memory 에 append 합니다.
     *
     * @param command append 대상 turn 과 저장 스코프
     * @return 성공/실패 결과
     */
    suspend fun appendTurn(command: AppendTurnCommand): Result<Unit>

    /**
     * 특정 학습 언어의 recentFullContext 변경을 구독합니다.
     *
     * @param language 조회 대상 학습 언어
     * @return 확정 turn 목록 Flow
     */
    fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>>

    /**
     * 특정 학습 언어의 Session Memory 전체 스냅샷을 조회합니다.
     *
     * @param language 조회 대상 학습 언어
     * @return Session Memory 조회 결과
     */
    suspend fun getSessionMemory(language: LangCode): Result<SessionMemory>

    /**
     * 원문 버퍼를 압축 결과로 교체합니다.
     *
     * @param command 압축 대상 언어와 압축 결과
     * @return 성공/실패 결과
     */
    suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit>

    /**
     * 교정 흐름에서 사용할 후보 친화적인 recentFullContext 를 반환합니다.
     *
     * @param language 조회 대상 학습 언어
     * @return 교정용 turn 목록 Flow
     */
    fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>>

    /**
     * 플래시카드 흐름에서 사용할 후보 친화적인 recentFullContext 를 반환합니다.
     *
     * @param language 조회 대상 학습 언어
     * @return 플래시카드용 turn 목록 Flow
     */
    fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>>

    /**
     * 로컬에 남아 있는 미동기화 turn 을 원격으로 동기화합니다.
     *
     * @param language 동기화 대상 학습 언어
     * @return 성공/실패 결과
     */
    suspend fun syncPendingTurns(language: LangCode): Result<Unit>

    /**
     * 회원탈퇴 시 이 Repository 가 소유한 로컬 영속 데이터를 모두 비운다.
     * domain UseCase 가 Room 구현체를 알지 않도록 정리 계약을 추상화 경계에 둔다.
     * 실제 정리는 data 레이어 Impl 이 override 하며, fake/test 더블은 no-op 으로 둔다.
     */
    suspend fun clearLocal(): Result<Unit> = Result.success(Unit)

    /**
     * 최근 대화 세션(최대 5개)의 주제를 AI로 요약해 Session Memory 의 topicSummaries 에 저장합니다.
     *
     * SYS-LEARNING-STATE-INFRA Session Memory 필드 정의(topicSummaries: 교정 이후 남기는 주제별 압축 요약)에 근거한다.
     * 저장 대상 칼럼은 SessionMetadataEntity.topicSummariesJson (마이그레이션 없이 재사용).
     * 실패 시 기존 topicSummaries 는 변경하지 않으며 호출자(CompleteCorrectionUseCase)가 pending 으로만 처리한다. (#162-C)
     * displayTitle 은 Dashboard 표시 품질을 위한 파생값이며, 비어 있으면 recentTopic 갱신에 사용하지 않는다. (#173)
     *
     * @param command 요약 대상 언어와 요청 시각
     * @return 저장 적용 여부와 Dashboard 표시용 제목을 담은 결과
     */
    suspend fun summarizeAndSaveTopics(command: SummarizeTopicsCommand): Result<TopicSummarySaveResult>
}
