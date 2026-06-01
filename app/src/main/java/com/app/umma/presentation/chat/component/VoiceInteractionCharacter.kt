package com.app.umma.presentation.chat.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.app.umma.domain.model.realtime.AIState
import kotlinx.coroutines.delay
import kotlin.math.PI

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
    val targetGazeDirection = remember { mutableStateOf(VoiceGazeDirection.Center) }
    val blinkTarget = remember { mutableFloatStateOf(0f) }
    val currentInputMeterLevel by rememberUpdatedState(inputMeterLevel)
    val currentOutputMeterLevel by rememberUpdatedState(outputMeterLevel)
    val latestVisualState = rememberUpdatedState(visualState)
    val orbLiftAnimation = remember { Animatable(0f) }
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
    val orbLift = orbLiftAnimation.value
    val eyeOffsetX by animateFloatAsState(
        // 시선은 눈 모양을 바꾸지 않고 기존 흰색 눈의 위치만 옮긴다.
        // 상태별 후보 방향은 LaunchedEffect에서 천천히 바뀌고, tween은 방향 전환이 튀지 않게 만든다.
        targetValue = targetGazeDirection.value.xOffset * visualState.gazeHorizontalAmplitude,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "voice-character-eye-offset-x"
    )
    val eyeOffsetY by animateFloatAsState(
        targetValue = targetGazeDirection.value.yOffset * visualState.gazeVerticalAmplitude,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "voice-character-eye-offset-y"
    )
    val blinkProgress by animateFloatAsState(
        // Blink는 별도 이미지나 복잡한 path가 아니라 eye height scale만 줄이는 보조 모션이다.
        // duration을 짧게 둬 상태 피드백보다 눈에 띄지 않게 한다.
        targetValue = blinkTarget.floatValue,
        animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
        label = "voice-character-blink"
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
    val drawCache = remember { VoiceCharacterDrawCache() }

    // Bounce coroutine은 visualState를 key로 삼지 않는다.
    // 상태 변경 때 coroutine이 취소되면 현재 속도와 위치가 끊겨 보이므로, 장기 루프가 최신 상태만 읽게 한다.
    // Bounce는 일정한 sine 반복이 아니라 "바닥 접지 -> 튀어오름 -> 중립 복귀 -> 잠깐 쉼"의 event로 만든다.
    // 이렇게 해야 실제 공처럼 간헐적으로 통통 튀고, 매번 같은 간격으로 왕복하는 기계적 느낌을 줄일 수 있다.
    LaunchedEffect(Unit) {
        var bounceIndex = 0
        delayUntilStateChangesOrTimeout(latestVisualState.value.bounceInitialDelayMillis) {
            latestVisualState.value
        }

        while (true) {
            val state = latestVisualState.value
            val bounceSpec = state.resolveBounceSpec(index = bounceIndex)

            // 낙하는 중력처럼 점점 빨라져야 하므로 FastOutLinearIn으로 바닥까지 떨어뜨린다.
            orbLiftAnimation.animateTo(
                targetValue = -bounceSpec.downLift,
                animationSpec = tween(durationMillis = bounceSpec.fallMillis, easing = FastOutLinearInEasing)
            )
            delay(bounceSpec.contactMillis)

            // 반동은 초반에 빠르게 튀고 꼭대기에서 감속해야 자연스럽다.
            orbLiftAnimation.animateTo(
                targetValue = bounceSpec.upLift,
                animationSpec = tween(durationMillis = bounceSpec.reboundMillis, easing = LinearOutSlowInEasing)
            )

            // 최고점 이후에는 바로 다음 접지로 가지 않고 중립 hover 높이로 부드럽게 돌아온다.
            orbLiftAnimation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = bounceSpec.settleMillis, easing = FastOutSlowInEasing)
            )
            // 휴지 구간은 상태 변화가 들어오면 짧게 끊어 다음 cycle에서 새 상태의 motion 규칙을 반영한다.
            // 현재 Animatable 값은 유지되므로 전환 자체가 snap으로 보이지 않는다.
            delayUntilStateChangesOrTimeout(bounceSpec.restMillis) { latestVisualState.value }
            bounceIndex += 1
        }
    }

    // Gaze coroutine도 상태를 key로 삼지 않는다.
    // 상태 전환마다 첫 방향으로 snap하면 눈동자가 끊겨 보이므로, 현재 방향에서 새 상태의 후보 방향으로만 부드럽게 이동한다.
    LaunchedEffect(Unit) {
        var directionIndex = 0
        var state = latestVisualState.value
        targetGazeDirection.value = state.gazeDirections.first()
        delayUntilStateChangesOrTimeout(state.gazeInitialDelayMillis) { latestVisualState.value }

        while (true) {
            state = latestVisualState.value
            val directions = state.gazeDirections

            // 상태별 시선 후보를 순환한다. 랜덤을 쓰지 않는 이유는 recomposition/테스트 시점마다 다른 결과가
            // 나와 디버깅이 어려워지는 것을 피하고, 각 상태의 인상을 의도한 순서로 유지하기 위해서다.
            directionIndex += 1
            targetGazeDirection.value = directions[directionIndex % directions.size]
            val stateChanged = delayUntilStateChangesOrTimeout(state.gazeHoldMillis) { latestVisualState.value }

            if (stateChanged) {
                // 새 상태가 현재 gaze를 허용하지 않으면 가장 안전한 첫 방향으로 보간한다.
                // Listening/Speaking처럼 Center 고정 상태에서는 이 분기가 눈동자를 정면으로 천천히 돌려준다.
                state = latestVisualState.value
                val nextDirections = state.gazeDirections
                directionIndex = nextDirections.indexOf(targetGazeDirection.value).takeIf { it >= 0 } ?: 0
                targetGazeDirection.value = nextDirections[directionIndex % nextDirections.size]
                delayUntilStateChangesOrTimeout(state.gazeInitialDelayMillis) { latestVisualState.value }
            }
        }
    }

    // Blink 역시 상태 변경으로 coroutine을 재시작하지 않는다.
    // 대신 상태가 바뀌면 현재 눈 높이는 유지하고, 다음 blink 타이밍만 새 상태 기준으로 조정한다.
    LaunchedEffect(Unit) {
        blinkTarget.floatValue = 0f
        delayUntilStateChangesOrTimeout(latestVisualState.value.blinkInitialDelayMillis) {
            latestVisualState.value
        }

        while (true) {
            val state = latestVisualState.value
            val activeLevel = when (state) {
                VoiceCharacterState.Listening -> currentInputMeterLevel
                VoiceCharacterState.Speaking -> currentOutputMeterLevel
                else -> 0f
            }
            // 사용자가 말하거나 AI가 말하는 순간에는 level meter와 jump가 주 피드백이다.
            // 음성 레벨이 높을수록 다음 blink를 늦춰 눈 깜빡임이 음성 반응을 덮지 않게 한다.
            val activeDelayBoost = if (activeLevel > 0.55f) 1_200L else 0L
            val stateChanged = delayUntilStateChangesOrTimeout(state.blinkIntervalMillis + activeDelayBoost) {
                latestVisualState.value
            }

            if (stateChanged) {
                // 상태 전환 직후 바로 깜빡이면 gaze/ring/bounce 변화와 겹쳐 산만하다.
                // coroutine을 재시작하지 않고 새 상태의 초기 지연만 적용해 motion continuity를 유지한다.
                blinkTarget.floatValue = 0f
                delayUntilStateChangesOrTimeout(latestVisualState.value.blinkInitialDelayMillis) {
                    latestVisualState.value
                }
                continue
            }

            blinkTarget.floatValue = 1f
            delay(70L)
            blinkTarget.floatValue = 0f
        }
    }

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
        orbSquashStretch = visualState.resolveOrbSquashStretch(orbLift = orbLift),
        eyeOffset = Offset(eyeOffsetX, eyeOffsetY),
        blinkProgress = blinkProgress,
        glowBoost = glowBoost,
        waveformPhase = waveformPhase,
        orangeRingAlpha = orangeRingAlpha,
        pinkRingAlpha = pinkRingAlpha,
        blueRingAlpha = blueRingAlpha,
        purpleRingAlpha = purpleRingAlpha,
        ringGlowMultiplier = ringGlowMultiplier,
        disabledAlpha = disabledAlpha
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 안내 문구는 하단 micStatusMessage 한 곳에서만 제공한다.
            // 중앙 컴포넌트는 캐릭터 그래픽만 담당해야 중복 안내와 상태 불일치 가능성을 줄일 수 있다.
            .heightIn(max = 350.dp),
        contentAlignment = Alignment.Center
    ) {
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
                    animation = characterAnimation,
                    drawCache = drawCache
                )
            }
        }
    }
}
