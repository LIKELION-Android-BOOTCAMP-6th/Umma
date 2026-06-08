package com.app.umma

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UmmaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // App Check provider는 Firestore/Firebase AI/Functions 인스턴스가 만들어지기 전에 설치되어야 한다.
        // 실제 provider 선택은 buildType별 source set에서 분리해 release APK에 debug provider 의존이 섞이지 않게 한다.
        AppCheckProviderInstaller.install()
    }
}
