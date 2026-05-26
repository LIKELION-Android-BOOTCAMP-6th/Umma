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
}
