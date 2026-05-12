---
phase: 260510-wgs
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - iosApp/iosApp/Views/AI/AISettingsView.swift
autonomous: true
requirements:
  - "iOS quick-pick model suggestions consistent with Kotlin defaults (260510-w9h follow-up)"
  - "Editorial notes honest with eval evidence (gpt-oss-20b Recipe = 0/4 reasoning-trace starvation)"
  - "gemma-4-31B-it surfaced as Together default suggestion (matches Kotlin PROVIDER_DEFAULTS)"
tags: [ai, ios, provider-suggestions, editorial, ui-copy]
user_setup: []

must_haves:
  truths:
    - "Together quick-pick list shows google/gemma-4-31B-it as the first (default suggestion) entry"
    - "openai/gpt-oss-20b on Together no longer carries 'empfohlen' framing"
    - "openai/gpt-oss-20b:free on OpenRouter no longer carries 'empfohlen' framing"
    - "openai/gpt-oss-20b on Groq carries a note that signals reasoning-model trade-off"
    - "deprecated Gemma 2/3 entries remain but are not framed as current recommendation"
  artifacts:
    - path: "iosApp/iosApp/Views/AI/AISettingsView.swift"
      provides: "modelSuggestions dict with eval-aligned entries + notes"
      contains: "google/gemma-4-31B-it"
  key_links:
    - from: "iosApp/iosApp/Views/AI/AISettingsView.swift modelSuggestions[\"together\"]"
      to: "shared AiSettingsViewModel PROVIDER_DEFAULTS together = google/gemma-4-31B-it"
      via: "first list entry == Kotlin default"
      pattern: "google/gemma-4-31B-it.*Empfohlen"
---

<objective>
Align the iOS `AISettingsView.swift` `modelSuggestions` dict (lines 340–365) with the eval-driven defaults that Kotlin already adopted in quick task 260510-w9h. This is a pure string + ordering edit in a single Swift file: reorder the Together list so `gemma-4-31B-it` is first (default suggestion), and rewrite three model notes (Together `gpt-oss-20b`, OpenRouter `gpt-oss-20b:free`, Groq `gpt-oss-20b`) so they stop claiming "empfohlen" / "Stark" without qualification — the eval data shows these are reasoning models that fail Recipe deterministically.

Purpose: Stop the iOS quick-pick from contradicting the Kotlin defaults + error messages. A user opening AI Settings on iOS should see the same recommendation hierarchy the Kotlin error-message hints already point them at.

Output: One modified Swift file, one atomic commit. No tests to run — pure UI copy + ordering. No KMP / Kotlin changes.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/260510-w9h-SUMMARY.md
@.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/deferred-items.md
@evals/MULTI-MODEL-RESULTS.md
@iosApp/iosApp/Views/AI/AISettingsView.swift

<interfaces>
<!-- Exact current state of modelSuggestions dict (file lines 340-364). -->
<!-- Executor: this is the ONLY block in the file to modify. -->

```swift
static let modelSuggestions: [String: [ModelSuggestion]] = [
    "openai": [
        ModelSuggestion(name: "gpt-4o-mini", note: "Günstig · Standard"),
        ModelSuggestion(name: "gpt-4o", note: "Stärker"),
        ModelSuggestion(name: "o4-mini", note: "Reasoning, langsamer")
    ],
    "together": [
        ModelSuggestion(name: "openai/gpt-oss-20b", note: "Kostenlos · empfohlen"),
        ModelSuggestion(name: "google/gemma-4-31B-it", note: "Google Gemma 4 · neu"),
        ModelSuggestion(name: "google/gemma-3-27b-it", note: "Google Gemma 3"),
        ModelSuggestion(name: "google/gemma-2-27b-it", note: "Google Gemma 2"),
        ModelSuggestion(name: "meta-llama/Llama-3.3-70B-Instruct-Turbo", note: "Llama · stark allround"),
        ModelSuggestion(name: "deepseek-ai/DeepSeek-V3", note: "DeepSeek · Code/Logik")
    ],
    "openrouter": [
        ModelSuggestion(name: "openai/gpt-oss-20b:free", note: "Kostenlos · empfohlen"),
        ModelSuggestion(name: "meta-llama/llama-3.3-70b-instruct", note: "Stark, allround"),
        ModelSuggestion(name: "deepseek/deepseek-chat", note: "Stark bei Code/Logik")
    ],
    "groq": [
        ModelSuggestion(name: "llama-3.3-70b-versatile", note: "Standard, sehr schnell"),
        ModelSuggestion(name: "llama-3.1-8b-instant", note: "Klein, sehr schnell"),
        ModelSuggestion(name: "openai/gpt-oss-20b", note: "Stark, mittlere Latenz")
    ]
]
```

<!-- The "openai" provider list is OUT OF SCOPE — do not modify it. -->
<!-- All edits live inside the "together", "openrouter", and "groq" arrays. -->
</interfaces>

<plan-decisions>
<!-- Editorial decisions baked into this plan, with rationale. Executor follows these as-is. -->

**D-01 (Together list — gemma-4-31B-it to position 1):**
Move `google/gemma-4-31B-it` to the FIRST position in the Together array. Change note from "Google Gemma 4 · neu" to "Empfohlen · 12/12 in Eval".
Rationale: First entry functions as the default suggestion in the UI; Kotlin's PROVIDER_DEFAULTS already uses this model as Together's default (260510-w9h). Eval evidence: only model with 12/12 stdev 0 across both Recipe + Workout suites.

**D-02 (Together list — gpt-oss-20b honest note):**
Keep `openai/gpt-oss-20b` in the list (don't remove — user may still want it for workout-only experiments) but move it AFTER the Gemma 4 entry and change note from "Kostenlos · empfohlen" to "Kostenlos · Reasoning · Recipe unzuverlässig".
Rationale: Eval shows Workout mean 3.00/4 stdev 0.82 (flickery but usable) and Recipe 0/4 (deterministic reasoning-trace token starvation). "Empfohlen" is empirically false. The new note tells the user (a) it's free, (b) it's a reasoning model, (c) it specifically breaks on Recipe — which matches the Kotlin error-message phrasing "Nicht-Reasoning-Modell wie ...".

**D-03 (Together list — deprecated Gemma 2/3 — KEEP, downgrade notes):**
Keep `google/gemma-3-27b-it` and `google/gemma-2-27b-it` entries. Change their notes from "Google Gemma 3" / "Google Gemma 2" to "Vorgänger · Gemma 4 bevorzugen" / "Veraltet · Gemma 4 bevorzugen".
Rationale: Briefing flags them as deprecated, but they're still callable on Together's API and a user with a custom endpoint or downgrade preference might want them. Removing them widens scope (changes the *set* of suggestions, not just notes/order); keeping them with clear "use Gemma 4 instead" notes preserves the menu while killing the implicit recommendation. Editorial: this is the minimal-surprise change.

**D-04 (OpenRouter — gpt-oss-20b:free honest note):**
Change OpenRouter `openai/gpt-oss-20b:free` note from "Kostenlos · empfohlen" to "Kostenlos · Reasoning · Recipe unzuverlässig".
Rationale: Same model family as Together's gpt-oss-20b — the reasoning-trace token starvation is a model-architecture failure mode, not provider-specific. We didn't run the eval against OpenRouter directly, but the failure mode transfers. "Empfohlen" was the false claim that prompted this task; the honest signal matches the Together copy verbatim for consistency.

**D-05 (Groq — gpt-oss-20b nuanced note):**
Change Groq `openai/gpt-oss-20b` note from "Stark, mittlere Latenz" to "Reasoning · Recipe unzuverlässig".
Rationale: We did not eval Groq directly. The model family is the same gpt-oss-20b reasoning architecture, so the Recipe failure mode is the honest expectation. "Stark, mittlere Latenz" implied general-purpose strength; the new note flags the reasoning trade-off without overclaiming based on un-evaluated provider behavior. We keep the entry (it's a legitimate Groq option for non-recipe workloads).

**D-06 (Out of scope — do NOT change):**
- `openai` provider list (3 entries): user did not request, not in scope.
- Groq `llama-3.3-70b-versatile` (note "Standard, sehr schnell") and `llama-3.1-8b-instant` (note "Klein, sehr schnell"): no eval evidence forces a change; copy is honest as-is.
- OpenRouter `meta-llama/llama-3.3-70b-instruct` and `deepseek/deepseek-chat`: same — no eval action.
- Together `meta-llama/Llama-3.3-70B-Instruct-Turbo` (note "Llama · stark allround") and `deepseek-ai/DeepSeek-V3` (note "DeepSeek · Code/Logik"): no eval action. (Llama-3.3 scored 4/4 Workout but 0.33/4 Recipe — current note "stark allround" is mildly optimistic but not eval-contradicted at the entry level since "allround" is editorial. Leave alone to keep the change surface tight.)

**D-07 (No API / behavior change):**
This task ONLY edits string literals and re-orders array entries inside one dict. No new fields on `ModelSuggestion`, no new providers, no UI restructuring. The single observable behavior change is: when a user taps the Together preset, the first quick-pick row shown is `google/gemma-4-31B-it`.
</plan-decisions>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Reorder Together list and update model-suggestion notes (Together / OpenRouter / Groq)</name>
  <files>iosApp/iosApp/Views/AI/AISettingsView.swift</files>
  <action>
Edit ONLY the `modelSuggestions` static dict (currently lines 340–364). Do not touch the surrounding `ModelSuggestion` struct (lines 334–337), the `ModelSuggestionRow` view (lines 367+), or the `openai` provider entry. Replace the three affected provider arrays so the final dict reads exactly:

```swift
static let modelSuggestions: [String: [ModelSuggestion]] = [
    "openai": [
        ModelSuggestion(name: "gpt-4o-mini", note: "Günstig · Standard"),
        ModelSuggestion(name: "gpt-4o", note: "Stärker"),
        ModelSuggestion(name: "o4-mini", note: "Reasoning, langsamer")
    ],
    "together": [
        ModelSuggestion(name: "google/gemma-4-31B-it", note: "Empfohlen · 12/12 in Eval"),
        ModelSuggestion(name: "openai/gpt-oss-20b", note: "Kostenlos · Reasoning · Recipe unzuverlässig"),
        ModelSuggestion(name: "google/gemma-3-27b-it", note: "Vorgänger · Gemma 4 bevorzugen"),
        ModelSuggestion(name: "google/gemma-2-27b-it", note: "Veraltet · Gemma 4 bevorzugen"),
        ModelSuggestion(name: "meta-llama/Llama-3.3-70B-Instruct-Turbo", note: "Llama · stark allround"),
        ModelSuggestion(name: "deepseek-ai/DeepSeek-V3", note: "DeepSeek · Code/Logik")
    ],
    "openrouter": [
        ModelSuggestion(name: "openai/gpt-oss-20b:free", note: "Kostenlos · Reasoning · Recipe unzuverlässig"),
        ModelSuggestion(name: "meta-llama/llama-3.3-70b-instruct", note: "Stark, allround"),
        ModelSuggestion(name: "deepseek/deepseek-chat", note: "Stark bei Code/Logik")
    ],
    "groq": [
        ModelSuggestion(name: "llama-3.3-70b-versatile", note: "Standard, sehr schnell"),
        ModelSuggestion(name: "llama-3.1-8b-instant", note: "Klein, sehr schnell"),
        ModelSuggestion(name: "openai/gpt-oss-20b", note: "Reasoning · Recipe unzuverlässig")
    ]
]
```

Exact change list (per D-01..D-05 in `<plan-decisions>`):

1. Together array: REORDER so `google/gemma-4-31B-it` is index 0 (was index 1). `openai/gpt-oss-20b` moves to index 1 (was index 0).
2. Together `google/gemma-4-31B-it` note: `"Google Gemma 4 · neu"` -> `"Empfohlen · 12/12 in Eval"` (per D-01).
3. Together `openai/gpt-oss-20b` note: `"Kostenlos · empfohlen"` -> `"Kostenlos · Reasoning · Recipe unzuverlässig"` (per D-02).
4. Together `google/gemma-3-27b-it` note: `"Google Gemma 3"` -> `"Vorgänger · Gemma 4 bevorzugen"` (per D-03).
5. Together `google/gemma-2-27b-it` note: `"Google Gemma 2"` -> `"Veraltet · Gemma 4 bevorzugen"` (per D-03).
6. OpenRouter `openai/gpt-oss-20b:free` note: `"Kostenlos · empfohlen"` -> `"Kostenlos · Reasoning · Recipe unzuverlässig"` (per D-04).
7. Groq `openai/gpt-oss-20b` note: `"Stark, mittlere Latenz"` -> `"Reasoning · Recipe unzuverlässig"` (per D-05).

Constraints:
- Do NOT change: `openai` provider array (3 entries), Groq's `llama-3.3-70b-versatile` and `llama-3.1-8b-instant`, OpenRouter's `meta-llama/llama-3.3-70b-instruct` and `deepseek/deepseek-chat`, Together's `meta-llama/Llama-3.3-70B-Instruct-Turbo` and `deepseek-ai/DeepSeek-V3` (per D-06).
- Do NOT add or remove model entries — order changes only.
- Preserve `·` (U+00B7 middle dot) and umlauts (`ä`, `ö`, `ü`, `ß`) byte-for-byte; the file is UTF-8.
- Preserve indentation (4 spaces) and trailing-comma style of the surrounding dict.
- Do NOT modify the `// MARK: - Model suggestions per provider (verified May 2026)` comment on line 332 or the `private struct ModelSuggestion` definition on lines 334–337.
  </action>
  <verify>
    <automated>cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp && \
      grep -c 'google/gemma-4-31B-it.*Empfohlen · 12/12 in Eval' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      grep -c 'openai/gpt-oss-20b".*"Kostenlos · Reasoning · Recipe unzuverlässig"' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      grep -c 'openai/gpt-oss-20b:free".*"Kostenlos · Reasoning · Recipe unzuverlässig"' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      grep -c 'openai/gpt-oss-20b".*"Reasoning · Recipe unzuverlässig"' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      grep -c 'gemma-3-27b-it".*"Vorgänger · Gemma 4 bevorzugen"' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      grep -c 'gemma-2-27b-it".*"Veraltet · Gemma 4 bevorzugen"' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -qx '1' && \
      ! grep -q 'Kostenlos · empfohlen' iosApp/iosApp/Views/AI/AISettingsView.swift && \
      ! grep -q 'Stark, mittlere Latenz' iosApp/iosApp/Views/AI/AISettingsView.swift && \
      ! grep -q '"Google Gemma 4 · neu"' iosApp/iosApp/Views/AI/AISettingsView.swift && \
      ! grep -q '"Google Gemma 3"' iosApp/iosApp/Views/AI/AISettingsView.swift && \
      ! grep -q '"Google Gemma 2"' iosApp/iosApp/Views/AI/AISettingsView.swift && \
      awk '/"together": \[/,/\]/' iosApp/iosApp/Views/AI/AISettingsView.swift | grep -n 'ModelSuggestion(name:' | head -1 | grep -q 'google/gemma-4-31B-it' && \
      echo "VERIFY OK"</automated>
  </verify>
  <done>
    - File compiles (no syntax error introduced — visual inspection of brackets/commas).
    - Together array's first ModelSuggestion is `google/gemma-4-31B-it` with note `"Empfohlen · 12/12 in Eval"`.
    - All four "empfohlen"/"Stark, mittlere Latenz" claims that the eval contradicted are gone.
    - Deprecated Gemma 2/3 entries remain but their notes signal "Gemma 4 bevorzugen".
    - No other lines in the file changed (git diff scoped to lines ~340–364).
  </done>
</task>

</tasks>

<verification>
- `git diff --stat` shows exactly 1 file changed: `iosApp/iosApp/Views/AI/AISettingsView.swift`.
- `git diff iosApp/iosApp/Views/AI/AISettingsView.swift` is confined to the `modelSuggestions` dict body (lines ~340–364). Any change outside that range is a bug.
- The automated verify command in Task 1 returns `VERIFY OK`.
- Manual cross-check against Kotlin source of truth: the first Together suggestion (`google/gemma-4-31B-it`) matches `PROVIDER_DEFAULTS["together"]` in `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` (set by 260510-w9h).
</verification>

<success_criteria>
- iOS Together provider quick-pick: opening the suggestion sheet shows `google/gemma-4-31B-it` first, with note "Empfohlen · 12/12 in Eval".
- No remaining "Kostenlos · empfohlen" or "Stark, mittlere Latenz" strings in `AISettingsView.swift`.
- iOS quick-pick recommendations no longer contradict the Kotlin defaults and error-message recommendations shipped in 260510-w9h.
- Atomic commit (single file, single concern). Suggested message: `chore(260510-wgs): align iOS AI model-suggestion notes with eval evidence`.
</success_criteria>

<output>
After completion, create `.planning/quick/260510-wgs-ios-aisettingsview-model-suggestions-an-/260510-wgs-SUMMARY.md` documenting:
- The 7 string sites changed (1 reorder + 6 note edits, mapped to D-01..D-05).
- Confirmation that the 4 out-of-scope entries (D-06) were left untouched.
- The cross-platform consistency claim: iOS Together quick-pick first entry now matches Kotlin `PROVIDER_DEFAULTS["together"]`.
- Resolves the deferred item logged in `.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/deferred-items.md`.
</output>
