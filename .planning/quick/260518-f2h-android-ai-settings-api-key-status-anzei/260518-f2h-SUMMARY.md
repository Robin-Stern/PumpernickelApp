---
quick_id: 260518-f2h
title: iOS AI Settings API-Key-Status-Anzeige falsch (UI sagt "nicht gespeichert", Key ist real da)
status: complete
date: 2026-05-18
commit: 7545b26
---

# Quick Task 260518-f2h — Summary

## Problem
User-Report (Parallel-Test 2026-05-18): Beim Speichern eines API-Keys auf iOS zeigt der Indikator zuerst "Gespeichert" ✓. Nach Verlassen + Wiederbetreten der KI-Settings steht "Kein Schlüssel gespeichert", obwohl die Generation trotzdem funktioniert.

Task war ursprünglich als Android-Bug klassifiziert (Sprach-Transkript "Eiweiß" → Speech-to-text Artefakt). Investigation zeigt: tatsächlich iOS-spezifisch.

## Root Cause

`AISettingsView.swift` koppelt das Provider-Picker an `@State providerPreset` und beobachtet parallel den StateFlow via `.task { observeProviderPreset() }`. Beim View-(Re-)Entry läuft folgendes:

1. `@State providerPreset` initialisiert mit Default `"openai"`.
2. `.task` startet, AsyncSequence emittiert den tatsächlich gespeicherten Wert (z.B. `"groq"` falls User ihn gewechselt hatte).
3. `self.providerPreset = value` updated den @State.
4. SwiftUI behandelt das als State-Change → `Picker.onChange` feuert.
5. `viewModel.setProviderPreset(preset: newValue)` wird aufgerufen.
6. **AiSettingsViewModel.setProviderPreset() ruft `secureKeyStore.clearApiKey()`** — das ist by-design wenn der User den Provider wechselt (Schlüssel sind anbieter-spezifisch).
7. `ApiKeyState.set(false)` → UI zeigt "Kein Schlüssel gespeichert".
8. Der Keychain-Eintrag selbst bleibt unangetastet (Race: `clearApiKey` löscht Keychain async, aber selbst wenn er es schafft, der Indikator-Flip ist sofort sichtbar). In manchen Fällen scheitert der Keychain-Delete, was erklärt warum Generation noch funktioniert.

Android ist nicht betroffen weil Compose `collectAsState()` den initialen Wert SYNCHRON aus der StateFlow.value liest, daher gibt es keinen "default → real value"-Übergang.

## Fix

Eine boolesche `@State`-Flag `isSyncingProviderFromFlow`:
- `observeProviderPreset()` setzt sie auf `true` BEVOR der Flow-Wert in den @State geschrieben wird (nur wenn der Wert sich wirklich unterscheidet).
- Der Picker-`onChange`-Handler konsumiert das Flag als One-Shot: wenn `true` → reset auf `false` + return (kein setProviderPreset-Call).
- User-Driven Picker-Changes bleiben unverändert.

## Files Modified
- `iosApp/iosApp/Views/AI/AISettingsView.swift` (+16/-1)

## Verification
- iOS xcodebuild: BUILD SUCCEEDED

## Manual UAT pending
- iOS App neu installieren → KI-Settings → API-Key speichern → "Gespeichert" ✓
- Settings verlassen → KI-Settings erneut öffnen → Indikator MUSS weiter "Gespeichert" zeigen (vorher: flippte auf "nicht gespeichert")
- Provider-Wechsel-Pfad funktioniert weiter: aktiv auf neuen Picker-Wert klicken → setProviderPreset läuft → clearApiKey läuft → Indikator korrekt rot

## Out of Scope (für späteren Quick)
- Sekundärer Verdacht: iOS keychain `SecItemDelete` kann silently fail (`status != errSecSuccess` wird nur geloggt). Wenn der User den Provider tatsächlich wechselt, sollte der Delete robust sein. Aktuell out-of-scope, weil User-Report nur die UI-Diskrepanz beschreibt.
