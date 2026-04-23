plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
}

android {
    namespace = "com.infocar.pokermaster.feature.lobby"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
