package com.app.umma.di

import com.app.umma.data.repository.ChatRepositoryImpl
import com.app.umma.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Real Chat repository binding for the dev variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        real: ChatRepositoryImpl
    ): ChatRepository = real
}
