---
phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
reviewed: 2026-04-22T00:00:00Z
depth: standard
scope: gap-closure (plans 15-10, 15-11) — iOS gamification UI surface
files_reviewed: 6
files_reviewed_list:
  - iosApp/iosApp/Views/Gamification/UnlockModalView.swift
  - iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift
  - iosApp/iosApp/Views/Overview/OverviewRankStrip.swift
  - iosApp/iosApp/Views/Overview/OverviewView.swift
  - iosApp/iosApp/Views/Settings/SettingsView.swift
  - iosApp/iosApp/Views/MainTabView.swift
findings:
  critical: 1
  warning: 4
  info: 5
  total: 10
status: issues_found
---

# Phase 15: Code Review Report (Gap Closure — Plans 10 + 11)

**Reviewed:** 2026-04-22
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Gap-closure review for the iOS gamification UI surface (rank strip, unlock modal
queue, achievement gallery, settings entry point). The code is coherent and
follows the phase PATTERNS doc well (sealed-class via `as?`, `@unknown default`,
KMPNativeCoroutines `asyncSequence`, `.task`-scoped observation).

One critical issue: the `fullScreenCover` queue drain in `MainTabView.swift`
will **skip every other unlock event** because both the user-tap dismissal
and the SwiftUI binding setter pop the head. This breaks D-20 ordering for
the common case (rank promo + achievement in the same save). Plus a handful
of warnings around view-model lifetime and `@State` initial-value allocation,
and a few info-level accessibility / polish notes.

## Critical Issues

### CR-01: Double-pop in pendingUnlocks queue drain (skips every 2nd event)

**File:** `iosApp/iosApp/Views/MainTabView.swift:43-65`
**Issue:** The `.fullScreenCover` binding uses a custom setter that calls
`pendingUnlocks.removeFirst()` whenever `newValue == false`. But the Dismiss
button's `onDismiss` closure (L60–62) *also* calls `removeFirst()`. When the
user taps Dismiss:

1. `onDismiss` runs → `pendingUnlocks` goes `[A, B] → [B]`.
2. SwiftUI then updates the `isPresented` binding to `false` to begin the
   dismissal animation → setter fires with `newValue == false` and
   `!pendingUnlocks.isEmpty` → second `removeFirst()` → queue becomes `[]`.
3. Event **B is lost**. Binding recomputes `get` as `false`, so the next
   event never presents.

This breaks D-20 queue ordering in the exact scenario D-20 was designed for —
a single engine save emitting both a rank promotion and an achievement tier
unlock. Only the first is shown; the second is silently dropped.

**Fix:** Pop in exactly one place. Recommended: let the setter own the pop
and make `onDismiss` a no-op (or the inverse — guard one against the other).

```swift
.fullScreenCover(
    isPresented: Binding(
        get: { !pendingUnlocks.isEmpty },
        set: { newValue in
            // Single source of truth for drain. Fires on both programmatic
            // and user-driven dismissal.
            if !newValue && !pendingUnlocks.isEmpty {
                pendingUnlocks.removeFirst()
            }
        }
    )
) {
    if let head = pendingUnlocks.first {
        UnlockModalView(event: head) {
            // Request dismissal only — do NOT pop here; the binding setter
            // runs removeFirst() when SwiftUI flips isPresented to false.
            // We just need to signal dismissal, which SwiftUI does by
            // observing the binding... but since the head is still in the
            // queue, we need a separate dismiss trigger.
        }
    }
}
```

Simpler fix — drive dismissal with a state flag or use `.sheet(item:)`
with an `Identifiable` wrapper so SwiftUI manages the lifecycle instead of
hand-rolled boolean + queue:

```swift
// Option A: remove the onDismiss pop, use Environment(\.dismiss) inside
// UnlockModalView, and let the binding setter do the single pop.

// Option B (cleaner): drive the cover with an Identifiable item.
@State private var currentUnlock: IdentifiedUnlock? = nil
@State private var pendingUnlocks: [SharedUnlockEvent] = []

.fullScreenCover(item: $currentUnlock, onDismiss: {
    if !pendingUnlocks.isEmpty {
        currentUnlock = IdentifiedUnlock(event: pendingUnlocks.removeFirst())
    }
}) { wrapper in
    UnlockModalView(event: wrapper.event) { currentUnlock = nil }
}
```

Either path ensures **one pop per presented event**. Add a unit/UI test
that enqueues two events and asserts both modals appear in order before the
queue is idle (UAT test 15, currently blocked per STATE notes).

## Warnings

### WR-01: ViewModel instances held in `private let` on View structs

**File:** `iosApp/iosApp/Views/MainTabView.swift:9`,
`iosApp/iosApp/Views/Overview/OverviewView.swift:7-8`,
`iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift:14`,
`iosApp/iosApp/Views/Settings/SettingsView.swift:6`
**Issue:** Each view stores its Koin-resolved VM as `private let viewModel =
KoinHelper....getXxxViewModel()`. SwiftUI `View` structs are value types and
are re-instantiated whenever the parent re-renders. A fresh VM will be
allocated on every parent re-eval, which (a) re-creates Flow collectors, (b)
discards in-flight state, and (c) can race with `.task` cancellation.

In practice this often *happens to work* on today's tab layout because the
parent rarely re-renders, but it is fragile — any parent `@State` change
upstream of these views (e.g., the `selectedTab` change in `MainTabView`)
would re-init the child and its VM. For `AchievementGalleryView` pushed onto
a NavigationStack, each push creates a new VM and re-subscribes to the Flow.

**Fix:** Hoist to `@StateObject`-style ownership. Koin's Compose-KMP pattern
for iOS is typically to wrap the Kotlin VM in a Swift `ObservableObject`
held via `@StateObject`. Alternatively, use `@State` with a lazy holder so
the VM is created exactly once per view identity:

```swift
struct AchievementGalleryView: View {
    @State private var holder = VMHolder()

    private final class VMHolder {
        let viewModel = AchievementGalleryKoinHelper().getAchievementGalleryViewModel()
    }
    // ... use holder.viewModel
}
```

Cheapest fix for v1: document that these screens are expected to have stable
parent identity and add a TODO tying this to the VM-lifetime refactor.

### WR-02: `@State` initial value allocates Kotlin objects on every struct init

**File:** `iosApp/iosApp/Views/Overview/OverviewView.swift:11-12,15`
**Issue:** `@State` default values `SharedRecipeMacros(...)`,
`SharedNutritionGoals(...)`, and `SharedRankStateUnranked()` call Kotlin
constructors at *every* `OverviewView` struct initialization (which happens
on every parent re-render). SwiftUI only uses the first value as the
initial state, but the constructor still executes each time.

**Fix:** Move the Kotlin allocations behind static defaults or into the
first observation, or accept the cost if bridged constructors are cheap.
Lowest-effort fix — store sensible Swift defaults (e.g., zero-valued
structs) and rely on the first `asyncSequence` emission to supply real data.

```swift
@State private var rankState: SharedRankState? = nil
// then:
OverviewRankStrip(rankState: rankState ?? SharedRankState.Unranked.shared)
```

### WR-03: `DateFormatter` instantiated per tile render

**File:** `iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift:177-180`
**Issue:** `footerText` builds a new `DateFormatter` on every redraw for
every unlocked tile. `DateFormatter` is expensive to construct. With N
achievements rendered in a grid, this is N allocations per state change.
Also no timezone is set — `yyyy-MM-dd` will reflect the device's local
time, which is probably intended but undocumented.

**Fix:** Hoist the formatter to a static let on the view type.

```swift
private static let dateFmt: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    return f
}()

private var footerText: String {
    if let millis = tile.unlockedAtMillis {
        let date = Date(timeIntervalSince1970: TimeInterval(truncating: millis) / 1000.0)
        return "Unlocked \(Self.dateFmt.string(from: date))"
    } else {
        return "\(tile.currentProgress) / \(tile.threshold)"
    }
}
```

### WR-04: Enum switches use `default:` instead of `@unknown default`

**File:** `iosApp/iosApp/Views/Gamification/UnlockModalView.swift:136-152`,
`iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift:156-172`,
`iosApp/iosApp/Views/Overview/OverviewView.swift:222-228,265-271`
**Issue:** Switches on bridged Kotlin enums (`SharedTier`,
`SharedTrainingIntensity`) use plain `default:` clauses. This means if
someone adds a `Tier.platinum` or `TrainingIntensity.extreme` in the Kotlin
source, the Swift side compiles silently with the default branch swallowing
the new case — no compiler warning, no test failure, just silently wrong
colors/labels. Kotlin enums bridge to Swift as frozen, but new cases are
additive and the compiler will emit `@unknown default` warnings if used.

**Fix:** Use `@unknown default` to get a compile-time warning when new
enum cases land:

```swift
switch tile.tier {
case .bronze: return Color(red: 0.80, green: 0.50, blue: 0.20)
case .silver: return Color(red: 0.75, green: 0.75, blue: 0.75)
case .gold:   return Color(red: 1.00, green: 0.84, blue: 0.00)
@unknown default: return .secondary
}
```

If the compiler refuses `@unknown default` on the KMP-bridged enum (it may
be treated as non-frozen or as a class), wrap handling in an extension that
exhausts known cases with a fallback instead.

## Info

### IN-01: No accessibility labels on icon-only elements

**File:** all 6 files (e.g., `UnlockModalView.swift:21`,
`OverviewRankStrip.swift:41,58`, `AchievementGalleryView.swift:120`,
`MainTabView.swift:17,26,33`, `OverviewView.swift:92`)
**Issue:** SF Symbols (`medal.fill`, `trophy.fill`, `lock.fill`, `rosette`,
`info.circle`, `arrow.clockwise`, `dumbbell.fill`, etc.) have no
`.accessibilityLabel`. VoiceOver will read the raw symbol name (e.g.,
"medal fill") instead of a human label. The dismiss button in
`UnlockModalView` has a text label so it is fine; icon-only affordances are
the concern.

**Fix:** Add labels to decorative/meaningful icons. For purely decorative
images next to text labels, mark them hidden from VO:

```swift
Image(systemName: "medal.fill")
    .accessibilityHidden(true)   // decorative — title text carries meaning

Button(action: { viewModel.refresh() }) {
    Image(systemName: "arrow.clockwise")
}
.accessibilityLabel("Refresh")
```

### IN-02: `theme` stored as `var` but never reassigned

**File:** `iosApp/iosApp/Views/Settings/SettingsView.swift:7`
**Issue:** `private var theme = ThemeManager.shared` is declared `var` but
never mutated; calls on it are method invocations that mutate the
singleton's internal state. Should be `let` for clarity.

**Fix:** `private let theme = ThemeManager.shared`

### IN-03: Error paths print to stdout only

**File:** `iosApp/iosApp/Views/Overview/OverviewView.swift:69,79`,
`iosApp/iosApp/Views/Settings/SettingsView.swift:101`,
`iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift:67`,
`iosApp/iosApp/Views/MainTabView.swift:80`
**Issue:** Flow observation catches use `print(...)`. Silent in production.
If a Flow dies (e.g., VM released, Kotlin coroutine cancelled abnormally),
the UI will never recover and the user sees stale or empty state.

**Fix:** For v1, this is acceptable (defer to iOS logging), but at minimum
route through `os.Logger`:

```swift
import os
private let log = Logger(subsystem: "app.pumpernickel", category: "Gamification")
// ...
log.error("Unlock observation error: \(error, privacy: .public)")
```

Future: surface an error banner or a retry affordance when observation
fails.

### IN-04: `SharedUnlockEvent` typealias is marginal

**File:** `iosApp/iosApp/Views/MainTabView.swift:87`
**Issue:** `private typealias SharedUnlockEvent = Shared.UnlockEvent` is
declared but only referenced once (L7). Fine as-is, but if you add no more
call-sites, the direct `Shared.UnlockEvent` spelling is just as clear.
Not actionable — noted only because the alias comment in `UnlockModalView`
(L156-157) is much more load-bearing and should probably live here too
given this is the file that owns the queue state.

**Fix:** Consider hoisting the "nested sealed subclass" explanatory
comment from `UnlockModalView.swift:156` into a shared location (a single
`SharedTypes.swift`) so the pattern is documented once, not per view.

### IN-05: Defensive `else` branch in `OverviewRankStrip` can hide regressions

**File:** `iosApp/iosApp/Views/Overview/OverviewRankStrip.swift:26-29`
**Issue:** The comment says "Defensive — should never hit because
`RankState` is a closed sealed class." That is true at the Kotlin level,
but bridging does not communicate sealed-ness to Swift. If a future
`RankState.Demoted` case lands, this branch silently falls back to
"Unranked — complete a workout to unlock Silver" — user sees wrong copy.

**Fix:** In DEBUG builds, `assertionFailure("Unknown RankState: \(rankState)")`
so the regression is loud during development but safe in release.

```swift
} else {
    #if DEBUG
    assertionFailure("Unknown RankState subtype: \(type(of: rankState))")
    #endif
    unrankedContent
}
```

---

_Reviewed: 2026-04-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Scope: gap-closure (plans 15-10, 15-11)_
