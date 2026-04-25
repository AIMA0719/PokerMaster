plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.infocar.pokermaster.core.data"
}

// M7: Room schema export — 차후 migration 작성 시 정답지 비교용.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // M5-A: core:model 의 GameState/Action 을 initialStateJson / actionsJson 직렬화 대상으로 사용.
    api(projects.core.model)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
