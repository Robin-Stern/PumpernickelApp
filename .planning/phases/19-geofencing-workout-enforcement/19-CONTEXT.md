# Phase 19: Geofencing-basierte Workout-Enforcement - Context

**Gathered:** 2026-05-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Während eines aktiven Workouts überwacht die App via nativer Geofence-Events (CLCircularRegion auf iOS, GeofencingClient auf Android) ob der User innerhalb eines 50m-Radius um den Gym-Ort bleibt. Der Anker wird beim ersten geloggten Set gesetzt — exakt dort, wo der existierende F5-Inactivity-Pfad (`WorkoutSessionViewModel:387–391`) bereits `gymLocation` captured.

**Verlassen ohne Re-Entry innerhalb 5min Grace-Period:**
1. Workout wird auto-abgebrochen (bisherige Sets bleiben als unvollständiges `CompletedWorkout` mit Volumen-XP gespeichert).
2. XP-Penalty wird abgezogen — gestaffelt nach Fortschritt: `(plannedSets - loggedSets) × 10 XP`, gedeckelt auf -200, Mindestens -50.
3. Lokale Notification erscheint.

**Eskape-Hatch:** 2 "Early Exits" pro Kalendermonat. Aktiviert über expliziten "Workout beenden"-Button im `WorkoutSessionView`. Bei Tap mit verbleibendem Budget: Confirm-Dialog "Verbraucht 1 von 2 Early Exits diesen Monat" → kein Penalty. Reset am 1. jedes Monats.

**Ersetzt** den bestehenden F5-Pfad (`GamificationEngine.onInactivityPenalty()` + 10min-Timer + -50 XP via `EventKeys.SOURCE_INACTIVITY`). Phase 19 ist die schärfere, schnellere, intent-basierte Variante derselben Idee.

**Plattform-Scope:** iOS + Android cross-platform via Compose Multiplatform shared logic mit expect/actual für Location/Notification/Permission-APIs.

</domain>

<decisions>
## Implementation Decisions

### Geofence-Mechanik & Background

- **D-19-01 (Native OS-Geofence-Events als Detection-Basis):** iOS via `CLLocationManager.startMonitoring(for: CLCircularRegion)` + `CLLocationManagerDelegate.locationManager(_:didExitRegion:)` / `didEnterRegion:`. Android via `GeofencingClient.addGeofences()` + `BroadcastReceiver` für `GEOFENCE_TRANSITION_EXIT/ENTER`. **Kein eigenes Polling.** Vorteile: OS-getriebenes Background-Wakeup, Battery-effizient, keine eigenen Foreground-Services nötig. Quotas (iOS 20, Android 100) für ein einzelnes Workout unkritisch.

- **D-19-02 (Background-Detection ist Kernfunktion):** Geofence-Exit muss auch dann erkannt werden wenn die App killed oder im Hintergrund ist. iOS: `Info.plist` UIBackgroundModes = `["location"]`. Android: kein Foreground-Service nötig wenn native Geofence-Events genutzt werden, aber `ACCESS_BACKGROUND_LOCATION` Permission Pflicht (API 29+).

- **D-19-03 (Neues `GeofenceProvider`-Interface in commonMain via expect/actual):** Separates Interface — `LocationProvider` (existing, Single-Fix) bleibt unverändert. Neues `GeofenceProvider` mit Signatur etwa:
  ```kotlin
  interface GeofenceProvider {
      suspend fun register(center: GeoPoint, radiusMeters: Double, id: String): Result<Unit>
      suspend fun unregister(id: String)
      val events: SharedFlow<GeofenceEvent>  // ENTER / EXIT / ERROR
  }
  ```
  Implementations: `IosGeofenceProvider` (CoreLocation), `AndroidGeofenceProvider` (Play Services GeofencingClient). Koin-Module registriert die Bindings analog zu `LocationProvider`.

- **D-19-04 (OS lädt App + Event direkt — Cold-Start-fähig):** Geofence-Event wird vom OS auch bei killed App zugestellt:
  - **Android:** Statischer `BroadcastReceiver` in `AndroidManifest.xml` registriert für `com.pumpernickel.geofence.TRANSITION` Action. Receiver bootstrapped Koin-Graph (oder ruft direkt `GamificationEngine.onGeofenceExit()`) — App hat ~10s bevor Android sie killed.
  - **iOS:** `AppDelegate.application(_:didFinishLaunchingWithOptions:)` prüft auf `UIApplication.LaunchOptionsKey.location` → bootstrapped Koin → dispatched Event auf `GeofenceProvider.events`. App hat ~30s.

  Planner muss berücksichtigen: Koin-Init muss schnell sein und idempotent. Penalty-Verbuchung muss in Sekunden durchlaufen können.

### XP-Penalty & Early-Exits-Budget

- **D-19-05 (Penalty gestaffelt nach Fortschritt):** Formel: `penalty = -min(200, max(50, (plannedSetCount - loggedSetCount) * 10))`. Neue Konstanten in `XpFormula.kt`:
  ```kotlin
  const val GEOFENCE_EXIT_PENALTY_PER_MISSED_SET: Int = 10
  const val GEOFENCE_EXIT_PENALTY_MIN: Int = 50
  const val GEOFENCE_EXIT_PENALTY_MAX: Int = 200
  ```
  Eingang `plannedSetCount` = Summe aller `targetSetCount` der WorkoutTemplate-Exercises; `loggedSetCount` = bereits in `CompletedWorkoutSetEntity` persistierte Sets.

- **D-19-06 (F5-Pfad wird ersetzt, nicht zusätzlich):** Bestehender 10min-Inactivity-Timer (`WorkoutSessionViewModel.startInactivityTimer()` Lines 777–794) wird entfernt. `GamificationEngine.onInactivityPenalty()` wird umbenannt/refactored zu `onGeofenceExitPenalty(workoutId, plannedSetCount, loggedSetCount)`. `EventKeys.SOURCE_INACTIVITY` wird durch neue `SOURCE_GEOFENCE_EXIT` ersetzt (Migration nicht nötig — Ledger ist additiv, historische Einträge mit Source `inactivity` bleiben gültig). `XpFormula.INACTIVITY_PENALTY_XP` und `INACTIVITY_TIMEOUT_SECONDS` werden entfernt. `GYM_RADIUS_METERS = 50.0` bleibt und wird zur Geofence-Radius-Source-of-Truth.

- **D-19-07 (Early-Exits: 2/Monat, Kalendermonat-Reset, DataStore-persistiert):** Persistenz in `SettingsRepository` (DataStore Preferences) — zwei Keys:
  - `early_exits_year_month: String` (Format `YYYY-MM`, z.B. `"2026-05"`)
  - `early_exits_used: Int` (0–2)
  Bei jedem Lesen prüfen: ist `early_exits_year_month` ≠ aktuelles Local-Time-yearMonth → reset auf `0`. Implementiert in einem neuen `EarlyExitTracker` (commonMain), der von `WorkoutSessionViewModel` und `GamificationEngine` konsumiert wird. Constant `EARLY_EXIT_BUDGET_PER_MONTH = 2` in einer config-Location, vorerst nicht als User-Setting.

- **D-19-08 (Explicit Early-Exit-Button im Workout):** Im `WorkoutSessionView` ein "Workout beenden"-Button (sichtbar während `WorkoutSessionState.Active`, vor `enterReview()`). Tap-Verhalten:
  - Wenn alle Sets fertig → Normaler Abschluss (kein Confirm, kein Budget-Verbrauch, kein Penalty).
  - Wenn nicht alle Sets fertig + Budget ≥ 1 → Confirm-Dialog: "Workout frühzeitig beenden? Verbraucht 1 von [N] Early Exits diesen Monat." → bei OK: `early_exits_used += 1`, Workout sauber speichern als unvollständig, kein Penalty.
  - Wenn nicht alle Sets fertig + Budget = 0 → Confirm-Dialog: "Du hast diesen Monat keine Early Exits mehr. Frühzeitiges Beenden kostet [calculated_penalty] XP. Fortsetzen?" → bei OK: voller Penalty wie bei Geofence-Exit.

  Verlassen der Zone ohne den Button → immer Penalty (das Budget wird NICHT auto-verbraucht — bewusste Intent ist erforderlich).

### Permission-Flow & Degradation

- **D-19-09 (Always-Allow wird beim 1. Workout-Start angefordert):** Beim ersten Tap auf "Workout starten" — wenn aktueller Permission-Status ≠ AuthorizedAlways/BackgroundLocationGranted — zeigt die App einen Rationale-Screen (in-app, vor dem nativen System-Dialog):
  - Titel: "Workout Enforcement aktivieren?"
  - Body: "Damit dein Workout auch zählt wenn du das Handy weglegst, brauchen wir Standortzugriff im Hintergrund. Wir tracken nur den ~50m-Radius um dein Gym während aktiver Workouts."
  - CTA: "Aktivieren" → triggert nativen Permission-Dialog. "Später" → schließt Sheet, Workout startet ohne Enforcement (siehe D-19-11).
  - iOS-Hinweis im Rationale: "iOS fragt erst 'When-In-Use', dann beim nächsten Workout-Start 'Always'."

- **D-19-10 (Bei nur When-In-Use: Foreground-aktiv, Background als Best-Effort):** Wenn der User When-In-Use gewährt aber Always verweigert:
  - Geofence-Registrierung wird trotzdem versucht. Auf iOS funktionieren `CLCircularRegion`-Events mit When-In-Use nur wenn die App im Foreground ist. Auf Android (API 29+) braucht `addGeofences()` ACCESS_BACKGROUND_LOCATION — ohne wird die Registration mit `GEOFENCE_NOT_AVAILABLE` fehlschlagen.
  - Beim Workout-Start: kleiner Banner im `WorkoutSessionView` "⚠️ Workout Enforcement nur aktiv solange App offen. Tippen für volle Funktion." → Deep-Link zu Settings.
  - Keine silent failure — User weiß was nicht passiert.

- **D-19-11 (Bei komplett verweigerter Location: Workout läuft, kein Enforcement):** Workout-Tracking, Sets-Logging und XP-Vergabe funktionieren unverändert. Phase-19-Geofencing ist deaktiviert (kein Penalty, kein Early-Exit-Counter, kein Budget). Im `WorkoutSessionView` Banner: "Workout Enforcement nicht aktiv — Standortzugriff fehlt. Tippen für Einstellungen." → Deep-Link via `UIApplication.openSettingsURLString` (iOS) bzw. `ACTION_APPLICATION_DETAILS_SETTINGS` Intent (Android). **F5-Inactivity-Pfad wird trotzdem nicht reaktiviert** — D-19-06 bleibt.

- **D-19-12 (Permission-Layer via expect/actual `PermissionController`):** Neues Interface in commonMain:
  ```kotlin
  interface PermissionController {
      suspend fun currentLocationStatus(): LocationPermissionStatus
      suspend fun requestWhenInUse(): LocationPermissionStatus
      suspend fun requestAlways(): LocationPermissionStatus
      suspend fun requestNotifications(): Boolean
      fun openAppSettings()
  }
  enum class LocationPermissionStatus { NOT_DETERMINED, DENIED, WHEN_IN_USE, ALWAYS, RESTRICTED }
  ```
  iOS-actual: `CLLocationManager` + `requestWhenInUseAuthorization()` / `requestAlwaysAuthorization()` + `UNUserNotificationCenter.requestAuthorization()`. Android-actual: `ActivityResultContracts.RequestMultiplePermissions` + Manifest-Permissions `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` + `POST_NOTIFICATIONS` (API 33+). Wired via Koin von `MainActivity` (Android) bzw. App-Root (iOS).

### Abort-Verhalten, Trigger & UI

- **D-19-13 (Geofence-Anker beim 1. geloggten Set):** Behält die existierende Logik in `WorkoutSessionViewModel:387–391` bei. Bei `completeSet()` mit `gymLocation == null`: nach erfolgreichem `locationProvider.getCurrentLocation()` wird zusätzlich `geofenceProvider.register(center=fix, radiusMeters=GYM_RADIUS_METERS, id="active-workout-${workoutId}")` aufgerufen. Bei `enterReview()` und `discardWorkout()` wird `unregister(id)` gerufen (im neuen `locationJob`-Cleanup-Pfad). Bestehender `locationJob: Job?` bleibt für die Single-Fix-Anker-Capture.

- **D-19-14 (Bereits geloggte Sets werden als unvollständig gespeichert mit Volumen-XP):** Bei Geofence-Exit-Penalty (D-19-05 triggert auch wenn `loggedSetCount == 0` mit Mindeststrafe -50):
  1. Wenn `loggedSetCount > 0`: `WorkoutSessionViewModel.saveAbandonedWorkout(reason=GEOFENCE_EXIT)` wird gerufen (neue Methode) — speichert `CompletedWorkout` mit Flag `abandoned=true` (neues Column, Room-Migration v10 → v11, additive AutoMigration). `GamificationEngine.onWorkoutSaved()` wird *nicht* gerufen für abandoned Workouts — stattdessen wird Volumen-XP direkt über eine neue `processAbandonedWorkout(workoutId)`-Methode in `GamificationEngine` vergeben (gleiche `XpFormula.workoutXp()`-Berechnung, aber ohne PR-Check, ohne Achievement-Walks für intermediate state — TODO planner: prüfen ob PRs beim abandoned Workout zählen sollen, vermutlich nein zur Bestrafung der Intent).
  2. Penalty wird separat verbucht via `gamificationRepo.awardXp(source=SOURCE_GEOFENCE_EXIT, eventKey=EventKeys.geofenceExit(workoutId, exitTime), amount=calculatedPenalty, ...)`.
  3. Wenn `loggedSetCount == 0`: keine Workout-Speicherung (es gab nichts zu speichern), nur Penalty (-50 Minimum).

- **D-19-15 (Re-Entry-Grace-Period 5 Minuten):** Bei `GeofenceEvent.EXIT` wird ein 5-Minuten-Timer gestartet + Notification gesendet ("Du hast die Zone verlassen. 5 Min um zurückzukommen, sonst wird das Workout abgebrochen."). Während dieser Zeit:
  - Wenn `GeofenceEvent.ENTER` empfangen wird → Timer abgebrochen, Notification "Workout läuft weiter", Zustand zurück zu "In der Zone". Kein Penalty, keine Speicherung.
  - Wenn 5 Min ohne Re-Entry vergehen → D-19-14 triggert (Auto-Speicherung + Penalty + Notification "Workout beendet wegen Verlassen der Zone").
  - Wenn User in der Grace-Period den Workout-Beenden-Button drückt → Early-Exit-Flow (D-19-08).

  Konstante: `GEOFENCE_GRACE_PERIOD_SECONDS = 300L` in `XpFormula.kt` oder neuem `GeofenceConstants`.

- **D-19-16 (UI: Minimaler Indikator im WorkoutSessionView + Settings-Row):**
  - **`WorkoutSessionView` Header:** kleines Pill/Chip in der Toolbar — Zustände:
    - 🟢 "In Zone" — Geofence aktiv und User innerhalb
    - 🟡 "Grace 4:32" — Countdown zeigt Re-Entry-Restzeit (Format `m:ss`)
    - ⚫ "Inactive" — Permission fehlt oder Pre-1st-Set (kein Anker gesetzt)
    - 🔴 "Exited" — kurz sichtbar bevor Workout abgebrochen wird (Übergang)
  - **"Workout beenden"-Button** als sekundäre Action neben/unter dem Set-Logger (genaue Platzierung Claude's Discretion — auf iOS evtl. Toolbar-Item, auf Android Material-Outlined-Button).
  - **Settings:** neue Row "Workout Enforcement" mit Subtitle "Diesen Monat: X von 2 Early Exits genutzt — Reset am 1. [Nächster-Monat]". Tap → Detail-Sheet mit:
    - Erklärung der Funktion (1-2 Sätze)
    - Permission-Status + "In Einstellungen ändern"-Button
    - Early-Exits-Verlauf (DataStore — nur aktueller Monat sichtbar, vergangene werden nicht historisiert)
    - "Zurücksetzen"-Affordance (vermutlich nicht im MVP, Claude's Discretion)

### Claude's Discretion

- **Notification-Copy konkret formulieren** — User hat keine bestimmten Wordings festgelegt. Planner wählt knapp, neutral, deutsch (analog zu existierenden Notification-Texten falls vorhanden).
- **Genaue Platzierung des "Workout beenden"-Buttons** im `WorkoutSessionView` — Layout-Detail.
- **Status-Chip-Style** (Pill vs. Icon-only, Farben aus Material/SF-Symbols-Token) — Design-Detail, konsistent mit existierender App-Sprache.
- **`abandoned`-Flag auf `CompletedWorkoutEntity`** vs. eigenes Status-Column (z.B. `status: String` mit Werten `"completed" / "abandoned"`) — Schema-Detail, Planner entscheidet bei Migration-Design.
- **`processAbandonedWorkout` PR-Check** — vermutlich keine PR-Anrechnung bei abandoned Workouts, aber Edge-Case-Frage offen.
- **Wer ruft `geofenceProvider.unregister()` bei App-Kill** — Best-Effort über bekannte Lifecycle-Hooks; OS räumt Geofences eh beim App-Uninstall. Falls die App während Workout killed wird und der User in der Zone bleibt: nächster App-Open prüft Active-Workout-State und macht Reconciliation.
- **Onboarding-Tutorial-Integration** (existierendes Onboarding-Overlay vom letzten Commit `0004059`) — Phase 19 fügt eventuell einen Tutorial-Step für das Enforcement-Feature hinzu, optional.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Location/Geofencing Foundation
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt` — Single-Fix-Interface (bleibt unverändert)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/location/GeoPoint.kt` — `distanceMetersTo()` via Haversine (wiederverwendet)
- `shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt` — Vorlage für `IosGeofenceProvider` (CLLocationManager-Patterns, Delegate-Bridge zu Coroutines)
- `shared/src/androidMain/kotlin/com/pumpernickel/feature/location/AndroidLocationProvider.kt` — Vorlage für `AndroidGeofenceProvider` (Play-Services-Patterns, Permission-Checks)

### F5 Inactivity-Penalty (wird ersetzt)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt` §F5-Konstanten — `GYM_RADIUS_METERS=50.0`, `INACTIVITY_PENALTY_XP=50`, `INACTIVITY_TIMEOUT_SECONDS=600L` (letzte zwei werden entfernt)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt` §onInactivityPenalty (Z. 55–86) — Logik-Vorlage für neuen `onGeofenceExitPenalty`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/EventKeys.kt` — `SOURCE_INACTIVITY` wird zu `SOURCE_GEOFENCE_EXIT`; `inactivityPenalty()` Key-Builder analog für `geofenceExit()`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` §gymLocation-Capture (Z. 387–391) + §startInactivityTimer (Z. 777–794) — erste Hälfte bleibt + erweitert, zweite Hälfte wird entfernt

### XP-System-Integration
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/XpLedgerEntity.kt` — Ledger-Schema (additiv erweitert; dedupe via `(source, eventKey)`)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt` (vermutlicher Pfad — Planner verifiziert) — `awardXp()` ist die existierende Penalty-API

### Persistenz
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` (vermutlicher Pfad — Planner verifiziert) — bestehende DataStore-Preferences-Schicht, neue Keys `early_exits_year_month` + `early_exits_used` werden hier hinzugefügt
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — falls `abandoned`-Flag oder Status-Spalte: Room v10→v11 AutoMigration registrieren

### DI
- `shared/src/commonMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` — Bindings für iOS-actuals (analog: neuer `IosGeofenceProvider`, `IosPermissionController`)
- Entsprechendes Android-PlatformModule (Planner verifiziert exakten Pfad) für Android-Bindings

### Project-Level
- `.planning/ROADMAP.md` §Phase 19 (Z. 293–320) — Premise, Scope, offene Designfragen
- `.planning/PROJECT.md` §"Future milestone — F5 Location/GPS" (Z. 89) — F5 war bereits geplant; Phase 19 reift es aus
- `.planning/STATE.md` §Phase 19 added 2026-05-13 (Z. 127) — Premise-Kurzfassung
- `.planning/phases/15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r/15-CONTEXT.md` — F5-Inactivity-Konzept im Original (Penalty-Patterns, Ledger-Konventionen)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`LocationProvider` + `GeoPoint.distanceMetersTo()`**: Single-Fix-Capture-Pfad für den initialen Anker beim 1. Set bleibt 1:1. Haversine-Distanz war für F5 bereits korrekt, kein neuer Math-Code nötig.
- **`gamificationRepo.awardXp()` mit dedupe-Key**: Penalty wird über genau diese API verbucht. EventKey-Builder-Pattern (`EventKeys.inactivityPenalty(sessionStart, now)`) wird kopiert für `geofenceExit(workoutId, exitTime)` → kein Risiko für doppelte Penalty bei Cold-Start-Race-Conditions.
- **`MutableSharedFlow<UnlockEvent>` Pattern in `GamificationEngine`**: Geofence-Events können demselben Pattern folgen (SharedFlow mit `extraBufferCapacity` für Queue-statt-Drop-Semantik).
- **`@Observable` / `StateFlow` Patterns** für iOS/Android-UI (siehe `WorkoutSessionViewModel`): Status-Chip bekommt `geofenceState: StateFlow<GeofenceUiState>` analog zu `restState`.

### Established Patterns
- **expect/actual mit Koin-Modulen pro Plattform** (siehe `PlatformModule.ios.kt`, `AndroidLocationProvider`): Neue `GeofenceProvider` + `PermissionController` folgen demselben Setup. Keine Plattform-Imports in commonMain — strikt enforced.
- **Suspend-Coroutine-Wrapper für native Delegate-Patterns** (siehe `IosLocationProvider.LocationDelegate`): Pattern wird kopiert für `IosGeofenceProvider`. iOS-Delegate-Lifetime-Trap (delegate muss strong-held werden) ist im existierenden Code bereits gelöst — als Vorlage.
- **Cancellation über `Job?`-Pattern in ViewModel** (z.B. `inactivityJob`, `locationJob`, `timerJob`): Neuer `gracePeriodJob: Job?` analog für die 5-Min-Re-Entry-Period.
- **Konstanten-Cluster in `XpFormula.kt`**: Neue Geofence-Konstanten leben dort (nicht in einer neuen `GeofenceConfig.kt`-Datei), um den existierenden Single-Source-of-Truth-Stil beizubehalten. Außer Konflikt mit "XP"-Fokus → Planner kann auch `GamificationConstants.kt` einführen.
- **Room AutoMigration** (siehe v6→v7, v9→v10 in `AppDatabase.kt`): Für neuen `abandoned`-Flag wird derselbe additive AutoMigration-Pattern genutzt.

### Integration Points
- **`WorkoutSessionViewModel.completeSet()`** Z. 380–393: nach `gymLocation`-Capture wird `geofenceProvider.register()` gerufen.
- **`WorkoutSessionViewModel.enterReview()` / `discardWorkout()`** Z. 550, 644: müssen `geofenceProvider.unregister()` triggern + `gracePeriodJob.cancel()`.
- **`WorkoutSessionViewModel.startInactivityTimer()`** Z. 777: wird komplett entfernt; Aufrufer in `completeSet()` (Z. 384) wird ebenfalls entfernt.
- **App-Bootstrap auf iOS (`iosApp/iosApp/App` / `iOSApp.swift`)** + **`MainActivity` auf Android**: müssen geofence-launch-events erkennen und Koin-Init triggern bevor das normale UI rendert.
- **Settings-Screen / -Sheet**: neue Row hinzufügen — Planner liest existierenden Settings-Code (vermutlich `iosApp/iosApp/Views/Settings/...` + `androidApp/.../SettingsScreen.kt`) und folgt dem Pattern (z.B. Theme-Section, Nutrition-Goals-Section).

</code_context>

<specifics>
## Specific Ideas

- **User-Framing aus ROADMAP:** "Klassischer Gym-Drop-Out — User startet Plan, macht die Hälfte, geht heim." → Phase 19 ist explizit eine *Behavior-Change*-Maßnahme. Penalty-Höhe muss sich "lohnen" als Abschreckung, ohne das Feature zu einem Geisel-System zu machen → die gestaffelte Formel (D-19-05) ist genau dieser Kompromiss.
- **Existierender F5-Pfad als 1:1-Migration:** Der User hat F5 nicht erwähnt, aber der existierende Code zeigt dass die Foundation (gymLocation-Capture beim 1. Set + 50m-Radius + EventKey-Dedupe) schon da ist. Phase 19 ist primär ein Upgrade des Detection-Mechanismus (Polling-on-Timeout → OS-Geofence-Events), keine grüne Wiese.
- **Always-Allow-UX-Sensibilität:** "Eskape-Hatch: 2 Early Exits pro Monat" zeigt dass User-Friendlyness wichtig ist. Permission-Verweigerung darf das Workout-Feature nicht killen (D-19-11). Banner statt Blocker.

</specifics>

<deferred>
## Deferred Ideas

- **Multi-Gym-Support** — aktuell wird der Anker pro Workout neu gesetzt (immer dort wo das 1. Set passiert). Eine bewusst gemerkte "Heimat-Gym"-Liste mit auto-detected GPS-Clustering ist eigene Capability → eigene Phase.
- **Workout-Pause-Feature** — kurz pausieren (z.B. für längere Trinkpause) ohne Penalty könnte eine Pause-Funktion sein, die den Geofence temporär deaktiviert. Out of scope hier — würde Phase-15-Streaks und Phase-17-Photo-Capture-Flows berühren.
- **Konfigurierbare Penalty/Budget** in Settings — Power-User möchten vielleicht "härter" oder "softer". Defer bis Tuning-Phase nach UAT.
- **Historische Early-Exits-Statistik** (vergangene Monate) — DataStore speichert nur aktueller Monat. Eine Room-Tabelle mit Vollhistorie wäre eine eigene "Stats"-Phase.
- **Notification-Sound/Haptic-Customization** — Phase 19 nutzt System-Default-Notification. Custom-Sound oder Haptic-Patterns für die "Du hast die Zone verlassen"-Notification → eigene Polish-Phase.
- **Geofence-Visualisierung auf Map** — Settings → "Heutige Workout-Zone anzeigen" mit Mini-Map-View → eigene MapKit/Maps-Integration-Phase (führt MapLibre/Google-Maps-Dependency ein).
- **Cross-Workout-Reconciliation bei App-Kill während Grace-Period** — wenn die App während des 5-Min-Grace-Timers killed wird und der User in der Zone bleibt: nächster App-Open soll prüfen ob es noch ein "abandoned" Workout im Active-State gibt. Edge-Case, Planner kann minimalen Recovery-Code einbauen oder Strict-Mode (App-Kill = Penalty) wählen — Diskussionspunkt fürs Plan-Phase, nicht Discuss.

### Reviewed Todos (not folded)
- **2026-05-06-retroactive-progress-photo-attach-from-history** — Auto-Matched über Keyword `workout`, aber inhaltlich unverwandt (Phase-17-Photo-Feature). Nicht in Phase 19 gefoldet.

</deferred>

---

*Phase: 19-geofencing-workout-enforcement*
*Context gathered: 2026-05-13*
