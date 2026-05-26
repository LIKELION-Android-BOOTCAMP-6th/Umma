package com.app.umma.di

import com.app.umma.data.repository.FlashcardRepositoryImpl
import com.app.umma.data.repository.StatisticsRepositoryImpl
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.repository.StatisticsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant에서는 Statistics 화면도 실제 통계 저장소를 사용한다.
 *
 * LearningStateRepo 같은 전역 상태 저장소는 main RepositoryModule의 real binding을 그대로 쓴다.
 * 이 variant module은 통계 목업 확인에 필요한 StatisticsRepository 교체만 책임진다.
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantRepositoryModule {

    @Provides
    @Singleton
    fun provideStatisticsRepository(
        real: StatisticsRepositoryImpl
    ): StatisticsRepository = real

    @Provides
    @Singleton
    fun provideFlashcardRepository(
        real: FlashcardRepositoryImpl
    ): FlashcardRepository = real
}
