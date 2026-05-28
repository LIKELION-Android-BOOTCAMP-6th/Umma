package com.app.umma.di

import com.app.umma.core.util.NetworkConnectivityMonitor
import com.app.umma.core.util.NetworkConnectivityMonitorImpl
import com.app.umma.data.repository.AuthRepositoryImpl
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
