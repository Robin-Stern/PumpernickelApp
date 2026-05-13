---
phase: 260510-wgs
plan: 01
subsystem: ui
tags: [ai, ios, swift, provider-suggestions, editorial, ui-copy, eval-alignment]

# Dependency graph
requires:
  - phase: 260510-w9h
    provides: "Kotlin PROVIDER_DEFAULTS swap to google/gemma-4-31B-it for Together + error-message recommendations; deferred-items.md flagged iOS AISettingsView.modelSuggestions as the matching follow-up site"
provides:
  - "iOS AISettingsView.modelSuggestions ordering + notes aligned with Kotlin defaults + eval evidence (Recipe failure mode of gpt-oss-20b family)"
  - "Cross-platform consistency: iOS Together quick-pick first entry == Kotlin PROVIDER_DEFAULTS[\"together\"]"
affects: [ai-settings, ios, model-quick-pick, future ai-onboarding work in Phase 18]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Editorial copy in iOS quick-pick mirrors Kotlin error-message phrasing (\"Reasoning · Recipe unzuverlässig\") so the two surfaces tell the same story when a user hits a Recipe failure"

key-files:
  created: []
  modified:
    - "iosApp/iosApp/Views/AI/AISettingsView.swift"

key-decisions:
  - "D-01..D-05 baked into plan, executed verbatim: reorder Together so gemma-4-31B-it is first (default suggestion); rewrite 6 notes (3 Together, 1 OpenRouter, 1 Groq, plus Together gpt-oss-20b honest note)"
  - "D-03: KEEP deprecated Gemma 2/3 entries but downgrade notes to \"Gemma 4 bevorzugen\" rather than remove them — minimal-surprise change preserves the menu while killing the implicit recommendation"
  - "D-06: openai provider list and 6 other entries left untouched — no eval evidence forces a change, keep diff surface tight"

patterns-established:
  - "iOS quick-pick first entry == Kotlin PROVIDER_DEFAULTS[provider] — invariant any future provider/default change must preserve"
  - "Honest-by-default copy for reasoning models: cite the specific failure mode (\"Recipe unzuverlässig\") rather than vague qualifiers"

requirements-completed:
  - "iOS quick-pick model suggestions consistent with Kotlin defaults (260510-w9h follow-up)"
  - "Editorial notes honest with eval evidence (gpt-oss-20b Recipe = 0/4 reasoning-trace starvation)"
  - "gemma-4-31B-it surfaced as Together default suggestion (matches Kotlin PROVIDER_DEFAULTS)"

# Metrics
duration: 2min
completed: 2026-05-10
---

# Phase 260510-wgs Plan 01: iOS AISettingsView model-suggestion alignment Summary

**Reordered iOS Together quick-pick to lead with google/gemma-4-31B-it and rewrote 6 model notes so iOS suggestions stop contradicting Kotlin defaults and the eval evidence from 260510-w9h.**

## Performance

- **Duration:** 2min 3s
- **Started:** 2026-05-10T21:25:24Z
- **Completed:** 2026-05-10T21:27:27Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Together provider quick-pick now leads with `google/gemma-4-31B-it` (note: "Empfohlen · 12/12 in Eval") — matches `PROVIDER_DEFAULTS["together"]` shipped by 260510-w9h
- All four false "empfohlen" / "Stark, mittlere Latenz" claims removed; replaced with honest reasoning-model framing ("Reasoning · Recipe unzuverlässig")
- Deprecated Gemma 2/3 entries kept (still callable on Together) but notes downgraded to "Gemma 4 bevorzugen"
- Resolves the deferred item logged in `.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/deferred-items.md` for the iOS surface

## Task Commits

Each task was committed atomically:

1. **Task 1: Reorder Together list and update model-suggestion notes (Together / OpenRouter / Groq)** — `7d7962a` (chore)

_No plan-metadata commit recorded in this summary — orchestrator owns the docs commit._

## Files Created/Modified
- `iosApp/iosApp/Views/AI/AISettingsView.swift` — `modelSuggestions` dict only: reordered Together array (gemma-4-31B-it -> index 0) and rewrote 6 notes across Together/OpenRouter/Groq. Surrounding struct, view, and `openai` provider list untouched.

## 7 String Sites Changed (1 reorder + 6 note edits, mapped to D-01..D-05)

| # | Site                                      | Before                              | After                                              | Decision |
|---|-------------------------------------------|-------------------------------------|----------------------------------------------------|----------|
| 1 | Together array order                      | gpt-oss-20b at index 0              | gemma-4-31B-it at index 0; gpt-oss-20b at index 1  | D-01     |
| 2 | Together `google/gemma-4-31B-it` note     | `Google Gemma 4 · neu`              | `Empfohlen · 12/12 in Eval`                        | D-01     |
| 3 | Together `openai/gpt-oss-20b` note        | `Kostenlos · empfohlen`             | `Kostenlos · Reasoning · Recipe unzuverlässig`     | D-02     |
| 4 | Together `google/gemma-3-27b-it` note     | `Google Gemma 3`                    | `Vorgänger · Gemma 4 bevorzugen`                   | D-03     |
| 5 | Together `google/gemma-2-27b-it` note     | `Google Gemma 2`                    | `Veraltet · Gemma 4 bevorzugen`                    | D-03     |
| 6 | OpenRouter `openai/gpt-oss-20b:free` note | `Kostenlos · empfohlen`             | `Kostenlos · Reasoning · Recipe unzuverlässig`     | D-04     |
| 7 | Groq `openai/gpt-oss-20b` note            | `Stark, mittlere Latenz`            | `Reasoning · Recipe unzuverlässig`                 | D-05     |

## Out-of-Scope Entries Left Untouched (D-06)

| Provider   | Entry                                          | Reason                                                                 |
|------------|------------------------------------------------|------------------------------------------------------------------------|
| openai     | gpt-4o-mini / gpt-4o / o4-mini (all 3)         | User did not request, not in scope                                     |
| together   | meta-llama/Llama-3.3-70B-Instruct-Turbo        | "stark allround" not eval-contradicted at entry level (editorial)      |
| together   | deepseek-ai/DeepSeek-V3                        | No eval action                                                         |
| openrouter | meta-llama/llama-3.3-70b-instruct              | No eval action                                                         |
| openrouter | deepseek/deepseek-chat                         | No eval action                                                         |
| groq       | llama-3.3-70b-versatile / llama-3.1-8b-instant | No eval evidence forces change; copy is honest                         |

## Cross-Platform Consistency Claim

- iOS `AISettingsView.modelSuggestions["together"]` index 0 (file line 347) == `google/gemma-4-31B-it`
- Kotlin `AiSettingsViewModel.PROVIDER_DEFAULTS["together"]` == `google/gemma-4-31B-it` (set by 260510-w9h, commit `b643711`)
- Honest copy on iOS (`Reasoning · Recipe unzuverlässig`) mirrors the failure-mode framing of Kotlin's "Nicht-Reasoning-Modell wie ..." error-message hint, so a user who lands on gpt-oss-20b and hits Recipe failure sees the same explanation across the two settings surfaces.

## Decisions Made
None - followed the editorial decisions D-01 through D-07 baked into the plan exactly. No new editorial judgement applied at execution time.

## Deviations from Plan

None - plan executed exactly as written. The grep-based verify command produced `VERIFY OK` on first run after the edit (against the worktree's file).

## Issues Encountered

- **Path resolution mishap during execution (not a deviation, fully self-corrected before commit):** The first Edit call was issued with an absolute path that resolved into the main repository checkout (`/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/iosApp/...`) instead of the worktree (`/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/.claude/worktrees/agent-a5c8e4c01b2337084/iosApp/...`). The mismatch was caught immediately when the plan's grep verification returned non-zero exit. Recovery: ran `git checkout -- iosApp/iosApp/Views/AI/AISettingsView.swift` in the main repo to discard the unintended working-tree change (single file by name, non-destructive), then re-applied the same Edit against the worktree's absolute path. Final state: main repo untouched, worktree carries the only commit. Diff scope verified clean before commit (`git diff --stat` showed only the 1 intended file).

## User Setup Required

None - no external service configuration required. Pure UI copy change.

## Self-Check

**Files claimed modified — existence + content check:**
- `iosApp/iosApp/Views/AI/AISettingsView.swift` (worktree): FOUND, contains "Empfohlen · 12/12 in Eval" at line 347 (verified via grep + Read).

**Commit hash claimed — existence check:**
- `7d7962a`: FOUND (`git log --oneline` shows `7d7962a chore(260510-wgs): align iOS AI model-suggestion notes with eval evidence`).

**Main repo state check:**
- `git status` in `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp` clean for `iosApp/iosApp/Views/AI/AISettingsView.swift` after revert — no stray modifications leaked out of the worktree.

## Self-Check: PASSED

## Next Phase Readiness
- iOS settings surface is now consistent with the eval-driven Kotlin defaults shipped in 260510-w9h. Phase 18 (AI features) onboarding flows can rely on the quick-pick recommending a model that actually passes Recipe.
- No follow-up work outstanding for this surface. Other AI surfaces in `Views/AI/` were not in scope; if Phase 18 introduces new model-pickers, the "first entry == Kotlin PROVIDER_DEFAULTS" invariant should carry forward.

---
*Phase: 260510-wgs*
*Completed: 2026-05-10*
