---
status: partial
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
source: [18-VERIFICATION.md]
started: 2026-05-19T12:00:00Z
updated: 2026-05-19T12:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. F6 Workout-Generation Endtoend-Flow auf Android
expected: Generieren-Button öffnet Skelett, Vorschau-Sheet erscheint mit Template, Alle speichern persistiert es in TemplateList, Abbrechen kehrt zur Form zurück
result: [pending]

### 2. F8 Meal-Generation Endtoend-Flow auf Android
expected: Restliche Makros werden berechnet, Generieren ruft LLM auf, Preview-Sheet zeigt Zutaten + Schritte + Makro-Fit-Indikator, Speichern persistiert Rezept in Rezeptliste
result: [pending]

### 3. BYOK Settings — API-Schlüssel wird nicht im Klartext gespeichert
expected: Kein Schlüsselwert in logcat, DataStore, SharedPreferences-Plaintext oder Crashberichten sichtbar; adb pull und grep auf Datenspeicher liefern keinen Klartext-Schlüssel
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
