plugins {
    `kotlin-dsl`
}

group = "com.infocar.pokermaster.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // AGP / Kotlin / Compose / Hilt / KSP 플러그인을 컨벤션 빌드 안에서 참조하기 위함
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:${libs.versions.hilt.get()}")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pokermaster.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "pokermaster.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "pokermaster.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "pokermaster.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "pokermaster.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
