package com.app.umma.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.app.umma.wear.di.WearChatContainer
import com.app.umma.wear.presentation.WearChatViewModel
import com.app.umma.wear.presentation.screen.WearDebugApp

class MainActivity : ComponentActivity() {
    private val container by lazy { WearChatContainer(applicationContext) }
    private var detachRequestedOnFinish = false

    private val viewModel by viewModels<WearChatViewModel> {
        WearChatViewModel.Factory(
            attachWatchChatUseCase = container.attachWatchChatUseCase,
            detachWatchChatUseCase = container.detachWatchChatUseCase,
            pressPttUseCase = container.pressPttUseCase,
            releasePttUseCase = container.releasePttUseCase,
            observeWatchChatStateUseCase = container.observeWatchChatStateUseCase,
            debugReleasePhoneOwnerUseCase = container.debugReleasePhoneOwnerUseCase
        )
    }
    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onPttClick()
            } else {
                viewModel.onMicPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            WearDebugApp(
                uiState = uiState,
                onAttachClick = viewModel::onAttachClick,
                onPttClick = ::handlePressPttClick,
                onReleaseOwnerClick = viewModel::onReleaseOwnerClick
            )
        }
    }

    private fun handlePressPttClick() {
        val permissionState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED || viewModel.uiState.value.canReleasePtt) {
            viewModel.onPttClick()
        } else {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        if (isFinishing && !detachRequestedOnFinish) {
            detachRequestedOnFinish = true
            viewModel.onAppFinishing()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && !detachRequestedOnFinish) {
            detachRequestedOnFinish = true
            viewModel.onAppFinishing()
        }
        super.onDestroy()
    }
}
