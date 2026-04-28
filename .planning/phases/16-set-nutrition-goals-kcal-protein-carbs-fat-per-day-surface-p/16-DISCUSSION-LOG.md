# Phase 16: Set nutrition goals — Overview progress + bonus XP - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
**Areas discussed:** Goal-editor UI, Tolerance policy, Sugar treatment, Bonus XP shape

---

## Goal-editor UI

### Q1 — Editor entry point

| Option | Description | Selected |
|--------|-------------|----------|
| Settings sheet row | Add 'Nährwertziele' row to existing Settings sheet. Mirrors Phase 15 D-21 (Achievement Gallery). | |
| Tappable rings on Overview | Tap a ring to open editor sheet. Discoverable but mixes display with config. | |
| Button on Overview rings card | Dedicated edit button on the existing nutrition rings card. Visible without tapping rings. | ✓ |

**User's choice:** Button on Overview rings card.
**Notes:** Keeps rings non-interactive, edit affordance is explicit.

### Q2 — Numeric input style

| Option | Description | Selected |
|--------|-------------|----------|
| Drum pickers | Match template editor + workout session vocabulary. Familiar. | ✓ |
| Plain numeric text fields | Standard numeric keypad. Faster typing for kcal but inconsistent with rest of app. | |
| Sliders with text fallback | Adds complexity; no slider component used elsewhere yet. | |

**User's choice:** Drum pickers.

### Q3 — Presets vs. manual

| Option | Description | Selected |
|--------|-------------|----------|
| Manual entry only | 5 drum pickers + Save. Simplest. | |
| Manual + 3 presets | Cut/Maintain/Bulk preset chips pre-fill pickers. | |
| Manual + body-weight calculator | Ask weight/height/activity → compute kcal + macros. Adds significant scope. | |

**User's choice (free text):** *"would be cool if the user could enter his weight, height and activity like in those online 'maintenance calorie' calculators, get a suggestion for cut maintain and bulk yk"*
**Notes:** Calculator was the implicit choice. Confirmed by reflecting back the TDEE-style flow (BMR → activity multiplier → 3 cards → drum pickers below).

### Q4 — BMR formula

| Option | Description | Selected |
|--------|-------------|----------|
| Mifflin–St Jeor | Modern standard (1990). Inputs: weight, height, age, sex. | ✓ |
| Harris–Benedict (revised) | Older formula, less accurate for modern populations. | |
| Katch–McArdle | Uses lean body mass; requires body-fat % (extra input). | |

**User's choice:** Mifflin–St Jeor.

### Q5 — Activity tiers

| Option | Description | Selected |
|--------|-------------|----------|
| Standard 5 tiers | Sedentary 1.2 / Light 1.375 / Moderate 1.55 / Very active 1.725 / Extra 1.9. | ✓ |
| Simplified 3 tiers | Low 1.4 / Medium 1.6 / High 1.8. | |
| User picks multiplier directly | Maximum flexibility, less guidance. | |

**User's choice:** Standard 5 tiers.

### Q6 — Macro derivation

| Option | Description | Selected |
|--------|-------------|----------|
| Protein/kg + fat % + carb remainder | Cut 2.2 / Maintain 2.0 / Bulk 1.8 g/kg. Fat 25%. Carbs = remainder. Sugar 50 g default. | ✓ |
| Fixed 40/30/30 | Each macro = % of kcal / kcal-per-gram. Ignores body weight. | |
| Same split across all 3 suggestions | Only kcal differs between cut/maintain/bulk. Easier UI but cut/bulk macros aren't optimal. | |

**User's choice:** Protein/kg + fat % + carb remainder.

### Q7 — Calculator layout

| Option | Description | Selected |
|--------|-------------|----------|
| Single screen, scroll | Stats inputs at top, 3 live-updating cards, drum pickers at bottom. | ✓ |
| Two-step: stats sheet → suggestion sheet | More guided, three sheets to ship. | |
| Skip calculator entry by default | Editor opens with drum pickers; calculator behind a button. | |

**User's choice:** Single screen, scroll.

### Q8 — Cut/Bulk deltas

| Option | Description | Selected |
|--------|-------------|----------|
| Cut −500 / Bulk +300 | Standard 0.5 kg/week loss / mild bulk. | ✓ |
| Cut −500 / Bulk +500 | Symmetric ±500. | |
| Cut −300 / Bulk +300 | Mild deltas, slower changes. | |

**User's choice:** Cut −500 / Bulk +300.

### Q9 — Personal stats persistence

| Option | Description | Selected |
|--------|-------------|----------|
| DataStore separate keys | New keys in SettingsRepository alongside nutritionGoals. No Room migration. | ✓ |
| New Room entity 'UserProfile' | Schema bump v7 → v8. Future-proof for body-weight history. | |
| Embed in NutritionGoals | Couples 'goals' with 'personal stats' (semantically distinct). | |

**User's choice:** DataStore separate keys.

### Q10 — First-launch surfacing

| Option | Description | Selected |
|--------|-------------|----------|
| Defaults only, no nudge | Lowest friction. Matches current behaviour. | |
| Subtle banner on Overview | Dismissable banner above rings, links to calculator. | ✓ |
| Onboarding sheet on first launch | Auto-present calculator. Most aggressive. | |

**User's choice:** Subtle banner on Overview.

---

## Tolerance policy

### Q1 — Keep ±10% or change?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep ±10% as-is | Phase 15 already locked it; engine + retroactive walker assume ±10%. | ✓ |
| Tighten globally to ±5% | Stricter, requires NutritionGoalDayPolicy change. | |
| Per-macro tolerance | Different ±X% per macro. Bigger code change. | |

**User's choice:** Keep ±10% as-is.
**Notes:** Roadmap's "±5–10%" is satisfied since 10% is the upper bound. Per-macro and ±5% deferred.

---

## Sugar treatment

### Q1 — Sugar in goals + goal-day check?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep all 5 macros | Editor + calculator handle all 5. Rings + check unchanged. | ✓ |
| Keep sugar but make optional (set 0 to skip) | Add 'Off / Track sugar' toggle. NutritionGoalDayPolicy already treats 0 as skip. | |
| Drop sugar entirely | Remove sugarGoal from model + ring + check. Schema/DataStore migration needed. | |

**User's choice:** Keep all 5 macros.
**Notes:** Roadmap title 'kcal/protein/carbs/fat' treated as abbreviation. No structural change.

---

## Bonus XP shape

### Q1 — How should bonus XP work?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep current 25 XP/day + 100 XP @ 7-day streak | What Phase 15 already implements. | ✓ |
| Raise the flat per-day amount | E.g. 50 or 75 XP per goal-day. One constant change. | |
| Add 'perfect day' bonus when all 5 macros are within ±5% | Stretch goal stacked on top. Needs second predicate. | |

**User's choice:** Keep current.
**Notes:** "Bonus XP" in roadmap referred to existing per-day + streak. No new constants.

---

## Claude's Discretion

- Drum-picker per-macro min/max ranges.
- Default placeholder values for first-time calculator users when no UserPhysicalStats is stored.
- Suggestion-card visual styling for "selected" state (border vs. fill).
- Banner copy + dismissal animation + visual treatment.
- Whether macro splits round to whole grams or 5 g increments before pre-filling drum pickers.
- Sex selector UI idiom (segmented control vs. toggle).

## Deferred Ideas

- Per-macro tolerance (rejected for this phase, may revisit after play-testing).
- Tighten tolerance globally to ±5% (rejected).
- "Perfect day" stretch bonus (extra XP if all 5 macros within ±5%) (rejected for now).
- Raise per-day goal-day XP from 25 (rejected).
- Drop sugar from NutritionGoals model (rejected — kept all 5).
- Onboarding sheet auto-presented on first launch (rejected — banner instead).
- Goal-editor entry from Settings sheet (rejected — Overview chosen).
- lbs/inches conversion in TDEE calculator (deferred to future phase).
- Body-weight history table (Room entity + charts) — deferred (charts are project-wide deferral).
- Body-fat-aware BMR (Katch–McArdle) (rejected — no body-fat tracking yet).
- Custom activity multiplier entry (rejected — standard 5 tiers cover most users).

### Reviewed Todos (not folded)

None — `gsd-sdk query todo.match-phase 16` returned 0 matches.
