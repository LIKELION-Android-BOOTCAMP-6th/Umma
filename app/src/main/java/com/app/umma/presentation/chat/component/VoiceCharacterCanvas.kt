package com.app.umma.presentation.chat.component

import android.graphics.Paint
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
import kotlin.math.min

/**
 * Voice character의 Canvas drawing만 담당합니다.
 *
 * Composable 상태 계산과 drawing 명령을 분리해도 픽셀 결과가 바뀌면 안 되므로,
 * 기존 draw 순서와 계산식을 그대로 유지합니다.
 */
internal fun DrawScope.drawVoiceCharacter(
    visualState: VoiceCharacterState,
    inputLevel: Float,
    outputLevel: Float,
    inputMeterLevel: Float,
    outputMeterLevel: Float,
    ringRotations: RingRotations,
    animation: VoiceCharacterAnimation,
    drawCache: VoiceCharacterDrawCache
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
    val shadowCenter = Offset(center.x, center.y + bodyRadius * 1.75f)
    // Shadow의 중심을 바닥으로 보고, orb 하단이 그 근처에 닿는 위치를 착지점으로 잡는다.
    // 기본 위치는 바닥과 윗공간의 중간쯤에 두고, signed bounce가 아래/위로 움직이게 한다.
    val groundedCenterY = shadowCenter.y - bodyRadius * GroundContactRadiusOffset
    val totalLift = GroundNeutralLiftRatio + animation.orbLift
    // ARC와 레벨미터는 상태 피드백 UI라 오브의 물리 bounce를 따라 움직이면 화면 전체가 흔들려 보인다.
    // 그래서 이 중립 center에 고정하고, 실제 공처럼 튀는 움직임은 orb body/face에만 적용한다.
    val stableVisualCenter = center.copy(y = groundedCenterY - bodyRadius * GroundNeutralLiftRatio)
    val animatedCenter = center.copy(y = groundedCenterY - bodyRadius * totalLift)

    drawFloatingShadow(
        shadowCenter = shadowCenter,
        bodyRadius = bodyRadius,
        orbLift = totalLift,
        outputLevel = outputLevel,
        disabledAlpha = disabledAlpha,
        drawCache = drawCache
    )
    drawSideVoiceBars(
        visualState = visualState,
        center = stableVisualCenter,
        bodyRadius = bodyRadius,
        inputLevel = inputMeterLevel,
        outputLevel = outputMeterLevel,
        waveformPhase = animation.waveformPhase,
        glowBoost = animation.glowBoost,
        disabledAlpha = disabledAlpha,
        drawCache = drawCache
    )
    drawSimpleOrbitArcs(
        center = stableVisualCenter,
        ringRadius = ringRadius,
        bodyRadius = bodyRadius,
        ringRotations = ringRotations,
        glowBoost = animation.glowBoost,
        animation = animation,
        disabledAlpha = disabledAlpha,
        drawCache = drawCache
    )
    drawOrbBody(
        center = animatedCenter,
        bodyRadius = bodyRadius,
        squashStretch = animation.orbSquashStretch,
        disabledAlpha = disabledAlpha
    )
    drawOrbFace(
        visualState = visualState,
        center = animatedCenter,
        bodyRadius = bodyRadius,
        squashStretch = animation.orbSquashStretch,
        eyeOffset = animation.eyeOffset,
        blinkProgress = animation.blinkProgress,
        disabledAlpha = disabledAlpha
    )
}

private fun DrawScope.drawFloatingShadow(
    shadowCenter: Offset,
    bodyRadius: Float,
    orbLift: Float,
    outputLevel: Float,
    disabledAlpha: Float,
    drawCache: VoiceCharacterDrawCache
) {
    // B 단계 shadow는 오브를 따라 올라가면 안 된다. 바닥 기준점은 유지하고,
    // 오브가 떠오를수록 폭과 alpha만 줄여 "높이"를 읽을 수 있게 만든다.
    val liftProgress = (orbLift / VoiceCharacterMaxJumpRatio).coerceIn(0f, 1f)
    val widthScale = 1f - liftProgress * 0.35f
    val alphaScale = 1f - liftProgress * 0.25f
    drawBlurredOval(
        center = shadowCenter,
        width = bodyRadius * 1.9f * widthScale,
        height = bodyRadius * 0.28f * (1f - liftProgress * 0.15f),
        // 실제 기기에서 그림자가 약하게 보여 바닥 접지감을 읽기 어려웠다.
        // alpha 기준을 조금 올리되 lift 시에는 여전히 옅어지게 해서 떠오르는 느낌은 유지한다.
        color = VoicePurple.copy(alpha = (0.35f + outputLevel * 0.08f) * alphaScale * disabledAlpha),
        blurRadius = bodyRadius * 0.12f,
        drawCache = drawCache
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
    disabledAlpha: Float,
    drawCache: VoiceCharacterDrawCache
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
    // 화면 양끝에 붙으면 캐릭터와 별도 장식처럼 보인다.
    // 양쪽 미터를 캐릭터 외곽과 화면 여백 사이로 당겨 같은 인터랙션 그룹으로 읽히게 한다.
    val leftBaseX = center.x - bodyRadius * 1.6f - gap * (barCount - 1)
    val rightBaseX = center.x + bodyRadius * 1.6f
    val sharedMeterLevel = visualState.resolveSharedMeterLevel(inputLevel = inputLevel, outputLevel = outputLevel)
    val meterAlpha = (0.3f + sharedMeterLevel * 0.7f) * visualState.meterAlpha * disabledAlpha

    repeat(barCount) { index ->
        val heightRatio = MeterHeightRatios[index]
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
            blurRadius = barWidth * 2.8f,
            drawCache = drawCache
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
            blurRadius = barWidth * 2.8f,
            drawCache = drawCache
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
    squashStretch: Offset,
    disabledAlpha: Float
) {
    // 2D 카툰 입체감은 복잡한 빛 표현이 아니라 깔끔한 면과 외곽선으로 만든다.
    // B 단계에서는 Y축 점프 위에 squash/stretch를 얹어 공이 바닥에 닿고 튀어오르는 물리감을 만든다.
    // scale만으로 bounce를 대체하지 않고, 실제 center 이동은 orbLift가 담당한다.
    val bodySize = Size(
        width = bodyRadius * 2f * squashStretch.x,
        height = bodyRadius * 2f * squashStretch.y
    )
    val bodyTopLeft = Offset(
        x = center.x - bodySize.width / 2f,
        y = center.y - bodySize.height / 2f
    )

    drawOval(
        color = VoiceCartoonTeal.copy(alpha = disabledAlpha),
        topLeft = bodyTopLeft,
        size = bodySize
    )

    drawOval(
        color = VoiceCartoonStroke.copy(alpha = 0.2f * disabledAlpha),
        topLeft = bodyTopLeft,
        size = bodySize,
        style = Stroke(width = bodyRadius * 0.05f)
    )
}

private fun DrawScope.drawSimpleOrbitArcs(
    center: Offset,
    ringRadius: Float,
    bodyRadius: Float,
    ringRotations: RingRotations,
    glowBoost: Float,
    animation: VoiceCharacterAnimation,
    disabledAlpha: Float,
    drawCache: VoiceCharacterDrawCache
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
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost,
        drawCache = drawCache
    )
    drawOrbitArc(
        rect = pinkRect,
        color = VoicePink,
        startAngle = 304f + ringRotations.pink,
        sweepAngle = 80f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.pinkRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost,
        drawCache = drawCache
    )
    drawOrbitArc(
        rect = blueRect,
        color = VoiceBlue,
        startAngle = 24f + ringRotations.blue,
        sweepAngle = 95f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.blueRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost,
        drawCache = drawCache
    )
    drawOrbitArc(
        rect = purpleRect,
        color = VoicePurple,
        startAngle = 126f + ringRotations.purple,
        sweepAngle = 65f,
        width = arcWidth,
        alpha = arcBaseAlpha * animation.purpleRingAlpha * disabledAlpha,
        glowMultiplier = animation.ringGlowMultiplier + arcGlowBoost,
        drawCache = drawCache
    )
}

private fun DrawScope.drawOrbitArc(
    rect: Rect,
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    width: Float,
    alpha: Float,
    glowMultiplier: Float,
    drawCache: VoiceCharacterDrawCache
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
        blurRadius = width * (1.25f + glowMultiplier * 0.8f),
        drawCache = drawCache
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
    blurRadius: Float,
    drawCache: VoiceCharacterDrawCache
) {
    drawIntoCanvas { canvas ->
        val paint = drawCache.blurPaint.apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            maskFilter = drawCache.blurFilter(radius = blurRadius)
        }
        val arcRect = drawCache.arcRect.apply {
            set(rect.left, rect.top, rect.right, rect.bottom)
        }
        canvas.nativeCanvas.drawArc(
            arcRect,
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
    squashStretch: Offset,
    eyeOffset: Offset,
    blinkProgress: Float,
    disabledAlpha: Float
) {
    // 눈도 본체의 squash/stretch를 약하게 따라가야 같은 물체 위에 붙어 있는 느낌이 난다.
    // 다만 blink와 겹치면 눈이 지나치게 납작해질 수 있어 body scale은 그대로 곱하되 하한은 blink 쪽에서 잡는다.
    val eyeWidth = bodyRadius * 0.1f * squashStretch.x
    // Blink는 eye height만 줄여 표현한다. 0.15f를 하한으로 둬 눈이 완전히 사라지는
    // 강한 표정 변화처럼 보이지 않게 하고, 기존 단순한 흰색 눈 디자인을 유지한다.
    val eyeHeightScale = (1f - blinkProgress * 0.85f).coerceIn(0.15f, 1f)
    val eyeHeight = bodyRadius * 0.3f * squashStretch.y * eyeHeightScale
    val eyeY =
        center.y -
            bodyRadius * 0.2f * squashStretch.y +
            bodyRadius * visualState.eyeVerticalBias +
            bodyRadius * eyeOffset.y
    val eyeOffsetX = bodyRadius * 0.3f * squashStretch.x

    repeat(2) { index ->
        val offsetX = if (index == 0) -eyeOffsetX else eyeOffsetX
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f * disabledAlpha),
            topLeft = Offset(center.x + offsetX + bodyRadius * eyeOffset.x - eyeWidth / 2f, eyeY - eyeHeight / 2f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth, eyeWidth)
        )
    }
}

private fun DrawScope.drawBlurredOval(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    blurRadius: Float,
    drawCache: VoiceCharacterDrawCache
) {
    drawIntoCanvas { canvas ->
        val paint = drawCache.blurPaint.apply {
            this.color = color.toArgb()
            style = Paint.Style.FILL
            maskFilter = drawCache.blurFilter(radius = blurRadius)
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
