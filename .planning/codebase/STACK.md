# Technology Stack

**Analysis Date:** 2026-05-18

## Languages

**Primary:**
- Kotlin 2.3.20 — shared business logic, data layer, ViewModels, and Compose Multiplatform UI (`shared/`, `androidApp/`)
- Swift (Xcode toolchain implied by `iosApp/iosApp.xcodeproj`) — iOS app shell, SwiftUI views, platform glue (`iosApp/iosApp/*.swift`)

**Secondary:**
- Kotlin DSL (Gradle build scripts) — `build.gradle.kts`, `settings.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`
- XML — Android manifest, resources (`androidApp/src/androidMain/AndroidManifest.xml`, `androidApp/src/main/res/values/strings.xml`)
- Markdown — system prompts for AI features (`shared/src/commonMain/resources/workout-system-prompt.md`, `recipe-system-prompt.md`)
- JSON — seed data and Room schemas (`shared/src/commonMain/resources/free_exercise_db.json`, `shared/schemas/com.pumpernickel.data.db.AppDatabase/*.json`)

## Runtime

**Environment:**
- Kotlin/JVM (Android target) — Java 17 source/target compatibility (`androidApp/build.gradle.kts:27-28`)
- Kotlin/Native (iOS targets) — `iosArm64`, `iosSimulatorArm64`, `iosX64` produce a static `Shared.framework` (`shared/build.gradle.kts:17-26`)
- Android SDK — `compileSdk = 35` for `:shared`, `compileSdk = 36` / `targetSdk = 36` / `minSdk = 26` for `:androidApp`
- iOS deployment target — driven by Xcode (`iosApp/Configuration/Config.xcconfig`); Info.plist references iOS-26-era frameworks (`UNUserNotificationCenter`, `BGTaskScheduler`)

**Package Manager:**
- Gradle 8.x+ via the wrapper (`gradlew`, `gradlew.bat`); Kotlin DSL
- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` enabled in `settings.gradle.kts:1`
- Version catalog: `gradle/libs.versions.toml` (single source of truth for versions)
- Plugin resolution via `google()`, `mavenCentral()`, `gradlePluginPortal()`
- Foojay JDK toolchain resolver 0.10.0 (`settings.gradle.kts:11`)
- Lockfile: not present

## Frameworks

**Core:**
- Compose Multiplatform via Jetpack Compose BOM `2025.06.00` on Android (`compose-bom` in `libs.versions.toml:15`)
- Compose compiler plugin tied to Kotlin 2.3.20 (`org.jetbrains.kotlin.plugin.compose`, `libs.versions.toml:69`)
- Compose modules used in `:androidApp`: `compose.ui`, `compose.ui.tooling.preview`, `compose.material3`, `compose.foundation`, `compose.runtime`, `compose.material-icons-extended`, `activity-compose` 1.10.1 (`androidApp/build.gradle.kts:60-66`)
- SwiftUI on iOS — `iosApp/iosApp/Views/**/*.swift` consume the Kotlin `Shared` framework

**Shared App Architecture:**
- Jetpack ViewModel KMP `2.10.0` — `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` in commonMain (`shared/build.gradle.kts:48`)
- Navigation Compose KMP `2.9.2` — `androidx.navigation:navigation-compose` (Android-only consumer at `androidApp/build.gradle.kts:67`; routes in `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt`)
- Koin `4.2.0` — runtime DI; `koin-core` + `koin-compose-viewmodel` in commonMain, `koin-android` in androidMain (`shared/build.gradle.kts:43-44, 55`)
- kotlinx.coroutines `1.10.2` — `kotlinx-coroutines-core` in commonMain
- kotlinx.serialization `1.10.0` — `kotlinx-serialization-json` (used for Ktor content negotiation, navigation routes, and AI request/response DTOs)
- kotlinx-datetime `0.7.1` — timestamps for workouts, nutrition logs, gamification

**Testing:**
- `kotlin.test` from the stdlib — only test framework currently used in `shared/src/commonTest` (e.g. `RankLadderTest.kt`, `XpFormulaTest.kt`, `StreakCalculatorTest.kt`, `TdeeCalculatorTest.kt`)
- Turbine and Kotest declared in `CLAUDE.md` as the recommended stack but **not** currently wired into `gradle/libs.versions.toml` or `shared/build.gradle.kts`

**Build/Dev:**
- Android Gradle Plugin (AGP) `9.1.1` — `com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library` (`libs.versions.toml:3, 60-64`)
- KSP `2.3.6` — Kotlin Symbol Processing; runs `androidx.room:room-compiler` for every active target (`shared/build.gradle.kts:72-77`)
- Room Gradle plugin `2.8.4` — applied with `schemaDirectory = "$projectDir/schemas"` (`shared/build.gradle.kts:68-70`)
- KMP-NativeCoroutines `1.0.2` — Rick Clephas plugin (`com.rickclephas.kmp.nativecoroutines`); generates Swift-friendly `async`/`AsyncSequence` wrappers around `Flow`/`StateFlow` (`libs.versions.toml:13`, used via `@NativeCoroutinesState` / `@NativeCoroutines` annotations in `shared/src/commonMain/kotlin/com/pumpernickel/presentation/**/*ViewModel.kt`)
- `kotlin.plugin.serialization` — applied to `:shared` and `:androidApp`
- `-Xexpect-actual-classes` compiler flag enabled for all targets (`shared/build.gradle.kts:31`, `androidApp/build.gradle.kts:49`)

## Key Dependencies

**Critical (commonMain — `shared/build.gradle.kts:40-53`):**
- `androidx.room:room-runtime` 2.8.4 — KMP-stable Room (`libs.versions.toml:4`)
- `androidx.sqlite:sqlite-bundled` 2.6.2 — bundled SQLite driver, required for Room KMP iOS targets (`libs.versions.toml:5`)
- `io.insert-koin:koin-core` 4.2.0 + `koin-compose-viewmodel` — DI graph wiring (`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` 1.10.2
- `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.10.0
- `org.jetbrains.kotlinx:kotlinx-datetime` 0.7.1
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` 2.10.0
- `androidx.datastore:datastore-preferences` 1.2.1 — key/value settings (`shared/src/commonMain/kotlin/com/pumpernickel/data/preferences/createDataStore.kt`)
- Ktor Client 3.4.2 — `ktor-client-core`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` (used by `OpenFoodFactsApi`, `OpenAICompatibleClient`)

**androidMain — `shared/build.gradle.kts:54-61`:**
- `io.insert-koin:koin-android` 4.2.0
- `io.ktor:ktor-client-okhttp` 3.4.2 — Android HTTP engine (`shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt`)
- `androidx.biometric:biometric` 1.2.0-alpha05 — progress-picture biometric gate
- `androidx.security:security-crypto` 1.0.0 — `EncryptedSharedPreferences` for the BYOK AI key (`shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt`)
- `com.google.android.gms:play-services-location` 21.3.0 — geofencing + fused location

**iosMain — `shared/build.gradle.kts:62-65`:**
- `io.ktor:ktor-client-darwin` 3.4.2 — iOS HTTP engine using `NSURLSession` (`shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt`)

**`:androidApp` — `androidApp/build.gradle.kts:54-75`:**
- Compose BOM 2025.06.00 + the Compose modules listed above
- `androidx.navigation:navigation-compose` 2.9.2
- CameraX 1.6.0 — `camera-camera2`, `camera-lifecycle`, `camera-view`
- ML Kit barcode scanning 17.3.0 — `com.google.mlkit:barcode-scanning` (used in `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionFoodEntryScreen.kt`)
- `androidx.biometric:biometric` 1.2.0-alpha05
- `kotlinx-datetime` 0.7.1
- `com.google.guava:guava:33.4.0-android` (hard-pinned, not in the version catalog — `androidApp/build.gradle.kts:74`)

## Configuration

**Environment:**
- `gradle.properties`: `kotlin.code.style=official`, `android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2g`, plus AGP/Kotlin flags (`android.builtInKotlin=true`, `android.newDsl=true`, `android.r8.optimizedResourceShrinking=true`)
- `local.properties` present at the repo root (Android SDK location only — not analyzed)
- No `.env` files detected at the repo root
- iOS bundle config: `iosApp/Configuration/Config.xcconfig` defines `PRODUCT_NAME=PumpernickelApp`, `PRODUCT_BUNDLE_IDENTIFIER=com.example.pumpernickelapp.PumpernickelApp$(TEAM_ID)`, `MARKETING_VERSION=1.0`, `CURRENT_PROJECT_VERSION=1` (TEAM_ID intentionally blank)
- Android app coordinates: `applicationId = "com.pumpernickel.android"`, `namespace = "com.pumpernickel.android"`, `versionName = "1.0"`, `versionCode = 1` (`androidApp/build.gradle.kts:8-17`)
- Shared module namespace: `com.pumpernickel.shared`

**Build:**
- Root `build.gradle.kts` only declares plugin aliases with `apply false` — actual application happens per-module
- `:shared` applies: `kotlinMultiplatform`, `androidKotlinMultiplatform`, `ksp`, `androidx.room`, `kotlinx.serialization`, `kmp-nativecoroutines`
- `:androidApp` applies: `androidApplication`, `compose-compiler`, `kotlinx.serialization`
- Room schemas committed at `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..11}.json`
- Build features: `buildFeatures.compose = true`, `buildFeatures.buildConfig = true` (`androidApp/build.gradle.kts:31-34`); `BuildConfig.DEBUG` gates `DebugGeofenceProvider` registration

## Platform Requirements

**Development:**
- macOS — required for iOS builds (Xcode, Kotlin/Native iOS targets)
- Xcode (current enough for the iOS 26 SDK paths in `shared/build/xcode-frameworks/.../iphonesimulator26.2/`)
- Android Studio / IntelliJ with the Kotlin Multiplatform plugin
- JDK 17 (Java toolchain compatibility; Gradle JVM args set to `-Xmx2g`)
- Gradle wrapper handles the Gradle version

**Production:**
- Android: APK/AAB targeting Android 14+ (`compileSdk = 36`, `minSdk = 26`)
- iOS: native iOS app (`PumpernickelApp` SwiftUI shell consuming the static `Shared.framework`)
- No backend, no CI/CD configuration detected in the repo

---

*Stack analysis: 2026-05-18*
