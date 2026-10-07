package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.domain.training.CompositionTaxonomy
import com.example.kpkn.domain.training.ExerciseCompositionMetadata
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider

/**
 * Lo que el materializador sabe de cada ejercicio para decidir su aproximación, sacado del catálogo ya cargado
 * ([ExerciseCompositionMetadataProvider]): articulaciones principales (`jointInvolvement` PRIMARY y SECONDARY),
 * si es compuesto (la misma taxonomía que usa el resto del motor de composición) y si admite carga externa
 * significativa.
 *
 * Admiten carga pesada los compuestos con barra, mancuernas, kettlebell, máquina, Smith o polea. No la admiten un
 * aislamiento (curl, elevación lateral, máquina de aislamiento…), una banda ni el peso corporal fácil; un
 * peso corporal con lastre declarado (dominadas o fondos con peso) sí.
 */
object ApproachInfoProvider {

    /** `equipmentId` del catálogo con carga externa que puede ser pesada. */
    private val LOADED_EQUIPMENT = setOf(
        "barbell", "dumbbells", "kettlebell", "machine", "smith_machine", "cable",
        "plate", "ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar",
    )

    /** El proveedor que usa el materializador: `ApproachPlanner.apply(session, options, ApproachInfoProvider.fromMetadata(metadata))`. */
    fun fromMetadata(metadata: ExerciseCompositionMetadataProvider): (Exercise) -> ApproachExerciseInfo? =
        { exercise -> infoOf(exercise, metadata) }

    fun infoOf(exercise: Exercise, metadata: ExerciseCompositionMetadataProvider): ApproachExerciseInfo? {
        val configurationId = listOf(
            exercise.catalogConfigurationId,
            exercise.canonicalExerciseId,
            exercise.exerciseId,
            exercise.exerciseDbId,
        ).firstOrNull { !it.isNullOrBlank() } ?: return null
        val meta = metadata.metadata(configurationId) ?: return null
        val family = CompositionTaxonomy.familyOf(meta.movementPatternId)
        val compound = !CompositionTaxonomy.isIsolation(family, meta.articulationType, meta.configurationId)
        return ApproachExerciseInfo(
            joints = LinkedHashSet<String>(meta.principalJoints),
            isCompound = compound,
            canBeHeavy = compound && carriesExternalLoad(meta, exercise),
        )
    }

    private fun carriesExternalLoad(meta: ExerciseCompositionMetadata, exercise: Exercise): Boolean {
        val equipment = meta.equipmentId
        // Sin dato de material (proveedor mínimo) no se descarta: manda la composición del patrón.
        if (equipment == null || equipment in LOADED_EQUIPMENT) return true
        // Peso corporal, banda, suspensión…: solo es «pesado» con lastre real declarado.
        return exercise.loadQuantityConvention == LoadQuantityConvention.ADDITIONAL_BODYWEIGHT ||
            exercise.sets.any { (it.weight ?: 0.0) > 0.0 }
    }
}

/**
 * Nivel de aproximación de una receta a partir del nivel que declara (`TrainingPlanRecipe.claimedLevel`:
 * «principiante», «intermedio», «avanzado» o el nombre del enum en inglés). Sin dato, intermedio.
 */
fun approachLevelOf(label: String?): ApproachLevel {
    val normalized = label?.trim()?.lowercase().orEmpty()
    return when {
        normalized.startsWith("princip") || normalized.startsWith("novat") ||
            normalized.startsWith("beginner") || normalized.startsWith("novice") -> ApproachLevel.NOVICE
        normalized.startsWith("avanz") || normalized.startsWith("advanced") -> ApproachLevel.ADVANCED
        else -> ApproachLevel.INTERMEDIATE
    }
}
