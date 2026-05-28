package com.app.umma.di

import com.app.umma.data.repository.fake.FakeChatRepository
import com.app.umma.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fake Chat repository binding for the mock variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object MockChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        fake: FakeChatRepository
    ): ChatRepository = fake
}
