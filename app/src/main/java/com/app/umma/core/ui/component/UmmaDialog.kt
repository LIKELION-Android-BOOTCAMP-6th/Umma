package com.app.umma.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.ButtonDialogSB
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleDialogSB

/**
 * 앱 전반에서 공통으로 사용되는 다이얼로그 컴포저블입니다.
 *
 * 상단 우측의 취소(X) 버튼, 중앙 정렬 타이틀, 커스텀 콘텐츠 영역,
 * 하단 확인 버튼으로 구성됩니다. 기본값은 기존 호출부와 동일하게 취소 버튼과
 * back/outside dismiss 를 허용하고, 필수 선택 흐름에서는 옵션으로 닫기 동작을 막습니다.
 *
 * 사용 예시:
 * ```
 * UmmaDialog(
 *     title = "알림",
 *     onCancel = { showDialog = false },
 *     onConfirm = { showDialog = false },
 *     confirmText = "확인"
 * ) {
 *     Text(text = "정말 삭제하시겠습니까?")
 * }
 * ```
 *
 * @param title 다이얼로그 상단 중앙에 표시할 제목 문자열.
 * @param modifier 다이얼로그 [Surface]에 적용할 [Modifier].
 * @param onCancel 우측 상단 취소(X) 버튼 클릭 시 호출되는 콜백.
 * @param onConfirm 하단 확인 버튼 클릭 시 호출되는 콜백.
 * @param confirmText 하단 확인 버튼에 표시할 텍스트. 기본값은 `"확인"`.
 * @param showCancelButton 우측 상단 취소 버튼 표시 여부. 필수 선택 다이얼로그는 false 로 숨긴다.
 * @param dismissOnBackPress 뒤로가기로 다이얼로그를 닫을 수 있는지 여부.
 * @param dismissOnClickOutside 외부 터치로 다이얼로그를 닫을 수 있는지 여부.
 * @param confirmEnabled 하단 확인 버튼 활성화 여부. 저장 중 중복 요청 방지에 사용한다.
 * @param content 타이틀과 확인 버튼 사이에 삽입할 커스텀 컴포저블 블록 ([ColumnScope] 내부).
 */
@Composable
fun UmmaDialog(
    title: String,
    titleColor: Color? = null,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "확인",
    confirmButtonColor: Color? = null,
    showCancelButton: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // DialogProperties 로 back/outside dismiss 정책을 분리해 화면별 필수 입력 정책을 제어한다.
    Dialog(
        // dismiss 가 허용된 일반 다이얼로그에서는 기존처럼 onCancel 로 상태를 닫는다.
        onDismissRequest = { onCancel() },
        properties = DialogProperties(
            // 관심주제 선택처럼 우회가 없어야 하는 화면은 false 를 전달한다.
            dismissOnBackPress = dismissOnBackPress,
            // 외부 터치 닫기도 화면 정책에 따라 별도로 막을 수 있게 한다.
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(30.dp),
            color = BackgroundPrimary,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 필수 선택 흐름에서는 취소 버튼 자체를 숨겨 사용자가 설정 단계를 우회하지 못하게 한다.
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 기본값은 true 라서 기존 다이얼로그 호출부의 UX는 유지된다.
                    if (showCancelButton) {
                        IconButton(
                            onClick = { onCancel() },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Outlined.Cancel,
                                contentDescription = "취소 버튼",
                                modifier = Modifier.size(30.dp),
                                tint = TextPrimary,
                            )
                        }
                    }
                }
                // 다이얼로그 타이틀
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = TitleDialogSB,
                        color = titleColor ?: TextPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                // 호출 화면이 선택 목록, 안내 문구 같은 실제 본문을 주입한다.
                content()
                Spacer(modifier = Modifier.height(24.dp))

                // confirmEnabled 로 저장 중 중복 클릭이나 필수 조건 미충족 상태를 공통 버튼에서 막는다.
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier
                        .width(92.dp)
                        .heightIn(min = 48.dp)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = confirmButtonColor ?: ThemePrimary)
                ) {
                    Text(
                        text = confirmText,
                        style = ButtonDialogSB
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, )
@Composable
fun UmmaPreview() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
        contentAlignment = Alignment.Center
    ) {
        UmmaDialog(
            title = "학습 언어 선택",
            onCancel = { print("") },
            onConfirm = { },
            modifier = Modifier.padding(horizontal = 32.dp),
            confirmText = "확인",
        ) {

            val languages = listOf("영어", "한국어", "일본어", "중국어", "독일어")
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "하이",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
