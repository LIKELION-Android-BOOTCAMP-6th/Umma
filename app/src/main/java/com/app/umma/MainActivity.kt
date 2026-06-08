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
import com.app.umma.watchbridge.WatchChatRuntimeCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * 앱 메인 액티비티.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_ROUTE = "extra_open_route"
        const val OPEN_ROUTE_CHAT = "chat"
    }

    // TimeZone 초기화 변수
    @Inject
    lateinit var refreshNotificationTimezoneUseCase: RefreshNotificationTimezoneUseCase

    // Firestore 동기화
    @Inject
    lateinit var syncCurrentNotificationDeviceUseCase: SyncCurrentNotificationDeviceUseCase

    @Inject
    lateinit var watchChatRuntimeCoordinator: WatchChatRuntimeCoordinator

    // 탭 화면 route Intent 발생 변수
    private var pendingNotificationTarget by mutableStateOf<NotificationNavigationTarget?>(null)
    private var pendingOpenRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingNotificationTarget = intent.toNotificationNavigationTarget()
        pendingOpenRoute = intent.toOpenRoute()
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
                    onPendingNotificationConsumed = { pendingNotificationTarget = null },
                    pendingOpenRoute = pendingOpenRoute,
                    onPendingOpenRouteConsumed = { pendingOpenRoute = null },
                    onChatRouteVisibilityChanged = watchChatRuntimeCoordinator::setChatRouteVisible
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationTarget = intent.toNotificationNavigationTarget()
        pendingOpenRoute = intent.toOpenRoute()
    }

    override fun onStart() {
        super.onStart()
        watchChatRuntimeCoordinator.setAppForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            watchChatRuntimeCoordinator.setAppForeground(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            watchChatRuntimeCoordinator.setChatRouteVisible(false)
        }
        super.onDestroy()
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
    onPendingNotificationConsumed: () -> Unit = {},
    pendingOpenRoute: String? = null,
    onPendingOpenRouteConsumed: () -> Unit = {},
    onChatRouteVisibilityChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentOnConsumed by rememberUpdatedState(onPendingNotificationConsumed)
    val isChatRouteVisible = navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Chat>()
    } == true
    val showBottomBar = navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Statistics>() ||
                it.hasRoute<Route.Chat>() ||
                it.hasRoute<Route.CorrectionList>() ||
                it.hasRoute<Route.SrsStudy>()
    } == true

    LaunchedEffect(isChatRouteVisible) {
        onChatRouteVisibilityChanged(isChatRouteVisible)
    }

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

    LaunchedEffect(pendingOpenRoute, navBackStackEntry) {
        val route = pendingOpenRoute ?: return@LaunchedEffect
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

        if (route == MainActivity.OPEN_ROUTE_CHAT) {
            navController.navigate(Route.Chat) {
                launchSingleTop = true
            }
            onPendingOpenRouteConsumed()
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

private fun Intent?.toOpenRoute(): String? {
    if (this == null) return null
    return getStringExtra(MainActivity.EXTRA_OPEN_ROUTE)
}
