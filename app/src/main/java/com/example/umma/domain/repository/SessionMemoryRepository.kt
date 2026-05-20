package com.example.umma.domain.repository

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.realtime.AppendTurnCommand
import com.example.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.example.umma.domain.model.realtime.SessionMemory
import com.example.umma.domain.model.realtime.SessionTurn
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
}
