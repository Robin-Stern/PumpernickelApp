package com.pumpernickel.domain.model

enum class WeightUnit {
    KG, LBS;

    fun formatWeight(kgX10: Int): String {
        return when (this) {
            KG -> {
                val whole = kgX10 / 10
                val decimal = kgX10 % 10
                if (decimal == 0) "$whole kg" else "$whole.$decimal kg"
            }
            LBS -> {
                val lbsX10 = (kgX10.toLong() * 22046L / 10000).toInt()
                val whole = lbsX10 / 10
                val decimal = lbsX10 % 10
                if (decimal == 0) "$whole lbs" else "$whole.$decimal lbs"
            }
        }
    }

    fun formatVolume(totalVolumeKgX10: Long): String {
        return when (this) {
            KG -> {
                val kg = totalVolumeKgX10 / 10
                "$kg kg"
            }
            LBS -> {
                val lbs = totalVolumeKgX10 * 22046L / 100000
                "$lbs lbs"
            }
        }
    }

    val label: String get() = when (this) { KG -> "kg"; LBS -> "lbs" }
}
