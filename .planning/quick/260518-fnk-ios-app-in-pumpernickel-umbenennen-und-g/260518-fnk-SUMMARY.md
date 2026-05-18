---
phase: quick-260518-fnk
plan: 01
subsystem: branding/app-identity
tags: [ios, android, branding, app-icon, display-name]
requires: []
provides:
  - assets/app-icon.svg
  - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png
  - androidApp/src/androidMain/res/mipmap-*/ic_launcher{,_round}.png
affects:
  - iosApp/iosApp.xcodeproj/project.pbxproj
  - androidApp/src/androidMain/AndroidManifest.xml
  - androidApp/src/main/res/values/strings.xml
tech_stack:
  added: []
  patterns:
    - "Single SVG source vendored in repo, rendered per-platform via qlmanage (iOS 1024×1024) and sips (Android 5 mipmap densities)"
key_files:
  created:
    - assets/app-icon.svg
    - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png
    - androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png
    - androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png
    - androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png
    - androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png
    - androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png
    - androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png
    - androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png
    - androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png
    - androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png
    - androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png
  modified:
    - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json
    - iosApp/iosApp.xcodeproj/project.pbxproj
    - androidApp/src/androidMain/AndroidManifest.xml
    - androidApp/src/main/res/values/strings.xml
decisions:
  - "Used `qlmanage -t -s 1024` for SVG→PNG render (pre-installed on macOS, no extra deps)"
  - "Reused 1024×1024 master via `sips -z` for all 5 Android densities; identical bitmap for ic_launcher and ic_launcher_round (system applies round mask itself)"
  - "Patched existing `src/main/res/values/strings.xml` in-place rather than adding a duplicate in `androidMain` — avoids merged-resources `app_name` key conflict (verified empirically in plan discovery)"
  - "Inserted `INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel;` in both Debug and Release build configs with matching tab indentation"
metrics:
  duration: ~3min
  completed: 2026-05-18
---

# Phase quick-260518-fnk Plan 01: iOS App in Pumpernickel umbenennen und Icon ausrollen — Summary

Unified iOS/Android branding to display name "Pumpernickel" with a shared app icon rendered from a single repo-versioned SVG source — iOS gets a 1024×1024 AppIcon plus `CFBundleDisplayName` in both build configs, Android gets 5 mipmap densities (× 2 names) plus `@string/app_name` + launcher icon attributes in the manifest.

## Commits

| Task | Hash | Type | Message |
|------|------|------|---------|
| T1 | `0e0a818` | chore | vendor app-icon SVG source |
| T2 | `3df6021` | feat  | set iOS display name to Pumpernickel and ship AppIcon |
| T3 | `c23909c` | feat  | set Android app name to Pumpernickel and add launcher icons |

## Asset Inventory

### iOS

| File | Size | Source |
|------|------|--------|
| `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png` | 1024×1024 | qlmanage from `assets/app-icon.svg` |
| `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` | n/a | references `AppIcon.png` via `"filename"` field |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | n/a | `INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel;` added in Debug (line 685) and Release (line 727) configs |

### Android

| File | Pixel Size |
|------|-----------|
| `androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png` | 48×48 |
| `androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png` | 48×48 |
| `androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png` | 72×72 |
| `androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png` | 72×72 |
| `androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png` | 96×96 |
| `androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png` | 96×96 |
| `androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png` | 144×144 |
| `androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png` | 144×144 |
| `androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png` | 192×192 |
| `androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png` | 192×192 |

| File | Change |
|------|--------|
| `androidApp/src/main/res/values/strings.xml` | `app_name`: `PumpernickelApp` → `Pumpernickel` (in-place) |
| `androidApp/src/androidMain/AndroidManifest.xml` | `<application>`: `android:label="PumpernickelApp"` → `android:label="@string/app_name"`, added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` |

## Verification Results

All three automated verify commands passed:

- **T1**: `test -f assets/app-icon.svg && grep -q 'viewBox="0 0 1024 1024"' assets/app-icon.svg` → PASS
- **T2**: AppIcon.png exists (1024×1024), Contents.json contains `"filename" : "AppIcon.png"`, `grep -c 'INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel' iosApp/iosApp.xcodeproj/project.pbxproj` = 2 → PASS
- **T3**: strings.xml has `app_name=Pumpernickel`, manifest references `@string/app_name` + `@mipmap/ic_launcher` + `@mipmap/ic_launcher_round`, no `PumpernickelApp` label remains, all 10 mipmap PNGs exist with correct pixel sizes → PASS

## Deviations from Plan

None — plan executed exactly as written.

## Next Step

T4 (human-verify checkpoint) — install on iOS simulator and Android emulator, confirm display names show "Pumpernickel" and icons match visually. This step is outside the executor's scope.

## Self-Check: PASSED

- `assets/app-icon.svg`: FOUND
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png`: FOUND
- `androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png`: FOUND
- Commit `0e0a818`: FOUND
- Commit `3df6021`: FOUND
- Commit `c23909c`: FOUND
