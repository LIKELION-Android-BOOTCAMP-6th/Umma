package com.example.umma.data.source.remote

import com.example.umma.core.util.safeFirestoreCall
import com.example.umma.data.model.statistics.StatisticsHistoryDto
import com.example.umma.data.model.statistics.toDto
import com.example.umma.data.model.statistics.toFirestoreMap
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StatisticsHistory의 Firestore sync 경계를 감싼다.
 *
 * local-first 완료가 이미 성립한 history를 remote mirror로 올리고,
 * 실패하면 호출자는 local cache를 유지한 채 pending 으로 남길 수 있어야 한다.
 */
interface StatisticsHistoryRemoteDataSource {
    suspend fun fetchHistory(userId: String, language: LangCode): Result<List<StatisticsHistoryDto>>
    suspend fun syncHistory(history: StatisticsHistory): Result<Unit>
}

@Singleton
class FirestoreStatisticsHistoryRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : StatisticsHistoryRemoteDataSource {

    override suspend fun fetchHistory(
        userId: String,
        language: LangCode
    ): Result<List<StatisticsHistoryDto>> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        // refresh는 현재 로그인한 사용자와 같은 userId, language 범위만 읽는다.
        if (uid != userId) {
            return Result.failure(
                IllegalStateException("history userId does not match signed-in user")
            )
        }

        return safeFirestoreCall {
            val snapshot = firestore
                .collection("users")
                .document(uid)
                .collection("statistics_history")
                .whereEqualTo("language", language.code)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { it.toStatisticsHistoryDtoOrNull() }
                // Firestore path가 uid로 이미 분리되어 있어도, 문서 필드가 잘못 저장된 경우를 막는다.
                // 잘못된 userId를 local cache에 넣지 않는 것이 Statistics 화면의 사용자 경계와 맞다.
                .filter { it.userId == userId }
                .sortedBy { it.recordedAt }
        }
    }

    override suspend fun syncHistory(history: StatisticsHistory): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        // remote mirror 는 현재 로그인한 계정과 같은 userId 에 대해서만 허용한다.
        // 다른 uid 로 저장되면 local/user boundary 가 무너질 수 있으므로 바로 실패시킨다.
        if (uid != history.userId) {
            return Result.failure(
                IllegalStateException("history userId does not match signed-in user")
            )
        }

        return safeFirestoreCall {
            // domain model 을 Firestore 문서 필드명으로만 펼쳐서,
            // 저장 계층이 StatisticsHistory 자체를 직접 알지 않도록 유지한다.
            val dto: StatisticsHistoryDto = history.toDto()
            val collection = firestore
                .collection("users")
                .document(uid)
                .collection("statistics_history")

            collection.document(dto.id)
                .set(dto.copy(syncStatus = "SYNCED").toFirestoreMap())
                .await()
            }
    }
}

private fun DocumentSnapshot.toStatisticsHistoryDtoOrNull(): StatisticsHistoryDto? {
    // Firestore 필드가 하나라도 누락되면 refresh 결과로 쓰지 않는다.
    // 잘못된 row를 local cache에 섞어 넣는 것보다 보수적으로 버리는 쪽이 안전하다.
    val id = getString("id") ?: id
    val userId = getString("userId") ?: return null
    val language = getString("language") ?: return null
    val recordedAt = getLong("recordedAt") ?: return null
    val vocabularyLevel = getString("vocabularyLevel") ?: return null
    val grammarAccuracy = getDouble("grammarAccuracy") ?: return null
    val expressionRange = (get("expressionRange") as? Number)?.toInt() ?: return null
    val fluencyScore = getDouble("fluencyScore") ?: return null
    val naturalnessScore = getDouble("naturalnessScore") ?: return null
    val sourceEventId = getString("sourceEventId") ?: return null
    val syncStatus = getString("syncStatus") ?: return null

    return StatisticsHistoryDto(
        id = id,
        userId = userId,
        language = language,
        recordedAt = recordedAt,
        vocabularyLevel = vocabularyLevel,
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore,
        sourceEventId = sourceEventId,
        syncStatus = syncStatus
    )
}
