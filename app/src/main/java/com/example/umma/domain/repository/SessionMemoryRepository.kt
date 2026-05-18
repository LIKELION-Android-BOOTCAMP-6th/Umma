package com.example.umma.domain.repository

import com.example.umma.domain.model.learningstate.SessionMemoryCompressionInput
import com.example.umma.domain.model.learningstate.SessionMemoryCompressionResult

/**
 * Session Memory recentFullContext 압축 계약.
 *
 * Correction 완료 파이프라인은 세션 원문을 무한히 키우지 않고,
 * 교정이 끝난 시점에 최근 문맥을 압축하는 경계만 먼저 맞춘다.
 */
interface SessionMemoryRepository {

    suspend fun compressRecentFullContext(
        input: SessionMemoryCompressionInput
    ): Result<SessionMemoryCompressionResult>

    /**
     * 완료 파이프라인 중간 실패 시, 같은 입력으로 만든 압축 마커를 되돌린다.
     */
    suspend fun rollbackRecentFullContextCompression(
        input: SessionMemoryCompressionInput
    ): Result<Unit>
}
