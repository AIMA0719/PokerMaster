plugins {
    alias(libs.plugins.pokermaster.jvm.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.engine.rules)
}

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
