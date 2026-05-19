package com.example.umma.di

import com.example.umma.core.util.NetworkConnectivityMonitor
import com.example.umma.core.util.NetworkConnectivityMonitorImpl
import com.example.umma.data.repository.AuthRepositoryImpl
import com.example.umma.data.repository.CorrectionRepositoryImpl
import com.example.umma.data.repository.ChatRepositoryImpl
import com.example.umma.data.repository.LearningStateRepoImpl
import com.example.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.example.umma.data.source.local.InMemoryCorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.data.source.remote.FirestoreCorrectionFlashcardRemoteDataSource
import com.example.umma.data.repository.UserProfileRepositoryImpl
import com.example.umma.data.source.remote.LearningStateRemoteDataSource
import com.example.umma.data.source.remote.LearningStateRemoteDataSourceImpl
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.repository.UserProfileRepository
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

    // Correction 저장소는 화면 검증 시 Fake 로 토글할 수 있다.
    // 필요하면 아래 파라미터를 CorrectionRepositoryImpl → FakeCorrectionRepository 로 바꾼다.
    @Binds
    @Singleton
    abstract fun bindCorrectionRepository(
        correctionRepositoryImpl: CorrectionRepositoryImpl
//        fakeCorrectionRepository: FakeCorrectionRepository
    ): CorrectionRepository

    // 학습 상태 저장소는 DataStore 기반 구현체를 domain 계약 뒤에 숨긴다.
    // 화면 검증 시 Fake 로 토글: USER_FLOW_MOCK_REAL_DATA_GUIDE.md §5.2 참조.
    @Binds
    @Singleton
    abstract fun bindLearningStateRepo(
        learningStateRepoImpl: LearningStateRepoImpl
//        fakeLearningStateRepo: FakeLearningStateRepo
    ): LearningStateRepo

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(
        networkConnectivityMonitorImpl: NetworkConnectivityMonitorImpl
    ): NetworkConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindCorrectionFlashcardLocalDataSource(
        impl: InMemoryCorrectionFlashcardLocalDataSource
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
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository
}
