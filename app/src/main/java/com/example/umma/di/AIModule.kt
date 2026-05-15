package com.example.umma.di

import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
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