package com.app.umma.di

import com.app.umma.BuildConfig
import com.app.umma.data.repository.ChatRepositoryImpl
import com.app.umma.data.repository.OpenAIRealtimeChatRepositoryPoC
import com.app.umma.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * dev variant 의 Chat transport 선택 지점입니다.
 *
 * production 전환 전에는 presentation/domain 레이어를 건드리지 않고 repository 구현체만
 * 바꿔 PoC 결과를 확인해야 하므로, Hilt binding 에서 기존 Gemini 구현과 OpenAI PoC 구현을
 * 선택한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        real: ChatRepositoryImpl,
        openAIPoC: OpenAIRealtimeChatRepositoryPoC
    ): ChatRepository {
        // CHAT-POC-001은 transport 교체 가능성 검증이 목적이므로 dev variant에서만
        // local.properties 플래그로 OpenAI 경로를 선택하고, 기본값은 기존 Gemini 구현을 유지한다.
        // mock variant 는 별도 module 에서 FakeChatRepository 를 제공하므로 이 분기 영향을 받지 않는다.
        return if (BuildConfig.OPENAI_REALTIME_ENABLED) openAIPoC else real
    }
}
