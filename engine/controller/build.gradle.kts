plugins {
    alias(libs.plugins.pokermaster.jvm.library)
    // Phase5-I: LlmDecision JSON 파싱 + PromptFormatter 직렬화용.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.engine.rules)
    implementation(projects.engine.decision)
    // Phase5-I: LlmEngine/GenerationConfig/JsonGrammar 타입 노출 (suspend advisor).
    api(projects.engine.llmApi)
    implementation(libs.kotlinx.serialization.json)
}

// :engine:controller 는 pure Kotlin (coroutines 의존 없음).
// StateFlow / ViewModel wrapping 은 feature 레이어(:feature:table VM)에서 수행.
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
