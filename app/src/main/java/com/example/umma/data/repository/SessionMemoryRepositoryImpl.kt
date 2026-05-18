package com.example.umma.data.repository

import com.example.umma.domain.model.learningstate.SessionMemoryCompressionInput
import com.example.umma.domain.model.learningstate.SessionMemoryCompressionResult
import com.example.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session Memory 압축 계약의 기본 구현체.
 *
 * 아직 실제 RT-003 저장소가 완성되지 않았으므로,
 * 현재 단계에서는 압축 요청이 어디로 흘러가야 하는지와 idempotent 경계를 먼저 고정한다.
 */
@Singleton
class SessionMemoryRepositoryImpl @Inject constructor() : SessionMemoryRepository {

    private val compressedAtByKey = mutableMapOf<String, Long>()

    override suspend fun compressRecentFullContext(
        input: SessionMemoryCompressionInput
    ): Result<SessionMemoryCompressionResult> {
        return runCatching {
            require(input.uid.isNotBlank()) { "uid must not be blank" }
            require(input.sessionMemoryKey.isNotBlank()) { "sessionMemoryKey must not be blank" }

            val compressedAt = compressedAtByKey.getOrPut(input.storageKey()) {
                input.requestedAt
            }

            SessionMemoryCompressionResult(
                sessionMemoryKey = input.sessionMemoryKey,
                compressedAt = compressedAt,
                recentFullContextCompacted = true
            )
        }
    }

    override suspend fun rollbackRecentFullContextCompression(
        input: SessionMemoryCompressionInput
    ): Result<Unit> {
        return runCatching {
            // 이번 완료 요청이 새로 만든 압축 마커만 되돌리고, 이전에 있던 압축 상태는 건드리지 않는다.
            compressedAtByKey.remove(input.storageKey(), input.requestedAt)
        }
    }

    private fun SessionMemoryCompressionInput.storageKey(): String {
        return "$uid:${lang.code}:$sessionMemoryKey"
    }
}
