---
quick_id: 260423-sja
type: quick
wave: 1
autonomous: true
files_modified:
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt
---

<objective>
Rework `CategoryHeader` in `AchievementGalleryScreen.kt` for stronger Material 3 section-break hierarchy — the existing `titleMedium + 4.dp/4.dp Spacer` treatment reads as weakly offset against the translucent AchievementCard tiles, so users perceive it as a faint "card" rather than a section break. After the Phase 15.1 reward-XP line was added (5 text rows per tile) the density increase made this worse.

Scope is exclusively the `CategoryHeader` composable body and related imports. `AchievementCard`, grid arrangements, the reward-XP line, and all other rendering logic are untouched.
</objective>

<context>
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt

The category header appears between groups of achievement tiles as a full-width item (`item(span = { GridItemSpan(2) })`). Current body at line ~110-128:

```kotlin
@Composable
private fun CategoryHeader(category: Category) {
    val label = when (category) { ... }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rework CategoryHeader for M3 section-break hierarchy</name>
  <files>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt
  </files>
  <action>
  Edit `CategoryHeader` to:
  - Bump typography: `titleMedium` → `titleLarge` (label now outranks tile text).
  - Replace both `Spacer(height=4.dp)` with asymmetric `Modifier.padding(top = 20.dp, bottom = 8.dp)` on the Column — the 20.dp belongs to separation from the previous section, 8.dp is breathing room before the divider.
  - Add a tight `Modifier.padding(bottom = 6.dp)` to the Text so the label doesn't crowd the divider.
  - Append a hairline `HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)` under the label — reads as "section boundary," stays subtler than the tile fills.

  Imports:
  - Add `import androidx.compose.material3.HorizontalDivider`.
  - Remove `import androidx.compose.foundation.layout.Spacer` and `import androidx.compose.foundation.layout.height` ONLY if a `grep` confirms no other uses in the file.

  Do NOT modify `AchievementCard`, `tierColor`, `tierLabel`, `rewardXp`, `formatUnlockDate`, the grid `contentPadding` / arrangements, or the imports block beyond the two changes above.
  </action>
  <verify>
    <automated>cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp && grep -E "style = MaterialTheme\.typography\.titleLarge" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt && grep -E "HorizontalDivider" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt && grep -E "padding\(top = 20\.dp, bottom = 8\.dp\)" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt && ! grep -E "Spacer\(modifier = Modifier\.height\(4\.dp\)\)" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt && ./gradlew :androidApp:compileDebugKotlin 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `CategoryHeader` uses `titleLarge` typography.
    - `HorizontalDivider` appears under the label with `outlineVariant` color.
    - Column has `padding(top = 20.dp, bottom = 8.dp)`.
    - Both 4.dp Spacers are gone.
    - `AchievementCard` byte-identical.
    - `./gradlew :androidApp:compileDebugKotlin` passes.
  </acceptance_criteria>
  <done>
    CategoryHeader rendering reads as a clean M3 section break. No other functions touched. Build passes.
  </done>
</task>

</tasks>

<success_criteria>
- Visual: category headers clearly mark section breaks (larger title + divider + asymmetric padding) instead of feeling like offset faint cards.
- No changes to `AchievementCard` or any other composable in the file.
- `./gradlew :androidApp:compileDebugKotlin` passes.
</success_criteria>

<output>
After completion, create `.planning/quick/260423-sja-clean-up-achievementgalleryscreen-catego/260423-sja-SUMMARY.md` documenting the exact changes (typography bump, padding values, divider addition, removed imports).
</output>
