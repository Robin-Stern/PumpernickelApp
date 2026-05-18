# External Integrations

**Analysis Date:** 2026-05-18

## APIs & External Services

**Nutrition data:**
- **OpenFoodFacts** — public food/barcode database
  - Client: `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt` (Ktor `HttpClient`)
  - DTOs: `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt`
  - Endpoints called:
    - `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json` — barcode lookup (`OpenFoodFactsApi.kt:15`)
    - `GET https://world.openfoodfacts.org/cgi/search.pl?search_terms=…` — name search (`OpenFoodFactsApi.kt:23`)
  - Auth: none (anonymous public API)
  - Identification header: `User-Agent: PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)` (`OpenFoodFactsApi.kt:16, 24`)
  - Domain consumers: `SearchFoodsRemoteUseCase`, `LookupBarcodeUseCase` (`shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/`)
  - Error handling: HTML responses are detected (`responseText.trimStart().startsWith("<")`) and re-thrown as `IllegalStateException("OpenFoodFacts ist gerade nicht erreichbar.")` (`OpenFoodFactsApi.kt:33-35`)

**AI inference (BYOK — Bring Your Own Key):**
- **OpenAI-compatible Chat Completions** — any provider implementing the OpenAI Chat Completions API
  - Client: `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt`
  - Endpoint shape: `{baseUrl}/chat/completions`
  - Default `baseUrl` in `SettingsRepository`: `https://api.openai.com/v1` (`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt`)
  - Designed to work against OpenAI, Together.AI, OpenRouter, Groq, etc. by overriding the base URL (D-18-06 noted in source comments)
  - Auth: `Authorization: Bearer {apiKey}` — the key is resolved per-request from `SecureKeyStore.readApiKey()` via a `keyProvider: suspend () -> String?` lambda (`AiModule.kt:30`); the client never holds the key
  - Modes: buffered (`chatCompletion`) and SSE streaming (`chatCompletionStreaming`) — UI prefers streaming for live token preview
  - HTTPS-only validation: rejects any non-`https://` base URL with `AiError.SchemaInvalid` (`OpenAICompatibleClient.kt:184-186`)
  - Per-request timeouts: `requestTimeoutMillis = 600_000` (10 min) and `socketTimeoutMillis = 120_000` (2 min between bytes) — sized for slow free-tier LLM generations
  - Response cap: 64 KB; exceeding throws `AiError.SchemaInvalid("response exceeded 64KB cap")`
  - Domain consumers: `WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager` (`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/`)
  - System prompts: `shared/src/commonMain/resources/workout-system-prompt.md`, `recipe-system-prompt.md` (loaded via `AiPromptCatalog`)

**HTTP client (Ktor 3.4.2):**
- Common factory: `expect fun createHttpClient(): HttpClient` in `shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt`
- iOS actual: `HttpClient(Darwin)` with `ContentNegotiation(Json{ignoreUnknownKeys=true; encodeDefaults=true})`, Ktor `HttpTimeout` (600 s request / 120 s socket), and Darwin engine `NSURLSessionConfiguration` overrides (`timeoutIntervalForRequest = 600`, `timeoutIntervalForResource = 600`) — both timeout layers must match; the smaller wins (`shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt`)
- Android actual: `HttpClient(OkHttp)` with the same Ktor timeouts plus OkHttp engine `readTimeout` / `writeTimeout = 600 s` and `connectTimeout = 30 s` (`shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt`)
- A single `HttpClient` is registered as a Koin `single` in `SharedModule.kt:98` and shared by both `OpenFoodFactsApi` and `OpenAICompatibleClient`

## Data Storage

**Database — Room KMP 2.8.4 (SQLite):**
- Definition: `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt`
- Version: 11 with auto-migrations 6→7, 7→8, 8→9, 9→10, 10→11
- Schemas tracked at `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..11}.json`
- Driver: `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`) wired in `SharedModule.kt:77` with `Dispatchers.IO` as the query coroutine context
- Entities: `ExerciseEntity`, `WorkoutTemplateEntity`, `TemplateExerciseEntity`, `ActiveSessionEntity`, `ActiveSessionSetEntity`, `CompletedWorkoutEntity`, `CompletedWorkoutExerciseEntity`, `CompletedWorkoutSetEntity`, `FoodEntity`, `RecipeEntity`, `RecipeIngredientEntity`, `ConsumptionEntryEntity`, `XpLedgerEntity`, `AchievementStateEntity`, `RankStateEntity`, `ProgressPictureEntity`
- DAOs: `ExerciseDao`, `WorkoutTemplateDao`, `WorkoutSessionDao`, `CompletedWorkoutDao`, `NutritionDao`, `GamificationDao`, `ProgressPictureDao` (all exposed via Koin in `SharedModule.kt:81-85`)
- Platform paths:
  - Android: `context.getDatabasePath("pumpernickel.db")` (`shared/src/androidMain/kotlin/com/pumpernickel/platform/Database.android.kt`), `.fallbackToDestructiveMigration(dropAllTables = true)`
  - iOS: `NSHomeDirectory() + "/Documents/pumpernickel.db"` (`shared/src/iosMain/kotlin/com/pumpernickel/data/db/Database.ios.kt`), `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 6)`
- Seeders: `DatabaseSeeder` (loads `free_exercise_db.json`) and `NutritionDataSeeder` (`SharedModule.kt:87-88, 100`)

**Key-value preferences — AndroidX DataStore Preferences 1.2.1:**
- Common factory: `shared/src/commonMain/kotlin/com/pumpernickel/data/preferences/createDataStore.kt` (`expect`/`actual` over `producePath`)
- Android actual: `context.filesDir.resolve(DATA_STORE_FILE_NAME)` (`shared/src/androidMain/kotlin/com/pumpernickel/platform/createDataStore.android.kt`)
- iOS actual: `shared/src/iosMain/kotlin/com/pumpernickel/data/preferences/createDataStore.ios.kt`
- Consumed by `SettingsRepository` for AI base URL, theme/accent, tutorial flag, weight unit, geofence sentinel, etc.

**Secrets — platform-native secure storage:**
- Common contract: `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` (`expect class`)
- Android: `EncryptedSharedPreferences` (AES256_SIV / AES256_GCM, `MasterKeys.AES256_GCM_SPEC`) — file `ai_secrets`, key `openai.api.key` (`SecureKeyStore.android.kt`)
- iOS: Keychain via `Security` framework — service `PumpernickelApp.AI`, account `openai.api.key`, accessibility `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (`SecureKeyStore.ios.kt`)
- Bound in `PlatformModule.{android,ios}.kt`; the BYOK key is the only secret persisted client-side

**File Storage:**
- Progress pictures: `PhotoVault` (platform actuals in `shared/src/androidMain/.../PhotoVault.android.kt` and the iOS equivalent under `shared/src/iosMain/kotlin/com/pumpernickel/data/...`)
- Android `FileProvider` registered with authority `${applicationId}.provider` and paths `@xml/file_paths` (`androidApp/src/androidMain/AndroidManifest.xml`)
- Embedded asset shipped from the shared module: `shared/src/commonMain/resources/free_exercise_db.json` (Android consumes via `assets.srcDirs(project(":shared").file("src/commonMain/resources"))` in `androidApp/build.gradle.kts:23`)
- No remote object storage (S3/Firebase/etc.) detected

**Caching:**
- None beyond Room (offline-first) and DataStore. No Redis/HTTP cache layer detected.

## Authentication & Identity

**User auth:** None — no remote account system. The app is local/offline-first per `CLAUDE.md` constraints.

**Biometric gate (progress pictures):**
- Common: `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt` (`expect class`)
- Android: `androidx.biometric:biometric` 1.2.0-alpha05; `<uses-permission android:name="android.permission.USE_BIOMETRIC"/>` in `AndroidManifest.xml`; bridged via `BiometricGateActivityHolder.kt`
- iOS: `NSFaceIDUsageDescription = "Fortschrittsbild entsperren."` in `iosApp/iosApp/Info.plist`

**AI key (BYOK):** see "Secrets" above — user pastes their own provider key; the app never sends it anywhere except as `Authorization: Bearer …` to the user-configured base URL.

## Monitoring & Observability

**Error Tracking:** None (no Sentry/Crashlytics/Bugsnag dependencies in `libs.versions.toml`).

**Logs:**
- Kotlin `println(...)` statements throughout the data layer for tracing (e.g. `[OFF] …` in `OpenFoodFactsApi.kt`, `[AI] …` in `OpenAICompatibleClient.kt`, `[SecureKeyStore.ios] …` in `SecureKeyStore.ios.kt`)
- iOS Swift logging via `print(...)` in `AppDelegate.swift`
- No structured logging framework configured

## CI/CD & Deployment

**Hosting:** Mobile apps — no server component.

**CI Pipeline:** None detected — no `.github/workflows/`, `.gitlab-ci.yml`, `bitrise.yml`, `fastlane/`, or `Jenkinsfile` at the repo root.

**Build outputs:**
- Android: `:androidApp:assembleDebug` / `assembleRelease` (no `R8/minify` enabled — `isMinifyEnabled = false` for both build types in `androidApp/build.gradle.kts:37-44`)
- iOS: built from Xcode against `iosApp/iosApp.xcodeproj`; consumes `shared/build/xcode-frameworks/{Debug,Release}/…/Shared.framework`

## Environment Configuration

**Required env vars at runtime:** None — the app is fully local.

**User-supplied configuration (entered at runtime, persisted in secure/local storage):**
- AI API key — stored via `SecureKeyStore` (Keychain on iOS, `EncryptedSharedPreferences` on Android)
- AI base URL — stored via `SettingsRepository` in DataStore, defaults to `https://api.openai.com/v1`

**Build-time configuration:**
- `iosApp/Configuration/Config.xcconfig` — Xcode bundle id / version, `TEAM_ID` placeholder for signing
- `local.properties` (gitignored by convention) — Android SDK path

**Secrets location:** No checked-in secrets. `SecureKeyStore` holds the only persistent credential, and it is per-device only.

## Webhooks & Callbacks

**Incoming (system-driven, not network):**
- Android `GeofenceBroadcastReceiver` — registered in `AndroidManifest.xml` for action `com.pumpernickel.geofence.TRANSITION`, exported=false (`shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/GeofenceBroadcastReceiver.kt`)
- Android `AiGenerationService` — foreground service with `foregroundServiceType="dataSync"` for long-running AI generation (`androidApp/src/androidMain/kotlin/com/pumpernickel/android/AiGenerationService.kt`)
- iOS BGTaskScheduler — identifier `com.pumpernickel.ai_generation` registered via `AiBgTaskRegistrarKt.registerAiBackgroundTask()` from `iosApp/iosApp/PumpernickelApp.swift:16`; declared in `Info.plist` under `BGTaskSchedulerPermittedIdentifiers`
- iOS `UNUserNotificationCenter` — delegate set in `AppDelegate.swift`; foreground presentation `[.banner, .sound, .list]`; authorization requested for `[.alert, .sound, .badge]`
- iOS background mode `location` declared in `Info.plist` (`UIBackgroundModes`) for geofence wake-ups; `CLLocationManager` instantiated eagerly in `PumpernickelApp.init()` to request `whenInUseAuthorization`

**Outgoing HTTP:** Only the two external services described above (OpenFoodFacts, the user's chosen OpenAI-compatible endpoint).

## System & Device Integrations

**Android (`androidApp/src/androidMain/AndroidManifest.xml`):**
- Permissions: `CAMERA`, `INTERNET`, `USE_BIOMETRIC`, `VIBRATE`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- Features: `<uses-feature android:name="android.hardware.camera" required="false"/>`
- CameraX 1.6.0 (`camera-camera2`, `camera-lifecycle`, `camera-view`) — used by `NutritionFoodEntryScreen.kt` for live barcode preview
- ML Kit barcode scanning 17.3.0 — `BarcodeScannerOptions`, `BarcodeScanning`, `InputImage` in `NutritionFoodEntryScreen.kt`
- Google Play Services Location 21.3.0 — `AndroidLocationProvider` (`shared/src/androidMain/kotlin/com/pumpernickel/feature/location/AndroidLocationProvider.kt`), `AndroidGeofenceProvider` (`shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/AndroidGeofenceProvider.kt`)
- `androidx.security:security-crypto` 1.0.0 — encrypted preferences for the BYOK key
- `androidx.biometric:biometric` 1.2.0-alpha05 — biometric gate for the progress-picture viewer
- `BuildConfig.DEBUG`-only override: `DebugGeofenceProvider` swapped in via `loadKoinModules` for emulator testing (`PumpernickelApplication.kt:25-31`)

**iOS (`iosApp/iosApp/Info.plist`):**
- Usage strings: `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSFaceIDUsageDescription`, `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription`
- `CADisableMinimumFrameDurationOnPhone = true` (allows >60 Hz rendering on ProMotion devices)
- Background modes: `location`
- Frameworks consumed from `iosApp/iosApp/*.swift`: `SwiftUI`, `UIKit`, `CoreLocation`, `UserNotifications`, plus the Kotlin `Shared` framework via `import Shared` and `KMPNativeCoroutinesAsync` (Swift Package, provided by the `kmp-nativecoroutines` plugin) for `asyncSequence(for:)` bridging of `Flow`/`StateFlow`
- Keychain access via Kotlin/Native `platform.Security.*` in `SecureKeyStore.ios.kt`
- Geofencing via `IosGeofenceProvider` (`shared/src/iosMain/kotlin/com/pumpernickel/data/geofence/IosGeofenceProvider.kt`), permissions via `IosPermissionController.kt`

**Dependency Injection bootstrap:**
- Common entry: `initKoin { … }` in `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt:138`
- Android: `PumpernickelApplication.onCreate()` calls `initKoin { androidContext(this) }` (`androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt:18-21`)
- iOS: `AppDelegate.application(_:didFinishLaunchingWithOptions:)` calls `KoinInitIosKt.doInitKoinIos()` before SwiftUI mounts (`iosApp/iosApp/AppDelegate.swift:29`); a suite of `*KoinHelper.kt` objects in `shared/src/iosMain/kotlin/com/pumpernickel/di/` exposes specific ViewModels/providers to Swift (e.g. `KoinHelper.kt`, `WorkoutAiKoinHelper.kt`, `RecipeAiKoinHelper.kt`, `ProgressGalleryKoinHelper.kt`)

---

*Integration audit: 2026-05-18*
