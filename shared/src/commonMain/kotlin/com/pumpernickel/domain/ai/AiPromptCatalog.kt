package com.pumpernickel.domain.ai

import com.pumpernickel.readResourceFile

/**
 * D-18-15 — Loads the versioned system prompts from
 * `shared/src/commonMain/resources/` (flat layout) and substitutes the
 * `{locale}` placeholder with the supplied locale string ("de" | "en").
 *
 * Reuses the existing readResourceFile expect/actual pair (already used by
 * DatabaseSeeder for free_exercise_db.json — see SharedModule.kt line 82).
 * No new platform code needed.
 *
 * Prompt files MUST be shipped in the platform bundles:
 * - Android: commonMain/resources is bundled to assets automatically.
 * - iOS: the .md files must be added to iosApp.xcodeproj's main target,
 *   alongside free_exercise_db.json.
 */
class AiPromptCatalog {

    fun workoutSystemPrompt(locale: String = DEFAULT_LOCALE): String =
        readResourceFile("workout-system-prompt.md").replace(LOCALE_TOKEN, locale)

    fun recipeSystemPrompt(locale: String = DEFAULT_LOCALE): String =
        readResourceFile("recipe-system-prompt.md").replace(LOCALE_TOKEN, locale)

    companion object {
        const val DEFAULT_LOCALE: String = "de"  // D-18-15 — app-current UI is German-first
        private const val LOCALE_TOKEN: String = "{locale}"
    }
}
