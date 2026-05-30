package com.app.umma.di

import com.app.umma.data.repository.fake.FakeSessionMemoryRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MockSessionMemoryRepositoryModule {

    @Provides
    @Singleton
    fun provideSessionMemoryRepository(
        fake: FakeSessionMemoryRepository
    ): SessionMemoryRepository = fake
}
