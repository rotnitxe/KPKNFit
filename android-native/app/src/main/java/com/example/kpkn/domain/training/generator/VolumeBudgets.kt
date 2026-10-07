package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Techos, objetivos y pisos de series semanales por músculo.
 *
 * Parten de las referencias globales de `VolumeLandmarks` (MEV/MAV/MRV) y de la calibración personal
 * (`VolumeCalibrationEngine`, con las cuatro respuestas 1..3 de la persona; `null` = no preguntado y se deduce del nivel),
 * igual que el generador histórico. Se cuentan series DIRECTAS (músculo principal, como `VolumeCalculator`) y hay dos topes:
 *
 * - **Techo duro = MRV** (volumen máximo recuperable): NUNCA se supera, ni con prioridades ni al rellenar tiempo.
 * - **Objetivo blando = MAV** (volumen máximo adaptativo): hasta ahí se AÑADEN series para llenar minutos; el programa
 *   puede pasarlo (sin llegar al MRV) cuando la estructura de la sesión lo pide, porque los compuestos cuentan como series
 *   directas para varios músculos a la vez (el glúteo suma en sentadilla, bisagra, zancada y puente): con el MAV como tope
 *   duro, una sesión de cuerpo completo se quedaría en uno o dos ejercicios.
 *
 * Los dos se escalan por nivel: el novato sube el volumen despacio (75 %), quien vuelve a entrenar usa el 85 % y el resto
 * el 100 %. Una prioridad sube las series de los huecos de su músculo (hasta el techo duro) y añade huecos extra (hasta el
 * objetivo blando).
 *
 * Dos ajustes del techo duro, por una razón de medida y no de recuperación:
 * - **Co-motores** ([COMOVER_FACTOR] ×1,5 al glúteo y a los erectores espinales): el catálogo los cuenta como motor principal
 *   (1,0) en sentadilla, bisagra, zancada, puente y remo con barra; la literatura de volumen cuenta esas series como ~0,5 para
 *   el glúteo. Sin este ajuste el MRV del glúteo (16) se agota con cinco ejercicios de pierna a la semana y los días de pierna
 *   se quedan en dos o tres ejercicios.
 * - **Funcional** ([FUNCTIONAL_CEILING_FACTOR] ×1,25 a todos): las series funcionales son submáximas (RIR 3–4, 8–15
 *   repeticiones) y el MRV de la literatura cuenta series «duras» (0–4 RIR); además cada sesión es de cuerpo completo.
 *
 * Las disciplinas escalan además los músculos que cargan de forma distinta (`DisciplineSpec.volumeScale`): el antebrazo del
 * armwrestling, el agarre y la espalda alta del strongman.
 */
internal class VolumeBudgets(
    val recommendations: List<VolumeRecommendation>,
    private val ceilings: Map<String, Int>,
    private val targets: Map<String, Int>,
    private val floors: Map<String, Int>,
    val style: TrainingStyle,
) {
    /** Techo duro semanal (MRV por nivel). */
    fun ceiling(muscle: String): Int = ceilings[muscle] ?: 0

    /** Objetivo blando semanal (MAV por nivel); nunca pasa del techo duro. */
    fun target(muscle: String): Int = minOf(targets[muscle] ?: 0, ceiling(muscle))

    /** Volumen mínimo efectivo (MEV) personal del músculo. */
    fun minimum(muscle: String): Int = floors[muscle] ?: 0
    val muscles: Set<String> get() = ceilings.keys

    companion object {
        private val LANDMARK_GROUP: Map<String, KpknMuscleGroup> = linkedMapOf(
            Muscles.CHEST to KpknMuscleGroup.CHEST,
            Muscles.LATS to KpknMuscleGroup.BACK_LATS,
            Muscles.TRAPS to KpknMuscleGroup.BACK_UPPER,
            Muscles.RHOMBOIDS to KpknMuscleGroup.BACK_UPPER,
            Muscles.QUADS to KpknMuscleGroup.QUADS,
            Muscles.HAMS to KpknMuscleGroup.HAMS,
            Muscles.GLUTES to KpknMuscleGroup.GLUTES,
            Muscles.DELTS to KpknMuscleGroup.DELT_LATERAL,
            Muscles.BICEPS to KpknMuscleGroup.BICEPS,
            Muscles.TRICEPS to KpknMuscleGroup.TRICEPS,
            Muscles.CALVES to KpknMuscleGroup.CALVES,
            Muscles.CORE to KpknMuscleGroup.CORE,
            Muscles.ABS to KpknMuscleGroup.CORE,
            Muscles.ERECTORS to KpknMuscleGroup.ERECTORS,
            Muscles.FOREARMS to KpknMuscleGroup.FOREARMS,
            Muscles.ADDUCTORS to KpknMuscleGroup.ADDUCTORS,
            Muscles.NECK to KpknMuscleGroup.NECK,
        )

        /** Estilo de calibración que corresponde a cada modo (solo escala los límites de volumen). */
        fun styleFor(mode: RoutineMode): TrainingStyle = when (mode) {
            RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.GENERAL_HYBRID,
            RoutineMode.CUSTOM_POWERBUILDING,
            -> TrainingStyle.POWERBUILDER
            RoutineMode.GENERAL_FUNCTIONAL, RoutineMode.CUSTOM_POWERLIFTING,
            RoutineMode.DISCIPLINE_STRONGMAN, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE,
            -> TrainingStyle.POWERLIFTER
            RoutineMode.CUSTOM_BODYBUILDING, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineMode.DISCIPLINE_ARMWRESTLING,
            -> TrainingStyle.BODYBUILDER
        }

        /** Multiplicador del techo duro en el modo funcional (ver KDoc de la clase). */
        const val FUNCTIONAL_CEILING_FACTOR = 1.25

        /** Multiplicador del techo duro de los co-motores de los compuestos de pierna (ver KDoc de la clase). */
        const val COMOVER_FACTOR = 1.5
        private val COMOVERS = setOf(Muscles.GLUTES, Muscles.ERECTORS)

        private fun levelFactor(level: RoutineLevel): Double = when (level) {
            RoutineLevel.NOVICE -> 0.75
            RoutineLevel.RETURNING -> 0.85
            RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED -> 1.0
        }

        /** Respuesta de calibración 1..3: la declarada o la que el nivel deja suponer. */
        private fun answer(declared: Int?, level: RoutineLevel, kind: String): Int {
            declared?.let { return it.coerceIn(1, 3) }
            return when (level) {
                RoutineLevel.NOVICE -> 1
                RoutineLevel.RETURNING -> if (kind == "consistency") 1 else 2
                RoutineLevel.INTERMEDIATE -> 2
                RoutineLevel.ADVANCED -> 3
            }
        }

        fun of(request: RoutineRequest): VolumeBudgets {
            val style = styleFor(request.mode)
            val calibration = VolumeCalibrationEngine.calculate(
                style = style,
                technique = answer(request.technique, request.level, "technique"),
                consistency = answer(request.consistency, request.level, "consistency"),
                strength = answer(request.strength, request.level, "strength"),
                mobility = answer(request.mobility, request.level, "mobility"),
            )
            val personal = calibration.recommendations.associateBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
            val factor = levelFactor(request.level)
            val hardScale = if (request.mode == RoutineMode.GENERAL_FUNCTIONAL) FUNCTIONAL_CEILING_FACTOR else 1.0
            val disciplineScale = Disciplines.of(request.mode)?.volumeScale.orEmpty()
            val ceilings = LinkedHashMap<String, Int>()
            val targets = LinkedHashMap<String, Int>()
            val floors = LinkedHashMap<String, Int>()
            LANDMARK_GROUP.forEach { (muscle, group) ->
                val global = VolumeLandmarks.byGroup.getValue(group)
                val recommendation = personal[muscle]
                val mav = (recommendation?.maxAdaptiveVolume ?: global.mav).coerceAtLeast(1)
                val mrv = minOf(recommendation?.maxRecoverableVolume ?: global.mrv, global.mrv).coerceAtLeast(1)
                val mev = (recommendation?.minEffectiveVolume ?: global.mev).coerceIn(0, minOf(mav, mrv))
                val soft = minOf(mav, mrv)
                val comover = if (muscle in COMOVERS) COMOVER_FACTOR else 1.0
                val own = disciplineScale[muscle] ?: 1.0
                ceilings[muscle] = maxOf(floor(mrv * factor * hardScale * comover * own).toInt(), minOf(mrv, 4))
                targets[muscle] = maxOf(floor(soft * factor * own).toInt(), minOf(soft, 4))
                floors[muscle] = mev
            }
            return VolumeBudgets(calibration.recommendations, ceilings, targets, floors, style)
        }
    }
}

/** Series semanales acumuladas por músculo canónico (directas e indirectas). */
internal class VolumeLedger(val budgets: VolumeBudgets) {
    val direct: HashMap<String, Double> = HashMap()
    val indirect: HashMap<String, Double> = HashMap()

    fun directOf(muscle: String): Double = direct[muscle] ?: 0.0

    fun add(entry: CatalogEntry, sets: Int) {
        entry.contributions.forEach { (muscle, c) ->
            if (c.direct > 0.0) direct[muscle] = directOf(muscle) + sets * c.direct
            if (c.indirect > 0.0) indirect[muscle] = (indirect[muscle] ?: 0.0) + sets * c.indirect
        }
    }

    /** Techo duro restante de [muscle] en la semana (puede ser 0). */
    fun remaining(muscle: String): Double = (budgets.ceiling(muscle) - directOf(muscle)).coerceAtLeast(0.0)

    /** Objetivo blando restante de [muscle] en la semana (puede ser 0). */
    fun remainingTarget(muscle: String): Double = (budgets.target(muscle) - directOf(muscle)).coerceAtLeast(0.0)
}

/**
 * Tope de series directas por sesión y músculo: la PARTE de la sesión en lo que queda de la semana. Las sesiones se arman de
 * la más exigente a la menos, así que sin reparto las primeras se comerían el presupuesto y las últimas (un día de pierna
 * armado después de tres de cuerpo completo) quedarían con un par de ejercicios. La parte de una sesión es el presupuesto
 * restante por su peso (los huecos de su plan que trabajan ese músculo, más pesados los compuestos) entre el peso de todas las
 * sesiones que aún faltan por armar: lo que una sesión no gasta pasa a las siguientes.
 */
internal class SessionCaps(private val budgets: VolumeBudgets, private val plans: List<SessionPlan>) {

    private val built = BooleanArray(plans.size)

    private val weights: List<Map<String, Double>> = plans.map { plan ->
        val perMuscle = HashMap<String, Double>()
        plan.slots.forEach { slot ->
            val weight = when (slot.role) {
                ItemRole.MAIN -> 3.0
                ItemRole.SECONDARY -> 2.0
                ItemRole.ACCESSORY -> 1.5
                ItemRole.POWER -> 1.5
                else -> 1.0
            }
            PatternCatalog.of(slot.pattern).muscles.forEach { muscle -> perMuscle[muscle] = (perMuscle[muscle] ?: 0.0) + weight }
        }
        perMuscle
    }

    /** La sesión [index] ya está armada: su parte deja de repartirse. */
    fun markBuilt(index: Int) {
        built[index] = true
    }

    /** Series directas que puede sumar la sesión [index] al músculo [muscle] (techo duro, o objetivo blando con [soft]). */
    fun cap(index: Int, muscle: String, soft: Boolean, ledger: VolumeLedger): Int {
        val budget = if (soft) budgets.target(muscle) else budgets.ceiling(muscle)
        val remaining = (budget - ledger.directOf(muscle)).coerceAtLeast(0.0)
        val own = maxOf(weights[index][muscle] ?: 0.0, 1.0)
        val pending = plans.indices.filter { !built[it] && it != index }.sumOf { weights[it][muscle] ?: 0.0 } + own
        val share = ceil(remaining * own / pending).toInt().coerceAtLeast(MIN_CAP)
        return minOf(share, ceil(remaining).toInt())
    }

    private companion object {
        /** Una sesión siempre puede sumar al menos las series mínimas de un ejercicio si queda presupuesto. */
        const val MIN_CAP = 2
    }
}
