package com.example.umma.data.source.remote

import com.example.umma.core.util.safeFirestoreCall
import com.example.umma.data.model.statistics.StatisticsHistoryDto
import com.example.umma.data.model.statistics.toDto
import com.example.umma.data.model.statistics.toFirestoreMap
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    suspend fun syncHistory(history: StatisticsHistory): Result<Unit>
}

@Singleton
class FirestoreStatisticsHistoryRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : StatisticsHistoryRemoteDataSource {

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
