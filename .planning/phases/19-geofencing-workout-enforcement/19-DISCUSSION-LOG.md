# Phase 19: Geofencing-basierte Workout-Enforcement - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 19-geofencing-workout-enforcement
**Areas discussed:** Geofence-Mechanik & Background, XP-Penalty & Early-Exits-Budget, Permission-Flow & Degradation, Abort-Verhalten/Trigger/UI

---

## Geofence-Mechanik & Background

### Frage 1: Welches Detection-Modell ist die Basis?

| Option | Description | Selected |
|--------|-------------|----------|
| Native Geofence-Events | iOS CLCircularRegion + Android GeofencingClient.addGeofences(). OS-driven Background, Battery-effizient, Always-Allow nötig, Events 1-3min verzögert, OS-Quotas. | ✓ |
| Polling alle ~2min via Coroutine | Eigener Loop, deterministisch, kein OS-Quirks, When-In-Use reicht, höherer Battery-Drain im Background. | |
| Hybrid: Native + Polling-Fallback | Primär native; bei When-In-Use-only fallback auf Polling-Foreground. Komplex, robust. | |

**User's choice:** Native Geofence-Events
**Notes:** Battery-Vorteil + OS-Background-Wakeup ist die richtige Wahl für ein Feature das physische Anwesenheit erzwingen soll.

### Frage 2: Soll das Geofencing im Background funktionieren?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, Background-Detection ist Kernfunktion | OS-Events feuern auch wenn App killed/Background. Always-Allow + iOS Background-Modes. Realistisch für Gym-Nutzung. | ✓ |
| Nur Foreground / Screen-Lock-OK | Events nur im Foreground. Einfacher, leicht zu umgehen. | |
| Foreground primär, Background als Best-Effort | Best-Effort-Background ohne Garantie. | |

**User's choice:** Ja, Background-Detection ist Kernfunktion
**Notes:** Konsequent mit Frage-1-Antwort.

### Frage 3: Wie soll der LocationProvider-Vertrag erweitert werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Neues GeofenceProvider-Interface | Separates Interface in commonMain mit register/unregister/events. LocationProvider unangetastet. | ✓ |
| LocationProvider erweitern | Bestehendes Interface bekommt Geofence-Methods zusätzlich. Mischt Single-Fix mit Long-Lived-Registration. | |
| Reaktiver Flow am LocationProvider | currentLocation: Flow<GeoPoint?>. Nutzt keine OS-native Events. | |

**User's choice:** Neues GeofenceProvider-Interface (commonMain mit expect/actual)
**Notes:** Single-Responsibility, klar trennbar von F5-Code.

### Frage 4: Wie wird das Geofence-Event vom OS in die App geleitet bei App-Kill?

| Option | Description | Selected |
|--------|-------------|----------|
| OS lädt App + Event direkt an Engine | Android BroadcastReceiver, iOS AppDelegate.didFinishLaunchingWithOptions. Cold-Start-Komplexität, robust. | ✓ |
| Notification-zuerst, Verarbeitung beim App-Open | Penalty wird verzögert verbucht. Einfacher, aber User öffnet App evtl. nie wieder. | |
| Beides: Sofort + Notification + Catch-up-Queue | Persistierte Event-Queue für ausgefallene Sofort-Verarbeitung. | |

**User's choice:** OS lädt App + Event direkt an Engine
**Notes:** Cold-Start-Initialisierung von Koin muss im Plan-Phase ausgearbeitet werden — kritischer Pfad.

---

## XP-Penalty & Early-Exits-Budget

### Frage 1: Wie hoch soll die XP-Strafe sein?

| Option | Description | Selected |
|--------|-------------|----------|
| Gestaffelt nach Fortschritt | (plannedSets - loggedSets) × -10 XP, gedeckelt auf -200. Skin in the Game. | ✓ |
| Flat -150 XP | Mitte des Bereichs, vorhersehbar. | |
| Flat -100 XP | Untere Grenze, sanfter Einstieg. | |
| Flat -200 XP | Obere Grenze, sehr harsh. | |

**User's choice:** Gestaffelt nach Fortschritt
**Notes:** Belohnt User die schon viel geschafft haben (geringerer Penalty) und bestraft frühes Aufgeben härter.

### Frage 2: Was passiert mit dem bestehenden -50 F5-Inactivity-Penalty?

| Option | Description | Selected |
|--------|-------------|----------|
| Ersetzen durch Phase-19-Geofence | F5-Pfad wird abgelöst, eine Penalty-Quelle, weniger Verwirrung. | ✓ |
| Beide parallel laufen | F5 deckt "im Gym sitzen ohne Training" ab, Phase 19 das physische Verlassen. | |
| Inactivity-Timer entfernen, nur Geofence | F5 komplett raus, "im Gym sitzen" wird nicht bestraft. | |

**User's choice:** Ersetzen durch Phase-19-Geofence
**Notes:** Phase 19 ist die schärfere Variante derselben Idee — kein Grund zwei parallel laufen zu lassen.

### Frage 3: Wie wird das Early-Exits-Budget zurückgesetzt?

| Option | Description | Selected |
|--------|-------------|----------|
| Kalendermonat (1. des Monats) | Reset am 1. um 00:00 Local-Time. Vorhersehbar, simple Persistenz. | ✓ |
| Rolling 30-Tage-Window | Pro Early-Exit eigene Expiry. Präziser, aber unintuitiv. | |
| Konfigurierbar in Settings | User wählt Reset-Mode. Over-Engineering. | |

**User's choice:** Kalendermonat (1. des Monats, Local-Time)
**Notes:** Persistenz via DataStore (yearMonth-String + count).

### Frage 4: Wie löst der User einen Early Exit aus?

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit-Button im Workout | Confirm-Dialog. Sauberer Intent, klare Kommunikation. | ✓ |
| Auto-detect: Verlassen mit Budget = Early Exit | Budget wird ohne bewusste Entscheidung verbraucht. | |
| Beides: Button bevorzugt, Auto-Fallback | Mischung, schult Verhalten. | |

**User's choice:** Explicit-Button im WorkoutSessionView
**Notes:** Verlassen ohne Button = immer Penalty. Budget wird nur bei bewusster Action verbraucht.

---

## Permission-Flow & Degradation

### Frage 1: Wann wird Always-Allow-Permission angefordert?

| Option | Description | Selected |
|--------|-------------|----------|
| Beim 1. Workout-Start mit Rationale-Screen | In-context, Apple/Google Best Practice, niedrigere Ablehnungsrate. | ✓ |
| Beim App-Onboarding | Out-of-context, höhere Ablehnungsrate. | |
| Settings-getrieben (manueller Opt-In) | User aktiviert Feature explizit, geringere Aktivierungsrate. | |

**User's choice:** Beim 1. Workout-Start mit Rationale-Screen
**Notes:** Rationale erklärt warum Background-Location nötig ist, dann nativer Dialog.

### Frage 2: Was bei nur When-In-Use Permission?

| Option | Description | Selected |
|--------|-------------|----------|
| Foreground aktiv, Background als Best-Effort | Banner-Hinweis im Workout, ehrlich. | ✓ |
| Feature komplett deaktivieren | Hard-Requirement Always-Allow. | |
| Phasenweise Eskalation | Mehrstufige Warnung über mehrere Sessions. | |

**User's choice:** Feature im Foreground aktiv, Background als Best-Effort mit Banner
**Notes:** Banner Tap → Deep-Link zu Settings. Keine silent failure.

### Frage 3: Was wenn Location komplett verweigert wird?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, Workout läuft ohne Enforcement | Tracking + XP weiter normal, nur Phase-19-Features off. Banner mit Deep-Link. | ✓ |
| Workout blockiert bis Permission gegeben | Aggressiv, könnte als Geisel wahrgenommen werden. | |
| F5-Inactivity-Penalty bleibt als Fallback aktiv | Widerspricht Area-2-Entscheidung. | |

**User's choice:** Ja, ohne Enforcement (Banner mit Deep-Link zu Settings)
**Notes:** Konsistent mit "F5 wird ersetzt" — kein Penalty wenn keine Permission.

### Frage 4: Wie wird Permission-Layer implementiert?

| Option | Description | Selected |
|--------|-------------|----------|
| expect/actual PermissionController in commonMain | Common-Interface, plattform-spezifische Implementierungen. ViewModel-driven. | ✓ |
| Native UI über expect/actual | Komplette Rationale-Screens nativ pro Plattform. | |
| Library wie moko-permissions | Vorgefertigt, Maintenance-Risiko. | |

**User's choice:** expect/actual PermissionController in commonMain
**Notes:** Konsistent mit existierendem KMP-Pattern (LocationProvider etc.).

---

## Abort-Verhalten, Trigger & UI

### Frage 1: Wann wird der Geofence-Anker gesetzt?

| Option | Description | Selected |
|--------|-------------|----------|
| Beim ersten geloggten Set | Wie existierende F5-Logik. Beweist Intent. Keine UI-Änderung. | ✓ |
| Beim Tap auf 'Workout starten' | Sofort GPS-Fix, kann manipulationssicherer sein, aber UI-Delay. | |
| Nach den ersten N Sets (z.B. N=2) | Warm-up-Phase ohne Geofence, komplexer. | |

**User's choice:** Beim ersten geloggten Set
**Notes:** Behält bestehende WorkoutSessionViewModel.completeSet()-Logik (Z. 387–391) bei.

### Frage 2: Was passiert mit geloggten Sets bei Auto-Abort?

| Option | Description | Selected |
|--------|-------------|----------|
| Als unvollständig speichern, Volumen-XP wird vergeben | abandoned-Flag, Anerkennung für was getan wurde, dann Penalty drauf. | ✓ |
| Komplett verwerfen (discard) | Nur Penalty, kein Volumen-XP. Sehr punishing. | |
| Wahlweise: User wählt im Resume-Dialog | User-Choice beim nächsten App-Open. Unklare Semantik. | |

**User's choice:** Als unvollständig speichern, Volumen-XP wird vergeben
**Notes:** Room-Migration v10 → v11 mit abandoned-Flag (additive AutoMigration).

### Frage 3: Re-Entry-Grace-Period?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, 5 Minuten Grace | Notification, Timer, bei Re-Entry resumed. Filtert GPS-Glitches. | ✓ |
| Sofort Penalty + Abort | Klar, aber anfällig für GPS-Glitches. | |
| Konfigurierbar in Settings | Mehr UI-Surface, Over-Engineering. | |

**User's choice:** Ja, 5 Minuten Grace
**Notes:** Konstante GEOFENCE_GRACE_PERIOD_SECONDS = 300L. Native Geofence enter/exit-Events beide genutzt.

### Frage 4: Wo wird Status sichtbar?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimaler Indikator im WorkoutSessionView + Settings-Row | Header-Chip mit Status-Farbe, Settings-Row mit Early-Exits-Counter. | ✓ |
| Dedizierte Karte im Workout | Eigene Card mit allen Infos + Beenden-Button, mehr Real-Estate. | |
| Nur State-driven Banner | Nur bei Events sichtbar, könnte Vertrauensproblem werden. | |

**User's choice:** Minimaler Indikator + Settings-Row
**Notes:** Chip-Zustände grün/gelb/inaktiv/exited; Settings-Detail-Sheet mit Permission-Status.

---

## Claude's Discretion

- Notification-Copy konkret formulieren (Wording-Detail).
- Genaue Button-Platzierung im WorkoutSessionView (Layout-Detail).
- Status-Chip-Style (Pill vs Icon, Material/SF-Symbols-Token).
- abandoned-Flag-Schema (Column vs. Status-Enum).
- PR-Check bei abandoned Workouts (vermutlich nicht).
- Best-Effort unregister() bei App-Kill.
- Onboarding-Tutorial-Step für Enforcement (optional).

## Deferred Ideas

- Multi-Gym-Support mit GPS-Clustering — eigene Phase.
- Workout-Pause-Feature mit Geofence-Toggle — eigene Phase.
- Konfigurierbare Penalty/Budget in Settings — nach UAT.
- Historische Early-Exits-Statistik (vergangene Monate) — Stats-Phase.
- Notification-Sound/Haptic-Customization — Polish-Phase.
- Geofence-Visualisierung auf Map — Maps-Integration-Phase.
- App-Kill während Grace-Period Reconciliation-Logik — fürs Plan-Phase als Edge-Case-Diskussion.

## Reviewed Todos (not folded)

- **2026-05-06-retroactive-progress-photo-attach-from-history** — Auto-Matched über `workout`-Keyword, inhaltlich aber Phase-17-Photo-Feature. Nicht relevant für Phase 19.
