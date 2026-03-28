package com.pumpernickel.domain.model

enum class MuscleGroup(val dbName: String, val displayName: String) {
    CHEST("chest", "Chest"),
    SHOULDERS("shoulders", "Shoulders"),
    BICEPS("biceps", "Biceps"),
    TRICEPS("triceps", "Triceps"),
    FOREARMS("forearms", "Forearms"),
    TRAPS("traps", "Traps"),
    LATS("lats", "Lats"),
    NECK("neck", "Neck"),
    QUADRICEPS("quadriceps", "Quadriceps"),
    HAMSTRINGS("hamstrings", "Hamstrings"),
    GLUTES("glutes", "Glutes"),
    CALVES("calves", "Calves"),
    ADDUCTORS("adductors", "Adductors"),
    ABDOMINALS("abdominals", "Abdominals"),
    OBLIQUES("obliques", "Obliques"),
    LOWER_BACK("lower back", "Lower Back");

    companion object {
        fun fromDbName(name: String): MuscleGroup? = when (name.lowercase().trim()) {
            "middle back" -> LATS
            "abductors" -> GLUTES
            else -> entries.find { it.dbName == name.lowercase().trim() }
        }
    }
}
