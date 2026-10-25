package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.KpknMuscleGroup

/** Correspondencia compartida de las etiquetas de planificación existentes. */
internal fun budgetMuscleGroups(): Map<String, KpknMuscleGroup> = linkedMapOf(
    "Pectorales" to KpknMuscleGroup.CHEST, "Dorsales" to KpknMuscleGroup.BACK_LATS,
    "Trapecio" to KpknMuscleGroup.BACK_UPPER, "Romboides" to KpknMuscleGroup.BACK_UPPER,
    "Cuádriceps" to KpknMuscleGroup.QUADS, "Isquiosurales" to KpknMuscleGroup.HAMS,
    "Glúteos" to KpknMuscleGroup.GLUTES, "Glúteo Medio" to KpknMuscleGroup.GLUTES,
    "Deltoides" to KpknMuscleGroup.DELT_LATERAL, "Bíceps" to KpknMuscleGroup.BICEPS,
    "Tríceps" to KpknMuscleGroup.TRICEPS, "Pantorrillas" to KpknMuscleGroup.CALVES,
    "Core" to KpknMuscleGroup.CORE, "Abdomen" to KpknMuscleGroup.CORE,
    "Erectores Espinales" to KpknMuscleGroup.ERECTORS, "Antebrazo" to KpknMuscleGroup.FOREARMS,
    "Aductores" to KpknMuscleGroup.ADDUCTORS, "Cuello" to KpknMuscleGroup.NECK,
)
