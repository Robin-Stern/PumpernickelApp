---
phase: 260512-lei
plan: 01
subsystem: ios-build
tags: [ios, kmp, swift, shared-framework, hotfix]
requires: []
provides:
  - "FoodEntryUiState Swift init with full 17-arg signature"
  - "Freshly linked Shared.framework for iosSimulatorArm64"
affects:
  - "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift"
key-files:
  modified:
    - "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift"
  generated:
    - "shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h"
decisions:
  - "Kept the type name SearchFoodsRemoteUseCaseRemoteFoodResult — verified against Shared.h export pattern; removed stale TODO."
  - "No worktree: ran in main checkout to reuse the warm gradle build cache (23s incremental vs ~30min cold)."
metrics:
  duration_seconds: 109
  completed: "2026-05-12T13:31:07Z"
  tasks_completed: 2
  commits_created: 1
  files_modified: 1
---

# Quick 260512-lei: iOS Build Endgültig Grün — FoodEntryUiState + Shared.framework Summary

**One-liner:** Completed the Swift `FoodEntryUiState` initializer with the three missing remote-search fields and rebuilt the Shared.framework so all four originally reported Xcode errors are resolved (two by source fix, two by stale-framework refresh).

## Outcome

All four originally reported Xcode build errors are now accounted for:

| # | Reported error | Disposition | Evidence |
|---|----------------|-------------|----------|
| 1 | `IosLocationProvider.kt:47:46 Unresolved reference 'latitude'` | Stale cache — source already correct on `main` | `grep -c "loc.coordinate.useContents" = 1`; framework rebuilt in Task 2 |
| 2 | `IosLocationProvider.kt:47:71 Unresolved reference 'longitude'` | Stale cache — same as #1 | Same as #1 |
| 3 | `NutritionFoodEntryView.swift:8:50 Missing arguments for parameters 'remoteSearchResults', 'isSearchingRemote', 'remoteSearchError'` | Code fix in Task 1 | 3 verify-greps each return 1 |
| 4 | `NutritionFoodEntryView.swift:277:43 Cannot find type 'SearchFoodsRemoteUseCaseRemoteFoodResult' in scope` | Stale framework — type now exported (Shared-prefixed) | Header grep `SearchFoodsRemoteUseCaseRemoteFoodResult` = 12; type appears in `@interface SharedSearchFoodsRemoteUseCaseRemoteFoodResult` at line 6385 |

## Task 1: Complete FoodEntryUiState Swift init + remove obsolete TODO

**Commit:** `0778526` — `fix(ios): supply missing remote-search fields in FoodEntryUiState init`

**Files in commit:** `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` (single file, +2 / -2 lines).

### Diff (vor / nach Init-Block)

**Init block — lines 8–12 before:**

```swift
@State private var uiState = FoodEntryUiState(
    name: "", calories: "", protein: "", fat: "", carbs: "", sugar: "",
    barcode: "", unit: .gram, errorMessage: nil, successMessage: nil,
    editingFoodId: nil, searchQuery: "", isLookingUp: false, pendingLogFood: nil
)
```

**Init block — lines 8–13 after:**

```swift
@State private var uiState = FoodEntryUiState(
    name: "", calories: "", protein: "", fat: "", carbs: "", sugar: "",
    barcode: "", unit: .gram, errorMessage: nil, successMessage: nil,
    editingFoodId: nil, searchQuery: "", isLookingUp: false, pendingLogFood: nil,
    remoteSearchResults: [], isSearchingRemote: false, remoteSearchError: nil
)
```

**TODO line — before (was line 276):**

```swift
    // TODO(framework-name): adjust generated nested type if Shared framework exports a different flat name
    private func remoteFoodCard(_ result: SearchFoodsRemoteUseCaseRemoteFoodResult) -> some View {
```

**TODO line — after (TODO removed, `remoteFoodCard` now starts at line 276):**

```swift
    private func remoteFoodCard(_ result: SearchFoodsRemoteUseCaseRemoteFoodResult) -> some View {
```

### Verify-greps (post-edit, pre-commit)

| grep | expect | got |
|------|--------|-----|
| `remoteSearchResults: \[\]` in `NutritionFoodEntryView.swift` | 1 | **1** ✓ |
| `isSearchingRemote: false` in `NutritionFoodEntryView.swift` | 1 | **1** ✓ |
| `remoteSearchError: nil` in `NutritionFoodEntryView.swift` | 1 | **1** ✓ |
| `TODO(framework-name)` in `NutritionFoodEntryView.swift` | 0 | **0** ✓ |
| `loc.coordinate.useContents` in `IosLocationProvider.kt` (anti-regression) | 1 | **1** ✓ |

### Anti-regression sanity (pre-edit)

`grep -c "loc.coordinate.useContents" shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt` returned **1** before Task 1 started — repo state was correct.

### Post-commit deletion check

`git diff --diff-filter=D --name-only HEAD~1 HEAD` returned **empty** — no files deleted.

## Task 2: Rebuild Shared.framework (no commit)

### Build command used

**Primary** (succeeded — no fallback needed):

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet
```

**Working directory:** `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp` (project root, not `evals/`).

### Build metrics

| Metric | Value |
|--------|-------|
| Gradle command | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` (primary, no fallback) |
| Exit code | **0** ✓ |
| Duration | **23 seconds** (warm incremental build — the rationale for no-worktree paid off) |
| Output | 51 KB of `w:` warnings only (no `e:` errors — pre-existing ObjC export warnings, FlowPreview hints, deprecated `Instant` typealias notes; not caused by this plan) |

### Header verification

**Path:** `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h`

**Exists:** yes
**Mtime:** `May 12 15:30:22 2026` (epoch `1778592622`) — generated during this run
**Size:** 17 129 lines
**Freshness:** `find -mmin -60` = 1 ✓, `find -mmin -30` = 1 ✓

### Post-build greps on Shared.h

| Grep | Need | Got |
|------|------|-----|
| `SearchFoodsRemoteUseCase` | ≥ 1 | **28** ✓ |
| `SearchFoodsRemoteUseCaseRemoteFoodResult` OR `SharedSearchFoodsRemoteUseCaseRemoteFoodResult` | ≥ 1 | **12** ✓ |
| `RemoteFoodResult.*brand` OR `brand[^a-zA-Z]` | ≥ 1 | **3** ✓ |

**`brand` field is exported as nullable NSString:**

```objc
@interface SharedSearchFoodsRemoteUseCaseRemoteFoodResult : SharedBase
- (instancetype)initWithName:(NSString *)name calories:(double)calories protein:(double)protein
    fat:(double)fat carbs:(double)carbs sugar:(double)sugar
    brand:(NSString * _Nullable)brand
    __attribute__((swift_name("init(name:calories:protein:fat:carbs:sugar:brand:)")))
    __attribute__((objc_designated_initializer));
...
@property (readonly) NSString * _Nullable brand __attribute__((swift_name("brand")));
...
@end
```

This matches the `nullable NSString *brand` pattern required by the constraints — and the Swift `swift_name` makes it directly accessible as `result.brand: String?` from `NutritionFoodEntryView.swift:288`.

### Cross-check: FoodEntryUiState init signature in Shared.h

The generated `SharedFoodEntryUiState` designated initializer (line 7836 in Shared.h) takes exactly the 17 parameters in the order the Swift `@State` init now supplies:

```
initWithName: calories: protein: fat: carbs: sugar: barcode: unit: errorMessage:
successMessage: editingFoodId: searchQuery: isLookingUp: pendingLogFood:
remoteSearchResults: isSearchingRemote: remoteSearchError:
```

### No `shared/build` files committed

```
$ git status --porcelain shared/build
(empty)
```

`shared/build/` is gitignored. No `git add` against it was executed in this plan. The only untracked entry on the working tree is the `.planning/quick/260512-lei-...` directory (PLAN.md and this SUMMARY.md — orchestrator handles those in Step 8).

## Stale-build hint for the user

> If Xcode in the IDE still shows the old errors after the gradle run, run
> Product → Clean Build Folder (⇧⌘K) or
> `rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*`.

## Deviations from Plan

None — plan executed exactly as written. No Rule 1/2/3 auto-fixes triggered. No auth gates. No checkpoints. No architectural surprises.

## Known Stubs

None. No empty/placeholder data introduced. The data wiring on the Swift side now matches the Kotlin source of truth.

## Threat Flags

None. The changes are purely a Swift initializer completion and a comment removal — no new endpoints, no auth changes, no schema modifications, no new trust boundaries.

## Self-Check: PASSED

- **Created/modified files exist:**
  - `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` — FOUND (modified)
  - `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h` — FOUND (regenerated, gitignored)
- **Commit exists:** `0778526` — confirmed via `git log -1 --pretty=%s` matching `fix(ios): supply missing remote-search fields in FoodEntryUiState init`
- **Anti-regression sanity:** `loc.coordinate.useContents` still present (grep = 1)
- **All 4 originally reported Xcode errors:** accounted for in the table above
- **`shared/build/` clean:** `git status --porcelain shared/build` returns empty
