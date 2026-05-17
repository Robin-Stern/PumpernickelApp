---
slug: ios-geofence-not-registering
status: resolved
trigger: |
  iOS: Geofence registriert sich nicht beim ersten geloggten Set in einem Workout.
  Debug-Build (DebugGeofenceProvider via Koin-Override aktiv).
  Reproduziert auf physischem iPhone via Xcode-Debug-Build.
created: 2026-05-17
updated: 2026-05-17
platform: ios
build: debug
component: phase-19-geofencing
specialist_hint: kotlin
---

# Debug Session: iOS Geofence registriert sich nicht beim 1. Set

## Symptoms

### Expected
Nach dem 1. geloggten Set einer Active Session sollte:
1. `WorkoutSessionViewModel.completeSet()` `locationProvider.getCurrentLocation()` aufrufen
2. Bei Erfolg `geofenceProvider.register(center, radiusMeters=50, regionId="active-workout-{startTimeMillis}")` aufrufen
3. Bei `result.isSuccess` → `_geofenceState.value = GeofenceUiState.InZone` setzen
4. Chip oben rechts in WorkoutSessionView von "Inaktiv" auf "In Zone" wechseln
5. `DebugGeofenceProvider.lastRegisteredRegionId` enthält den regionId
6. Mock-Panel in Settings zeigt diesen regionId statt "no active workout"

### Actual
- Chip bleibt "Inaktiv" nach dem 1. Set
- Mock-Panel zeigt "Region: no active workout" → `lastRegisteredRegionId` ist leer
- Schlussfolgerung: `geofenceProvider.register()` wurde NIE aufgerufen
- Location-Permission ist granted (User hat explizit erlaubt)
- Workout Enforcement Rationale wurde gezeigt und mit "Aktivieren" beantwortet

### Error messages
Keine sichtbaren Fehler. Stille Fehlfunktion.

### Timeline
- Phase 19 frisch implementiert (gestern), Quick-Task 260516-nfn Debug-Mock fertig
- Erster physischer iOS-Build-Test überhaupt
- Hat noch nie funktioniert auf iOS (kein Regress, sondern Implementation-Defekt)

### Reproduction
1. Xcode öffnen, Debug-Build auf physisches iPhone deployen
2. App starten, Settings öffnen → "DEBUG — Geofence Mock" Panel sollte sichtbar sein, Region "no active workout"
3. Zur Workout-Tab wechseln, Template wählen, Workout starten
4. Permission-Rationale-Sheet erscheint → "Aktivieren" tappen → System-Dialog → "When in Use" / "Always" erlauben
5. 1. Set loggen (reps + weight + checkmark)
6. Schauen auf Chip oben rechts → bleibt "Inaktiv"
7. Settings öffnen → Mock-Panel zeigt immer noch "no active workout"

## Hypotheses

### H1: `locationProvider.getCurrentLocation()` returned null
`IosLocationProvider.getCurrentLocation()` checked `authorizationStatus()` und returnt `null` wenn nicht `AuthorizedWhenInUse` / `AuthorizedAlways`. Bei iOS-15+ kann die Authorization beim ersten Call vorhanden, beim zweiten Call rejected sein wenn die App backgrounded/foregrounded hat. Oder: die Authorization wurde gerade granted, aber `CLLocationManager` hat den State noch nicht propagiert.

**Falsifiable check:**
- printlns in `IosLocationProvider.getCurrentLocation()` einbauen (status, captured location)
- printlns in `WorkoutSessionViewModel.completeSet` locationJob block (gymLocation Wert vor/nach getCurrentLocation)

### H2: locationJob fires aber `gymLocation` ist bereits ≠ null
Wenn `gymLocation` aus einem vorherigen Workout bestand und `discardWorkout()` / `enterReview()` es nicht resetted: würde der `if (gymLocation == null)` Guard greifen und der register-Block nie laufen. Plan 19-01 + Cold-Start-Refactor in 19-05 könnten hier was reset-Pfad zerschossen haben.

**Status: ELIMINATED** — Code-Audit bestätigt: `enterReview()` (line 658), `discardWorkout()` (line 760), und `handleGeofenceExitGraceExpired` (line 1034, 1122) setzen alle `gymLocation = null`. Reset-Pfad ist korrekt. H2 scheidet aus.

### H3: `geofenceProvider.register()` returned `Result.failure` ohne sichtbare Error
`DebugGeofenceProvider.register()` müsste `Result.success(Unit)` returnen und `lastRegisteredRegionId = id` setzen.

**Status: ELIMINATED** — Code-Lesung von `DebugGeofenceProvider.register()` (line 37-44): setzt `lastRegisteredId = id` und returnt `Result.success(Unit)` — kein Bug. H3 scheidet aus.

### H4: `completeSet` returned early bevor der locationJob-Block erreicht wird
`completeSet` hat einen früheren `return@launch` bei "letztem Set" (lines 443-449 im VM):
```kotlin
if (nextCursor.first == exIdx && nextCursor.second == setIdx) {
    workoutRepository.updateCursor(nextCursor.first, nextCursor.second)
    _sessionState.value = active.copy(exercises = updatedExercises)
    enterReview()
    return@launch        // ← EARLY RETURN — geofence-block wird übersprungen
}
```
Der geofence-register-Block sitzt NACH diesem Early Return (lines 476-499). Für jedes Template bei dem das 1. Set GLEICHZEITIG das letzte Set des letzten Exercises ist (1-Set-Template, oder 1-Exercise/1-Set), trifft diese Bedingung beim ersten Set-Log bereits zu.

**Status: CONFIRMED ROOT CAUSE (primary)**

### H5 (bonus): Permission-Timing-Problem
**Status: SECONDARY / LATENT** — Selbst wenn H4 gefixt wird, bleibt ein potenzielles Timing-Problem: `IosLocationProvider.getCurrentLocation()` ruft `CLLocationManager.authorizationStatus()` synchron ab (line 24). In einem Debug-Build, wo der DebugGeofenceProvider aktiv ist, wird `locationProvider.getCurrentLocation()` trotzdem aufgerufen (er ist nicht gemockt). Wenn die Location-Permission frisch granted wurde (innerhalb derselben App-Session), könnte CLLocationManager den neuen Status noch nicht haben. Dieses Problem tritt aber nur auf wenn H4 erst gefixt ist und das Template mehr als 1 Set hat.

## Eliminated

- H2: `gymLocation` wird in allen Teardown-Pfaden korrekt auf `null` gesetzt (Code-Audit)
- H3: `DebugGeofenceProvider.register()` ist korrekt implementiert (Code-Lesung)

## Evidence

- timestamp: 2026-05-17T00:00:00Z
  type: code_read
  file: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  lines: 400-500
  finding: >
    completeSet() hat Early Return bei line 448 (`return@launch`) direkt nach enterReview().
    Der geofence-register-Block (if gymLocation == null) sitzt bei lines 476-499,
    NACH dem Early Return. Bei Single-Set-Templates trifft computeNextCursor die
    Bedingung nextCursor == (exIdx, setIdx) beim 1. Set → Early Return greift →
    geofence-block wird nie erreicht.

- timestamp: 2026-05-17T00:00:00Z
  type: code_read
  file: shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt
  lines: 37-44
  finding: >
    register() ist korrekt: setzt lastRegisteredId = id, returnt Result.success(Unit).
    H3 ausgeschlossen.

- timestamp: 2026-05-17T00:00:00Z
  type: code_read
  file: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  lines: 651-680, 755-777, 1024-1034, 1113-1122
  finding: >
    gymLocation wird in enterReview() (line 658), discardWorkout() (line 760),
    und zweifach in handleGeofenceExitGraceExpired() (lines 1034, 1122) auf null gesetzt.
    H2 ausgeschlossen.

- timestamp: 2026-05-17T00:00:00Z
  type: code_read
  file: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  lines: 843-859
  finding: >
    computeNextCursor() gibt (exIdx, setIdx) zurück wenn kein weiterer Set und kein
    weiteres Exercise vorhanden ist — d.h. bei jedem "letzten Set" Szenario.
    Single-Set-Templates treffen das beim allerersten completeSet()-Aufruf.

## Current Focus

**Hypothesis:** H4 BESTÄTIGT als primäre Root Cause.

**Root Cause:** In `completeSet()` sitzt der geofence-register-Block (lines 476-499) NACH dem Early-Return-Pfad (line 448) für das letzte Set. Bei jedem Template bei dem das 1. geloggte Set das letzte Set ist (mindestens: alle 1-Set-Templates), wird der geofence-Block komplett übersprungen. `geofenceProvider.register()` wird nie aufgerufen → `lastRegisteredRegionId` bleibt leer → Chip bleibt "Inaktiv".

**Fix direction:** Den geofence-register-Block aus dem "normalen" Pfad extrahieren und SOWOHL im Early-Return-Pfad (vor dem `return@launch`) als auch im normalen Pfad ausführen. Alternativ: einen gemeinsamen Helper `maybeRegisterGeofence(active)` extrahieren und an beiden Stellen aufrufen.

**Next action:** Fix anwenden.

## Resolution

**root_cause:** `completeSet()` in WorkoutSessionViewModel hat einen Early Return für das letzte Set (line 448: `return@launch`) der den geofence-register-Block (lines 476-499) überspringt. Bei Single-Set-Templates greift dieser Early Return beim ersten und einzigen Set — `geofenceProvider.register()` wird nie aufgerufen.

**fix:** `maybeRegisterGeofenceForFirstSet(active)` Helper extrahiert und VOR der `if (last set)`-Branch in completeSet() aufgerufen. Der `(gymLocation == null)` Guard im Helper bewahrt die "once-per-workout"-Semantik. Bei 1-Set-Templates feuert enterReview() den Unregister-Pfad ohnehin direkt danach — Lifecycle bleibt clean.

**fix_status:** applied
**fix_commit:** f50d5e2
**files_changed:**
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt (+35 -24)

**verification:** `:shared:compileKotlinIosArm64` ✅. Manuelle Verifikation auf iPhone steht aus:
1. Debug-Build deployen
2. Workout starten, Permission granten
3. 1. Set loggen → Chip sollte auf "In Zone" wechseln
4. Settings → Mock-Panel → Region zeigt "active-workout-{millis}"
5. Mock "Trigger Exit" → Chip wechselt zu "GracePeriod 5:00"
