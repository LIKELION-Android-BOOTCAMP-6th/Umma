package com.app.umma.di

import com.app.umma.data.repository.correction.CorrectionAiClient
import com.app.umma.data.repository.correction.GeminiCorrectionAiClient
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
 * Firebase AI 및 Gemini Live API 설정을 담당하는 Hilt 모듈입니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    /**
     * [FirebaseAI] 인스턴스를 제공합니다.
     *
     * 백엔드로 Google AI(Generative AI)를 사용하도록 구성되어 있습니다.
     * 시스템 지침(System Instruction)은 이 레이어가 아닌 [ChatRepositoryImpl]에서
     * 세션 시작 시 동적으로 주입됩니다.
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
abstract class CorrectionAiBindings {

    @Binds
    @Singleton
    abstract fun bindCorrectionAiClient(
        impl: GeminiCorrectionAiClient
    ): CorrectionAiClient
}
