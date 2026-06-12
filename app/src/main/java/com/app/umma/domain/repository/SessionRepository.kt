package com.app.umma.domain.repository

/**
 * 중복 로그인 방지(단일 활성 세션)를 위한 로컬/서버 세션 정보 처리 레포지토리
 */
interface SessionRepository {

    /**
     * 로컬에 저장된 sessionId 조회 (없으면 null)
     */
    fun getLocalSessionId(): String?

    /**
     * 로컬에 sessionId 저장
     */
    fun saveLocalSessionId(sessionId: String)

    /**
     * 로컬에 저장된 sessionId 제거
     */
    fun clearLocalSessionId()

    /**
     * 회원탈퇴 시 로컬 인증 세션 흔적을 모두 제거한다.
     *
     * 일반 로그아웃은 강제 로그아웃 안내 플래그를 유지해야 하므로 [clearLocalSessionId]만 사용한다.
     * 계정 삭제는 다음 계정으로 잔여 안내가 전파되지 않도록 sessionId와 안내 플래그를 함께 지운다.
     */
    fun clearLocalAccountSessionState() {
        clearLocalSessionId()
    }

    /**
     * claimLoginSession Cloud Function 호출.
     * 서버에서 새 sessionId를 발급받아 로컬에 저장하고, 잔여 force-logout 안내 플래그를 제거한다.
     * 실패해도 로그인 흐름 자체를 막지 않는 best-effort 동작.
     */
    suspend fun claimSession(): Result<Unit>

    /**
     * Firestore의 users/{uid}.activeSession.sessionId와 로컬 sessionId를 비교한다.
     * activeSession이 없거나 로컬 값과 같으면 true(세션 유효), 다르면 false(다른 기기에서 claim됨).
     * 로컬 sessionId가 없으면(직전 claim 실패 등) "밀려남"으로 단정하지 않고 재claim을 시도한 뒤
     * true를 반환한다(서버를 단일 진실 공급원으로 보고 자기 자신을 오탐 강제 로그아웃하지 않음).
     */
    suspend fun isCurrentSessionActive(uid: String): Result<Boolean>

    /**
     * 강제 로그아웃 FCM 수신 시 호출.
     * 다음 앱 진입 시 안내 다이얼로그를 표시하라는 로컬 플래그를 저장한다.
     */
    fun markPendingForceLogoutNotice()

    /**
     * 대기 중인 강제 로그아웃 안내 플래그를 읽고 즉시 제거한다.
     * @return 안내가 대기 중이었으면 true
     */
    fun consumePendingForceLogoutNotice(): Boolean
}
