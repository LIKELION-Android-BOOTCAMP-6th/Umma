package com.example.umma.di

import com.example.umma.data.repository.fake.FakeStatisticsRepository
import com.example.umma.domain.repository.StatisticsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * mock variant에서는 화면 검증용 fake 저장소를 사용한다.
 *
 * mockDebug에서는 화면 검증이 필요한 repository만 fake 구현체로 바꾼다.
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantRepositoryModule {

    @Provides
    @Singleton
    fun provideStatisticsRepository(
        fake: FakeStatisticsRepository
    ): StatisticsRepository = fake
}
