# Phase 16: Set nutrition goals — Overview progress + bonus XP - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship the user-facing **goal-editor flow** for nutrition goals (kcal/protein/fat/carbs/sugar) — including a Mifflin–St Jeor TDEE calculator that suggests Cut / Maintain / Bulk targets — plus a discoverability **banner** on the Overview tab. Personal stats (weight/height/age/sex/activity) are persisted alongside `nutritionGoals` in DataStore so the calculator remembers them.

Engine, schema, Overview rings, and goal-day XP awarding are **unchanged from Phase 15**. This phase fills the missing UI gap: there is currently no way to edit `NutritionGoals` from either platform (the model + setter exist; the editor screen does not).

</domain>

<decisions>
## Implementation Decisions

### Goal-editor placement & shape

- **D-16-01 (Entry point):** Edit button on the existing nutrition rings card on the Overview tab opens the editor. Rings themselves stay non-interactive (avoids accidental edits during normal browsing).
- **D-16-02 (Single-screen layout, scrollable):** Editor is **one screen with three stacked sections**, not a multi-step wizard:
  1. Personal stats inputs (weight, height, age, sex, activity tier)
  2. Three suggestion cards (Cut / Maintain / Bulk) that **update live** as stats inputs change. Tapping a card pre-fills the picker section below.
  3. Five drum pickers (kcal, protein, carbs, fat, sugar) — pre-filled from selected card or current goals — plus a single Save button.
- **D-16-03 (Drum pickers for numeric input):** Reuse existing drum-picker vocabulary from the template editor + workout session (`androidApp/.../components/DrumPicker.kt` on Android; SwiftUI `Picker(.wheel)` on iOS). Match the existing kg/reps picker styling. Per-macro picker ranges are Claude's discretion (sane bounds — e.g. kcal 800–6000, protein 20–400 g, fat 10–250 g, carbs 20–700 g, sugar 0–200 g).

### TDEE calculator

- **D-16-04 (BMR formula — Mifflin–St Jeor):**
  - Men: `BMR = 10 × weightKg + 6.25 × heightCm − 5 × age + 5`
  - Women: `BMR = 10 × weightKg + 6.25 × heightCm − 5 × age − 161`
- **D-16-05 (Activity tiers — standard 5):**
  | Tier | Multiplier | German label |
  |---|---|---|
  | Sedentary | 1.2 | Bürojob / kaum Bewegung |
  | Lightly active | 1.375 | Leicht aktiv (1–3×/Woche) |
  | Moderately active | 1.55 | Mäßig aktiv (3–5×/Woche) |
  | Very active | 1.725 | Sehr aktiv (6–7×/Woche) |
  | Extra active | 1.9 | Extrem aktiv / körperlicher Beruf |
  TDEE = BMR × multiplier.
- **D-16-06 (Suggestion deltas):** Cut = TDEE − 500 kcal · Maintain = TDEE · Bulk = TDEE + 300 kcal. (Conservative bulk, standard cut. Consistent with `0.5 kg/week` heuristic.)
- **D-16-07 (Macro derivation per suggestion):**
  | Macro | Cut | Maintain | Bulk | Formula |
  |---|---|---|---|---|
  | Protein (g) | 2.2 × kg | 2.0 × kg | 1.8 × kg | per body weight |
  | Fat (g) | 25% of kcal / 9 | 25% of kcal / 9 | 25% of kcal / 9 | fat % of kcal |
  | Carbs (g) | (kcal − protein·4 − fat·9) / 4 | same | same | remainder |
  | Sugar (g) | 50 (default) | 50 (default) | 50 (default) | not derived; user sets manually |
- **D-16-08 (Calculator state — live preview):** As the user changes any stat input, the three suggestion cards re-compute in-place. No "Compute" button. Selecting a card highlights it and pushes its values down into the drum pickers; user can still tweak any picker before Save.
- **D-16-09 (No calculator? Skip allowed):** Stats section is collapsible / dismissable. A user who already knows their numbers can scroll past the calculator and just edit the drum pickers directly. The current `NutritionGoals` (defaults or last-saved) populate the pickers on open.

### Personal stats persistence

- **D-16-10 (DataStore separate keys):** Add new keys to `SettingsRepository` alongside the existing `nutritionGoals` keys:
  - `user_weight_kg` (Double)
  - `user_height_cm` (Int)
  - `user_age` (Int)
  - `user_sex` (Enum: MALE / FEMALE — Kotlin enum stored as String)
  - `user_activity_level` (Enum: SEDENTARY / LIGHTLY_ACTIVE / MODERATELY_ACTIVE / VERY_ACTIVE / EXTRA_ACTIVE — stored as String)
  Mirrors the existing pattern (`weightUnit`, accent colour, theme are already separate DataStore keys). **No Room schema migration.**
- **D-16-11 (Domain model — new `UserPhysicalStats` data class):** Lives in `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt`. Exposed by `SettingsRepository` as `Flow<UserPhysicalStats?>` (null when no stats ever entered → calculator opens with sensible placeholders, e.g. 80 kg / 180 cm / 30 / male / moderately active).
- **D-16-12 (Weight unit — kg only in editor for v1):** The calculator and stats input use **kg + cm** only. The existing app-wide `weightUnit` (kg/lbs) stays scoped to workout weights. Adding lbs/inches conversion to the calculator is deferred — keep scope tight.

### First-launch discoverability

- **D-16-13 (Subtle banner on Overview):** On first app launch (or until the user has set goals **or** dismissed the banner), show a small banner above the rings card on the Overview tab: "Persönliche Ziele berechnen →". Tapping opens the editor. Includes a small "×" dismiss control.
- **D-16-14 (Banner dismissal — persistent):** Two dismissal triggers (either ends banner forever): user dismisses via "×", **or** user successfully Saves new goals (defaults are unchanged → no save → banner still shows; any Save with non-default values → hide banner). Persisted as `nutrition_goals_banner_dismissed: Boolean` in DataStore. Avoids nag.

### Tolerance, sugar, and XP — carried forward from Phase 15 (unchanged)

- **D-16-15 (Tolerance kept at ±10% strict):** Phase 15 D-04 holds. `NutritionGoalDayPolicy.TOLERANCE = 0.10` is **not changed** by this phase. The roadmap's "±5–10%" hint is satisfied since 10% is the upper bound. Per-macro tolerance and ±5% tightening are explicitly rejected for this phase (deferred ideas).
- **D-16-16 (Sugar stays — all 5 macros):** `NutritionGoals` keeps `sugarGoal`. Overview rings keep the sugar ring. Goal-day check keeps sugar in the predicate. The roadmap title's "kcal/protein/carbs/fat" is treated as an abbreviation, not a deletion. **No schema/model change.**
- **D-16-17 (Bonus XP unchanged):** `XpFormula.NUTRITION_GOAL_DAY_XP = 25` per goal-day and `STREAK_NUTRITION_7D = 100` at 7-day streak (both Phase 15 D-06 / D-04) **stay as-is**. The roadmap's "bonus XP" refers to the existing per-day + streak rewards. No new constants, no perfect-day stretch goal.

### Claude's Discretion

- Drum-picker per-macro min/max ranges (sensible bounds, e.g. kcal 800–6000).
- Default placeholder values for first-time calculator users when no `UserPhysicalStats` is stored (e.g. 80 kg / 180 cm / 30 / male / moderately active).
- Suggestion-card visual styling (which card highlights as "selected" — accent border, filled background, etc.) — match Material 3 on Android and iOS card conventions.
- Banner copy, exact dismissal animation, and visual treatment.
- Whether the cut/maintain/bulk macro splits round to whole grams or 5 g increments before pushing to drum pickers.
- Sex selector UI (segmented control vs. toggle) — match platform idioms.

</decisions>

<specifics>
## Specific Ideas

- "Like those online maintenance-calorie calculators" — TDEE-style UX is the explicit reference. Standard Mifflin–St Jeor + 5 activity tiers + Cut/Maintain/Bulk cards.
- The user wants the calculator to **suggest** numbers that the user can then **tweak** via drum pickers — not replace manual entry. The flow is informative, not prescriptive.
- Banner is intentionally low-friction — "subtle banner" was the explicit choice over an auto-presented onboarding sheet.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Carried-forward decisions (engine + tolerance + sugar + XP)
- `.planning/phases/15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r/15-CONTEXT.md` — Phase 15 locks D-04 (±10% strict per macro, 0 = skip), D-05 (idempotent once-per-day), D-06 (streak thresholds incl. nutrition 7-day +100), D-22 (eval on Overview tab appearance). All of these flow through Phase 16 unchanged.
- `.planning/ROADMAP.md` §"Phase 16" — Phase entry text + dependency on Phase 15.
- `.planning/PROJECT.md` §"Current State" — Lists shipped post-v1.5 nutrition goals + DataStore persistence pattern. Confirms "scope: Workout feature only" caveat is stale (nutrition + gamification have shipped; Phase 16 is incremental UI on top).

### Goal-day engine (read-only — Phase 16 does NOT modify)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt` — Pure ±10% predicate. **Do not change `TOLERANCE` constant.**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GoalDayTrigger.kt` — Fires on Overview tab appearance, evaluates yesterday + today, idempotent.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt` — `evaluateGoalDay(date)` writes XP ledger row (source = NUTRITION_GOAL_DAY, dedupe key = ISO date). **Do not change.**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt` — `NUTRITION_GOAL_DAY_XP = 25`, `STREAK_NUTRITION_7D = 100`. **Constants stay.**

### Goals model + persistence (Phase 16 EXTENDS)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt` — Existing 5-field data class (calorieGoal/proteinGoal/fatGoal/carbGoal/sugarGoal: Int, defaults 2500/150/80/300/50). Treated as input to the editor. **No structural change** (sugar stays).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — DataStore wrapper; already exposes `nutritionGoals: Flow<NutritionGoals>` + `setNutritionGoals(goals)`. Phase 16 adds parallel keys + `userPhysicalStats: Flow<UserPhysicalStats?>` + `setUserPhysicalStats(stats)` here. Pattern: same shape as existing nutrition-goals accessor. (Same file also stores `weightUnit` — D-16-12 confirms we do not couple to it.)

### Overview surface (Phase 16 EXTENDS — adds banner + edit button)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` — Already exposes `uiState.nutritionGoals` and `updateNutritionGoals(goals)`. Phase 16 will also expose `userPhysicalStats: StateFlow<UserPhysicalStats?>` + `updateUserPhysicalStats(stats)` and a banner-dismissed StateFlow (or expose dismissal as a method).
- `iosApp/iosApp/Views/Overview/OverviewView.swift` — iOS Overview screen. Add banner above `nutritionRingsSection` and an edit button on / above the rings section.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` — Android equivalent. Same banner + edit button wiring.

### Drum-picker pattern (reuse, do NOT re-invent)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DrumPicker.kt` — Existing reusable drum picker (LazyColumn + SnapFlingBehavior per Phase 13 decision). Used by template editor + workout session. **D-16-03 reuses this verbatim.**
- iOS: SwiftUI native `Picker(.wheel)` (used in workout session — iOS uses native scroll wheels, Android uses the custom drum). Same pattern in editor.

### Settings location (entry point NOT used here — D-16-01 chose Overview)
- `iosApp/iosApp/Views/Settings/SettingsView.swift` / `androidApp/.../SettingsSheet.kt` — Settings is **NOT** the entry point for the goal editor (per D-16-01). Listed here only so planner does not mistakenly add the entry there.

### Unrelated but adjacent (for awareness during planning)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/MacroRow.kt` — Existing macro row component used in nutrition daily log; unrelated to the goal editor but worth knowing it exists.
- `.planning/phases/14-history-settings-anatomy/14-CONTEXT.md` — Confirms Settings sheet location + `weightUnit` DataStore precedent.

### Reference (no direct port)
- `/Users/olli/schenanigans/gymtracker` — Firmware reference. No goal-editor logic to port; nutrition goals are mobile-only.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`DrumPicker` (Android) / SwiftUI `Picker(.wheel)` (iOS)** — drop-in for the 5 macro pickers (D-16-03). Same component as template editor + workout session.
- **`SettingsRepository.nutritionGoals` Flow + setter** — already wired through the DataStore pattern; the new `UserPhysicalStats` accessor mirrors it 1:1.
- **`OverviewViewModel.updateNutritionGoals`** — already exists; the editor's Save just calls this. No new ViewModel writer needed for the goals path.
- **Overview rings card + macro rings** — already render `state.nutritionGoals`. Adding an edit button or banner on the same screen does not touch the rings themselves (avoids ring re-layout work).
- **DataStore Boolean dismissal flag pattern** — same shape as the gamification "retroactive_applied" sentinel from Phase 15 D-13. Reuse the pattern for banner dismissal (D-16-14).

### Established Patterns
- **Single sealed UI state per ViewModel surface** — `OverviewUiState` already holds `nutritionGoals`. Two options for the new state: (a) extend `OverviewUiState` with `userPhysicalStats` + `bannerDismissed`, or (b) expose them as separate StateFlows on `OverviewViewModel` (matches how `rankState` is exposed). Planner picks; Claude leans toward (b) for symmetry with `rankState`.
- **DataStore `Flow.map { defaults }` pattern** — `nutritionGoals` already uses `Flow.map` to surface defaults when keys are missing; `userPhysicalStats: Flow<UserPhysicalStats?>` returns null when keys are missing (no defaults — D-16-11). The calculator handles null with placeholder pre-fills.
- **KMP shared VM + platform UI** — iOS SwiftUI binds via `@NativeCoroutines` / `asyncSequence`; Android uses `collectAsState()`. The new editor screen needs platform-specific UI implementations driven by the same `OverviewViewModel` (or a new `NutritionGoalsEditorViewModel` if the editor flow is heavy enough to warrant its own — Claude's discretion).
- **Banner dismissal once-only flag** — DataStore Boolean, default false. Saving non-default goals also flips it (D-16-14). No expiry, no re-show logic.

### Integration Points
- `OverviewViewModel` → add `userPhysicalStats: StateFlow<UserPhysicalStats?>` + `nutritionGoalsBannerVisible: StateFlow<Boolean>` (or equivalent) + `updateUserPhysicalStats(stats: UserPhysicalStats)` + `dismissBanner()` methods.
- `SettingsRepository` → add `userPhysicalStats: Flow<UserPhysicalStats?>` + `setUserPhysicalStats(stats: UserPhysicalStats)` + `nutritionGoalsBannerDismissed: Flow<Boolean>` + `setBannerDismissed(value: Boolean)`.
- New file: `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` — data class + activity-level/sex enums.
- New file (optional): `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` — pure functions for BMR, TDEE, suggestions, macro split. Trivially unit-testable; planner should add tests.
- iOS new view: `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift` (or similar). Reachable from the edit button on `OverviewView.swift`.
- Android new screen: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt`. Add a route in `Routes.kt` + nav entry from `OverviewScreen`.

</code_context>

<deferred>
## Deferred Ideas

- **Per-macro tolerance (e.g. kcal ±5%, protein ±10%, fat ±15%, sugar ±20%)** — discussed under Tolerance policy; rejected for this phase to avoid changing Phase 15's locked predicate. Revisit if play-testing shows ±10% uniform feels off for some macros.
- **Tighten tolerance globally to ±5%** — rejected for the same reason.
- **"Perfect day" stretch bonus (extra +25 XP if all 5 macros within ±5%)** — discussed under Bonus XP shape; rejected to keep XP semantics simple. Revisit when the engine has more sophisticated stretch goals.
- **Raise per-day goal-day XP from 25** — explicitly rejected; current values stay.
- **Drop sugar from `NutritionGoals` model** — rejected; roadmap title was abbreviation, not a model change.
- **Onboarding sheet auto-presented on first launch** — rejected in favour of a subtle dismissable banner. Could revisit if banner is ignored at scale (none of relevant since this is single-user/uni project).
- **Goal-editor entry from Settings sheet** — rejected (D-16-01); Overview is the chosen entry. Mentioned here so future readers don't add a duplicate Settings entry.
- **lbs/inches conversion in the TDEE calculator** — D-16-12 keeps it kg/cm only for v1. Revisit if the user adds lbs preference later.
- **Body-weight history table (Room entity, charts)** — would require schema bump v7 → v8 + a UI surface. Out of scope; D-16-10 stays in DataStore. Revisit when charts come online (currently a project-wide deferral — see PROJECT.md "Out of Scope").
- **Body-fat-aware BMR (Katch–McArdle)** — rejected in favour of Mifflin–St Jeor. Revisit if the app ever adds body-fat tracking.
- **Custom activity multiplier entry** — rejected; standard 5 tiers cover the vast majority of users.
- **Reviewed Todos (not folded):** None — `gsd-sdk query todo.match-phase 16` returned 0 matches.

</deferred>

---

*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Context gathered: 2026-04-28*
