plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
    alias(libs.plugins.pokermaster.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.infocar.pokermaster.feature.history"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    // M5-C/D 에서 리플레이 시 HoldemReducer 재구동.
    implementation(projects.engine.rules)
    implementation(projects.engine.controller)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
