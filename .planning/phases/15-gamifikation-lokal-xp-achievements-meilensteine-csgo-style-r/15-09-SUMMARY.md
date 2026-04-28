---
phase: 15
plan: 09
type: execute
wave: 8
status: complete
---

# Plan 15-09 Summary — Achievement Gallery

## What shipped

- **`AchievementGalleryViewModel`** (shared) — exposes `@NativeCoroutinesState uiState: StateFlow<AchievementGalleryUiState>` built by joining the static `AchievementCatalog.all` with the live `GamificationRepository.achievements` flow. Tiles are pre-grouped by `Category` and sorted Bronze → Silver → Gold within each category. `AchievementTile` is the flat UI row model.
- **`AchievementGalleryKoinHelper`** (iOS) — `getAchievementGalleryViewModel()` factory mirroring the existing `KoinHelper` pattern.
- **`AchievementGalleryModule`** (shared) — populated with `viewModel { AchievementGalleryViewModel(get()) }`. Mounted via `SharedModule.includes(achievementGalleryModule)` wired in plan 03.
- **`AchievementGalleryScreen`** (Android) — `Scaffold` + `LazyVerticalGrid(GridCells.Fixed(2))`. Category headers span full row via `GridItemSpan(2)`. Locked tiles: `Modifier.alpha(0.45f)` + `Icons.Filled.Lock` + `"current / threshold"` footer. Unlocked: full alpha, `Icons.Filled.EmojiEvents`, tier-coloured border, `"Unlocked YYYY-MM-DD"` footer using `kotlinx-datetime`.
- **`Routes.kt`** — `@Serializable data object AchievementGalleryRoute` added under a new "Gamification" section.
- **`MainScreen.kt`** — `composable<AchievementGalleryRoute>` registered in the workout-tab NavHost (inserted after the `WorkoutHistoryDetailRoute` composable, keeping the existing order).
- **`SettingsSheet.kt`** — new `onNavigateToAchievements: () -> Unit` parameter. "Gamification" section with `Icons.Filled.EmojiEvents` leading icon + `KeyboardArrowRight` trailing icon inserted after the Weight Unit block. Calls `onDismiss()` before `onNavigateToAchievements()` so the sheet closes before navigation (avoids modal-over-destination flicker).
- **`TemplateListScreen.kt`** — threads `{ navController.navigate(AchievementGalleryRoute) }` into the sheet. `navController` is the outer workout-tab `NavHostController`, so the navigate reaches the new composable registration in `MainScreen`.

## Category rendering order

Per D-14, rendered in fixed order:
1. **Volume**
2. **Consistency**
3. **PR Hunter**
4. **Exercise Variety** (enum is `Category.VARIETY` in `AchievementCatalog.kt`; the plan referenced it as `EXERCISE_VARIETY` — actual enum name used to avoid a rename).

## Tile count

`AchievementCatalog.all.size` = **36** (12 families × 3 tiers), within the D-15 10–15 families × 3 tiers range.

## Build verification

- `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — **BUILD SUCCESSFUL**
- `./gradlew :androidApp:compileDebugKotlin` — **BUILD SUCCESSFUL**

## Deviations from plan

1. **`Category.VARIETY`** — the plan referenced `Category.EXERCISE_VARIETY`, but the actual enum (plan 02 output) uses `Category.VARIETY`. UI label "Exercise Variety" preserved in `CategoryHeader`.
2. **`AchievementProgress` shape** — the plan described a `{ achievementId, tier, currentProgress, unlockedAtMillis }` flat struct, but the actual model (plan 02) nests the catalog def: `{ def: AchievementDef, currentProgress, unlockedAtMillis }`. ViewModel key lookup adjusted to `progressRows.associateBy { it.def.id }`.
3. **Smart-cast fix** — Kotlin cannot smart-cast `tile.unlockedAtMillis` inside the `else` branch because it crosses a module boundary. Assigned to a local `val unlockedAt = tile.unlockedAtMillis` before the null check; compiles without `!!`.
4. **Task 3 insertion point** — the plan said "replace trailing `Spacer(32.dp)` at line 155" but the actual line in the current SettingsSheet was line 155. Section inserted directly before the trailing `Spacer(32.dp)` (unchanged) rather than rewriting it.

## iOS note

Per `<ios_integration_contract>` in the plan, the Swift `AchievementGalleryView.swift` and the `SettingsView.swift` `NavigationLink` entry are user-implemented. This plan shipped only the shared VM contract + the iOS `AchievementGalleryKoinHelper` factory. The iOS contract:

```swift
// AchievementGalleryView.swift (user-written)
let vm = AchievementGalleryKoinHelper().getAchievementGalleryViewModel()
for try await state in asyncSequence(for: vm.uiState) { ... }

// SettingsView.swift (user-edited, new section)
Section("Gamification") {
    NavigationLink("Achievements") { AchievementGalleryView() }
}
```

## Phase 15 closure

15-09 was the final plan. All 9 plans now complete, Room v8 schema live, retroactive walker wired, workout-save hook active, Android rank strip + unlock modal rendered, achievement gallery reachable from Settings.
