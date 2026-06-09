package com.app.umma.presentation.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.R
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.SpacingXXL
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextPrimaryR
import com.app.umma.core.theme.TitleB
import com.app.umma.core.theme.TitleColor
import com.app.umma.core.ui.component.PageIndicator
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.util.GoogleSignInHelper
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
    val pagerState = rememberPagerState(pageCount = { onBoardingPages.size })
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
        containerColor = BackgroundPrimary,
        topBar = {
            UmmaAppBar(
                title = "Umma",
                isCenterTitle = false,
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //----- pager 시작 Page(3장)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    // 로딩 중 스와이프 차단
                    userScrollEnabled = !uiState.isLoading
                ) { page ->
                    OnboardingPageContent(page = onBoardingPages[page])
                }
                //----- pager 끝
                //----- google 로그인 버튼 시작
                if (pagerState.currentPage == onBoardingPages.size - 1) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = SpacingL)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = !uiState.isLoading) {
                                viewModel.updateLoading(true)
                                coroutineScope.launch {
                                    try {
                                        val idToken =
                                            GoogleSignInHelper.getGoogleIdToken(
                                                context = context,
                                                webClientId = webClientId
                                            )
                                        viewModel.signInWithGoogle(idToken)
                                    } catch (e: Exception) {
                                        viewModel.updateLoading(false)
                                        viewModel.updateErrorMessage(
                                            "Google 로그인에 실패했습니다"
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_auth_google),
                                contentDescription = "Google 로그인",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(0.2f))
                }
                //----- google 로그인 버튼 끝
                //----- 페이지 인디케이터 시작
                PageIndicator(
                    modifier = Modifier.padding(horizontal = SpacingL, vertical = SpacingL),
                    totalCount = onBoardingPages.size,
                    currentIndex = pagerState.currentPage
                )
                //----- 페이지 인디케이터 끝
            }
        }
    }
}


private data class OnBoardingPage(
    val title: String,
    val description: String
)

private val onBoardingPages = listOf(
    OnBoardingPage(
        title = "AI와 함께하는 실전 회화",
        description = "더 이상 혼자 공부하지 마세요." +
                "\n당신의 언어 수준에 맞춘 AI 튜터와 실시간으로 대화하며 성장하세요"
    ),
    OnBoardingPage(
        title = "실시간 문장 교정 시스템",
        description = "당신이 말하는 문장을 분석하여 더 자연스러운 표현으로 교정해 드립니다." +
                "\n듣는 것을 두려워하지 마세요"
    ),
    OnBoardingPage(
        title = "데이터로 확인하는 나의 성장",
        description = "매일의 학습 통계와 성취도를 통해 눈에 보이는 실력 향상을 경험하세요." +
                "\n지금 바로 Umma와 함께 시작해볼까요?"
    ),
)


@Composable
private fun OnboardingPageContent(page: OnBoardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpacingL, vertical = SpacingXXL)
    ) {
        Text(
            text = page.title,
            style = TitleB.copy(
                lineHeight = 44.sp,
                letterSpacing = (-1.6).sp
            ),
            color = TitleColor
        )
        Spacer(modifier = Modifier.height(SpacingXL))
        Spacer(modifier = Modifier.height(SpacingXL))
        Text(
            text = page.description,
            style = TextPrimaryR.copy(fontSize = 18.sp),
            color = TextPrimary,
            lineHeight = 28.8.sp
        )
    }
}
