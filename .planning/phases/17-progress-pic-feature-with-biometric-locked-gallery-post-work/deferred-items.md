# Phase 17 Deferred Items

Pre-existing issues found during execution that are out of scope for the current plan.

## 17-05 deferred

### TdeeCalculatorTest unresolved Android-target test compile

- **Found during:** Plan 17-05 Task 1 verification (`./gradlew :shared:allTests`)
- **File:** `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt`
- **Error:** `Unresolved reference 'Test'`, `'assertTrue'`, `'assertEquals'` on the Android target only — `compileDebugUnitTestKotlinAndroid` fails with these references missing despite the imports being syntactically correct (`import kotlin.test.Test/assertTrue/assertEquals` are present at the top of the file).
- **Reproduces on main:** Yes — running `./gradlew :shared:compileDebugUnitTestKotlinAndroid` against the main repo (HEAD = `e988ce4`) produces the exact same failure. iOS test target (`compileTestKotlinIosSimulatorArm64`) compiles cleanly.
- **Why not fixed in 17-05:** Plan 17-05 modifies `WorkoutSessionViewModel.Finished` (no impact on nutrition tests). The failure is a pre-existing Android-target build configuration issue — kotlin-test-junit dependency wiring on `androidUnitTest` source set. Plan 16 likely shipped this without ever running the Android test target (only iOS / common). Out of scope per the executor scope-boundary rule.
- **Suggested fix (for a future scope-appropriate plan):** Add `androidx.test:runner` or `junit:junit:4.13.2` + `kotlin-test-junit` to the `androidUnitTest` source set in `shared/build.gradle.kts`, OR exclude the file from the Android target via source-set configuration.
