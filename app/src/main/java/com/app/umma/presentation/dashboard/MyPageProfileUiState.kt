package com.app.umma.presentation.dashboard

import com.app.umma.domain.model.learningstate.LangCode

/**
 * 마이페이지 프로필 영역 UI 상태.
 * 사용자 언어만 추후 닉네임, 프로필 이미지 등 추가
 */
data class MyPageProfileUiState(
    val primaryLang: LangCode = LangCode.KO,
    val isSaving: Boolean = false,
    val message: String? = null,
)
