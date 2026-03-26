package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine.getEffectiveRPE
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * AugeTtcEngine — Motor de Tendones y Baterías Articulares AUGE v3.0.
 * Equivalente a @kpkn/shared-domain ttc.ts + tendonRecovery.ts +
 * structuralReadiness.ts + tendonAlerts.ts
 *
 * TTC = Tendon & Tissue Cost (0-5). Mide el estrés mecánico sobre tendones/articulaciones.
 */
object AugeTtcEngine {

    private const val TTC_MAX = 5.0
    private const val TENDON_CAPACITY_BASE = 80.0
    private const val TENDON_RECOVERY_HIGH_TTC = 60.0
    private const val TENDON_RECOVERY_STD = 48.0
    private const val CUMULATIVE_PENALTY = 0.1
    private const val IMBALANCE_THRESHOLD = 30

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
    private fun safeExp(v: Double): Double {
        val r = exp(v)
        return if (r.isNaN() || r.isInfinite()) 0.0 else r
    }

    // ─── Músculo → baterías articulares relacionadas ──────────────────────────

    val MUSCLE_TO_ARTICULAR: Map<String, List<ArticularBattery>> = mapOf(
        "Deltoides Anterior"  to listOf(ArticularBattery.SHOULDER),
        "Deltoides Lateral"   to listOf(ArticularBattery.SHOULDER),
        "Deltoides Posterior" to listOf(ArticularBattery.SHOULDER),
        "Deltoides"           to listOf(ArticularBattery.SHOULDER),
        "Pectorales"          to listOf(ArticularBattery.SHOULDER),
        "Dorsales"            to listOf(ArticularBattery.SHOULDER),
        "Bíceps"              to listOf(ArticularBattery.SHOULDER, ArticularBattery.ELBOW),
        "Tríceps"             to listOf(ArticularBattery.ELBOW),
        "Antebrazo"           to listOf(ArticularBattery.ELBOW),
        "Cuádriceps"          to listOf(ArticularBattery.KNEE, ArticularBattery.HIP),
        "Isquiosurales"       to listOf(ArticularBattery.KNEE, ArticularBattery.HIP),
        "Glúteos"             to listOf(ArticularBattery.HIP),
        "Aductores"           to listOf(ArticularBattery.HIP),
        "Pantorrillas"        to listOf(ArticularBattery.ANKLE),
        "Trapecio"            to listOf(ArticularBattery.SHOULDER, ArticularBattery.CERVICAL),
        "Cuello"              to listOf(ArticularBattery.CERVICAL),
        "Erectores Espinales" to listOf(ArticularBattery.HIP),
        "Core"                to listOf(ArticularBattery.HIP),
    )

    fun articularLabel(ab: ArticularBattery): String = when (ab) {
        ArticularBattery.SHOULDER -> "Hombro"
        ArticularBattery.ELBOW    -> "Codo"
        ArticularBattery.KNEE     -> "Rodilla"
        ArticularBattery.HIP      -> "Cadera"
        ArticularBattery.ANKLE    -> "Tobillo"
        ArticularBattery.CERVICAL -> "Cervical"
    }

    // ─── 1. TTC por ejercicio ─────────────────────────────────────────────────

    /**
     * Calcula el TTC (Tendon & Tissue Cost) para un ejercicio usando nombre y equipamiento.
     * Equivalente a calculateTTC() en ttc.ts.
     */
    fun calculateTTC(exerciseName: String, equipment: String? = null): Double {
        val name = exerciseName.lowercase()
        val eq   = equipment?.lowercase() ?: ""

        // 1. TTC base por patrón de movimiento
        val baseTtc: Double = when {
            // Olímpico / balístico
            name.contains("snatch") || name.contains("arrancada") || name.contains("arranque") -> 3.0
            name.contains("clean")  && (name.contains("power") || name.contains("cargada"))    -> 3.0
            name.contains("jerk")   || name.contains("envión")                                  -> 3.0
            name.contains("salto")  || name.contains("jump")  || name.contains("box jump")      -> 3.0
            name.contains("bound")  || name.contains("hop")   || name.contains("pliométric")    -> 3.0
            // Compuesto pesado
            name.contains("peso muerto") || name.contains("deadlift") -> 2.0
            name.contains("sentadilla")  || name.contains("squat")    -> 2.0
            name.contains("press banca") || name.contains("bench press") -> 2.0
            name.contains("press militar") || name.contains("ohp")   -> 2.0
            name.contains("dominada") || name.contains("pull-up") || name.contains("pullup") -> 2.0
            name.contains("remo")    || name.contains("row")          -> 2.0
            name.contains("hip thrust") || name.contains("puente")   -> 1.5
            name.contains("zancada") || name.contains("lunge")        -> 2.0
            // Aislamiento
            name.contains("curl")    || name.contains("extensión")    -> 1.0
            else                                                        -> 1.0
        }

        // 2. Modificador de equipamiento
        val eqMod: Double = when {
            eq == "máquina" || eq == "machine" || eq == "polea" || eq == "cable" -> 0.8
            eq == "barra" || eq == "barbell" || eq == "mancuerna" || eq == "dumbbell"
                || eq == "kettlebell"                                              -> 1.2
            else                                                                   -> 1.0
        }

        // 3. Modificador de tipo de contracción
        val contraMod: Double = when {
            name.contains("nórdico") || name.contains("nordic")            -> 1.8
            name.contains("excéntrico") || name.contains("eccentric")      -> 1.8
            name.contains("plancha") || name.contains("plank")             -> 0.5
            name.contains("isométric") || name.contains("wall sit")        -> 0.5
            name.contains("pausa") || name.contains("paused")              -> 1.1
            else                                                             -> 1.0
        }

        return min(TTC_MAX, baseTtc * eqMod * contraMod)
    }

    // ─── 2. Drenaje tendinoso por set ─────────────────────────────────────────

    /**
     * Distribuye el drenaje tendinoso entre las baterías articulares según sus pesos.
     * Equivalente a calculateSetTendonDrain() en ttc.ts.
     */
    fun calculateSetTendonDrain(
        set: CompletedSet,
        ttc: Double,
        articularWeights: Map<ArticularBattery, Double>,
    ): Map<ArticularBattery, Double> {
        val result = ArticularBattery.entries.associateWith { 0.0 }.toMutableMap()
        if (ttc <= 0.0) return result

        val rpe = getEffectiveRPE(set)
        val intensityMult = when {
            rpe >= 10.0 -> 1.4
            rpe >= 8.0  -> 1.0
            rpe >= 6.0  -> 0.7
            else        -> 0.4
        }

        val repsFactor = min(1.5, 0.1 + set.reps / 15.0)
        val baseDrain = ttc * repsFactor * intensityMult * 2.5  // escalado para batería 0-100

        val totalWeight = articularWeights.values.sum().takeIf { it > 0.0 } ?: 1.0
        for ((ab, w) in articularWeights) {
            result[ab] = baseDrain * (w / totalWeight)
        }
        return result
    }

    // ─── 3. Baterías articulares desde historial ──────────────────────────────

    /**
     * Calcula el estado de las 6 baterías articulares desde el historial de 10 días.
     * Equivalente a calculateArticularBatteries() en tendonRecovery.ts.
     */
    fun calculateArticularBatteries(
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Map<ArticularBattery, ArticularBatteryState> {
        val now = System.currentTimeMillis()
        val tenDaysMs = 10L * 24 * 3600 * 1000

        val relevantLogs = history
            .filter { logDateMs(it) > now - tenDaysMs }
            .sortedBy { logDateMs(it) }

        // Estado acumulado
        val accumulatedStress = ArticularBattery.entries.associateWith { 0.0 }.toMutableMap()
        val lastDrainTime     = ArticularBattery.entries.associateWith { 0L }.toMutableMap()
        val recoveryWindowMs  = 72L * 3600 * 1000

        for (log in relevantLogs) {
            val logTime   = logDateMs(log)
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)

            val sessionDrain = ArticularBattery.entries.associateWith { 0.0 }.toMutableMap()

            for (ex in log.completedExercises) {
                val info = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
                    ?: exerciseDb.values.find { it.name.equals(ex.exerciseName, ignoreCase = true) }

                val weights = getArticularWeightsForExercise(info)
                if (weights.values.all { it == 0.0 }) continue

                val ttc = calculateTTC(ex.exerciseName, info?.equipment)

                for (s in ex.sets) {
                    if (!AugeFatigueEngine.isSetEffective(s)) continue
                    val drain = calculateSetTendonDrain(s, ttc, weights)
                    for ((ab, d) in drain) sessionDrain[ab] = (sessionDrain[ab] ?: 0.0) + d
                }
            }

            // Penalización acumulativa: sesión dentro de 72h de la anterior
            for (ab in ArticularBattery.entries) {
                var d = sessionDrain[ab] ?: 0.0
                val last = lastDrainTime[ab] ?: 0L
                if (d > 0 && last > 0 && logTime - last < recoveryWindowMs) d *= 1 + CUMULATIVE_PENALTY
                if (d > 0) lastDrainTime[ab] = logTime
                sessionDrain[ab] = d
            }

            // Decay exponencial: k = 2 / recoveryHours
            val totalTtc   = sessionDrain.values.sum()
            val drainedCnt = sessionDrain.values.count { it > 0 }
            val avgTtc     = if (drainedCnt > 0) totalTtc / drainedCnt else 0.0
            val recoveryHours = if (avgTtc > 3.0) TENDON_RECOVERY_HIGH_TTC else TENDON_RECOVERY_STD
            val k = 2.0 / recoveryHours

            for (ab in ArticularBattery.entries) {
                val stress = sessionDrain[ab] ?: 0.0
                if (stress <= 0.0) continue
                val remaining = stress * safeExp(-k * hoursSince)
                accumulatedStress[ab] = (accumulatedStress[ab] ?: 0.0) + remaining
            }
        }

        // Finalizar baterías
        return ArticularBattery.entries.associateWith { ab ->
            val acc     = accumulatedStress[ab] ?: 0.0
            val battery = clamp(100.0 - (acc / TENDON_CAPACITY_BASE) * 100.0, 0.0, 100.0)
            val score   = battery.toInt()
            val status  = when {
                battery < 40.0 -> ArticularStatus.EXHAUSTED
                battery < 85.0 -> ArticularStatus.RECOVERING
                else           -> ArticularStatus.OPTIMAL
            }
            val hoursToRecovery: Int = if (battery < 90.0 && acc > 0.0) {
                val targetStress = ((100.0 - 90.0) / 100.0) * TENDON_CAPACITY_BASE
                val kFinal = 2.0 / TENDON_RECOVERY_STD
                max(0, (-ln(targetStress / acc) / kFinal).toInt())
            } else 0

            ArticularBatteryState(
                recoveryScore = score,
                estimatedHoursToRecovery = hoursToRecovery,
                status = status,
                accumulatedStress = acc,
            )
        }
    }

    // ─── 4. Readiness estructural combinada ──────────────────────────────────

    /**
     * Combina batería muscular + articular para un músculo.
     * Equivalente a getStructuralReadinessForMuscle() en structuralReadiness.ts.
     */
    fun getStructuralReadinessForMuscle(
        muscleName: String,
        muscleBattery: Int,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
    ): StructuralReadinessBreakdown {
        val related       = MUSCLE_TO_ARTICULAR[muscleName] ?: emptyList()
        val articularScore = if (related.isEmpty()) muscleBattery
        else related.map { articularBatteries[it]?.recoveryScore ?: 100 }.average().toInt()

        val combined  = ((muscleBattery + articularScore) / 2.0).toInt()
        val limiting  = min(muscleBattery, articularScore)

        return StructuralReadinessBreakdown(
            muscleName        = muscleName,
            muscleBattery     = muscleBattery,
            articularBattery  = articularScore,
            combinedBattery   = combined,
            limitingBattery   = limiting,
            relatedArticular  = related,
        )
    }

    fun getStructuralReadinessForMuscles(
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        muscleFilter: List<String> = emptyList(),
    ): List<StructuralReadinessBreakdown> {
        val muscles = if (muscleFilter.isEmpty()) perMuscle.keys.toList() else muscleFilter
        return muscles.map { name ->
            getStructuralReadinessForMuscle(
                name,
                perMuscle[name]?.recoveryScore ?: 100,
                articularBatteries,
            )
        }
    }

    // ─── 5. Alertas de desequilibrio tendinoso ────────────────────────────────

    /**
     * Detecta desequilibrios donde el músculo va mejor que el tendón (>30 pts de gap).
     * Equivalente a getTendonImbalanceAlerts() en tendonAlerts.ts.
     */
    fun getTendonImbalanceAlerts(
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        sessionMuscles: List<String> = emptyList(),
    ): List<TendonImbalanceAlert> {
        val readiness = getStructuralReadinessForMuscles(perMuscle, articularBatteries, sessionMuscles)
        val alerts    = mutableListOf<TendonImbalanceAlert>()

        for (item in readiness) {
            for (ab in item.relatedArticular) {
                val abScore = articularBatteries[ab]?.recoveryScore ?: continue
                val gap     = item.muscleBattery - abScore
                if (gap <= IMBALANCE_THRESHOLD) continue

                val label = articularLabel(ab)
                alerts.add(TendonImbalanceAlert(
                    type             = if (abScore < 40) AlertSeverity.DANGER else AlertSeverity.WARNING,
                    muscleLabel      = item.muscleName,
                    articularLabel   = label,
                    muscleBattery    = item.muscleBattery,
                    articularBattery = abScore,
                    gap              = gap,
                    message          = "Tu lectura muscular de ${item.muscleName} va mejor (${item.muscleBattery}%), pero el tejido de $label sigue atrasado ($abScore%). Usa la media combinada antes de subir cargas o meter explosividad hoy.",
                ))
            }
        }
        return alerts
    }

    /**
     * Sugerencias compensatorias cuando hay baterías articulares bajas.
     * Equivalente a getTendonCompensationSuggestions() en tendonAlerts.ts.
     */
    fun getTendonCompensationSuggestions(
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        sessionArticular: List<ArticularBattery> = emptyList(),
    ): List<TendonCompensationSuggestion> {
        val filter  = if (sessionArticular.isEmpty()) articularBatteries.keys else sessionArticular.toSet()
        val low     = articularBatteries.entries.filter { (ab, s) -> ab in filter && s.recoveryScore < 50 }
        val veryLow = low.filter { (_, s) -> s.recoveryScore < 30 }

        val suggestions = mutableListOf<TendonCompensationSuggestion>()
        if (low.isNotEmpty()) {
            suggestions.add(TendonCompensationSuggestion(
                type    = SuggestionType.NUTRITION,
                title   = "Soporte nutricional",
                message = "Colageno hidrolizado + Vitamina C 30-60 min antes del entrenamiento puede mejorar la sintesis de colageno en tendones y acelerar la recuperacion (~10%).",
            ))
        }
        if (veryLow.isNotEmpty()) {
            suggestions.add(TendonCompensationSuggestion(
                type    = SuggestionType.BIOMECHANICAL,
                title   = "Ajuste biomecanico",
                message = "Considera sustituir ejercicios pesados o pliometricos por alternativas isometricas o de bajo TTC para no agravar el dano tendinoso.",
            ))
        }
        return suggestions
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private fun logDateMs(log: WorkoutLog): Long = try {
        Instant.parse(log.date).toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.LocalDate.parse(log.date.take(10))
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (e2: Exception) { 0L }
    }

    /**
     * Infiere qué baterías articulares involucra un ejercicio según sus músculos primarios.
     * Asigna peso 1.0 por batería involucrada (distribución uniforme).
     */
    private fun getArticularWeightsForExercise(
        info: ExerciseMuscleInfo?,
    ): Map<ArticularBattery, Double> {
        val result = mutableMapOf<ArticularBattery, Double>()
        if (info == null) return result

        for (involved in info.involvedMuscles) {
            if (involved.role != MuscleRole.PRIMARY && involved.role != MuscleRole.SECONDARY) continue
            val articulars = MUSCLE_TO_ARTICULAR[involved.muscle] ?: continue
            for (ab in articulars) {
                val roleMult = if (involved.role == MuscleRole.PRIMARY) 1.0 else 0.6
                result[ab] = (result[ab] ?: 0.0) + roleMult
            }
        }
        return result
    }
}
