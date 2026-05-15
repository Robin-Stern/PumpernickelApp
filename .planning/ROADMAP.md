# Roadmap: PumpernickelApp

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-29)
- ✅ **v1.1 Workout Polish & Firmware Parity** — Phases 5-10 (shipped 2026-03-31)
- ✅ **v1.5 Android Material 3 UI** — Phases 11-14 (shipped 2026-03-31)
- ⚠️ **Post-v1.5 (Untracked)** — Nutrition + theming (merged 2026-04-14 on `feature/workouts` outside GSD)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-29</summary>

- [x] Phase 1: Foundation & Exercise Catalog (3/3 plans) — completed 2026-03-28
- [x] Phase 2: Template Management (3/3 plans) — completed 2026-03-28
- [x] Phase 3: Workout Session (3/3 plans) — completed 2026-03-28
- [x] Phase 4: History & Settings (3/3 plans) — completed 2026-03-29

</details>

<details>
<summary>✅ v1.1 Workout Polish & Firmware Parity (Phases 5-10) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Scroll Wheel Pickers & Auto-Increment (2/2 plans) — completed 2026-03-29
- [x] Phase 6: Personal Best Display (1/1 plan) — completed 2026-03-29
- [x] Phase 7: Post-Workout Recap & Edit (1/1 plan) — completed 2026-03-29
- [x] Phase 8: Mid-Workout Exercise Reorder (2/2 plans) — completed 2026-03-29
- [x] Phase 9: Abandon Guards & Context Menu (1/1 plan) — completed 2026-03-30
- [x] Phase 10: Minimal Set Screen & UI Polish (2/2 plans) — completed 2026-03-30

</details>

<details>
<summary>✅ v1.5 Android Material 3 UI (Phases 11-14) — SHIPPED 2026-03-31</summary>

### Phase 11: Android Shell & Navigation
**Requirements:** ANDROID-01, ANDROID-02
**Goal:** Bootstrap the Android app with Material 3 theme, bottom navigation, navigation graph with type-safe routes, and Koin DI wiring — making the app runnable with placeholder screens.
**Plans:** 1/1 plans complete
- [x] 11-01-PLAN.md — Build config, Koin init, Material 3 theme, NavigationBar with 3 tabs, type-safe routes, placeholder screens

### Phase 12: Exercise Catalog & Templates
**Requirements:** ANDROID-03, ANDROID-04
**Depends on:** Phase 11
**Goal:** Port exercise catalog (search, detail, create) and template management (list, editor, exercise picker) screens to Jetpack Compose with Material 3 components.
**Plans:** 2/2 plans complete
- [x] 12-01-PLAN.md — ExerciseCatalogScreen, ExerciseDetailScreen, CreateExerciseScreen + route fix
- [x] 12-02-PLAN.md — TemplateListScreen, TemplateEditorScreen, ExercisePickerScreen, WorkoutEmptyStateScreen

### Phase 13: Workout Session Core
**Requirements:** ANDROID-05, ANDROID-06, ANDROID-07
**Depends on:** Phase 12
**Goal:** Port the complete workout execution flow — active session with custom drum picker set entry, rest timer, exercise overview bottom sheet, abandon guards, post-workout recap with edit, and finished state.
**Plans:** 4/4 plans complete
- [x] 13-01-PLAN.md — Custom drum/wheel picker composable with snap fling behavior (Wave 1)
- [x] 13-02-PLAN.md — WorkoutSessionScreen Active state: set entry, rest timer, toolbar menu, abandon dialog, nav wiring (Wave 2)
- [x] 13-03-PLAN.md — ExerciseOverviewSheet with Completed/Current/Up Next sections, move reorder, skip (Wave 3)
- [x] 13-04-PLAN.md — Reviewing state (recap with tap-to-edit) and Finished state (summary + Done) (Wave 4)

### Phase 14: History, Settings & Anatomy
**Requirements:** ANDROID-08, ANDROID-09
**Depends on:** Phase 11
**Goal:** Port workout history, settings, and anatomy picker with Canvas-drawn body maps to Jetpack Compose.
**Plans:** 2/2 plans complete
- [x] 14-01-PLAN.md — WorkoutHistoryListScreen, WorkoutHistoryDetailScreen, SettingsSheet with kg/lbs toggle
- [x] 14-02-PLAN.md — AnatomyPickerSheet with Compose Canvas front/back body drawings, shared MuscleRegionPaths, touch region detection

**v1.5 Dependency Graph**

```
Phase 11 ──► Phase 12 ──► Phase 13
Phase 11 ──► Phase 14 (independent of 12/13)
```

</details>

### ⚠️ Post-v1.5 (Untracked) — Merged 2026-04-14

Work landed on `feature/workouts` at `fe297ad` **without GSD planning artifacts**. No PLAN.md / RESEARCH.md files exist. Recorded for traceability only — see `MILESTONES.md` → "Post-v1.5 (Untracked)" for the full scope, and `git log 4d02ce0..fe297ad` for commit-level history.

**Delivered:**
- Nutrition feature (F2): Food/Recipe/Consumption CRUD, 11 use cases, OpenFoodFacts barcode lookup, iOS + Android UI (6 iOS views, Android navigation)
- Dynamic theming: light/dark/system + 8 accent color presets, persisted in DataStore
- Nutrition goals on Overview tab (calorie/protein/fat/carb/sugar)
- Template editor redesign; workout history detail set-count + RIR; PB calculation fix
- Room schema v4 → v7 (AutoMigration 6→7)
- Android: `android-kmp-library` plugin migration, `androidApp` module extracted
- Ktor CIO + kotlinx-datetime added to the stack

**Gap:** no per-phase artifacts, no verification reports, no decision log entries in `.planning/phases/`. Before the next milestone opens, consider a retroactive `/gsd:map-codebase` to re-anchor `.planning/` intel files to the current tree.

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Exercise Catalog | v1.0 | 3/3 | Complete | 2026-03-28 |
| 2. Template Management | v1.0 | 3/3 | Complete | 2026-03-28 |
| 3. Workout Session | v1.0 | 3/3 | Complete | 2026-03-28 |
| 4. History & Settings | v1.0 | 3/3 | Complete | 2026-03-29 |
| 5. Scroll Wheel Pickers & Auto-Increment | v1.1 | 2/2 | Complete | 2026-03-29 |
| 6. Personal Best Display | v1.1 | 1/1 | Complete | 2026-03-29 |
| 7. Post-Workout Recap & Edit | v1.1 | 1/1 | Complete | 2026-03-29 |
| 8. Mid-Workout Exercise Reorder | v1.1 | 2/2 | Complete | 2026-03-29 |
| 9. Abandon Guards & Context Menu | v1.1 | 1/1 | Complete | 2026-03-30 |
| 10. Minimal Set Screen & UI Polish | v1.1 | 2/2 | Complete | 2026-03-30 |
| 11. Android Shell & Navigation | v1.5 | 1/1 | Complete    | 2026-03-31 |
| 12. Exercise Catalog & Templates | v1.5 | 2/2 | Complete    | 2026-03-31 |
| 13. Workout Session Core | v1.5 | 4/4 | Complete    | 2026-03-31 |
| 14. History, Settings & Anatomy | v1.5 | 2/2 | Complete    | 2026-03-31 |
| Post-v1.5 (Untracked) | — | n/a | Merged outside GSD | 2026-04-14 |

### Phase 15: Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks

**Requirements:** GAME-01 (F4 from Lastenheft)
**Depends on:** Workout tracking (Phases 1–10/11–14) + Nutrition (Post-v1.5)
**Goal:** Local-only gamification layer that awards XP for completed workouts, new personal records (PRs), daily nutrition goal-days, and streak thresholds; tracks achievements across 4 categories × 3 tiers (Bronze/Silver/Gold); assigns a CSGO-style 10-rank ladder (Silver → Global Elite) on an exponential ×1.5 threshold curve with permanent ranks (no decay); surfaces rank/XP on the Overview tab (D-18) and achievements under Settings (D-21); fires celebratory modal + haptic on unlocks (D-19). Retroactive walker on first-launch replays existing history idempotently (D-12/D-13). Room schema v7 → v8 via non-destructive AutoMigration.

**Scope notes:**
- XP sources: workout completed (volume-scaled, D-02), new PR (+50, D-03), nutrition goal-day (±10% strict macros, D-04), streak bonuses (D-06), achievement unlocks (D-17).
- Persistence: 3 new Room entities (xp_ledger with unique (source, eventKey) dedupe index, achievement_state singleton-per-tier, rank_state singleton), new GamificationDao, AutoMigration(7, 8).
- Surfaces: Android fully implemented; iOS ships VM contracts + KoinHelper factories, user hand-writes SwiftUI per MEMORY.md.
- Catalog: static code-defined 10–15 achievements × 3 tiers in `AchievementCatalog.kt`.
- Out of scope (deferred per CONTEXT.md): compounding streaks, rank decay, leaderboards, custom achievements, sound effects, progress charts.

**Plans:** 11/11 plans complete

**Wave structure** (serialized where plans touch the same file — revision-iter-1 BLOCKER-1 fix):
- Wave 1 (foundation, parallel): 01 (Room schema + entities + DAO), 02 (pure domain — Rank/XpFormula/AchievementCatalog/UnlockEvent/NutritionGoalDayPolicy)
- Wave 2: 03 (repository + seeder + DataStore sentinel + 4 Koin sub-modules via `includes(...)` in SharedModule.kt)
- Wave 3: 04 (GamificationEngine + StreakCalculator + AchievementRules + nutrition-streak + PR-hunter snapshot fill — registers in GamificationEngineModule.kt)
- Wave 4: 05 (retroactive walker + GamificationStartup — registers in GamificationEngineModule.kt)
- Wave 5: 06 (workout-save hook D-20 — edits SharedModule.kt for WorkoutSessionVM binding)
- Wave 6: 07 (OverviewVM rankState + GoalDayTrigger D-22 — edits SharedModule.kt for OverviewVM binding + GamificationEngineModule.kt for GoalDayTrigger)
- Wave 7: 08 (OverviewRankStrip + UnlockModalHost — GamificationUiModule.kt + GamificationUiKoinHelper.kt; wraps Android MainScreen.kt in Box + UnlockModalHost())
- Wave 8: 09 (AchievementGalleryScreen + Settings entry — AchievementGalleryModule.kt + AchievementGalleryKoinHelper.kt; adds composable<AchievementGalleryRoute> to Android MainScreen.kt, serialized after 08 to avoid same-file overlap)
- Wave 9 (GAP CLOSURE, depends on 09): 10 (create iOS Swift files — OverviewRankStrip.swift + UnlockModalView.swift + AchievementGalleryView.swift + pbxproj Xcode target registration)
- Wave 10 (GAP CLOSURE, depends on 10): 11 (wire iOS Swift files into existing OverviewView.swift + SettingsView.swift + MainTabView.swift — observe flows, present modal, add NavigationLink)

Plans:
- [x] 15-01-PLAN.md — Room schema v7 → v8: XpLedgerEntity + AchievementStateEntity + RankStateEntity + GamificationDao + AutoMigration(7, 8)
- [x] 15-02-PLAN.md — Pure domain: Rank + RankLadder + RankState + XpFormula + GamificationEvent + UnlockEvent + AchievementCatalog + EventKeys
- [x] 15-03-PLAN.md — GamificationRepository + AchievementStateSeeder + SettingsRepository sentinel flag + Koin wiring
- [x] 15-04-PLAN.md — GamificationEngine + StreakCalculator + AchievementRules (with unit tests)
- [x] 15-05-PLAN.md — RetroactiveWalker (D-12/D-13) + GamificationStartup first-launch orchestrator
- [x] 15-06-PLAN.md — Workout-save integration: WorkoutSessionViewModel.saveReviewedWorkout() + WorkoutRepository.saveCompletedWorkout returning Long (D-20)
- [x] 15-07-PLAN.md — OverviewViewModel rankState StateFlow + GoalDayTrigger (D-22)
- [x] 15-08-PLAN.md — GamificationViewModel + Android OverviewRankStrip (D-18) + UnlockModalHost (D-19/D-20) + iOS contract
- [x] 15-09-PLAN.md — AchievementGalleryViewModel + Android AchievementGalleryScreen + Settings entry + Route + iOS contract (D-21)
- [x] 15-10-PLAN.md — GAP CLOSURE: create iOS OverviewRankStrip.swift + UnlockModalView.swift + AchievementGalleryView.swift + register in iosApp.xcodeproj (Wave 9)
- [x] 15-11-PLAN.md — GAP CLOSURE: wire the three new Swift views into OverviewView.swift + SettingsView.swift + MainTabView.swift — observe flows, present .fullScreenCover modal queue, add Settings NavigationLink (Wave 10)

### Phase 15.1: Ranks & Achievements Browser (INSERTED)

**Requirements:** GAME-01 follow-up (transparency gap surfaced in Phase 15 UAT)
**Depends on:** Phase 15
**Goal:** Add a browsable "Ranks & Achievements" UI reachable from the Overview XP banner so the gamification system becomes legible. Users can see (1) the full rank ladder — every tier in order, which one they're on, which they've passed, XP thresholds, and what's next; and (2) the full achievement catalog — every achievement (locked + unlocked), with progress bars on partially-completed ones, unlock state, and metadata (description, reward XP). Pure presentation over the existing `GamificationRepository` / `RankLadder` / `AchievementCatalog` / `AchievementStateSeeder` — no new domain logic, no schema changes.

**Scope notes:**
- iOS surface: tappable affordance on `OverviewRankStrip` (or a "View all" button adjacent) opens the browser; SwiftUI views written by user per MEMORY.md convention.
- Shared VM contract: new `RanksAndAchievementsViewModel` in commonMain exposing `rankLadderState: StateFlow<RankLadderUiState>` (achievement half reuses existing `AchievementGalleryViewModel` from Phase 15-09 per D-151-03 / D-151-05), registered via Koin + KoinHelper per the Phase 15 pattern.
- Android: reuses existing `AchievementGalleryScreen` (Phase 15-09) as the achievement half; a new `RankLadderScreen` renders the ladder.
- Entry points: Overview rank-strip tap → Ranks ladder; existing Settings → Achievements entry unchanged per D-151-02 / D-21.

**Out of scope:** new XP sources, new rank tiers, leaderboards, social features, custom achievements, progress charts beyond simple progress bars.

**Plans:** 4/4 plans complete

**Wave structure:**
- Wave 1 (foundation): 01 (shared `RanksAndAchievementsViewModel` + `RankLadderUiState` + `RankRow` + `RankRowStatus` in commonMain; register in `GamificationUiModule`; create `RanksAndAchievementsKoinHelper` in iosMain)
- Wave 2 (parallel — no file conflicts with each other): 02 (new Android `RankLadderScreen.kt`); 04 (iOS handoff doc `15.1-IOS-HANDOFF.md`)
- Wave 3: 03 (Android navigation wiring — adds `OverviewRootRoute` + `RanksAndAchievementsRoute` to `Routes.kt`; introduces Overview-tab NavHost in `MainScreen.kt`; modifies `OverviewScreen.kt` to accept `navController`; modifies `OverviewRankStrip.kt` to accept `onTap` callback; adds D-151-19 reward-XP label to `AchievementGalleryScreen.kt`)

Plans:
- [x] 15.1-01-PLAN.md — Shared `RanksAndAchievementsViewModel` + `RankLadderUiState`/`RankRow`/`RankRowStatus` + Koin + iOS `RanksAndAchievementsKoinHelper`
- [x] 15.1-02-PLAN.md — Android `RankLadderScreen.kt` rendering all 10 tiers with PASSED/CURRENT/LOCKED presentation + D-11 unranked header + SILVER "First workout unlocks" special case
- [x] 15.1-03-PLAN.md — Android navigation wiring: Routes.kt + Overview-tab NavHost in MainScreen.kt + OverviewScreen/OverviewRankStrip tappable card + reward-XP label on AchievementGalleryScreen tiles (D-151-19)
- [x] 15.1-04-PLAN.md — iOS handoff doc `15.1-IOS-HANDOFF.md` (per D-151-16/D-151-17 — user hand-writes SwiftUI, this plan specifies the contract)

**Context:** Surfaced during Phase 15 UAT (2026-04-23) — users reported current rank/XP and unlock toasts are visible but there is no way to browse what ranks exist or what achievements exist, so the system is not legible. See `.planning/debug/fresh-install-rank-silver1.md` for a related UAT bug from the same session.

### Phase 16: Set nutrition goals (kcal/protein/carbs/fat per day) — surface progress on Overview tab and award bonus XP when daily goal achieved within tolerance

**Goal:** Ship a user-facing nutrition-goal editor (Mifflin–St Jeor TDEE calculator with Cut/Maintain/Bulk suggestions + drum-picker macro tweaking) reachable from the Overview tab via an edit pencil and a dismissable banner; persist `UserPhysicalStats` (weight/height/age/sex/activity) and `nutrition_goals_banner_dismissed` in DataStore alongside the existing `NutritionGoals` keys. Phase 15 engine (`NutritionGoalDayPolicy`, `GoalDayTrigger`, `GamificationEngine`, `XpFormula`) is unchanged — `±10%` tolerance and goal-day XP rewards stay as-is per D-16-15 / D-16-17.
**Requirements**: TBD (decisions D-16-01 … D-16-17 in 16-CONTEXT.md serve as the requirement source)
**Depends on:** Phase 15
**Plans:** 9/9 plans complete

**Wave structure:**
- Wave 1 (foundation, no deps): 01 (UserPhysicalStats domain model + Sex / ActivityLevel enums)
- Wave 2 (parallel — both depend on 01, no file overlap): 02 (TdeeCalculator pure functions + tests); 03 (SettingsRepository extensions: userPhysicalStats Flow + bannerDismissed Flow)
- Wave 3: 04 (OverviewViewModel extensions: 2 new StateFlows + updateUserPhysicalStats + dismissBanner; chains banner-dismiss into updateNutritionGoals)
- Wave 4 (parallel — different platform files): 05 (Android: Routes + MainScreen NavHost + NutritionGoalsEditorScreen + OverviewScreen banner/edit pencil); 06 (iOS: NutritionGoalsEditorView + OverviewView banner/edit pencil + pbxproj target registration)

Plans:
- [x] 16-01-PLAN.md — UserPhysicalStats data class + Sex / ActivityLevel enums (commonMain)
- [x] 16-02-PLAN.md — TdeeCalculator pure functions (BMR, TDEE, suggestions) + 12 unit tests
- [x] 16-03-PLAN.md — SettingsRepository: userPhysicalStats Flow + setter + nutrition_goals_banner_dismissed Boolean
- [x] 16-04-PLAN.md — OverviewViewModel: userPhysicalStats + bannerVisible StateFlows + updateUserPhysicalStats + dismissBanner; chain dismiss into updateNutritionGoals
- [x] 16-05-PLAN.md — Android: NutritionGoalsEditorScreen + Routes/MainScreen wiring + OverviewScreen banner & edit pencil (with human verify checkpoint)
- [x] 16-06-PLAN.md — iOS: NutritionGoalsEditorView SwiftUI sheet + OverviewView banner & edit pencil + pbxproj target membership (with human verify checkpoint)
- [x] 16-07-PLAN.md — Gap closure (WR-05): snap kcal to multiple of 50 in TdeeCalculator.buildSplit + tests
- [x] 16-08-PLAN.md — Gap closure (WR-03 / WR-04): one-shot field-state seeding in NutritionGoalsEditor on Android (rememberSaveable guards) and iOS (@State guards)
- [x] 16-09-PLAN.md — Gap closure (CR-01 / WR-06 / IN-02): iOS sheet onDismiss refresh + bannerVisible default-false; Android remove redundant first-composition LaunchedEffect refresh

**Status:** Complete (verified 2026-04-28 — see `16-VERIFICATION.md`)

### Phase 17: Progress-pic feature with biometric-locked gallery: post-workout photo capture (camera or library) tied to workouts, gallery under Overview tab with blurred-by-default tiles showing day highlights (volume, PRs, nutrition), tap to unlock individual image via biometrics with passcode fallback, re-locks on gallery close. Cross-platform iOS and Android via Compose Multiplatform.

**Goal:** Ship a privacy-conscious post-workout photo feature: capture (camera or library) tied to the just-saved workout; new "Fortschritts-Galerie" entry on the Overview tab renders one tile per workout-with-photos as a blurred cover photo overlaid with date/volume/PR-count/goal-day stats; tapping a tile triggers OS biometric/passcode auth (LAContext .deviceOwnerAuthentication on iOS, BiometricPrompt BIOMETRIC_STRONG or DEVICE_CREDENTIAL on Android) and pages all of that workout's photos in a swipeable carousel; closing re-blurs the tile. Files live in app-private storage with NSFileProtectionComplete + NSURLIsExcludedFromBackupKey on iOS and dataExtractionRules + fullBackupContent exclusion on Android. Schema bumps Room v8 → v9 via additive AutoMigration. Phase 15 engine, retroactive walker, rank ladder, and existing nutrition/gamification surfaces are unchanged.
**Requirements**: D-17-01 through D-17-19 (decisions in 17-CONTEXT.md serve as the requirement source)
**Depends on:** Phase 16
**Plans:** 8/8 plans complete

**Wave structure:**
- Wave 1 (foundation): 01 (Room v8 → v9 — ProgressPictureEntity + DAO + AutoMigration)
- Wave 2: 02 (commonMain expect classes + ProgressPictureRepository + domain models)
- Wave 3 (parallel — different platform files): 03 (Android actuals: PhotoVault.android + PhotoCaptureLauncher.android + BiometricGate.android + AndroidManifest hardening + backup_rules + biometric dep); 04 (iOS actuals: PhotoVault.ios with NSFileProtectionComplete + isExcludedFromBackupKey, PhotoCaptureLauncher.ios via UIImagePickerController + PHPickerViewController, BiometricGate.ios via LAContext + Info.plist additions)
- Wave 4 (parallel — different files): 05 (capture flow: WorkoutSessionState.Finished + ProgressPicturePromptViewModel + ProgressPicturePromptCard mounted on Finished branch); 06 (gallery + viewer: ProgressGalleryViewModel + ProgressViewerViewModel with explicit unlockedWorkoutId gate for T-BIOMETRIC-BYPASS + ProgressGalleryScreen + ProgressViewerScreen + Routes/MainScreen/OverviewScreen wiring)
- Wave 5: 07 (DI — ProgressGalleryModule + PlatformModule.{android,ios} bindings for PhotoVault/PhotoCaptureLauncher/BiometricGate + 3 iOS KoinHelpers)
- Wave 6: 08 (iOS handoff doc — 17-IOS-HANDOFF.md specifying ProgressGalleryView.swift + ProgressViewerView.swift + ProgressPicturePromptCard.swift + OverviewView.swift edit + WorkoutFinishedView.swift edit; D-17-18 enforces SwiftUI not Compose Multiplatform on iOS)

Plans:
- [x] 17-01-PLAN.md — Room schema v8 → v9: ProgressPictureEntity + ProgressPictureDao + AutoMigration(8, 9)
- [x] 17-02-PLAN.md — commonMain expect classes (PhotoVault, PhotoCaptureLauncher, BiometricGate) + ProgressPictureRepository + domain models (ProgressPicture, ProgressGalleryTile, UnlockResult)
- [x] 17-03-PLAN.md — Android actuals + OS hardening: PhotoVault.android, PhotoCaptureLauncher.android, BiometricGate.android, AndroidManifest dataExtractionRules + fullBackupContent, androidx.biometric dep
- [x] 17-04-PLAN.md — iOS actuals + OS hardening: PhotoVault.ios with NSFileProtectionComplete + isExcludedFromBackupKey, PhotoCaptureLauncher.ios, BiometricGate.ios with LAContext.deviceOwnerAuthentication, Info.plist NSPhotoLibraryUsageDescription + NSFaceIDUsageDescription
- [x] 17-05-PLAN.md — Capture flow + Workout integration: extend WorkoutSessionState.Finished with workoutId, ProgressPicturePromptViewModel, ProgressPicturePromptCard mounted on Finished branch
- [x] 17-06-PLAN.md — Gallery + Viewer: ProgressGalleryViewModel + ProgressViewerViewModel (unlockedWorkoutId gate addresses T-BIOMETRIC-BYPASS) + ProgressGalleryScreen + ProgressViewerScreen + Routes/MainScreen/OverviewScreen wiring
- [x] 17-07-PLAN.md — DI: ProgressGalleryModule + PlatformModule.{android,ios} bindings + 3 iOS KoinHelpers (Gallery, Viewer, Prompt)
- [x] 17-08-PLAN.md — iOS handoff doc 17-IOS-HANDOFF.md (per D-17-18 — SwiftUI hand-written, not Compose Multiplatform)

### Phase 18: AI Features — F6 Workout Generation + F8 Meal Generation (BYOK, OpenAI-compatible HTTPS)

**Requirements:** REQ-AI-01 … REQ-AI-08 — see `.planning/REQUIREMENTS-ai-features.md`. Anchored in Lastenheft F6 ("MCP/API für CRU(D) Operationen, Systemprompt, vordefinierte Struktur, UI Interface, LLM, D manuell möglich") and F8 ("KI querien für Mahlzeiten").
**Depends on:** Phase 17 (no code dep — sequencing only, so AI/network is the *last* dependency added before deadline). Leverages existing `WorkoutTemplate`, `Exercise`, `Recipe`, `Food`, `ConsumptionEntry`, `NutritionGoals` data models from v1.0 + Post-v1.5.

**Goal:** Ship two AI-driven generation flows on a single Bring-Your-Own-API-Key OpenAI-compatible HTTPS LLM transport. **(1) Workout AI (F6):** user fills a small form (target muscles, exercise count, optional Push/Pull/Legs-style split), app calls the configured LLM with a versioned system prompt + JSON schema, persists the result(s) through the existing `WorkoutTemplate` repository — single-template or multi-template "chained" generation per the user's split selection. Delete stays manual. **(2) Meal AI (F8):** user taps "Fill remaining macros", app reads today's `ConsumptionEntry` log against `NutritionGoals` to compute remaining kcal + protein/carbs/fat/sugar, prompts the LLM with those targets + a recipe schema, persists the result through the existing `Recipe` entity. Both flows share: a single `OpenAICompatibleClient` (Ktor), a Settings screen with API key + base URL stored in Keychain (iOS) / EncryptedSharedPreferences (Android), schema validation on every response, and consistent error UX for timeout / 4xx / 5xx / refusal. No on-device LLM, no MCP server, no tool-use, no multi-provider abstraction (all deferred — see `SEED-002` and `.planning/notes/ai-features-design-decisions.md`).

**Scope notes:**
- **Provider:** Single OpenAI-compatible HTTPS client. Works against OpenAI, Together.AI, OpenRouter, Groq, and any compatible endpoint by changing base URL only.
- **Structured output:** JSON schemas live in source under the AI feature module; validated before any DB write. Use `response_format: json_schema` where supported, fall back to schema-validate-and-retry pattern otherwise.
- **System prompts:** Versioned in the repo (one for workout, one for recipe), referenced by the client. Reviewable in PRs.
- **Settings UX:** API key + base URL + optional model name. Key never logged or persisted to plaintext storage. Cleared key disables AI features with explanatory empty state.
- **F6 form:** Reuse existing `AnatomyPickerSheet` (Phase 14) for muscle-group selection where it makes sense. Number-of-exercises and split-style as additional fields. Single big LLM call returning an array for multi-template generation (decided at plan-phase between this and N sequential calls per D-AI-08).
- **F8 entry point:** "Fill remaining macros" CTA from the Nutrition tab / Overview surface. Refuses cleanly when remaining macros are negative or near zero.
- **Cross-platform:** Compose Multiplatform shared UI for the form + settings screen; iOS-specific handoff doc only if the AI Settings screen needs SwiftUI per existing project conventions (decided at plan-phase).
- **Schema migration:** None expected — existing `WorkoutTemplate` / `Recipe` schemas are reused as-is. Verify during plan-phase that `Recipe` accepts AI-sourced provenance metadata (e.g., a `source` field) without a Room migration; if not, bump v9 → v10 with an additive AutoMigration.

**Out of scope (this phase):**
- On-device / local Gemma inference — see `.planning/seeds/SEED-002-on-device-gemma-llamatik.md`.
- Multi-provider abstraction with more than one concrete client.
- An MCP server implementation.
- Agent loops / tool use.
- Prompt-injection hardening, jailbreak audits, content-moderation pipelines.
- RAG, embeddings, fine-tuning.
- AI editing of *existing* templates / recipes (Update via AI).
- Streaming responses.
- Cost / token-count UI.

**Plans:** 8/10 plans executed

**Wave structure** (serialized where plans share files; parallelism within a wave only when files_modified do not overlap):
- Wave 1 (foundation, parallel): 01 (Room v9 → v10 — source column on 4 entities + AutoMigration(9, 10)), 02 (HttpClientFactory plugins + AiChatDto + OpenAICompatibleClient + AiError sealed)
- Wave 2 (parallel — different files): 03 (versioned prompt .md files + WorkoutAiSchema + RecipeAiSchema + AiPromptCatalog), 04 (SecureKeyStore expect/actual + EncryptedSharedPreferences/Keychain bindings + SettingsRepository AI fields + security-crypto dep)
- Wave 3: 05 (AiSettingsViewModel + AiModule + SharedModule includes + Routes/MainScreen + AiSettingsScreen + SettingsSheet AI row + iOS KoinHelper) — *checkpoint:human-verify*
- Wave 4: 06 (WorkoutAiUseCase + WorkoutAiPreview + WorkoutAiViewModel + AiModule binding + iOS KoinHelper + TemplateRepository.createTemplate source arg)
- Wave 5: 07 (AiPreviewSheet sealed shape + Workout body + AiWorkoutGenScreen + TemplateListScreen sparkles + MainScreen route) — *checkpoint:human-verify*
- Wave 6: 08 (RecipeAiUseCase + RecipeAiPreview + RecipeAiViewModel + AiModule binding + iOS KoinHelper)
- Wave 7: 09 (AiPreviewSheet Recipe branch + AiMealGenScreen + NutritionDailyLogScreen sparkles + MainScreen route) — *checkpoint:human-verify*
- Wave 8: 10 (18-IOS-HANDOFF.md spec)

Plans:
- [x] 18-01-PLAN.md — Room v9 → v10: nullable source column on WorkoutTemplate / Exercise / Recipe / Food entities + AutoMigration(9, 10) + propagate field through domain models
- [x] 18-02-PLAN.md — OpenAICompatibleClient + AiChatDto + AiError sealed + HttpClientFactory ContentNegotiation/HttpTimeout(60s) install
- [x] 18-03-PLAN.md — Versioned prompt files (workout-system.md / recipe-system.md) + WorkoutAiSchema + RecipeAiSchema + AiPromptCatalog (reuses readResourceFile)
- [x] 18-04-PLAN.md — SecureKeyStore expect/actual (Keychain on iOS, EncryptedSharedPreferences on Android) + PlatformModule bindings + SettingsRepository AI configuration fields
- [x] 18-05-PLAN.md — AiSettingsViewModel + AiModule (registered in SharedModule) + AiSettingsScreen + SettingsSheet AI row + Routes/MainScreen wiring + iOS AiSettingsKoinHelper
- [x] 18-06-PLAN.md — WorkoutAiUseCase + WorkoutAiViewModel + AiModule binding + iOS WorkoutAiKoinHelper + TemplateRepository.createTemplate gains source arg
- [ ] 18-07-PLAN.md — AiPreviewSheet sealed AiPreviewContent + Workout body + AiWorkoutGenScreen + TemplateListScreen sparkles entry + MainScreen route
- [x] 18-08-PLAN.md — RecipeAiUseCase + RecipeAiViewModel + AiModule binding + iOS RecipeAiKoinHelper
- [ ] 18-09-PLAN.md — AiPreviewSheet Recipe branch + AiMealGenScreen + NutritionDailyLogScreen sparkles + MainScreen route
- [x] 18-10-PLAN.md — 18-IOS-HANDOFF.md spec for SwiftUI surfaces (4 new + 2 modify)

**Success criteria (draft, finalized at plan-phase):**
- A user with no API key sees clear empty-state UX in the AI flows; settings prompts them to add a key.
- A user with a valid key generates a workout template from a small form; the result is editable / launchable via the existing template UI; multi-template "split" generation produces ≥3 templates from one form submission.
- A user with a valid key generates a recipe matching today's remaining macros to within ±10% on a representative day; the recipe is saveable and shows up in the existing recipe collection.
- Schema-invalid LLM responses surface a clear error and never write partial data.
- BYOK key is stored in Keychain (iOS) / EncryptedSharedPreferences (Android), never in DataStore plaintext.
- Phase 15 gamification, Phase 16 nutrition goals, and Phase 17 progress-pic flows continue to work unchanged.

### Phase 19: Geofencing-basierte Workout-Enforcement (XP-Strafe bei vorzeitigem Verlassen, Early-Exits-Budget)

**Premise:** Klassischer Gym-Drop-Out — User startet Plan, macht die Hälfte, geht heim. Geofencing erzwingt das Durchhalten: erstes geloggtes Set fixiert die aktuelle Position als Trainingsort, ein ~50m-Geofence wird gesetzt, die App pollt alle ~2min ob der User noch drin ist. Verlässt er die Zone ohne das Workout sauber zu beenden, wird das Workout abgebrochen und XP abgezogen (Vorschlag: -100 bis -200, finale Höhe in Discuss). Eskape-Hatch: 2 "Early Exits" pro Monat erlauben sauberes Verkürzen ohne Strafe — monatliches Reset.

**Scope:**
- Cross-platform iOS + Android (Compose Multiplatform shared logic; Platform-spezifische Location/Notification APIs via expect/actual)
- Permissions-Flow für Location-Always-Allow (inkl. graceful Degradation bei Verweigerung)
- Push-Notification als Warnung beim Verlassen der Zone
- UI: Geofence-Status-Indikator im Workout-Flow + Early-Exits-Counter in Settings
- Background-Behavior — App muss auch im Hintergrund prüfen können
- Integration in bestehendes XP-System (Phase 15) für Penalty + Early-Exit-Tracking
- Wiederverwendung der bereits existierenden `LocationProvider`-Abstraktion (commonMain) und `IosLocationProvider` / `AndroidLocationProvider`

**Offene Designfragen (für /gsd-discuss-phase 19):**
- Exakte XP-Strafe (-100 vs -200 vs gestaffelt nach Workout-Länge)
- Polling alle 2min vs. native Geofence-Events (CLCircularRegion bzw. GeofencingClient `addGeofences`)
- Verhalten bei verweigerter Location-Permission (Feature deaktivieren? Soft-Warning? Phasenweise Eskalation?)
- Background-Mode-Strategie iOS (Significant-Change vs. Continuous Updates) + Android (Foreground Service?)
- Geofence-Trigger: erstes geloggtes Set vs. erste N Sets vs. konfigurierbar
- Monthly Reset: Kalendermonat oder rolling 30-day window?

**Goal:** Geofence-gated workout enforcement — the first logged set anchors a 50m geofence at the user's current location via native OS region monitoring (CLCircularRegion / GeofencingClient). Leaving the zone without using the explicit "Workout beenden" button triggers a 5-minute grace period; if the user does not return, the workout auto-saves as `abandoned=true` (volume-XP still awarded), a staffeled XP penalty (-50 to -200 per `XpFormula.geofenceExitPenalty`) is applied, and a local notification is posted. The 10-minute F5 inactivity timer is fully removed (replaced, not added). Users get 2 Early Exits per calendar month (D-19-07) that bypass the penalty when invoked via the explicit menu action. UI surfaces a 4-state status chip during Active state plus a Settings "Workout Enforcement" row + detail sheet.

**Requirements:** D-19-01 through D-19-16 (tracked via CONTEXT.md decision IDs; no formal REQ-* IDs in REQUIREMENTS.md per planning_context note).
**Depends on:** Phase 18 (AI features), Phase 15 (XP-System), post-v1.5 LocationProvider abstraction.
**Plans:** 4/7 plans executed

Plans:
- [x] 19-01-PLAN.md — Wave 1: commonMain interfaces + XP-constants refactor (GeofenceProvider, PermissionController, EarlyExitTracker, geofence penalty constants, F5 removal)
- [x] 19-02-PLAN.md — Wave 1: Room schema v10→v11 (abandoned flag) + WorkoutRepository.saveAbandonedWorkout
- [x] 19-03-PLAN.md — Wave 2: iOS platform impls (IosGeofenceProvider, IosPermissionController, AppDelegate cold-start, Info.plist UIBackgroundModes + privacy strings)
- [x] 19-04-PLAN.md — Wave 2: Android platform impls (AndroidGeofenceProvider, BroadcastReceiver, AndroidPermissionController, AndroidManifest permissions)
- [ ] 19-05-PLAN.md — Wave 3: WorkoutSessionViewModel integration (geofence lifecycle, grace timer, Early-Exit flow) + processAbandonedWorkout
- [ ] 19-06-PLAN.md — Wave 4: iOS UI (chip, rationale sheet, banners, early-exit dialog, Settings detail view, notifications) + visual UAT
- [ ] 19-07-PLAN.md — Wave 4: Android UI (chip, rationale sheet, banners, early-exit dialog, Settings detail sheet, notifications) + visual UAT
