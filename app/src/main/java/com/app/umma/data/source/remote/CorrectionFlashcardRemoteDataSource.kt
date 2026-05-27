package com.app.umma.data.source.remote

import com.app.umma.core.util.safeFirestoreCall
import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction에서 최초 저장한 Flashcard를 Firestore에 동기화하는 remote data source입니다.
 *
 * 로컬 저장 성공이 사용자 완료 기준이고, 이 remote sync는 후속 보정 단계다.
 * 따라서 호출자는 실패를 local save 실패로 다루지 않고 pending sync로 남긴다.
 */
interface CorrectionFlashcardRemoteDataSource {

    suspend fun syncFlashcards(flashcards: List<CorrectionFlashcardDto>): Result<List<String>>

    suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit>

    /**
     * 복습 평가 결과(nextReviewAt / interval / easeFactor)를 Firestore에 업데이트한다.
     * 카드 내용은 건드리지 않고 스케줄 값만 바꾼다.
     * 실패하면 로컬에 pending 상태로 남겨 나중에 재시도할 수 있게 한다.
     */
    suspend fun syncReviewSchedule(
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Result<Unit>
}

@Singleton
class FirestoreCorrectionFlashcardRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : CorrectionFlashcardRemoteDataSource {

    override suspend fun syncFlashcards(
        flashcards: List<CorrectionFlashcardDto>
    ): Result<List<String>> {
        if (flashcards.isEmpty()) return Result.success(emptyList())

        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        return safeFirestoreCall {
            val batch = firestore.batch()
            val collection = firestore
                .collection("users")
                .document(uid)
                .collection("flashcards")

            flashcards.forEach { flashcard ->
                // Firestore에는 sync 완료 상태로 저장한다. local cache의 dirty 상태는 sync 결과로 판단한다.
                val remoteDocument = flashcard.copy(dirty = false).toFirestoreMap()
                batch.set(collection.document(flashcard.id), remoteDocument)
            }

            batch.commit().await()
            flashcards.map { it.id }
        }
    }

    override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> {
        if (flashcardIds.isEmpty()) return Result.success(Unit)

        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        return safeFirestoreCall {
            val batch = firestore.batch()
            val collection = firestore
                .collection("users")
                .document(uid)
                .collection("flashcards")

            flashcardIds.forEach { flashcardId ->
                batch.delete(collection.document(flashcardId))
            }

            batch.commit().await()
        }
    }

    override suspend fun syncReviewSchedule(
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        return safeFirestoreCall {
            // update()는 문서가 없으면 실패한다.
            // 실패 시 Repository에서 isSyncPending=true로 처리해 나중에 재시도한다.
            firestore
                .collection("users")
                .document(uid)
                .collection("flashcards")
                .document(flashcardId)
                .update(
                    mapOf(
                        "nextReviewAt" to nextReviewAt,
                        "interval" to interval,
                        "easeFactor" to easeFactor,
                        "updatedAt" to updatedAt,
                        "dirty" to false
                    )
                )
                .await()
        }
    }
}
