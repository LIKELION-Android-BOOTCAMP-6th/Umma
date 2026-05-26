package com.app.umma.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.app.umma.R

/**
 * Pretendard 글꼴 설정을 위한 FontFamily 정의입니다.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_semi_bold, FontWeight.SemiBold),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_regular, FontWeight.Normal)
)

/**
 * TextStyle 정의입니다. UI에서 직접 참조하여 사용할 수 있습니다.
 */
val TitleB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 28.sp,
    fontWeight = FontWeight.Bold,
)

val PercentageDialogB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold,
)

val ButtonScreenB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
)

val TitleDialogSB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 26.sp,
    fontWeight = FontWeight.SemiBold,
)

val TitleScreenSB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 24.sp,
    fontWeight = FontWeight.SemiBold,
)

val ButtonDialogSB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 16.sp,
    fontWeight = FontWeight.SemiBold
)

val TextCorrectionSB = TextStyle(
    fontFamily = Pretendard,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold
)

val TextCardR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold
)

val TitleCardR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 24.sp,
    fontWeight = FontWeight.Normal
)

val TextPrimaryR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 20.sp,
    fontWeight = FontWeight.Normal
)

val TextSecondaryR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal
)

val TextExplanationR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal
)

val TextAnalysisR = TextStyle(
    fontFamily = Pretendard,
    fontSize = 12.sp,
    fontWeight = FontWeight.Normal
)

val TextDialogM = TextStyle(
    fontFamily = Pretendard,
    fontSize = 24.sp,
    fontWeight = FontWeight.Medium
)

val TextCheckboxM = TextStyle(
    fontFamily = Pretendard,
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium
)
val Typography = Typography()