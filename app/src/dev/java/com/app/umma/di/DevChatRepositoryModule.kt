package com.app.umma.di

import com.app.umma.data.repository.ChatRepositoryImpl
import com.app.umma.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant 의 AI Chat transport binding 입니다.
 *
 * CHAT-ENGINE-001 이후 AI Chat realtime transport 는 OpenAI Realtime 단일 경로를 사용합니다.
 * mock variant 는 별도 module 에서 FakeChatRepository 를 제공하므로 데모/QA 시나리오는 기존처럼 분리됩니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository {
        // provider 선택 분기를 남기면 실제 테스트가 어떤 엔진에서 도는지 흐려진다.
        // devDebug 는 OpenAI Realtime만 사용하고, 예외 시 화면/로그로 실패를 확인한다.
        return impl
    }
}
