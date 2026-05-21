package com.example.umma.presentation.dashboard.component

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * Dashboard 카드 공용 유틸.
 *
 * 적용 범위: DASH-002 ~ DASH-005 카드 4 종 공통 throttle.
 */

/**
 * 카드 네비게이션 클릭 throttle.
 *
 * DASH-002 AC 8 / DASH-003~005 공통:
 *   "카드 클릭 중 중복 Navigation 이 방지된다."
 *
 * navigate() 호출 직후 화면이 실제로 떠나기 전 짧은 사이 발생하는 연타를
 * 무력화. 500ms 면 사용자 연타(약 200~300ms 간격) 와 비동기 navigate 사이
 * 의 공백을 모두 덮는다.
 *
 * 화면 복귀 후엔 새 composition 에서 lastClickMs 가 0 으로 초기화될 수 있으나
 * 의도된 동작 — 진짜 다음 클릭은 정상적으로 통과해야 한다.
 *
 * @return 원본 [onClick] 을 throttle 로 감싼 클릭 람다.
 */
@Composable
internal fun rememberDashboardCardClick(onClick: () -> Unit): () -> Unit {
    // 마지막 클릭 시각(ms). recompose 가 일어나도 값 유지.
    val lastClickMs = remember { mutableLongStateOf(0L) }
    // onClick 람다가 바뀌면 새 throttle 함수를 만든다.
    return remember(onClick) {
        {
            val now = System.currentTimeMillis()
            if (now - lastClickMs.longValue >= NAV_THROTTLE_MS) {
                lastClickMs.longValue = now
                onClick()
            }
            // throttle 윈도우 안의 호출은 silently drop — 사용자에겐 첫 클릭이
            // 이미 받아들여진 것으로 보임.
            else
                Log.d("DashCardThrottle", "drop — throttle window")
        }
    }
}

/** 네비 throttle 윈도우. */
private const val NAV_THROTTLE_MS = 500L
