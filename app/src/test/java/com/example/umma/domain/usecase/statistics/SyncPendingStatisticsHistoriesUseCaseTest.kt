package com.example.umma.domain.usecase.statistics

import com.example.umma.data.repository.fake.FakeStatisticsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPendingStatisticsHistoriesUseCaseTest {

    @Test
    fun `usecase returns synced pending count`() = runBlocking {
        // UseCase는 repository의 local -> remote pending retry 계약을 얇게 위임한다.
        // 화면 계층은 이 count만 보고 로그나 보조 상태를 판단할 수 있다.
        val repository = FakeStatisticsRepository()
        val useCase = SyncPendingStatisticsHistoriesUseCase(repository)

        val result = useCase("user-1").getOrThrow()

        // 기본 fake seed에서 user-1의 PENDING history만 성공 처리된다.
        assertEquals(6, result)
    }

    @Test
    fun `usecase forwards retry failure without consuming pending rows`() = runBlocking {
        // retry 실패는 local history 삭제나 화면 실패가 아니라,
        // 다음 진입 때 다시 시도할 수 있는 실패 결과로 전달된다.
        val repository = FakeStatisticsRepository().apply {
            setPendingSyncFailure(IllegalStateException("network down"))
        }
        val useCase = SyncPendingStatisticsHistoriesUseCase(repository)

        val result = useCase("user-1")

        assertTrue(result.isFailure)
    }
}
