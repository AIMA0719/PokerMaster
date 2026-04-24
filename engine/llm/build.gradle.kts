plugins {
    alias(libs.plugins.pokermaster.android.library)
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
    implementation(projects.core.model)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
