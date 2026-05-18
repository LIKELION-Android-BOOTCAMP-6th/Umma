package com.example.umma.data.source.remote

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.realtime.SessionTurn
import com.google.firebase.firestore.FieldValue
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
     * 미동기화 turn 목록을 Firestore recentFullContext 배열에 추가합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @param pendingTurns 동기화할 turn 목록
     * @return 성공/실패 결과
     */
    suspend fun syncTurnsToFirestore(
        userId: String,
        language: LangCode,
        pendingTurns: List<SessionTurn>
    ): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId)
                .collection("sessions").document(language.code)

            val turnsMapList = pendingTurns.map { turn ->
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
                    "recentFullContext" to FieldValue.arrayUnion(*turnsMapList.toTypedArray()),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Session Memory 압축 결과를 Firestore 에 반영합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @param recentTopics 최근 주제 목록
     * @param topicSummaries 주제 요약 목록
     * @param topicKeySentences 핵심 문장 목록
     * @param lastCompressedAt 압축 완료 시각
     * @return 성공/실패 결과
     */
    suspend fun compressSessionInFirestore(
        userId: String,
        language: LangCode,
        recentTopics: List<String>,
        topicSummaries: List<String>,
        topicKeySentences: List<String>,
        lastCompressedAt: Long
    ): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId)
                .collection("sessions").document(language.code)

            docRef.set(
                mapOf(
                    "recentFullContext" to emptyList<Any>(),
                    "recentTopics" to recentTopics,
                    "topicSummaries" to topicSummaries,
                    "topicKeySentences" to topicKeySentences,
                    "correctionAvailable" to false,
                    "lastCompressedAt" to lastCompressedAt,
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
