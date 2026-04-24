plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.koin.android)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.pumpernickel.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pumpernickel.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.kotlinx.datetime)
    implementation("com.google.guava:guava:33.4.0-android")
}
