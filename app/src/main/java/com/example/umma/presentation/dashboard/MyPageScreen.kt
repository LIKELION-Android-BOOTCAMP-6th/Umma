package com.example.umma.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.TextLogout
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.ui.component.UmmaDialog
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.presentation.auth.AuthViewModel

/**
 * 마이페이지 화면을 구성하는 컴포저블입니다.
 *
 * 사용자 프로필 정보 및 앱 설정을 관리할 수 있는 인터페이스를 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onNavigateToOnBoarding: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showNativeLanguageDialog by remember { mutableStateOf(false) }
    var selectedNativeLanguage by remember { mutableStateOf(LangCode.KO) }
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "마이페이지",
                isCenterTitle = true,
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)


        ) {
            if (showNativeLanguageDialog) {
                UmmaDialog(
                    title = "모국어 선택",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    onCancel = {
                        showNativeLanguageDialog = false
                    },
                    onConfirm = {
                        showNativeLanguageDialog = false
                    },
                    confirmText = "완료"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {

                        nativeLanguageOptions.forEach { (code, label) ->
                            val isSelected = (selectedNativeLanguage == code)
                            LanguageButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { selectedNativeLanguage = code }
                            )
                        }
                    }
                }
            }

            if (showLogoutDialog) {
                UmmaDialog(
                    title = "로그아웃 하시겠습니까?",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    confirmText = "확인",
                    onConfirm = {
                        showLogoutDialog = false
                        authViewModel.signOut()
                        onNavigateToOnBoarding()
                    },
                    onCancel = { showLogoutDialog = false }) {
                    Text(
                        text = "로그아웃 시 서비스 이용을 위해 다시 로그인해야 합니다."
                    )
                }
            }
            //
            Button(
                onClick = { showNativeLanguageDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextLogout
                ),
                border = BorderStroke(1.dp, TextLogout),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(text = "모국어 설정")
            }

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextLogout
                ),
                border = BorderStroke(1.dp, TextLogout),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(text = "Logout")
            }
        }
    }
}

/**
 * 다이얼로그에 학습 언어 리스트에 사용되는 버튼
 */
@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp)
    )
    {
        Text(
            text = text,
            fontSize = 16.sp,
            color = if (isSelected) ThemePrimary else TextPrimary
        )
    }
}

private val nativeLanguageOptions = listOf(
    LangCode.KO to "한국어",
    LangCode.EN to "English",
    LangCode.JA to "日本語",
    LangCode.ES to "Español"
)