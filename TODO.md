# Offene Verbesserungen

## 1. FoodSwipeToAddItem — Threshold-Problem
In `RecipeCreationScreen.kt` fehlen `positionalThreshold` und `velocityThreshold` beim Hinzufügen-Swipe.
Gleiche Lösung wie heute in `RecipeListScreen`:
```kotlin
positionalThreshold = { it * 0.3f },
velocityThreshold = { 100f },
```

## 2. Lebensmittel bearbeiten
Aktuell kann man ein gespeichertes Lebensmittel nur löschen, nicht editieren.
- Tap auf Karte öffnet Bearbeitungsformular mit vorausgefüllten Feldern
- `updateFood`-Methode in `FoodRepository` ergänzen
- `UpdateFoodUseCase` anlegen
- `FoodEntryViewModel` um Edit-Modus erweitern

## 3. Rezept-Gesamtmenge
In der Rezeptliste fehlt die Gesamtportion eines Rezepts (z.B. „ergibt 650 g").
Ermöglicht später die Berechnung von Portionsgrößen.
- Summe aller `amountGrams`-Werte im `RecipeCard` anzeigen

## 4. Eingabefelder kompakter anordnen
In `FoodEntryScreen` könnten Protein/Fett und Kohlenhydrate/Zucker jeweils
paarweise in einer `Row` nebeneinander stehen, um Scrollen zu reduzieren.

## 5. Suche im Lebensmittel-Screen
`FoodEntryScreen` hat keine Suchfunktion über die gespeicherten Lebensmittel.
- Suchfeld über der Liste ergänzen
- Filterlogik im `FoodEntryViewModel`

## 6. Repository als Singleton via Koin
In `App.kt` wird `FoodRepositoryImpl()` direkt instanziiert und bei jeder
Rekomposition neu erstellt. Koin ist bereits eingebunden aber ungenutzt.
- `AppModule` mit `single<FoodRepository> { FoodRepositoryImpl() }` anlegen
- ViewModels über Koin injizieren statt manuell in `App()` erstellen
