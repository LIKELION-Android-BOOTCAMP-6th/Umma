package com.app.umma.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.app.umma.wear.data.WearBridgeRepositoryImpl
import com.app.umma.wear.presentation.WearChatViewModel
import com.app.umma.wear.presentation.screen.WearDebugApp

class MainActivity : ComponentActivity() {
    private val repository by lazy { WearBridgeRepositoryImpl(applicationContext) }

    private val viewModel by viewModels<WearChatViewModel> {
        WearChatViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            WearDebugApp(
                uiState = uiState,
                onAttachClick = viewModel::onAttachClick,
                onReleaseOwnerClick = viewModel::onReleaseOwnerClick
            )
        }
    }
}
