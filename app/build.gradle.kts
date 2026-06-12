import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.app.umma"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.umma"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }
        // Android 앱은 OpenAI API key 를 직접 갖지 않는다.
        // 이 URL 은 Firebase Cloud Function 이 발급하는 short-lived Realtime client secret endpoint 다.
        val openAiRealtimeTokenUrl = properties.getProperty("OPENAI_REALTIME_TOKEN_URL") ?: ""
        // Usage sync 는 Realtime token 발급과 다른 Cloud Function 이 담당한다.
        // Cloud Run URL 과 cloudfunctions.net URL 은 형태가 달라 문자열 치환에 의존하지 않고 명시 값으로 분리한다.
        val openAiUsageSyncUrl = properties.getProperty("OPENAI_USAGE_SYNC_URL") ?: ""
        // 전환 후보 모델은 문서와 백로그에서 결정한 gpt-realtime-mini 로 고정하되,
        // local.properties 로만 바꿀 수 있게 해 코드 변경 없이 비교 테스트할 수 있게 한다.
        val openAiRealtimeModel = properties.getProperty(
            "OPENAI_REALTIME_MODEL"
        ) ?: "gpt-realtime-mini"
        // OpenAI Realtime WebSocket endpoint 다.
        // 공식 endpoint 변경 또는 프록시 검증이 필요할 때만 local.properties 에서 override 한다.
        val openAiRealtimeWebSocketUrl = properties.getProperty(
            "OPENAI_REALTIME_WS_URL"
        ) ?: "wss://api.openai.com/v1/realtime"
        buildConfigField("String", "OPENAI_REALTIME_TOKEN_URL", "\"$openAiRealtimeTokenUrl\"")
        buildConfigField("String", "OPENAI_USAGE_SYNC_URL", "\"$openAiUsageSyncUrl\"")
        buildConfigField("String", "OPENAI_REALTIME_MODEL", "\"$openAiRealtimeModel\"")
        buildConfigField(
            "String",
            "OPENAI_REALTIME_WS_URL",
            "\"$openAiRealtimeWebSocketUrl\""
        )
        // 개발용 프롬프트 리뷰 자료수집 스위치입니다.
        // 현재 프롬프트 튜닝 단계에서는 debug build 기본값을 true로 두고,
        // 필요 시 local.properties에서 CHAT_PROMPT_REVIEW_ENABLED=false로 끌 수 있게 합니다.
        val chatPromptReviewEnabled = properties.getProperty(
            "CHAT_PROMPT_REVIEW_ENABLED"
        ) ?: "true"
        buildConfigField("boolean", "CHAT_PROMPT_REVIEW_ENABLED", chatPromptReviewEnabled)
        // Correction prompt review is a separate dev-only switch so COR review data does not
        // accidentally share the Chat prompt-review lifecycle or storage policy.
        val correctionPromptReviewEnabled = properties.getProperty(
            "CORRECTION_PROMPT_REVIEW_ENABLED"
        ) ?: "true"
        buildConfigField("boolean", "CORRECTION_PROMPT_REVIEW_ENABLED", correctionPromptReviewEnabled)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "data"
    productFlavors {
        create("dev") {
            dimension = "data"
        }
        create("mock") {
            dimension = "data"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(
        selector()
            .withFlavor("data" to "mock")
            .withBuildType("release")
    ) { variantBuilder ->
        // mock은 화면 검증용이므로 release 변형을 만들지 않는다.
        // 배포 후보는 devRelease만 사용한다.
        variantBuilder.enable = false
    }
}

dependencies {
    implementation(project(":watchbridge-contract"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.ui)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.icons)
    implementation(libs.androidx.core.splashscreen)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Navigation
    implementation(libs.navigation.compose)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Firebase & AI & Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.wearable)

    // Google Credential Manager (Auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
