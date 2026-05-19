# Phase 23: AI UX + iOS-Parität — Discussion Log

**Date:** 2026-05-19
**Mode:** Interactive (default)
**Areas discussed:** Sets/Reps-Feld, iOS-Parität Scope, Background-Mini-Bar Design, Mini-Bar Tap-to-Expand

---

## Area 1: Sets/Reps-Feld im Formular

**Q: Was soll im Formular einstellbar sein?**
Options: Sets pro Übung (Stepper) / Sets UND Reps / Presets (Hypertrophie/Kraft/Endurance)
**Selected:** Sets pro Übung (Stepper, z.B. 2–5) — nur die Satzanzahl

**Q: Range für den Sets-Stepper?**
Options: 1–6, Default 3 / 2–5, Default 3 / Claude entscheidet
**Selected:** 1–6 Sätze, Default 3

**Q: Soll der Sets-Wert im Prompt-Text an das LLM übergeben werden?**
Options: Ja — Platzhalter im System-Prompt / Nein — nur UI-Hinweis
**Selected:** Ja — System-Prompt bekommt Platzhalter (minimale Änderung, kein Rewrite)

---

## Area 2: iOS-Parität Scope

**Q: Was fehlt dir auf iOS bei der Workout-Generierung konkret?**
Options: Nur Sets-Stepper (Rest ist parity) / Sets-Stepper + weitere Lücken
**Selected:** Sets-Stepper + schaue welche Lücken es noch gibt

*Code-Check ergab:* iOS hat bereits Zielmuskeln ✓, Split-Picker ✓, Übungsanzahl ✓, Cancel-Button ✓. Einzige Lücke = neuer Sets-Stepper.

---

## Area 3: Background-Mini-Bar Design

**Q: Wo soll die Mini-Bar erscheinen?**
Options: Global (jede App-Ansicht) / Nur Workout-Tab / Nur im Gen-Screen
**Selected:** Global — in jeder App-Ansicht sichtbar

**Q: Layout-Position?**
Options: Direkt über Tab-Bar (unten) / Floating-Pill oben (Dynamic Island-style)
**Selected:** Direkt über der Tab-Bar (unten), persistent

**Q: Welche Information zeigt die Bar an?**
Options: Typing-Indicator + Label / Fortschritt in Prozent/Sekunden
**Selected:** Typing-Indicator (3 Punkte) + Label ("KI generiert Workout…")

**Q: Was passiert wenn User Gen-Screen verlässt?**
Options: Generation läuft weiter im Hintergrund / Nav gesperrt / Verlassen = Abbruch (Status quo)
**Selected:** Generation läuft im Hintergrund weiter, Mini-Bar erscheint

---

## Area 4: Mini-Bar Tap-to-Expand Verhalten

**Q: Was sieht der User beim Tap auf die Mini-Bar?**
Options: Navigation zurück zum Gen-Screen / Expandiertes Panel (kein Screen-Wechsel)
**Selected:** Navigation zurück in den Gen-Screen

**Q: Was passiert wenn Generation fertig ist?**
Options: Mini-Bar wechselt zu "Workout bereit — tippen zum Ansehen" / Lokale Notification + Bar verschwindet
**Selected:** Mini-Bar wechselt zu "Workout bereit — tippen zum Ansehen" (bleibt bis User Preview gesehen hat)

**Q: Workout-KI und Rezept-KI?**
Options: Beide / Nur Workout-Generation
**Selected:** Beide — Label unterscheidet sich je nach Typ

---

## Deferred Ideas

- Reps-Stepper — User will nur Sets
- Sets/Reps-Presets (Hypertrophie/Kraft/Endurance)
- Streaming-Text direkt in der Mini-Bar
- Fehler-Banner in der Mini-Bar

---

## Claude's Discretion

- Animationsdetail des Typing-Indicators (Dot-Animation, Timing)
- Genaue Höhe der Mini-Bar (44–48dp/pt)
- Hintergrundfarbe (Surface-Variant o.ä.)
- iOS-HANDOFF.md Struktur und Detailtiefe
