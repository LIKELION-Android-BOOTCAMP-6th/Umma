package com.app.umma.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.app.umma.presentation.auth.AppEntryScreen
import com.app.umma.presentation.auth.OnBoardingScreen
import com.app.umma.presentation.chat.ChatScreen
import com.app.umma.presentation.dashboard.DashboardScreen
import com.app.umma.presentation.dashboard.MyPageScreen
import com.app.umma.presentation.correction.CorrectionScreen
import com.app.umma.presentation.srsstudy.SrsStudyScreen
import com.app.umma.presentation.statistics.StatisticsScreen

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
                    onNavigateToStatistics = { navController.navigate(Route.Statistics) },
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
        navigation<Route.StatisticsGraph>(startDestination = Route.Statistics) {
            composable<Route.Statistics> { StatisticsScreen() }
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
            composable<Route.CorrectionList> {
                CorrectionScreen(
                    onNavigateToDashboard = {
                        // COR-007-A: 완료 파이프라인 성공 직후 Dashboard 로 복귀.
                        // - popUpTo<CorrectionGraph>{inclusive=true}: CorrectionGraph 를 backstack 에서 통째로
                        //   제거해, 비정상 진입 경로(Dashboard 없이 Correction 으로 진입)에서도 backstack 이
                        //   깔끔하게 정리되도록 한다. 기존 Umma 네비게이션 컨벤션(현재 그래프 통째 정리)과 일관.
                        // - launchSingleTop=true: 정상 경로(Dashboard → Correction → Dashboard)에서 기존
                        //   Dashboard 인스턴스를 재사용해 스크롤/상태를 보존하고 중복 push 도 방지한다.
                        navController.navigate(Route.Dashboard) {
                            popUpTo<Route.CorrectionGraph> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
