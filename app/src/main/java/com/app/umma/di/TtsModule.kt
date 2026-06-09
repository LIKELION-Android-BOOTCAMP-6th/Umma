package com.app.umma.di

import android.content.Context
import com.app.umma.core.tts.TextToSpeechController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * TextToSpeechController 를 Hilt를 통해 제공하는 모듈
 * ViewModel 진입마다 새 인스턴스 생성
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {
    @Provides
    @Singleton
    fun provideTextToSpeechController(
        @ApplicationContext context: Context
    ): TextToSpeechController = TextToSpeechController(context)
}
