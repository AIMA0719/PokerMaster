plugins {
    alias(libs.plugins.pokermaster.android.library)
    alias(libs.plugins.pokermaster.android.compose)
    alias(libs.plugins.pokermaster.android.hilt)
}

android {
    namespace = "com.infocar.pokermaster.feature.lobby"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    // M6-C: WalletRepository + LobbyViewModel.
    implementation(projects.core.data)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
}
