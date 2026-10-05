package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import kotlin.math.floor

/**
 * Resuelve el porcentaje de una serie. Es puro: lo comparten el materializador,
 * que lo convierte en kg, y la política de composición, que lo convierte en
 * %1RM para los chequeos de intensidad a través de [PercentBasis]. Así las dos miden con la
 * misma regla y no pueden discrepar.
 *
 * El resolutor solo transforma `PERCENT_OF_TOP_SET`: cuelga del top set de la
 * semana (Texas: lunes 5×5 al 90 % del top del viernes, miércoles 2×5 al 80 %
 * del lunes) y se devuelve relativo al TM, la misma unidad que `PERCENT_TM`.
 * Cualquier otra base se devuelve tal cual, en su propia unidad: `PERCENT_TM`
 * queda en %TM, y `PERCENT_1RM` y `PERCENT_DESIRED_MAX` quedan en %1RM (no se
 * reexpresan respecto del TM). La conversión a %1RM la hace [PercentBasis].
 */
object PercentResolver {
    /** Ancla cuando la semana no declara ningún top set de ese levantamiento. */
    private const val DEFAULT_ANCHOR_PERCENT = 100.0

    /** Factor del slot de volumen cuando la semana no tiene ninguno (Texas: 90 %). */
    private const val DEFAULT_VOLUME_FACTOR_PERCENT = 90.0

    /** Un slot sin top set y con al menos tantas series de trabajo es un slot de volumen. */
    private const val VOLUME_SLOT_MIN_WORKING_SETS = 5

    /**
     * Porcentaje de [set] en la unidad de su base (para `PERCENT_OF_TOP_SET`, respecto del TM),
     * o null si la serie no lleva porcentaje.
     *
     * Para `PERCENT_OF_TOP_SET`:
     * - el propio top set se devuelve tal cual;
     * - el **ancla** es el top set del propio slot; si no tiene, el primer top
     *   set de otro slot del mismo levantamiento en la misma semana (los días
     *   en orden); si no hay ninguno, 100;
     * - un slot de volumen (sin top set y con 5 o más series de trabajo)
     *   cuelga directamente del ancla: `porcentaje ÷ 100 × ancla`;
     * - cualquier otro slot (Texas miércoles) cuelga del slot de volumen, y el
     *   factor del volumen es su serie de trabajo **más pesada** (la primera
     *   era una rampa ligera y escalaba mal): `porcentaje ÷ 100 × factor ÷ 100 × ancla`.
     */
    fun resolve(set: SetRecipe, slot: SlotRecipe, week: WeekRecipe): Double? {
        val raw = set.percent ?: return null
        if (set.loadBasis != LoadBasis.PERCENT_OF_TOP_SET) return raw
        if (set.isTopSet) return raw
        val sameLift = sameLiftSlots(slot, week)
        val anchor = anchorOf(slot, sameLift)
        if (isVolumeSlot(slot)) return raw / 100.0 * anchor
        val volumeFactor = sameLift.firstOrNull { isVolumeSlot(it) }?.heaviestWorkingPercent()
            ?: DEFAULT_VOLUME_FACTOR_PERCENT
        return raw / 100.0 * (volumeFactor / 100.0 * anchor)
    }

    /** Slots de la semana que trabajan el mismo levantamiento, en el orden de los días. */
    private fun sameLiftSlots(slot: SlotRecipe, week: WeekRecipe): List<SlotRecipe> {
        val liftSlot = slot.lift.liftSlot ?: return emptyList()
        return week.days.flatMap { day -> day.slots.filter { it.lift.liftSlot == liftSlot } }
    }

    private fun anchorOf(slot: SlotRecipe, sameLift: List<SlotRecipe>): Double =
        slot.sets.firstOrNull { it.isTopSet }?.percent
            ?: sameLift.firstNotNullOfOrNull { other -> other.sets.firstOrNull { it.isTopSet }?.percent }
            ?: DEFAULT_ANCHOR_PERCENT

    private fun isVolumeSlot(slot: SlotRecipe): Boolean =
        slot.sets.none { it.isTopSet } &&
            slot.sets.count { !it.isWarmup } >= VOLUME_SLOT_MIN_WORKING_SETS

    private fun SlotRecipe.heaviestWorkingPercent(): Double? =
        sets.filter { !it.isWarmup }.mapNotNull { it.percent }.maxOrNull()
}

/**
 * Base de porcentaje común: convierte la prescripción de una serie en %1RM
 * efectivo y calcula cuántas repeticiones caben a ese porcentaje. Lo usan
 * H5a/H8/H9/H11/H11b/W3/W4/BLOCK/taper de [SessionCompositionPolicy] para medir
 * kg resueltos y no el valor crudo de la receta.
 */
object PercentBasis {
    /** TM por defecto (90 % del 1RM) cuando la receta no declara un `trainingMaxPercent` válido. */
    private const val DEFAULT_TM_FRACTION = 0.90

    /** Margen contra el redondeo de coma flotante en los límites exactos (75 % da 10,0 en papel y 9,999… en el ordenador). */
    private const val EPLEY_EPSILON = 1e-9

    /** Referencias de carga que NO se expresan sobre el 1RM: trabajo observado y lastre/asistencia externa. */
    private val NON_1RM_REFERENCE_KINDS = setOf(
        PlanLoadReferenceKind.OBSERVED_WORKING_SET,
        PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
    )

    /** Fracción del 1RM que representa el TM de la receta (0,87 = TM al 87 % del 1RM). */
    fun tmFraction(trainingMaxPercent: Double): Double =
        if (trainingMaxPercent > 0.0) trainingMaxPercent else DEFAULT_TM_FRACTION

    /**
     * %1RM efectivo de [set], o null si no se puede expresar sobre el 1RM
     * (`RPE`, `REP_MAX`, serie sin porcentaje o serie con una referencia de carga
     * explícita de trabajo observado o de lastre).
     * - `PERCENT_1RM` y `PERCENT_DESIRED_MAX`: el porcentaje tal cual.
     * - `PERCENT_TM`: porcentaje × fracción de TM de la receta.
     * - `PERCENT_OF_TOP_SET`: el porcentaje que resuelve [PercentResolver] × fracción de TM.
     *
     * Una serie con [SetRecipe.reference] `OBSERVED_WORKING_SET` o `BODYWEIGHT_EXTERNAL` se
     * expresa sobre el trabajo observado o el lastre, no sobre el 1RM (§14.2): devuelve null
     * y H11/H11b no la miden.
     */
    fun effective1RmPercent(
        set: SetRecipe,
        slot: SlotRecipe,
        week: WeekRecipe,
        trainingMaxPercent: Double,
    ): Double? {
        val raw = set.percent ?: return null
        val referenceKind = set.reference?.kind
        if (referenceKind != null && referenceKind in NON_1RM_REFERENCE_KINDS) return null
        val tmFraction = tmFraction(trainingMaxPercent)
        return when (set.loadBasis) {
            LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> raw
            LoadBasis.PERCENT_TM -> raw * tmFraction
            LoadBasis.PERCENT_OF_TOP_SET -> PercentResolver.resolve(set, slot, week)?.let { it * tmFraction }
            LoadBasis.RPE, LoadBasis.REP_MAX -> null
        }
    }

    /**
     * Repeticiones máximas que caben al [percentOf1Rm] % del 1RM según Epley
     * invertido: `floor(30 × (100 ÷ p − 1)) + 1`. A 100 % devuelve 1; a 90 %, 4;
     * a 87 % (un TM de 5RM), 5. Por encima del 100 % no cabe ni una y devuelve 0,
     * nunca un negativo.
     *
     * El resultado queda acotado a `[0, Int.MAX_VALUE - 1]`: con un porcentaje
     * minúsculo la cuenta se sale del rango de `Int` y no desborda. Solo un
     * porcentaje menor o igual que 0 (sin base) devuelve `Int.MAX_VALUE`, que
     * significa «sin límite».
     */
    fun maxRepsByEpley(percentOf1Rm: Double): Int {
        if (percentOf1Rm <= 0.0) return Int.MAX_VALUE
        val reps = 30.0 * (100.0 / percentOf1Rm - 1.0)
        // Se acota en Double antes de convertir: `toInt()` satura en Int.MAX_VALUE y el `+ 1` posterior lo desbordaba a negativo.
        return (floor(reps + EPLEY_EPSILON) + 1.0).coerceIn(0.0, (Int.MAX_VALUE - 1).toDouble()).toInt()
    }
}
