# Prompt-Testing Explained — Wie und warum der Eval-Harness funktioniert

Dieses Dokument ist eine didaktische End-to-End-Erklärung des Eval-Harness
unter `evals/`. Zielgruppe: Leser/innen ohne Vorkontext (Prof, Kommilitonen,
zukünftiges Ich in sechs Monaten). Nach 10–15 Minuten Lesezeit sollte klar
sein, was der Harness tut, warum er existiert, und wie der Iteration-Loop
zwischen Hypothese und Messung in der Praxis aussieht.

Alle Zahlen, Failure-Modes und die in Section 7 erzählte B.1-Episode stammen
aus `evals/MULTI-MODEL-RESULTS.md`. Code-Snippets stammen aus den echten
Eval-Dateien (`evals/cases/workout-validator.cjs`, `evals/cases/workout.json`,
`evals/prepare.mjs`).

---

## Warum überhaupt Prompt-Testing?

LLM-Outputs sind probabilistisch. Derselbe System-Prompt mit derselben User-
Eingabe und denselben Sampling-Parametern produziert nicht zwangsläufig denselben
Output-String — selbst bei `temperature: 0`. Das ist ein qualitativer Unterschied
zu klassischer Software: ein Unit-Test, der `assertEquals(add(2, 3), 5)` prüft,
testet einen deterministischen Ausdruck. Ein Eval, der `generateWorkout(...)`
prüft, kann nicht denselben String erwarten — wohl aber dieselbe akzeptable
**Form**: ein parsbares JSON, mit den erwarteten Feldern, in plausiblen
Wertebereichen.

Der naheliegende Fehler in einem KI-Projekt ohne Eval ist:

> Ich habe den Prompt einmal in der App ausprobiert. Es hat funktioniert.
> Also ist er gut.

Dieser Schluss ist nicht zulässig. Eine einzelne erfolgreiche Stichprobe sagt
über die Verteilung der Outputs fast nichts aus. Sie sagt nur: „dieser Output
war für diese Eingabe akzeptabel." Sie sagt nichts darüber, ob:

- derselbe Prompt bei derselben Eingabe in 9 von 10 Fällen funktioniert oder
  in 1 von 10,
- das gewählte Modell auf Kanten-Fälle (sehr kleine Workouts, ungewöhnliche
  Split-Kombinationen, niedrige Kalorien-Ziele) zuverlässig reagiert,
- eine Prompt-Änderung, die in einem Chat-Fenster gut aussieht, andere
  Cases regressiert.

Genau das misst der Eval-Harness. Er codifiziert die Akzeptanzkriterien
deterministisch in einem JavaScript-`validator`, fährt mehrere Test-Cases gegen
ein Modell, und zählt Pass/Fail. Das Ergebnis ist eine harte Zahl pro
`(Modell, Case)`-Paar — keine Bauchschmerz-Aussage „fühlt sich besser an".

Konkret hat das schon einmal verhindert, dass eine Prompt-Änderung eingespielt
wurde, die in einem Chat „besser klang", aber in der Messung eine Regression
zeigte. Siehe Section 7.

---

## Die Architektur

Der Harness hat eine wichtige strukturelle Eigenschaft: **die Eval-Pipeline und
die App lesen denselben System-Prompt**. Es gibt keine zweite Kopie, die in
der Eval erfolgreich passiert und in der App divergiert.

Die Quelle der Wahrheit ist:

```
shared/src/commonMain/resources/workout-system-prompt.md
shared/src/commonMain/resources/recipe-system-prompt.md
```

Beim Build der App landen diese Dateien im KMP-Common-Resource-Bundle und werden
zur Runtime gelesen. Für den Eval konvertiert `evals/prepare.mjs` dieselben
`.md`-Dateien in `promptfoo`-Chat-Templates (`prompts/workout-system.json`,
`prompts/recipe-system.json`) — mit `{locale}` → `de` substituiert. `prepare.mjs`
wird vor jedem Eval-Run aufgerufen; das stellt sicher, dass die Eval-Pipeline
nie veraltete Prompts misst.

```
┌────────────────────────────────────────────────┐
│  shared/.../workout-system-prompt.md           │  ← Single Source of Truth
└──────────────┬─────────────────┬───────────────┘
               │                 │
               │                 │
       ┌───────▼──────┐   ┌──────▼─────────────┐
       │ App-Build    │   │ evals/prepare.mjs  │
       │ (KMP-Bundle) │   │ (subst. {locale})  │
       └───────┬──────┘   └──────┬─────────────┘
               │                 │
               ▼                 ▼
       ┌──────────────┐   ┌────────────────────────┐
       │ Runtime in   │   │ prompts/workout-system │
       │ App (User    │   │ .json (chat template)  │
       │ macht Anfrage)│  └──────┬─────────────────┘
       └──────────────┘          │
                                 ▼
                          ┌────────────────────┐
                          │ promptfoo run      │
                          │ (provider call)    │
                          └──────┬─────────────┘
                                 ▼
                          ┌────────────────────┐
                          │ validator (cjs)    │
                          └──────┬─────────────┘
                                 ▼
                              pass / fail
```

Konsequenz: wenn der Eval grünes Licht gibt, hat die App die exakt selbe
Prompt-Bytes durch ein konkretes Modell laufen lassen. Es gibt keinen Pfad,
auf dem die Eval optimistisch ist und die App pessimistisch — beide lesen
dieselbe Datei.

---

## Anatomie eines Eval-Runs

Ein einzelner Eval-Run besteht aus drei Zutaten, die `promptfoo` zusammenführt:

**1. Prompt-Template** (`evals/prompts/workout-system.json`, generiert durch
`prepare.mjs`): eine zweistufige Chat-Konversation aus `system` (der Inhalt
des `.md`-Prompts) und `user` (eine `{{userMessage}}`-Variable, die pro
Test-Case substituiert wird). Generiert wird das so:

```js
// evals/prepare.mjs
const md = readFileSync(resolve(sharedResources, src), "utf8");
const systemContent = md.replaceAll("{locale}", "de");
const chat = [
  { role: "system", content: systemContent },
  { role: "user", content: "{{userMessage}}" }
];
writeFileSync(resolve(here, dst), JSON.stringify(chat, null, 2), "utf8");
```

**2. Test-Cases** (`evals/cases/workout.json`): ein Array von Cases. Jedes
Element hat eine `description` und ein `vars`-Objekt mit den Variablen,
die promptfoo in das Prompt-Template substituiert. Auszug (gekürzt):

```json
{
  "description": "Upper/Lower split · 6 per template",
  "vars": {
    "templatesExpected": 2,
    "exerciseCount": 6,
    "splitStyle": "UPPER_LOWER",
    "userMessage": "targetMuscles: chest, shoulders, lats, biceps, triceps, quadriceps, hamstrings, glutes\nexerciseCount: 6\nsplitStyle: UPPER_LOWER\ntemplatesExpected: 2\n\nexistingExercises:\n- ..."
  }
}
```

`templatesExpected` und `exerciseCount` sind hier doppelt vorhanden: einmal als
Top-Level-`vars` (so dass der validator sie ohne Parsen lesen kann) und einmal
eingebettet in `userMessage`, weil das Modell sie nur dort sieht.

**3. Validator** (`evals/cases/workout-validator.cjs`): eine JavaScript-Funktion,
die das `output`-String des Modells und den `context` mit `vars` bekommt und
ein `{ pass, score, reason }`-Objekt zurückgibt. Details dazu in Section 4.

Der Datenfluss eines einzelnen Test-Runs:

```
┌─────────────┐
│ Test-Case   │   vars = { templatesExpected: 2, ... }
│ (workout    │
│  .json)     │
└──────┬──────┘
       │ vars substitution
       ▼
┌─────────────────────────┐
│ Chat-Template           │   system = workout-system-prompt.md content
│ (prompts/*.json)        │   user   = vars.userMessage
└──────┬──────────────────┘
       │ promptfoo provider call (togetherai:google/gemma-4-31B-it, ...)
       ▼
┌─────────────────┐
│ Model API call  │   POST /v1/chat/completions
└──────┬──────────┘
       │ raw response string
       ▼
┌─────────────────────────┐
│ validator (workout-     │   extractJsonObject → JSON.parse → schema checks
│ validator.cjs)          │
└──────┬──────────────────┘
       ▼
   { pass: bool, score: 0|1, reason: "..." }
```

Promptfoo aggregiert die `pass`-Felder pro Suite und schreibt das Ergebnis in
`runs/*.json`. Der Sweep-Treiber `evals/multi-model.mjs` führt das für mehrere
Modelle und Repetitionen aus und aggregiert Mean + Stdev pro `(Modell, Suite)`.

---

## Validator-Pattern: Code statt LLM-as-Judge

Bei der Frage „wie entscheide ich, ob ein LLM-Output gut ist?" gibt es im
Wesentlichen zwei Schulen:

**(a) LLM-as-Judge.** Ein zweites (typischerweise stärkeres) Modell bewertet
die Antwort. Die Bewertung ist meist eine freie Skala (1–5, gut/mittel/schlecht).
Das ist attraktiv, weil semantische Qualität schwer formal auszudrücken ist —
ein zweites LLM „versteht" den Output ähnlich wie ein Mensch.

**(b) Deterministischer Code-Validator.** Eine Funktion in einer
General-Purpose-Sprache prüft das Output gegen ein Schema und konkrete
Wertebereiche. Pass/Fail wird mechanisch berechnet.

Wir nutzen **(b)**. Begründung:

- **Reproduzierbar.** Derselbe Output erzeugt immer dasselbe Urteil. Bei
  LLM-as-Judge variiert das Urteil zwischen Runs.
- **Schnell und kostenlos.** Ein zweiter Modell-Call pro Test verdoppelt
  Latenz und Token-Kosten. Bei 5 Modellen × 2 Suites × 3 Reps × 8 Cases
  läppert sich das.
- **Risiko-niedrig.** LLM-as-Judge schiebt das Konsistenzproblem nur
  weiter — jetzt muss der Judge konsistent sein. Wer prüft den Prüfer?
- **Codifiziert das Akzeptanzkriterium.** Was der validator prüft, prüft
  auch die App-Runtime-Validierung in `WorkoutAiUseCase.validateResponse()`.
  Test und Production teilen die Definition von „gültig".

Auszug aus `evals/cases/workout-validator.cjs` — was genau gecheckt wird:

```js
module.exports = function (output, ctx) {
  const vars = ctx && ctx.vars ? ctx.vars : {};
  const cleaned = extractJsonObject(String(output));
  let parsed;
  try { parsed = JSON.parse(cleaned); }
  catch (e) {
    return { pass: false, score: 0,
             reason: `JSON parse failed: ${e.message}\n\n...` };
  }

  if (parsed.refusal) {
    return { pass: false, score: 0, reason: `LLM refused: ${parsed.refusal}` };
  }
  if (!Array.isArray(parsed.templates)) {
    return { pass: false, score: 0,
             reason: "templates is not an array (and no refusal field)" };
  }
  if (parsed.templates.length !== vars.templatesExpected) {
    return { pass: false, score: 0,
             reason: `expected ${vars.templatesExpected} templates, got ${parsed.templates.length}` };
  }
  for (const [i, t] of parsed.templates.entries()) {
    if (!t.name || typeof t.name !== "string")
      return { pass: false, score: 0, reason: `template[${i}].name missing` };
    if (!Array.isArray(t.exercises) || t.exercises.length === 0)
      return { pass: false, score: 0, reason: `template[${i}].exercises empty` };
    if (t.exercises.length !== vars.exerciseCount)
      return { pass: false, score: 0,
               reason: `template[${i}] has ${t.exercises.length} exercises, expected ${vars.exerciseCount}` };
    for (const [j, e] of t.exercises.entries()) {
      // targetSets ∈ 1..10, targetReps ∈ 1..50, restPeriodSec ∈ 0..600, ...
    }
  }
  // inlineNewExercises checks ...
  return { pass: true, score: 1, reason: "all checks passed" };
};
```

Die Pointe: jede `return { pass: false, ... }`-Zeile ist ein konkretes
Akzeptanzkriterium aus dem System-Prompt — `templates` ist ein Array, das
genau `templatesExpected` Einträge hat; jedes Template hat einen `name`-String
und genau `exerciseCount` Übungen; jede Übung hat einen `exerciseName` und
Werte in plausiblen Bereichen. Wenn der Output diese Kriterien nicht erfüllt,
würde die App ihn zur Runtime ohnehin verwerfen. Der Eval prüft also genau
**das**, was der User in der App als „funktioniert" oder „funktioniert nicht"
erleben würde.

`extractJsonObject` (oben im selben File) ist ein kleiner Helfer, der mit
Reasoning-Modellen umgeht: diese geben oft einen `Thinking: ...`-Trace vor
dem eigentlichen JSON-Output aus. Der Helfer extrahiert das längste balanced
`{...}`-Block aus dem Roh-String. Details siehe Datei.

---

## Der Iteration-Loop

Der Eval-Harness ist kein einmaliger Smoke-Test. Er ist ein Werkzeug für einen
wiederkehrenden Loop:

```
┌────────────────────┐
│ 1. Hypothese       │  „Wenn ich Satz X am Ende des Prompts ergänze,
│   (Mensch)         │   sollte Modell Y Failure-Mode Z weniger oft zeigen."
└──────────┬─────────┘
           │
           ▼
┌────────────────────┐
│ 2. Edit            │  → ändere shared/.../workout-system-prompt.md
│   (Mensch)         │
└──────────┬─────────┘
           │
           ▼
┌────────────────────┐
│ 3. Eval-Run        │  → node multi-model.mjs --suite workout
│   (Code)           │
└──────────┬─────────┘
           │
           ▼
┌────────────────────┐
│ 4. Diagnose        │  Welcher Case failt? Mit welcher reason-Message?
│   (Mensch + Code)  │  Hat sich Δ mean / Δ stdev pro Modell bewegt?
└──────────┬─────────┘
           │
           ▼
┌────────────────────┐
│ 5. Decision        │  → commit (Hypothese bestätigt),
│   (Mensch)         │  → revert (Hypothese widerlegt),
│                    │  → weiter messen (zu wenig Signal)
└──────────┬─────────┘
           │
           └──────────► (zurück zu 1.)
```

Wer macht was, ist hier explizit:

- **Mensch** formuliert Hypothese, entscheidet, was im Prompt geändert wird,
  und interpretiert das Ergebnis.
- **Code** (Validator + Sweep-Runner) misst, ohne semantisches Urteil.
- **LLM** produziert den Output, ohne sich selbst zu bewerten.

Kein Schritt im Loop ist „LLM bewertet sich selbst". Genau diese Trennung ist
das Wertversprechen — die Messung ist unbeeinflusst davon, wie überzeugend
ein bestimmter Output sprachlich wirkt.

---

## Die vier Failure-Modes, die wir gefunden haben

In der Multi-Model-Konsistenz-Studie (5 Modelle × 2 Suites × 3 Reps,
~309k Tokens, siehe `MULTI-MODEL-RESULTS.md`) sind strukturell vier
unterschiedliche Arten von Fehlern aufgetaucht. Sie verlangen unterschiedliche
Fixes — eine Prompt-Änderung, die A löst, muss B nicht lösen.

### A — Reasoning-Trace-Token-Starvation

**Wer:** `openai/gpt-oss-20b` und partiell `openai/gpt-oss-120b`, am
stärksten in der Recipe-Suite.

**Was passiert:** Together's `togetherai`-Provider konkateniert
`"Thinking: " + reasoning + content` in den Response-String. Mit
`max_tokens: 4096` zählt das *gesamte* Budget — inklusive des internen
Reasoning-Traces. Bei langen Recipe-Prompts verbraucht `gpt-oss-20b`
regelmäßig das gesamte Budget im Reasoning, bevor überhaupt ein JSON-Token
emittiert wird. Output: `Thinking: We need...` mitten im Gedanken
abgeschnitten, kein `{...}`-Block.

**Validator-Reaktion:** `JSON parse failed: Unexpected token 'T'`. Der
bracket-matcher findet kein balanced `{...}`. Das ist **kein Validator-Bug** —
es ist ein Budget-Problem spezifisch für Reasoning-Modelle bei dieser Task-Größe.

**Zahl:** `gpt-oss-20b` Recipe: 0/4 in Run 1 (broken-model guard stoppt
Reps 2–3). `gpt-oss-120b` Recipe: 2.67/4 mean, σ 0.47.

### B — Schema-Shape-Inkonsistenz

**Wer:** `openai/gpt-oss-120b` in der Workout-Suite.

**Was passiert:** Der Prompt sagt explizit „immer `{ "templates": [...] }`
zurückgeben, auch bei `templatesExpected = 1`." Auf 2 von 3 Runs liefert
gpt-oss-120b stattdessen `{ "name": "...", "exercises": [...] }` — ein bare
Template-Objekt ohne `templates`-Wrapper. Es ist gültiges JSON, aber das
falsche Schema.

**Validator-Reaktion:** `templates is not an array (and no refusal field)`.

**Zahl:** `gpt-oss-120b` Workout mean 2.67/4, σ 0.47. Der Fix-Versuch (B.1,
siehe Section 7) zeigt, dass „mehr vom Schema im Prompt wiederholen" nicht
trivial hilft.

### C — Mode-Collapse

**Wer:** `Qwen/Qwen3-235B-A22B-Instruct-2507-tput` in der Recipe-Suite.

**Was passiert:** Qwen produziert valides JSON, aber für drei sehr
unterschiedliche Macro-Targets (600 kcal, 1100 kcal, 2000 kcal) immer dasselbe
Rezept: „Hähnchen-Reis-Bowl mit Brokkoli und Avocado", mit re-skalierten
Mengen. Das Ergebnis liegt mehr als 15 % daneben (z. B. 731 kcal vs. 600 kcal,
73 g vs. 55 g Protein). Das Modell behandelt „produziere ein Rezept" als
dominante Constraint und „treffe diese Macros" als weichen Hinweis.

**Validator-Reaktion:** Macros außerhalb ±15 %-Toleranz; Plausibilitäts-Check
schlägt fehl.

**Zahl:** `Qwen3-235B` Recipe: 0/4 in Run 1 (broken-model guard stoppt
weitere Reps). Wichtig: in der Workout-Suite ist Qwen3 perfekt (4/4, σ 0) —
Mode-Collapse ist task-spezifisch, nicht modellweit.

### D — Macro-Arithmetik-Schwäche

**Wer:** `meta-llama/Llama-3.3-70B-Instruct-Turbo` in der Recipe-Suite.

**Was passiert:** Llama produziert sinnvolle, abwechslungsreiche Rezepte
(kein Mode-Collapse), aber die Macros stimmen nicht: systematisch zu wenig
kcal, zu viel Protein. Konkrete Werte aus den Runs: `protein 47g vs 30g
target`, `kcal 1258 vs 2000 target`. Das Modell wählt plausible Zutaten,
führt aber die Multi-Ingredient-Summe nicht präzise genug aus, um in
±15 % der Targets zu landen.

**Validator-Reaktion:** Macro-Toleranz-Check schlägt fehl mit konkretem
Δ-Wert.

**Zahl:** `Llama-3.3-70B-Turbo` Recipe: mean 0.33/4, σ 0.47 — Llama-3.3 ist
in der Workout-Suite perfekt (4/4), aber in Recipe nahezu unbrauchbar. Auch
das ist task-spezifisch.

### Randnotiz — Llama 4 Maverick

`meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8` lieferte über mehrere
Retry-Runden konsistent HTTP 503 auf Together's Serverless-Tier. Ursache
unbekannt — möglicherweise ein Deployment-Outage am 2026-05-10. Im Report
als „Together-Tier-Outage, Ursache unbekannt" markiert; statt Maverick wurde
Llama-3.3-70B-Turbo als Llama-Repräsentant gemessen.

---

## Echtes Beispiel: die B.1-Lehre

Die B.1-Episode ist dokumentiert in `MULTI-MODEL-RESULTS.md` (Abschnitt
„B.1 — Workout-Prompt Wrapper-Restatement"). Sie ist das beste verfügbare
Argument für die Existenz des Harness.

**Hypothese.** Failure-Mode B (Schema-Shape-Inkonsistenz bei `gpt-oss-120b`)
trat besonders bei `templatesExpected = 1` auf, also wenn das Modell nur ein
einzelnes Template emittieren sollte. Plausible Diagnose: der Prompt erwähnt
die Array-Wrapper-Regel zwar am Anfang, aber das Modell „vergisst" sie auf
dem Weg zur Ausgabe. Vermutung: eine Wiederholung am Ende des Prompts
(im `## Final reminder`-Block) schärft die Constraint.

**Edit.** Commit `54167d7` hängte zwei Sätze an den `## Final reminder`-Block
in `shared/src/commonMain/resources/workout-system-prompt.md` an:

> The top-level shape is always `{ "templates": [...], "inlineNewExercises": [...] }`.
> Even if `templatesExpected = 1`, `templates` MUST still be an array
> containing one object — NEVER a bare template object.

**Messung.** Workout-Suite × 5 Modelle × 3 Reps, 108k Tokens.

| Modell | Baseline | B.1 | Δ mean | Verdict |
|---|---|---|---|---|
| `gpt-oss-20b` | 3.00 (σ 0.82) | 3.33 (σ 0.47) | +0.33 | improved |
| `gemma-4-31B-it` | 4.00 (σ 0.00) | 4.00 (σ 0.00) | 0 | unchanged (perfekt) |
| `gpt-oss-120b` | 2.67 (σ 0.47) | **2.00** (σ 0.00) | **-0.67** | **REGRESSED** |
| `Qwen3-235B` | 4.00 (σ 0.00) | 4.00 (σ 0.00) | 0 | unchanged (perfekt) |

Der Edit hat **nicht** gefixt, was er fixen sollte. Die Single-Template-Cases
auf `gpt-oss-120b` wurden schlechter — von 1/3 auf 0/3 Fails. Der Validator
liefert konsistent `templates is not an array (and no refusal field)`; das
Modell emittiert weiter ein bare Template-Objekt.

Plausibler Mechanismus: der neue Satz nennt `templatesExpected = 1` als
gefährlichen Fall. Ein Reasoning-Modell, das Phrasen aus dem System-Prompt
in seinen Thinking-Trace pattern-matched, wird durch das explizite Hervorheben
*stärker* auf den Single-Template-Branch fokussiert, behandelt das als „die
wichtige Form" und emittiert sie direkt. Gemeint als De-Biasing, gewirkt als
Salience-Prime.

**Entscheidung.** Edit zurückgedreht — **revert 501c31d**. Begründung: das
Produktionsmodell (`gemma-4-31B-it`) ist nicht betroffen (weiter 12/12), aber
die Hypothese hinter B.1 hält der Messung nicht stand. Es gibt keinen Grund,
einen Edit committed zu lassen, der seine Begründung verloren hat und ein
anderes Modell schlechter macht.

**Lehre.** Ohne den Harness wäre dieser Edit unbemerkt committed worden. Im
Chat-Fenster, ein- oder zweimal getestet, hätte er „besser geklungen". Erst
die 15-Run-Messung über 5 Modelle zeigt das gemischte Bild. Genau dafür
existiert das System: nicht um „den richtigen Prompt zu finden", sondern um
Hypothesen über Prompts widerlegbar zu machen.

---

## Status und nächste Schritte

**Produktions-Modell.** Aus der Multi-Model-Studie hat sich
`google/gemma-4-31B-it` als das einzige Modell qualifiziert, das in beiden
Suites (Workout und Recipe) jede Repetition fehlerfrei bestanden hat —
12/12 mit stdev 0, bei moderater Latenz (~9–14 s pro Test in der Baseline)
und auf bereits bezahlten Together-Credits. Die App-Defaults zeigen
mittlerweile `gemma-4-31B-it` als empfohlenes Modell (siehe Quick-Task
`260510-w9h`).

**Laufend — B.2 (Recipe Worked-Example).** Failure-Modes C und D (Mode-Collapse
und Macro-Arithmetik) suggerieren beide, dass die Recipe-Prompt zu wenig
explizite Macro-Buchhaltung verlangt. Quick-Task `260511-117` hat ein
„worked example" in `recipe-system-prompt.md` ergänzt: eine konkrete
Beispielrechnung, die zeigt, wie Zutaten gewählt werden, um ein Macro-Ziel
in ±15 % zu treffen. Der zugehörige Eval-Run wird gerade gemessen — eine
Aussage über das Ergebnis liegt zum Zeitpunkt dieses Dokuments nicht vor.

**Geplant — B.3 (max_tokens Tuning).** Failure-Mode A (Reasoning-Trace-Starvation)
ist nicht prompt-basiert. Mögliche Fixes: `max_tokens` auf 8192+ heben,
`reasoning: { effort: "low" }` setzen, oder Reasoning-Modelle aus dem
Default-Set ausschließen. Wird gemessen, sobald B.2 abgeschlossen ist.

**Geplant — B.4 (Refusal-Hook).** Der validator akzeptiert `{ "refusal": "..." }`
als explizite Antwort, aber nur `gemma-4-31B-it` benutzt das Feld in
Edge-Cases. Eine Verschärfung im Prompt („wenn du die Constraints nicht
erfüllen kannst, liefere `refusal` — nicht ein partielles oder Off-Target-Ergebnis")
soll das System gegen Failure-Modes C/D härter machen.

**Limitations — ehrlich.** Vier Tests pro Suite sind dünn. Die Suite deckt
Standard-Fälle ab (1–3 Templates, übliche Split-Stile, Recipe in vier
Macro-Ranges), aber nicht den Long-Tail (50+ verfügbare Übungen, ungewöhnliche
Sprachen, Recipe mit nullbasierten Restmacros). Coverage-Ausbau steht aus
und ist eine eigene Aufgabe; die aktuelle Suite ist „gerade ausreichend,
um Modelle voneinander zu unterscheiden", nicht „erschöpfend".

---

## Was du brauchst, um's selbst auszuprobieren

Reproduktion in fünf Schritten:

1. **API-Key besorgen.** Together AI bietet ein kostenloses Tier. Account
   anlegen unter `https://api.together.xyz`, dann unter „API Keys" einen
   Key generieren.

   ```bash
   export TOGETHER_API_KEY=<dein-key>
   ```

2. **In das eval-Verzeichnis wechseln.** Alle nachfolgenden Befehle gehen
   davon aus, dass das Working-Directory `evals/` ist.

   ```bash
   cd evals
   ```

3. **Dependencies installieren** (nur beim ersten Mal):

   ```bash
   npm install
   ```

4. **Smoke-Test fahren.** Ein Modell, ein Case — bestätigt, dass der
   API-Key und die Pipeline funktionieren:

   ```bash
   node multi-model.mjs --smoke
   ```

5. **Volle Workout-Suite gegen das aktuelle Default-Modell:**

   ```bash
   node multi-model.mjs --suite workout --output runs/aggregate-test.json
   ```

   Ausgabe: `runs/aggregate-test.json` mit per-Test pass/fail und einer
   Mean/Stdev-Aggregation. Das HTML-Report lässt sich danach mit
   `npx promptfoo view` öffnen.

Wichtige Hinweise:

- `--no-cache` ist in `multi-model.mjs` für Varianzmessungen schon gesetzt.
  Ohne diesen Flag würde promptfoo dieselbe Antwort dreimal aus dem Cache
  servieren und falsche 0-Varianz melden.
- Die Validators (`*.cjs`) MÜSSEN `.cjs`-Endung haben, weil
  `evals/package.json` `"type": "module"` setzt. Eine `.js`-Datei würde als
  ESM geladen und `module.exports` wäre kein No-Op-Hook mehr.
- Token-Verbrauch ist nicht trivial. Ein voller 5-Modelle-×-2-Suites-×-3-Reps-
  Sweep konsumiert in der Größenordnung von 300k Tokens (siehe Methodology in
  `MULTI-MODEL-RESULTS.md`). Smoke-Tests vorher fahren spart Geld.

Detaillierte Beschreibung der Files, Test-Cases und Switch-Provider-Anleitung
in `evals/README.md`. Der vollständige Ergebnisbericht der Multi-Model-Studie
in `MULTI-MODEL-RESULTS.md`. Briefing-Kontext für diesen Sweep in
`MULTI-MODEL-BRIEF.md`.
