package com.example.umma.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.umma.presentation.home.HomeScreen
import com.example.umma.presentation.home.MyPageScreen
import com.example.umma.presentation.OnBoardingScreen
import com.example.umma.presentation.analytics.AnalyticsScreen
import com.example.umma.presentation.auth.SignInScreen
import com.example.umma.presentation.chat.ChatScreen
import com.example.umma.presentation.feed_back.FeedbackDetailScreen
import com.example.umma.presentation.feed_back.FeedbackListScreen
import com.example.umma.presentation.study.StudyDetailScreen
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
                OnBoardingScreen {
                    navController.navigate(Route.SignIn)
                }
            }
            composable<Route.SignIn> {
                SignInScreen {
                    navController.navigate(Route.Home) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }

        // 홈 그래프 (마이페이지 포함)
        navigation<Route.HomeGraph>(startDestination = Route.Home) {
            composable<Route.Home> {
                HomeScreen(
                    onNavigateToChat = { navController.navigate(Route.Chat) },
                    onNavigateToAnalytics = { navController.navigate(Route.Analytics) },
                    onNavigateToStudyList = { navController.navigate(Route.StudyList) },
                    onNavigateToFeedbackList = { navController.navigate(Route.FeedbackList) },
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
            composable<Route.StudyList> {
                StudyListScreen {
                    navController.navigate(Route.StudyDetail)
                }
            }
            composable<Route.StudyDetail> { StudyDetailScreen() }
        }

        // 챗(대화) 그래프
        navigation<Route.ChatGraph>(startDestination = Route.Chat) {
            composable<Route.Chat> { ChatScreen() }
        }

        // 교정 그래프
        navigation<Route.FeedbackGraph>(startDestination = Route.FeedbackList) {
            composable<Route.FeedbackList> {
                FeedbackListScreen {
                    navController.navigate(Route.StudyDetail)
                }
            }
            composable<Route.FeedbackDetail> { FeedbackDetailScreen() }
        }
    }
}