package com.app.umma.presentation.chat.component

import androidx.compose.ui.geometry.Offset
import com.app.umma.domain.model.realtime.AIState
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chat UI state를 캐릭터가 이해하는 시각 상태와 motion 정책으로 바꾸는 파일입니다.
 *
 * ViewModel/Repository 정책을 알지 않고, presentation 컴포넌트가 받은 상태와 레벨만
 * 캐릭터용 값으로 변환합니다. 파일을 분리해도 동작은 기존과 같아야 하므로 기존 수치와
 * 분기 순서를 그대로 유지합니다.
 */
internal fun Float.toDesignLevel(active: Boolean): Float {
    val curved = sqrt(coerceIn(0f, 1f))
    return when {
        active -> curved.coerceAtLeast(0.3f)
        else -> curved.coerceAtLeast(0.2f)
    }
}

internal fun Float.toMeterLevel(active: Boolean): Float {
    val rawLevel = coerceIn(0f, 1f)
    return when {
        // 마이크가 켜져 있거나 AI가 말하는 상태라도 실제 음성 강도가 낮으면 막대는 작아야 한다.
        // sqrt 보정은 작은 실제 입력을 살짝 더 보이게만 하고, active minimum을 크게 강제하지 않는다.
        active -> sqrt(rawLevel)
        else -> rawLevel * 0.35f
    }
}

internal fun Float.toArcGlowLevel(active: Boolean): Float {
    val rawLevel = coerceIn(0f, 1f)
    return when {
        // ARC glow는 작은 소리와 큰 소리의 차이가 명확해야 한다.
        // pow(1.55)는 낮은 레벨을 더 낮게 눌러 작은 소리에서 빛번짐이 과하게 켜지는 것을 막는다.
        // 뒤의 multiplier는 큰 소리에서 glow가 충분히 치고 올라오도록 하는 보정값이다.
        active -> (rawLevel.pow(1.5f) * 1.3f).coerceIn(0f, 1f)
        else -> 0f
    }
}

internal fun resolveVoiceCharacterState(
    isRecording: Boolean,
    isAwaitingUserTranscript: Boolean,
    aiState: AIState,
    isAudioOutputPlaying: Boolean
): VoiceCharacterState {
    return when {
        // 연결 복구/오류 중에는 새 대화를 시작할 수 없으므로 Idle처럼 보이면 안 된다.
        // 하단 micStatusMessage의 "연결을 복구하고 있어요." 안내와 시각 상태가 어긋나지 않게 비활성 톤으로 묶는다.
        aiState == AIState.ERROR || aiState == AIState.RECONNECTING -> VoiceCharacterState.Disabled
        isRecording -> VoiceCharacterState.Listening
        isAwaitingUserTranscript || aiState == AIState.THINKING -> VoiceCharacterState.Thinking
        aiState == AIState.SPEAKING || isAudioOutputPlaying -> VoiceCharacterState.Speaking
        else -> VoiceCharacterState.Idle
    }
}

internal enum class VoiceCharacterState {
    Idle,
    Listening,
    Thinking,
    Speaking,
    Disabled
}

internal val VoiceCharacterState.ringSpeedMultiplier: Float
    get() = when (this) {
        // ARC 회전 속도 배율입니다.
        // 값을 키울수록 해당 상태에서 링이 더 바쁘게 돌고, 줄일수록 차분하게 회전합니다.
        // 실제 회전 각도는 이 배율에 링별 기본 속도(orange/pink/blue/purple)가 곱해져 계산됩니다.
        VoiceCharacterState.Idle -> 1f
        VoiceCharacterState.Listening -> 10f
        VoiceCharacterState.Thinking -> 3f
        VoiceCharacterState.Speaking -> 10f
        VoiceCharacterState.Disabled -> 0.5f
    }

internal val VoiceCharacterState.meterAlpha: Float
    get() = when (this) {
        // 양쪽 레벨미터의 기본 투명도입니다.
        // 듣기/말하기 상태를 가장 선명하게 두고, 대기/생각중/비활성 상태는 시각적 존재감만 남깁니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.4f
        VoiceCharacterState.Idle -> 0.35f
        VoiceCharacterState.Disabled -> 0.2f
    }

internal val VoiceCharacterState.meterGlowAlpha: Float
    get() = when (this) {
        // 레벨미터 주변 빛 번짐의 투명도입니다.
        // 값이 높을수록 음성 반응이 더 강하게 빛나는 것처럼 보입니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.3f
        VoiceCharacterState.Idle -> 0.2f
        VoiceCharacterState.Disabled -> 0.1f
    }

internal val VoiceCharacterState.orangeRingAlpha: Float
    get() = when (this) {
        // Orange ARC의 상태별 강조값입니다.
        // 기본값은 다른 ARC와 같은 기준으로 맞추고, 특정 색만 강조하고 싶을 때 여기만 조정합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

internal val VoiceCharacterState.pinkRingAlpha: Float
    get() = when (this) {
        // Pink ARC의 상태별 강조값입니다.
        // 현재는 Orange/Blue/Purple과 같은 기준을 사용해 색상별 의미 없는 밝기 차이를 제거합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

internal val VoiceCharacterState.blueRingAlpha: Float
    get() = when (this) {
        // Blue ARC의 상태별 강조값입니다.
        // AI 출력 색으로 더 강조하고 싶다면 Speaking 값만 별도로 올리면 됩니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

internal val VoiceCharacterState.purpleRingAlpha: Float
    get() = when (this) {
        // Purple ARC의 상태별 강조값입니다.
        // 전체 링 균형을 유지하기 위해 현재는 다른 ARC와 같은 값을 사용합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

internal val VoiceCharacterState.ringGlowMultiplier: Float
    get() = when (this) {
        // ARC 전체 빛 번짐의 강도입니다.
        // 이 값은 "상태 자체가 가진 기본 발광량"입니다.
        // Listening/Speaking은 가장 활성화된 상태라 기본 glow도 높게 두고,
        // Thinking은 Idle보다 살아 있지만 발화 상태보다는 차분하게 둡니다.
        // 실제 음성 크기에 따른 추가 발광은 arcGlowBoost에서 별도로 더합니다.
        VoiceCharacterState.Idle -> 0.2f
        VoiceCharacterState.Listening -> 0.45f
        VoiceCharacterState.Thinking -> 0.3f
        VoiceCharacterState.Speaking -> 0.45f
        VoiceCharacterState.Disabled -> 0.05f
    }

internal val VoiceCharacterState.eyeVerticalBias: Float
    get() = when (this) {
        // 눈 위치의 세로 보정값입니다.
        // Thinking 상태에서는 눈을 아주 살짝 위로 올려 "생각 중"인 표정을 만듭니다.
        VoiceCharacterState.Thinking -> -0.05f
        VoiceCharacterState.Disabled -> 0.05f
        else -> 0f
    }

internal val VoiceCharacterState.gazeDirections: List<VoiceGazeDirection>
    get() = when (this) {
        // Idle은 사용자를 기다리는 상태라 시선이 천천히 주변을 살펴보는 정도가 자연스럽다.
        VoiceCharacterState.Idle -> listOf(
            VoiceGazeDirection.Center,
            VoiceGazeDirection.Left,
            VoiceGazeDirection.UpRight,
            VoiceGazeDirection.Right
        )
        // Listening은 "듣고 있음"이 핵심이므로 시선을 크게 흔들지 않는다.
        VoiceCharacterState.Listening -> listOf(VoiceGazeDirection.Center)
        // Thinking은 사용자가 "응답 준비 중"이라고 느껴야 해서 위쪽 대각선 시선으로 생각하는 인상을 준다.
        VoiceCharacterState.Thinking -> listOf(
            VoiceGazeDirection.UpLeft,
            VoiceGazeDirection.Center,
            VoiceGazeDirection.UpRight
        )
        // Speaking은 AI가 말하는 중이라는 레벨 반응이 주 피드백이라 시선은 안정적으로 유지한다.
        VoiceCharacterState.Speaking -> listOf(VoiceGazeDirection.Center)
        // Disabled/Error는 추가 움직임을 줄여 상호작용 불가 상태가 흔들리지 않게 한다.
        VoiceCharacterState.Disabled -> listOf(VoiceGazeDirection.Center)
    }

internal val VoiceCharacterState.gazeInitialDelayMillis: Long
    get() = when (this) {
        // 상태가 바뀐 직후에는 ring/jump 변화가 먼저 읽혀야 하므로 시선 전환을 잠깐 늦춘다.
        VoiceCharacterState.Idle -> 900L
        VoiceCharacterState.Listening -> 1_100L
        VoiceCharacterState.Thinking -> 700L
        VoiceCharacterState.Speaking -> 1_000L
        VoiceCharacterState.Disabled -> 2_000L
    }

internal val VoiceCharacterState.gazeHoldMillis: Long
    get() = when (this) {
        // 말하거나 듣는 동안 시선이 자주 움직이면 음성 반응보다 산만해져 hold 시간을 길게 둔다.
        VoiceCharacterState.Idle -> 2_200L
        VoiceCharacterState.Listening -> 2_800L
        VoiceCharacterState.Thinking -> 1_700L
        VoiceCharacterState.Speaking -> 2_600L
        VoiceCharacterState.Disabled -> 4_000L
    }

internal val VoiceCharacterState.gazeHorizontalAmplitude: Float
    get() = when (this) {
        // eye offset은 bodyRadius에 곱해지므로 값이 커지면 눈이 orb 밖으로 밀려 보일 수 있다.
        // 이전 값은 실제 기기에서 눈동자 움직임이 거의 읽히지 않아 "고개를 돌리는" 느낌이 약했다.
        // 흰 눈이 orb 안에 남는 범위에서 이동 폭을 크게 키워 상태별 시선 방향을 명확히 보이게 한다.
        VoiceCharacterState.Idle -> 0.15f
        VoiceCharacterState.Listening -> 0f
        VoiceCharacterState.Thinking -> 0.14f
        VoiceCharacterState.Speaking -> 0f
        VoiceCharacterState.Disabled -> 0f
    }

internal val VoiceCharacterState.gazeVerticalAmplitude: Float
    get() = when (this) {
        // 대각선 시선이 "방향을 바라본다"로 읽히려면 세로 이동도 어느 정도 필요하다.
        // 다만 Listening/Speaking은 음성 반응이 주 피드백이라 Thinking보다 세로 폭을 낮게 둔다.
        VoiceCharacterState.Thinking -> 0.1f
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking,
        VoiceCharacterState.Disabled -> 0f
        else -> 0.06f
    }

internal val VoiceCharacterState.blinkInitialDelayMillis: Long
    get() = when (this) {
        // 시선 전환과 상태 motion 변화가 먼저 읽힌 뒤 blink가 나오도록 상태별 초기 지연을 둔다.
        VoiceCharacterState.Idle -> 1_200L
        VoiceCharacterState.Listening -> 1_800L
        VoiceCharacterState.Thinking -> 1_500L
        VoiceCharacterState.Speaking -> 1_900L
        VoiceCharacterState.Disabled -> 3_000L
    }

internal val VoiceCharacterState.blinkIntervalMillis: Long
    get() = when (this) {
        // Blink는 생동감 보조 요소다. Listening/Speaking에서는 음성 반응이 주 피드백이므로 빈도를 낮춘다.
        VoiceCharacterState.Idle -> 3_600L
        VoiceCharacterState.Listening -> 5_600L
        VoiceCharacterState.Thinking -> 4_400L
        VoiceCharacterState.Speaking -> 5_200L
        VoiceCharacterState.Disabled -> 8_000L
    }

internal val VoiceCharacterState.bounceInitialDelayMillis: Long
    get() = when (this) {
        // 상태 진입 직후에는 label/ring/level 반응을 먼저 읽게 하고, bounce는 약간 늦게 시작한다.
        VoiceCharacterState.Idle -> 800L
        VoiceCharacterState.Listening -> 250L
        VoiceCharacterState.Thinking -> 450L
        VoiceCharacterState.Speaking -> 250L
        VoiceCharacterState.Disabled -> 1_200L
    }

internal fun VoiceCharacterState.resolveOrbScale(
    inputLevel: Float,
    outputLevel: Float,
    idleBreathing: Float
): Float {
    return when (this) {
        // B 단계에서 "bounce"는 scale이 아니라 Y축 jump로 표현한다.
        // 이 scale은 A 단계의 미세한 생동감만 남기며, 상태 전달의 주 피드백으로 쓰지 않는다.
        VoiceCharacterState.Idle -> 1f + idleBreathing * 0.01f
        VoiceCharacterState.Listening -> 1f + inputLevel * 0.01f
        VoiceCharacterState.Thinking -> 1f
        VoiceCharacterState.Speaking -> 1f + outputLevel * 0.01f
        VoiceCharacterState.Disabled -> 0.95f
    }
}

internal fun VoiceCharacterState.resolveOrbSquashStretch(orbLift: Float): Offset {
    if (this == VoiceCharacterState.Disabled) return Offset(1f, 1f)

    val totalLift = GroundNeutralLiftRatio + orbLift
    val activeMotionWeight = when (this) {
        // Idle breathing은 상태 전달용 생동감이므로 과한 squash/stretch를 주지 않는다.
        VoiceCharacterState.Idle -> 0.25f
        // 현재 UX에서는 Listening/Speaking의 주 반응이 level meter와 ARC다.
        // 다만 지금 구현된 탄성감은 사용자 확인 기준으로 자연스러워, 오브의 물리 과장은 유지한다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        // Thinking은 차분한 floating이라 찌그러짐도 절반 이하로 제한한다.
        VoiceCharacterState.Thinking -> 0.45f
    }
    // 중립 높이보다 아래로 내려와 바닥에 가까울수록 squash가 강해진다.
    val contactSquash = (1f - (totalLift / GroundNeutralLiftRatio)).coerceIn(0f, 1f) * activeMotionWeight
    // 중립 높이보다 위로 올라간 구간만 stretch로 처리해 튀어오를 때 세로로 길어지는 느낌을 강화한다.
    val airborneStretch =
        ((totalLift - GroundNeutralLiftRatio) / VoiceCharacterMaxJumpRatio).coerceIn(0f, 1f) * activeMotionWeight
    val xScale = (1f + contactSquash * 0.12f - airborneStretch * 0.12f).coerceIn(0.86f, 1.12f)
    val yScale = (1f - contactSquash * 0.1f + airborneStretch * 0.2f).coerceIn(0.9f, 1.2f)

    return Offset(xScale, yScale)
}

internal fun VoiceCharacterState.resolveBounceSpec(index: Int): BounceSpec {
    val intervalJitter = when (index % 5) {
        0 -> 0L
        1 -> 420L
        2 -> 160L
        3 -> 620L
        else -> 300L
    }
    val heightJitter = when (index % 3) {
        0 -> 0f
        1 -> -0.1f
        else -> 0.08f
    }

    return when (this) {
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> {
            BounceSpec(
                // 대화 중에는 레벨미터와 ARC가 주 피드백이다.
                // 오브는 Thinking과 비슷한 낮은 floating만 유지해 산만한 점프를 피한다.
                downLift = GroundNeutralLiftRatio * 0.35f,
                upLift = 0.25f + heightJitter.coerceAtLeast(0f) * 0.3f,
                fallMillis = 320,
                contactMillis = 140L,
                reboundMillis = 420,
                settleMillis = 320,
                restMillis = 1_000L + intervalJitter
            )
        }
        VoiceCharacterState.Thinking -> BounceSpec(
            // Thinking은 바닥 접지보다 낮은 부유감이 목적이라 접지 깊이를 줄인다.
            downLift = GroundNeutralLiftRatio * 0.35f,
            upLift = 0.25f + heightJitter.coerceAtLeast(0f) * 0.3f,
            fallMillis = 320,
            contactMillis = 140L,
            reboundMillis = 420,
            settleMillis = 320,
            restMillis = 1_000L + intervalJitter
        )
        VoiceCharacterState.Idle -> BounceSpec(
            downLift = 0.06f,
            upLift = 0.08f,
            fallMillis = 380,
            contactMillis = 160L,
            reboundMillis = 420,
            settleMillis = 360,
            restMillis = 1_800L + intervalJitter
        )
        VoiceCharacterState.Disabled -> BounceSpec(
            downLift = 0f,
            upLift = 0f,
            fallMillis = 600,
            contactMillis = 600L,
            reboundMillis = 0,
            settleMillis = 300,
            restMillis = 1_200L
        )
    }
}

internal fun VoiceCharacterState.resolveGlowBoost(inputLevel: Float, outputLevel: Float): Float {
    return when (this) {
        // Orb와 ARC의 추가 발광량입니다.
        // Listening은 입력 레벨, Speaking은 출력 레벨을 그대로 반영해 음성 강도와 빛 반응이 같이 움직이게 합니다.
        VoiceCharacterState.Listening -> inputLevel
        VoiceCharacterState.Speaking -> outputLevel
        VoiceCharacterState.Thinking -> 0.05f
        VoiceCharacterState.Idle -> 0f
        VoiceCharacterState.Disabled -> 0f
    }
}

internal fun VoiceCharacterState.resolveSharedMeterLevel(inputLevel: Float, outputLevel: Float): Float {
    return when (this) {
        // 사용자가 말할 때도, AI가 말할 때도 좌우 레벨미터가 함께 움직여야 한다.
        // 따라서 현재 상태의 실제 발화 레벨 하나를 공유 레벨로 만들고 양쪽 막대에 동시에 적용한다.
        VoiceCharacterState.Listening -> inputLevel
        VoiceCharacterState.Speaking -> outputLevel
        VoiceCharacterState.Thinking -> 0.1f
        VoiceCharacterState.Idle -> 0.05f
        VoiceCharacterState.Disabled -> 0.05f
    }.coerceIn(0f, 1f)
}

internal fun VoiceCharacterState.resolveMeterHeightScale(level: Float, waveform: Float): Float {
    val turbulence = when (this) {
        // 레벨미터의 출렁임 강도입니다.
        // Listening/Speaking 값을 키우면 실제 음성 레벨에 따른 막대 변화가 더 격동적으로 보입니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1.3f
        VoiceCharacterState.Thinking -> 0.35f
        VoiceCharacterState.Idle -> 0.15f
        VoiceCharacterState.Disabled -> 0.05f
    }
    val activeLevel = level.coerceIn(0f, 1f)
    val levelEmphasis = sqrt(activeLevel)
    // Waveform 라이브러리처럼 보이게 하려면 단순히 전체 높이만 키우면 안 된다.
    // 현재 음성 레벨 위에 막대별 시간차를 곱해 각 막대가 서로 다른 높이로 격동하도록 만든다.
    return (
        0.05f +
            activeLevel * 0.35f +
            levelEmphasis * 0.2f +
            waveform * levelEmphasis * 0.85f * turbulence
        ).coerceIn(0.05f, 1.2f)
}

internal fun waveformFactor(
    phase: Float,
    index: Int,
    state: VoiceCharacterState
): Float {
    val primary = ((sin(phase) + 1f) / 2f)
    val secondary = ((sin(phase * 1.7f + index * 0.4f) + 1f) / 2f)
    val activeWeight = when (state) {
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.45f
        VoiceCharacterState.Idle -> 0.3f
        VoiceCharacterState.Disabled -> 0.1f
    }
    // 두 개의 sine 파형을 섞어 규칙적인 계단 움직임보다 실제 waveform에 가까운 불규칙감을 만든다.
    return ((primary * 0.7f + secondary * 0.3f) * activeWeight).coerceIn(0f, 1f)
}

internal suspend fun delayUntilStateChangesOrTimeout(
    totalMillis: Long,
    currentState: () -> VoiceCharacterState
): Boolean {
    val observedState = currentState()
    var elapsedMillis = 0L

    // 긴 delay를 한 번에 걸면 상태 변경 후에도 이전 상태의 휴지 시간이 끝날 때까지 반응하지 못한다.
    // 짧은 polling 단위로 나눠 기다리면 coroutine을 취소하지 않고도 다음 cycle에서 새 상태 규칙을 반영할 수 있다.
    // 반환값은 상태 변경으로 빠져나왔는지 알려줘, 호출부가 "이전 상태와 비교"하는 모호한 분기를 만들지 않게 한다.
    while (elapsedMillis < totalMillis) {
        val stepMillis = min(120L, totalMillis - elapsedMillis)
        delay(stepMillis)
        elapsedMillis += stepMillis

        if (currentState() != observedState) {
            return true
        }
    }

    return false
}
