package com.app.umma.di

import com.app.umma.data.repository.SessionMemoryRepositoryImpl
import com.app.umma.domain.repository.SessionMemoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DevSessionMemoryRepositoryModule {

    @Provides
    @Singleton
    fun provideSessionMemoryRepository(
        real: SessionMemoryRepositoryImpl
    ): SessionMemoryRepository = real
}
