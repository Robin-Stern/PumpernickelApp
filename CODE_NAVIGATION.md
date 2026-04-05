# Code verstehen & navigieren

Am besten folgst du dem **Datenfluss** — von der UI über das ViewModel bis zum Repository.

---

## 1. Einstiegspunkt: `App.kt`
Hier siehst du, welche Screens es gibt und wie die ViewModels erstellt/injiziert werden. Gut zum Orientieren.

---

## 2. Dem Datenfluss folgen (am Beispiel "Lebensmittel speichern")

```
Nutzer tippt → Screen → Event → ViewModel → UseCase → Repository → Settings
```

**Screen** (`FoodEntryScreen.kt`)
→ Nutzer tippt auf "Speichern"
→ `viewModel.onEvent(FoodEntryEvent.OnSaveClicked)`

**ViewModel** (`FoodEntryViewModel.kt`, `save()`)
→ liest `_uiState.value`
→ ruft `addFood(name, calories, ...)` auf

**UseCase** (`AddFoodUseCase.kt`)
→ ruft zuerst `validate(...)` auf
→ dann `repository.saveFood(food)`

**Repository** (`FoodRepositoryImpl.kt`, `saveFood()`)
→ schreibt in `Settings` (lokaler Speicher)

---

## 3. Rückweg: Wie kommen Daten zurück in die UI?

```
Repository → ViewModel (StateFlow) → Screen (collectAsStateWithLifecycle)
```

Im ViewModel:
```kotlin
private val _foods = MutableStateFlow(loadFoods())  // Quelle
val filteredFoods: StateFlow<List<Food>> = combine(...) // abgeleitet
```

Im Screen:
```kotlin
val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
// → Compose zeichnet automatisch neu bei Änderung
```

---

## 4. Konkrete Navigationstipps in Android Studio

| Was du willst | Wie |
|---|---|
| Wo wird diese Funktion aufgerufen? | Rechtsklick → **Find Usages** (`Alt+F7`) |
| Zur Definition springen | `Ctrl+Click` oder `Ctrl+B` |
| Alle Implementierungen eines Interfaces | `Ctrl+Alt+B` (z.B. auf `FoodRepository`) |
| Datei schnell öffnen | `Ctrl+Shift+N` → Dateiname tippen |
| Symbol suchen | `Ctrl+Alt+Shift+N` → Klassenname/Funktion |
| Struktur der aktuellen Datei | `Alt+7` (Structure-Panel) |

---

## 5. Sinnvolle Lesereihenfolge für dieses Projekt

1. `Food.kt` — die Datenmodelle verstehen
2. `FoodRepository.kt` — welche Operationen gibt es?
3. `FoodRepositoryImpl.kt` — wie werden sie gespeichert?
4. `AddFoodUseCase.kt` / `UpdateFoodUseCase.kt` — Geschäftslogik
5. `FoodEntryViewModel.kt` — State & Events
6. `FoodEntryScreen.kt` — wie reagiert die UI darauf?

Domain → Data → UI. Nie anders herum, sonst verlierst du den Überblick.
