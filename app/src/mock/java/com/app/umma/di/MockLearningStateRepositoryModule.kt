package com.app.umma.di

import com.app.umma.data.repository.fake.FakeLearningStateRepo
import com.app.umma.domain.repository.LearningStateRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * mock variant는 데모 시나리오용 LearningState fake 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object MockLearningStateRepositoryModule {

    @Provides
    @Singleton
    fun provideLearningStateRepo(
        fake: FakeLearningStateRepo
    ): LearningStateRepo = fake
}
