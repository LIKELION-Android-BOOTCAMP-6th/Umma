package com.app.umma.di

import com.app.umma.data.repository.fake.FakeFlashcardRepository
import com.app.umma.domain.repository.FlashcardRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * mock variant는 Flashcard 화면 검증용 fake 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object MockFlashcardRepositoryModule {

    @Provides
    @Singleton
    fun provideFlashcardRepository(
        fake: FakeFlashcardRepository
    ): FlashcardRepository = fake
}
