package com.app.umma.presentation.chat.component

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextPrimary
import com.app.umma.domain.model.realtime.AIState
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AI Chat 화면 중앙의 음성 인터랙션 캐릭터입니다.
 *
 * 이 컴포넌트는 레퍼런스 디자인 재현을 위한 presentation 전용 컴포넌트입니다.
 * API 연결, 세션 상태 전환, 저장 정책은 알지 않고 ViewModel 이 넘겨주는 상태와 음성 레벨만
 * 시각 요소로 변환합니다. 링 회전은 화면 장식용 애니메이션이며 대화 상태 판단에는 관여하지 않습니다.
 */
@Composable
fun VoiceInteractionCharacter(
    inputLevel: Float,
    outputLevel: Float,
    isRecording: Boolean,
    isAwaitingUserTranscript: Boolean,
    aiState: AIState,
    isAudioOutputPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val visualState = resolveVoiceCharacterState(
        isRecording = isRecording,
        isAwaitingUserTranscript = isAwaitingUserTranscript,
        aiState = aiState,
        isAudioOutputPlaying = isAudioOutputPlaying
    )
    val inputVisualLevel = inputLevel.toDesignLevel(active = visualState == VoiceCharacterState.Listening)
    val outputVisualLevel = outputLevel.toDesignLevel(active = visualState == VoiceCharacterState.Speaking)
    // Orb bounce와 ring glow는 상태가 살아 있음을 보여주기 위해 최소 보정값을 사용한다.
    // 반면 레벨미터는 실제 음성 강도를 보여줘야 하므로 raw level에 가까운 값을 별도로 사용한다.
    val inputMeterLevel = inputLevel.toMeterLevel(active = visualState == VoiceCharacterState.Listening)
    val outputMeterLevel = outputLevel.toMeterLevel(active = visualState == VoiceCharacterState.Speaking)
    // ARC glow는 "소리의 유무와 강도 차이"가 더 도드라져야 하므로 레벨미터와 다른 곡선을 쓴다.
    // 작은 소리는 낮게 눌러주고, 큰 소리는 확실히 끌어올려 빛번짐 대비가 크게 보이게 한다.
    val inputArcGlowLevel = inputLevel.toArcGlowLevel(active = visualState == VoiceCharacterState.Listening)
    val outputArcGlowLevel = outputLevel.toArcGlowLevel(active = visualState == VoiceCharacterState.Speaking)
    val ringMotion = rememberInfiniteTransition(label = "voice-character-ring-motion")
    val idleBreathing by ringMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-character-idle-breathing"
    )
    val eyeDrift by ringMotion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-character-eye-drift"
    )
    val waveformPhase by ringMotion.animateFloat(
        initialValue = 0f,
        targetValue = (PI.toFloat() * 2f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = LinearEasing)
        ),
        label = "voice-character-meter-waveform"
    )
    val ringSpeedMultiplier by animateFloatAsState(
        targetValue = visualState.ringSpeedMultiplier,
        // 상태가 바뀔 때 목표 회전 속도로 즉시 점프하면 링이 기계적으로 튀어 보인다.
        // durationMillis 값을 키우면 속도 변화가 더 천천히 반영되고, 줄이면 더 즉각적으로 반응한다.
        // FastOutSlowInEasing은 초반에 부드럽게 가속하고 목표값 근처에서 감속하는 체감 가속도를 만든다.
        animationSpec = tween(durationMillis = 1_250, easing = FastOutSlowInEasing),
        label = "voice-character-ring-speed"
    )
    val currentRingSpeedMultiplier by rememberUpdatedState(ringSpeedMultiplier)
    val orbScale by animateFloatAsState(
        targetValue = visualState.resolveOrbScale(
            inputLevel = inputVisualLevel,
            outputLevel = outputVisualLevel,
            idleBreathing = idleBreathing
        ),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "voice-character-orb-scale"
    )
    val orbLift by animateFloatAsState(
        targetValue = visualState.resolveOrbLift(inputLevel = inputVisualLevel, outputLevel = outputVisualLevel),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "voice-character-orb-lift"
    )
    val eyeOffset by animateFloatAsState(
        targetValue = visualState.resolveEyeOffset(eyeDrift = eyeDrift),
        animationSpec = tween(durationMillis = 520, easing = LinearEasing),
        label = "voice-character-eye-offset"
    )
    val glowBoost by animateFloatAsState(
        // ARC는 레벨미터보다 반응이 작게 보이기 쉬워 별도 glow level을 사용한다.
        // duration을 짧게 두어 큰 소리가 들어오는 순간 빛번짐이 즉시 커지고, 소리가 작아지면 빠르게 가라앉게 한다.
        targetValue = visualState.resolveGlowBoost(inputLevel = inputArcGlowLevel, outputLevel = outputArcGlowLevel),
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "voice-character-glow-boost"
    )
    val orangeRingAlpha by animateFloatAsState(
        // 상태별 ARC 밝기값입니다. 상태가 바뀔 때 즉시 튀지 않고 천천히 밝아지거나 어두워지게 한다.
        targetValue = visualState.orangeRingAlpha,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-orange-ring-alpha"
    )
    val pinkRingAlpha by animateFloatAsState(
        targetValue = visualState.pinkRingAlpha,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-pink-ring-alpha"
    )
    val blueRingAlpha by animateFloatAsState(
        targetValue = visualState.blueRingAlpha,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-blue-ring-alpha"
    )
    val purpleRingAlpha by animateFloatAsState(
        targetValue = visualState.purpleRingAlpha,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-purple-ring-alpha"
    )
    val ringGlowMultiplier by animateFloatAsState(
        // 상태 자체가 가진 기본 glow입니다. 이 값이 천천히 변하면 ARC의 빛번짐 폭도 자연스럽게 커지고 줄어든다.
        targetValue = visualState.ringGlowMultiplier,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-ring-glow"
    )
    val disabledAlpha by animateFloatAsState(
        // 비활성 상태 전환 시 캐릭터 전체가 갑자기 흐려지지 않도록 전체 alpha도 보간한다.
        targetValue = if (visualState == VoiceCharacterState.Disabled) 0.45f else 1f,
        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
        label = "voice-character-disabled-alpha"
    )
    var orangeRingRotation by remember { mutableFloatStateOf(0f) }
    var pinkRingRotation by remember { mutableFloatStateOf(0f) }
    var blueRingRotation by remember { mutableFloatStateOf(0f) }
    var purpleRingRotation by remember { mutableFloatStateOf(0f) }

    // Arc ring은 상태에 따라 속도가 바뀌어야 하므로 단순 infiniteTransition duration만으로는 부족하다.
    // 프레임 간 시간 차이를 직접 누적해 각 링의 기본 속도와 상태별 배속을 함께 반영한다.
    LaunchedEffect(Unit) {
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
            previousFrameNanos = frameNanos

            orangeRingRotation = (orangeRingRotation + 36f * currentRingSpeedMultiplier * elapsedSeconds).wrapDegrees()
            pinkRingRotation = (pinkRingRotation - 24f * currentRingSpeedMultiplier * elapsedSeconds).wrapDegrees()
            blueRingRotation = (blueRingRotation + 17f * currentRingSpeedMultiplier * elapsedSeconds).wrapDegrees()
            purpleRingRotation = (purpleRingRotation - 13f * currentRingSpeedMultiplier * elapsedSeconds).wrapDegrees()
        }
    }
    val ringRotations = RingRotations(
        orange = orangeRingRotation,
        pink = pinkRingRotation,
        blue = blueRingRotation,
        purple = purpleRingRotation
    )
    val characterAnimation = VoiceCharacterAnimation(
        orbScale = orbScale,
        orbLift = orbLift,
        eyeOffset = eyeOffset,
        glowBoost = glowBoost,
        waveformPhase = waveformPhase,
        orangeRingAlpha = orangeRingAlpha,
        pinkRingAlpha = pinkRingAlpha,
        blueRingAlpha = blueRingAlpha,
        purpleRingAlpha = purpleRingAlpha,
        ringGlowMultiplier = ringGlowMultiplier,
        disabledAlpha = disabledAlpha
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 캐릭터, 상태 문구, 자막 영역이 한 화면 안에서 충돌하지 않도록 상단 그래픽 높이를 제한한다.
            .heightIn(max = 390.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Umma",
            style = TextAnalysisR.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1.1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVoiceCharacter(
                    visualState = visualState,
                    inputLevel = inputVisualLevel,
                    outputLevel = outputVisualLevel,
                    inputMeterLevel = inputMeterLevel,
                    outputMeterLevel = outputMeterLevel,
                    ringRotations = ringRotations,
                    animation = characterAnimation
                )
            }
        }

        Text(
            text = visualState.statusText,
            style = TextAnalysisR,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun DrawScope.drawVoiceCharacter(
    visualState: VoiceCharacterState,
    inputLevel: Float,
    outputLevel: Float,
    inputMeterLevel: Float,
    outputMeterLevel: Float,
    ringRotations: RingRotations,
    animation: VoiceCharacterAnimation
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val center = Offset(canvasWidth / 2f, canvasHeight * 0.45f)
    val minSide = min(canvasWidth, canvasHeight)
    val activeLevel = maxOf(inputLevel, outputLevel)
    val baseBodyRadius = minSide * (0.25f + activeLevel * 0.01f)
    val bodyRadius = baseBodyRadius * animation.orbScale
    val ringRadius = bodyRadius * 1.35f
    val disabledAlpha = animation.disabledAlpha
    val animatedCenter = center.copy(y = center.y - bodyRadius * animation.orbLift)

    drawFloatingShadow(center = animatedCenter, bodyRadius = bodyRadius, outputLevel = outputLevel, disabledAlpha = disabledAlpha)
    drawSideVoiceBars(
        visualState = visualState,
        center = animatedCenter,
        bodyRadius = bodyRadius,
        inputLevel = inputMeterLevel,
        outputLevel = outputMeterLevel,
        waveformPhase = animation.waveformPhase,
        glowBoost = animation.glowBoost,
        disabledAlpha = disabledAlpha
    )
    drawSimpleOrbitArcs(
        visualState = visualState,
        center = animatedCenter,
        ringRadius = ringRadius,
        bodyRadius = bodyRadius,
        ringRotations = ringRotations,
        glowBoost = animation.glowBoost,
        animation = animation,
        disabledAlpha = disabledAlpha
    )
    drawOrbBody(center = animatedCenter, bodyRadius = bodyRadius, disabledAlpha = disabledAlpha)
    drawOrbFace(
        visualState = visualState,
        center = animatedCenter,
        bodyRadius = bodyRadius,
        eyeOffset = animation.eyeOffset,
        disabledAlpha = disabledAlpha
    )
}

private fun DrawScope.drawFloatingShadow(
    center: Offset,
    bodyRadius: Float,
    outputLevel: Float,
    disabledAlpha: Float
) {
    // 그림자는 캐릭터가 가볍게 떠 있다는 사실만 전달한다.
    // 이전처럼 다층 타원선을 많이 넣으면 전체가 복잡해져 단순화된 캐릭터와 맞지 않는다.
    val shadowCenter = Offset(center.x, center.y + bodyRadius * 1.5f)
    drawBlurredOval(
        center = shadowCenter,
        width = bodyRadius * 1.45f,
        height = bodyRadius * 0.2f,
        color = VoicePurple.copy(alpha = (0.15f + outputLevel * 0.05f) * disabledAlpha),
        blurRadius = bodyRadius * 0.15f
    )
}

private fun DrawScope.drawSideVoiceBars(
    visualState: VoiceCharacterState,
    center: Offset,
    bodyRadius: Float,
    inputLevel: Float,
    outputLevel: Float,
    waveformPhase: Float,
    glowBoost: Float,
    disabledAlpha: Float
) {
    val barCount = 5
    val barWidth = bodyRadius * 0.05f
    val gap = bodyRadius * 0.15f
    // 사이드 레벨미터는 캐릭터의 음성 반응을 보조하는 요소다.
    // 기존 높이는 레퍼런스 대비 짧아 보였으므로 약 2.5배 키워 음성 미터로 명확히 보이게 한다.
    val maxHeight = bodyRadius * 3.2f
    // 5개 막대는 중앙으로 갈수록 일정하게 커졌다가 다시 줄어드는 형태를 유지한다.
    // 중앙 막대는 이전 기준보다 약 70% 더 길게 보이도록 maxHeight를 키웠고,
    // 가장 짧은 양끝 막대는 중앙 대비 30% 높이로 줄여 리듬감 있는 레벨미터 실루엣을 만든다.
    val heightRatios = listOf(0.3f, 0.65f, 1f, 0.65f, 0.3f)
    // 화면 양끝에 붙으면 캐릭터와 별도 장식처럼 보인다.
    // 양쪽 미터를 캐릭터 외곽과 화면 여백 사이로 당겨 같은 인터랙션 그룹으로 읽히게 한다.
    val leftBaseX = center.x - bodyRadius * 1.6f - gap * (barCount - 1)
    val rightBaseX = center.x + bodyRadius * 1.6f
    val sharedMeterLevel = visualState.resolveSharedMeterLevel(inputLevel = inputLevel, outputLevel = outputLevel)
    val meterAlpha = (0.3f + sharedMeterLevel * 0.7f) * visualState.meterAlpha * disabledAlpha

    repeat(barCount) { index ->
        val heightRatio = heightRatios[index]
        val leftWave = waveformFactor(
            phase = waveformPhase + index * 0.9f,
            index = index,
            state = visualState
        )
        val rightWave = waveformFactor(
            phase = waveformPhase + (barCount - 1 - index) * 0.9f + 0.55f,
            index = barCount - 1 - index,
            state = visualState
        )
        val leftHeight = maxHeight * heightRatio * visualState.resolveMeterHeightScale(
            level = sharedMeterLevel,
            waveform = leftWave
        )
        val rightHeight = maxHeight * heightRatio * visualState.resolveMeterHeightScale(
            level = sharedMeterLevel,
            waveform = rightWave
        )
        val leftAlpha = meterAlpha
        val rightAlpha = meterAlpha

        // 실제 음성 레벨은 사용자 발화와 AI 발화 모두 양쪽 레벨미터에 동시에 반영한다.
        // 좌우 색상은 역할감을 유지하기 위한 시각 구분이고, 움직임 자체는 현재 발화 레벨을 공유한다.
        drawBlurredOval(
            center = Offset(leftBaseX + index * gap + barWidth / 2f, center.y),
            width = barWidth * (3.2f + glowBoost),
            height = leftHeight * (1.1f + glowBoost * 0.1f),
            color = VoiceOrange.copy(alpha = leftAlpha * visualState.meterGlowAlpha * 0.25f),
            blurRadius = barWidth * 2.8f
        )
        drawRoundRect(
            color = VoiceOrange.copy(alpha = leftAlpha),
            topLeft = Offset(leftBaseX + index * gap, center.y - leftHeight / 2f),
            size = Size(barWidth, leftHeight),
            cornerRadius = CornerRadius(barWidth, barWidth)
        )
        drawBlurredOval(
            center = Offset(rightBaseX + index * gap + barWidth / 2f, center.y),
            width = barWidth * (3.2f + glowBoost),
            height = rightHeight * (1.1f + glowBoost * 0.1f),
            color = VoiceBlue.copy(alpha = rightAlpha * visualState.meterGlowAlpha * 0.2f),
            blurRadius = barWidth * 2.8f
        )
        drawRoundRect(
            color = VoiceBlue.copy(alpha = rightAlpha),
            topLeft = Offset(rightBaseX + index * gap, center.y - rightHeight / 2f),
            size = Size(barWidth, rightHeight),
            cornerRadius = CornerRadius(barWidth, barWidth)
        )
    }
}

private fun DrawScope.drawOrbBody(
    center: Offset,
    bodyRadius: Float,
    disabledAlpha: Float
) {
    // 2D 카툰 입체감은 복잡한 빛 표현이 아니라 깔끔한 면과 외곽선으로 만든다.
    // 별도 타원 highlight나 offset shadow는 어색하게 보일 수 있어 제거하고, 단순한 본체와 stroke만 유지한다.
    drawCircle(
        color = VoiceCartoonTeal.copy(alpha = disabledAlpha),
        radius = bodyRadius,
        center = center
    )
    drawCircle(
        color = VoiceCartoonStroke.copy(alpha = 0.2f * disabledAlpha),
        radius = bodyRadius,
        center = center,
        style = Stroke(width = bodyRadius * 0.05f)
    )
}

private fun DrawScope.drawSimpleOrbitArcs(
    visualState: VoiceCharacterState,
    center: Offset,
    ringRadius: Float,
    bodyRadius: Float,
    ringRotations: RingRotations,
    glowBoost: Float,
    animation: VoiceCharacterAnimation,
    disabledAlpha: Float
) {
    // 색상별 arc를 같은 원 위에 올리면 화면에서는 하나의 두꺼운 링처럼 뭉쳐 보인다.
    // 그래서 네 색상 모두 서로 다른 반지름을 갖게 하여 각 arc의 궤도를 명확히 분리한다.
    val orangeRect = Rect(center = center, radius = ringRadius * 1.1f)
    val pinkRect = Rect(center = center, radius = ringRadius * 1.0f)
    val blueRect = Rect(center = center, radius = ringRadius * 0.9f)
    val purpleRect = Rect(center = center, radius = ringRadius * 0.8f)

    // ARC별 시각값은 나중에 개별 튜닝할 수 있도록 호출부에 남겨둔다.
    // alpha/두께는 같은 기준으로 맞추되, 길이는 레퍼런스처럼 색마다 약간 다른 리듬이 생기도록 개별값을 유지한다.
    val arcWidth = bodyRadius * 0.05f
    val arcBaseAlpha = 0.6f
    // 음성 강도에 따라 추가되는 동적 발광량입니다.
    // 상태 자체의 활성감은 ringGlowMultiplier가 만들고, 실제 음성 강도에 따른 추가 반응은 이 값이 만듭니다.
    // 값을 키우면 큰 소리에서 빛번짐이 더 튀고, 줄이면 상태별 기본 glow 중심으로 차분해집니다.
    val arcGlowBoost = glowBoost * 1.7f

    // 인접한 링이 같은 방향으로 돌면 움직임이 단조롭고 서로 붙어 보인다.
    // orange/blue는 시계 방향, pink/purple은 반시계 방향으로 돌려 교차 회전감을 만든다.
    drawOrbitArc(
        rect = orangeRect,
        color = VoiceOrange,
        startAngle = 206f + ringRotations.orange,
        sweepAngle = 110f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.orangeRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost
    )
    drawOrbitArc(
        rect = pinkRect,
        color = VoicePink,
        startAngle = 304f + ringRotations.pink,
        sweepAngle = 80f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.pinkRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost
    )
    drawOrbitArc(
        rect = blueRect,
        color = VoiceBlue,
        startAngle = 24f + ringRotations.blue,
        sweepAngle = 95f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.blueRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost
    )
    drawOrbitArc(
        rect = purpleRect,
        color = VoicePurple,
        startAngle = 126f + ringRotations.purple,
        sweepAngle = 65f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.purpleRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost
    )
}

private fun DrawScope.drawOrbitArc(
    rect: Rect,
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    width: Float,
    alpha: Float,
    glowMultiplier: Float
) {
    // ARC는 "하나의 단색 선"이 아니라 여러 두께의 stroke를 같은 경로에 겹쳐 발광선처럼 만든다.
    // 레퍼런스의 빛번짐은 선 바깥 glow만으로는 부족해서, 중심 stroke 자체에도 부드러운 core와 highlight를 둔다.
    // 값을 조정할 때는 아래 순서대로 보면 된다:
    // 1) drawBlurredArc: 선 바깥으로 퍼지는 넓은 빛
    // 2) outer halo drawArc: blur와 실제 선 사이를 채우는 반투명 색면
    // 3) soft core drawArc: 실제로 보이는 주 색상 선
    // 4) inner highlight drawArc: 선 중앙의 밝은 빛줄기
    drawBlurredArc(
        rect = rect,
        color = color.copy(alpha = alpha * (0.45f + glowMultiplier * 0.1f)),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        width = width * (1.9f + glowMultiplier * 0.8f),
        blurRadius = width * (1.25f + glowMultiplier * 0.8f)
    )

    // blur 위에 매우 옅은 넓은 stroke를 한 번 더 얹어 glow가 배경에서 갑자기 끊기지 않게 한다.
    drawArc(
        color = color.copy(alpha = alpha * (0.15f + glowMultiplier * 0.15f)),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = width * (2.1f + glowMultiplier * 0.7f), cap = StrokeCap.Round)
    )

    // 실제 색상 core도 약간 두껍고 투명하게 그려 단단한 플랫 라인 대신 빛이 찬 선처럼 보이게 한다.
    drawArc(
        color = color.copy(alpha = alpha * 0.8f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = width * 1.1f, cap = StrokeCap.Round)
    )

    // 중심부에 얇은 밝은 선을 얹어 "스트로크 자체가 빛나는" 느낌을 만든다.
    // alpha를 높이면 유리관 같은 하이라이트가 강해지고, 낮추면 더 부드럽고 차분해진다.
    drawArc(
        color = Color.White.copy(alpha = alpha * (0.2f + glowMultiplier * 0.1f)),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = width * 0.35f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawBlurredArc(
    rect: Rect,
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    width: Float,
    blurRadius: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawArc(
            RectF(rect.left, rect.top, rect.right, rect.bottom),
            startAngle,
            sweepAngle,
            false,
            paint
        )
    }
}

private fun DrawScope.drawOrbFace(
    visualState: VoiceCharacterState,
    center: Offset,
    bodyRadius: Float,
    eyeOffset: Float,
    disabledAlpha: Float
) {
    val eyeWidth = bodyRadius * 0.1f
    val eyeHeight = bodyRadius * 0.3f
    val eyeY = center.y - bodyRadius * 0.2f + bodyRadius * visualState.eyeVerticalBias
    val eyeOffsetX = bodyRadius * 0.3f

    listOf(-eyeOffsetX, eyeOffsetX).forEach { offsetX ->
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f * disabledAlpha),
            topLeft = Offset(center.x + offsetX + bodyRadius * eyeOffset - eyeWidth / 2f, eyeY - eyeHeight / 2f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth, eyeWidth)
        )
    }
}

private fun Float.toDesignLevel(active: Boolean): Float {
    val curved = sqrt(coerceIn(0f, 1f))
    return when {
        active -> curved.coerceAtLeast(0.3f)
        else -> curved.coerceAtLeast(0.2f)
    }
}

private fun Float.toMeterLevel(active: Boolean): Float {
    val rawLevel = coerceIn(0f, 1f)
    return when {
        // 마이크가 켜져 있거나 AI가 말하는 상태라도 실제 음성 강도가 낮으면 막대는 작아야 한다.
        // sqrt 보정은 작은 실제 입력을 살짝 더 보이게만 하고, active minimum을 크게 강제하지 않는다.
        active -> sqrt(rawLevel)
        else -> rawLevel * 0.35f
    }
}

private fun Float.toArcGlowLevel(active: Boolean): Float {
    val rawLevel = coerceIn(0f, 1f)
    return when {
        // ARC glow는 작은 소리와 큰 소리의 차이가 명확해야 한다.
        // pow(1.55)는 낮은 레벨을 더 낮게 눌러 작은 소리에서 빛번짐이 과하게 켜지는 것을 막는다.
        // 뒤의 multiplier는 큰 소리에서 glow가 충분히 치고 올라오도록 하는 보정값이다.
        active -> (rawLevel.pow(1.5f) * 1.3f).coerceIn(0f, 1f)
        else -> 0f
    }
}

private fun resolveVoiceCharacterState(
    isRecording: Boolean,
    isAwaitingUserTranscript: Boolean,
    aiState: AIState,
    isAudioOutputPlaying: Boolean
): VoiceCharacterState {
    return when {
        aiState == AIState.ERROR -> VoiceCharacterState.Disabled
        isRecording -> VoiceCharacterState.Listening
        isAwaitingUserTranscript || aiState == AIState.THINKING -> VoiceCharacterState.Thinking
        aiState == AIState.SPEAKING || isAudioOutputPlaying -> VoiceCharacterState.Speaking
        else -> VoiceCharacterState.Idle
    }
}

private enum class VoiceCharacterState(val statusText: String) {
    Idle("대화할 준비가 되었어요."),
    Listening("듣고 있어요."),
    Thinking("Umma가 답변을 준비하고 있어요."),
    Speaking("Umma가 말하는 중입니다."),
    Disabled("연결을 다시 확인해주세요.")
}

private val VoiceCharacterState.ringSpeedMultiplier: Float
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

private val VoiceCharacterState.meterAlpha: Float
    get() = when (this) {
        // 양쪽 레벨미터의 기본 투명도입니다.
        // 듣기/말하기 상태를 가장 선명하게 두고, 대기/생각중/비활성 상태는 시각적 존재감만 남깁니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.4f
        VoiceCharacterState.Idle -> 0.35f
        VoiceCharacterState.Disabled -> 0.2f
    }

private val VoiceCharacterState.meterGlowAlpha: Float
    get() = when (this) {
        // 레벨미터 주변 빛 번짐의 투명도입니다.
        // 값이 높을수록 음성 반응이 더 강하게 빛나는 것처럼 보입니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.3f
        VoiceCharacterState.Idle -> 0.2f
        VoiceCharacterState.Disabled -> 0.1f
    }

private val VoiceCharacterState.orangeRingAlpha: Float
    get() = when (this) {
        // Orange ARC의 상태별 강조값입니다.
        // 기본값은 다른 ARC와 같은 기준으로 맞추고, 특정 색만 강조하고 싶을 때 여기만 조정합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

private val VoiceCharacterState.pinkRingAlpha: Float
    get() = when (this) {
        // Pink ARC의 상태별 강조값입니다.
        // 현재는 Orange/Blue/Purple과 같은 기준을 사용해 색상별 의미 없는 밝기 차이를 제거합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

private val VoiceCharacterState.blueRingAlpha: Float
    get() = when (this) {
        // Blue ARC의 상태별 강조값입니다.
        // AI 출력 색으로 더 강조하고 싶다면 Speaking 값만 별도로 올리면 됩니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

private val VoiceCharacterState.purpleRingAlpha: Float
    get() = when (this) {
        // Purple ARC의 상태별 강조값입니다.
        // 전체 링 균형을 유지하기 위해 현재는 다른 ARC와 같은 값을 사용합니다.
        VoiceCharacterState.Listening,
        VoiceCharacterState.Speaking -> 1f
        VoiceCharacterState.Thinking -> 0.7f
        VoiceCharacterState.Idle -> 0.4f
        VoiceCharacterState.Disabled -> 0.2f
    }

private val VoiceCharacterState.ringGlowMultiplier: Float
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

private val VoiceCharacterState.eyeVerticalBias: Float
    get() = when (this) {
        // 눈 위치의 세로 보정값입니다.
        // Thinking 상태에서는 눈을 아주 살짝 위로 올려 "생각 중"인 표정을 만듭니다.
        VoiceCharacterState.Thinking -> -0.05f
        VoiceCharacterState.Disabled -> 0.05f
        else -> 0f
    }

private fun VoiceCharacterState.resolveOrbScale(
    inputLevel: Float,
    outputLevel: Float,
    idleBreathing: Float
): Float {
    return when (this) {
        // Orb의 전체 크기 반응입니다.
        // Listening/Speaking 값은 실제 음성 레벨에 곱해지므로 키우면 더 통통 튀고, 줄이면 안정적으로 보입니다.
        VoiceCharacterState.Idle -> 1f + idleBreathing * 0.01f
        VoiceCharacterState.Listening -> 1f + inputLevel * 0.02f
        VoiceCharacterState.Thinking -> 1f
        VoiceCharacterState.Speaking -> 1f + outputLevel * 0.03f
        VoiceCharacterState.Disabled -> 0.95f
    }
}

private fun VoiceCharacterState.resolveOrbLift(inputLevel: Float, outputLevel: Float): Float {
    return when (this) {
        // Orb의 위아래 이동량입니다.
        // Speaking을 Listening보다 크게 둔 이유는 AI가 말할 때 캐릭터가 더 적극적으로 반응해 보이게 하기 위해서입니다.
        VoiceCharacterState.Listening -> inputLevel * 0.03f
        VoiceCharacterState.Speaking -> outputLevel * 0.05f
        else -> 0f
    }
}

private fun VoiceCharacterState.resolveEyeOffset(eyeDrift: Float): Float {
    return when (this) {
        // 눈의 좌우 미세 이동입니다.
        // Idle/Thinking에서만 움직여 생명감을 주고, 발화 중에는 시선이 흔들리지 않게 고정합니다.
        VoiceCharacterState.Idle -> eyeDrift * 0.02f
        VoiceCharacterState.Thinking -> eyeDrift * 0.01f
        else -> 0f
    }
}

private fun VoiceCharacterState.resolveGlowBoost(inputLevel: Float, outputLevel: Float): Float {
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

private fun VoiceCharacterState.resolveSharedMeterLevel(inputLevel: Float, outputLevel: Float): Float {
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

private fun VoiceCharacterState.resolveMeterHeightScale(level: Float, waveform: Float): Float {
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

private fun waveformFactor(
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

private data class RingRotations(
    val orange: Float,
    val pink: Float,
    val blue: Float,
    val purple: Float
)

private data class VoiceCharacterAnimation(
    val orbScale: Float,
    val orbLift: Float,
    val eyeOffset: Float,
    val glowBoost: Float,
    val waveformPhase: Float,
    val orangeRingAlpha: Float,
    val pinkRingAlpha: Float,
    val blueRingAlpha: Float,
    val purpleRingAlpha: Float,
    val ringGlowMultiplier: Float,
    val disabledAlpha: Float
)

private fun Float.wrapDegrees(): Float {
    val wrapped = this % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

private fun DrawScope.drawBlurredOval(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    blurRadius: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawOval(
            center.x - width / 2f,
            center.y - height / 2f,
            center.x + width / 2f,
            center.y + height / 2f,
            paint
        )
    }
}

private val VoiceOrange = Color(0xFFF4901E)
private val VoicePink = Color(0xFFFF6FAE)
private val VoicePurple = Color(0xFF7B4C9E)
private val VoiceBlue = Color(0xFF4A7DFF)
private val VoiceCartoonTeal = Color(0xFF08746F)
private val VoiceCartoonStroke = Color(0xFF005A55)
