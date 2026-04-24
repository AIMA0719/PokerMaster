plugins {
    alias(libs.plugins.pokermaster.jvm.library)
}

dependencies {
    // LlmEngine 의 suspend API 를 서명으로 노출하므로 coroutines-core 는 API 의존성.
    api(libs.kotlinx.coroutines.android)
}

// JUnit5 testing.suites DSL (Gradle 9.0 호환, 자동 dependency 등록 deprecation 회피)
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junitJupiter.get())
            dependencies {
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.params)
                implementation(libs.truth)
                // Phase3c-III: LlmSessionTest 의 runTest + StateFlow 관찰용.
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
