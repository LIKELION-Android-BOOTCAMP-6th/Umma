package com.app.umma.di

import com.app.umma.data.repository.FlashcardRepositoryImpl
import com.app.umma.domain.repository.FlashcardRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant는 실제 Flashcard 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevFlashcardRepositoryModule {

    @Provides
    @Singleton
    fun provideFlashcardRepository(
        real: FlashcardRepositoryImpl
    ): FlashcardRepository = real
}
