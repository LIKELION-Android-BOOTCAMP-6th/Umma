package com.app.umma.di

import com.app.umma.data.repository.fake.FakeCorrectionRepository
import com.app.umma.domain.repository.CorrectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MockCorrectionRepositoryModule {

    @Provides
    @Singleton
    fun provideCorrectionRepository(
        fake: FakeCorrectionRepository
    ): CorrectionRepository = fake
}
