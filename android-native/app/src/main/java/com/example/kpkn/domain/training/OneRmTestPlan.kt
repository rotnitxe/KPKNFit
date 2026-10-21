package com.example.kpkn.domain.training

import com.example.kpkn.data.models.OneRmResolution
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.TrainingPlanRecipe

/**
 * El test de 1RM al cerrar un bloque de realización o de pico (CAL5). Dominio puro.
 *
 * - [requiredLifts]: pide SOLO los levantamientos del programa (los de su receta: Smolov trabaja solo la sentadilla), y el press militar
 *   cuando el programa lo declara. Un programa sin receta pide sentadilla, banca y peso muerto, como siempre.
 * - [prefill]: precarga cada levantamiento con la mejor serie «Test» registrada (el single al 100 % del 1RM de un top set). La persona
 *   confirma o corrige el valor antes de guardarlo: la precarga nunca guarda nada por sí sola.
 */
object OneRmTestPlan {
    private val DEFAULT_LIFTS = listOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT)

    /** Los levantamientos que pide el test de [recipe], en el orden sentadilla, banca, peso muerto y press militar. */
    fun requiredLifts(recipe: TrainingPlanRecipe?): List<LiftSlot> {
        val declared = recipe?.liftSlots?.keys.orEmpty()
        return LiftSlot.entries.filter { it in declared }.ifEmpty { DEFAULT_LIFTS }
    }

    fun requiredLifts(program: Program): List<LiftSlot> = requiredLifts(program.sourceRecipe)

    /** El 1RM de [lift] que trae [resolution]. */
    fun oneRmOf(resolution: OneRmResolution, lift: LiftSlot): Double? = when (lift) {
        LiftSlot.SQUAT -> resolution.squat1RM
        LiftSlot.BENCH -> resolution.bench1RM
        LiftSlot.DEADLIFT -> resolution.deadlift1RM
        LiftSlot.OVERHEAD -> resolution.overhead1RM
    }

    /** Los levantamientos de [required] sin un 1RM válido (mayor que cero) en [resolution]. */
    fun missingLifts(resolution: OneRmResolution, required: List<LiftSlot>): List<LiftSlot> =
        required.filter { (oneRmOf(resolution, it) ?: 0.0) <= 0.0 }

    /** Una resolución «registrada» con los 1RM de [values] (kg); los levantamientos que no están quedan sin valor. */
    fun recorded(values: Map<LiftSlot, Double>, note: String? = null): OneRmResolution = OneRmResolution(
        status = OneRmResolutionStatus.RECORDED,
        squat1RM = values[LiftSlot.SQUAT],
        bench1RM = values[LiftSlot.BENCH],
        deadlift1RM = values[LiftSlot.DEADLIFT],
        overhead1RM = values[LiftSlot.OVERHEAD],
        note = note,
    )

    /**
     * Los 1RM sugeridos por las series «Test» realizadas ([hits], de `ProgramAutoregulationEngine.collectOneRmTestHits`): por
     * levantamiento de [required], la carga más alta con alguna repetición completa. Sin serie de test, el levantamiento no aparece.
     */
    fun prefill(
        hits: List<ProgramAutoregulationEngine.OneRmTestHit>,
        required: List<LiftSlot> = LiftSlot.entries,
    ): Map<LiftSlot, Double> = hits
        .filter { it.liftSlot in required && it.reps >= 1 && it.weightKg > 0.0 }
        .groupBy { it.liftSlot }
        .mapValues { (_, lifts) -> lifts.maxOf { it.weightKg } }
}
