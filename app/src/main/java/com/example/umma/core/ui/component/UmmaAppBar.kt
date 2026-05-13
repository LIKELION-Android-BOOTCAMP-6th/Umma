package com.example.umma.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TitleColor
import com.example.umma.core.theme.TitleScreenSB

/**
 * 앱 전반에서 공통으로 사용되는 상단 앱 바 컴포저블입니다.
 *
 * 타이틀 정렬 방식(좌측/중앙)에 따라 [TopAppBar] 또는 [CenterAlignedTopAppBar]를 렌더링하며,
 * 뒤로가기 버튼과 우측 액션 버튼을 선택적으로 포함할 수 있습니다.
 *
 * 사용 예시:
 * ```
 * UmmaAppBar(
 *     title = "홈",
 *     isCenterTitle = true,
 *     onBackClick = { navController.popBackStack() },
 *     actions = {
 *         IconButton(onClick = { /* 검색 */ }) {
 *             Icon(Icons.Default.Search, contentDescription = "검색")
 *         }
 *     }
 * )
 * ```
 *
 * @param modifier 이 컴포저블에 적용할 [Modifier].
 * @param title 앱 바에 표시할 제목 문자열.
 * @param isCenterTitle `true`이면 제목을 중앙 정렬([CenterAlignedTopAppBar]),
 *   `false`이면 좌측 정렬([TopAppBar]). 기본값은 `false`.
 * @param scrollBehavior 스크롤 연동 동작을 정의하는 [TopAppBarScrollBehavior].
 *   `null`이면 스크롤에 반응하지 않음. 기본값은 `null`.
 * @param actions 앱 바 우측에 배치할 액션 아이콘 컴포저블 블록 ([RowScope] 내부).
 *   기본값은 빈 블록.
 * @param onBackClick 뒤로가기 버튼 클릭 시 호출되는 콜백.
 *   `null`이면 뒤로가기 버튼이 표시되지 않음. 기본값은 `null`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmmaAppBar(
    modifier: Modifier = Modifier,
    title: String,
    isCenterTitle: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = BackgroundPrimary
    )
    val titleContent = @Composable {
        Text(
            text = title,
            style = TitleScreenSB, 
            color = TitleColor
        )
    }

    val navigationIcon = @Composable {
        if (onBackClick != null) {
            IconButton(
                onClick = onBackClick
            ) {
                Icon(
                    Icons.Default.ArrowBackIosNew,
                    contentDescription = "뒤로가기",
                    tint = TextPrimary
                )
            }
        }
    }

    if (isCenterTitle) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            windowInsets = WindowInsets(0.dp),
            title = titleContent,
            scrollBehavior = scrollBehavior,
            colors = appBarColors,
            navigationIcon = navigationIcon,
            actions = actions
        )
    } else {
        TopAppBar(
            modifier = modifier,
            windowInsets = WindowInsets(0.dp),
            title = titleContent,
            scrollBehavior = scrollBehavior,
            colors = appBarColors,
            navigationIcon = navigationIcon,
            actions = actions
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppBarPreview() {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "마이프로필",
                isCenterTitle = true,
                onBackClick = { println() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) { }

    }
}