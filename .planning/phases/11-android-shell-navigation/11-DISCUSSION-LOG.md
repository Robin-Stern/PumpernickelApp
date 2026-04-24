# Phase 11: Android Shell & Navigation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 11-android-shell-navigation
**Areas discussed:** Theme color mapping, Compose dependencies, App startup, Koin init
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Theme Color Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Primary = accent green, derive full scheme | Use #66BB6A as Material 3 primary, generate scheme | ✓ |
| Custom color slots | Map accent to multiple Material 3 roles manually | |
| Material You dynamic | Use wallpaper-based dynamic color | |

**User's choice:** [auto] Primary = accent green, derive full scheme
**Notes:** Consistent with iOS fixed accent color approach. No dynamic color per REQUIREMENTS-v1.5.md out-of-scope decision.

---

## Compose Dependencies

| Option | Description | Selected |
|--------|-------------|----------|
| Compose BOM | Version-aligned via Bill of Materials | ✓ |
| Individual pinning | Pin each Compose artifact version separately | |

**User's choice:** [auto] Compose BOM
**Notes:** BOM is standard practice, reduces version conflict risk.

---

## App Startup

| Option | Description | Selected |
|--------|-------------|----------|
| No splash, straight to content | Match iOS immediate-load behavior | ✓ |
| Material 3 SplashScreen API | Branded splash during init | |

**User's choice:** [auto] No splash
**Notes:** iOS has no splash screen. Keep parity.

---

## Koin Initialization

| Option | Description | Selected |
|--------|-------------|----------|
| Custom Application class | PumpernickelApplication with startKoin in onCreate | ✓ |
| ComponentActivity-level init | Init Koin per-activity | |

**User's choice:** [auto] Custom Application class
**Notes:** Application-level init is standard for Koin Android. Matches iOS KoinInitIos() pattern.

---

## Claude's Discretion

- Material 3 typography scale choices
- Exact lightColorScheme derivation values
- Navigation animation transitions
- Edge-to-edge display configuration
- Compose compiler plugin configuration approach

## Deferred Ideas

None
