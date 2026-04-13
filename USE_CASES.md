# Use Case Stories - PumpernickelApp

Diese Datei beschreibt die funktionalen Anforderungen und das gewünschte Verhalten der App aus Sicht des Anwenders.

## 1. Lebensmittel (Food Management)
### Use Cases:
- **Lebensmittel CRUD:** Als Benutzer möchte ich Lebensmittel anlegen, lesen, bearbeiten und löschen können. Löschen erfolgt durch Wischen nach links. Ist das Lebensmittel favorit, dann kommt zuerst eine Abfrage, ob man den Status als Favorit löschen möchte. 
    Diese Interaktionen sollen über die Lebensmittelkarte möglich sein, die im Lebensmittelreiter zu finden sind.  
- **Favoriten:** Ich möchte Lebensmittel als Favoriten markieren können, um schneller darauf zuzugreifen. Die markierung erfolgt durch Wischen nach Rechts. 
- **Kombinieren:** Ich möchte mehrere Lebensmittel auswählen und zu einem Rezept zusammenfassen. Dazu gehe ich in der APp in den Lebensmittelbereich und starte dort den Prozess ein Rezept zu erstellen.
Diese Ansicht zeigt mir die zuletzt hinzugefügten Lebensmittel und die Kalorien der Gesamtmenge des Rezeptes. Ich kann für jede Zutat eine Mengenangabe im Rezept erstellen.
- **Durchsuchen:** Ich möchte meine Lebensmittel-Datenbank nach Namen durchsuchen. Dazu gibt es eine Suchfunktion im Lebensmittelteil der App
- **Barcodescanner:** Ich möchte Lebensmittel schnell erfassen können, indem ich ihren Barcode scanne. Das soll über einen Button im Lebenmittelbereich als auch über einen Button in der Übersicht möglich sein(sofern die Übersicht entsprechend konfiguriert. 
- **Wassertracker** Als neues Feature?
## 2. Rezepte (Recipe Management)
### Use Cases:
- **Ansicht:** Wenn ich die Rezepte Ansicht öffne, will ich die Favoriten sehen. Wenn es keine Favoriten gibt, sollen die zuletzt hinzugefügten Rezepte angezeigt werden. 
- **Rezepte CRUD:** Ich möchte Rezepte erstellen, anzeigen, bearbeiten und löschen können.  Löschen erfolgt durch Wischen nach links. Ist das Rezept Favorit, dann kommt zuerst eine Abfrage, ob man den Status als Favorit löschen möchte.
- **Favoriten:** Beliebte Rezepte sollen als Favoriten markiert werden können.
- **Portionsrechner:** Ich möchte die Anzahl der Portionen anpassen können, woraufhin die Zutatenmengen automatisch neu berechnet werden.
- **Durchsuchen:** Rezepte sollen über eine Suchfunktion leicht findbar sein.
- **KI-Generierung:** Ich möchte Rezepte automatisch generieren lassen, basierend auf meinen verbleibenden Restkalorien für den Tag.

## 3. Übungen & Training (Exercise Tracking)
### Use Cases:
- **Übung CRUD:** Ich möchte neue Übungen manuell zur Liste/Trainingsliste hinzufügen. Übungen definieren oder ändern.
- **Übung favorisieren:** Ich möchte wie bei den Rezepten und Lebensmitteln Übungen durch Wischen nach rechts favorisieren können. öschen erfolgt durch Wischen nach links. Ist die Übung favorit, dann kommt zuerst eine Abfrage, ob man den Status als Favorit löschen möchte.
- **KI-Übungsverlauf:** Ich möchte mir Übungen von der KI generieren lassen.
- **Leistungstracking:** Pro Übung möchte ich die Wiederholungen und das verwendete Gewicht protokollieren.
- **Persönliche Rekorde (PR):** Ich möchte meine persönlichen Bestleistungen pro Übung einsehen können.
- **Trainingsverlauf & Analyse:**
    - Einsehen der Trainingsdauer.
    - Anzahl der durchgeführten Übungen pro Einheit.
    - Berechnung des bewegten Gesamtgewichts (Volume).
    - Überprüfung der Regelmäßigkeit (Kalender-Ansicht/Statistik).
- **Nutzungsstatistik:** Anzeige von häufig durchgeführten sowie selten gemachten Übungen. Sortierung nach Häufigkeit möglich (entweder durch tippen oder extra Button))

## 4. Tagestracking (Daily Log)
### Use Cases:
- **Interaktives Log:** Ich möchte Mahlzeiten und Übungen in einer Tagesübersicht sehen.
- **Status-Toggle:** Durch einfaches Klicken auf einen Eintrag möchte ich diesen als erledigt markieren (Toggle-Funktion).
- **KI-Chat:**
    - Ich möchte mir Rezepte oder Mahlzeiten passend zu meinen Makros vorschlagen lassen.
    - Ich möchte Übungen eintragen lassen.
    - Ich möchte komplette Workouts automatisch erstellen lassen.
- **Übersicht:** Die Standardansicht soll mir mein Tagesbudget an Kalorien und Macros zeigen. Ein Tippen auf die Zahl soll wechseln zwischen einer Ansicht,
die mir sagt, wie viel ich verbraucht habe (Budget - Verbraucht in kCal = positive Betrachtung) oder wie viel ich von meinem Budget noch übrig habe (Verbraucht-Budget in kCal = Negative Betrachtung).
- In der Übersicht soll ein Button sein, der (frei konfigurierbar?) einen Schnellzugriff der mir eine der KI Funktionen anbietet oder den BarcodeScanner ohne den Übersichtsbereich zu verlassen