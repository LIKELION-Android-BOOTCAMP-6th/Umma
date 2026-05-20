package com.example.umma.data.repository.correction

import com.example.umma.data.model.correction.toCorrectionFlashcardDto
import com.example.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import javax.inject.Inject

/**
 * Correction Flashcard의 local-first 저장 순서를 담당하는 data 계층 store입니다.
 *
 * RepositoryImpl 안에 local/remote 저장 순서를 직접 넣지 않고 이 store로 분리해 둔다.
 * 나중에 in-memory local 구현이 Room DAO로 바뀌어도 domain 계약과 UseCase는 흔들리지 않는다.
 */
class CorrectionFlashcardStore @Inject constructor(
    private val localDataSource: CorrectionFlashcardLocalDataSource,
    private val remoteDataSource: CorrectionFlashcardRemoteDataSource
) {

    suspend fun save(request: CorrectionSaveRequest): CorrectionSaveResult {
        val flashcardDtos = request.flashcards.map { item ->
            item.toCorrectionFlashcardDto(
                lang = request.lang,
                requestedAt = request.requestedAt
            )
        }

        // 1) 사용자 완료 기준은 local save다. 이 단계가 실패하면 완료 파이프라인도 실패해야 한다.
        val localSavedIds = localDataSource.saveFlashcards(flashcardDtos)

        // 2) 중복 요청으로 새로 저장된 카드가 없으면 remote sync도 새로 예약하지 않는다.
        val newlySavedFlashcards = flashcardDtos.filter { it.id in localSavedIds }
        if (newlySavedFlashcards.isEmpty()) {
            return CorrectionSaveResult(
                localSavedFlashcardIds = emptyList(),
                pendingSyncFlashcardIds = emptyList(),
                savedAt = request.requestedAt
            )
        }

        // 3) Firestore sync는 local save 이후의 후속 단계다. 실패해도 local 완료를 실패로 바꾸지 않는다.
        val syncedIds = remoteDataSource.syncFlashcards(newlySavedFlashcards)
            .getOrElse { emptyList() }
            .toSet()
        val pendingSyncIds = localSavedIds.filterNot { it in syncedIds }

        return CorrectionSaveResult(
            localSavedFlashcardIds = localSavedIds,
            pendingSyncFlashcardIds = pendingSyncIds,
            savedAt = request.requestedAt
        )
    }

    suspend fun rollback(request: CorrectionSaveRequest) {
        val flashcardIds = request.flashcards.map { it.suggestionId }

        // local rollback은 완료 파이프라인의 부분 완료 상태를 없애기 위한 필수 보상 작업이다.
        localDataSource.rollbackFlashcards(flashcardIds)

        // remote delete는 best-effort다. 이미 Firestore sync가 성공했을 수 있으므로 정리만 시도한다.
        remoteDataSource.deleteFlashcards(flashcardIds)
    }
}
