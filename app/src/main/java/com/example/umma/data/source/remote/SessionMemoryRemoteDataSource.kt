package com.example.umma.data.source.remote

import com.example.umma.domain.model.realtime.SessionMemory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Session Memory Firestore 동기화 경계를 담당하는 RemoteDataSource 입니다.
 */
class SessionMemoryRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * Session Memory 전체 snapshot 을 Firestore 에 반영합니다.
     *
     * 정책:
     * - remote 는 local mirror 역할을 합니다.
     * - recentFullContext 는 arrayUnion 으로 누적 append 하지 않고,
     *   로컬 bounded snapshot 전체를 그대로 반영합니다.
     *
     * @param memory 현재 로컬 Session Memory 스냅샷
     * @return 성공/실패 결과
     */
    suspend fun syncSessionMemorySnapshot(
        memory: SessionMemory
    ): Result<Unit> {
        return try {
            val docRef = firestore
                .collection("users")
                .document(memory.userId)
                .collection("sessions")
                .document(memory.language.code)

            val turnsMapList = memory.recentFullContext.map { turn ->
                mapOf(
                    "turnId" to turn.turnId,
                    "sessionId" to turn.sessionId,
                    "text" to turn.text,
                    "role" to turn.role.name,
                    "createdAt" to turn.createdAt,
                    "durationMs" to turn.durationMs,
                    "tokenCount" to turn.tokenCount,
                    "confidence" to turn.confidence
                )
            }

            docRef.set(
                mapOf(
                    "language" to memory.language.code,
                    "recentFullContext" to turnsMapList,
                    "recentTopics" to memory.recentTopics,
                    "topicKeySentences" to memory.topicKeySentences,
                    "topicSummaries" to memory.topicSummaries,
                    "correctionAvailable" to memory.correctionAvailable,
                    "lastCompressedAt" to memory.lastCompressedAt,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
