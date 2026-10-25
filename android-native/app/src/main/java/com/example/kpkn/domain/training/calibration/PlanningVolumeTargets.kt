package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.domain.training.RecipeContractPolicy
import kotlinx.serialization.Serializable

/** Rango de planificación elegido expresamente; no representa una capacidad fisiológica medida. */
@Serializable
data class PlanningVolumeTarget(
    val muscleGroup: KpknMuscleGroup,
    val minSets: Int,
    val maxSets: Int,
    val chosenAtMs: Long = 0L,
    val notes: String = "",
    val source: String = "MANUAL",
    val evidenceWeeks: Int = 0,
)

object PlanningVolumeTargets {
    fun valid(targets: List<PlanningVolumeTarget>): Boolean =
        targets.map { it.muscleGroup }.distinct().size == targets.size &&
            targets.all { it.minSets >= 0 && it.maxSets >= it.minSets && it.chosenAtMs >= 0 && it.evidenceWeeks >= 0 }

    /** Adaptador al tipo de límites heredado. Sus nombres históricos no convierten el rango en diagnóstico. */
    fun asPlanningBounds(targets: List<PlanningVolumeTarget>): List<VolumeRecommendation> {
        require(valid(targets))
        return targets.map { target ->
            val reference = VolumeLandmarks.byGroup.getValue(target.muscleGroup)
            VolumeRecommendation(RecipeContractPolicy.volumeGroupLabel(target.muscleGroup), target.minSets,
                reference.mav.coerceIn(target.minSets, target.maxSets), target.maxSets)
        }
    }
}
