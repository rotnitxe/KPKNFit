package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.WorkoutContextProfile

/**
 * Carga base (barra vacía / pin mínimo / stack): piso para sugerencias [LoadModeV2.LOAD].
 * Persistencia y floor de sugerencias solo con etiqueta activa.
 * Lectura legacy: [baseLoadKg] ?: [barWeightKg].
 */
object BaseLoadPolicy {

    /** Valores del perfil etiquetado (Room / contextProfilesV3). */
    fun resolvedFromProfile(profile: WorkoutContextProfile?): Double? {
        if (profile == null) return null
        return firstPositive(
            profile.baseLoadKg,
            profile.setupDetails?.baseLoadKg,
            profile.barWeightKg,
            profile.setupDetails?.barWeightKg,
        )
    }

    /**
     * Valor mostrado en UI de setup (perfil activo, o setup de sesión/ejercicio sin tag).
     */
    fun resolvedForDisplay(
        profile: WorkoutContextProfile?,
        exercise: Exercise? = null,
        sessionSetup: ExerciseSetupDetails? = null,
    ): Double? = resolvedFromProfile(profile)
        ?: firstPositive(
            sessionSetup?.baseLoadKg,
            sessionSetup?.barWeightKg,
            exercise?.setupDetails?.baseLoadKg,
            exercise?.setupDetails?.barWeightKg,
        )

    /**
     * Floor for live LOAD suggestions. Returns null when the floor must not apply
     * (no tag, non-LOAD mode, or missing/non-positive base on the tagged profile).
     */
    fun floorForLoadSuggestion(
        loadMode: LoadModeV2,
        activeTagId: String?,
        engineSuggestedKg: Double,
        taggedProfileBaseLoadKg: Double?,
        tagDisplayName: String? = null,
    ): FloorResult? {
        if (loadMode != LoadModeV2.LOAD) return null
        if (activeTagId.isNullOrBlank()) return null
        val base = taggedProfileBaseLoadKg?.takeIf { it > 0.0 } ?: return null
        if (engineSuggestedKg >= base) return null
        val label = tagDisplayName?.takeIf { it.isNotBlank() } ?: activeTagId
        return FloorResult(
            suggestedWeight = base,
            reason = "Carga base · $label",
        )
    }

    /**
     * Prefer writing [baseLoadKg]; mirror into [barWeightKg] for older consumers.
     */
    fun withMirroredBaseLoad(setup: ExerciseSetupDetails, baseLoadKg: Double?): ExerciseSetupDetails {
        val normalized = baseLoadKg?.takeIf { it > 0.0 }
        return setup.copy(
            baseLoadKg = normalized,
            barWeightKg = normalized ?: setup.barWeightKg,
        )
    }

    private fun firstPositive(vararg values: Double?): Double? =
        values.firstOrNull { it != null && it > 0.0 }

    data class FloorResult(
        val suggestedWeight: Double,
        val reason: String,
    )
}
