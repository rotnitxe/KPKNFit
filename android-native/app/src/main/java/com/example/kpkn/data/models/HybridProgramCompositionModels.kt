package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/** An authored/generated discipline session with explicit goal and resource requirements. */
@Serializable
data class HybridSessionContribution(
    val session: Session,
    val binding: HybridSessionGoalBinding,
    val sourceId: String,
    val sourceVersion: Int,
)

@Serializable
data class HybridWeekComposition(
    val weekId: String,
    val baseSessionBindings: List<HybridSessionGoalBinding>,
    val contributions: List<HybridSessionContribution> = emptyList(),
    val configuration: HybridPlanningConfiguration? = null,
)

/** Optional authoring input for the deferred wizard. No recipe is guessed from a sport name. */
@Serializable
data class HybridProgramComposition(
    val schemaVersion: Int = 1,
    val configuration: HybridPlanningConfiguration,
    val weeks: List<HybridWeekComposition>,
)

/** Provenance of additions remains alongside the existing native plan's own recipe/progression. */
@Serializable
data class HybridAuthoredSessionSource(
    val weekId: String,
    val sessionId: String,
    val goalId: String,
    val sourceId: String,
    val sourceVersion: Int,
)
