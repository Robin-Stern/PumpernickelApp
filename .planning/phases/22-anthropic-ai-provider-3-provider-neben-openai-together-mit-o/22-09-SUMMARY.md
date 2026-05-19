---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 09
subsystem: ios-handoff
tags: [ios, swiftui, ai, anthropic, oauth, handoff, spec]
dependency_graph:
  requires:
    - 22-05  # AnthropicOAuthFlow API (referenced indirectly via viewModel.startAnthropicOAuth)
    - 22-07  # Info.plist CFBundleURLTypes for pumpernickel-oauth scheme
    - 22-08  # AiSettingsViewModel refactor (the API this spec maps to SwiftUI)
  provides:
    - "Pflichtenheft für SwiftUI AISettingsView Multi-Provider Rewrite + Anthropic Connect-Sheet"
    - "Per-MEMORY-Konvention dokumentierte Handoff-Spec — User implementiert SwiftUI selbst"
  affects:
    - iosApp/iosApp/Views/AI/AISettingsView.swift  # User-scope rewrite, no code change in this plan
tech_stack:
  added: []
  patterns:
    - "iOS Handoff via Markdown-Spec statt direkt-geschriebene .swift-Files (Konvention seit Phase 15)"
key_files:
  created:
    - .planning/phases/22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o/22-IOS-HANDOFF.md
  modified: []
decisions:
  - "D-22-05 applied — Spec listet die Settings-only Provider-Switching-Surface, kein Per-Action-Picker"
  - "D-22-06 applied — Modell-Reihenfolge Opus → Sonnet → Haiku, Default Opus 4.7"
metrics:
  duration_minutes: 1
  completed_date: 2026-05-19
  tasks_completed: 1
  files_changed: 1
---

# Phase 22 Plan 09: iOS Handoff Spec für Multi-Provider AI Settings Summary

**One-liner:** iOS-Handoff-Spec dokumentiert das neue `AiSettingsViewModel` Multi-Provider-API (5 StateFlows + 7 Actions) und gibt dem User eine SwiftUI-Implementations-Vorlage für AISettingsView + AnthropicConnectSheet.

## Was erstellt wurde

`.planning/phases/22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o/22-IOS-HANDOFF.md` (144 Zeilen):

- **What broke**-Sektion: alle entfernten Properties/Methoden des ViewModels (`providerPreset`, `baseUrl`, `model`, `apiKeyConfigured`, `setApiKey`, `setProviderPreset` etc.)
- **New StateFlows**-Tabelle: alle 5 reaktiven Flows (`activeProvider`, `modelByProvider`, `connectedProviders`, `oauthInProgress`, `lastError`)
- **New actions**-Liste: alle 7 Aktionen (`setActiveProvider`, `setModel`, `startAnthropicOAuth`, `setAnthropicApiKey`, `setApiKeyFor`, `disconnect`, `clearError`)
- **SwiftUI surfaces**: 
  - AISettingsView (Form mit Section-pro-Provider, Radio + Modell-Picker + Connect/Disconnect-Button)
  - AnthropicConnectSheet (OAuth-Primary + SecureField-API-Key-Fallback)
- **Observer-Hook Pattern**: KMPNativeCoroutinesAsync `asyncSequence(for:)` mit Beispiel-Code
- **Provider model lists** als kopierbarer Swift-Code (Opus 4.7 → Sonnet 4.6 → Haiku 4.5 per D-22-06)
- **AiSettingsKoinHelper-Hinweis**: kein Swift-Side-Change nötig (Koin-internal change)
- **9-Item Acceptance-Checklist** für User-Self-Verify
- **Out-of-Scope**: Info.plist (Plan 22-07), AppDelegate-URL-Handler, AiSettingsKoinHelper.kt

## Cross-References

- **Plan 22-08** hat das `AiSettingsViewModel` neu geschrieben — diese Spec ist die direkte SwiftUI-Konsumenten-Anleitung dafür.
- **Plan 22-07** hat das `Info.plist` mit `pumpernickel-oauth` URL-Scheme committet — Spec macht klar dass kein weiterer iOS-Konfig-Change nötig ist.
- **Plan 22-05** hat den `AnthropicOAuthFlow` mit `ASWebAuthenticationSession` umgesetzt — die Spec verweist auf den self-contained Charakter (kein App-URL-Handling nötig).

## Scope

iOS-Implementation ist explizit **User-Scope** (MEMORY-Konvention seit Phase 15/17/18: `feedback_agent_token_limits.md` — Source-Files mit großen SwiftUI-Surfaces übersteigen Agent-Token-Limits). Dieser Plan erzeugt **kein** `.swift`-File — nur die Markdown-Spec.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- FOUND: `.planning/phases/22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o/22-IOS-HANDOFF.md`
- FOUND commit: `cfbe8b1` (`docs(22-09): add iOS handoff spec for multi-provider AI settings`)
- Automated verification (file existence + 6 grep patterns + line count > 80): all pass (144 lines)
