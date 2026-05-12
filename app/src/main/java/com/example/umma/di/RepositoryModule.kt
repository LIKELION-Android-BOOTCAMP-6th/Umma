package com.example.umma.di

import com.example.umma.core.util.NetworkConnectivityMonitor
import com.example.umma.core.util.NetworkConnectivityMonitorImpl
import com.example.umma.data.repository.AuthRepositoryImpl
import com.example.umma.data.repository.ChatRepositoryImpl
import com.example.umma.data.repository.LearningStateRepoImpl
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.LearningStateRepo
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

    // 학습 상태 저장소는 DataStore 기반 구현체를 domain 계약 뒤에 숨긴다.
    @Binds
    @Singleton
    abstract fun bindLearningStateRepo(
        learningStateRepoImpl: LearningStateRepoImpl
    ): LearningStateRepo

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(
        networkConnectivityMonitorImpl: NetworkConnectivityMonitorImpl
    ): NetworkConnectivityMonitor
}