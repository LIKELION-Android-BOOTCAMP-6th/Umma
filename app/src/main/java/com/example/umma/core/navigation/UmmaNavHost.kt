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
import com.example.umma.presentation.chat.ChatScreen
import com.example.umma.presentation.dashboard.DashboardScreen
import com.example.umma.presentation.dashboard.MyPageScreen
import com.example.umma.presentation.correction.CorrectionScreen
import com.example.umma.presentation.onboarding.OnBoardingScreen
import com.example.umma.presentation.study.StudyListScreen

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
            startDestination = Route.OnBoarding
        ) {
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
                    onNavigateToStudyList = { navController.navigate(Route.StudyList) },
                    onNavigateToCorrection = { navController.navigate(Route.CorrectionList) },
                    onNavigateToMyPage = { navController.navigate(Route.MyPage) }
                )

            }

            composable<Route.MyPage> { MyPageScreen() }
        }

        // 통계 그래프
        navigation<Route.AnalyticsGraph>(startDestination = Route.Analytics) {
            composable<Route.Analytics> { AnalyticsScreen() }
        }

        // 학습 그래프
        navigation<Route.StudyGraph>(startDestination = Route.StudyList) {
            composable<Route.StudyList> { StudyListScreen() }
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
