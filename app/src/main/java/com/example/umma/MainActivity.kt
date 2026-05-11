package com.example.umma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
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
import dagger.hilt.android.AndroidEntryPoint

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


// Root Scaffold
@Composable
fun UmmaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Analytics>()
                || it.hasRoute<Route.Chat>()
                || it.hasRoute<Route.FeedbackList>()
                || it.hasRoute<Route.StudyList>()
        //      || it.hasRoute<Route.FeedbackDetail>()
        //      || it.hasRoute<Route.StudyDetail>()
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