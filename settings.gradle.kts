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
// include(":core:data")        // M1: Room 들어갈 때 활성화

// Engine
include(":engine:rules")        // M1
include(":engine:decision")     // M2
include(":engine:controller")   // M3 (M1 보강): GameStateReducer + AI driver
// include(":engine:llm")       // M4

// Feature
include(":feature:lobby")       // M0: 빈 LobbyScreen
include(":feature:table")       // M3
include(":feature:onboarding")  // M3 (v1.1 §1.2.B)
// include(":feature:history")  // M5
// include(":feature:training") // M5
// include(":feature:settings") // M3+
