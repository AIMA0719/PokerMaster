plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
}

android {
    namespace = "com.infocar.pokermaster.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
