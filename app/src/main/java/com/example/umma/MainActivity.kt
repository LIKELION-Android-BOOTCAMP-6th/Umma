package com.example.umma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.umma.core.navigation.Route
import com.example.umma.presentation.chat.ChatScreen
import com.example.umma.core.theme.UmmaTheme
import com.example.umma.presentation.HomeScreen
import com.example.umma.presentation.OnBoardingScreen
import com.example.umma.presentation.auth.SignInScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UmmaTheme {
                ChatScreen()
            }
        }
    }
}

@Composable
fun UmmaNavGraph() {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = Route.OnBoarding
    ) {

        // 1. 온보딩
        composable<Route.OnBoarding> {
            OnBoardingScreen(
                onNavigateToSignIn = { rootNavController.navigate(Route.SignIn) }
            )
        }

        // 2. 로그인
        composable<Route.SignIn> {
            SignInScreen(
                onNavigateToHome = { rootNavController.navigate(Route.Home) }
            )
        }

        // 3. 대시보드
        composable<Route.Home> {
            HomeScreen(
                onNavigateTo = { route ->
                    rootNavController.navigate(route)
                }
            )
        }

    }
}

@Composable
fun UmmaSubGraph() {
    val childNavController = rememberNavController()
    val navBackStackEntry = childNavController.currentBackStackEntry
    val currentDestination = navBackStackEntry?.destination
}