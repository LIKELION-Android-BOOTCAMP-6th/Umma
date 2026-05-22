package com.example.umma.data.source.local

import com.example.umma.data.model.correction.CorrectionFlashcardDto

/**
 * JVM unit test에서 Room 없이 local-first 저장 계약만 검증하기 위한 테스트 대역입니다.
 *
 * production은 RoomCorrectionFlashcardLocalDataSource를 사용하고,
 * 이 클래스는 저장 순서, 중복 방지, rollback, dirty 해제, SRS due 조회/갱신 정책을 빠르게 검증할 때만 쓴다.
 */
class TestCorrectionFlashcardLocalDataSource : CorrectionFlashcardLocalDataSource {

    private val flashcardsByUserAndId = linkedMapOf<String, CorrectionFlashcardDto>()
    val syncedIds = mutableListOf<String>()

    override suspend fun saveFlashcards(
        uid: String,
        flashcards: List<CorrectionFlashcardDto>
    ): List<String> {
        // Room insert IGNORE가 새로 insert된 row만 알려주는 흐름을 메모리 map으로 재현한다.
        // 테스트는 이 반환값을 이용해 remote sync/rollback 대상이 좁혀지는지 확인한다.
        val savedIds = mutableListOf<String>()

        flashcards.forEach { flashcard ->
            val key = key(uid, flashcard.id)
            // production DAO의 userId + id 복합 키와 같은 중복 방지 정책을 흉내 낸다.
            if (!flashcardsByUserAndId.containsKey(key)) {
                flashcardsByUserAndId[key] = flashcard
                savedIds += flashcard.id
            }
        }

        return savedIds
    }

    override suspend fun markSynced(
        uid: String,
        flashcardIds: List<String>
    ) {
        // 실제 Room 구현처럼 dirty=false를 반영해 최초 저장과 review 갱신의 sync 상태를 모두 검증할 수 있게 한다.
        syncedIds += flashcardIds
        flashcardIds.forEach { flashcardId ->
            val key = key(uid, flashcardId)
            flashcardsByUserAndId[key] = flashcardsByUserAndId[key]?.copy(dirty = false)
                ?: return@forEach
        }
    }

    override suspend fun getDueFlashcards(
        uid: String,
        language: String,
        now: Long,
        limit: Int
    ): List<CorrectionFlashcardDto> {
        if (limit <= 0) return emptyList()

        // Room DAO의 userId/language/nextReviewAt 조건과 정렬 기준을 같은 방식으로 재현한다.
        return flashcardsByUserAndId.entries
            .asSequence()
            .filter { (key, flashcard) ->
                belongsToUser(key, uid) &&
                    flashcard.language == language &&
                    flashcard.nextReviewAt <= now
            }
            .map { it.value }
            .sortedWith(
                compareBy<CorrectionFlashcardDto> { it.nextReviewAt }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            .take(limit)
            .toList()
    }

    override suspend fun updateReviewSchedule(
        uid: String,
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Boolean {
        val key = key(uid, flashcardId)
        val current = flashcardsByUserAndId[key] ?: return false

        // SRS review 결과가 반영된 카드는 remote sync 전까지 다시 dirty 상태가 된다.
        flashcardsByUserAndId[key] = current.copy(
            nextReviewAt = nextReviewAt,
            interval = interval,
            easeFactor = easeFactor,
            updatedAt = updatedAt,
            dirty = true
        )
        return true
    }

    override suspend fun countFlashcards(
        uid: String,
        language: String
    ): Int {
        // Summary의 saved count도 production과 같이 userId/language 조건으로 계산한다.
        return flashcardsByUserAndId.entries.count { (key, flashcard) ->
            belongsToUser(key, uid) && flashcard.language == language
        }
    }

    override suspend fun countDueFlashcards(
        uid: String,
        language: String,
        now: Long
    ): Int {
        // due count는 getDueFlashcards와 같은 조건을 사용하되 limit 없이 전체 수를 센다.
        return flashcardsByUserAndId.entries.count { (key, flashcard) ->
            belongsToUser(key, uid) &&
                flashcard.language == language &&
                flashcard.nextReviewAt <= now
        }
    }

    override suspend fun rollbackFlashcards(
        uid: String,
        flashcardIds: List<String>
    ) {
        // rollback도 userId + id 기준으로 삭제해 계정 간 카드가 섞이지 않는 계약을 유지한다.
        flashcardIds.forEach { flashcardId ->
            flashcardsByUserAndId.remove(key(uid, flashcardId))
        }
    }

    private fun key(uid: String, flashcardId: String): String {
        return "$uid:$flashcardId"
    }

    private fun belongsToUser(key: String, uid: String): Boolean {
        // production의 복합 키(userId + id) 범위를 테스트 대역에서도 명시적으로 유지한다.
        return key.startsWith("$uid:")
    }
}
