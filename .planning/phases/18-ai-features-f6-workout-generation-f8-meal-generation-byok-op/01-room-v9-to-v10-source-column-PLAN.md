---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
autonomous: true
requirements:
  - REQ-AI-01
  - REQ-AI-04
user_setup: []

must_haves:
  truths:
    - "AppDatabase reports version 10 with AutoMigration(9, 10) registered"
    - "Each of the 4 affected entities has a nullable source column added without affecting existing rows"
    - "Domain models (WorkoutTemplate, Exercise, Recipe, Food) carry the source field through to/from entity mappers"
    - "Existing entries continue to load (source = null is treated as USER on read by downstream code)"
    - "App build succeeds and existing tests pass against the new schema"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt"
      provides: "version = 10 + AutoMigration(9, 10)"
      contains: "version = 10"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt"
      provides: "Nullable source column"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt"
      provides: "Nullable source column"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt"
      provides: "Nullable source column"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt"
      provides: "Nullable source column"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt"
      provides: "WorkoutTemplate.source field + propagated mapper"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt"
      provides: "Exercise.source field + propagated mapper"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt"
      provides: "Recipe.source field"
      contains: "val source: String? = null"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt"
      provides: "Food.source field"
      contains: "val source: String? = null"
  key_links:
    - from: "AppDatabase.kt"
      to: "AutoMigration(9, 10)"
      via: "Room annotation"
      pattern: "AutoMigration\\(from = 9, to = 10\\)"
    - from: "ExerciseRepositoryImpl.createExercise"
      to: "ExerciseEntity"
      via: "constructor"
      pattern: "ExerciseEntity\\("
---

<objective>
Bump Room database from v9 to v10 with an additive AutoMigration that adds a nullable `source: String?` column to four entities (WorkoutTemplateEntity, ExerciseEntity, RecipeEntity, FoodEntity) so AI-authored entries become taggable. Propagate the field through the corresponding domain models and entity↔domain mappers. Existing rows keep `source = null` (treated as USER on read).

Purpose: Provenance tracking for AI-authored entries without losing existing data — load-bearing for D-18-09 (LLM authoring rights for Exercise + Food) and D-18-11 (provenance via source column). All downstream AI plans depend on this schema.
Output: Schema v10, four migrated entities, four propagated domain models, ExerciseRepository createExercise wired through (its constructor builds the entity inline).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md

<interfaces>
<!-- Existing entities that MUST be modified additively. Source: Read tool output of these files. -->

From AppDatabase.kt (current state):
```kotlin
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        ActiveSessionEntity::class,
        ActiveSessionSetEntity::class,
        CompletedWorkoutEntity::class,
        CompletedWorkoutExerciseEntity::class,
        CompletedWorkoutSetEntity::class,
        FoodEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ConsumptionEntryEntity::class,
        XpLedgerEntity::class,
        AchievementStateEntity::class,
        RankStateEntity::class,
        ProgressPictureEntity::class
    ],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9)
    ]
)
```

From WorkoutTemplateEntity.kt (current state, 12 lines):
```kotlin
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

From ExerciseEntity.kt (current state):
```kotlin
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val force: String?,
    val level: String,
    val mechanic: String?,
    val equipment: String?,
    val category: String,
    val instructions: String,
    val images: String,
    val isCustom: Boolean = false,
    val primaryMuscles: String,
    val secondaryMuscles: String
)
```

From RecipeEntity.kt:
```kotlin
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isFavorite: Boolean = false
)
```

From FoodEntity.kt:
```kotlin
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double,
    val unit: String,
    val isRecipe: Boolean = false,
    val barcode: String? = null
)
```

From WorkoutTemplate.kt domain mapper (existing toDomain):
```kotlin
data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<TemplateExercise> = emptyList()
)

fun WorkoutTemplateEntity.toDomain(
    exercises: List<TemplateExercise> = emptyList()
) = WorkoutTemplate(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    exercises = exercises
)
```

From ExerciseRepository.createExercise (constructs ExerciseEntity inline — must add source = null to keep existing user-created exercises untagged):
```kotlin
override suspend fun createExercise(exercise: Exercise) {
    val entity = ExerciseEntity(
        id = exercise.id,
        name = exercise.name,
        // ... existing fields
        primaryMuscles = ...,
        secondaryMuscles = ...
    )
    dao.insert(entity)
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add nullable source column to all 4 entities</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
    Append a single new last constructor parameter to each of the 4 entity data classes:
    `val source: String? = null` with a one-line trailing comment `// "USER" | "AI" — null treated as USER on read (D-18-11)`.

    Concrete diffs (apply exactly):

    1. WorkoutTemplateEntity.kt — append after `val updatedAt: Long`:
       `,\n    val source: String? = null  // "USER" | "AI" — null treated as USER on read (D-18-11)`
       (Note the leading comma to terminate the previous line.)

    2. ExerciseEntity.kt — append after `val secondaryMuscles: String`:
       `,\n    val source: String? = null  // "USER" | "AI" — null treated as USER on read (D-18-11)`

    3. RecipeEntity.kt — append after `val isFavorite: Boolean = false`:
       `,\n    val source: String? = null  // "USER" | "AI" — null treated as USER on read (D-18-11)`

    4. FoodEntity.kt — append after `val barcode: String? = null`:
       `,\n    val source: String? = null  // "USER" | "AI" — null treated as USER on read (D-18-11)`

    Do NOT add new imports — `String?` is in the default import set. Do NOT change column ordering for existing fields. Do NOT add any annotation on the new field (no `@ColumnInfo`, no defaults via Room — the Kotlin default `= null` is what Room AutoMigration uses to add the column as `NULL` SQL).
  </action>
  <verify>
    <automated>grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt | grep -v ':0$' | wc -l | tr -d ' '</automated>
  </verify>
  <acceptance_criteria>
    - `grep -l "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` returns the file path (1 line)
    - `grep -l "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt` returns the file path
    - `grep -l "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt` returns the file path
    - `grep -l "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt` returns the file path
    - `grep -c "@Entity(tableName" shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt | grep -v ':0$' | wc -l | tr -d ' '` returns exactly `4`
    - No new imports added — `grep -c "^import" shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` returns exactly `2` (Entity + PrimaryKey, unchanged from before)
  </acceptance_criteria>
  <done>All 4 entity files have the additive `source: String? = null` column with the D-18-11 comment, no new imports, no field reordering.</done>
</task>

<task type="auto">
  <name>Task 2: Bump AppDatabase to version 10 with AutoMigration(9, 10)</name>
  <files>shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt</files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
    Modify AppDatabase.kt:

    1. Change `version = 9` to `version = 10` (single token replace on the version line).

    2. Append `, AutoMigration(from = 9, to = 10)` to the `autoMigrations` array — place it as the last element so the array becomes:
       ```kotlin
       autoMigrations = [
           AutoMigration(from = 6, to = 7),
           AutoMigration(from = 7, to = 8),
           AutoMigration(from = 8, to = 9),
           AutoMigration(from = 9, to = 10)
       ]
       ```

    Do NOT change the `entities` list — the four entities being modified are already registered (WorkoutTemplateEntity, ExerciseEntity, RecipeEntity, FoodEntity). The migration is purely additive (Room handles nullable column adds without a migration spec, per the 8→9 precedent).

    Do NOT add a `Spec` argument to `AutoMigration(...)` — none is needed because no destructive change occurs. Do NOT touch `AppDatabaseConstructor` or any DAO accessors.
  </action>
  <verify>
    <automated>grep -E "version = 10" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt && grep -E "AutoMigration\(from = 9, to = 10\)" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "version = 10" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `1`
    - `grep -c "version = 9" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `0` (the v9 line was rewritten, not duplicated)
    - `grep -c "AutoMigration(from = 9, to = 10)" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `1`
    - `grep -c "AutoMigration(from = 8, to = 9)" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `1` (precedent migration retained)
    - `grep -c "AutoMigration(from = 6, to = 7)" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `1` (oldest precedent retained)
    - `grep -c "Spec::class" shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` returns exactly `0` (no spec argument added)
  </acceptance_criteria>
  <done>AppDatabase declares version 10 with the additive AutoMigration(9, 10) registered alongside the existing 6→7, 7→8, 8→9 entries.</done>
</task>

<task type="auto">
  <name>Task 3: Propagate source field through 4 domain models + their entity mappers + ExerciseRepository.createExercise</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt
  </read_first>
  <action>
    Add `val source: String? = null` to each of the 4 domain data classes AND propagate the field through the existing `toDomain()` extension functions / explicit constructors so reads carry it from entity → domain.

    Apply diffs:

    1. **WorkoutTemplate.kt** — add field to data class (last param) and update extension function:
       ```kotlin
       data class WorkoutTemplate(
           val id: Long,
           val name: String,
           val createdAt: Long,
           val updatedAt: Long,
           val exercises: List<TemplateExercise> = emptyList(),
           val source: String? = null  // "USER" | "AI" — null treated as USER (D-18-11)
       )

       fun WorkoutTemplateEntity.toDomain(
           exercises: List<TemplateExercise> = emptyList()
       ) = WorkoutTemplate(
           id = id,
           name = name,
           createdAt = createdAt,
           updatedAt = updatedAt,
           exercises = exercises,
           source = source  // pass through from entity
       )
       ```
       Do NOT modify `TemplateExercise` or its `toDomain` (templates carry source; per-exercise rows use the exercise table's source).

    2. **Exercise.kt** — add field to data class (last param after `secondaryMuscles`) and update extension:
       ```kotlin
       data class Exercise(
           val id: String,
           // ... existing fields unchanged ...
           val secondaryMuscles: List<MuscleGroup>,
           val source: String? = null
       )

       fun ExerciseEntity.toDomain(): Exercise = Exercise(
           // ... existing field assignments unchanged ...
           secondaryMuscles = secondaryMuscles
               .split(",")
               .filter { it.isNotBlank() }
               .mapNotNull { MuscleGroup.fromDbName(it) },
           source = source
       )
       ```

    3. **Recipe.kt** — add field to `Recipe` data class (last param after `isFavorite`):
       ```kotlin
       @Serializable
       data class Recipe(
           val id: String = Uuid.random().toString(),
           val name: String,
           val ingredients: List<RecipeIngredient>,
           val isFavorite: Boolean = false,
           val source: String? = null
       )
       ```
       (Recipe.kt has no `toDomain` — the FoodRepositoryImpl assembles Recipes from RecipeEntity + RecipeIngredientEntity. Search for the assembly site in FoodRepositoryImpl.kt and add `source = entity.source` there in the `Recipe(...)` constructor call. If multiple call sites construct Recipe, update each.)

    4. **Food.kt** — add field to data class (last param after `barcode`):
       ```kotlin
       @Serializable
       data class Food(
           // ... existing fields unchanged ...
           val barcode: String? = null,
           val source: String? = null
       )
       ```
       (Food.kt has no `toDomain` either — find the FoodEntity → Food construction in FoodRepositoryImpl.kt and add `source = entity.source`.)

    5. **ExerciseRepository.kt** — `createExercise` constructs `ExerciseEntity` inline (lines 64-80 of the current file). Add `source = exercise.source` as the LAST constructor argument so user-created exercises preserve their nullable source (which will be `null` for existing UI flows; later AI flows pass `"AI"`).

    For the FoodRepositoryImpl.kt edits in steps 3 and 4: open the file, locate the `Food(...)` and `Recipe(...)` constructor calls (they map from `FoodEntity` / `RecipeEntity`), and add `source = entity.source` as the last argument. If `RecipeIngredient(...)` is constructed, do NOT add source there (RecipeIngredient is a sub-entity without provenance).
  </action>
  <verify>
    <automated>grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt | grep -v ':0$' | wc -l | tr -d ' '</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` returns exactly `1`
    - `grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` returns exactly `1`
    - `grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt` returns exactly `1`
    - `grep -c "val source: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt` returns exactly `1`
    - `grep -c "source = source" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` returns at least `1` (the toDomain pass-through)
    - `grep -c "source = source" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` returns at least `1`
    - `grep -c "source = exercise.source" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` returns exactly `1` (createExercise wires the field through)
    - In FoodRepositoryImpl.kt: `grep -c "source = " shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt` returns at least `2` (one for Food construction, one for Recipe construction). If file path differs, check `find shared/src/commonMain/kotlin/com/pumpernickel/data/repository -name "FoodRepositoryImpl.kt"`.
  </acceptance_criteria>
  <done>The four domain models carry source through their data classes, the entity → domain mappers pass the field through, and ExerciseRepository.createExercise + FoodRepositoryImpl construction sites wire the value end-to-end.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Schema migration | Existing v9 rows must survive the v9 → v10 migration unchanged |
| Entity → domain mapping | Reads must not silently drop the new `source` field |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-01-01 | Tampering | AppDatabase migration path | mitigate | Use Room AutoMigration (additive nullable column) — Room generates the SQL; no hand-rolled SQL means no destructive ALTER. Existing rows keep `source = null`. |
| T-18-01-02 | Information disclosure | source field | accept | The field stores `"AI"` or `"USER"` only — no PII, no secrets. Provenance is internal metadata. |
| T-18-01-03 | Denial of service | Migration time on first launch after upgrade | accept | Adding a nullable column is O(1) DDL on SQLite; no row rewrite. Existing 8→9 migration set the precedent. |
</threat_model>

<verification>
- AppDatabase.kt declares version 10 and AutoMigration(9, 10).
- All four entities have the new nullable `source: String?` column.
- All four domain models carry the field; mappers propagate it.
- App compiles. Existing tests pass against the new schema (run `./gradlew :shared:testDebugUnitTest` if tests exist for the affected modules; otherwise rely on `./gradlew assembleDebug`).
- A clean reinstall against an old v9 database (manual UAT in execute phase) loads existing templates / recipes / foods / exercises with `source = null` and no exceptions.
</verification>

<success_criteria>
- Schema bumped to v10 with the additive AutoMigration registered (REQ-AI-01 / REQ-AI-04 prerequisite).
- Domain models propagate provenance end-to-end so future plans can write `source = "AI"` without further schema work (D-18-11).
- No regressions to existing reads — all `source` adds default to `null`.
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-01-SUMMARY.md` with: schema version delta, list of files modified, sample mapper diff (1-2 lines), and a one-line confirmation that AutoMigration(9, 10) is registered.
</output>
