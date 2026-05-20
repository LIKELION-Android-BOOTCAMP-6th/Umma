package com.example.umma.core.navigation

import androidx.annotation.DrawableRes
import com.example.umma.R

sealed class NavItem(
    val route: Route,
    val label: String,
    @field:DrawableRes val iconRes: Int
) {
    // SRS 반복학습 탭
    object SrsStudy: NavItem(
        route = Route.SrsStudy,
        label = "복습",
        iconRes = R.drawable.import_contacts_24
    )

    // 홈 탭
    object Dashboard: NavItem(
        route = Route.Dashboard,
        label = "홈",
        iconRes = R.drawable.home_24
    )

    // 교정 탭
    object Correction: NavItem(
        route = Route.CorrectionList,
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
            Correction,
            Dashboard,
            SrsStudy,
            Analytics,
        )
    }
}
