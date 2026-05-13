package com.example.umma.domain.model.learningstate

/**
 * 학습 상태 전역에서 사용하는 가장 기본적인 코드와 동기화 상태.
 */
enum class LangCode(val code: String) {
    KO("ko"),
    EN("en"),
    JA("ja"),
    ES("es");

    companion object {
        // Firestore나 문자열 입력을 enum으로 되돌릴 때 사용한다.
        fun fromCode(code: String): LangCode? {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}

/**
 * 로컬/원격 동기화의 현재 상태를 나타내는 경량 플래그.
 */
enum class SyncStatus {
    // 로컬과 원격 값이 같은 상태.
    SYNCED,
    // 아직 원격 반영이 끝나지 않은 상태.
    PENDING,
    // 동기화 실패 후 재시도가 필요한 상태.
    FAILED
}
