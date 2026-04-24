package com.pumpernickel.domain.model

enum class AnatomyView {
    FRONT, BACK, BOTH
}

enum class MuscleRegion(
    val group: MuscleGroup,
    val view: AnatomyView,
    val svgId: String
) {
    // Chest (2)
    UPPER_PECTORALIS(MuscleGroup.CHEST, AnatomyView.FRONT, "upper-pectoralis"),
    MID_LOWER_PECTORALIS(MuscleGroup.CHEST, AnatomyView.FRONT, "mid-lower-pectoralis"),

    // Shoulders (3)
    ANTERIOR_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.FRONT, "anterior-deltoid"),
    LATERAL_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.BOTH, "lateral-deltoid"),
    POSTERIOR_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.BACK, "posterior-deltoid"),

    // Biceps (2)
    LONG_HEAD_BICEP(MuscleGroup.BICEPS, AnatomyView.FRONT, "long-head-bicep"),
    SHORT_HEAD_BICEP(MuscleGroup.BICEPS, AnatomyView.FRONT, "short-head-bicep"),

    // Triceps (3)
    LATERAL_HEAD_TRICEPS(MuscleGroup.TRICEPS, AnatomyView.BACK, "lateral-head-triceps"),
    LONG_HEAD_TRICEPS(MuscleGroup.TRICEPS, AnatomyView.BACK, "long-head-triceps"),
    MEDIAL_HEAD_TRICEPS(MuscleGroup.TRICEPS, AnatomyView.BACK, "medial-head-triceps"),

    // Forearms (2)
    WRIST_EXTENSORS(MuscleGroup.FOREARMS, AnatomyView.BOTH, "wrist-extensors"),
    WRIST_FLEXORS(MuscleGroup.FOREARMS, AnatomyView.BOTH, "wrist-flexors"),

    // Traps (3)
    UPPER_TRAPEZIUS(MuscleGroup.TRAPS, AnatomyView.BOTH, "upper-trapezius"),
    TRAPS_MIDDLE(MuscleGroup.TRAPS, AnatomyView.BACK, "traps-middle"),
    LOWER_TRAPEZIUS(MuscleGroup.TRAPS, AnatomyView.BACK, "lower-trapezius"),

    // Lats (1)
    LATS(MuscleGroup.LATS, AnatomyView.BACK, "lats"),

    // Neck (1)
    NECK(MuscleGroup.NECK, AnatomyView.BOTH, "neck"),

    // Quadriceps (3)
    RECTUS_FEMORIS(MuscleGroup.QUADRICEPS, AnatomyView.FRONT, "rectus-femoris"),
    INNER_QUADRICEP(MuscleGroup.QUADRICEPS, AnatomyView.FRONT, "inner-quadricep"),
    OUTER_QUADRICEP(MuscleGroup.QUADRICEPS, AnatomyView.FRONT, "outer-quadricep"),

    // Hamstrings (2)
    MEDIAL_HAMSTRINGS(MuscleGroup.HAMSTRINGS, AnatomyView.BACK, "medial-hamstrings"),
    LATERAL_HAMSTRINGS(MuscleGroup.HAMSTRINGS, AnatomyView.BACK, "lateral-hamstrings"),

    // Glutes (2)
    GLUTEUS_MAXIMUS(MuscleGroup.GLUTES, AnatomyView.BACK, "gluteus-maximus"),
    GLUTEUS_MEDIUS(MuscleGroup.GLUTES, AnatomyView.BACK, "gluteus-medius"),

    // Calves (3)
    GASTROCNEMIUS(MuscleGroup.CALVES, AnatomyView.BOTH, "gastrocnemius"),
    SOLEUS(MuscleGroup.CALVES, AnatomyView.BOTH, "soleus"),
    TIBIALIS(MuscleGroup.CALVES, AnatomyView.FRONT, "tibialis"),

    // Adductors (2)
    INNER_THIGH(MuscleGroup.ADDUCTORS, AnatomyView.BOTH, "inner-thigh"),
    GROIN(MuscleGroup.ADDUCTORS, AnatomyView.FRONT, "groin"),

    // Abdominals (2)
    UPPER_ABDOMINALS(MuscleGroup.ABDOMINALS, AnatomyView.FRONT, "upper-abdominals"),
    LOWER_ABDOMINALS(MuscleGroup.ABDOMINALS, AnatomyView.FRONT, "lower-abdominals"),

    // Obliques (1)
    OBLIQUES(MuscleGroup.OBLIQUES, AnatomyView.FRONT, "obliques"),

    // Lower Back (1)
    LOWERBACK(MuscleGroup.LOWER_BACK, AnatomyView.BACK, "lowerback");

    companion object {
        fun forGroup(group: MuscleGroup): List<MuscleRegion> =
            entries.filter { it.group == group }

        fun forView(view: AnatomyView): List<MuscleRegion> =
            entries.filter { it.view == view || it.view == AnatomyView.BOTH }

        fun fromSvgId(svgId: String): MuscleRegion? =
            entries.find { it.svgId == svgId }
    }
}
