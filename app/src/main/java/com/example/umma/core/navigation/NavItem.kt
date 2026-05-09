package com.example.umma.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavItem(
    val route: Route,
    val label: String,
    val icon: ImageVector
) {
    // 학습 탭
    object Study: NavItem(
        route = Route.StudyList,
        label = "학습",
        icon = Icons.Default.Book
    )

    // 홈 탭
    object Home: NavItem(
        route = Route.Home,
        label = "홈",
        icon = Icons.Default.Home
    )

    // 교정 탭
    object Feedback: NavItem(
        route = Route.FeedbackList,
        label = "피드백",
        icon = Icons.Default.Edit
    )

    // 챗 탭
    object Chat: NavItem(
        route = Route.Chat,
        label = "챗",
        icon = Icons.Default.Call
    )

    // 통계 탭
    object Analytics: NavItem(
        route = Route.Analytics,
        label = "통계",
        icon = Icons.Default.Analytics
    )

    companion object {
        val list = listOf(
            Home,
            Analytics,
            Chat,
            Feedback,
            Study
        )
    }
}