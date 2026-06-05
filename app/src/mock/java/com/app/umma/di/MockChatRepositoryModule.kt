package com.app.umma.di

import com.app.umma.data.repository.fake.FakeChatRepository
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewSessionReporter
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

    @Provides
    @Singleton
    fun provideChatPromptReviewSessionReporter(
        fake: FakeChatRepository
    ): ChatPromptReviewSessionReporter {
        // mockDebug는 fake transport가 active session을 들고 있으므로 reporter도 같은 인스턴스를 사용한다.
        return fake
    }
}
