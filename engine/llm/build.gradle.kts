plugins {
    alias(libs.plugins.pokermaster.android.library)
    // Phase3a: Hilt @Module/@Singleton 으로 LlmEngine 을 Application scope 주입.
    alias(libs.plugins.pokermaster.android.hilt)
}

android {
    namespace = "com.infocar.pokermaster.engine.llm"
    // v1.1 §5.5: 16KB page size 대응 — NDK r27+ 필요, 28.2 사용 (app 과 동일).
    ndkVersion = "28.2.13676358"

    defaultConfig {
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_TOOLCHAIN=clang",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}

dependencies {
    // Phase3a: 공개 API (LlmEngine 타입이 함수 시그니처에 노출) 는 api() 로 propagate.
    api(projects.engine.llmApi)

    implementation(projects.core.model)
    // Phase2c §5.6: LlamaCppEngine suspend API + single-thread dispatcher.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
