package com.app.umma.di

import com.app.umma.domain.usecase.learningstate.DefaultLangStateAnalysisPolicy
import com.app.umma.domain.usecase.learningstate.LangStateAnalysisPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * LearningState domain policy bindings.
 *
 * 계산 정책은 Repository/Data layer가 아니라 domain policy가 소유하므로,
 * Hilt도 use case가 interface만 의존하도록 이 모듈에서 기본 구현을 연결한다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LearningStatePolicyModule {
    // UseCase가 concrete 구현을 직접 new 하지 않도록, policy 구현은 DI에서 한 번만 묶는다.
    @Binds
    @Singleton
    abstract fun bindLangStateAnalysisPolicy(
        impl: DefaultLangStateAnalysisPolicy
    ): LangStateAnalysisPolicy
}
