# PumpernickelApp – Briefing zur PowerPoint-Überarbeitung

**Kontext:** Universitätspräsentation, 15 Minuten. Die Präsentation existiert bereits — sie ist jedoch spärlich befüllt und braucht konkrete Ergänzungen und Korrekturen. Deine Aufgabe ist **nicht**, eine neue Präsentation zu erstellen, sondern die bestehende slide-spezifisch zu überarbeiten.

**Wichtig:** Analysiere das Design der bestehenden Präsentation (Farben, Schriften, Abstände, Kreis-Icons, Bullet-Stile) und übernimm es exakt. Weiche nicht ab.

---

## SLIDE-BY-SLIDE ÄNDERUNGSLISTE

### SLIDE 1 — Titelseite (Pumpernickel)

**Was fehlt:**
Unter dem App-Namen fehlt eine kurze Beschreibung was die App ist. Ergänze genau diesen Satz (oder sinngemäß in gleichem Stil):

> „Pumpernickl ist dein All-in-One Fitness-Begleiter — Workout-Tracker, Ernährungs-Log und Gamification in einer App."

„Modern" im bestehenden Text kann bleiben. Der Untertitel soll zeigen: drei Säulen — Workout, Nutrition, Gamification.

---

### SLIDE 2 — Architektur / Tech-Stack

**Was da steht, ist zu dünn.** Bau diese Folie aus. Verwende das gleiche Layout (Bullet-Liste oder geteilte Spalten) — aber mit mehr Substanz:

**Inhalt für die Folie:**

- **Kotlin Multiplatform (KMP):** Gemeinsame Business-Logik (ViewModels, UseCases, Datenbank-Schicht) läuft auf iOS und Android aus einer Codebasis
- **Compose Multiplatform:** Shared UI-Komponenten — eine Compose-Implementierung für beide Plattformen
- **Architektur-Pattern:** MVVM — ViewModels halten den State via `StateFlow`, Compose-UIs observen reaktiv mit `collectAsState()`
- **Expect / Actual:** KMP-Mechanismus für Plattform-Spezifika. `expect` deklariert ein Interface im Shared Code, `actual` liefert die plattformspezifische Implementierung (z.B. iOS-Kamera vs. Android-Kamera). Dadurch bleibt die gesamte Business-Logik plattformunabhängig.
- **Dependency Injection:** Koin — schlankes DI-Framework, komplett KMP-kompatibel, kein Code-Generator nötig
- **Lokale Datenbank:** Room KMP — SQLite-basiert, annotationsbasierte DAOs, Flow-Integration
- **Navigation:** Jetpack Navigation Compose (KMP-Port), type-safe Routen via `@Serializable`

---

### SLIDE 3 — Workout (Feature-Übersicht mit bunten Kreisen)

**Layout-Problem:** Die Zahlen / Icons in den bunten Kreisen sind nicht zentriert — sie hängen irgendwo. Zentriere alle Inhalte in den Kreisen exakt (vertikal + horizontal).

**Inhalt passt sonst.** Kein Text-Change nötig.

---

### SLIDE 4 — RIR & Intensitätsberechnung

**Passt gut.** Keine Änderung nötig.

Zur Info für dich (falls du Text präzisieren willst):
- RIR = „Reps in Reserve" — wie viele Wiederholungen hätte ich noch geschafft?
- RIR ≤ 3 = echter Trainingsreiz. RIR 4+ = Aufwärmsatz, wird ignoriert.
- Multiplikatoren: RIR 0 → 2,0×, RIR 1 → 1,5×, RIR 2–3 → 1,0×, RIR 4+ → 0,5×
- Ergebnis: LOW / MODERATE / HIGH pro Muskelgruppe, sichtbar als Muscle-Load-Map

---

### SLIDE 5 — Geofencing

**Was fehlt: Erklärung von Expect/Actual im Geofencing-Kontext.**

Ergänze einen kurzen Absatz oder eine Infobox auf der Folie:

> **Expect / Actual Pattern:**
> `GeofenceProvider` ist ein gemeinsames Interface im Shared Code (`expect`). iOS und Android liefern je eine eigene Implementierung (`actual`) — iOS über CoreLocation, Android über Google Play Services. Die gesamte Workout-Logik (Session, Gamification) kennt nur das Interface und ist dadurch plattformunabhängig.

**Was fehlt: Presenter Notes (Speaker Notes zur Folie).**
Füge als Speaker Notes folgende Texte hinzu — 4 Sätze pro Bibliothek:

**CoreLocation (iOS):**
CoreLocation ist Apples Framework für standortbasierte Dienste auf iOS. Es stellt den `CLLocationManager` für GPS-Tracking und `CLCircularRegion` für kreisförmige Geofence-Zonen bereit. Über den `UIBackgroundModes`-Eintrag „location" läuft das Geofencing auch wenn die App im Hintergrund ist. Betritt oder verlässt der Nutzer die Zone, liefert das OS einen Delegate-Callback, den die App in ein `GeofenceEvent` (Enter/Exit) übersetzt.

**Google Play Services Geofencing (Android):**
Google Play Services stellt den `GeofencingClient` bereit, über den kreisförmige Geofences per GPS-Koordinate und Radius registriert werden. Bei einer Transitions (Enter/Exit) sendet das OS einen Broadcast-Intent — auch wenn die App nicht im Vordergrund läuft. Ein statisch registrierter `BroadcastReceiver` empfängt diesen Intent und leitet das Event via `SharedFlow` weiter. Die Workout-Session-Logik reagiert reaktiv auf den Flow, ohne Polling.

---

### SLIDE 6 — Übersicht / Dashboard (bunte Kreise mit Icons)

**Layout-Problem:** Icons in den bunten Kreisen sind nicht zentriert und wirken generisch. Tausche die Icons gegen passendere aus und stelle sicher dass sie vertikal + horizontal zentriert sind:

- Muscle Load → Bizeps-Arm / Körper-Silhouette Icon
- Undertraining Alert → Warnung / Blitz Icon
- Makros / Nutrition → Gabel + Messer oder Apfel Icon
- Vault / Fotos → Kamera oder Schloss Icon

**Inhalt passt sonst.**

---

### SLIDE 7 — Vault (Fortschritts-Fotos)

**Problem 1: Bibliotheken-Zuordnung unklar.** Überarbeite den Bibliotheken-Block so, dass sofort klar ist was zu iOS und was zu Android gehört. Schlage eine geteilte Darstellung vor:

| iOS | Android |
|-----|---------|
| `LocalAuthentication` (LAContext) — Face ID / Touch ID | `androidx.biometric` (BiometricPrompt) — Fingerabdruck / Gesicht |
| `UIImagePickerController` — Kamera-Zugriff | Android Camera API — Kamera-Zugriff |
| `PHPickerViewController` — Foto-Bibliothek | `ActivityResultContracts` — Galerie-Zugriff |
| `NSFileProtectionComplete` — Datei-Verschlüsselung | `filesDir` (private, App-sandboxed) — Datei-Speicherung |

**Problem 2: Expect/Actual fehlt.** Ergänze kurze Erklärung:

> **PhotoVault — Expect/Actual:**
> `expect class PhotoVault` definiert im Shared Code die Operationen (Speichern, Lesen, Löschen). iOS speichert in `Documents/progress_pics/` mit `NSFileProtectionComplete` (Gerät-Verschlüsselung aktiv, solange gesperrt). Android speichert in `filesDir/progress_pics/` — privat, nur von der App zugreifbar. Business-Logik (Gallery, Viewer, Biometrie-Gate) ist vollständig in Shared Code und weiß nichts von der Plattform.

---

### SLIDE 8 — Nutrition (bunte Kreise mit Icons)

**Layout-Problem:** Gleiche wie Workout — Icons/Zahlen in den bunten Kreisen sind nicht zentriert. Zentriere vertikal + horizontal.

**Inhalt passt sonst.**

---

### SLIDE 9 — Barcode-Scanner

**Passt.** Keine Änderung nötig.

---

### SLIDE 10 — KI Workout-Generierung

**Passt.** Keine Änderung nötig.

---

### SLIDE 11 — KI Rezeptgenerierung

**Passt.** Keine Änderung nötig.

---

### SLIDE 12 — KI Infrastruktur

**Passt.** Keine Änderung nötig.

---

### SLIDE 13 — Prompt-Fu / Prompt-Testing

**Was fehlt:** Ergänze auf dieser Folie explizit, dass die Prompts mit **Prompt-Fu** getestet und iteriert wurden. Füge einen Absatz hinzu:

> **Prompt-Testing mit Prompt-Fu:**
> Zur Qualitätssicherung der KI-Ausgaben wurden die System-Prompts mit **Prompt-Fu** getestet — einem Tool für systematisches Prompt-Engineering. Verschiedene Formulierungen, Constraints und Ausgabeformate wurden gegeneinander evaluiert, bevor der finale Prompt in die App übernommen wurde. Da die Prompts als externe `.md`-Dateien im Asset-Bundle liegen (nicht hart-kodiert), war der Iterations-Zyklus extrem kurz: Datei ändern → App neu starten → Ergebnis sehen.

**Warum das cool ist:**
Die App ist backend-agnostisch — Nutzer können eigene OpenAI-kompatible Endpunkte, Modelle und API-Keys konfigurieren. In Kombination mit den austauschbaren Prompts kann die KI-Komponente ohne Code-Änderung vollständig getauscht werden.

---

## GLOBALE LAYOUT-REGELN (auf alle Slides anwenden)

1. **Bunte Kreise:** Alle Inhalte (Zahlen, Icons, Text) in bunten Kreisen müssen exakt vertikal + horizontal zentriert sein. Das gilt für Workout-, Übersicht- und Nutrition-Slides.
2. **Icons:** Verwende konsistente, klare Icons — kein Kraut-und-Rüben-Mix. SF Symbols (iOS-Stil) oder Material Icons funktionieren gut.
3. **Stil:** Vorhandenes Farbschema, Schriftart, Abstände — nicht anfassen.

---

## HINTERGRUNDWISSEN (nicht 1:1 auf Slides — nur als Kontext für dich)

**Was ist Expect/Actual in KMP?**
In Kotlin Multiplatform schreibt man mit `expect` eine Deklaration im gemeinsamen Code (ohne Implementierung — wie ein Interface). Für jede Zielplattform schreibt man eine `actual`-Implementierung mit dem plattformspezifischen Code. Dadurch bleibt die gesamte App-Logik in einer Codebasis, nur die echten Plattform-APIs (CoreLocation, BiometricPrompt, AVFoundation etc.) werden einmal pro Plattform implementiert.

**Genutzte Sensoren im Überblick:**
| Sensor | Wozu | iOS | Android |
|--------|------|-----|---------|
| Kamera | Barcode-Scan + Fortschrittsfotos | AVFoundation, PHPicker | MLKit, Camera API |
| Biometrie | Vault-Zugriff | LocalAuthentication | androidx.biometric |
| GPS | Geofencing (Gym-Erkennung) | CoreLocation | Google Play Services |

**Prompt-Fu:**
Tool für Prompt-Engineering und -Testing. Wurde genutzt um die System-Prompts für Workout- und Rezept-Generierung zu iterieren und zu evaluieren bevor sie in die App übernommen wurden.
