---
quick_id: 260518-f1c
title: Android SettingsSheet scroll fix
status: complete
date: 2026-05-18
commit: 4b787fd
---

# Quick Task 260518-f1c — Summary

## Problem
Auf Android-Geräten war die KI-Sektion im SettingsSheet unerreichbar — die ModalBottomSheet-Column hatte keinen `verticalScroll`-Modifier, also wurde alles unterhalb des Viewports abgeschnitten.

## Fix
`androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt`:
- Import `rememberScrollState` + `verticalScroll`
- Column-Modifier um `.verticalScroll(rememberScrollState())` ergänzt

## Verification
- `./gradlew :androidApp:compileDebugKotlin` → BUILD SUCCESSFUL

## Manual UAT pending
- Settings-Sheet auf physischem Android öffnen → bis nach unten scrollen → AI-Provider/Modell-Einstellungen sichtbar
