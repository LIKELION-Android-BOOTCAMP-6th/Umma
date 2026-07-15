package com.app.umma.core.navigation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.ThemeSecondary

@Composable
fun UmmaBottomAppBar(
    navController: NavHostController, modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = BackgroundSecondary,
        modifier = Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = Color(0xFFDBC2AF),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
    ) {
        NavItem.list.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any {
                it.hasRoute(item.route::class) ||
                    // 학습 탭은 하위 화면(SrsCardList)에서도 선택 유지되도록 그래프로 체크
                    (item.route is Route.SrsStudy && it.hasRoute(Route.SrsStudyGraph::class))
            } == true

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        if (item.route is Route.Dashboard) {
                            navController.popBackStack(route = Route.Dashboard, inclusive = false)
                        } else {
                            navController.navigate(item.route) {
                                popUpTo<Route.Dashboard> {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                colors = NavigationBarItemColors(
                    selectedIconColor = BackgroundSecondary,
                    selectedTextColor = ThemePrimary,
                    selectedIndicatorColor = ThemePrimary,
                    unselectedIconColor = ThemeSecondary,
                    unselectedTextColor = ThemeSecondary,
                    disabledTextColor = ThemeSecondary,
                    disabledIconColor = ThemeSecondary
                ),
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.label,
                        modifier = Modifier.size(30.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, backgroundColor = 0xFFF8F2E5)
@Composable
fun UmmaNavigationBarPreview() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = BackgroundPrimary,
        // Scaffold의 bottomBar 파라미터를 사용하면 자동으로 하단에 고정됩니다.
        bottomBar = {
            NavigationBar(
                containerColor = BackgroundSecondary,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color(0xFFDBC2AF),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            ) {
                NavItem.list.forEachIndexed { index, item ->
                    val isSelected = index == 3
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { },
                        colors = NavigationBarItemColors(
                            selectedIconColor = BackgroundSecondary,
                            selectedTextColor = ThemePrimary,
                            selectedIndicatorColor = ThemePrimary,
                            unselectedIconColor = ThemeSecondary,
                            unselectedTextColor = ThemeSecondary,
                            disabledTextColor = ThemeSecondary,
                            disabledIconColor = ThemeSecondary
                        ),
                        icon = {
                                Icon(
                                    painter = painterResource(id = item.iconRes),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(30.dp)
                                )
                        },
                        label = { Text(text = item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // 하단 바를 제외한 나머지 화면 영역
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize())
    }
}

