package com.app.umma.presentation.util

import kotlin.math.sqrt

/**
 * 16-bit PCM little-endian 오디오 바이트에서 상대 강도 값을 계산합니다.
 *
 * 계산 방식:
 * - 2바이트씩 Short 샘플로 해석
 * - [-1.0, 1.0] 범위로 정규화
 * - RMS를 계산
 * - 최종 결과를 0f..1f 범위로 clamp
 *
 * 이 값은 절대 음량이 아니라 UI 효과용 상대 강도입니다.
 *
 * @param audioBytes PCM 16-bit little-endian 바이트 배열
 * @return 0f..1f 범위의 상대 강도
 */
fun calculateLevel(audioBytes: ByteArray): Float {
    if (audioBytes.isEmpty()) return 0f

    var sumSquares = 0.0
    var sampleCount = 0

    var i = 0
    while (i + 1 < audioBytes.size) {
        val sample = ((audioBytes[i + 1].toInt() shl 8) or (audioBytes[i].toInt() and 0xFF)).toShort()
        val normalized = sample / 32768.0
        sumSquares += normalized * normalized
        sampleCount++
        i += 2
    }

    if (sampleCount == 0) return 0f

    val rms = sqrt(sumSquares / sampleCount)
    return rms.coerceIn(0.0, 1.0).toFloat()
}