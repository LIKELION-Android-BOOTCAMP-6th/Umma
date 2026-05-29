package com.app.umma.di

import com.app.umma.data.repository.CorrectionRepositoryImpl
import com.app.umma.domain.repository.CorrectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DevCorrectionRepositoryModule {

    @Provides
    @Singleton
    fun provideCorrectionRepository(
        real: CorrectionRepositoryImpl
    ): CorrectionRepository = real
}
