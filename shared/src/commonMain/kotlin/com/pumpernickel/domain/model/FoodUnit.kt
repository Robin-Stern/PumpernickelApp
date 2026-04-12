package com.pumpernickel.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FoodUnit {
    GRAM, MILLILITER;
    val label get() = if (this == MILLILITER) "ml" else "g"
}
