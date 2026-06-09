package com.app.umma

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release 빌드용 App Check provider 설치자입니다.
 *
 * 배포 빌드는 Firebase Console에 등록한 Play Integrity provider로 Firebase 요청을 증명한다.
 * Debug provider를 release source set에 두지 않아, 운영 APK가 개발용 App Check 우회 경로에 의존하지 않게 한다.
 */
object AppCheckProviderInstaller {

    fun install() {
        // Play Integrity provider는 Google Play 서명 정보와 Console의 App Check 등록 상태를 기준으로 토큰을 발급한다.
        // enforcement를 켜기 전에는 모니터링으로 정상 토큰 수집 여부를 먼저 확인해야 한다.
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
