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
import com.app.umma.presentation.correction.CorrectionReturnOutcome
import com.app.umma.presentation.correction.CorrectionScreen
import com.app.umma.presentation.dashboard.DashboardScreen
import com.app.umma.presentation.dashboard.MyPageScreen
import com.app.umma.presentation.srsstudy.SrsCardListScreen
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
            composable<Route.Dashboard> { backStackEntry ->
                val correctionCompletionMessage =
                    backStackEntry.savedStateHandle.get<String>(CorrectionCompletionMessageKey)
                val correctionReturnOutcomeRaw =
                    backStackEntry.savedStateHandle.get<String>(CorrectionReturnOutcomeKey)
                val correctionReturnOutcome = correctionReturnOutcomeRaw
                    ?.let { runCatching { CorrectionReturnOutcome.valueOf(it) }.getOrNull() }
                DashboardScreen(
                    // navigateSingle: 서로 다른 카드 동시 탭 → 첫 탭만 통과(화면 단위 가드)
                    // rememberDashboardCardClick throttle 은 같은 카드 연타 방지 보조로 병행 유지
                    onNavigateToChat = { navController.navigateSingle(Route.Chat) },
                    onNavigateToStatistics = { navController.navigateSingle(Route.Statistics) },
                    onNavigateToSrsStudy = { navController.navigateSingle(Route.SrsStudy) },
                    onNavigateToCorrection = { navController.navigateSingle(Route.CorrectionList) },
                    onNavigateToMyPage = { navController.navigateSingle(Route.MyPage) },
                    correctionCompletionMessage = correctionCompletionMessage,
                    correctionReturnOutcome = correctionReturnOutcome,
                    onCorrectionCompletionMessageConsumed = {
                        backStackEntry.savedStateHandle.remove<String>(CorrectionCompletionMessageKey)
                        backStackEntry.savedStateHandle.remove<String>(CorrectionReturnOutcomeKey)
                    },
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
            composable<Route.SrsStudy> {
                SrsStudyScreen(
                    onNavigateToDashboard = {
                        // 학습 완료 후 Dashboard로 복귀
                        // popUpTo: SRS 화면을 뒤로가기 기록에서 지움
                        // launchSingleTop: Dashboard 인스턴스 재사용 스크롤/상태 보존 중복 push 방지
                        navController.navigate(Route.Dashboard) {
                            popUpTo<Route.SrsStudyGraph> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCardList = {
                        navController.navigate(Route.SrsCardList)
                    },
                    onNavigateToCorrection = { navController.navigate((Route.CorrectionList)) }
                )
            }
            composable<Route.SrsCardList> {
                SrsCardListScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCorrection = { navController.navigate((Route.CorrectionList)) }
                )
            }
        }

        // 챗(대화) 그래프
        navigation<Route.ChatGraph>(startDestination = Route.Chat) {
            composable<Route.Chat> { ChatScreen() }
        }

        // 교정 그래프
        navigation<Route.CorrectionGraph>(startDestination = Route.CorrectionList) {
            composable<Route.CorrectionList> {
                CorrectionScreen(
                    onNavigateToDashboard = { message, outcome ->
                        // COR-007-A: 완료 파이프라인 성공 직후 Dashboard 로 복귀.
                        // - popUpTo<CorrectionGraph>{inclusive=true}: CorrectionGraph 를 backstack 에서 통째로
                        //   제거해, 비정상 진입 경로(Dashboard 없이 Correction 으로 진입)에서도 backstack 이
                        //   깔끔하게 정리되도록 한다. 기존 Umma 네비게이션 컨벤션(현재 그래프 통째 정리)과 일관.
                        // - launchSingleTop=true: 정상 경로(Dashboard → Correction → Dashboard)에서 기존
                        //   Dashboard 인스턴스를 재사용해 스크롤/상태를 보존하고 중복 push 도 방지한다.
                        navController.setCorrectionCompletionMessage(message, outcome)
                        navController.navigate(Route.Dashboard) {
                            popUpTo<Route.CorrectionGraph> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToChat = {
                        // COR-001-B: Empty 상태 CTA — BottomBar 탭 전환과 동일 패턴.
                        // - popUpTo<Dashboard>{saveState=true}: 현재 Correction 탭 backstack 상태를 보존해
                        //   사용자가 다시 교정 탭으로 돌아오면 재진입이 자연스럽다. 시작점인 Dashboard 까지 pop 하므로
                        //   다른 탭(Chat) 진입 시 backstack 이 평탄해진다.
                        // - launchSingleTop=true + restoreState=true: 기존 Chat 인스턴스 재사용 + 상태 복원.
                        //   UmmaBottomAppBar 의 탭 전환 정책과 1:1 동치라 두 진입점이 동일한 nav 결과를 만든다.
                        navController.navigate(Route.Chat) {
                            popUpTo<Route.Dashboard> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

private const val CorrectionCompletionMessageKey = "correction_completion_message"
private const val CorrectionReturnOutcomeKey = "correction_return_outcome"

private fun NavHostController.setCorrectionCompletionMessage(
    message: String,
    outcome: CorrectionReturnOutcome
) {
    runCatching { getBackStackEntry<Route.Dashboard>() }
        .getOrNull()
        ?.savedStateHandle
        ?.also {
            it[CorrectionCompletionMessageKey] = message
            it[CorrectionReturnOutcomeKey] = outcome.name
        }
}
