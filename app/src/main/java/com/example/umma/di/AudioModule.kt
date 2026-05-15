package com.example.umma.di

import com.example.umma.data.source.local.AudioPlayer
import com.example.umma.data.source.local.AudioRecorder
import com.example.umma.domain.audio.AudioInput
import com.example.umma.domain.audio.AudioOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioInput(
        impl: AudioRecorder
    ): AudioInput

    @Binds
    @Singleton
    abstract fun bindAudioOutput(
        impl: AudioPlayer
    ): AudioOutput
}