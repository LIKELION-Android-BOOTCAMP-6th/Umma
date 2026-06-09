package com.app.umma.wear.di

import android.content.Context
import com.app.umma.wear.data.WearAudioPlayer
import com.app.umma.wear.data.WearAudioRecorder
import com.app.umma.wear.data.datasource.WearBridgeRemoteDataSourceImpl
import com.app.umma.wear.data.repository.WearChatRepositoryImpl
import com.app.umma.wear.domain.usecase.AttachWatchChatUseCase
import com.app.umma.wear.domain.usecase.DetachWatchChatUseCase
import com.app.umma.wear.domain.usecase.DebugReleasePhoneOwnerUseCase
import com.app.umma.wear.domain.usecase.ObserveWatchChatStateUseCase
import com.app.umma.wear.domain.usecase.PressPttUseCase
import com.app.umma.wear.domain.usecase.RefreshPhoneTargetsUseCase
import com.app.umma.wear.domain.usecase.ReleasePttUseCase
import com.app.umma.wear.domain.usecase.SelectPhoneTargetUseCase
import com.app.umma.wear.domain.usecase.SetTargetChooserVisibleUseCase

class WearChatContainer(context: Context) {
    private val dataSource = WearBridgeRemoteDataSourceImpl(context)
    private val audioRecorder = WearAudioRecorder()
    private val audioPlayer = WearAudioPlayer()
    private val repository = WearChatRepositoryImpl(
        dataSource = dataSource,
        audioInput = audioRecorder,
        audioOutput = audioPlayer
    )

    val attachWatchChatUseCase = AttachWatchChatUseCase(repository)
    val detachWatchChatUseCase = DetachWatchChatUseCase(repository)
    val pressPttUseCase = PressPttUseCase(repository)
    val releasePttUseCase = ReleasePttUseCase(repository)
    val observeWatchChatStateUseCase = ObserveWatchChatStateUseCase(repository)
    val refreshPhoneTargetsUseCase = RefreshPhoneTargetsUseCase(repository)
    val selectPhoneTargetUseCase = SelectPhoneTargetUseCase(repository)
    val setTargetChooserVisibleUseCase = SetTargetChooserVisibleUseCase(repository)
    val debugReleasePhoneOwnerUseCase = DebugReleasePhoneOwnerUseCase(repository)
}
