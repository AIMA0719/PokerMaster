pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PokerMaster"

// type-safe project accessors: build.gradle.kts 에서 `projects.core.model` 형태로 참조 가능
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core (M0)
include(":core:model")
include(":core:ui")
// M5-A: Room 기반 핸드 히스토리 저장소 활성화 (§1.2.G/§1.2.H).
include(":core:data")

// Engine
include(":engine:rules")        // M1
include(":engine:decision")     // M2
include(":engine:controller")   // M3 (M1 보강): GameStateReducer + AI driver
include(":engine:llm")          // M4-Phase2: llama.cpp JNI
include(":engine:llm-api")

// Feature
include(":feature:lobby")       // M0: 빈 LobbyScreen
include(":feature:table")       // M3
include(":feature:onboarding")  // M3 (v1.1 §1.2.B)
include(":feature:history")     // M5: 핸드 히스토리 리스트 + 리플레이 (§1.2.G/H/R)
// include(":feature:training") // 스코프 제외 (M5 결정)
// include(":feature:settings") // M3+

// PAD: AI 모델 에셋팩 (install-time). Release AAB 에 모델 포함.
include(":model-pack")
