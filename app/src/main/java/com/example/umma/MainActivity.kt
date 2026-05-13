package com.example.umma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.umma.core.navigation.Route
import com.example.umma.core.navigation.UmmaBottomAppBar
import com.example.umma.core.navigation.UmmaNavHost
import com.example.umma.core.theme.UmmaTheme
import com.example.umma.core.ui.component.UmmaAppBar
import dagger.hilt.android.AndroidEntryPoint

/**
 * 애플리케이션의 메인 액티비티입니다.
 *
 * [UmmaTheme]을 적용하고 메인 UI 구조인 [UmmaApp]을 설정합니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UmmaTheme {
                UmmaApp()
            }
        }
    }
}


/**
 * 앱의 루트 컴포저블로, 전체적인 Scaffold 구조와 네비게이션을 관리합니다.
 *
 * 특정 경로([Route.Analytics], [Route.Chat], [Route.FeedbackList], [Route.StudyList], [Route.Dashboard])
 * 에서는 하단 앱 바를 표시하며, [UmmaNavHost]를 통해 화면 전환을 처리합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmmaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Analytics>()
                || it.hasRoute<Route.Chat>()
                || it.hasRoute<Route.FeedbackList>()
                || it.hasRoute<Route.StudyList>()
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) UmmaBottomAppBar(navController = navController)
        }
    ) { innerPadding ->
        UmmaNavHost(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
        )
    }
}