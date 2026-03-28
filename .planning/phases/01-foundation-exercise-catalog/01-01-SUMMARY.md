---
phase: 01-foundation-exercise-catalog
plan: 01
subsystem: database
tags: [kotlin-multiplatform, room, ksp, kotlinx-serialization, gradle, kmp-nativecoroutines]

# Dependency graph
requires: []
provides:
  - "KMP project scaffolding (Gradle, shared module, androidApp module)"
  - "Room database with exercises table and ExerciseDao"
  - "ExerciseEntity, Exercise domain model, ExerciseRepository"
  - "MuscleGroup enum (16 groups) with DB-name mapping"
  - "MuscleRegion enum (33 regions) matching gymtracker anatomy SVG"
  - "DatabaseSeeder for 873-exercise JSON import"
  - "expect/actual readResourceFile and getDatabaseBuilder for Android/iOS"
affects: [01-02, 01-03, 02-workout-templates, 03-workout-session]

# Tech tracking
tech-stack:
  added: [Kotlin 2.3.20, Gradle 8.12, Room KMP 2.8.4, KSP 2.3.6, Koin 4.2.0, kotlinx-serialization 1.10.0, kotlinx-coroutines 1.10.2, kotlinx-datetime 0.7.1, Lifecycle ViewModel 2.10.0, KMP-NativeCoroutines 1.0.2, AGP 8.9.3, SQLite Bundled 2.6.2]
  patterns: [expect/actual for platform code, Room @ConstructedBy for KMP, deferred seeding via Mutex, entity-to-domain mapping with toDomain(), comma-separated muscle storage]

key-files:
  created:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - shared/build.gradle.kts
    - androidApp/build.gradle.kts
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseDao.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/DatabaseSeeder.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegion.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/Platform.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/Platform.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/data/db/Database.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/Platform.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/data/db/Database.ios.kt
    - shared/src/commonMain/resources/free_exercise_db.json
  modified: []

key-decisions:
  - "KSP version 2.3.6 (new simplified versioning) instead of plan's 2.3.20-1.0.31 which does not exist"
  - "Resource file placed in commonMain/resources (not composeResources) since shared module has no Compose Multiplatform UI dependency"
  - "ExperimentalForeignApi opt-in added for iOS platform files using NSBundle/NSString/NSHomeDirectory"

patterns-established:
  - "KMP module structure: shared (commonMain/androidMain/iosMain) + androidApp"
  - "Room KMP with @ConstructedBy and @Suppress(NO_ACTUAL_FOR_EXPECT) pattern"
  - "Deferred seeding: Mutex-guarded check-then-seed on first repository access"
  - "Entity-to-domain mapping via extension function toDomain()"
  - "Muscle data stored as comma-separated strings in Room, parsed to enums in domain layer"

requirements-completed: [EXER-01]

# Metrics
duration: 10min
completed: 2026-03-28
---

# Phase 1 Plan 01: KMP Project Foundation Summary

**KMP project with Room database, 873-exercise seeded catalog, 16-group muscle mapping, and deferred repository seeding pattern compiling for both iOS and Android targets**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-28T18:12:06Z
- **Completed:** 2026-03-28T18:22:21Z
- **Tasks:** 2
- **Files modified:** 26

## Accomplishments
- KMP project scaffolding with Gradle version catalog, shared module targeting Android + 3 iOS architectures, and androidApp module
- Room database with ExerciseEntity (all D-03 fields), ExerciseDao (search, filter, insert, count), and AppDatabase with KMP @ConstructedBy pattern
- MuscleGroup enum with 16 canonical groups mapping 17 DB names (middle back -> LATS, abductors -> GLUTES) and MuscleRegion enum with 33 SVG regions matching gymtracker's muscleRegionMap.ts
- ExerciseRepository with deferred seeding via Mutex, DatabaseSeeder parsing 873-exercise JSON with kotlinx-serialization
- Platform-specific implementations for resource reading and database creation (Android/iOS)

## Task Commits

Each task was committed atomically:

1. **Task 1: KMP Gradle build system and project structure** - `b24a1db` (feat)
2. **Task 2: Room database, domain models, repository with seeding** - `c889123` (feat)

## Files Created/Modified
- `gradle/libs.versions.toml` - Version catalog with all KMP dependencies
- `settings.gradle.kts` - Module declarations with TYPESAFE_PROJECT_ACCESSORS
- `build.gradle.kts` - Root build with plugin declarations
- `gradle.properties` - Kotlin code style, AndroidX, JVM args
- `shared/build.gradle.kts` - KMP module with Room, KSP, serialization plugins
- `androidApp/build.gradle.kts` - Android application module
- `androidApp/src/main/AndroidManifest.xml` - Minimal manifest
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt` - Room @Entity for exercises table
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseDao.kt` - Room @Dao with search/filter/insert queries
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` - Room @Database with @ConstructedBy
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/DatabaseSeeder.kt` - JSON parser and seeder for 873 exercises
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` - Repository interface + impl with deferred seeding
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` - Domain model with toDomain() mapper
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt` - 16-group enum with DB-name mapping
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegion.kt` - 33-region enum with AnatomyView
- `shared/src/commonMain/kotlin/com/pumpernickel/Platform.kt` - expect readResourceFile declaration
- `shared/src/androidMain/kotlin/com/pumpernickel/Platform.android.kt` - Android resource reading via classloader
- `shared/src/androidMain/kotlin/com/pumpernickel/data/db/Database.android.kt` - Android getDatabaseBuilder with Context
- `shared/src/iosMain/kotlin/com/pumpernickel/Platform.ios.kt` - iOS resource reading via NSBundle
- `shared/src/iosMain/kotlin/com/pumpernickel/data/db/Database.ios.kt` - iOS getDatabaseBuilder with NSHomeDirectory
- `shared/src/commonMain/resources/free_exercise_db.json` - 873-exercise seed data from gymtracker
- `gradlew` / `gradlew.bat` / `gradle/wrapper/` - Gradle wrapper (8.12)
- `.gitignore` - Gradle, IDE, build output exclusions

## Decisions Made
- **KSP version 2.3.6:** Plan specified `2.3.20-1.0.31` which does not exist on Maven Central. KSP 2.x adopted a new simplified versioning scheme (no longer `kotlin-version-ksp-version`). Used latest stable `2.3.6` which is compatible with Kotlin 2.3.20.
- **Resource placement in commonMain/resources:** Plan suggested composeResources/files/ but since the shared module intentionally excludes Compose Multiplatform (D-01: platform-native UI), resources go in the standard `commonMain/resources/` directory with expect/actual reading.
- **ExperimentalForeignApi opt-in:** iOS platform files require `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` for NSBundle, NSString, NSHomeDirectory APIs. Added as file-level annotations.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KSP version 2.3.20-1.0.31 does not exist**
- **Found during:** Task 1 (Gradle configuration)
- **Issue:** Plan specified KSP version `2.3.20-1.0.31` but KSP 2.x changed to simplified versioning. This version was never published.
- **Fix:** Used KSP `2.3.6` (latest stable, compatible with Kotlin 2.3.20)
- **Files modified:** gradle/libs.versions.toml
- **Verification:** `./gradlew tasks --dry-run` succeeds
- **Committed in:** b24a1db (Task 1 commit)

**2. [Rule 3 - Blocking] settings.gradle.kts used wrong API name**
- **Found during:** Task 1 (Gradle configuration)
- **Issue:** `dependencyResolution` is not a valid Gradle Settings API. The correct name is `dependencyResolutionManagement`.
- **Fix:** Changed `dependencyResolution` to `dependencyResolutionManagement`
- **Files modified:** settings.gradle.kts
- **Verification:** `./gradlew tasks --dry-run` succeeds
- **Committed in:** b24a1db (Task 1 commit)

**3. [Rule 3 - Blocking] iOS files need ExperimentalForeignApi opt-in**
- **Found during:** Task 2 (iOS compilation)
- **Issue:** `NSBundle.mainBundle.pathForResource()`, `NSString.stringWithContentsOfFile()`, and `NSHomeDirectory()` require ExperimentalForeignApi opt-in in Kotlin/Native
- **Fix:** Added `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` to both iOS platform files
- **Files modified:** shared/src/iosMain/kotlin/com/pumpernickel/Platform.ios.kt, shared/src/iosMain/kotlin/com/pumpernickel/data/db/Database.ios.kt
- **Verification:** `./gradlew :shared:compileKotlinIosSimulatorArm64` succeeds
- **Committed in:** c889123 (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes necessary to resolve build failures. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all data layer code is fully wired. The 873-exercise JSON is bundled, seeder parses it, repository triggers seeding on first access.

## Next Phase Readiness
- Data layer complete: ExerciseEntity, ExerciseDao, ExerciseRepository ready for Plan 02 (Koin DI, ViewModels)
- MuscleGroup/MuscleRegion enums ready for Plan 03 (anatomy SVG picker)
- Database builders ready for platform-specific Koin module wiring
- No blockers for Plan 02 or Plan 03

## Self-Check: PASSED

All 20 key files verified present. Both task commits (b24a1db, c889123) verified in git log.

---
*Phase: 01-foundation-exercise-catalog*
*Completed: 2026-03-28*
