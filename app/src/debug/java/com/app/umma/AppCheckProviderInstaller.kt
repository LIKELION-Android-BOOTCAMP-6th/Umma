package com.app.umma

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug 빌드용 App Check provider 설치자입니다.
 *
 * 개발 중 Android Studio Run으로 설치한 앱은 Play Console의 PLAY_RECOGNIZED 조건을 통과하지 못할 수 있다.
 * 그래서 debug 빌드는 Debug provider를 사용하고, enforcement 적용 전에는 Firebase Console에 debug token을
 * 등록해 팀원 개발 환경이 막히지 않게 해야 한다.
 */
object AppCheckProviderInstaller {

    fun install() {
        // Firebase SDK가 Firestore/Firebase AI/Functions 요청을 만들기 전에 debug provider를 먼저 등록한다.
        // 이 provider는 Logcat에 debug token을 출력하므로, enforcement 전에 해당 token을 Console에 등록해야 한다.
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
