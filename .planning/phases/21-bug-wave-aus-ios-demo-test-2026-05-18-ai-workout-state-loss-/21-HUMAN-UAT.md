---
status: partial
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
source: [21-VERIFICATION.md]
started: 2026-05-19T02:00:00Z
updated: 2026-05-19T02:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. B5 — Dynamic Grace-Period in Geofence-Exit Notification (Plan 21-01 Task 3)
expected: Set grace=10 in Settings → trigger geofence-exit → Notification reads "10 Sekunden um zurückzukommen, sonst wird das Workout abgebrochen." Set grace=300 → "X Minuten …".
result: [pending]

### 2. B4 — Daily-Log Search Submit-Clear (Plan 21-02 Task 3)
expected: Im Daily-Log → Lebensmittel hinzufügen → "Joghurt" eingeben, Return drücken: Tastatur kollabiert, Resultate bleiben sichtbar, Query bleibt im Feld.
result: [pending]

### 3. B3 — OFF Search Relevance + Brand-Match (Plan 21-03 Task 3)
expected: Spec-Queries "Chips ungarisch" / "Walnüsse gut und günstig" / "Joghurt" liefern jeweils den erwarteten Brand-Match-Treffer in den Top-3. Keine Regression bei "Banane" / "Apfel".
result: [pending]

### 4. B1 — AI-Workout state-loss survives screen re-entry (Plan 21-04 Task 3)
expected: AI-Generation starten → Tab wechseln → "ist bereit"-Notification → zurück navigieren → generiertes Workout sichtbar, Save/Edit erreichbar, State NICHT Idle. 3× wiederholbar ohne Degradation.
result: [pending]

### 5. B2 — Barcode-Scan persists real macros / shows honest hint (Plan 21-05 Task 3)
expected: Barcode mit OFF-Daten (z.B. 4002971990126 / Coca-Cola 1L): non-zero kcal/protein/carbs/fat, persistiert über Re-Open. Barcode ohne OFF-Daten: Hint "OpenFoodFacts hat für '…' keine Nährwerte. Bitte manuell ergänzen." statt stillem Zero-Row.
result: [pending]

### 6. B6 — XP-Bookkeeping after workout-save lifts user out of Unranked (Plan 21-06 Task 3)
expected: Demo-Replay: -50 Geofence-Penalty, dann normales Workout speichern → User landet auf Silver (Rank 1), nicht stuck auf Unranked. Regression: Existing GOLD_NOVA_I user mit -200 Penalty bleibt auf Rank-Floor (D-10 monotonic).
result: [pending]

### 7. CR-01 advisory BLOCKER — Recipe Double-Save bei wiederholtem Barcode-Scan
expected: Verifizieren ob das in Plan 21-05 eingeführte saveFood (RecipeCreationViewModel:224) + nachfolgendes onEvent(OnFoodSelected) (Z. 237) bei zwei aufeinanderfolgenden Scans desselben Barcodes zu zwei FoodEntity-Rows mit unterschiedlicher UUID führt. Falls ja: Fix per 21-REVIEW.md CR-01.
result: [pending]

### 8. CR-02 advisory BLOCKER — iOS Stale-Default 300 bei Cold-Start
expected: Verifizieren ob bei Cold-Start (App startet während Workout im Hintergrund läuft + User-Setting grace=10) die Exit-Notification mit hardcoded "300" (5 Minuten) statt 10 Sekunden gepostet wird. Falls reproduzierbar: Fix per 21-REVIEW.md CR-02.
result: [pending]

## Summary

total: 8
passed: 0
issues: 0
pending: 8
skipped: 0
blocked: 0

## Gaps
