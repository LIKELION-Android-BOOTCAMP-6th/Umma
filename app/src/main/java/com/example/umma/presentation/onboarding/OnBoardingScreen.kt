package com.example.umma.presentation.onboarding

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.R
import com.example.umma.core.navigation.Route
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.util.GoogleSignInHelper
import com.example.umma.presentation.auth.AuthViewModel
import com.example.umma.presentation.auth.GoogleAuthState
import kotlinx.coroutines.launch


/**
 * 온보딩 화면을 구성하는 컴포저블입니다.
 *
 * 앱의 주요 기능을 소개하며 로그인 화면으로의 전환을 유도합니다.
 *
 * @param onNavigateToHome 로그인 화면으로 이동하는 콜백 함수.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnBoardingScreen(
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val webClientId = stringResource(id = R.string.default_web_client_id)

    LaunchedEffect(uiState.googleState) {
        if (uiState.googleState == GoogleAuthState.SUCCESS) {
            onNavigateToHome()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.updateErrorMessage(null)
        }
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "Umma",
                isCenterTitle = false
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
            ) {
                Text(
                    text = "온보딩 플레이스 홀더", textAlign = TextAlign.Center
                )
                Button(
                    enabled = !uiState.isLoading,
                    onClick = {
                        viewModel.updateLoading(true)
                        coroutineScope.launch {
                            try {
                                val idToken = GoogleSignInHelper.getGoogleIdToken(
                                    context = context,
                                    webClientId = webClientId
                                )
                                viewModel.signInWithGoogle(idToken)
                            } catch (e: Exception) {
                                viewModel.updateLoading(false)
                                viewModel.updateErrorMessage("Google 로그인에 실패했습니다")
                            }
                        }
                    }
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = "Google 계정으로 시작하기")
                    }
                }

            }
        }
    }
}