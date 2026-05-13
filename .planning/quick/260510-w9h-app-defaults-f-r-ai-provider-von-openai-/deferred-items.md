# Deferred Items — 260510-w9h

Items observed during execution that fall outside the plan's `<files_modified>` scope. Logged for future planning, not auto-fixed in this task.

## iOS model-suggestion quick-pick rows still reference `openai/gpt-oss-20b`

**File:** `iosApp/iosApp/Views/AI/AISettingsView.swift`

**Hits:**
- Line 347 — `ModelSuggestion(name: "openai/gpt-oss-20b", note: "Kostenlos · empfohlen")` (likely Together suggestions list)
- Line 355 — `ModelSuggestion(name: "openai/gpt-oss-20b:free", note: "Kostenlos · empfohlen")` (likely OpenRouter — uses `:free` variant, intentionally out of scope)
- Line 362 — `ModelSuggestion(name: "openai/gpt-oss-20b", note: "Stark, mittlere Latenz")` (likely Groq or another preset)

**Why not auto-fixed:**
- Plan `<files_modified>` lists only the 3 Kotlin files (AiSettingsViewModel, RecipeAiUseCase, WorkoutAiUseCase).
- Plan's <interfaces> section explicitly scopes the change to `PROVIDER_DEFAULTS` + 6 error-message sites, not the iOS quick-pick suggestions surface.
- Pre-existing UI surface that lists historical "recommended" notes — updating the model id alone may make the editorial note ("Kostenlos · empfohlen") inaccurate without a deeper editorial review of whether `google/gemma-4-31B-it` is free on Together / OpenRouter / etc.
- The AiSettingsViewModel comment already flags this picker as a separate surface: "see iosApp settings model picker for additional per-provider suggestions surfaced as quick-pick rows."

**Recommended follow-up:** Separate quick task to update the iOS `AISettingsView.swift` `ModelSuggestion` entries with both new model ids AND fresh editorial notes (verify which models are free/paid on each provider before changing copy).
