package com.app.umma.di

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase 관련 SDK 인스턴스를 앱 전역에 제공하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseAppCheck(): FirebaseAppCheck {
        // App Check provider 설치는 Application의 build type source set에서 먼저 수행한다.
        // 이 모듈은 OkHttp로 직접 호출하는 서버 endpoint에 token을 붙일 수 있도록 SDK handle만 제공한다.
        return Firebase.appCheck
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
//        return FirebaseFirestore.getInstance()
        return FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            "default"
        )
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions {
        return FirebaseFunctions.getInstance("us-central1")
    }
}
