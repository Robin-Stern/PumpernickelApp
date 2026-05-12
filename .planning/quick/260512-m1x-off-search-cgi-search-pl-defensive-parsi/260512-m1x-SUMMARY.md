---
phase: quick-260512-m1x
plan: 01
type: execute
status: complete
completed: 2026-05-12
files_changed:
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt
  - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
commits:
  - 5826f37 fix(off): use cgi/search.pl + defensive HTML detection (v2 search 503)
  - 24087b1 feat(ios): tab-picker for food entry — manual / online search / barcode
requirements_completed:
  - QUICK-260512-m1x
---

# Quick 260512-m1x — OFF cgi/search.pl + iOS Tab-Picker Summary

**Hinweis:** Der Executor hat während Task 2 einen Stream-Timeout erlitten, aber beide Code-Edits sind erfolgreich committet. SUMMARY.md wird vom Orchestrator nachträglich verfasst, basierend auf der Verifikation des Repo-Zustands.

## Task 1 — Backend (Commit 5826f37)

`OpenFoodFactsApi.kt`:
- URL: `api/v2/search` → `cgi/search.pl` (Zeile 22)
- Parameter: `search_terms`, `search_simple=1`, `action=process`, `json=1`, `fields=product_name,brands,nutriments`, `page_size`
- Defensive HTML-Detection vor `json.decodeFromString`: `if (responseText.trimStart().startsWith("<")) throw IllegalStateException(...)` (Zeile 31)
- User-Agent header unverändert
- `lookupBarcode()` unverändert

`SearchFoodsRemoteUseCase.kt`:
- catch-block markert die HTML-Detection-Exception und mappt auf deutsche User-Message "OpenFoodFacts ist gerade nicht erreichbar. Versuch es später nochmal."

## Task 2 — iOS UI (Commit 24087b1)

`NutritionFoodEntryView.swift`:
- `private enum InputMode { case manual, search, barcode }` als nested type (Zeile 8)
- `@State private var inputMode: InputMode = .manual` (Zeile 20)
- Segmented Picker oben am Body: 3 Tags "Manuell" / "Suchen" / "Barcode" (Zeile 27-32)
- `switch inputMode` mit 3 cases:
  - `.manual` → entryForm + savedFoodsList
  - `.search` → onlineSearchSection
  - `.barcode` → barcodeSection
- Auto-switch `inputMode = .manual` an 2 Stellen:
  - im `remoteFoodCard` Button-action nach `OnRemoteFoodSelected`
  - in `.fullScreenCover(isPresented: $showBarcodeScanner, onDismiss: { inputMode = .manual })`
- Barcode-Button aus entryForm raus, nur noch im Barcode-Tab
- `remoteFoodCard(_ result: SearchFoodsRemoteUseCase.RemoteFoodResult)` — dotted typename preserved

## Verification Outcomes

| Gate | Result |
|---|---|
| `grep -c "api/v2/search" OpenFoodFactsApi.kt` | 0 ✓ |
| `grep -c "cgi/search.pl" OpenFoodFactsApi.kt` | 1 ✓ |
| `grep -c "startsWith(\"<\")" OpenFoodFactsApi.kt` | 1 ✓ |
| `grep -c "OpenFoodFacts ist gerade nicht erreichbar" SearchFoodsRemoteUseCase.kt` | ≥1 ✓ |
| `grep -c "InputMode" NutritionFoodEntryView.swift` | 5 ✓ |
| `grep -c "pickerStyle(.segmented)" NutritionFoodEntryView.swift` | 2 (Tab + UnitPicker) ✓ |
| `grep -c "inputMode = .manual" NutritionFoodEntryView.swift` | 2 ✓ |
| `grep -cE "case .search:|case .barcode:|case .manual:" NutritionFoodEntryView.swift` | 3 ✓ |
| `grep -c "Lebensmittel in OpenFoodFacts suchen" NutritionFoodEntryView.swift` | 1 ✓ |
| `grep -c "Per Barcode hinzufügen" NutritionFoodEntryView.swift` | 1 ✓ |
| `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` | exit 0, 1.3s warm cache ✓ |

## Notes

- Beide Commits in der korrekten Reihenfolge (backend → iOS UI).
- Stale-build hint für den User: bei Xcode noch alte Errors → **Product → Clean Build Folder (⇧⌘K)** oder `rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*`.
- SourceKit-Warnung "No such module 'Shared'" ist umgebungsbedingt (IDE-Cache, kein echter Build-Fehler).

## Deviations

None — Plan executed as written. Executor stream timed out at the very end (post-commits, pre-SUMMARY), Orchestrator wrote SUMMARY.md based on direct verification of repo state.
