package com.example.umma.domain.model.learningstate

/**
 * 학습 상태 전역에서 사용하는 가장 기본적인 코드와 동기화 상태.
 */
enum class LangCode(val code: String) {
    KO("ko"),
    EN("en"),
    JA("ja"),
    ES("es"),

    // 미지원/오염 상태 표현용. 정상 학습 언어로는 사용하지 않는다.
    //   - selectedLang 이 어떤 이유(앱 다운그레이드, DB 마이그레이션 실패, 외부 소스 오염 등)로
    //     유효 범위를 벗어났을 때를 시뮬레이션/표현하기 위한 값.
    //   - learningLangs 에 절대 추가하지 않는다. UI/카드 데이터 렌더링 시에도 노출 금지.
    //   - DASH-006 AC 9 fallback 분기(selectedLang ∉ learningLangs)가 이 값을 정확히
    //     처리하도록 보장. FakeFixtures.corruptedSelectedLang 가 대표 사용처.
    UNKNOWN("unknown");

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
