package com.app.umma.di

import com.app.umma.watchbridge.DefaultPhoneChatSessionRuntime
import com.app.umma.watchbridge.DefaultWatchBridgeEventSink
import com.app.umma.watchbridge.DefaultWatchPhoneLauncher
import com.app.umma.watchbridge.PhoneChatSessionRuntime
import com.app.umma.watchbridge.WatchBridgeEventSink
import com.app.umma.watchbridge.WatchPhoneLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchBridgeModule {
    @Binds
    @Singleton
    abstract fun bindPhoneChatSessionRuntime(
        impl: DefaultPhoneChatSessionRuntime
    ): PhoneChatSessionRuntime

    @Binds
    @Singleton
    abstract fun bindWatchBridgeEventSink(
        impl: DefaultWatchBridgeEventSink
    ): WatchBridgeEventSink

    @Binds
    @Singleton
    abstract fun bindWatchPhoneLauncher(
        impl: DefaultWatchPhoneLauncher
    ): WatchPhoneLauncher
}
