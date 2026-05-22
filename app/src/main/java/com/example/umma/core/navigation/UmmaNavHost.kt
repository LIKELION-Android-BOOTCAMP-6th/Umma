package com.example.umma.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.umma.presentation.analytics.AnalyticsScreen
import com.example.umma.presentation.auth.AppEntryScreen
import com.example.umma.presentation.auth.OnBoardingScreen
import com.example.umma.presentation.chat.ChatScreen
import com.example.umma.presentation.dashboard.DashboardScreen
import com.example.umma.presentation.dashboard.MyPageScreen
import com.example.umma.presentation.correction.CorrectionScreen
import com.example.umma.presentation.srsstudy.SrsStudyScreen

@Composable
fun UmmaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Route = Route.AuthGraph
) {
    NavHost(
        navController = navController,
        modifier = modifier,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popExitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None }

    ) {

        // 인증 그래프 (온보딩 화면 포함)
        navigation<Route.AuthGraph>(
            startDestination = Route.AppEntry
        ) {
            composable<Route.AppEntry> {
                AppEntryScreen(
                    onNavigateToOnBoarding = {
                        navController.navigate(Route.OnBoarding) {
                            popUpTo(Route.AppEntry) { inclusive = true }
                        }
                    },
                    onNavigateToDashboard = {
                        navController.navigate(Route.Dashboard) {
                            popUpTo(Route.AuthGraph) { inclusive = true }
                        }
                    }
                )
            }
            composable<Route.OnBoarding> {
                OnBoardingScreen(
                    onNavigateToHome = {
                        navController.navigate(Route.Dashboard) {
                            popUpTo(Route.AuthGraph) { inclusive = true }
                        }
                    }
                )
            }
        }

        // 홈 그래프 (마이페이지 포함)
        navigation<Route.HomeGraph>(startDestination = Route.Dashboard) {
            composable<Route.Dashboard> {
                DashboardScreen(
                    onNavigateToChat = { navController.navigate(Route.Chat) },
                    onNavigateToAnalytics = { navController.navigate(Route.Analytics) },
                    onNavigateToSrsStudy = { navController.navigate(Route.SrsStudy) },
                    onNavigateToCorrection = { navController.navigate(Route.CorrectionList) },
                    onNavigateToMyPage = { navController.navigate(Route.MyPage) }
                )

            }

            composable<Route.MyPage> {
                MyPageScreen(
                    onNavigateToOnBoarding = {
                        navController.navigate(Route.OnBoarding) {
                            // 로그아웃 후 BackStack 전체 초기화
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // 통계 그래프
        navigation<Route.AnalyticsGraph>(startDestination = Route.Analytics) {
            composable<Route.Analytics> { AnalyticsScreen() }
        }

        // SRS 반복학습 그래프
        navigation<Route.SrsStudyGraph>(startDestination = Route.SrsStudy) {
            composable<Route.SrsStudy> { SrsStudyScreen() }
        }

        // 챗(대화) 그래프
        navigation<Route.ChatGraph>(startDestination = Route.Chat) {
            composable<Route.Chat> { ChatScreen() }
        }

        // 교정 그래프
        navigation<Route.CorrectionGraph>(startDestination = Route.CorrectionList) {
            composable<Route.CorrectionList> { CorrectionScreen() }
        }
    }
}
