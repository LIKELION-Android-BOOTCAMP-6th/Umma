package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.umma.domain.model.learningstate.LangCode

/**
 * 학습 언어 selector.
 *
 * SSOT: DASH-006_Language_Selector.md
 *
 * Phase 1 구현 범위:
 *  - 현재 선택 언어 표시 (AssistChip, "EN ▼" 형태)
 *  - DropdownMenu 로 learningLanguages 목록 노출
 *  - 항목 클릭 시 [onLanguageSelected] 콜백 호출
 *  - 콜백 본 구현은 Phase 2 (ViewModel.onChangeLearningLanguage) 에서 채움
 *
 * 후속 Phase:
 *  - 클릭 시 실제 selectedLearningLanguage 갱신 + DashSummary fetch
 *  - isLoading=true 일 때 칩 비활성화 (중복 방지 시각화)
 *
 * @param selectedLang 현재 선택된 학습 언어. 호출자가 non-null 보장한 상태에서 사용.
 * @param learningLangs 학습 중인 언어 목록. 보통 2~3 개.
 * @param isLoading 변경 진행 중 여부. true 면 칩 클릭 / 메뉴 진입 차단.
// *                  Phase 1 에선 초기 preload 의 isLoading 을 그대로 매핑.
// *                  후속 Phase 에서 isChangingLanguage 로 별도화 예정.
 * @param onLanguageSelected 항목 클릭 콜백. ViewModel.onChangeLearningLanguage 로 연결.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningLanguageSelector(
    selectedLang: LangCode,
    learningLangs: List<LangCode>,
    isLoading: Boolean,
    onLanguageSelected: (LangCode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dropdown 메뉴 열림 여부. 칩 탭 시 true, 항목 선택 또는 외부 탭 시 false.
    //   remember 로 recomposition 사이에 값 보존.
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { if (!isLoading) expanded = true },
            label = { Text(selectedLang.displayLabel()) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            },
            enabled = !isLoading
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            learningLangs.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayLabel()) },
                    onClick = {
                        expanded = false
                        onLanguageSelected(lang)
                    },
                    leadingIcon = if (lang == selectedLang) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * LangCode → 사용자에게 보일 짧은 라벨.
 *
 * TODO: LangCode 모델 측에 정식 displayName/displayLabel 프로퍼티가 있으면 그걸로 교체.
 *       우선은 code 를 대문자화해서 표시 ("en" → "EN", "ja" → "JA").
 */
private fun LangCode.displayLabel(): String = this.code.uppercase()