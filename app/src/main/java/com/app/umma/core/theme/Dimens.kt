package com.app.umma.core.theme

import androidx.compose.ui.unit.dp

/**
 * 앱 전반에서 사용되는 dimension 토큰.
 *
 * 텍스트 크기(sp) 는 [Type.kt] 에서 관리하고,
 * 레이아웃 / 컴포넌트 크기 및 간격(dp) 은 여기서 관리한다.
 *
 * 명명 규칙:
 *  - Spacing___ : 간격 / 패딩 (XS=4 / S=8 / M=12 / L=16 / XL=24 / XXL=40)
 *  - IconSize___ : 아이콘 크기 (Small=24 / Medium=30 / Large=72)
 *  - 그 외는 의미 이름 (CardCornerRadius, BadgeDotSize 등)
 */

// === 간격 ===
val SpacingXS = 4.dp
val SpacingS = 8.dp
val SpacingM = 12.dp
val SpacingL = 16.dp
val SpacingXL = 24.dp
val SpacingXXL = 40.dp

// === 아이콘 ===
/** 인라인 아이콘(상단 바, 칩 옆 등) */
val IconSizeSmall = 24.dp
/** 다이얼로그 취소 버튼 등 중간 아이콘 */
val IconSizeMedium = 30.dp
/** 카드 중앙 대표 아이콘 */
val IconSizeLarge = 72.dp

// === 카드 ===
val CardCornerRadius = 16.dp
val CardElevation = 2.dp

// === 칩 / 배지 ===
val ChipCornerRadius = 12.dp
val ChipPaddingHorizontal = SpacingS  // 8.dp
val ChipPaddingVertical = SpacingXS   // 4.dp
/** 우상단 알림 점(교정 카드 등) */
val BadgeDotSize = 10.dp