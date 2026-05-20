package com.example.umma.domain.usecase.realtime

/**
 * 세션 메모리와 관련된 모든 UseCase를 하나의 객체로 묶어 제공하는 래퍼 클래스입니다.
 * ViewModel에서 단일 파라미터로 주입받아 간결하게 사용할 수 있습니다.
 */
data class SessionMemoryUseCases(
    val appendTurn: AppendTurnUseCase,
    val compressSessionMemory: CompressSessionMemoryUseCase,
    val observeRecentFullContext: ObserveRecentFullContextUseCase,
    val getSessionMemory: GetSessionMemoryUseCase,
    val getCorrectionContext: GetCorrectionContextUseCase,
    val getFlashcardContext: GetFlashcardContextUseCase,
    val syncPendingTurns: SyncPendingTurnsUseCase
)
