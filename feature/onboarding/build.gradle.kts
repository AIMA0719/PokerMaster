plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
}

android {
    namespace = "com.infocar.pokermaster.feature.onboarding"
}

dependencies {
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
