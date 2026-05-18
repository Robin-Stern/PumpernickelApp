# Codebase Structure

**Analysis Date:** 2026-05-18

## Directory Layout

```
PumpernickelApp/
├── build.gradle.kts              # Root Gradle (empty allprojects block — config in modules)
├── settings.gradle.kts           # Declares :shared and :androidApp Gradle modules. iosApp is NOT a Gradle module.
├── gradle.properties             # KMP + KSP + Compose flags
├── gradle/libs.versions.toml     # Version catalog (Kotlin 2.3.20, CMP/Compose, Room 2.8.4, Koin 4.2.0, …)
├── CLAUDE.md                     # Project instructions for AI agents
├── README.md
├── USE_CASES.md                  # Documented use cases
├── kls_database.db               # Local SQLite snapshot (committed — likely accidental)
│
├── shared/                       # KMP module — all shared code (data + domain + presentation)
│   ├── build.gradle.kts          # KMP plugin, Room/KSP/Ktor/Koin deps; targets android + iosArm64/iosSimulatorArm64/iosX64
│   ├── schemas/                  # Auto-generated Room schema JSON snapshots
│   └── src/
│       ├── commonMain/kotlin/com/pumpernickel/   # 151 .kt files — see deep tree below
│       ├── commonMain/resources/                  # `free_exercise_db.json` seed data
│       ├── commonTest/kotlin/                     # 7 unit tests (gamification + TDEE calc) — domain-only coverage
│       ├── androidMain/kotlin/com/pumpernickel/   # Android-specific implementations (geofence, biometric, location, …)
│       └── iosMain/kotlin/com/pumpernickel/       # iOS-specific implementations + KoinHelper for Swift
│
├── androidApp/                   # Android UI module — Jetpack Compose
│   ├── build.gradle.kts
│   └── src/
│       ├── androidMain/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/pumpernickel/android/    # MainActivity, screens, components, navigation, theme
│       │   └── res/                                 # mipmap-* launcher icons, xml
│       └── main/res/values/strings.xml             # split between two sourceSets ("androidMain" + "main") — legacy
│
├── iosApp/                       # iOS UI app — NATIVE SwiftUI (NOT Compose Multiplatform)
│   ├── iosApp.xcodeproj/
│   ├── Configuration/
│   └── iosApp/
│       ├── PumpernickelApp.swift, AppDelegate.swift  # entry points
│       ├── Assets.xcassets/                          # AppIcon, AccentColor
│       ├── Extensions/Color+App.swift
│       ├── Theme/ThemeManager.swift
│       ├── Utilities/                                # FlowObservation.swift, NotificationCenter+Geofence.swift
│       └── Views/                                    # 13 feature subfolders × 54 .swift files
│
├── assets/                       # Project-level assets (e.g. app-icon SVG source)
├── evals/                        # Eval harness for AI features
│   ├── cases/, prompts/, runs/
│   └── node_modules/             # (NPM tooling for evals only)
└── .planning/                    # GSD workflow artifacts (not source)
    ├── codebase/, milestones/, phases/, quick/, debug/, research/, seeds/, todos/, notes/
```

### Deep tree — `shared/src/commonMain/kotlin/com/pumpernickel/`

```
com/pumpernickel/
├── Platform.kt                                  # expect class Platform
│
├── data/                                        # Data layer
│   ├── api/                                     # Ktor HTTP + DTOs
│   │   ├── AiChatDto.kt                         # OpenAI-compatible request/response models
│   │   ├── HttpClientFactory.kt                 # expect fun createHttpClient(): HttpClient
│   │   ├── OpenAICompatibleClient.kt            # 209 lines — LLM chat client
│   │   ├── OpenFoodFactsApi.kt                  # Barcode + search endpoints
│   │   ├── OpenFoodFactsDto.kt
│   │   ├── RecipeAiSchema.kt                    # JSON-schema spec for structured AI output (recipe)
│   │   └── WorkoutAiSchema.kt                   # JSON-schema spec for structured AI output (workout template)
│   │
│   ├── db/                                      # Room entities + DAOs + seeders
│   │   ├── AppDatabase.kt                       # @Database, version 11, 16 entities, 5 AutoMigrations
│   │   ├── DatabaseSeeder.kt                    # Loads free_exercise_db.json
│   │   ├── NutritionDataSeeder.kt
│   │   ├── AchievementStateSeeder.kt
│   │   ├── ExerciseDao.kt           + ExerciseEntity.kt
│   │   ├── WorkoutTemplateDao.kt    + WorkoutTemplateEntity.kt + TemplateExerciseEntity.kt
│   │   ├── WorkoutSessionDao.kt     + ActiveSessionEntity.kt + ActiveSessionSetEntity.kt
│   │   ├── CompletedWorkoutDao.kt   + CompletedWorkoutEntity.kt + CompletedWorkoutExerciseEntity.kt + CompletedWorkoutSetEntity.kt
│   │   ├── NutritionDao.kt          + FoodEntity.kt + RecipeEntity.kt + RecipeIngredientEntity.kt + ConsumptionEntryEntity.kt
│   │   ├── GamificationDao.kt       + XpLedgerEntity.kt + AchievementStateEntity.kt + RankStateEntity.kt
│   │   ├── ProgressPictureDao.kt    + ProgressPictureEntity.kt
│   │   ├── ExercisePbDto.kt, ExerciseSetCountDto.kt, ExerciseSetRirDto.kt, WorkoutSummaryDto.kt  # projection DTOs
│   │
│   ├── geofence/DebugGeofenceProvider.kt        # DEBUG-only Koin override
│   ├── preferences/createDataStore.kt           # expect fun
│   │
│   └── repository/                              # Repository interfaces + Impls (CO-LOCATED — anti-pattern)
│       ├── ExerciseRepository.kt                # interface + ExerciseRepositoryImpl
│       ├── FoodRepository.kt + FoodRepositoryImpl.kt    # ⚠ only repo split into two files
│       ├── GamificationRepository.kt
│       ├── ProgressPictureRepository.kt
│       ├── RetroactiveWalker.kt                 # Goal-day backfill walker (data → domain logic)
│       ├── SettingsRepository.kt                # 348 lines — DataStore-backed user prefs + geofence state
│       ├── TemplateRepository.kt                # interface + TemplateRepositoryImpl
│       └── WorkoutRepository.kt                 # 320 lines — interface + WorkoutRepositoryImpl
│
├── di/                                          # Koin modules
│   ├── SharedModule.kt                          # Master module — includes all submodules + builds DI graph
│   ├── AiModule.kt
│   ├── GamificationModule.kt
│   ├── GamificationEngineModule.kt
│   ├── GamificationUiModule.kt
│   ├── AchievementGalleryModule.kt
│   ├── ProgressGalleryModule.kt
│   └── GamificationStartup.kt
│
├── domain/                                      # Domain layer
│   ├── model/                                   # Domain entities (16 files)
│   │   ├── CompletedWorkout.kt, ConsumptionEntry.kt, Exercise.kt, Food.kt, FoodUnit.kt,
│   │   ├── MuscleGroup.kt, MuscleRegion.kt, MuscleRegionPaths.kt (308 lines — SVG path constants),
│   │   ├── NutritionGoals.kt, Recipe.kt, RecipeMacros.kt, UserPhysicalStats.kt,
│   │   ├── WeightUnit.kt, WorkoutSession.kt, WorkoutSummary.kt, WorkoutTemplate.kt
│   │
│   ├── ai/                                      # AI use cases + platform contracts
│   │   ├── AiError.kt, AiGenerationManager.kt, AiPromptCatalog.kt, ApiKeyState.kt,
│   │   ├── NotificationService.kt (expect), SecureKeyStore.kt (expect),
│   │   ├── RecipeAiPreview.kt, RecipeAiUseCase.kt (381 lines),
│   │   ├── WorkoutAiPreview.kt, WorkoutAiUseCase.kt (378 lines)
│   │
│   ├── gamification/                            # Gamification engine + rules
│   │   ├── AchievementCatalog.kt (231 lines), AchievementProgress.kt, AchievementRules.kt,
│   │   ├── EventKeys.kt, GamificationEngine.kt (499 lines), GamificationEvent.kt,
│   │   ├── GoalDayTrigger.kt, NutritionGoalDayPolicy.kt,
│   │   ├── Rank.kt, RankLadder.kt, RankState.kt, StreakCalculator.kt, UnlockEvent.kt, XpFormula.kt
│   │
│   ├── geofence/  EarlyExitTracker.kt, GeofenceEvent.kt, GeofenceProvider.kt (interface),
│   │              PendingGeofenceExit.kt, PendingGeofenceExitStore.kt
│   ├── location/  GeoPoint.kt, LocationProvider.kt (interface)
│   ├── permissions/ LocationPermissionStatus.kt, PermissionController.kt (interface)
│   ├── progresspic/ BiometricGate.kt (expect), PhotoCaptureLauncher.kt (expect), PhotoVault.kt (expect),
│   │                ProgressGalleryTile.kt, ProgressPicture.kt, UnlockResult.kt
│   ├── nutrition/   13 *UseCase.kt files + TdeeCalculator.kt
│   └── workout/     GetUndertrainedMusclesUseCase.kt, UndertrainedMuscle.kt
│
└── presentation/                                # ViewModels (no Composables — UI is platform-specific)
    ├── ai/             AiSettingsViewModel.kt, RecipeAiViewModel.kt, WorkoutAiViewModel.kt
    ├── exercises/      CreateExerciseViewModel.kt, ExerciseCatalogViewModel.kt, ExerciseDetailViewModel.kt
    ├── gamification/   AchievementGalleryViewModel.kt, GamificationViewModel.kt, RanksAndAchievementsViewModel.kt
    ├── history/        WorkoutHistoryViewModel.kt
    ├── nutrition/      DailyLogViewModel.kt, FoodEntryViewModel.kt, RecipeCreationViewModel.kt, RecipeListViewModel.kt
    ├── overview/       OverviewViewModel.kt
    ├── progresspic/    ProgressGalleryViewModel.kt, ProgressPicturePromptViewModel.kt, ProgressViewerViewModel.kt
    ├── settings/       SettingsViewModel.kt
    ├── templates/      TemplateEditorViewModel.kt, TemplateListViewModel.kt
    └── workout/        WorkoutSessionViewModel.kt (⚠ 1,171 lines)
```

### Deep tree — `shared/src/androidMain/kotlin/com/pumpernickel/`

```
├── Platform.android.kt
├── data/api/HttpClientFactory.android.kt            # Ktor OkHttp engine
├── di/PlatformModule.android.kt                      # androidContext-aware bindings
├── domain/ai/NotificationService.android.kt
├── domain/ai/SecureKeyStore.android.kt               # EncryptedSharedPreferences impl
├── domain/progresspic/BiometricGate.android.kt
├── domain/progresspic/PhotoCaptureLauncher.android.kt
├── domain/progresspic/PhotoVault.android.kt
├── feature/                                          # ⚠ Stray "feature/" sibling — does not match the
│   ├── biometric/BiometricGateActivityHolder.kt      #    layer-first convention used elsewhere
│   ├── geofence/AndroidGeofenceProvider.kt + GeofenceBroadcastReceiver.kt
│   ├── location/AndroidLocationProvider.kt
│   ├── permissions/AndroidPermissionController.kt + PermissionActivityHolder.kt
│   └── photo/PhotoCaptureLauncherHost.kt
└── platform/                                         # ⚠ Second stray sibling for Room+DataStore
    ├── createDataStore.android.kt
    └── Database.android.kt
```

### Deep tree — `shared/src/iosMain/kotlin/com/pumpernickel/`

```
├── Platform.ios.kt
├── data/
│   ├── api/HttpClientFactory.ios.kt                 # Ktor Darwin engine
│   ├── db/Database.ios.kt
│   ├── geofence/IosGeofenceProvider.kt              # ⚠ data/geofence on iOS; androidMain has feature/geofence
│   ├── location/IosLocationProvider.kt
│   ├── permissions/IosPermissionController.kt
│   └── preferences/createDataStore.ios.kt
├── di/                                              # 11 KoinHelper.kt files (one per feature) + KoinInitIos
│   ├── KoinHelper.kt                                 # Main entry — exposes ViewModels/providers to Swift
│   ├── AchievementGalleryKoinHelper.kt, AiSettingsKoinHelper.kt, GamificationStartupIos.kt,
│   ├── GamificationUiKoinHelper.kt, PhotoVaultKoinHelper.kt, PlatformModule.ios.kt,
│   ├── ProgressGalleryKoinHelper.kt, ProgressPicturePromptKoinHelper.kt, ProgressViewerKoinHelper.kt,
│   ├── RanksAndAchievementsKoinHelper.kt, RecipeAiKoinHelper.kt, WorkoutAiKoinHelper.kt,
│   └── KoinInitIos.kt
└── domain/
    ├── ai/AiBgTaskRegistrar.kt, NotificationService.ios.kt, SecureKeyStore.ios.kt
    └── progresspic/BiometricGate.ios.kt, PhotoCaptureLauncher.ios.kt, PhotoVault.ios.kt
```

### Deep tree — `androidApp/src/androidMain/kotlin/com/pumpernickel/android/`

```
├── MainActivity.kt
├── PumpernickelApplication.kt                # startKoin entry
├── AiGenerationService.kt                    # Foreground service for AI generation
├── notifications/GeofenceNotifications.kt
└── ui/
    ├── navigation/
    │   ├── MainScreen.kt                     # Bottom-nav + NavHost
    │   └── Routes.kt                         # Type-safe @Serializable routes
    ├── theme/Theme.kt                        # Material 3 theme
    ├── components/                           # 10 reusable Composables
    │   ├── DebugGeofencePanel.kt, DrumPicker.kt, EarlyExitConfirmDialog.kt,
    │   ├── GeofenceStatusChip.kt, NutritionRings.kt, PermissionBanner.kt,
    │   ├── PermissionRationaleSheet.kt, ProgressPicturePromptCard.kt, SectionCard.kt
    └── screens/                              # 41 screen-level Composables (all in one flat folder)
        ├── AchievementGalleryScreen, AiMealGenScreen, AiPreviewSheet, AiSettingsScreen,
        ├── AiWorkoutGenScreen, AnatomyPickerSheet, CreateExerciseScreen, ExerciseCatalogScreen,
        ├── ExerciseDetailScreen, ExerciseOverviewSheet, ExercisePickerScreen, MacroRow,
        ├── NutritionDailyLogScreen, NutritionFoodEntryScreen, NutritionGoalsEditorScreen,
        ├── NutritionRecipeCreationScreen, NutritionRecipeListScreen, OverviewRankStrip,
        ├── OverviewScreen, PlaceholderScreen, ProgressGalleryScreen, ProgressViewerScreen,
        ├── RankLadderScreen, SettingsSheet, TemplateEditorScreen, TemplateListScreen,
        ├── TutorialOverlay, UnlockModal, WorkoutEmptyStateScreen, WorkoutEnforcementDetailSheet,
        ├── WorkoutHistoryDetailScreen, WorkoutHistoryListScreen, WorkoutPlaceholderScreen,
        └── WorkoutSessionScreen
```

### Deep tree — `iosApp/iosApp/`

```
├── PumpernickelApp.swift, AppDelegate.swift
├── Assets.xcassets/, Preview Content/
├── Extensions/Color+App.swift
├── Theme/ThemeManager.swift
├── Utilities/FlowObservation.swift, NotificationCenter+Geofence.swift
└── Views/                                   # 13 feature-first subfolders, 54 .swift total
    ├── MainTabView.swift                    # Bottom tab container
    ├── AI/         (AIMealGenView, AIPreviewSheet, AISettingsView, AIWorkoutGenView)
    ├── Anatomy/    (AnatomyFrontShape, AnatomyBackShape, AnatomyPickerView, MuscleRegionPaths)
    ├── Common/     (PlaceholderTabView, WorkoutEmptyStateView)
    ├── Exercises/  (CreateExerciseView, ExerciseCatalogView, ExerciseDetailView)
    ├── Gamification/ (AchievementGalleryView, RankLadderView, UnlockModalView)
    ├── History/    (WorkoutHistoryDetailView, WorkoutHistoryListView)
    ├── Nutrition/  (BarcodeScannerView, MacroRowView, NutritionDailyLogView, NutritionFoodEntryView,
    │               NutritionRecipeCreationView, NutritionRecipeListView)
    ├── Onboarding/ (TutorialOverlayView)
    ├── Overview/   (NutritionGoalsEditorView, OverviewRankStrip, OverviewView, ProgressGalleryView, ProgressViewerView)
    ├── Settings/   (DebugGeofencePanel, SettingsView, WorkoutEnforcementDetailView)
    ├── Templates/  (ExercisePickerView, TemplateEditorView, TemplateListView)
    └── Workout/    (EarlyExitConfirmDialog, ExerciseOverviewSheet, GeofenceStatusChip, PermissionBanner,
                     PermissionRationaleSheet, ProgressPicturePromptCard, RestTimerView, WorkoutAbortedView,
                     WorkoutFinishedView, WorkoutSessionView, WorkoutSetRow)
```

## Directory Purposes

**`shared/`:**
- Purpose: KMP module shared between Android + iOS
- Contains: All non-UI code (data, domain, presentation) for both platforms
- Key files: `build.gradle.kts`, `src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`

**`shared/src/commonMain/kotlin/com/pumpernickel/data/`:**
- Purpose: Persistence + network adapters
- Contains: Room (`db/`), repositories (`repository/`), HTTP clients & DTOs (`api/`), DataStore (`preferences/`), debug-only geofence
- Key files: `db/AppDatabase.kt`, `repository/TemplateRepository.kt`, `api/OpenAICompatibleClient.kt`

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/`:**
- Purpose: Business model + use cases + platform contracts (expect/actual)
- Contains: `model/`, plus feature subpackages (`workout/`, `nutrition/`, `ai/`, `gamification/`, `geofence/`, `location/`, `permissions/`, `progresspic/`)
- Key files: `model/WorkoutTemplate.kt`, `gamification/GamificationEngine.kt`, `nutrition/*UseCase.kt`

**`shared/src/commonMain/kotlin/com/pumpernickel/presentation/`:**
- Purpose: ViewModels exposing `StateFlow<UiState>`
- Contains: One subpackage per feature; each holds the ViewModel(s) for that feature
- Key files: `templates/TemplateListViewModel.kt`, `workout/WorkoutSessionViewModel.kt`

**`shared/src/commonMain/kotlin/com/pumpernickel/di/`:**
- Purpose: Koin module declarations
- Contains: `SharedModule.kt` + per-feature submodules + `expect val platformModule`
- Key files: `SharedModule.kt`

**`shared/src/androidMain/...`:**
- Purpose: Android-specific `actual` implementations (Ktor OkHttp, Room driver via KSP, biometric, location, geofence, EncryptedSharedPreferences)
- Inconsistency: Uses _three_ top-level sibling packages — `data/`, `domain/`, **`feature/`**, **`platform/`**. The `feature/` and `platform/` siblings break the layer-first pattern enforced in `commonMain`.

**`shared/src/iosMain/...`:**
- Purpose: iOS-specific `actual` implementations + Swift-facing Koin helpers
- Contains: Darwin Ktor engine, NSURLSession, CoreLocation, BGTaskScheduler registrar, Keychain, 11 `KoinHelper*.kt` files (manual Swift-friendly DI accessors)
- Key files: `di/KoinHelper.kt`, `di/KoinInitIos.kt`

**`androidApp/`:**
- Purpose: Android application module — Jetpack Compose UI consuming `:shared`
- Contains: `MainActivity`, `MainScreen` (NavHost + bottom nav), 41 screens, 10 components, Material 3 theme, AI foreground service
- Key files: `MainActivity.kt`, `ui/navigation/MainScreen.kt`

**`iosApp/`:**
- Purpose: iOS application — **native SwiftUI** project (Xcode workspace, _not_ Compose Multiplatform)
- Contains: SwiftUI views organised feature-first under `Views/`, theme, assets, extensions, Combine/AsyncSequence bridges to KMP Flows
- Key files: `PumpernickelApp.swift`, `AppDelegate.swift`, `Views/MainTabView.swift`

**`evals/`:**
- Purpose: AI evaluation harness (Node.js tooling) for `domain/ai/` features
- Contains: `cases/`, `prompts/`, `runs/`, `node_modules/`
- Committed: Yes
- Generated: `runs/` and `node_modules/` likely should be gitignored

**`.planning/`:**
- Purpose: GSD workflow artifacts (phases, quick fixes, debug notes, codebase maps, milestones)
- Committed: Yes — intentional, drives the AI workflow

## Key File Locations

**Entry Points:**
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt`: Android UI entry
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt`: Koin init for Android
- `iosApp/iosApp/PumpernickelApp.swift` + `iosApp/iosApp/AppDelegate.swift`: iOS entry
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinInitIos.kt`: Koin init for iOS

**Configuration:**
- `gradle/libs.versions.toml`: Single source of truth for all versions
- `shared/build.gradle.kts`: KMP targets, Room, KSP, Ktor, Koin deps
- `androidApp/build.gradle.kts`: Android UI module — Compose Compiler plugin
- `shared/schemas/`: Auto-generated Room schema JSON (commit these — required for AutoMigration)

**Core Logic:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt`: Room database root
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`: Master Koin DI graph
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`: Workout state machine (1,171 lines)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt`: XP + achievement engine

**Testing:**
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/`: 7 unit tests, domain-only
- No tests for: any ViewModel, any Repository, any UseCase outside gamification/TDEE
- No instrumented (Android) or XCTest (iOS) targets present

## Naming Conventions

**Files:**
- PascalCase, single top-level declaration per file in most cases (exception: `TemplateRepository.kt` declares interface + Impl together)
- Room entities: `*Entity` suffix (`WorkoutTemplateEntity`)
- Room DAOs: `*Dao` suffix (`WorkoutTemplateDao`)
- DTOs (projection types, network DTOs): `*Dto` suffix (`WorkoutSummaryDto`, `AiChatDto`)
- Repositories: `*Repository` interface + `*RepositoryImpl` class
- Use cases: `*UseCase` suffix (`AddFoodUseCase`)
- ViewModels: `*ViewModel` suffix
- Composables (Android UI): `*Screen.kt` for top-level, plain noun for components (`DrumPicker.kt`)
- SwiftUI views: `*View.swift` (`TemplateListView.swift`)
- expect/actual: `Xxx.kt` (common) + `Xxx.android.kt` / `Xxx.ios.kt` (platform)
- Koin modules: `*Module.kt`
- Koin Swift bridges: `*KoinHelper.kt`

**Directories:**
- All lowercase, single word where possible: `data`, `domain`, `presentation`, `di`, `model`, `workout`
- Feature subpackages: lowercase, no hyphens: `ai`, `gamification`, `progresspic` (not `progress-pic`)

**Packages:**
- `com.pumpernickel.<layer>.<feature>` for shared code
- `com.pumpernickel.android.<area>` for Android UI

## Where to Add New Code

**New domain entity:**
- Today: `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MyEntity.kt`
- Tests: `shared/src/commonTest/kotlin/com/pumpernickel/domain/MyEntityTest.kt`
- **After Clean Architecture refactor**: Keep here, but DO NOT add Entity→Domain mappers in this file — put mappers in a new `data/mappers/` package.

**New use case:**
- Today: `shared/src/commonMain/kotlin/com/pumpernickel/domain/<feature>/MyUseCase.kt`
- Wire in: `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` (or feature submodule)
- **After refactor**: Use case must depend only on `domain/repository/MyRepository` interface — never on `data.repository.MyRepositoryImpl`.

**New repository:**
- Today: `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/MyRepository.kt` (interface + Impl in same file)
- **After refactor**: `domain/repository/MyRepository.kt` (interface) + `data/repository/MyRepositoryImpl.kt` (impl)

**New Room entity + DAO:**
- Entity: `shared/src/commonMain/kotlin/com/pumpernickel/data/db/MyEntity.kt`
- DAO: `shared/src/commonMain/kotlin/com/pumpernickel/data/db/MyDao.kt`
- Register in `data/db/AppDatabase.kt`: add to `@Database(entities=[...])`, bump `version`, add `AutoMigration(from=N, to=N+1)`, declare `abstract fun myDao(): MyDao`
- Bind in `di/SharedModule.kt`: `single<MyDao> { get<AppDatabase>().myDao() }`

**New ViewModel:**
- Today: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/<feature>/MyViewModel.kt`
- Annotate StateFlow with `@NativeCoroutinesState` for Swift consumption
- Bind in `di/SharedModule.kt`: `viewModel { MyViewModel(get(), get()) }`
- Expose to iOS in `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` (or feature `*KoinHelper.kt`)

**New Android screen:**
- Composable: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/MyScreen.kt`
- Route: register in `androidApp/.../ui/navigation/Routes.kt` (type-safe `@Serializable`)
- Wire NavHost: `androidApp/.../ui/navigation/MainScreen.kt`
- Use `koinViewModel<MyViewModel>()`

**New iOS screen:**
- SwiftUI view: `iosApp/iosApp/Views/<Feature>/MyView.swift`
- Inject VM: `KoinHelper.shared.getMyViewModel()` (add accessor in `shared/src/iosMain/.../di/KoinHelper.kt`)
- Wire into `iosApp/iosApp/Views/MainTabView.swift` (tabs) or via `NavigationLink`
- Observe Flow via `iosApp/iosApp/Utilities/FlowObservation.swift` helpers

**New platform-specific behaviour:**
- Define `interface` or `expect fun`/`expect class` in `shared/src/commonMain/kotlin/com/pumpernickel/domain/<feature>/MyService.kt`
- Implement in `shared/src/androidMain/kotlin/com/pumpernickel/domain/<feature>/MyService.android.kt` (or `feature/<area>/...` if it grows beyond a single file — though this sibling-folder pattern is itself a structural inconsistency)
- Implement in `shared/src/iosMain/kotlin/com/pumpernickel/data/<feature>/IosMyService.kt` (note iOS prefers `data/` not `feature/`)
- Bind in `di/PlatformModule.android.kt` and `di/PlatformModule.ios.kt`

**Tests:**
- Today only domain-side unit tests in `shared/src/commonTest/kotlin/`. No `androidTest/`, no XCTest target.
- New tests must follow path `shared/src/commonTest/kotlin/com/pumpernickel/<layer>/<feature>/MyClassTest.kt`

## Special Directories

**`shared/schemas/`:**
- Purpose: Room-generated schema JSON snapshots, one per `version`
- Generated: Yes (by Room KSP)
- Committed: Yes (required so AutoMigration can validate from-to schemas)

**`.kotlin/`, `.gradle/`, `build/`, `androidApp/build/`, `shared/build/`:**
- Purpose: Build outputs + Gradle caches
- Generated: Yes
- Committed: No (should be gitignored — verify `.gradle/` is not tracked)

**`kls_database.db` (root):**
- Purpose: Stray SQLite file — likely produced by an IDE database tool or test run
- Generated: Yes
- Committed: **Yes (accidentally)** — should be added to `.gitignore` and removed from tracking

**`evals/node_modules/`:**
- Purpose: NPM tooling for the AI eval harness
- Generated: Yes
- Committed: **Yes (questionable)** — usually gitignored

**`assets/`:**
- Purpose: Source artwork (e.g. app-icon SVG) referenced by build scripts
- Generated: No
- Committed: Yes

## Where things SHOULD live vs where they currently DO live

This section is the seed for the upcoming Clean-Architecture refactor.

### 1. Repository interfaces

| | Currently | Should be |
|--|-----------|-----------|
| Interface | `shared/.../data/repository/TemplateRepository.kt` (top of file) | `shared/.../domain/repository/TemplateRepository.kt` |
| Implementation | `shared/.../data/repository/TemplateRepository.kt` (same file) | `shared/.../data/repository/TemplateRepositoryImpl.kt` |
| Applies to | `ExerciseRepository`, `FoodRepository` (already split), `GamificationRepository`, `ProgressPictureRepository`, `SettingsRepository`, `TemplateRepository`, `WorkoutRepository` |

### 2. Entity → Domain mappers

| | Currently | Should be |
|--|-----------|-----------|
| Mapper for `WorkoutTemplateEntity` | `shared/.../domain/model/WorkoutTemplate.kt:27` (extension `toDomain()`) — drags `data.db` into `domain/` | `shared/.../data/mappers/WorkoutTemplateMappers.kt` |
| Mapper for `ExerciseEntity` | `shared/.../domain/model/Exercise.kt:3` | `shared/.../data/mappers/ExerciseMappers.kt` |
| Applies to | All `toDomain()` extensions currently sitting in `domain/model/*.kt` files |

### 3. Use cases that import data layer

| File | Problem | Should be |
|------|---------|-----------|
| `domain/ai/WorkoutAiUseCase.kt` | Imports `data.api.OpenAICompatibleClient`, `data.repository.*` | Inject `AiChatGateway` interface (`domain/ai/AiChatGateway.kt`) implemented in `data/api/AiChatGatewayImpl.kt`; inject `*Repository` interfaces from `domain/repository/` |
| `domain/ai/RecipeAiUseCase.kt` | Same | Same |
| `domain/nutrition/*UseCase.kt` (10 files) | Import `data.repository.FoodRepository`, `data.api.OpenFoodFactsApi` | Inject `domain/repository/FoodRepository` + `domain/nutrition/BarcodeLookupGateway` |
| `domain/workout/GetUndertrainedMusclesUseCase.kt` | Imports `data.repository.{Exercise,Workout}Repository` | Inject domain repository interfaces |
| `domain/gamification/GamificationEngine.kt` | Imports `data.db.*Dao` directly | Inject `domain/repository/*Repository` — no DAOs in domain |
| `domain/gamification/NutritionGoalDayPolicy.kt` | Imports `data.db.ConsumptionEntryEntity` | Map to domain `ConsumptionEntry` _before_ entering the policy |
| `domain/geofence/EarlyExitTracker.kt` | Imports `data.repository.SettingsRepository` | Move `SettingsRepository` interface to `domain/repository/`; impl stays in `data/` |

### 4. `ViewModel` god object

| | Currently | Should be |
|--|-----------|-----------|
| `WorkoutSessionViewModel` | 1,171 lines, 11 constructor deps | Split into `StartSessionUseCase`, `LogSetUseCase`, `AbortSessionUseCase`, `FinalizeSessionUseCase` in `domain/workout/`; keep ViewModel as thin orchestrator |

### 5. `androidMain` package-layout inconsistency

| | Currently | Should be |
|--|-----------|-----------|
| Android geofence impl | `shared/.../androidMain/.../feature/geofence/AndroidGeofenceProvider.kt` | `shared/.../androidMain/.../data/geofence/AndroidGeofenceProvider.kt` (matches `iosMain` and `commonMain` taxonomy) |
| Android location impl | `shared/.../androidMain/.../feature/location/AndroidLocationProvider.kt` | `shared/.../androidMain/.../data/location/AndroidLocationProvider.kt` |
| Android permissions | `shared/.../androidMain/.../feature/permissions/` | `shared/.../androidMain/.../data/permissions/` |
| Android photo capture | `shared/.../androidMain/.../feature/photo/PhotoCaptureLauncherHost.kt` | `shared/.../androidMain/.../data/progresspic/` |
| Android biometric | `shared/.../androidMain/.../feature/biometric/` | `shared/.../androidMain/.../data/progresspic/` |
| Android DataStore + Room driver | `shared/.../androidMain/.../platform/` | `shared/.../androidMain/.../data/preferences/` and `shared/.../androidMain/.../data/db/` |

iOS already uses `data/...` for all of these; Android is the outlier.

### 6. Cross-cutting / stray files

| File | Issue | Fix |
|------|-------|-----|
| `domain/model/MuscleRegionPaths.kt` (308 lines of SVG path strings) | UI asset masquerading as a domain model | Move to `androidApp/.../ui/anatomy/` and `iosApp/.../Views/Anatomy/MuscleRegionPaths.swift` — already duplicated on iOS side |
| `data/repository/RetroactiveWalker.kt` | Lives in `data/repository/` but imports `domain/gamification/*` — it's domain orchestration, not a repository | Move to `domain/gamification/RetroactiveWalker.kt` |
| `data/geofence/DebugGeofenceProvider.kt` | Tagged as data but only used for Koin overrides in debug | Acceptable, but document the DEBUG-only contract in a header comment |
| `kls_database.db` (root) | Accidentally committed SQLite snapshot | gitignore + `git rm --cached` |

### 7. Folder/file mismatches summary

- **Domain that imports data** — see Dependency Rule Check in `ARCHITECTURE.md`. 18+ files affected.
- **Repositories not inverted** — interfaces co-located with implementations in `data/repository/` (every repository except `FoodRepository`, which is already split into two files).
- **Mappers in domain** — every `domain/model/*.kt` that has a `toDomain()` extension.
- **`androidMain` sibling drift** — `feature/` and `platform/` should fold into `data/`.
- **Flat `androidApp/.../ui/screens/`** — 41 screens in one folder; should be feature-grouped (`screens/workout/`, `screens/nutrition/`, etc.), mirroring iOS `Views/<Feature>/` and presentation/`feature/`.
- **`AndroidManifest` split between `src/androidMain/AndroidManifest.xml` and `src/main/res/values/strings.xml`** — `androidApp/build.gradle.kts` has both `sourceSets["main"]` overrides and `androidMain/` sources. Consolidate everything under `androidMain/`.

---

*Structure analysis: 2026-05-18*
