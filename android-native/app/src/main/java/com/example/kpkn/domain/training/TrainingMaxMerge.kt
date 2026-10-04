package com.example.kpkn.domain.training

import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import kotlin.math.abs

/**
 * Fusión del perfil de cargas que ya tiene el programa con el que llega de «Guardar TM» (R-04) o del
 * test de 1RM (R-19). El asistente de TM solo edita los cuatro 1RM: crea un perfil nuevo con las
 * variantes por defecto y un TM recién hidratado, de modo que guardarlo tal cual borraba las variantes
 * del atleta y los TM que el método o una propuesta habían ajustado.
 *
 * Reglas, por levantamiento:
 * - El 1RM del perfil nuevo es [PowerliftingProfile.squat1RM] y sus equivalentes. Si falta (un campo en
 *   blanco), el levantamiento no se editó y conserva todo lo suyo.
 * - Si el 1RM nuevo es igual al que ya tenía el programa (el 1RM guardado o, sin él, el estimado), el
 *   TM viejo se conserva: puede traer un ajuste de una propuesta o de la progresión del método.
 * - Si el 1RM cambió, el TM pasa a `1RM × porcentaje` del plan. Los TM del perfil nuevo se ignoran: el
 *   TM sale del 1RM y del porcentaje, nunca de un valor que llegue ya calculado.
 * - Las variantes, la modalidad y los 1RM estimados del perfil viejo se conservan siempre: el asistente
 *   no los edita.
 *
 * Sin perfil viejo la fusión es la hidratación de siempre. Dominio puro: sin `android.*`.
 */
object TrainingMaxMerge {
    /** Porcentaje del 1RM que se usa como TM cuando el programa no tiene receta que lo declare. */
    const val DEFAULT_TRAINING_MAX_PERCENT = 0.90

    /** Dos 1RM que difieren menos que esto (en kg) son el mismo: el asistente devuelve lo que se le mostró. */
    private const val ONE_RM_EPSILON = 1e-6

    /** Porcentaje del 1RM que usa el programa como TM: el de su receta o, sin ella, el 90 %. */
    fun trainingMaxPercentOf(program: Program): Double =
        program.sourceRecipe?.trainingMaxPercent ?: DEFAULT_TRAINING_MAX_PERCENT

    fun merge(
        old: PowerliftingProfile?,
        new: PowerliftingProfile,
        trainingMaxPercent: Double,
    ): PowerliftingProfile {
        if (old == null) return TrainingMaxResolver.hydrateProfile(new, trainingMaxPercent)
        val squat = mergeLift(old.squat1RM, old.squatE1RM, old.squatTM, new.squat1RM, trainingMaxPercent)
        val bench = mergeLift(old.bench1RM, old.benchE1RM, old.benchTM, new.bench1RM, trainingMaxPercent)
        val deadlift = mergeLift(old.deadlift1RM, old.deadliftE1RM, old.deadliftTM, new.deadlift1RM, trainingMaxPercent)
        val overhead = mergeLift(old.overhead1RM, old.overheadE1RM, old.overheadTM, new.overhead1RM, trainingMaxPercent)
        val merged = old.copy(
            squat1RM = squat.oneRm,
            squatTM = squat.trainingMax,
            bench1RM = bench.oneRm,
            benchTM = bench.trainingMax,
            deadlift1RM = deadlift.oneRm,
            deadliftTM = deadlift.trainingMax,
            overhead1RM = overhead.oneRm,
            overheadTM = overhead.trainingMax,
        )
        // Un TM que seguía sin valor (el perfil viejo solo tenía el 1RM) se deriva del 1RM, como siempre.
        return TrainingMaxResolver.hydrateProfile(merged, trainingMaxPercent)
    }

    private data class LiftValues(val oneRm: Double?, val trainingMax: Double?)

    private fun mergeLift(
        oldOneRm: Double?,
        oldEstimated: Double?,
        oldTrainingMax: Double?,
        newOneRm: Double?,
        trainingMaxPercent: Double,
    ): LiftValues {
        val tested = newOneRm?.takeIf { it > 0.0 } ?: return LiftValues(oldOneRm, oldTrainingMax)
        val previous = (oldOneRm ?: oldEstimated)?.takeIf { it > 0.0 }
        val unchanged = previous != null && abs(previous - tested) < ONE_RM_EPSILON
        return LiftValues(
            oneRm = tested,
            // Un TM guardado sin valor útil (cero o negativo) se vuelve a derivar del 1RM al hidratar.
            trainingMax = if (unchanged) oldTrainingMax?.takeIf { it > 0.0 } else tested * trainingMaxPercent,
        )
    }
}
