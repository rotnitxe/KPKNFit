package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryDayLoad
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class InitialRecoveryPolicyInput(
    val evidence: InitialRecoveryEvidence?,
    val nowMs: Long,
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val overrides: InitialRecoveryOverrides = InitialRecoveryOverrides(),
    /** Zona para comparar fechas (día local) con los registros reales. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

data class InitialRecoveryOverrides(
    val muscular: Int? = null,
    val system: Int? = null,
    val structure: Int? = null,
    val anchorMs: Long? = null,
)

data class InitialRecoveryContribution(
    val muscular: Int?,
    val system: Int?,
    val structure: Int?,
    val confidence: Int,
    val isEstimated: Boolean,
    val isExpired: Boolean,
    val hasRealOverlap: Boolean = false,
    val anchorMs: Long? = null,
    val label: String,
    val source: String,
)

object InitialRecoveryEvidencePolicy {
    private const val TAU_HOURS = 72.0

    fun resolve(input: InitialRecoveryPolicyInput): InitialRecoveryContribution {
        val evidence = input.evidence?.normalized()
        if (evidence != null && evidence.estimatorVersion >= 2) return resolveV2(evidence, input)
        val expired = evidence != null && input.nowMs >= evidence.expiresAtMs
        val active = evidence?.takeUnless { expired }
        val overlap = active != null && input.workoutLogs.any { log ->
            val time = parseTime(log.date)
            time in active.coveredFromMs..active.coveredToMs
        }
        val overrideAnchor = input.overrides.anchorMs
        val anchorMs = overrideAnchor ?: active?.capturedAtMs
        val hasOverride = input.overrides.muscular != null ||
            input.overrides.system != null || input.overrides.structure != null
        val hasAnchor = hasOverride || active != null

        fun estimate(base: Int?, override: Int?): Int? {
            if (override == null && (active == null || overlap || base == null)) return null
            val anchor = overrideAnchor ?: active?.capturedAtMs ?: return null
            val anchoredScore = (override ?: base ?: return null).coerceIn(0, 100)
            val elapsedHours = ((input.nowMs - anchor).coerceAtLeast(0L) / 3_600_000.0)
            val recovered = 100.0 - (100 - anchoredScore) * exp(-elapsedHours / TAU_HOURS)
            return recovered.roundToInt().coerceIn(0, 100)
        }

        return InitialRecoveryContribution(
            muscular = estimate(active?.muscularScore, input.overrides.muscular),
            system = estimate(active?.systemScore, input.overrides.system),
            structure = estimate(active?.structureScore, input.overrides.structure),
            confidence = when {
                !hasAnchor -> 0
                overlap -> 65
                hasOverride -> 60
                else -> active?.confidence?.coerceIn(0, 100) ?: 0
            },
            isEstimated = active != null && !expired && !overlap,
            isExpired = expired,
            hasRealOverlap = overlap,
            anchorMs = anchorMs,
            label = when {
                hasOverride -> "Ajuste manual anclado"
                overlap -> "Historial real"
                active != null -> "Estimación inicial"
                else -> "Sin calibrar"
            },
            source = when {
                hasOverride -> "manual-anchor"
                overlap -> "workout-history"
                active != null -> active.sourceId
                else -> "none"
            },
        )
    }

    /**
     * Batería v2 de cada pilar pedido, con los días que ya tienen registro real descartados. Vacío si no hay
     * evidencia v2 vigente con días entrenados, o si todos esos días quedan descartados.
     */
    internal fun v2PillarBatteries(input: InitialRecoveryPolicyInput, pillars: Collection<String>): Map<String, Int> {
        val evidence = input.evidence?.normalized()?.takeIf { it.estimatorVersion >= 2 } ?: return emptyMap()
        if (input.nowMs >= evidence.expiresAtMs) return emptyMap()
        val kept = keptDayLoads(evidence.dayLoads, input.workoutLogs, input.zone)
        if (kept.isEmpty()) return emptyMap()
        return pillars.associateWith { pillar ->
            InitialRecoveryV2Model.pillarBattery(pillar, kept, evidence.sensations, evidence.capturedAtMs, input.nowMs)
                .roundToInt()
                .coerceIn(0, 100)
        }
    }

    /**
     * Ley v2: cada canal sale de las cargas de los días (con sus curvas) o, si hay ajuste manual, de la ley de
     * ancla del motor. Sin ajuste de columna y sin patrones axiales ni sensación, la Columna queda en null.
     */
    private fun resolveV2(evidence: InitialRecoveryEvidence, input: InitialRecoveryPolicyInput): InitialRecoveryContribution {
        val nowMs = input.nowMs
        val expired = nowMs >= evidence.expiresAtMs
        val active = evidence.takeUnless { expired }
        val kept = if (active == null) {
            emptyList<InitialRecoveryDayLoad>()
        } else {
            keptDayLoads(evidence.dayLoads, input.workoutLogs, input.zone)
        }
        // Todos los días declarados tienen registro real: la evidencia queda sustituida por el historial.
        val superseded = active != null && evidence.dayLoads.isNotEmpty() && kept.isEmpty()
        val usesLoads = active != null && !superseded
        val overrides = input.overrides
        val anchorMs = overrides.anchorMs ?: active?.capturedAtMs
        val hasOverride = overrides.muscular != null || overrides.system != null || overrides.structure != null
        val hasAnchor = hasOverride || active != null

        fun anchored(override: Int?, decayAt: (Double) -> Double): Int? {
            if (override == null) return null
            val anchor = anchorMs ?: return null
            val hours = max(0.0, (nowMs - anchor) / 3_600_000.0)
            return InitialRecoveryV2Model.anchorLaw(override, decayAt(hours)).roundToInt().coerceIn(0, 100)
        }

        val muscularAnchored = anchored(overrides.muscular) { hours ->
            InitialRecoveryV2Model.muscleDecay(hours, InitialRecoveryV2Model.TAU_GLOBAL_MUSCLE_HOURS)
        }
        val muscular = muscularAnchored ?: if (usesLoads) {
            val pillars = InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS
            InitialRecoveryV2Model.muscularBattery(
                pillars.map { pillar ->
                    InitialRecoveryV2Model.pillarBattery(pillar, kept, evidence.sensations, evidence.capturedAtMs, nowMs)
                },
            ).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val systemAnchored = anchored(overrides.system) { hours -> InitialRecoveryV2Model.energyDecay(hours) }
        val system = systemAnchored ?: if (usesLoads) {
            InitialRecoveryV2Model.energyBattery(kept, evidence.sensations, evidence.capturedAtMs, nowMs)
                .roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val structureAnchored = anchored(overrides.structure) { hours -> InitialRecoveryV2Model.spinalDecay(hours) }
        val structure = structureAnchored ?: if (usesLoads && evidence.structureKnown) {
            InitialRecoveryV2Model.spinalBattery(kept, evidence.sensations, evidence.capturedAtMs, nowMs)
                .roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        return InitialRecoveryContribution(
            muscular = muscular,
            system = system,
            structure = structure,
            confidence = when {
                !hasAnchor -> 0
                superseded -> 65
                hasOverride -> 60
                else -> active?.confidence?.coerceIn(0, 100) ?: 0
            },
            isEstimated = usesLoads,
            isExpired = expired,
            hasRealOverlap = superseded,
            anchorMs = anchorMs,
            label = when {
                hasOverride -> "Ajuste manual anclado"
                superseded -> "Historial real"
                active != null -> "Estimación inicial"
                else -> "Sin calibrar"
            },
            source = when {
                hasOverride -> "manual-anchor"
                superseded -> "workout-history"
                active != null -> active.sourceId
                else -> "none"
            },
        )
    }

    /** Quita los días cuya fecha local coincide con la de algún registro real. */
    private fun keptDayLoads(
        dayLoads: List<InitialRecoveryDayLoad>,
        logs: List<WorkoutLog>,
        zone: ZoneId,
    ): List<InitialRecoveryDayLoad> {
        if (dayLoads.isEmpty()) return emptyList()
        val realDays = logs.mapNotNullTo(HashSet<LocalDate>()) { log ->
            AugeUtils.logDateMs(log).takeIf { it > 0L }?.let { localDate(it, zone) }
        }
        return dayLoads.filter { localDate(it.dayStartMs, zone) !in realDays }
    }

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun parseTime(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrElse {
        runCatching {
            java.time.LocalDate.parse(value.take(10))
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}

/**
 * Ley v2 de la evidencia inicial de RINGS (E3a). Usa las mismas curvas del motor AUGE: cada día aporta
 * `carga · recoveryDecay(horas, τ)` y cada canal vale `100·exp(−R/C)`, como en `calculateMuscleBattery`,
 * `calculateSystemicFatigue` y `calculateSpinalBattery`. Las cargas están en unidades de «% de capacidad».
 */
internal object InitialRecoveryV2Model {
    /** C del músculo: `calculateMuscleBattery`, `exp(-rawFatiguePct / 90.0)`. */
    const val C_MUSCLE = 90.0

    /** C de la energía: `calculateSystemicFatigue`, `exp(-rawGymPct / 75.0)`. */
    const val C_ENERGY = 75.0

    /** C de la columna: `calculateSpinalBattery`, `exp(-rawPct / 70.0)`. */
    const val C_SPINAL = 70.0

    /** τ de la energía en horas crudas: `adaptiveCache.cnsRecoveryHours ?: 36.0` (`calculateSystemicFatigue`). */
    const val TAU_ENERGY_HOURS = 36.0

    /** τ de la columna en horas efectivas de `getSpinalRecoveryHours`: `adaptiveCache.spinalRecoveryHours ?: 52.0`. */
    const val TAU_SPINAL_HOURS = 52.0

    /** τ del músculo global en ajustes manuales: `… ?: 48.0` (`calculateGlobalBatteries`), horas de `getSigmoidalHours`. */
    const val TAU_GLOBAL_MUSCLE_HOURS = 48.0

    /**
     * τ de cada pilar: horas base de su perfil en `AugeRecoveryEngine` (`MUSCLE_PROFILE_MAP` y `RECOVERY_PROFILES`:
     * fast 24 · medium 48 · slow 72 · heavy 96). Son copias de esos valores: si cambian en el motor, cambiar aquí.
     */
    val PILLAR_TAU_HOURS: Map<String, Double> = mapOf(
        "Pectorales" to 48.0, "Dorsales" to 48.0, "Deltoides" to 24.0,
        "Bíceps" to 24.0, "Tríceps" to 24.0,
        "Cuádriceps" to 72.0, "Isquiosurales" to 96.0, "Glúteos" to 72.0, "Pantorrillas" to 24.0,
        "Abdomen" to 24.0, "Trapecio" to 48.0, "Erectores Espinales" to 96.0, "Core" to 48.0,
    )

    /**
     * Carga muscular por serie de grupo a intensidad HARD. Calibrada con el descenso de Cuádriceps de la sesión de
     * referencia (5 sentadillas y 4 rumanos, RPE 8,5, 8 repeticiones, descanso 90 s, atleta ENTHUSIAST sin historial).
     * Transcribiendo `AugeFatigueEngine.calculateSetBatteryDrain` (~412) y `AugeRecoveryEngine.calculateMuscleBattery`
     * (~461–699; el cuádriceps es primario en la sentadilla y el rumano no lo activa en la heurística), el motor da
     * Cuádriceps 80,93 a 0 h y 87,95 a 24 h. La ley v2 con 0,4 × 9 series × 5,3 da 80,90 y 87,93. Es un cálculo a mano
     * del código, no una ejecución del motor en la app.
     */
    const val MUSCULAR_LOAD_PER_GROUP_SET_HARD = 5.3

    /**
     * Factor de intensidad frente a HARD: `calculateRpeMultiplier` (~285) con RPE 6 / 7,5 / 8,5 / 9,5, que dan
     * 1,117 / 1,299 / 1,505 / 1,806 (cociente 0,74 / 0,86 / 1,00 / 1,20). El motor usa ese mismo multiplicador en
     * el drenaje de cada serie, muscular, energético y espinal.
     */
    private val INTENSITY_FACTOR: Map<InitialRecoveryIntensity, Double> = mapOf(
        InitialRecoveryIntensity.EASY to 0.74,
        InitialRecoveryIntensity.MODERATE to 0.86,
        InitialRecoveryIntensity.HARD to 1.0,
        InitialRecoveryIntensity.VERY_HARD to 1.20,
    )

    /**
     * Energía por día entrenado: tabla de `InitialRecoveryEvidenceFactory.fromInputs` (3 / 8 / 16 / 24), fijada en el
     * brief. Con la sesión de referencia solo de fuerza (×0,8), la energía queda 84,3 a 0 h frente a 78,6 del motor;
     * sin el ×0,8 quedaría 80,8.
     */
    private val ENERGY_BY_INTENSITY: Map<InitialRecoveryIntensity, Double> = mapOf(
        InitialRecoveryIntensity.EASY to 3.0,
        InitialRecoveryIntensity.MODERATE to 8.0,
        InitialRecoveryIntensity.HARD to 16.0,
        InitialRecoveryIntensity.VERY_HARD to 24.0,
    )

    /**
     * Columna por día con patrón axial a intensidad HARD. «Alto» (sentadilla y peso muerto) 5,2; «moderado»
     * (rumano o remo) 2,6; «bajo» (zancada) 1,6. Con sentadilla y rumano la suma es 7,8; el motor, con las mismas
     * series de referencia y el tope de la columna (82), da 7,98 unidades. Cada valor no se calibró por separado.
     */
    private val SPINAL_AXIAL_HARD: Map<RingsLiftPattern, Double> = mapOf(
        RingsLiftPattern.SQUAT to 5.2,
        RingsLiftPattern.DEADLIFT to 5.2,
        RingsLiftPattern.HINGE_ROW to 2.6,
        RingsLiftPattern.LUNGE to 1.6,
    )

    val AXIAL_LIFTS: Set<RingsLiftPattern> = setOf(
        RingsLiftPattern.SQUAT, RingsLiftPattern.DEADLIFT, RingsLiftPattern.HINGE_ROW, RingsLiftPattern.LUNGE,
    )

    val CARDIO_LIFTS: Set<RingsLiftPattern> = setOf(RingsLiftPattern.CARDIO_LONG, RingsLiftPattern.INTERVALS)

    /** Reparto de cada grupo entre los 13 pilares del motor (brief E3a). */
    private val GROUP_PILLAR_WEIGHTS: Map<RingsMuscleGroup, Map<String, Double>> = mapOf(
        RingsMuscleGroup.CHEST to mapOf("Pectorales" to 0.8, "Deltoides" to 0.1, "Tríceps" to 0.1),
        RingsMuscleGroup.BACK to mapOf("Dorsales" to 0.5, "Trapecio" to 0.3, "Erectores Espinales" to 0.2),
        RingsMuscleGroup.SHOULDERS to mapOf("Deltoides" to 0.8, "Trapecio" to 0.2),
        RingsMuscleGroup.ARMS to mapOf("Bíceps" to 0.5, "Tríceps" to 0.5),
        RingsMuscleGroup.LEGS to mapOf("Cuádriceps" to 0.4, "Isquiosurales" to 0.25, "Glúteos" to 0.25, "Pantorrillas" to 0.1),
        RingsMuscleGroup.CORE to mapOf("Abdomen" to 0.6, "Core" to 0.4),
    )

    /** Grupos que implica cada patrón, para estimar la carga muscular sin series por grupo. */
    private val IMPLIED_GROUPS: Map<RingsLiftPattern, Set<RingsMuscleGroup>> = mapOf(
        RingsLiftPattern.SQUAT to setOf(RingsMuscleGroup.LEGS),
        RingsLiftPattern.LUNGE to setOf(RingsMuscleGroup.LEGS),
        RingsLiftPattern.DEADLIFT to setOf(RingsMuscleGroup.LEGS, RingsMuscleGroup.BACK),
        RingsLiftPattern.HINGE_ROW to setOf(RingsMuscleGroup.LEGS, RingsMuscleGroup.BACK),
        RingsLiftPattern.BENCH to setOf(RingsMuscleGroup.CHEST),
        RingsLiftPattern.OVERHEAD to setOf(RingsMuscleGroup.SHOULDERS),
        RingsLiftPattern.PULL_UP to setOf(RingsMuscleGroup.BACK),
    )

    /** Series por grupo implicado cuando el usuario no las da (supuesto del brief: estimación genérica). */
    private const val GENERIC_SETS_PER_IMPLIED_GROUP = 3

    /** Sin patrones reconocibles, dos series en cada grupo (cuerpo completo genérico). */
    private const val GENERIC_SETS_WITHOUT_PATTERNS = 2

    fun genericGroupSets(lifts: Set<RingsLiftPattern>): Map<RingsMuscleGroup, Int> {
        val implied = lifts.flatMap { IMPLIED_GROUPS[it].orEmpty() }.toSet()
        return if (implied.isNotEmpty()) {
            implied.associateWith { GENERIC_SETS_PER_IMPLIED_GROUP }
        } else {
            RingsMuscleGroup.values().associateWith { GENERIC_SETS_WITHOUT_PATTERNS }
        }
    }

    /** Carga muscular de cada día reciente: las series por grupo se reparten por igual entre esos días. */
    fun muscularShare(
        setsByGroup: Map<RingsMuscleGroup, Int>,
        intensity: InitialRecoveryIntensity,
        recentDayCount: Int,
    ): Map<String, Double> {
        if (recentDayCount <= 0) return emptyMap()
        val perSet = MUSCULAR_LOAD_PER_GROUP_SET_HARD * INTENSITY_FACTOR.getValue(intensity) / recentDayCount
        val perPillar = mutableMapOf<String, Double>()
        setsByGroup.forEach { (group, sets) ->
            GROUP_PILLAR_WEIGHTS[group].orEmpty().forEach { (pillar, weight) ->
                perPillar[pillar] = (perPillar[pillar] ?: 0.0) + sets.coerceAtLeast(0) * weight * perSet
            }
        }
        return perPillar
    }

    /** Energía de un día entrenado: ×1,3 con intervalos, ×0,8 si solo hay fuerza y ×1,0 con cardio largo. */
    fun energyLoad(intensity: InitialRecoveryIntensity, lifts: Set<RingsLiftPattern>): Double {
        val factor = when {
            RingsLiftPattern.INTERVALS in lifts -> 1.3
            lifts.none { it in CARDIO_LIFTS } -> 0.8
            else -> 1.0
        }
        return ENERGY_BY_INTENSITY.getValue(intensity) * factor
    }

    /** Columna de un día entrenado: suma de los patrones axiales declarados, a la intensidad dada. */
    fun spinalLoad(intensity: InitialRecoveryIntensity, lifts: Set<RingsLiftPattern>): Double =
        lifts.sumOf { SPINAL_AXIAL_HARD[it] ?: 0.0 } * INTENSITY_FACTOR.getValue(intensity)

    fun muscleDecay(hours: Double, tauHours: Double): Double =
        AugeUtils.recoveryDecay(AugeUtils.getSigmoidalHours(hours), tauHours)

    fun energyDecay(hours: Double): Double = AugeUtils.recoveryDecay(hours, TAU_ENERGY_HOURS)

    fun spinalDecay(hours: Double): Double =
        AugeUtils.recoveryDecay(AugeUtils.getSpinalRecoveryHours(hours), TAU_SPINAL_HOURS)

    /** Ley de ancla del motor: B(t) = 100·(B₀/100)^d(Δt), donde d es la fracción que queda. */
    fun anchorLaw(score: Int, decay: Double): Double =
        100.0 * (score.coerceIn(0, 100) / 100.0).coerceAtLeast(0.001).pow(decay)

    /** Carga equivalente a una sensación, en la escala de C (misma penalización que la puntuación v1). */
    fun sensationLoad(value: Int?, c: Double): Double {
        val penalty = InitialRecoveryEvidenceFactory.sensationPenalty(value)
        return if (penalty <= 0) 0.0 else -c * ln(1.0 - penalty / 100.0)
    }

    /**
     * Batería 0–100 de un pilar en [nowMs]. La sensación muscular se suma como carga en la hora de captura y solo
     * cuenta si hay días entrenados.
     */
    fun pillarBattery(
        pillar: String,
        dayLoads: List<InitialRecoveryDayLoad>,
        sensations: InitialRecoverySensations,
        capturedAtMs: Long,
        nowMs: Long,
    ): Double {
        val tau = PILLAR_TAU_HOURS[pillar] ?: return 100.0
        var load = 0.0
        dayLoads.forEach { day ->
            load += (day.muscular[pillar] ?: 0.0) * muscleDecay(hoursBetween(day.dayStartMs, nowMs), tau)
        }
        if (dayLoads.isNotEmpty()) {
            load += sensationLoad(sensations.muscular, C_MUSCLE) * muscleDecay(hoursBetween(capturedAtMs, nowMs), tau)
        }
        return 100.0 * exp(-load / C_MUSCLE)
    }

    /** Agregado muscular global, como `calculateGlobalBatteries`: 85 % de la media y 15 % del cuartil inferior. */
    fun muscularBattery(pillarBatteries: List<Double>): Double {
        if (pillarBatteries.isEmpty()) return 100.0
        val sorted = pillarBatteries.sorted()
        val bottomCount = max(1, (sorted.size * 0.25).toInt())
        return sorted.average() * 0.85 + sorted.take(bottomCount).average() * 0.15
    }

    fun energyBattery(
        dayLoads: List<InitialRecoveryDayLoad>,
        sensations: InitialRecoverySensations,
        capturedAtMs: Long,
        nowMs: Long,
    ): Double {
        var load = 0.0
        dayLoads.forEach { day -> load += day.energy * energyDecay(hoursBetween(day.dayStartMs, nowMs)) }
        if (dayLoads.isNotEmpty()) {
            load += sensationLoad(sensations.energy, C_ENERGY) * energyDecay(hoursBetween(capturedAtMs, nowMs))
        }
        return 100.0 * exp(-load / C_ENERGY)
    }

    fun spinalBattery(
        dayLoads: List<InitialRecoveryDayLoad>,
        sensations: InitialRecoverySensations,
        capturedAtMs: Long,
        nowMs: Long,
    ): Double {
        var load = 0.0
        dayLoads.forEach { day -> load += day.spinal * spinalDecay(hoursBetween(day.dayStartMs, nowMs)) }
        if (dayLoads.isNotEmpty()) {
            load += sensationLoad(sensations.structure, C_SPINAL) * spinalDecay(hoursBetween(capturedAtMs, nowMs))
        }
        return 100.0 * exp(-load / C_SPINAL)
    }

    private fun hoursBetween(fromMs: Long, toMs: Long): Double = max(0.0, (toMs - fromMs) / 3_600_000.0)
}
