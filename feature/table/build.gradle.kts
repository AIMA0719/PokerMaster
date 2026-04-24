plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
    alias(libs.plugins.pokermaster.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.infocar.pokermaster.feature.table"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    // M5-B / M6-A: HandHistoryRepository + 설정 화면이 Hilt VM 으로 사용.
    implementation(projects.core.data)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(projects.engine.rules)
    implementation(projects.engine.decision)
    implementation(projects.engine.controller)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
