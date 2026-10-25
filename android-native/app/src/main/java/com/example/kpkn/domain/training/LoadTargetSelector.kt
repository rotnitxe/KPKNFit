package com.example.kpkn.domain.training

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.performance.StrengthRegion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * T4 · Objetivo automático: propone el par (repeticiones, carga) que más se acerca al esfuerzo
 * objetivo con cargas que de verdad se pueden montar.
 *
 * Dominio puro: no decide CUÁNDO subir o bajar (eso lo manda la regla de doble progresión del
 * perfil, que llega aquí como [Direction]); decide QUÉ se propone entre las cargas disponibles.
 * Las cargas disponibles las arma quien llama desde «Mi material» (pares de mancuernas, rango de
 * la máquina, discos formables…): este objeto nunca inventa una carga.
 *
 * Estimación: e1RM de la última exposición con [StrengthModel] (reps + reserva registrada; sin
 * reserva, RIR 0, la convención del modelo). Manda la serie MÁS FLOJA, porque la regla de doble
 * progresión exige las repeticiones objetivo en todas las series. Repeticiones esperadas a una
 * carga = repeticiones al fallo del modelo menos el RIR objetivo; se redondean hacia abajo con la
 * tolerancia [FIT_TOLERANCE_REPS] (una repetición cuenta si deja al menos «reserva objetivo menos
 * 0,25» de reserva: la reserva se declara en enteros) y se acotan al rango del plan.
 *
 * Puntuación (menor es mejor; todo en «repeticiones de esfuerzo», cada término con su razón):
 *  - Falta ([SHORTFALL_WEIGHT] por repetición): la carga NO deja el rango con el RIR objetivo.
 *    Es, con diferencia, el término más caro porque quedarse por debajo del mínimo (o de la reserva
 *    objetivo) hace que la propia regla de progresión pida bajar después (subir-bajar-subir). Se
 *    perdona [FIT_TOLERANCE_REPS], el ruido de redondeo del e1RM (0,1 kg) y de la reserva declarada
 *    en enteros. El peso 20 sale del banco de simulación (con 6 aún oscilaba en los escalones grandes).
 *  - Sobra ([SURPLUS_WEIGHT] por repetición): la carga queda más fácil que el esfuerzo objetivo
 *    aun con el tope del rango.
 *  - Quedarse en la misma carga ([STAY_WEIGHT]): la regla ya decidió mover la carga; mover solo
 *    pierde si el paso deja al atleta más de ~1 repetición fuera de rango.
 *  - Tamaño del paso ([STEP_WEIGHT] × cambio relativo de carga): entre cargas que caben gana el
 *    paso más pequeño, como la doble progresión de toda la vida; evita saltos mayores de lo que
 *    la regla pidió.
 * Empates: la carga más cercana a la actual y, después, la más ligera. Sin azar: los mismos datos
 * dan siempre la misma propuesta, y con el estado sin cambios la propuesta tampoco cambia.
 */
object LoadTargetSelector {
    /** Ruido de redondeo que se perdona al decidir si un paso «cabe» en el rango (repeticiones). */
    const val FIT_TOLERANCE_REPS = 0.25
    const val SHORTFALL_WEIGHT = 20.0
    const val SURPLUS_WEIGHT = 1.0
    const val STAY_WEIGHT = 1.0
    const val STEP_WEIGHT = 2.0

    private const val LOAD_TOLERANCE_KG = 0.0001
    private const val REPS_EPSILON = 1e-6

    /** Lo que decidió la regla de progresión: subir la carga o bajarla. */
    enum class Direction { INCREASE, REDUCE }

    /** Qué hace la propuesta respecto de la carga con la que se trabajó. */
    enum class Change { INCREASE, KEEP, REDUCE }

    /** Una serie de trabajo de la última exposición. [rir] nulo = sin reserva registrada. */
    data class SetEvidence(val loadKg: Double, val reps: Int, val rir: Int?)

    /**
     * Propuesta. [reserve] es la reserva que se enseña al atleta (la objetivo si la carga cabe;
     * la esperada, redondeada, si el rango obliga a quedarse corto). [repsOverLast] son las
     * repeticiones propuestas menos las de la serie más floja de la última exposición.
     */
    data class LoadTarget(
        val kg: Double,
        val reps: Int,
        val expectedRir: Double,
        val reserve: Int,
        val change: Change,
        val repsOverLast: Int,
        val e1rmKg: Double,
        val score: Double,
    )

    /** e1RM de la última exposición: el de la serie más floja; 0 si no hay series válidas. */
    fun exposureE1rm(sets: List<SetEvidence>, region: StrengthRegion = StrengthRegion.UNSPECIFIED): Double =
        sets.asSequence()
            .filter { it.reps > 0 && it.loadKg.isFinite() && it.loadKg > 0.0 }
            .map { StrengthModel.estimate1RM(weight = it.loadKg, reps = it.reps, rir = it.rir, region = region) }
            .filter { it > 0.0 }
            .minOrNull() ?: 0.0

    /**
     * Elige la propuesta. Devuelve null si no hay con qué estimar (sin series válidas, rango o
     * cargas inservibles): quien llama conserva entonces su comportamiento anterior.
     *
     * @param sets series de trabajo de la última exposición, con la carga de cada una.
     * @param currentLoadKg carga con la que se trabajó (la primera serie, como la regla).
     * @param repMin repMax rango de repeticiones del plan.
     * @param targetRir reserva objetivo del plan.
     * @param availableLoads cargas montables con el material declarado, de cualquier lado de la
     * actual; se usan las de la dirección pedida y la actual (mantener). Nunca se devuelve otra.
     */
    fun select(
        sets: List<SetEvidence>,
        currentLoadKg: Double,
        repMin: Int,
        repMax: Int,
        targetRir: Int,
        direction: Direction,
        availableLoads: List<Double>,
        region: StrengthRegion = StrengthRegion.UNSPECIFIED,
    ): LoadTarget? {
        if (!currentLoadKg.isFinite() || currentLoadKg <= 0.0 || repMin < 1 || repMax < repMin) return null
        val e1rm = exposureE1rm(sets, region)
        if (e1rm <= 0.0) return null
        val reserveTarget = targetRir.coerceAtLeast(0)
        val lastMinReps = sets.filter { it.reps > 0 }.minOfOrNull { it.reps } ?: return null

        val loads = (availableLoads + currentLoadKg)
            .filter { it.isFinite() && it > 0.0 }
            .filter {
                when (direction) {
                    Direction.INCREASE -> it >= currentLoadKg - LOAD_TOLERANCE_KG
                    Direction.REDUCE -> it <= currentLoadKg + LOAD_TOLERANCE_KG
                }
            }
            .sorted()
            .fold(mutableListOf<Double>()) { unique, load ->
                if (unique.isEmpty() || load - unique.last() > LOAD_TOLERANCE_KG) unique += load
                unique
            }

        var best: LoadTarget? = null
        var bestDistance = Double.MAX_VALUE
        for (load in loads) {
            val failure = StrengthModel.repsToFailureAt(load, e1rm, region)
            if (failure <= 0.0) continue
            val raw = failure - reserveTarget
            val reps = floor(raw + FIT_TOLERANCE_REPS + REPS_EPSILON).toInt().coerceIn(max(1, repMin), repMax)
            val shortfall = max(0.0, repMin - raw - FIT_TOLERANCE_REPS)
            val surplus = max(0.0, raw - repMax)
            val isCurrent = abs(load - currentLoadKg) <= LOAD_TOLERANCE_KG
            val moveCost = if (isCurrent) STAY_WEIGHT else STEP_WEIGHT * abs(load - currentLoadKg) / currentLoadKg
            val score = SHORTFALL_WEIGHT * shortfall + SURPLUS_WEIGHT * surplus + moveCost
            val distance = abs(load - currentLoadKg)
            val better = best == null || score < best.score - 1e-9 ||
                (abs(score - best.score) <= 1e-9 && distance < bestDistance - LOAD_TOLERANCE_KG)
            if (!better) continue
            val expectedRir = failure - reps
            val fitsRange = raw >= repMin - FIT_TOLERANCE_REPS && raw <= repMax + REPS_EPSILON
            best = LoadTarget(
                kg = load,
                reps = reps,
                expectedRir = expectedRir,
                reserve = if (fitsRange) reserveTarget else expectedRir.roundToInt().coerceAtLeast(0),
                change = when {
                    isCurrent -> Change.KEEP
                    load > currentLoadKg -> Change.INCREASE
                    else -> Change.REDUCE
                },
                repsOverLast = reps - lastMinReps,
                e1rmKg = e1rm,
                score = score,
            )
            bestDistance = distance
        }
        return best
    }
}
