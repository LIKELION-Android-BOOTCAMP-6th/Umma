package com.app.umma.presentation.chat.component

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Voice character drawing과 motion이 공유하는 내부 모델과 디자인 토큰입니다.
 *
 * public API로 노출할 값은 아니지만, 캐릭터 진입점/상태 정책/Canvas drawing을 여러 파일로
 * 나눠도 같은 패키지 안에서는 동일한 값을 재사용해야 하므로 `internal`로 둡니다.
 */
internal data class RingRotations(
    val orange: Float,
    val pink: Float,
    val blue: Float,
    val purple: Float
)

internal data class BounceSpec(
    val downLift: Float,
    val upLift: Float,
    val fallMillis: Int,
    val contactMillis: Long,
    val reboundMillis: Int,
    val settleMillis: Int,
    val restMillis: Long
)

internal enum class VoiceGazeDirection(
    val xOffset: Float,
    val yOffset: Float
) {
    Center(0f, 0f),
    Left(-1f, 0f),
    Right(1f, 0f),
    UpLeft(-1f, -1f),
    UpRight(1f, -1f)
}

internal data class VoiceCharacterAnimation(
    val orbScale: Float,
    val orbLift: Float,
    val orbSquashStretch: Offset,
    val eyeOffset: Offset,
    val blinkProgress: Float,
    val glowBoost: Float,
    val waveformPhase: Float,
    val orangeRingAlpha: Float,
    val pinkRingAlpha: Float,
    val blueRingAlpha: Float,
    val purpleRingAlpha: Float,
    val ringGlowMultiplier: Float,
    val disabledAlpha: Float
)

internal class VoiceCharacterDrawCache {
    val blurPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val arcRect: RectF = RectF()
    private val blurFilters: MutableMap<Int, BlurMaskFilter> = mutableMapOf()

    fun blurFilter(radius: Float): BlurMaskFilter {
        // BlurMaskFilter 생성은 Canvas가 매 프레임 돌 때 눈에 띄는 할당 지점이 된다.
        // 0.1px 단위로 반경을 양자화하면 시각 차이는 거의 없고, 같은 blur 효과를 재사용할 수 있다.
        val key = (radius.coerceAtLeast(0f) * 10f).roundToInt()
        return blurFilters.getOrPut(key) {
            BlurMaskFilter(key / 10f, BlurMaskFilter.Blur.NORMAL)
        }
    }
}

internal const val VoiceCharacterMaxJumpRatio = 1.15f
internal const val GroundNeutralLiftRatio = 0.7f
internal const val GroundContactRadiusOffset = 0.78f
internal val MeterHeightRatios = floatArrayOf(0.3f, 0.65f, 1f, 0.65f, 0.3f)

internal fun Float.wrapDegrees(): Float {
    val wrapped = this % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

internal val VoiceOrange = Color(0xFFF4901E)
internal val VoicePink = Color(0xFFFF6FAE)
internal val VoicePurple = Color(0xFF7B4C9E)
internal val VoiceBlue = Color(0xFF4A7DFF)
internal val VoiceCartoonTeal = Color(0xFF08746F)
internal val VoiceCartoonStroke = Color(0xFF005A55)
