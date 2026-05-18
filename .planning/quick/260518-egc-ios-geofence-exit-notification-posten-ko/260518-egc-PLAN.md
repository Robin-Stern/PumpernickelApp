---
phase: quick-260518-egc
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - iosApp/iosApp/AppDelegate.swift
  - iosApp/iosApp/PumpernickelApp.swift
autonomous: false
requirements: [QUICK-260518-EGC-01]
must_haves:
  truths:
    - "Beim ersten App-Start fragt iOS einmalig nach Erlaubnis für Notifications (Alert+Sound+Badge)."
    - "Wenn der Mock-Geofence-Exit getriggert wird und die App im Foreground ist, erscheint ein Banner + Sound."
    - "Wenn die App im Background/gesperrt ist und Mock-Exit getriggert wird, erscheint die Notification im Lock-Screen / Notification-Center."
    - "Mindestens eine der 5 Trigger-Varianten (exitDetected, reEntered, graceExpired, earlyExitWithBudget, earlyExitWithPenalty) wird zuverlässig zugestellt."
  artifacts:
    - path: "iosApp/iosApp/AppDelegate.swift"
      provides: "UNUserNotificationCenterDelegate + Authorization-Request beim Launch"
      contains: "UNUserNotificationCenterDelegate"
    - path: "iosApp/iosApp/PumpernickelApp.swift"
      provides: "Frühes Setzen des Delegates über UIApplicationDelegateAdaptor"
      contains: "@UIApplicationDelegateAdaptor"
  key_links:
    - from: "iosApp/iosApp/AppDelegate.swift"
      to: "UNUserNotificationCenter.current()"
      via: "requestAuthorization(options:) im didFinishLaunching"
      pattern: "requestAuthorization"
    - from: "iosApp/iosApp/AppDelegate.swift"
      to: "UNUserNotificationCenter.current().delegate"
      via: "self assignment im didFinishLaunching"
      pattern: "\\.delegate\\s*=\\s*self"
    - from: "iosApp/iosApp/Views/Workout/WorkoutSessionView.swift"
      to: "iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift"
      via: "postGeofenceNotification(_:) — bereits gewired, NICHT anfassen"
      pattern: "postGeofenceNotification"
---

<objective>
iOS-Notifications für Geofence-Exit-Events erscheinen aktuell GAR NICHT (User-Test 2026-05-18), Android funktioniert. Das Posten ist Swift-seitig in `WorkoutSessionView.handleGeofenceStateChange` (Z. 1002–1027) und im Early-Exit-Dialog (Z. 500–505) bereits korrekt gewired und ruft `postGeofenceNotification` auf — aber zwei iOS-spezifische Voraussetzungen fehlen:

1. **`UNUserNotificationCenter.requestAuthorization(...)` wird beim App-Start nie aufgerufen.** Die einzige Stelle, die Authorization anfragt, ist `shared/src/iosMain/.../NotificationService.ios.kt` (init-Block) — diese Klasse wird aber von der iOS-UI nirgends aus Koin resolved. Ergebnis: ohne Authorization → `add(request)` schlägt silent fehl.
2. **Kein `UNUserNotificationCenterDelegate` gesetzt.** iOS unterdrückt im Foreground Notifications by-default, es sei denn der Delegate's `willPresent`-Callback retourniert `[.banner, .sound, .list]`. Da der User beim Test mit kurzer Grace-Period und Mock-Exit testete, war die App vermutlich im Foreground → silent drop.

Beide Fixes leben rein im Swift-Code und sind sehr klein. Die Kotlin-Seite (commonMain ViewModel, GeofenceProvider, expect/actual `NotificationService`) bleibt UNVERÄNDERT.

Purpose: Parity mit Android-Verhalten (User: "Bei Android bekomme ich eine Menge Benachrichtigungen") + Vorbereitung für Demo.
Output: AppDelegate als `UNUserNotificationCenterDelegate` mit Authorization-Request + Foreground-Presentation-Policy.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md

<interfaces>
<!-- Bereits existierender Swift-Code — Executor MUSS hier nichts ändern. Nur lesen für Kontext. -->

From iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift:
```swift
enum GeofenceNotification {
    case exitDetected
    case reEntered
    case graceExpired(loggedSets: Int, penaltyXp: Int)
    case earlyExitWithBudget(remainingAfter: Int)
    case earlyExitWithPenalty(penaltyXp: Int)
    static let categoryId = "workout.geofence"
}

extension UNUserNotificationCenter {
    func postGeofenceNotification(_ notification: GeofenceNotification)
}
```

From iosApp/iosApp/AppDelegate.swift (current shape — keep all existing logic, ADD to it):
```swift
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: ...) -> Bool {
        // existing: UserDefaults reset, KoinInitIosKt.doInitKoinIos(), DEBUG override,
        // getGeofenceProvider(), LocationPermissionRequester.shared
        return true
    }
}
```

From iosApp/iosApp/PumpernickelApp.swift (current shape):
```swift
@main
struct PumpernickelApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    // init() already requests CL location authorization — ADD UN authorization there too
}
```

Existing call sites (already in place, working on Android, working on iOS once auth+delegate is fixed):
- WorkoutSessionView.swift:1009 — `.exitDetected` on grace-period entry
- WorkoutSessionView.swift:1014 — `.reEntered` on return to zone
- WorkoutSessionView.swift:1024 — `.graceExpired` on grace expiry
- WorkoutSessionView.swift:502 — `.earlyExitWithBudget` on early-exit dialog confirm
- WorkoutSessionView.swift:504 — `.earlyExitWithPenalty` on early-exit dialog confirm

</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: AppDelegate als UNUserNotificationCenterDelegate + Authorization-Request</name>
  <files>iosApp/iosApp/AppDelegate.swift</files>
  <action>
Erweitere `AppDelegate` in `iosApp/iosApp/AppDelegate.swift`:

1. Import hinzufügen (oben in der Datei, neben den vorhandenen Imports): `import UserNotifications`.

2. Klassen-Konformität erweitern: `class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate { ... }`.

3. Am ENDE von `application(_:didFinishLaunchingWithOptions:)`, direkt VOR `return true`, folgenden Block einfügen:

```swift
// Phase quick-260518-egc — iOS Geofence-Notification Parity:
// (a) UNUserNotificationCenter Authorization muss explizit angefragt werden, sonst
//     schlagen alle `postGeofenceNotification(...)`-Calls silent fehl.
// (b) Self als Delegate setzen, damit Notifications auch im FOREGROUND als Banner+Sound
//     präsentiert werden (iOS unterdrückt sie sonst by-default).
let notificationCenter = UNUserNotificationCenter.current()
notificationCenter.delegate = self
notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
    if let error {
        print("UN authorization error: \(error.localizedDescription)")
    } else {
        print("UN authorization granted: \(granted)")
    }
}
```

4. Füge AM ENDE der Klasse (vor der schließenden `}`) die Delegate-Methode für Foreground-Presentation hinzu:

```swift
// MARK: - UNUserNotificationCenterDelegate

/// D-quick-egc — Foreground-Presentation der Geofence-Notifications.
/// Ohne diese Methode unterdrückt iOS Notifications, wenn die App im Vordergrund läuft —
/// genau der Test-Fall, in dem der User Mock-Exit aus dem Debug-Sheet triggert.
func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
) {
    completionHandler([.banner, .sound, .list])
}
```

5. NICHTS anderes anfassen — die bestehende Koin-Init / Geofence-Provider-Force-Resolve / LocationPermissionRequester-Logik bleibt unverändert in ihrer ursprünglichen Reihenfolge.

Hinweis: Die Authorization-Anfrage wird nach Apple-Konvention exakt einmal angezeigt — beim allerersten Launch nach der Installation. Beim Test auf einem Gerät, auf dem das App bereits installiert war (mit alter Version OHNE Authorization-Request), muss der User die App einmal komplett deinstallieren ODER manuell in den iOS-Settings → PumpernickelApp → Mitteilungen aktivieren. Das ist KEIN Bug, sondern iOS-Verhalten.
  </action>
  <verify>
    <automated>cd "/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp" && grep -c "UNUserNotificationCenterDelegate" iosApp/iosApp/AppDelegate.swift | grep -qE "^[1-9]" && grep -c "requestAuthorization" iosApp/iosApp/AppDelegate.swift | grep -qE "^[1-9]" && grep -c "willPresent" iosApp/iosApp/AppDelegate.swift | grep -qE "^[1-9]" && grep -c "\\.delegate = self" iosApp/iosApp/AppDelegate.swift | grep -qE "^[1-9]"</automated>
  </verify>
  <done>
- `AppDelegate.swift` deklariert Konformität zu `UNUserNotificationCenterDelegate`.
- `requestAuthorization(options:)` mit `[.alert, .sound, .badge]` wird in `didFinishLaunchingWithOptions` aufgerufen.
- `UNUserNotificationCenter.current().delegate = self` wird gesetzt.
- `userNotificationCenter(_:willPresent:withCompletionHandler:)` ist implementiert und retourniert `[.banner, .sound, .list]`.
- Datei kompiliert (xcodebuild bzw. der nachfolgende Checkpoint validiert das).
  </done>
</task>

<task type="auto">
  <name>Task 2: Sanity-Check — PumpernickelApp.swift weiter konsistent</name>
  <files>iosApp/iosApp/PumpernickelApp.swift</files>
  <action>
Öffne `iosApp/iosApp/PumpernickelApp.swift` und verifiziere VIA READ-ONLY, dass:
- `@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate` vorhanden ist (Z. 9 im aktuellen Stand).
- Die existierende Zeile `locationManager.requestWhenInUseAuthorization()` (Z. 17) bleibt — KEIN Touch.

Falls (und nur falls) `@UIApplicationDelegateAdaptor` fehlen sollte (sollte vorhanden sein!), füge die Property direkt unter `struct PumpernickelApp: App {` ein:

```swift
@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
```

Wichtig: Die UN-Authorization landet bewusst in `AppDelegate.didFinishLaunchingWithOptions`, NICHT in `PumpernickelApp.init()`. Grund: AppDelegate-Adapter laufen vor `body`-Eval und vor `init()` ist `appDelegate` noch nicht aufgesetzt. AppDelegate ist die idiomatische Stelle für solche System-Service-Initialisierungen.

In den meisten Fällen ist diese Task ein No-Op (nur Read + Bestätigung).
  </action>
  <verify>
    <automated>cd "/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp" && grep -c "@UIApplicationDelegateAdaptor(AppDelegate.self)" iosApp/iosApp/PumpernickelApp.swift | grep -qE "^[1-9]"</automated>
  </verify>
  <done>
- `PumpernickelApp.swift` enthält weiterhin `@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate`.
- Keine Regressionen am Theme-/Tutorial-/Photo-Capture-Setup.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Manueller Test — Notifications kommen an</name>
  <what-built>
- `AppDelegate` ist jetzt `UNUserNotificationCenterDelegate`, fragt beim Launch nach Notification-Berechtigung und erlaubt Foreground-Banner via `willPresent`-Callback.
- Existing Call-Sites in `WorkoutSessionView.swift` (handleGeofenceStateChange + Early-Exit-Dialog) bleiben unverändert — sie können jetzt erfolgreich posten.
  </what-built>
  <how-to-verify>
**Wichtig vor dem Test:** App komplett vom Test-Device/Simulator deinstallieren (Apple zeigt den Authorization-Prompt nur einmal pro Install). Alternativ: iOS-Settings → PumpernickelApp → Mitteilungen → manuell auf "Erlauben" stellen, falls Deinstallation nicht möglich ist.

**Build & Run (entweder):**

A) Über Xcode: `iosApp/iosApp.xcodeproj` öffnen → Scheme `iosApp` → Run auf Simulator (z.B. iPhone 16 Pro).

B) Über CLI (falls aktiver Simulator-Build gewünscht):
```bash
cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination "platform=iOS Simulator,name=iPhone 16 Pro" build
```

**Test-Schritte:**

1. App starten → iOS muss EINMALIG den Dialog **"PumpernickelApp möchte dir Mitteilungen senden"** zeigen → "Erlauben" tippen.
2. In Settings einen Workout starten, Trainingszone setzen lassen (oder Mock-Exit-Modus).
3. Debug-Pille (rotes Ladybug-Icon unten links) tippen → DebugGeofenceSheet öffnen.
4. **Test 1 — Foreground, kurze Grace:** Grace-Period auf wenige Sekunden setzen, Mock-Exit triggern. Erwartet: 
   - Banner-Notification "Du hast die Zone verlassen — 5 Minuten um zurückzukommen…" ERSCHEINT TOP-OF-SCREEN während App im Foreground ist.
   - Nach Ablauf der Grace: zweite Notification "Workout beendet — Du hast die Trainingszone verlassen. X Sätze wurden gespeichert, Y XP abgezogen."
5. **Test 2 — Background, Lock-Screen:** Workout starten, App in den Background schicken (Home-Button), Mock-Exit via DebugGeofenceSheet triggern (falls vom DEBUG-Pfad aus Background möglich — sonst Test 2 überspringen und nur Test 1 + Test 3 nachweisen). Notification erscheint im Notification-Center.
6. **Test 3 — Re-Enter:** Workout starten → Mock-Exit triggern (in Grace) → Mock-Enter triggern. Erwartet: zweite Notification "Workout läuft weiter — Willkommen zurück. Weiter geht's."
7. **Test 4 — Early-Exit-Dialog:** Während Workout aktiv & in Zone, "Workout beenden"-Button drücken → Early-Exit-Dialog bestätigen. Erwartet: Notification "Workout beendet — Early Exit genutzt …" oder "… XP abgezogen für vorzeitiges Beenden."

**Failure-Modes zum Triage:**
- Dialog "Mitteilungen senden" erscheint NICHT → Authorization-Code im AppDelegate falsch platziert. Console-Log nach "UN authorization …" prüfen.
- Dialog erscheint, danach aber keine Notification → Permission nachträglich abgelehnt oder iOS-Settings → PumpernickelApp → Mitteilungen prüfen.
- Background-Notification fehlt, aber Foreground geht → kein neuer Bug; Delegate ist korrekt, das war Test-2-Edge-Case (Simulator ist bei Background-Mock-Exit unzuverlässig).

**Console-Logs zum Prüfen** (in Xcode Console während App läuft):
- "UN authorization granted: true" beim ersten Launch
- KEIN "GeofenceNotification post failed: …" beim Mock-Exit
  </how-to-verify>
  <resume-signal>
Antworte mit:
- `approved` — Notifications erscheinen erwartungsgemäß (mindestens Test 1 + 3 + 4 funktionieren); Phase als done markieren.
- `partial: <details>` — wenn nur einige Trigger funktionieren (z.B. Foreground ja, Background nein). Ich entscheide dann, ob ein Follow-up-Plan nötig ist.
- `failed: <details>` — keine Notification kommt an. Ich untersuche dann tiefer (Authorization-Status via `getNotificationSettings`, Provisioning-Profile etc.).
  </resume-signal>
</task>

</tasks>

<verification>
Phase done wenn:
- Beide Code-Änderungen committed.
- User-Test bestätigt mindestens Foreground-Banner für `.exitDetected` UND `.graceExpired` UND Early-Exit-Dialog-Notifications.
</verification>

<success_criteria>
- iOS verhält sich bei Geofence-Exit funktional gleich wie Android: Notifications erscheinen.
- KEIN Regressionsverhalten bei Theme, Tutorial, Photo-Capture, AI-Generation oder Geofence-Cold-Start-Reconcile.
- Build kompiliert ohne neue Warnings (außer ggf. unused-result auf `requestAuthorization` completion — akzeptabel, da wir Logging haben).
</success_criteria>

<output>
After completion, create `.planning/quick/260518-egc-ios-geofence-exit-notification-posten-ko/260518-egc-SUMMARY.md`
</output>
