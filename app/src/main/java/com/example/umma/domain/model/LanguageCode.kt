package com.example.umma.domain.model

/**
 * 앱 전체에서 사용하는 학습 언어 식별자.
 *
 * LS-003에서 UserLearningPreference는 "현재 앱이 어느 언어를 바라보는지"를
 * 안정적으로 표현해야 하므로, 문자열을 직접 흩뿌리지 않고 enum으로 언어 코드를
 * 고정한다.
 *
 * Firestore / Local persist 에서는 [code] 값을 저장하고,
 * Domain 내부에서는 이 enum을 사용해 오타와 분기 실수를 줄인다.
 */
enum class LanguageCode(val code: String) {
    KO("ko"),
    EN("en"),
    JA("ja"),
    ES("es");

    companion object {
        /**
         * 외부 저장소에서 읽은 문자열을 LanguageCode로 복원한다.
         *
         * 지원하지 않는 코드는 null을 반환해, 상위 계층에서 Initial Setup
         * 필요 상태나 fallback 정책을 적용할 수 있게 한다.
         */
        fun fromCode(code: String): LanguageCode? {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}
