package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Afinado del plan de alimentación en el alta: lógica pura (sin Android ni textos de interfaz) que
 * liga las kcal, los tres macros y el ritmo semanal en los DOS sentidos y calcula los avisos.
 *
 * Todo cuelga de una sola fuente de verdad, los números enteros de [PlanValues]:
 * - **Macros → kcal**: mover un macro recalcula el total con Atwater 4/4/9 ([atwaterKcal]); los otros dos
 *   no se tocan ([editSingleMacro]).
 * - **Ritmo → kcal → macros**: el ritmo semanal fija las kcal (EER ± ajuste) y los TRES macros se reescalan
 *   con [scaleMacrosToCalories], igual que el editor directo al editar calorías.
 * - **Kcal → ritmo**: el ritmo que se muestra SIEMPRE sale de las kcal reales frente al EER
 *   ([weeklyChangeFor], 7700 kcal/kg); nunca se guarda aparte, así que un cambio de macros mueve el ritmo.
 *
 * Los umbrales de aviso son los de [buildNutritionRiskFlags]; aquí solo se evalúan en vivo sobre los
 * valores que se están tocando.
 */

/** Zona del ritmo semanal: del cambio de peso por semana, no de la dirección del plan. */
enum class PaceZone {
    /** Sin referencia (sin EER o peso) o cambio prácticamente nulo: mantenimiento. */
    NONE,
    SUSTAINABLE,
    DEMANDING,
    AGGRESSIVE,
    EXTREME,
}

/** Aviso en vivo del plan. La gravedad reutiliza [RiskSeverity] de los avisos de riesgo existentes. */
enum class PlanWarning(val severity: RiskSeverity) {
    LOW_CALORIES_SOFT(RiskSeverity.WARNING),
    LOW_CALORIES_HARD(RiskSeverity.DANGER),
    PACE_AGGRESSIVE(RiskSeverity.WARNING),
    PACE_EXTREME(RiskSeverity.DANGER),
    LOW_PROTEIN(RiskSeverity.WARNING),
    LOW_FAT(RiskSeverity.WARNING),
}

/** Macro que se puede afinar. */
enum class PlanMacro { PROTEIN, CARBS, FAT }

/** Reglas numéricas del afinado. Los umbrales de ritmo y calorías son los de [buildNutritionRiskFlags]. */
object PlanTuningRules {
    const val PROTEIN_MIN_G_PER_KG = 0.8
    const val PROTEIN_MAX_G_PER_KG = 3.0

    /** Por debajo de este aporte (g/kg) un déficit avisa de proteína baja. */
    const val PROTEIN_LOW_G_PER_KG = 1.2

    const val FAT_MIN_G_PER_KG = 0.5
    const val FAT_MIN_ENERGY_SHARE = 0.15
    const val FAT_MAX_ENERGY_SHARE = 0.45

    /** Por debajo de esta parte de la energía las grasas avisan de «muy bajas». */
    const val FAT_LOW_ENERGY_SHARE = 0.20

    const val LOSS_AGGRESSIVE_KG_PER_WEEK = 1.0
    const val LOSS_EXTREME_KG_PER_WEEK = 1.5
    const val GAIN_AGGRESSIVE_KG_PER_WEEK = 0.5
    const val GAIN_EXTREME_KG_PER_WEEK = 0.75

    /** El «sostenible» llega hasta la mitad del umbral agresivo (0,5 kg/sem al perder, 0,25 al ganar). */
    private const val SUSTAINABLE_SHARE_OF_AGGRESSIVE = 0.5

    /** Menos de esto (kg/sem, ≈55 kcal/día) se considera mantenimiento. */
    const val PACE_NEUTRAL_KG_PER_WEEK = 0.05

    /** Rango mínimo de ritmo para que tenga sentido ofrecer el control. */
    const val PACE_MIN_RANGE_KG_PER_WEEK = 0.05

    /** Margen (kcal) bajo el mantenimiento a partir del cual se habla de déficit real. */
    const val DEFICIT_TOLERANCE_KCAL = 50.0

    /** Suelo de las calorías recomendadas por el motor automático. */
    const val ENGINE_MIN_KCAL = 1200.0

    /** Peso de referencia solo para dimensionar los deslizadores cuando no hay peso. */
    const val NOMINAL_WEIGHT_KG = 70.0

    /** Margen de energía sobre la base cuando no hay EER (objetivos propios sin datos). */
    private const val NO_EER_HEADROOM_KCAL = 1500
    private const val NO_EER_MIN_CEILING_KCAL = 3500

    /** Calorías bajo las que se avisa (suave) según el sexo de la ecuación; sexo desconocido o promedio: el estricto. */
    fun lowCaloriesSoft(sex: EerSex?): Int = if (sex == EerSex.FEMALE) 1200 else 1500

    /** Calorías bajo las que se detiene el plan (duro) según el sexo de la ecuación; sexo desconocido: el estricto. */
    fun lowCaloriesHard(sex: EerSex?): Int = if (sex == EerSex.FEMALE) 1000 else 1200

    /** Límite superior de la zona sostenible (kg/sem) para pérdida o ganancia. */
    fun sustainableKgPerWeek(loss: Boolean): Double =
        (if (loss) LOSS_AGGRESSIVE_KG_PER_WEEK else GAIN_AGGRESSIVE_KG_PER_WEEK) * SUSTAINABLE_SHARE_OF_AGGRESSIVE

    fun aggressiveKgPerWeek(loss: Boolean): Double =
        if (loss) LOSS_AGGRESSIVE_KG_PER_WEEK else GAIN_AGGRESSIVE_KG_PER_WEEK

    fun extremeKgPerWeek(loss: Boolean): Double =
        if (loss) LOSS_EXTREME_KG_PER_WEEK else GAIN_EXTREME_KG_PER_WEEK

    internal fun noEerCeilingKcal(referenceKcal: Int): Int =
        max(referenceKcal + NO_EER_HEADROOM_KCAL, NO_EER_MIN_CEILING_KCAL)
}

/**
 * Los cuatro números que el plan lleva: kcal objetivo y gramos de cada macro. Son lo que se escribe en el
 * borrador y, por tanto, lo que se activa. [kcal] puede no coincidir con la suma Atwater cuando la persona
 * escribió valores propios incoherentes; mover un macro la rederiva.
 */
data class PlanValues(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    /** Energía de los gramos de macros con los factores Atwater 4/4/9. */
    val atwaterKcal: Int get() = 4 * proteinG + 4 * carbsG + 9 * fatG

    fun gramsOf(macro: PlanMacro): Int = when (macro) {
        PlanMacro.PROTEIN -> proteinG
        PlanMacro.CARBS -> carbsG
        PlanMacro.FAT -> fatG
    }

    fun withGrams(macro: PlanMacro, grams: Int): PlanValues = when (macro) {
        PlanMacro.PROTEIN -> copy(proteinG = grams)
        PlanMacro.CARBS -> copy(carbsG = grams)
        PlanMacro.FAT -> copy(fatG = grams)
    }
}

/**
 * Datos del perfil que no cambian al afinar: peso, gasto de mantenimiento (EER), dirección y sexo de la
 * ecuación (que solo decide los umbrales de calorías bajas). Cualquiera puede faltar.
 */
data class PlanTuningContext(
    val weightKg: Double?,
    val eerKcal: Double?,
    val direction: PlanDirection?,
    val sex: EerSex?,
) {
    /** Peso válido o null: nunca se fabrica uno. */
    val knownWeightKg: Double? get() = weightKg?.takeIf { it.isFinite() && it > 0.0 }

    /** EER válido o null: sin él no hay ritmo, ni marca de mantenimiento, ni control de ritmo. */
    val knownEerKcal: Double? get() = eerKcal?.takeIf { it.isFinite() && it > 0.0 }
}

/** Rango de gramos de un deslizador (ambos extremos incluidos). */
data class MacroRange(val minG: Int, val maxG: Int) {
    init {
        require(minG <= maxG) { "Rango invertido: $minG..$maxG" }
    }

    operator fun contains(grams: Int): Boolean = grams in minG..maxG
}

/** Límites de los tres deslizadores y la energía máxima común. No dependen de los valores que se tocan. */
data class PlanTuningLimits(
    val protein: MacroRange,
    val carbs: MacroRange,
    val fat: MacroRange,
    /** Energía máxima (kcal) del plan: tope común de la suma Atwater de los tres macros. */
    val ceilingKcal: Int,
) {
    fun of(macro: PlanMacro): MacroRange = when (macro) {
        PlanMacro.PROTEIN -> protein
        PlanMacro.CARBS -> carbs
        PlanMacro.FAT -> fat
    }
}

/** Una muesca con nombre del control de ritmo: el ritmo (kg/sem, en magnitud) de un preset. */
data class PaceNotch(val preset: WizardPacePreset, val kgPerWeek: Double)

/**
 * Control de ritmo disponible para un plan de definir o volumen con EER y peso: de 0 al máximo que
 * permiten los límites de [calorieBoundsFor] (en magnitud, hacia el lado de [direction]) y las muescas
 * Lento · Medio · Rápido que caben en ese rango.
 */
data class PaceControl(
    val direction: PlanDirection,
    val maxKgPerWeek: Double,
    val notches: List<PaceNotch>,
)

/**
 * Plan afinable: [values] son los números en vivo y [baseline] aquellos a los que vuelve [reset] (la
 * recomendación, o lo que había al abrir la pantalla en objetivos propios). Inmutable: cada operación
 * devuelve un plan nuevo.
 */
data class PlanTuning(
    val context: PlanTuningContext,
    val baseline: PlanValues,
    val values: PlanValues,
) {
    val kcal: Int get() = values.kcal
    val proteinG: Int get() = values.proteinG
    val carbsG: Int get() = values.carbsG
    val fatG: Int get() = values.fatG

    val proteinKcal: Int get() = 4 * values.proteinG
    val carbsKcal: Int get() = 4 * values.carbsG
    val fatKcal: Int get() = 9 * values.fatG

    /** % de la energía de cada macro (suman 100 salvo que no haya energía): proteína, hidratos, grasas. */
    val energyPercents: Triple<Int, Int, Int>
        get() = percentsOfEnergy(values.proteinG, values.carbsG, values.fatG)

    val proteinPercent: Int get() = energyPercents.first
    val carbsPercent: Int get() = energyPercents.second
    val fatPercent: Int get() = energyPercents.third

    /** Proteína en g/kg; null sin peso. */
    val proteinPerKg: Double? get() = context.knownWeightKg?.let { values.proteinG / it }

    /** true si los números difieren de [baseline]: lo que habilita «restablecer». */
    val isEdited: Boolean get() = values != baseline

    // ── Ritmo: SIEMPRE derivado de las kcal reales frente al EER ────────────────────────────────────

    /** Cambio de peso por semana (kg, con signo: negativo adelgaza); null sin EER o sin peso. */
    val weeklyChangeKg: Double?
        get() {
            val eer = context.knownEerKcal ?: return null
            val weight = context.knownWeightKg ?: return null
            return weeklyChangeFor(GoalMetric.WEIGHT, values.kcal, eer, weight)
        }

    /** Cambio semanal en % del peso corporal (con signo); null sin EER o sin peso. */
    val weeklyPercentBodyWeight: Double?
        get() {
            val eer = context.knownEerKcal ?: return null
            val weight = context.knownWeightKg ?: return null
            return realRateFor(values.kcal, eer, weight)?.times(100.0)
        }

    /** Cambio proyectado (kg, con signo) tras [weeks] semanas al ritmo actual; null sin referencia. */
    fun projectedChangeKg(weeks: Int): Double? = weeklyChangeKg?.times(weeks)

    val paceZone: PaceZone
        get() {
            val change = weeklyChangeKg ?: return PaceZone.NONE
            val magnitude = abs(change)
            if (magnitude < PlanTuningRules.PACE_NEUTRAL_KG_PER_WEEK) return PaceZone.NONE
            val loss = change < 0.0
            return when {
                magnitude > PlanTuningRules.extremeKgPerWeek(loss) -> PaceZone.EXTREME
                magnitude > PlanTuningRules.aggressiveKgPerWeek(loss) -> PaceZone.AGGRESSIVE
                magnitude > PlanTuningRules.sustainableKgPerWeek(loss) -> PaceZone.DEMANDING
                else -> PaceZone.SUSTAINABLE
            }
        }

    // ── Avisos ──────────────────────────────────────────────────────────────────────────────────────

    /** Avisos vigentes, de más a menos grave. Las calorías duras sustituyen a las suaves. */
    val warnings: List<PlanWarning>
        get() {
            val found = mutableListOf<PlanWarning>()
            val sex = context.sex
            val hard = PlanTuningRules.lowCaloriesHard(sex)
            val soft = PlanTuningRules.lowCaloriesSoft(sex)
            if (values.kcal < hard) found += PlanWarning.LOW_CALORIES_HARD
            when (paceZone) {
                PaceZone.EXTREME -> found += PlanWarning.PACE_EXTREME
                PaceZone.AGGRESSIVE -> found += PlanWarning.PACE_AGGRESSIVE
                else -> Unit
            }
            if (values.kcal in hard until soft) found += PlanWarning.LOW_CALORIES_SOFT
            if (isLowProtein()) found += PlanWarning.LOW_PROTEIN
            if (isLowFat()) found += PlanWarning.LOW_FAT
            // Estable: a igual gravedad se conserva el orden de evaluación.
            return found.sortedByDescending { it.severity.ordinal }
        }

    /**
     * El plan no debería continuar: calorías por debajo del umbral duro, o una pérdida más rápida que el
     * umbral extremo. Una ganancia extrema avisa pero NO detiene (igual que [buildNutritionRiskFlags]).
     */
    val hardStop: Boolean
        get() {
            val found = warnings
            return PlanWarning.LOW_CALORIES_HARD in found ||
                (PlanWarning.PACE_EXTREME in found && (weeklyChangeKg ?: 0.0) < 0.0)
        }

    /** Déficit real: bajo el mantenimiento más un margen; sin EER, la dirección elegida. */
    private fun isDeficitNow(): Boolean {
        val eer = context.knownEerKcal ?: return context.direction == PlanDirection.DEFICIT
        return values.kcal < eer - PlanTuningRules.DEFICIT_TOLERANCE_KCAL
    }

    private fun isLowProtein(): Boolean {
        val weight = context.knownWeightKg ?: return false
        return isDeficitNow() && values.proteinG / weight < PlanTuningRules.PROTEIN_LOW_G_PER_KG
    }

    private fun isLowFat(): Boolean {
        val energy = values.atwaterKcal
        return energy > 0 && values.fatG * 9.0 / energy < PlanTuningRules.FAT_LOW_ENERGY_SHARE
    }

    // ── Límites ─────────────────────────────────────────────────────────────────────────────────────

    /** Límites de los deslizadores: estables (no cambian al mover los macros) para que los anillos no bailen. */
    val limits: PlanTuningLimits get() = tuningLimitsOf(context, baseline.kcal)

    /** Control de ritmo, o null si no aplica (mantenimiento, sin EER o peso, o sin recorrido posible). */
    val paceControl: PaceControl?
        get() {
            val direction = context.direction
            if (direction != PlanDirection.DEFICIT && direction != PlanDirection.SURPLUS) return null
            val eer = context.knownEerKcal ?: return null
            val weight = context.knownWeightKg ?: return null
            val bounds = calorieBoundsFor(direction, eer) ?: return null
            val maxDeltaKcal = if (direction == PlanDirection.DEFICIT) eer - bounds.first else bounds.last - eer
            val maxKg = maxDeltaKcal * 7.0 / KCAL_PER_KG_FAT
            if (maxKg < PlanTuningRules.PACE_MIN_RANGE_KG_PER_WEEK) return null
            val notches = WizardPacePreset.entries.mapNotNull { preset ->
                val rate = paceRateFor(direction, preset) ?: return@mapNotNull null
                val kg = rate * weight
                if (kg <= maxKg + 1e-9) PaceNotch(preset, kg) else null
            }
            return PaceControl(direction, maxKg, notches)
        }

    // ── Operaciones ─────────────────────────────────────────────────────────────────────────────────

    fun withProtein(grams: Int): PlanTuning = withMacro(PlanMacro.PROTEIN, grams)

    fun withCarbs(grams: Int): PlanTuning = withMacro(PlanMacro.CARBS, grams)

    fun withFat(grams: Int): PlanTuning = withMacro(PlanMacro.FAT, grams)

    /**
     * Mueve UN macro: los otros dos quedan intactos y las kcal pasan a ser su suma Atwater. El valor se
     * limita a su rango y a la energía máxima común: tocar un macro lo lleva al rango (también uno propio por
     * debajo del mínimo) salvo que la energía no dé para tanto, y nunca se impide BAJAR un macro que ya
     * estaba fuera de rango ni se sube por encima de donde estaba si ya lo superaba. Así un macro movido a
     * mano jamás lleva la suma por encima de la energía máxima (salvo que ya la superara).
     */
    fun withMacro(macro: PlanMacro, grams: Int): PlanTuning {
        val range = limits.of(macro)
        val reach = reachableMaxOf(macro)
        val target = grams.coerceIn(min(range.minG, reach), reach)
        val next = values.withGrams(macro, target)
        // Volver a los mismos gramos de la base devuelve también sus kcal exactas (la recomendación del
        // motor redondea cada macro por separado y su suma puede diferir unas kcal de su total).
        val sameAsBaseline = next.proteinG == baseline.proteinG && next.carbsG == baseline.carbsG &&
            next.fatG == baseline.fatG
        return copy(values = next.copy(kcal = if (sameAsBaseline) baseline.kcal else next.atwaterKcal))
    }

    /**
     * Gramos máximos a los que se puede llevar [macro] AHORA: su rango y la energía máxima con los otros dos
     * macros tal como están. Nunca queda por debajo de lo que el macro ya tiene (un valor propio por
     * encima del tope se puede bajar, pero no subir). Puede quedar por debajo del mínimo del rango cuando
     * el macro ya estaba por debajo y la energía no deja subirlo hasta él.
     */
    fun reachableMaxOf(macro: PlanMacro): Int {
        val bounds = limits
        val range = bounds.of(macro)
        val byEnergy = energyCapOf(macro, bounds.ceilingKcal)
        return max(min(range.maxG, byEnergy), values.gramsOf(macro))
    }

    /**
     * Fija el ritmo semanal (kg/sem, en magnitud hacia el lado de la dirección del plan): las kcal pasan a
     * ser EER ± ajuste, dentro de [calorieBoundsFor], y los tres macros se reescalan. Sin [paceControl]
     * no hace nada.
     */
    fun withPaceRate(magnitudeKgPerWeek: Double): PlanTuning {
        val control = paceControl ?: return this
        val eer = context.knownEerKcal ?: return this
        if (!magnitudeKgPerWeek.isFinite()) return this
        val bounds = calorieBoundsFor(control.direction, eer) ?: return this
        val magnitude = magnitudeKgPerWeek.coerceIn(0.0, control.maxKgPerWeek)
        val delta = magnitude * KCAL_PER_KG_FAT / 7.0
        val raw = if (control.direction == PlanDirection.DEFICIT) eer - delta else eer + delta
        return rescaledTo(raw.roundToInt().coerceIn(bounds.first, bounds.last))
    }

    /** Lleva el ritmo a la muesca del [preset]; no hace nada si esa muesca no cabe en el rango. */
    fun withPreset(preset: WizardPacePreset): PlanTuning {
        val notch = paceControl?.notches?.firstOrNull { it.preset == preset } ?: return this
        return withPaceRate(notch.kgPerWeek)
    }

    /** Vuelve a los valores de [baseline]. */
    fun reset(): PlanTuning = copy(values = baseline)

    /** Reescala los tres macros a [kcal] y fija el total: los ceros manuales se conservan. */
    private fun rescaledTo(kcal: Int): PlanTuning {
        if (values.atwaterKcal <= 0) return this
        val (protein, carbs, fat) = scaleMacrosToCalories(
            values.proteinG.toDouble(),
            values.carbsG.toDouble(),
            values.fatG.toDouble(),
            kcal,
        )
        return copy(values = PlanValues(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat))
    }

    /** Gramos máximos de [macro] que caben bajo [ceilingKcal] con los otros dos tal como están. */
    private fun energyCapOf(macro: PlanMacro, ceilingKcal: Int): Int = when (macro) {
        PlanMacro.PROTEIN -> floor((ceilingKcal - 4 * values.carbsG - 9 * values.fatG) / 4.0).toInt()
        PlanMacro.CARBS -> floor((ceilingKcal - 4 * values.proteinG - 9 * values.fatG) / 4.0).toInt()
        PlanMacro.FAT -> floor((ceilingKcal - 4 * values.proteinG - 4 * values.carbsG) / 9.0).toInt()
    }

    companion object {
        /** Plan sin ediciones: [values] = [baseline]. */
        fun of(context: PlanTuningContext, values: PlanValues): PlanTuning = PlanTuning(context, values, values)

        /**
         * Plan recomendado con el ritmo de [preset]; null si no se puede recomendar (sin EER, peso o una
         * dirección de definir, mantener o volumen).
         */
        fun recommended(context: PlanTuningContext, preset: WizardPacePreset): PlanTuning? =
            recommendedPlanValues(context, preset)?.let { of(context, it) }
    }
}

// ─── Recomendación automática ───────────────────────────────────────────────────────────────────────

/**
 * Lo que el motor ([NutritionEnergyEngine.recommendPlan]) recomienda SIN ediciones y con su ritmo por
 * defecto, ya en los enteros que el editor muestra y guarda ([reviewedBaseOf]). Es la misma cuenta:
 * un test la compara con el motor para que no se desvíen.
 *
 * Null sin EER, sin peso o con una dirección que no es definir, mantener o volumen.
 */
fun automaticPlanValues(context: PlanTuningContext): PlanValues? {
    val direction = context.direction ?: return null
    if (direction == PlanDirection.PROFESSIONAL) return null
    val eer = context.knownEerKcal ?: return null
    val weight = context.knownWeightKg ?: return null
    val rate = paceRateFor(direction, WizardPacePreset.MEDIUM)
    val adjustment = if (rate == null) 0.0 else weight * rate * KCAL_PER_KG_FAT / 7.0
    val automatic = eer + when (direction) {
        PlanDirection.DEFICIT -> -adjustment
        PlanDirection.SURPLUS -> adjustment
        else -> 0.0
    }
    val target = automatic.coerceAtLeast(PlanTuningRules.ENGINE_MIN_KCAL)
    val macros = NutritionEnergyEngine.calculateMacros(target, weight, direction) ?: return null
    return PlanValues(
        kcal = target.roundToInt(),
        proteinG = macros.proteinG.roundToInt().coerceAtLeast(0),
        carbsG = macros.carbsG.roundToInt().coerceAtLeast(0),
        fatG = macros.fatG.roundToInt().coerceAtLeast(0),
    )
}

/**
 * La recomendación con el ritmo del [preset]: la automática (ritmo medio, la del motor) reescalada a las
 * kcal del preset con el mismo cálculo que el control de ritmo. Con el preset medio es exactamente la
 * automática; en mantenimiento no hay ritmo y también lo es.
 */
fun recommendedPlanValues(context: PlanTuningContext, preset: WizardPacePreset): PlanValues? {
    val automatic = automaticPlanValues(context) ?: return null
    val direction = context.direction ?: return automatic
    val weight = context.knownWeightKg ?: return automatic
    val rate = paceRateFor(direction, preset) ?: return automatic
    if (preset == WizardPacePreset.MEDIUM) return automatic
    return PlanTuning.of(context, automatic).withPaceRate(rate * weight).values
}

/**
 * Valores con los que sembrar el borrador cuando el ritmo elegido en el paso anterior no es el que el
 * motor aplica por sí solo (el motor no lee el preset): null si ya coinciden o no hay recomendación.
 */
fun presetSeedValues(context: PlanTuningContext, preset: WizardPacePreset): PlanValues? {
    val recommended = recommendedPlanValues(context, preset) ?: return null
    val automatic = automaticPlanValues(context) ?: return null
    return recommended.takeIf { it != automatic }
}

// ─── Límites ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Límites de los deslizadores para un plan con energía de referencia [referenceKcal] (la de la base: no
 * cambia al mover los macros):
 * - proteína: 0,8–3,0 g/kg;
 * - grasas: del mayor entre 0,5 g/kg y el 15 % de la energía, al 45 % de la energía;
 * - hidratos: de 0 a lo que deja la energía máxima con la proteína y las grasas mínimas.
 * La energía máxima es el mantenimiento al definir y el tope de [calorieBoundsFor] al mantener o ganar.
 */
internal fun tuningLimitsOf(context: PlanTuningContext, referenceKcal: Int): PlanTuningLimits {
    val ceiling = ceilingKcalOf(context, referenceKcal)
    val weight = context.knownWeightKg ?: PlanTuningRules.NOMINAL_WEIGHT_KG
    val proteinMin = ceilGrams(PlanTuningRules.PROTEIN_MIN_G_PER_KG * weight)
    val fatMin = max(
        ceilGrams(PlanTuningRules.FAT_MIN_G_PER_KG * weight),
        ceilGrams(PlanTuningRules.FAT_MIN_ENERGY_SHARE * referenceKcal / 9.0),
    )
    val proteinMax = max(
        min(
            floorGrams(PlanTuningRules.PROTEIN_MAX_G_PER_KG * weight),
            floorGrams((ceiling - 9 * fatMin) / 4.0),
        ),
        proteinMin,
    )
    val fatMax = max(
        min(
            floorGrams(PlanTuningRules.FAT_MAX_ENERGY_SHARE * referenceKcal / 9.0),
            floorGrams((ceiling - 4 * proteinMin) / 9.0),
        ),
        fatMin,
    )
    val carbsMax = max(floorGrams((ceiling - 4 * proteinMin - 9 * fatMin) / 4.0), 0)
    return PlanTuningLimits(
        protein = MacroRange(proteinMin, proteinMax),
        carbs = MacroRange(0, carbsMax),
        fat = MacroRange(fatMin, fatMax),
        ceilingKcal = ceiling,
    )
}

/** Redondeos que ignoran el ruido de coma flotante (0,8 × 85 = 68,00000000000001 no debe dar 69 g). */
private fun ceilGrams(value: Double): Int = ceil(value - 1e-9).toInt()

private fun floorGrams(value: Double): Int = floor(value + 1e-9).toInt()

private fun ceilingKcalOf(context: PlanTuningContext, referenceKcal: Int): Int {
    val eer = context.knownEerKcal
    val byEnergy = if (eer == null) {
        PlanTuningRules.noEerCeilingKcal(referenceKcal)
    } else {
        when (context.direction) {
            PlanDirection.DEFICIT -> floor(eer).toInt()
            PlanDirection.MAINTENANCE ->
                calorieBoundsFor(PlanDirection.MAINTENANCE, eer)?.last ?: floor(eer).toInt()
            PlanDirection.SURPLUS ->
                calorieBoundsFor(PlanDirection.SURPLUS, eer)?.last ?: floor(eer).toInt()
            PlanDirection.PROFESSIONAL, null ->
                (calorieBoundsFor(PlanDirection.SURPLUS, eer)?.last ?: floor(eer).toInt())
        }
    }
    return max(byEnergy, referenceKcal)
}

// ─── Porcentajes ────────────────────────────────────────────────────────────────────────────────────

/**
 * % de la energía de proteína, hidratos y grasas (4/4/9) como enteros que suman 100: se reparte por resto
 * mayor para que la suma de lo que se lee no baile entre 99 y 101. Sin energía, ceros.
 */
fun percentsOfEnergy(proteinG: Int, carbsG: Int, fatG: Int): Triple<Int, Int, Int> {
    val energies = doubleArrayOf(4.0 * proteinG, 4.0 * carbsG, 9.0 * fatG)
    val total = energies.sum()
    if (total <= 0.0) return Triple(0, 0, 0)
    val exact = energies.map { it * 100.0 / total }
    val floors = exact.map { floor(it).toInt() }.toIntArray()
    var remainder = 100 - floors.sum()
    val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
    var index = 0
    while (remainder > 0) {
        floors[order[index % order.size]] += 1
        index++
        remainder--
    }
    return Triple(floors[0], floors[1], floors[2])
}
