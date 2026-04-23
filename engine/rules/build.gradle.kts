plugins {
    alias(libs.plugins.pokermaster.jvm.library)
}

dependencies {
    implementation(projects.core.model)
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
            }
        }
    }
}
