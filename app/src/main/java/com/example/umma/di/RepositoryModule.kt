package com.example.umma.di

// TODO(COR-001-A): 화면 시각 검증 후 Fake import 제거.
import com.example.umma.core.util.NetworkConnectivityMonitor
import com.example.umma.core.util.NetworkConnectivityMonitorImpl
import com.example.umma.data.repository.AuthRepositoryImpl
import com.example.umma.data.repository.ChatRepositoryImpl
import com.example.umma.data.repository.CorrectionRepositoryImpl
import com.example.umma.data.repository.FlashcardRepositoryImpl
import com.example.umma.data.repository.LearningStateRepoImpl
import com.example.umma.data.repository.SessionMemoryRepositoryImpl
import com.example.umma.data.repository.StatisticsRepositoryImpl
import com.example.umma.data.repository.UserProfileRepositoryImpl
import com.example.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.example.umma.data.source.local.RoomCorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.data.source.remote.FirestoreCorrectionFlashcardRemoteDataSource
import com.example.umma.data.source.remote.FirestoreStatisticsHistoryRemoteDataSource
import com.example.umma.data.source.remote.LearningStateRemoteDataSource
import com.example.umma.data.source.remote.LearningStateRemoteDataSourceImpl
import com.example.umma.data.source.remote.StatisticsHistoryRemoteDataSource
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.repository.FlashcardRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.repository.SessionMemoryRepository
import com.example.umma.domain.repository.StatisticsRepository
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

    // 학습 상태 저장소는 DataStore 기반 구현체를 domain 계약 뒤에 숨긴다.
    // Fake 는 Correction/Dashboard 화면 시나리오 검증용. 평소에는 Real 을 활성화한다.
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

    // SRS 반복학습 저장소. 화면 개발 시 FakeFlashcardRepository 로 교체 가능.
    @Binds
    @Singleton
    abstract fun bindFlashcardRepository(
        impl: FlashcardRepositoryImpl
//        impl: FakeFlashcardRepository
    ): FlashcardRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindStatisticsRepository(
        impl: StatisticsRepositoryImpl
    ): StatisticsRepository

    @Binds
    @Singleton
    abstract fun bindStatisticsHistoryRemoteDataSource(
        impl: FirestoreStatisticsHistoryRemoteDataSource
    ): StatisticsHistoryRemoteDataSource
}
