package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/** Categorical training equipment availability, independent of numeric stock. */
@Serializable
data class EquipmentAvailability(
    val categories: Set<EquipmentCategory> = emptySet(),
)

/** Closed set of equipment categories the setup flow can declare. */
@Serializable
enum class EquipmentCategory {
    BARBELL,
    DUMBBELLS,
    KETTLEBELL,
    MACHINES,
    CABLE,
    SMITH_MACHINE,
    BAND,
    SUPPORT,
    PULL_UP_BAR,
    BALL,
    CARDIO,
}
