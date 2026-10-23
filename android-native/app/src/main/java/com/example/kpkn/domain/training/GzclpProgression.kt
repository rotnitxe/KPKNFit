package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.WeekRecipe

/**
 * GZCLP (CAL5; Cody Lefever, «GZCL Applications & Adaptations», 2016, fuente primaria de RES1 §6). Notación del autor
 * reps × series: «3x5+» son 3 repeticiones en 5 series con la última al máximo.
 *
 * T1: etapa 1 = 5 series de 3 (15 repeticiones de volumen base), etapa 2 = 6 de 2 (12), etapa 3 = 10 de 1 (10). No completar
 * el volumen base pasa a la etapa siguiente con la MISMA carga; no completar la etapa 3 es el reinicio: descansar, probar un
 * 5RM nuevo y empezar otro ciclo de 5×3+ al 85 % de ese 5RM. La AMRAP de la última serie no cambia la carga por sí sola
 * (es margen de volumen) y cuenta como mucho [T1_AMRAP_CAP] repeticiones aunque queden más.
 *
 * T2: 3 series de 10 (30), de 8 (24) y de 6 (18), con el mismo criterio; tras la etapa 3 reinicia en 3×10.
 *
 * T3: 3 series de 15 con la última al máximo; la carga sube cuando esa serie llega a [T3_RAISE_REPS] repeticiones.
 *
 * Dominio puro. La receta del programa trae la etapa 1; las etapas siguientes se escriben como receta efectiva del ciclo
 * nuevo con [overlay].
 */
object GzclpProgression {
    const val T1_AMRAP_CAP = 10
    const val T3_RAISE_REPS = 25

    /** El reinicio tras la etapa 3: la carga de la etapa 1 es este porcentaje del 5RM nuevo. */
    const val RESTART_FRACTION_OF_FIVE_RM = 0.85

    enum class T1Stage(val number: Int, val sets: Int, val reps: Int) {
        STAGE_1(1, 5, 3),
        STAGE_2(2, 6, 2),
        STAGE_3(3, 10, 1),
        ;

        /** Volumen base de la etapa: repeticiones totales que hay que completar. */
        val baseVolume: Int get() = sets * reps

        fun next(): T1Stage? = entries.getOrNull(ordinal + 1)

        /** «5×3+»: series × repeticiones, la última al máximo. */
        val label: String get() = "$sets×$reps+"
    }

    enum class T2Stage(val number: Int, val sets: Int, val reps: Int) {
        STAGE_1(1, 3, 10),
        STAGE_2(2, 3, 8),
        STAGE_3(3, 3, 6),
        ;

        val baseVolume: Int get() = sets * reps

        fun next(): T2Stage? = entries.getOrNull(ordinal + 1)

        val label: String get() = "$sets×$reps"
    }

    enum class TierOutcome {
        /** Volumen base completado: se queda en la etapa y el TM puede subir. */
        PASSED,

        /** Volumen base sin completar: etapa siguiente con la misma carga. */
        ADVANCE_STAGE,

        /** Etapa 3 sin completar: reinicio (5RM nuevo × 85 % en el T1). */
        RESTART,
    }

    data class TierDecision(val fromStage: Int, val toStage: Int, val outcome: TierOutcome)

    fun t1StageOfBaseVolume(plannedReps: Int): T1Stage? = T1Stage.entries.firstOrNull { it.baseVolume == plannedReps }

    fun t2StageOfBaseVolume(plannedReps: Int): T2Stage? = T2Stage.entries.firstOrNull { it.baseVolume == plannedReps }

    fun decideT1(stage: T1Stage, completedBaseVolume: Boolean): TierDecision = when {
        completedBaseVolume -> TierDecision(stage.number, stage.number, TierOutcome.PASSED)
        stage.next() != null -> TierDecision(stage.number, stage.number + 1, TierOutcome.ADVANCE_STAGE)
        else -> TierDecision(stage.number, T1Stage.STAGE_1.number, TierOutcome.RESTART)
    }

    fun decideT2(stage: T2Stage, completedBaseVolume: Boolean): TierDecision = when {
        completedBaseVolume -> TierDecision(stage.number, stage.number, TierOutcome.PASSED)
        stage.next() != null -> TierDecision(stage.number, stage.number + 1, TierOutcome.ADVANCE_STAGE)
        else -> TierDecision(stage.number, T2Stage.STAGE_1.number, TierOutcome.RESTART)
    }

    /** Las repeticiones de la AMRAP del T1 que cuentan: como mucho [T1_AMRAP_CAP], aunque queden más. */
    fun cappedAmrapReps(reps: Int, cap: Int = T1_AMRAP_CAP): Int = reps.coerceIn(0, cap)

    /** La carga de la etapa 1 tras el reinicio: el 85 % del 5RM nuevo. */
    fun restartLoadKg(fiveRmKg: Double): Double = fiveRmKg * RESTART_FRACTION_OF_FIVE_RM

    /** El T3 sube cuando su última serie llega a [raiseReps] repeticiones (25 en Lefever). */
    fun t3ShouldRaise(lastSetReps: Int, raiseReps: Int = T3_RAISE_REPS): Boolean = lastSetReps >= raiseReps

    /** Las series del T1 de [stage] al porcentaje [percent] del TM: la última, la AMRAP, con la repetición de la etapa como mínimo. */
    fun t1Sets(stage: T1Stage, template: SetRecipe): List<SetRecipe> = List(stage.sets) { index ->
        val last = index == stage.sets - 1
        template.copy(
            reps = stage.reps,
            repsMin = null,
            repsMax = null,
            amrap = last,
            purpose = null,
            amrapMinimumReps = null,
            calibrationScope = null,
            isTopSet = false,
        )
    }

    fun t2Sets(stage: T2Stage, template: SetRecipe): List<SetRecipe> = List(stage.sets) {
        template.copy(reps = stage.reps, repsMin = null, repsMax = null, amrap = false, purpose = null, amrapMinimumReps = null)
    }

    /** La etapa del T1 que prescribe [slot]: por el número de series de trabajo (5, 6 o 10); null si no es de GZCLP. */
    fun t1StageOf(slot: SlotRecipe): T1Stage? =
        T1Stage.entries.firstOrNull { it.sets == slot.sets.count { set -> !set.isWarmup } && it.reps == slot.sets.firstOrNull { s -> !s.isWarmup }?.reps }

    /**
     * La semana de receta [week] con el T1 y el T2 de cada levantamiento en la etapa que dicen [t1] y [t2]. Los slots de
     * otros levantamientos, los accesorios y los porcentajes (la carga no cambia entre etapas) se conservan.
     */
    fun overlay(week: WeekRecipe, t1: Map<LiftSlot, T1Stage>, t2: Map<LiftSlot, T2Stage>): WeekRecipe =
        week.copy(days = week.days.map { day -> overlayDay(day, t1, t2) })

    private fun overlayDay(day: DayRecipe, t1: Map<LiftSlot, T1Stage>, t2: Map<LiftSlot, T2Stage>): DayRecipe =
        day.copy(
            slots = day.slots.map { slot ->
                val lift = slot.lift.liftSlot
                val template = slot.sets.firstOrNull { !it.isWarmup }
                when {
                    lift == null || template == null -> slot
                    slot.role == SlotRole.T1_MAIN && lift in t1 -> slot.copy(sets = t1Sets(t1.getValue(lift), template))
                    slot.role == SlotRole.T2_SUPPLEMENTAL && lift in t2 -> slot.copy(sets = t2Sets(t2.getValue(lift), template))
                    else -> slot
                }
            },
        )
}
