package com.example.umma.di

import com.example.umma.data.repository.LiveChatRepositoryImpl
import com.example.umma.domain.repository.LiveChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLiveChatRepository(
        liveChatRepositoryImpl: LiveChatRepositoryImpl
    ): LiveChatRepository
}