package com.app.umma.di

import com.app.umma.core.util.NetworkConnectivityMonitor
import com.app.umma.core.util.NetworkConnectivityMonitorImpl
import com.app.umma.data.repository.AuthRepositoryImpl
import com.app.umma.data.repository.ChatUsageRepositoryImpl
import com.app.umma.data.repository.NotificationSettingsRepositoryImpl
import com.app.umma.data.repository.UserProfileRepositoryImpl
import com.app.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.app.umma.data.source.local.RoomCorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.app.umma.data.source.remote.ChatUsageRemoteDataSource
import com.app.umma.data.source.remote.FirestoreCorrectionFlashcardRemoteDataSource
import com.app.umma.data.source.remote.CloudFunctionChatUsageRemoteDataSource
import com.app.umma.data.source.remote.FirestoreStatisticsHistoryRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteDataSourceImpl
import com.app.umma.data.source.remote.StatisticsHistoryRemoteDataSource
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewRepository
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewRepositoryImpl
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.ChatUsageRepository
import com.app.umma.domain.repository.NotificationSettingsRepository
import com.app.umma.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Variant-independent repository and data source bindings.
 *
 * Repository interfaces that switch between real and fake implementations
 * are bound in dev/mock source sets to avoid duplicate Hilt bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    // CorrectionRepository binding is variant-specific:
    // devDebug -> DevCorrectionRepositoryModule, mockDebug -> MockCorrectionRepositoryModule.
    // LearningStateRepo, FlashcardRepository, and StatisticsRepository follow the same pattern.

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
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindStatisticsHistoryRemoteDataSource(
        impl: FirestoreStatisticsHistoryRemoteDataSource
    ): StatisticsHistoryRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindChatUsageRepository(
        impl: ChatUsageRepositoryImpl
    ): ChatUsageRepository

    @Binds
    @Singleton
    abstract fun bindChatPromptReviewRepository(
        impl: ChatPromptReviewRepositoryImpl
    ): ChatPromptReviewRepository

    @Binds
    @Singleton
    abstract fun bindChatUsageRemoteDataSource(
        impl: CloudFunctionChatUsageRemoteDataSource
    ): ChatUsageRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindNotificationSettingsRepository(
        impl: NotificationSettingsRepositoryImpl
    ): NotificationSettingsRepository
}
