package com.example.kpkn.domain.training.split

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.VolumeCalculator

/**
 * Los grupos con los que la pantalla de reparto agrupa el volumen y la frecuencia por músculo (Pecho, Espalda, Hombros…).
 * El informe del motor sigue siendo por músculo canónico; esto solo lo junta para mostrarlo: «Espalda» suma dorsales y
 * espalda alta, «Hombros» las tres cabezas del deltoides, «Lumbar» los erectores espinales.
 */
object SplitMuscleGroups {

    /** Un grupo para mostrar. [main] = entra en la mediana de «veces por semana por músculo» del catálogo. */
    data class Group(val label: String, val atoms: List<KpknMuscleGroup>, val main: Boolean)

    /** En el orden en que se muestran; los tres últimos no se listan en la revisión salvo que tengan series. */
    val ALL: List<Group> = listOf(
        Group("Pecho", listOf(KpknMuscleGroup.CHEST), true),
        Group("Espalda", listOf(KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.BACK_UPPER), true),
        Group("Hombros", listOf(KpknMuscleGroup.DELT_FRONT, KpknMuscleGroup.DELT_LATERAL, KpknMuscleGroup.DELT_REAR), true),
        Group("Bíceps", listOf(KpknMuscleGroup.BICEPS), true),
        Group("Tríceps", listOf(KpknMuscleGroup.TRICEPS), true),
        Group("Cuádriceps", listOf(KpknMuscleGroup.QUADS), true),
        Group("Isquios", listOf(KpknMuscleGroup.HAMS), true),
        Group("Glúteos", listOf(KpknMuscleGroup.GLUTES), true),
        Group("Pantorrillas", listOf(KpknMuscleGroup.CALVES), true),
        Group("Lumbar", listOf(KpknMuscleGroup.ERECTORS), false),
        Group("Core", listOf(KpknMuscleGroup.CORE), false),
        Group("Antebrazo", listOf(KpknMuscleGroup.FOREARMS), false),
        Group("Aductores", listOf(KpknMuscleGroup.ADDUCTORS), false),
        Group("Cuello", listOf(KpknMuscleGroup.NECK), false),
    )

    /** Los nueve grupos principales, en orden. */
    val MAIN: List<String> = ALL.filter { it.main }.map { it.label }

    private val byAtom: Map<KpknMuscleGroup, Group> = ALL.flatMap { group -> group.atoms.map { it to group } }.toMap()

    fun labelOf(atom: KpknMuscleGroup): String = byAtom.getValue(atom).label

    /** Series ponderadas (primario 1, secundario 0,5) por grupo en cada sesión: la base de volumen y frecuencia. */
    internal fun setsBySession(sessions: List<Session>, resolver: ExerciseTraitResolver): List<Map<String, Double>> =
        sessions.map { session ->
            val totals = LinkedHashMap<String, Double>()
            session.allExercises().distinctBy { it.id }.forEach { exercise ->
                if (exercise.cardioDetails != null) return@forEach
                val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
                if (sets <= 0) return@forEach
                val traits = resolver.traitsOf(exercise) ?: return@forEach
                traits.muscles.forEach { (atom, weight) ->
                    totals.merge(labelOf(atom), sets * weight) { a, b -> a + b }
                }
            }
            totals
        }

    /** Series semanales por grupo (suma de las sesiones). */
    internal fun volume(sessions: List<Session>, resolver: ExerciseTraitResolver): Map<String, Double> {
        val totals = LinkedHashMap<String, Double>()
        setsBySession(sessions, resolver).forEach { perSession ->
            perSession.forEach { (group, sets) -> totals.merge(group, sets) { a, b -> a + b } }
        }
        return totals
    }

    /** Frecuencia por grupo: sesiones en las que el grupo suma al menos [MIN_SETS_FOR_FREQUENCY] series ponderadas. */
    internal fun frequency(sessions: List<Session>, resolver: ExerciseTraitResolver): Map<String, Int> {
        val perSession = setsBySession(sessions, resolver)
        return ALL.associate { group -> group.label to perSession.count { (it[group.label] ?: 0.0) >= MIN_SETS_FOR_FREQUENCY } }
            .filterValues { it > 0 }
    }

    /** Una sesión cuenta como un día de trabajo de un grupo si le da al menos esto (series ponderadas). */
    const val MIN_SETS_FOR_FREQUENCY = 2.0
}
