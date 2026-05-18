package com.example.umma.domain.model.learningstate

/**
 * Session Memory recentFullContext 압축 요청 입력.
 *
 * 실제 turn list 전체를 여기서 다루지 않고, 압축이 발생했음을 나타내는 최소 계약만 전달한다.
 */
data class SessionMemoryCompressionInput(
    // 사용자 식별자.
    val uid: String,
    // 현재 선택 언어.
    val lang: LangCode,
    // 압축 대상 Session Memory 키.
    val sessionMemoryKey: String,
    // 압축 요청 시각.
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * Session Memory 압축 결과를 나타내는 최소 결과 모델.
 */
data class SessionMemoryCompressionResult(
    // 어떤 Session Memory가 압축되었는지 추적하기 위한 키.
    val sessionMemoryKey: String,
    // 압축이 반영된 시각.
    val compressedAt: Long,
    // 실제 구현에서는 recentFullContext 정리 여부를 표현한다.
    val recentFullContextCompacted: Boolean = true
)
