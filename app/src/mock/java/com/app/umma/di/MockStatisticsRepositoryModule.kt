package com.app.umma.di

import com.app.umma.data.repository.fake.FakeStatisticsRepository
import com.app.umma.domain.repository.StatisticsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * mock variant는 Statistics 데모/QA preset을 가진 fake 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object MockStatisticsRepositoryModule {

    @Provides
    @Singleton
    fun provideStatisticsRepository(
        fake: FakeStatisticsRepository
    ): StatisticsRepository = fake
}
