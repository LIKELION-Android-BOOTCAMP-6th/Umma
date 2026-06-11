package com.app.umma.core.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * 화면 단위 단일 네비게이션 가드.
 *
 * [rememberDashboardCardClick] 의 throttle 은 카드 컴포저블 인스턴스마다 독립적이라
 * "같은 카드 연타"만 차단하고, 서로 다른 두 카드의 "멀티터치 동시 탭"은 막지 못한다.
 * 이 가드는 source 화면 entry 가 RESUMED 일 때만 navigate 를 통과시켜,
 * 동시 탭 중 첫 탭만 수락한다.
 *
 * RESUMED 판정 근거: 첫 탭이 navigate() 를 호출하면 source entry 는 즉시
 * RESUMED 를 벗어난다(STARTED → CREATED). 따라서 같은 프레임에 들어온 두 번째 탭은
 * 이미 RESUMED 가 아니므로 차단된다. 화면 복귀로 entry 가 다시 RESUMED 되면
 * 다음 탭은 정상 통과한다.
 *
 * 책임 분리 — throttle: 같은 카드 연타 방지 / navigateSingle: 서로 다른 카드 동시 탭 방지.
 *
 * @param route 이동할 [Route].
 * @param builder launchSingleTop/popUpTo 등 기존 NavOptions 빌더. 기본값은 빈 옵션.
 */
internal fun NavHostController.navigateSingle(
    route: Route,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}
