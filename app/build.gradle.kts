plugins {
    alias(libs.plugins.pokermaster.android.application)
    alias(libs.plugins.pokermaster.android.compose)
    alias(libs.plugins.pokermaster.android.hilt)
    alias(libs.plugins.kotlin.serialization)
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

        // M4-Phase1b: 모델 매니페스트 Ed25519 검증용 개발자 공개키 (Base64, 32 bytes raw).
        // 빈 문자열이면 런타임에서 "unconfigured" 오류. 실제 배포 키는
        // gradle.properties 또는 -P 플래그로 주입:
        //   ./gradlew :app:assembleRelease -PMODEL_MANIFEST_ED25519_PUBKEY_BASE64=...
        val pubKey = providers.gradleProperty("MODEL_MANIFEST_ED25519_PUBKEY_BASE64").orNull ?: ""
        buildConfigField("String", "MODEL_MANIFEST_ED25519_PUBKEY_BASE64", "\"$pubKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            // BouncyCastle multi-release jar 이 OSGI 메타를 포함 → 중복 경로 충돌 방지.
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
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
    implementation(projects.feature.table)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.prov)

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
