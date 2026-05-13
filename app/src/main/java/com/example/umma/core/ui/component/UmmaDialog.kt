package com.example.umma.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.ButtonDialogSB
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.theme.TitleDialogSB

// 움마 공통 다이얼로그 위젯
@Composable
fun UmmaDialog(
    title: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "확인",
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = { }) {
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
                // 상단 취소 버튼
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { onCancel() },
                        modifier = Modifier.align(Alignment.TopEnd )
                    ) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = "취소 버튼",
                            modifier = Modifier.size(30.dp),
                            tint = TextPrimary,
                        )
                    }
                }
                // 다이얼로그 타이틀
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = TitleDialogSB,
                        color = TextPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                //내부 컨텐츠
                content()
                // 하단 버튼
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(92.dp)
                        .height(48.dp)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary)
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

