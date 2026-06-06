package com.app.umma.di

import com.app.umma.data.repository.correction.CorrectionAiClient
import com.app.umma.data.repository.correction.GeminiCorrectionAiClient
import com.app.umma.data.repository.chatconversation.ChatConversationEvidenceAiClient
import com.app.umma.data.repository.chatconversation.GeminiChatConversationEvidenceAiClient
import com.app.umma.data.repository.realtime.GeminiTopicSummaryAiClient
import com.app.umma.data.repository.realtime.TopicSummaryAiClient
import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 교정 생성과 세션 주제 요약에서 사용하는 Firebase AI 설정을 담당하는 Hilt 모듈입니다.
 *
 * AI Chat realtime transport 는 CHAT-ENGINE-001 이후 OpenAI Realtime 경로를 사용하므로,
 * 이 모듈은 음성 대화 엔진을 제공하지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    /**
     * [FirebaseAI] 인스턴스를 제공합니다.
     *
     * 백엔드로 Google AI(Generative AI)를 사용하도록 구성되어 있습니다.
     * 이 인스턴스는 교정 생성과 topic title 요약 같은 단발 텍스트 호출에만 사용됩니다.
     */
    @Provides
    @Singleton
    fun provideFirebaseAI(): FirebaseAI {
        return Firebase.ai(backend = GenerativeBackend.googleAI())
    }
}

/**
 * Correction 도메인이 사용하는 단발 AI 호출 어댑터 바인딩.
 *
 * 인터페이스를 두는 이유는 Repository/ViewModel 단위 테스트에서 fake JSON 을 주입하기 위해서다.
 * 모듈을 [AIModule] 과 분리한 이유는 @Provides (object) 와 @Binds (abstract class) 가
 * 같은 클래스에 공존할 수 없기 때문이다.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class CorrectionAiBindings {

    @Binds
    @Singleton
    abstract fun bindCorrectionAiClient(
        impl: GeminiCorrectionAiClient
    ): CorrectionAiClient
}

/**
 * 세션 주제 요약 AI 호출 어댑터 바인딩.
 *
 * [CorrectionAiBindings] 와 같은 이유로 [AIModule] 과 별도 abstract class 로 분리한다.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class TopicSummaryAiBindings {

    @Binds
    @Singleton
    abstract fun bindTopicSummaryAiClient(
        impl: GeminiTopicSummaryAiClient
    ): TopicSummaryAiClient
}

/**
 * Chat conversation evidence 분석용 단발 AI 호출 어댑터 바인딩.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ChatConversationEvidenceAiBindings {

    @Binds
    @Singleton
    abstract fun bindChatConversationEvidenceAiClient(
        impl: GeminiChatConversationEvidenceAiClient
    ): ChatConversationEvidenceAiClient
}
