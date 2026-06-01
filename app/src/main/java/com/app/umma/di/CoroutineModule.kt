package com.app.umma.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체 생명주기에 묶이는 coroutine 의존성을 제공합니다.
 *
 * ViewModel scope는 화면 back stack 제거 시 즉시 취소되므로, local-first sync처럼
 * "화면은 사라져도 짧게 마무리되어야 하는" 작업에는 별도 application scope가 필요합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        // SupervisorJob을 사용해 한 sync 작업 실패가 같은 scope의 다른 작업까지 취소하지 않게 한다.
        // Dispatchers.IO는 Room/HTTP 같은 I/O 중심 작업을 기본 실행 위치로 둔다.
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * 화면 생명주기보다 오래 살아야 하는 best-effort 작업용 scope qualifier입니다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
