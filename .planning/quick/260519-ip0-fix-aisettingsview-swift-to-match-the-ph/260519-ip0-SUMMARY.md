---
status: complete
quick_id: 260519-ip0
slug: fix-aisettingsview-swift-to-match-the-ph
date: 2026-05-19
commit: d21dad9
---

# Quick Task 260519-ip0: fix AISettingsView.swift to match the Phase 22 AiSettingsViewModel API

## What Was Done

Rewrote `iosApp/iosApp/Views/AI/AISettingsView.swift` to align with the Phase 22
multi-provider `AiSettingsViewModel`. The Swift file was using the old Phase 18
single-provider API; all old methods and flows were gone from the Kotlin side.

## Root Cause

`AiSettingsViewModel.kt` was fully redesigned in Phase 22 — the single-provider
BYOK model (one preset, one key, one baseUrl, one model) was replaced by a
multi-provider model (OpenAI / Together / Anthropic each with their own
credential slot). All legacy methods (`setProviderPreset`, `clearApiKey`,
`setApiKey`, `setBaseUrl`, `setModel(value:)`) and flows (`providerPresetFlow`,
`apiKeyConfiguredFlow`, `modelFlow`, `baseUrlFlow`) were removed. The Swift UI
was never updated, causing `BUILD FAILED` with 24 errors.

## Changes Made

**File:** `iosApp/iosApp/Views/AI/AISettingsView.swift`

- Removed `baseUrl` / `baseUrlDraft` @State and `baseUrlSection` entirely —
  provider base URLs are preset, not user-configurable
- Changed provider picker to 3 entries only: OpenAI, Together.AI, Anthropic
  (removed Groq, OpenRouter, Custom)
- Added Anthropic OAuth path: "Mit Anthropic verbinden (OAuth)" button +
  `oauthInProgress` spinner; `startAnthropicOAuth()` → `setAnthropicApiKey(key:)`
  API-key fallback in a DisclosureGroup
- Fixed all method calls: → `setActiveProvider(provider:)`, `setApiKeyFor(provider:key:)`,
  `setAnthropicApiKey(key:)`, `setModel(provider:model:)`, `disconnect(provider:)`,
  `clearError()`
- Fixed all flow observers:
  - `activeProviderFlow` → ProviderId, extract `.wireName`
  - `modelByProviderFlow` → NSDictionary, iterate to `[String: String]` keyed by wireName
  - `connectedProvidersFlow` → NSSet, extract wireNames to `Set<String>`
  - `oauthInProgressFlow` → KotlinBoolean, use `.boolValue`
  - `lastErrorFlow` → `String?` inline with "Schließen" button → `clearError()`
- Added Anthropic model suggestions: claude-sonnet-4-6, claude-opus-4-7, claude-haiku-4-5
- Removed Groq / OpenRouter model suggestions

## Build Result

`BUILD SUCCEEDED` — Xcode build for iOS Simulator Arm64 passed.
