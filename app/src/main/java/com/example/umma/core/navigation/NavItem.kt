package com.example.umma.core.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.umma.R

sealed class NavItem(
    val route: Route,
    val label: String,
    @field:DrawableRes val iconRes: Int
) {
    // 학습 탭
    object Study: NavItem(
        route = Route.StudyList,
        label = "학습",
        iconRes = R.drawable.import_contacts_24
    )

    // 홈 탭
    object Dashboard: NavItem(
        route = Route.Dashboard,
        label = "홈",
        iconRes = R.drawable.home_24
    )

    // 교정 탭
    object Feedback: NavItem(
        route = Route.FeedbackList,
        label = "교정",
        iconRes = R.drawable.wand_shine_24
    )

    // 챗 탭
    object Chat: NavItem(
        route = Route.Chat,
        label = "대화",
        iconRes = R.drawable.record_voice_over_24
    )

    // 통계 탭
    object Analytics: NavItem(
        route = Route.Analytics,
        label = "통계",
        iconRes = R.drawable.leaderboard_24
    )

    companion object {
        val list = listOf(
            Chat,
            Feedback,
            Dashboard,
            Study,
            Analytics,
        )
    }
}