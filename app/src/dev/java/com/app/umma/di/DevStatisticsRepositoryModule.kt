package com.app.umma.di

import com.app.umma.data.repository.StatisticsRepositoryImpl
import com.app.umma.domain.repository.StatisticsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant는 실제 Statistics 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevStatisticsRepositoryModule {

    @Provides
    @Singleton
    fun provideStatisticsRepository(
        real: StatisticsRepositoryImpl
    ): StatisticsRepository = real
}
