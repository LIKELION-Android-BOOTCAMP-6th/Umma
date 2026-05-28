package com.app.umma.di

import com.app.umma.data.repository.LearningStateRepoImpl
import com.app.umma.domain.repository.LearningStateRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant는 실제 LearningState 저장소를 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevLearningStateRepositoryModule {

    @Provides
    @Singleton
    fun provideLearningStateRepo(
        real: LearningStateRepoImpl
    ): LearningStateRepo = real
}
