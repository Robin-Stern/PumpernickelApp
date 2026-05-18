---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: Android Material 3 UI
status: idle
stopped_at: Phase 19 complete — all 7 plans + Layer A/B crash & UI follow-ups + 4 demo-prep quick-tasks; user-confirmed UAT pass 2026-05-17
last_updated: "2026-05-18T09:10:00.000Z"
last_activity: 2026-05-18 -- Completed quick task 260518-ey4: hardcoded skeletonRowCount → user exerciseCount
progress:
  total_phases: 6
  completed_phases: 5
  total_plans: 49
  completed_plans: 49
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-22)

**Core value:** Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow
**Current focus:** Phase 19 closed; ready for next phase or demo prep

## Current Position

Milestone: v1.5 (shipped 2026-03-31)
Phase: 19 (geofencing-workout-enforcement) — COMPLETE (UAT confirmed 2026-05-17)
Plan: 7 of 7
Branch: `android-ios-parity` (ahead of `main` by ~25 commits — merge pending)
Status: Idle. Phase 19 functional + UAT confirmed. Diagnostic prints removed.
Last activity: 2026-05-17 -- Phase 19 closure + diagnostic-prints cleanup (quick-task 260517-x4p)

Progress: [██████████] 100%

## ⚠️  Untracked Drift

Between the last GSD artifact (Phase 14 completed 2026-03-31, commit `4d02ce0`) and the current branch tip (`fe297ad`, 2026-04-14), 28 commits landed on `feature/workouts` outside the GSD workflow. Highlights:

- **Nutrition feature (F2 from Lastenheft)** — Food / Recipe / ConsumptionEntry domain, 11 use cases in `domain/nutrition/`, 4 ViewModels in `presentation/nutrition/`, OpenFoodFacts barcode lookup, iOS views in `Views/Nutrition/` (DailyLog, FoodEntry, RecipeList, RecipeCreation, BarcodeScanner, MacroRow), Android screens + navigation
- **Dynamic theming** — Light/dark/system toggle + 8 accent color presets, persisted in DataStore, iOS `ThemeManager` (@Observable) with `asyncSequence` observation on app root, unified Material palette on Android
- **Nutrition goals** — `NutritionGoals` domain model (calorie/protein/fat/carb/sugar), persisted in `SettingsRepository`, surfaced on Overview tab via `OverviewViewModel`
- **Workout/History polish** — Template editor redesign, history detail set-count + RIR display, PB calculation fix, semantic colors replacing hardcoded RGB, rest timer improvements
- **Infrastructure** — Room schema v4 → v7 (AutoMigration 6→7 registered; 4 new nutrition entities), composeApp → `android-kmp-library` plugin with androidApp module extracted, kotlinx-datetime + Ktor client added to stack, camera/barcode permissions wired

See `MILESTONES.md` → "Post-v1.5 (Untracked)" for the full summary. No per-phase artifacts exist for this work — treat it as shipped but not planned.

## Performance Metrics

**Velocity:**

- Total plans completed: 33 (12 v1.0 + 9 v1.1)
- v1.1 execution: 6 phases, 9 plans, 18 tasks in 2 days

**By Phase (v1.1):**

| Phase | Plans | Duration |
|-------|-------|----------|
| Phase 05 P01 | 3min | 2 tasks |
| Phase 05 P02 | 2min | 2 tasks |
| Phase 06 P01 | 5min | 2 tasks |
| Phase 07 P01 | 3min | 2 tasks |
| Phase 08 P01 | 2min | 2 tasks |
| Phase 08 P02 | 2min | 2 tasks |
| Phase 09 P01 | 1min | 2 tasks |
| Phase 10 P01 | 4min | 2 tasks |
| Phase 10 P02 | 3min | 2 tasks |
| Phase 11-android-shell-navigation P01 | 5 | 2 tasks | 12 files |
| Phase 12-exercise-catalog-templates P01 | 10 | 2 tasks | 5 files |
| Phase 12-exercise-catalog-templates P02 | 4 | 2 tasks | 5 files |
| Phase 13-workout-session-core P01 | 2 | 1 tasks | 1 files |
| Phase 13-workout-session-core P02 | 3 | 2 tasks | 2 files |
| Phase 13-workout-session-core P03 | 105 | 2 tasks | 2 files |
| Phase 13-workout-session-core P04 | 3 | 2 tasks | 1 files |
| Phase 14-history-settings-anatomy P01 | 5 | 2 tasks | 6 files |
| Phase 15 P10 | 2 | 3 tasks | 4 files |
| Phase 15 P11 | 8 | 3 tasks | 5 files |
| Phase 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p P08 | 3min | 2 tasks | 2 files |
| Phase 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p P09 | 2min | 3 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
See PROJECT.md for full decision history across v1.0, v1.1, v1.5, and post-v1.5.

- [Phase 11-android-shell-navigation]: compileSdk bumped to 36: Compose BOM 2025.06.00 requires API 36
- [Phase 11-android-shell-navigation]: initKoin() accepts KoinApplication lambda to enable androidContext() before module loading
- [Phase 11-android-shell-navigation]: KMP v2 source layout: src/androidMain/ required by KMP Gradle plugin in Kotlin 2.3
- [Phase 11-android-shell-navigation]: Compose BOM placed in top-level dependencies{} block: platform() unavailable in KMP sourceSets block
- [Phase 12-exercise-catalog-templates]: collectAsState() used over collectAsStateWithLifecycle() - lifecycle-runtime-compose not in explicit androidApp deps
- [Phase 12-exercise-catalog-templates]: ExerciseDetailRoute.exerciseId fixed from Long to String to match Exercise.id domain type
- [Phase 12-exercise-catalog-templates]: Anatomy picker (Canvas body drawing) deferred to Phase 14 - muscle group uses ExposedDropdownMenuBox
- [Phase 12-exercise-catalog-templates]: Move-up/down buttons for exercise reorder: avoids reorderable library dependency for prototype scope; calls viewModel.moveExercise() with existing ViewModel logic
- [Phase 12-exercise-catalog-templates]: koinViewModel(viewModelStoreOwner = parentEntry) in ExercisePickerRoute: shares same TemplateEditorViewModel instance for direct addExercise() call across screens
- [Phase 13-workout-session-core]: LazyColumn + SnapFlingBehavior for drum picker: gives iOS-equivalent fling physics without Canvas draw complexity
- [Phase 13-workout-session-core]: ActiveWorkoutContent extracted as private composable with all state as parameters to keep sub-composables pure and avoid ViewModel re-injection
- [Phase 13-workout-session-core]: ExerciseOverviewSheetContent is pure content composable without ModalBottomSheet wrapper — caller owns sheet lifecycle for testability
- [Phase 13-workout-session-core]: onJumpToExercise and onReorderExercise threaded through ActiveWorkoutContent parameter list from WorkoutSessionScreen
- [Phase 13-workout-session-core]: Edit sheet state hoisted to WorkoutSessionScreen level: both Active and Reviewing branches share EditSetSheetContent without duplication
- [Phase 13-workout-session-core]: CompletedSetsSection extended with exerciseIndex + onEditSet callback (default no-op): backward-compatible tap-to-edit wiring
- [Phase 14-history-settings-anatomy]: WorkoutHistoryDetailScreen uses DisposableEffect onDispose to call clearDetail() — ensures stale detail is not shown on re-navigation
- [Post-v1.5 (untracked)]: MuscleRegionPaths moved from iOS-only to commonMain/domain/model for cross-platform anatomy reuse
- [Post-v1.5 (untracked)]: composeApp migrated to `android-kmp-library` plugin; androidApp extracted as separate module (enables per-module dependency graphs)
- [Post-v1.5 (untracked)]: iOS theming uses `ThemeManager.shared` (@Observable) + `Color.appAccent` computed extension; observed from `AppRootView` via `withTaskGroup` on two flows
- [Post-v1.5 (untracked)]: NutritionGoals defaults chosen as 2500 kcal / 150g protein / 80g fat / 300g carbs / 50g sugar; persisted as string-encoded ints in DataStore
- [Post-v1.5 (untracked)]: OpenFoodFacts barcode lookup via Ktor CIO client; no local caching (network required per scan)
- [Phase 15]: OverviewRankStrip is passive (let rankState) — OverviewView remains single subscription owner per existing pattern
- [Phase 15]: UnlockModalView typealias uses flat Shared.UnlockEventRankPromotion — matches KMP-NativeCoroutines 1.0.2 flat export convention
- [Phase 15]: unlockEvents (not unlockEventsFlow) is the correct KMPNativeCoroutines property name — KMP-Native generates the Swift property name from the Kotlin val name directly
- [Phase 15]: KMP-Native sealed subclasses are nested Swift types (Shared.UnlockEvent.RankPromotion) not flat names — use swift_name attribute in Shared.h to determine correct access path
- [Phase ?]: [Phase 16-08]: Use rememberSaveable booleans + LaunchedEffect for one-shot Flow → state seeding on Android — survives configuration changes and prevents re-keyed remember() from discarding user edits
- [Phase ?]: [Phase 16-08]: Use @State guard + 'continue' (not 'break') in iOS async-sequence loops so first-launch nil emissions don't permanently lock initialization while subsequent re-emissions still short-circuit
- [Phase ?]: [Phase 16-08]: Hard-code editor placeholder defaults (80/180/30/MALE/MODERATELY_ACTIVE; 2500/150/300/80/50) instead of reading initial Flow value — keeps the LaunchedEffect/async-sequence as the single source of truth for first-emission seeding
- [Phase ?]: [Phase 16-09]: iOS rings staleness fixed via .sheet onDismiss refresh — non-invasive, no shared-VM plumbing across the editor sheet boundary
- [Phase ?]: [Phase 16-09]: bannerVisible @State defaults to false on iOS — observeBannerVisible() seeds the persisted value on first emission; one-frame missing-banner is less distracting than the flash
- [Phase ?]: [Phase 16-09]: Android first-composition refresh handled by OverviewViewModel.init { refresh() } alone — LaunchedEffect(Unit) was redundant and spawned a second concurrent refresh per re-entry

### Roadmap Evolution

- Phase 15 added (2026-04-22): Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks (F4 from Lastenheft). Added outside an active milestone — assign to a new milestone before planning.
- Phase 15.1 inserted after Phase 15 (2026-04-23): Ranks & Achievements Browser — rank ladder + achievement catalog UI (URGENT). Surfaced by Phase 15 UAT — current gamification is opaque: users see their rank and unlock toasts but cannot browse what tiers / achievements exist. Pure presentation over existing GamificationRepository / RankLadder / AchievementCatalog — no new domain logic.
- Phase 16 added (2026-04-28): Set nutrition goals (kcal/protein/carbs/fat per day) — surface progress on Overview tab and award bonus XP when daily goal achieved within tolerance. Builds on existing post-v1.5 NutritionGoals model (in SettingsRepository) + Phase 15 gamification engine; user mentions a ±5–10% tolerance + XP reward already partially in code — planner should investigate first.
- Phase 17 added (2026-04-28): Progress-pic feature with biometric-locked gallery — post-workout photo capture (camera or library) tied to workouts; gallery surfaces under Overview tab with blurred-by-default tiles showing day highlights (volume, PRs, nutrition); tap unblurs individual image via biometric auth (passcode fallback); re-locks on gallery close. Cross-platform iOS + Android via Compose Multiplatform. Spans new domain (ProgressPicture entity tied to WorkoutHistory), platform integrations (camera/photo-library + LocalAuthentication on iOS, CameraX/PhotoPicker + BiometricPrompt on Android), and a new gallery surface integrated into Overview rather than a corner button.
- Phase 19 added (2026-05-13): Geofencing-basierte Workout-Enforcement — erstes geloggtes Set setzt einen ~50m-Geofence um den aktuellen Standort; verlässt der User die Zone vor regulärem Workout-Ende, wird das Workout abgebrochen und XP abgezogen (-100 bis -200, finalisiert in Discuss). Eskape-Hatch: 2 Early Exits pro Monat erlauben sauberes Verkürzen. Cross-platform iOS + Android. Baut auf der vorhandenen `LocationProvider`-Abstraktion auf, neu sind Geofence-Logik, Background-Polling-Strategie, Notification-Trigger, Permissions-UX und XP-System-Integration (Phase 15). Viele offene Designfragen — Discuss-Phase vor Planung empfohlen.

### Pending Todos

- [ios] Retroactive progress photo attach from History (2026-05-06) — `.planning/todos/pending/2026-05-06-retroactive-progress-photo-attach-from-history.md`

### Blockers/Concerns

- GSD history has a gap: nutrition + theming shipped without phase artifacts. If future work needs to reference how those features were built, `git log` is authoritative — not `.planning/phases/`.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260423-sja | Clean up AchievementGalleryScreen category headers — M3 section-break hierarchy (titleLarge + 20/8 padding + hairline divider) | 2026-04-23 | 4c0740b | [260423-sja-clean-up-achievementgalleryscreen-catego](./quick/260423-sja-clean-up-achievementgalleryscreen-catego/) |
| 260501-wcg | Phase 17 iOS surfaces (SwiftUI gallery + viewer + prompt card + presenter holder wire) | 2026-05-01 | 17581fa | [260501-wcg-phase-17-ios-surfaces](./quick/260501-wcg-phase-17-ios-surfaces/) |
| 260506-0hk | fix iOS Progress Gallery tile layout — tiles render offscreen-left with date label clipped (visual checkpoint pending) | 2026-05-05 | db2812e | [260506-0hk-fix-ios-progress-gallery-tile-layout-til](./quick/260506-0hk-fix-ios-progress-gallery-tile-layout-til/) |
| 260506-pf3 | iOS Progress Gallery photo viewer prompts for passcode instead of Face ID (visual UAT pending) | 2026-05-06 | abb61f5 | [260506-pf3-ios-progress-gallery-photo-viewer-prompt](./quick/260506-pf3-ios-progress-gallery-photo-viewer-prompt/) |
| 260510-w9h | Swap Together preset default + error-msg recommendations to google/gemma-4-31B-it (eval-driven) | 2026-05-10 | b643711 | [260510-w9h-app-defaults-f-r-ai-provider-von-openai-](./quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/) |
| 260510-wgs | Align iOS AISettingsView model-suggestion notes with eval evidence (Together/OpenRouter/Groq) | 2026-05-10 | 7d7962a | [260510-wgs-ios-aisettingsview-model-suggestions-an-](./quick/260510-wgs-ios-aisettingsview-model-suggestions-an-/) |
| 260510-x7o | B.1.code: workout-prompt array-wrapper restatement + multi-model.mjs CLI flags for targeted re-runs (prompt edit later reverted in 501c31d — see B.1 delta in MULTI-MODEL-RESULTS.md) | 2026-05-11 | df71e85 | [260510-x7o-b-1-code-workout-prompt-wrapper-restatem](./quick/260510-x7o-b-1-code-workout-prompt-wrapper-restatem/) |
| 260511-117 | B.2.code: add worked-example macro arithmetic section to recipe-system-prompt.md (target: Llama-3.3 arithmetic + Qwen mode-collapse failure modes) | 2026-05-11 | 380d8d2 | [260511-117-b-2-code-recipe-prompt-mit-worked-exampl](./quick/260511-117-b-2-code-recipe-prompt-mit-worked-exampl/) |
| 260511-1h7 | Didactic end-to-end explainer for eval harness (evals/PROMPT-TESTING-EXPLAINED.md, 565 lines, 9 sections + ASCII diagrams) — presentation material | 2026-05-11 | 4d1d615 | [260511-1h7-didaktische-dokumentation-des-eval-harne](./quick/260511-1h7-didaktische-dokumentation-des-eval-harne/) |
| 260511-gff | B.4.code: refusal path measurable — validators honor expectRefusal, 2 impossible-scenario cases per suite, sharpened Refusal sections in both system prompts | 2026-05-11 | e33ef0d | [260511-gff-b-4-code-refusal-path-messbar-machen-exp](./quick/260511-gff-b-4-code-refusal-path-messbar-machen-exp/) |
| 260516-nfn | Debug-GPS-Mock für Phase 19: DebugGeofenceProvider (commonMain) + build-gated Koin override (BuildConfig.DEBUG / #if DEBUG) + Debug-Panel in Settings mit Enter/Exit/Error-Trigger-Buttons — Phase-19-Flow ohne physisches Rumlaufen testbar. UAT auf Emulator/Simulator pending | 2026-05-16 | 8846475 | [260516-nfn-debug-gps-mock-f-r-phase-19-geofencing-b](./quick/260516-nfn-debug-gps-mock-f-r-phase-19-geofencing-b/) |
| 260517-pzh | Debug Mock-Panel direkt im Workout-Screen erreichbar machen — DEBUG-gated overlay/FAB öffnet bestehendes DebugGeofencePanel inline (iOS + Android), Exit-Trigger ohne App-Switch testbar mid-workout. Manual UAT pending | 2026-05-17 | 2f3808e | [260517-pzh-debug-mock-panel-direkt-im-workout-scree](./quick/260517-pzh-debug-mock-panel-direkt-im-workout-scree/) |
| 260517-ra5 | Layer B — Workout-Abort Recap-View per UI-SPEC §180: Finished state erweitert um abandoned/loggedSets/penaltyXp, neue WorkoutAbortedView (iOS) + AbortedContent (Android), branch in WorkoutSessionView/WorkoutSessionScreen. Built auf Layer A (87ff836) der Debug-Session ios-geofence-grace-expiry-crash. Manual UAT pending | 2026-05-17 | 180a915 | [260517-ra5-layer-b-workout-abort-recap-view-per-ui-](./quick/260517-ra5-layer-b-workout-abort-recap-view-per-ui-/) |
| 260517-vn7 | Demo-Vorbereitung — Settings Debug-Modus-Toggle + konfigurierbare Grace-Period (5/10/30/60/300sec) Picker. SettingsRepository um debugModeEnabled+gracePeriodSeconds erweitert; WorkoutSessionViewModel.startGracePeriod liest dynamisch aus Repo (defaults 300L via XpFormula). iOS + Android Settings-Section, in-workout-Pille/FAB gated. Manual UAT pending | 2026-05-17 | eef64c2 | [260517-vn7-settings-debug-mode-toggle-configurable-](./quick/260517-vn7-settings-debug-mode-toggle-configurable-/) |
| 260517-w2f | End-Button-Konsolidierung — 3 redundante Workout-End-Buttons (Abandon/Finish/Workout beenden) → 1 kontextsensitiver Trailing-Button "Workout beenden". Tap → viewModel.requestEarlyExit(); allDone-Check öffnet entweder Review-Pfad oder EarlyExitConfirmDialog (Budget/Penalty). Discard-Pfad entfällt, "Abandon Workout?" Dialog entfernt. iOS + Android. Manual UAT pending | 2026-05-17 | 0bb5c18 | [260517-w2f-end-button-konsolidierung-drei-redundant](./quick/260517-w2f-end-button-konsolidierung-drei-redundant/) |
| 260517-x4p | Phase 19 Closure + Diagnostic-Prints Cleanup — 44 println/print-Marker (`[Geofence]`/`[LocProvider]`/`[LocDelegate]`/`[PermController]`/`[Rationale]`/`[SwiftPerm]`) aus 4 Files entfernt; FK-race-Catch-Log behalten aber Tag-Prefix gestrippt. STATE.md auf `idle`/100%, VERIFICATION.md auf `passed` mit uat_confirmed, ROADMAP Phase 19 als COMPLETE markiert | 2026-05-17 | fcc6478 | [260517-x4p-phase-19-closure-diagnostic-prints-clean](./quick/260517-x4p-phase-19-closure-diagnostic-prints-clean/) |
| 260518-e7r | AI Generation Timeout auf 10 Minuten erhöhen (Ktor HttpClient + Stream) — 6 Timeout-Sites (per-request buffered+stream, iOS Ktor engine + Darwin URLSession, Android Ktor engine + OkHttp) auf 600_000 ms / 600s gehoben; socketTimeout 120_000 unverändert, TCP-connect 30s unverändert | 2026-05-18 | ab3f1a9 | [260518-e7r-ai-generation-timeout-auf-10-minuten-erh](./quick/260518-e7r-ai-generation-timeout-auf-10-minuten-erh/) |
| 260518-egc | iOS Geofence-Exit-Notification Parity — AppDelegate als UNUserNotificationCenterDelegate registriert + requestAuthorization beim Launch + willPresent[.banner,.sound,.list] für Foreground-Präsentation. Root cause: ohne expliziten Authorize-Call schlugen alle `postGeofenceNotification`-Calls silent fehl; ohne Delegate unterdrückt iOS Foreground-Notifications by default. Manual UAT pending (App neu installieren/Notif-Permission akzeptieren) | 2026-05-18 | 289f2e8 | [260518-egc-ios-geofence-exit-notification-posten-ko](./quick/260518-egc-ios-geofence-exit-notification-posten-ko/) |
| 260518-eny | iOS AI Workout Generation State-Reset nach Save — one-shot `savedEvent: SharedFlow<Unit>` + `reset()` im WorkoutAiViewModel; AIWorkoutGenView ruft reset() in onAppear + dismiss via savedEvent statt persistentem Saved-State. Root cause: `Saved` war persistent im StateFlow, View triggerte `dismiss()` bei jedem Re-Entry sofort → "verbrannter Screen". Android intakt (NavBackStackEntry-Scoping). Manual UAT pending | 2026-05-18 | 81113d6 | [260518-eny-ios-ai-workout-generation-state-reset-na](./quick/260518-eny-ios-ai-workout-generation-state-reset-na/) |
| 260518-ey4 | AI Workout Stream-Status hardcoded "5 Übungen" → User-exerciseCount — Single-line Fix in WorkoutAiViewModel.kt:113 (skeletonRowCount aus originatingData.exerciseCount statt const 5). Beide Plattformen konsumieren state.skeletonRowCount → automatisch korrigiert. Manual UAT pending | 2026-05-18 | 59e5a67 | [260518-ey4-ai-generation-stream-status-zeigt-hardco](./quick/260518-ey4-ai-generation-stream-status-zeigt-hardco/) |

## Session Continuity

Last session: 2026-05-17T22:00:00.000Z
Stopped at: Phase 19 closed — UAT user-confirmed, diagnostic prints removed, all demo-prep quick-tasks shipped
Next step: Optional — `android-ios-parity` → `main` merge; plan post-v1.0 work (e.g. Phase 20 or demo polish for university deadline)
