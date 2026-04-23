plugins {
    alias(libs.plugins.pokermaster.android.application)
    alias(libs.plugins.pokermaster.android.compose)
    alias(libs.plugins.pokermaster.android.hilt)
}

android {
    namespace = "com.infocar.pokermaster"
    // v1.1 §5.5: 16KB page size 대응 위해 NDK r27+ 필요. 28.2 사용.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.infocar.pokermaster"
        versionCode = 1
        versionName = "0.1.0"

        // arm64-v8a 단일 ABI (ADR-008). x86_64 는 디버그 emulator 지원이 필요하면 추가.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }

        // 사용자 데이터 보호 정책
        // - 모델/통계는 userdata 영역에 보존되며 backup 제외(M5 dataExtractionRules 추가 예정)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // M0: x86_64 emulator 지원이 필요할 경우 ABI 분기 - 일단 disable
    // splits { abi { isEnable = true; reset(); include("arm64-v8a"); isUniversalApk = false } }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.feature.lobby)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
