plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kmp.nativecoroutines) apply false
    alias(libs.plugins.compose.compiler) apply false
}
