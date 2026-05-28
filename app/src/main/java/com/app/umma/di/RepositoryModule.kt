package com.app.umma.di

import com.app.umma.core.util.NetworkConnectivityMonitor
import com.app.umma.core.util.NetworkConnectivityMonitorImpl
import com.app.umma.data.repository.AuthRepositoryImpl
import com.app.umma.data.repository.ChatRepositoryImpl
import com.app.umma.data.repository.CorrectionRepositoryImpl
import com.app.umma.data.repository.SessionMemoryRepositoryImpl
import com.app.umma.data.repository.UserProfileRepositoryImpl
import com.app.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.app.umma.data.source.local.RoomCorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.app.umma.data.source.remote.FirestoreCorrectionFlashcardRemoteDataSource
import com.app.umma.data.source.remote.FirestoreStatisticsHistoryRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteDataSourceImpl
import com.app.umma.data.source.remote.StatisticsHistoryRemoteDataSource
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.CorrectionRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 인터페이스와 구현체를 연결(Binds)하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository


    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    // Correction 저장소 Real ↔ Fake 토글
    // 아래 두 파라미터 중 한 줄만 활성화한다.
    // 동시에 활성화하거나 같은 interface 에 @Binds 메서드를 추가하면 Dagger duplicate binding 으로 컴파일이 실패한다.
    //   - Real:  correctionRepositoryImpl: CorrectionRepositoryImpl (실제 Gemini 호출)
    //   - Fake:  fakeCorrectionRepository: FakeCorrectionRepository (화면/ViewModel 분기 검증용 토글 필드 제공)
    // COR-002-A 부터 Real 활성화. 후속 화면 백로그(COR-003~005)에서 Empty/Error/Save 검증이 필요해지면
    // Fake 로 한 줄 토글한 뒤 검증이 끝나면 다시 Real 로 원복한다.
    @Binds
    @Singleton
    abstract fun bindCorrectionRepository(
        correctionRepositoryImpl: CorrectionRepositoryImpl
//        fakeCorrectionRepository: FakeCorrectionRepository
    ): CorrectionRepository

    // LearningStateRepo 바인딩은 dev/mock variant module에서 처리한다.
    // mockDebug에서 Statistics overview 방어 상태를 재현해야 하므로 variant별 교체 경계로 분리한다.

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(
        networkConnectivityMonitorImpl: NetworkConnectivityMonitorImpl
    ): NetworkConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindCorrectionFlashcardLocalDataSource(
        impl: RoomCorrectionFlashcardLocalDataSource
    ): CorrectionFlashcardLocalDataSource

    @Binds
    @Singleton
    abstract fun bindCorrectionFlashcardRemoteDataSource(
        impl: FirestoreCorrectionFlashcardRemoteDataSource
    ): CorrectionFlashcardRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindLearningStateRemoteDataSource(
        impl: LearningStateRemoteDataSourceImpl
    ): LearningStateRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSessionMemoryRepository(
        impl: SessionMemoryRepositoryImpl
    ): SessionMemoryRepository

    // FlashcardRepository 바인딩은 dev/mock source set의 도메인별 module에서 처리한다.

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository

    // StatisticsRepository처럼 build variant에 따라 전환할 대상만 별도 source set에 둔다.

    @Binds
    @Singleton
    abstract fun bindStatisticsHistoryRemoteDataSource(
        impl: FirestoreStatisticsHistoryRemoteDataSource
    ): StatisticsHistoryRemoteDataSource
}
