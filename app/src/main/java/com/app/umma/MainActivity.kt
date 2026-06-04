package com.app.umma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.umma.core.navigation.Route
import com.app.umma.core.navigation.UmmaBottomAppBar
import com.app.umma.core.navigation.UmmaNavHost
import com.app.umma.core.theme.UmmaTheme
import com.app.umma.data.push.UmmaFirebaseMessagingService
import com.app.umma.domain.usecase.notification.RefreshNotificationTimezoneUseCase
import com.app.umma.domain.usecase.notification.SyncCurrentNotificationDeviceUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * 앱 메인 액티비티.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TimeZone 초기화 변수
    @Inject
    lateinit var refreshNotificationTimezoneUseCase: RefreshNotificationTimezoneUseCase

    // Firestore 동기화
    @Inject
    lateinit var syncCurrentNotificationDeviceUseCase: SyncCurrentNotificationDeviceUseCase

    // 탭 화면 route Intent 발생 변수
    private var pendingNotificationTarget by mutableStateOf<NotificationNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingNotificationTarget = intent.toNotificationNavigationTarget()
        enableEdgeToEdge()
        UmmaFirebaseMessagingService.ensureNotificationChannels(this)
        lifecycleScope.launch {
            val timezone = TimeZone.getDefault().id
            refreshNotificationTimezoneUseCase(timezone)
            syncCurrentNotificationDeviceUseCase(
                permissionGranted = hasNotificationPermission(),
                timezone = timezone
            )
        }

        setContent {
            UmmaTheme {
                UmmaApp(
                    pendingNotificationTarget = pendingNotificationTarget,
                    onPendingNotificationConsumed = { pendingNotificationTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationTarget = intent.toNotificationNavigationTarget()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 앱 루트 Scaffold 와 알림 탭 라우팅을 관리하는 Composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UmmaApp(
    pendingNotificationTarget: NotificationNavigationTarget? = null,
    onPendingNotificationConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentOnConsumed by rememberUpdatedState(onPendingNotificationConsumed)
    val showBottomBar = navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Statistics>() ||
                it.hasRoute<Route.Chat>() ||
                it.hasRoute<Route.CorrectionList>() ||
                it.hasRoute<Route.SrsStudy>()
    } == true

    LaunchedEffect(pendingNotificationTarget, navBackStackEntry) {
        val target = pendingNotificationTarget ?: return@LaunchedEffect
        val currentDestination = navBackStackEntry?.destination ?: return@LaunchedEffect
        val isAuthenticatedGraph = currentDestination.hierarchy.any {
            it.hasRoute<Route.HomeGraph>() ||
                    it.hasRoute<Route.Dashboard>() ||
                    it.hasRoute<Route.MyPage>() ||
                    it.hasRoute<Route.Chat>() ||
                    it.hasRoute<Route.CorrectionList>() ||
                    it.hasRoute<Route.Statistics>() ||
                    it.hasRoute<Route.SrsStudy>()
        }
        if (!isAuthenticatedGraph) return@LaunchedEffect

        when (target.route) {
            SRS_NOTIFICATION_ROUTE -> {
                navController.navigate(Route.SrsStudy) {
                    launchSingleTop = true
                }
                currentOnConsumed()
            }

            MARKETING_NOTIFICATION_ROUTE -> {
                navController.navigate(Route.Dashboard) {
                    launchSingleTop = true
                }
                currentOnConsumed()
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                UmmaBottomAppBar(navController = navController)
            }
        }
    ) { innerPadding ->
        UmmaNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

private data class NotificationNavigationTarget(
    val type: String,
    val route: String,
    val lang: String?,
    val historyId: String?
)

private const val SRS_NOTIFICATION_ROUTE = "srs_study"
private const val MARKETING_NOTIFICATION_ROUTE = "home"

private fun Intent?.toNotificationNavigationTarget(): NotificationNavigationTarget? {
    if (this == null) return null
    val type = getStringExtra(UmmaFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE) ?: return null
    val route = getStringExtra(UmmaFirebaseMessagingService.EXTRA_NOTIFICATION_ROUTE) ?: return null
    return NotificationNavigationTarget(
        type = type,
        route = route,
        lang = getStringExtra(UmmaFirebaseMessagingService.EXTRA_NOTIFICATION_LANG),
        historyId = getStringExtra(UmmaFirebaseMessagingService.EXTRA_NOTIFICATION_HISTORY_ID)
    )
}
