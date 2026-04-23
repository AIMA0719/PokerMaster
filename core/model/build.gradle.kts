plugins {
    alias(libs.plugins.pokermaster.jvm.library)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
