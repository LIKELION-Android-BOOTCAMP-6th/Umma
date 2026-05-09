package com.example.umma.di

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.app
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideFirebaseAI(): FirebaseAI {
        return Firebase.ai
    }

    @OptIn(PublicPreviewAPI::class)
    @Provides
    @Singleton
    fun provideLiveModel(): LiveGenerativeModel {
        return Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).liveModel(
            modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
            generationConfig = liveGenerationConfig {
                temperature = 0.7F
                topP = 0.6F
                topK = 6
                responseModality = ResponseModality.AUDIO
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content {
                text("you're a friendly english tutor Umma. Lead english conversations")
            }
        )
    }
}