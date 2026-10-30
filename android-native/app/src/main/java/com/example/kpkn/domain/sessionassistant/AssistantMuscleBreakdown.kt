package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.VolumeCalculator

/** Lo que un ejercicio aporta a un músculo: [sets] series efectivas × [weight] del músculo en ese ejercicio. */
data class MuscleContributionLine(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val weight: Double,
) {
    val contribution: Double get() = sets * weight
}

/** Un músculo de la sesión: su cifra (la de siempre, truncada a una décima) y de dónde sale cada serie. */
data class MuscleBreakdownRow(
    val muscle: String,
    val volume: Double,
    val lines: List<MuscleContributionLine>,
)

/**
 * Volumen por músculo de la hoja Métricas del asistente.
 *
 * Es el mismo recorrido que [VolumeCalculator.calculateUnifiedMuscleVolume] (la cifra de siempre pondera por el papel mayor de cada
 * ejercicio), pero conserva la lista de ejercicios que suman a cada músculo: la suma de las líneas es exactamente la cifra
 * (`pulido:as-met-completa` — detalle «Qué aporta a …»). Kotlin puro: sin `android.*`.
 */
object AssistantMuscleBreakdown {

    /** Músculos de la sesión, de más a menos series. «General» no es un músculo y no sale. */
    fun rows(session: Session, exerciseIndex: Map<String, ExerciseMuscleInfo>): List<MuscleBreakdownRow> {
        val byMuscle = linkedMapOf<String, MutableList<MuscleContributionLine>>()
        for (exercise in session.allExercises()) {
            val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
            if (sets <= 0) continue
            val involved = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
            if (involved.isEmpty()) continue
            VolumeCalculator.buildPerExerciseMuscleContributions(involved).forEach { (muscle, weight) ->
                byMuscle.getOrPut(muscle) { mutableListOf() }.add(lineOf(exercise, sets, weight))
            }
        }
        return byMuscle.entries
            .filter { it.key != "General" }
            .map { (muscle, lines) ->
                MuscleBreakdownRow(muscle = muscle, volume = truncateTenth(lines.sumOf { it.contribution }), lines = lines)
            }
            .sortedByDescending { it.volume }
    }

    /** Series por músculo de varias sesiones (la semana): la cifra de siempre sin truncar, para compararla con el límite semanal. */
    fun weekly(sessions: List<Session>, exerciseIndex: Map<String, ExerciseMuscleInfo>): Map<String, Double> =
        VolumeCalculator.calculateMuscleVolume(sessions, exerciseIndex.values.toList())

    private fun lineOf(exercise: Exercise, sets: Int, weight: Double) =
        MuscleContributionLine(exerciseId = exercise.id, exerciseName = exercise.name, sets = sets, weight = weight)

    internal fun truncateTenth(value: Double): Double = (value * 10).toInt().toDouble() / 10
}
