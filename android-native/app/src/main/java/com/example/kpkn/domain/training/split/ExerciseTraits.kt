package com.example.kpkn.domain.training.split

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.training.PatternFamily

/** De dónde salieron los rasgos de un ejercicio: el catálogo v2, los músculos que ya traía el ejercicio o su nombre. */
enum class TraitSource { CATALOG, EXERCISE_MUSCLES, NAME }

/**
 * Región del cuerpo que trabaja un ejercicio. Se deriva de sus músculos y no del campo `bodyRegion` del catálogo, que en
 * antebrazo y cuello no es fiable (los marca como tren inferior).
 */
enum class BodyRegion { UPPER, LOWER, CORE, FULL }

/** Cadena cinética del ejercicio (la del catálogo): decide qué días «anterior / posterior» lo acogen. */
enum class KineticChain { ANTERIOR, POSTERIOR, FULL }

/** Grupo de reparto de un ejercicio: lo que una persona llamaría «es de empuje», «es de tirón», «es de pierna». */
enum class SplitGroup(val label: String) {
    PUSH("Empuje"),
    PULL("Tirón"),
    LEGS("Pierna"),
    CORE("Core"),
    OTHER("Otros"),
}

/**
 * Lo que el redistribuidor necesita saber de UN ejercicio para decidir a qué día va: sus músculos, su patrón de
 * movimiento, su región, sus articulaciones y si es compuesto. Lo entrega un [ExerciseTraitResolver] (por defecto sobre
 * el catálogo v2).
 *
 * [muscles] es el aporte de cada músculo: 1,0 si es primario y 0,5 si es secundario (el mismo criterio con el que la app
 * cuenta el volumen). Un ejercicio sin músculos reconocibles no tiene rasgos: el resolutor devuelve `null`.
 */
data class ExerciseTraits(
    val pattern: PatternFamily?,
    val movementPatternId: String?,
    val muscles: Map<KpknMuscleGroup, Double>,
    val region: BodyRegion,
    val chain: KineticChain,
    /** Articulaciones principales y secundarias que carga (ids de `jointInvolvement`); alimenta la aproximación. */
    val joints: Set<String> = emptySet(),
    val isCompound: Boolean,
    /** Admite carga externa significativa: un aislamiento ligero o un peso corporal fácil no. */
    val canBeHeavy: Boolean,
    val axialLoad: Double = 0.0,
    val equipmentId: String? = null,
    /** Definición del catálogo a la que pertenece (varias configuraciones comparten una: barra y polea del mismo remo); null si no sale del catálogo. */
    val definitionId: String? = null,
    val source: TraitSource = TraitSource.CATALOG,
) {
    /** Músculos con aporte completo (los primarios). */
    val primaryMuscles: Set<KpknMuscleGroup>
        get() = muscles.filterValues { it >= PRIMARY }.keys

    val group: SplitGroup
        get() = SplitMuscles.groupOf(this)

    companion object {
        const val PRIMARY = 1.0
        const val SECONDARY = 0.5
    }
}

/** Tablas puras sobre los músculos canónicos de la app ([KpknMuscleGroup]): grupos, regiones y rótulos en español. */
internal object SplitMuscles {

    fun groupOfAtom(atom: KpknMuscleGroup): SplitGroup = when (atom) {
        KpknMuscleGroup.CHEST,
        KpknMuscleGroup.DELT_FRONT,
        KpknMuscleGroup.DELT_LATERAL,
        KpknMuscleGroup.TRICEPS -> SplitGroup.PUSH
        KpknMuscleGroup.BACK_LATS,
        KpknMuscleGroup.BACK_UPPER,
        KpknMuscleGroup.DELT_REAR,
        KpknMuscleGroup.BICEPS,
        KpknMuscleGroup.FOREARMS -> SplitGroup.PULL
        KpknMuscleGroup.QUADS,
        KpknMuscleGroup.HAMS,
        KpknMuscleGroup.GLUTES,
        KpknMuscleGroup.CALVES,
        KpknMuscleGroup.ADDUCTORS,
        KpknMuscleGroup.ERECTORS -> SplitGroup.LEGS
        KpknMuscleGroup.CORE -> SplitGroup.CORE
        KpknMuscleGroup.NECK -> SplitGroup.OTHER
    }

    /** Primero manda el patrón de movimiento; sin patrón conocido, el grupo con más aporte muscular. */
    fun groupOf(traits: ExerciseTraits): SplitGroup = when (traits.pattern) {
        PatternFamily.SQUAT,
        PatternFamily.HINGE,
        PatternFamily.HIP_EXTENSION,
        PatternFamily.KNEE_EXTENSION,
        PatternFamily.KNEE_FLEXION,
        PatternFamily.CALF -> SplitGroup.LEGS
        PatternFamily.HORIZONTAL_PUSH,
        PatternFamily.VERTICAL_PUSH,
        PatternFamily.SHOULDER_ABDUCTION,
        PatternFamily.SHOULDER_FLEXION,
        PatternFamily.ELBOW_EXTENSION -> SplitGroup.PUSH
        PatternFamily.HORIZONTAL_PULL,
        PatternFamily.VERTICAL_PULL,
        PatternFamily.ELBOW_FLEXION,
        PatternFamily.GRIP,
        PatternFamily.SHRUG -> SplitGroup.PULL
        PatternFamily.CORE -> SplitGroup.CORE
        PatternFamily.NECK -> SplitGroup.OTHER
        PatternFamily.OTHER,
        null -> groupByMuscles(traits.muscles)
    }

    fun groupByMuscles(muscles: Map<KpknMuscleGroup, Double>): SplitGroup {
        val totals = SplitGroup.entries.associateWith { 0.0 }.toMutableMap()
        muscles.forEach { (atom, weight) ->
            val group = groupOfAtom(atom)
            totals[group] = (totals[group] ?: 0.0) + weight
        }
        // Empate: el primero en el orden de la enumeración (empuje, tirón, pierna…), para que sea determinista.
        val best = SplitGroup.entries.maxByOrNull { totals[it] ?: 0.0 } ?: return SplitGroup.OTHER
        return if ((totals[best] ?: 0.0) > 0.0) best else SplitGroup.OTHER
    }

    fun regionOf(muscles: Map<KpknMuscleGroup, Double>): BodyRegion {
        var upper = 0.0
        var lower = 0.0
        var core = 0.0
        muscles.forEach { (atom, weight) ->
            when (atom) {
                KpknMuscleGroup.CHEST,
                KpknMuscleGroup.BACK_LATS,
                KpknMuscleGroup.BACK_UPPER,
                KpknMuscleGroup.DELT_FRONT,
                KpknMuscleGroup.DELT_LATERAL,
                KpknMuscleGroup.DELT_REAR,
                KpknMuscleGroup.BICEPS,
                KpknMuscleGroup.TRICEPS,
                KpknMuscleGroup.FOREARMS,
                KpknMuscleGroup.NECK -> { upper += weight }
                KpknMuscleGroup.QUADS,
                KpknMuscleGroup.HAMS,
                KpknMuscleGroup.GLUTES,
                KpknMuscleGroup.CALVES,
                KpknMuscleGroup.ADDUCTORS -> { lower += weight }
                KpknMuscleGroup.ERECTORS,
                KpknMuscleGroup.CORE -> { core += weight }
            }
        }
        return when {
            upper > 0.0 && lower > 0.0 -> {
                if (minOf(upper, lower) >= 0.5 * maxOf(upper, lower)) BodyRegion.FULL
                else if (upper > lower) BodyRegion.UPPER else BodyRegion.LOWER
            }
            upper > 0.0 -> BodyRegion.UPPER
            lower > 0.0 -> BodyRegion.LOWER
            core > 0.0 -> BodyRegion.CORE
            else -> BodyRegion.FULL
        }
    }

    /** Rótulo completo (informes y volumen por músculo). */
    fun label(atom: KpknMuscleGroup): String = when (atom) {
        KpknMuscleGroup.CHEST -> "Pectorales"
        KpknMuscleGroup.BACK_LATS -> "Dorsales"
        KpknMuscleGroup.BACK_UPPER -> "Espalda alta"
        KpknMuscleGroup.QUADS -> "Cuádriceps"
        KpknMuscleGroup.HAMS -> "Isquiosurales"
        KpknMuscleGroup.GLUTES -> "Glúteos"
        KpknMuscleGroup.ERECTORS -> "Erectores espinales"
        KpknMuscleGroup.DELT_FRONT -> "Deltoides anterior"
        KpknMuscleGroup.DELT_LATERAL -> "Deltoides lateral"
        KpknMuscleGroup.DELT_REAR -> "Deltoides posterior"
        KpknMuscleGroup.BICEPS -> "Bíceps"
        KpknMuscleGroup.TRICEPS -> "Tríceps"
        KpknMuscleGroup.CALVES -> "Pantorrillas"
        KpknMuscleGroup.CORE -> "Core"
        KpknMuscleGroup.FOREARMS -> "Antebrazo"
        KpknMuscleGroup.NECK -> "Cuello"
        KpknMuscleGroup.ADDUCTORS -> "Aductores"
    }

    /** Rótulo corto para el foco de un día («Pecho · Hombros · Tríceps»): agrupa las cabezas del deltoides y la espalda. */
    fun focusLabel(atom: KpknMuscleGroup): String = when (atom) {
        KpknMuscleGroup.CHEST -> "Pecho"
        KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.BACK_UPPER, KpknMuscleGroup.ERECTORS -> "Espalda"
        KpknMuscleGroup.QUADS -> "Cuádriceps"
        KpknMuscleGroup.HAMS -> "Isquios"
        KpknMuscleGroup.GLUTES -> "Glúteos"
        KpknMuscleGroup.DELT_FRONT, KpknMuscleGroup.DELT_LATERAL, KpknMuscleGroup.DELT_REAR -> "Hombros"
        KpknMuscleGroup.BICEPS -> "Bíceps"
        KpknMuscleGroup.TRICEPS -> "Tríceps"
        KpknMuscleGroup.CALVES -> "Pantorrillas"
        KpknMuscleGroup.CORE -> "Core"
        KpknMuscleGroup.FOREARMS -> "Antebrazo"
        KpknMuscleGroup.NECK -> "Cuello"
        KpknMuscleGroup.ADDUCTORS -> "Aductores"
    }
}
