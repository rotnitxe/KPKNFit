package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine.calculateSetBatteryDrain
import com.example.kpkn.domain.auge.AugeFatigueEngine.getDynamicAugeMetrics
import com.example.kpkn.domain.auge.AugeFatigueEngine.isSetEffective
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * AugeRecoveryEngine — Motor de Recuperación AUGE v3.0 para Kotlin.
 * Equivalente a @kpkn/shared-domain recovery.ts + recoveryService.ts
 */
object AugeRecoveryEngine {

    // ─── Perfiles de recuperación (horas base) ────────────────────────────────

    private val RECOVERY_PROFILES = mapOf(
        "fast"   to 24.0,
        "medium" to 48.0,
        "slow"   to 72.0,
        "heavy"  to 96.0,
    )

    private val MUSCLE_PROFILE_MAP = mapOf(
        "Bíceps" to "fast", "Tríceps" to "fast", "Deltoides" to "fast",
        "Deltoides Anterior" to "fast", "Deltoides Lateral" to "fast", "Deltoides Posterior" to "fast",
        "Pantorrillas" to "fast", "Abdomen" to "fast", "Antebrazo" to "fast",
        "Pectorales" to "medium", "Dorsales" to "medium", "Hombros" to "medium", "Trapecio" to "medium",
        "Cuádriceps" to "slow", "Glúteos" to "slow", "Aductores" to "medium",
        "Isquiosurales" to "heavy", "Erectores Espinales" to "heavy", "Core" to "medium", "Cuello" to "medium",
    )

    // Los músculos "pilar" para el cálculo de batería muscular global
    private val PILLAR_MUSCLES = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

    private fun safeExp(v: Double): Double {
        val r = exp(v)
        return if (r.isNaN() || r.isInfinite()) 0.0 else r
    }

    private fun normKey(s: String) = s
        .lowercase().trim()
        .replace("á","a").replace("é","e").replace("í","i")
        .replace("ó","o").replace("ú","u").replace("ü","u")

    private fun nowMs() = System.currentTimeMillis()

    private fun logDateMs(log: WorkoutLog): Long = try {
        Instant.parse(log.date).toEpochMilli()
    } catch (e: Exception) {
        try {
            val ld = LocalDate.parse(log.date.take(10))
            ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e2: Exception) { 0L }
    }

    // Verifica si un músculo pertenece a una categoría (normalización básica)
    private fun muscleMatchesCategory(specificMuscle: String, category: String): Boolean {
        val s = normKey(specificMuscle)
        val c = normKey(category)
        return s == c || s.contains(c) || c.contains(s)
    }

    /**
     * Promedio ponderado de sueño de los últimos 3 días.
     * Pesos: 50% última noche, 30% anteayer, 20% hace 3 días.
     * Equivalente a recoveryService.ts línea 310-313.
     * Fallback: wellbeing.sleepHours o 7.5 si no hay datos.
     */
    private fun calculateWeightedSleepHours(
        sleepLogs: List<SleepLog>,
        wellbeing: DailyWellbeingLog?,
    ): Double {
        val sorted = sleepLogs
            .sortedByDescending { it.endTime }
            .take(3)
        return when (sorted.size) {
            0    -> wellbeing?.sleepHours ?: 7.5
            1    -> sorted[0].duration
            2    -> sorted[0].duration * 0.6 + sorted[1].duration * 0.4
            else -> sorted[0].duration * 0.5 + sorted[1].duration * 0.3 + sorted[2].duration * 0.2
        }
    }

    /**
     * Capacidad de trabajo dinámica para un músculo basada en el historial de 4 semanas.
     * Equivalente a calculateUserWorkCapacity() en recoveryService.ts líneas 136-175.
     *
     * La capacidad = promedio semanal de estrés para ese músculo × 1.8 (supercompensación),
     * con suelo en el floor del tipo de atleta y techo en 3500.
     */
    private fun calculateUserWorkCapacity(
        muscleName: String,
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Double {
        val now = nowMs()
        val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > fourWeeksAgo }
        val baseFloor = AugeFatigueEngine.getAthleteCapacity(settings)

        if (recentLogs.isEmpty()) return baseFloor

        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        var totalStress = 0.0

        recentLogs.forEach { log ->
            log.completedExercises.forEach { ex ->
                val dbInfo = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
                    ?: exerciseDb.values.find { it.name.equals(ex.exerciseName, ignoreCase = true) }

                val involvement = dbInfo?.involvedMuscles?.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                } ?: return@forEach

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)

                val setStress = ex.sets.sumOf { s ->
                    if (!isSetEffective(s)) return@sumOf 0.0
                    val drain = calculateSetBatteryDrain(s, metrics, tanks, 0, ex.restTime)
                    drain.muscularDrainPct
                }

                // Ponderar por rol de participación
                val roleMult = FATIGUE_ROLE_MULTIPLIERS[involvement.role] ?: 1.0
                totalStress += setStress * roleMult
            }
        }

        // Promedio semanal × buffer supercompensación (1.8×)
        val weeklyAvg = totalStress / 4.0
        val calculatedCapacity = weeklyAvg * 1.8

        return clamp(max(calculatedCapacity, baseFloor), 500.0, 3500.0)
    }

    // ─── 1. BATERÍA MUSCULAR INDIVIDUAL ──────────────────────────────────────

    fun calculateMuscleBattery(
        muscleName: String,
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        nutritionMultiplier: Double = 1.0,
        sleepLogs: List<SleepLog> = emptyList(),
    ): MuscleRecoveryStatus {
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        // Capacidad dinámica basada en historial de 4 semanas (vs floor estático)
        val capacity = calculateUserWorkCapacity(muscleName, history, settings, exerciseDb)

        // 1. Perfil de recuperación
        val profileKey = MUSCLE_PROFILE_MAP.entries
            .firstOrNull { normKey(it.key) == normKey(muscleName) }?.value ?: "medium"
        val baseRecoveryTime = clamp(RECOVERY_PROFILES[profileKey] ?: 48.0, 18.0, 144.0)

        // 2. Multiplicadores de recuperación
        var multiplier = nutritionMultiplier

        // Sueño: promedio ponderado 3 días (0.5/0.3/0.2) si hay SleepLogs,
        // fallback a wellbeing.sleepHours o 7.5h neutral
        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        when {
            weightedSleep < 5.5  -> multiplier *= 1.5
            weightedSleep < 6.5  -> multiplier *= 1.2
            weightedSleep >= 8.5 -> multiplier *= 0.8
            weightedSleep >= 7.5 -> multiplier *= 0.9
        }

        wellbeing?.let { w ->
            if (w.stressLevel >= 4) multiplier *= 1.4
            else if (w.stressLevel == 3) multiplier *= 1.1
        }

        // Edad y género
        val age = settings.userVitals.age ?: 25
        if (age > 35) multiplier *= (1.0 + (age - 35) * 0.01)
        val gender = settings.userVitals.gender
        if (gender == Gender.FEMALE) multiplier *= 0.85

        val realRecoveryTime = baseRecoveryTime * max(0.5, multiplier)

        // 3. Acumulación exponencial de fatiga (últimos 10 días)
        val tenDaysAgo = now - 10L * 24 * 3600 * 1000
        val relevantHistory = history.filter { logDateMs(it) > tenDaysAgo }

        var accumulatedFatigue = 0.0
        var effectiveSetsCount = 0
        var lastSessionDate = 0L

        relevantHistory.forEach { log ->
            val logTime = logDateMs(log)
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)
            var sessionMuscleStress = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
                    ?: exerciseDb.values.find { it.name.equals(ex.exerciseName, ignoreCase = true) }

                val involvement = dbInfo?.involvedMuscles?.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                } ?: return@forEach

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo.equipment)

                val rawStress = ex.sets.sumOf { s ->
                    if (!isSetEffective(s)) return@sumOf 0.0
                    val drain = calculateSetBatteryDrain(s, metrics, tanks, 0, ex.restTime)
                    drain.muscularDrainPct
                }

                val roleMult = when (involvement.role) {
                    MuscleRole.PRIMARY    -> 1.0
                    MuscleRole.SECONDARY  -> 0.5
                    MuscleRole.STABILIZER -> 0.15
                    MuscleRole.NEUTRALIZER -> 0.1
                }

                sessionMuscleStress += rawStress * roleMult

                // Contar series efectivas (para métricas)
                if (hoursSince <= 168 && (involvement.role == MuscleRole.PRIMARY || involvement.role == MuscleRole.SECONDARY)) {
                    effectiveSetsCount += ex.sets.count { isSetEffective(it) }
                }
            }

            if (sessionMuscleStress > 0) {
                val k = 2.9957 / max(1.0, realRecoveryTime)
                accumulatedFatigue += sessionMuscleStress * safeExp(-k * hoursSince)
                if (logTime > lastSessionDate) lastSessionDate = logTime
            }
        }

        // 4. Batería final
        val rawFatiguePct = (accumulatedFatigue / capacity) * 100.0
        val fatiguePenalty = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / 30.0)), 0.0, 100.0)
        var battery = clamp(100.0 - fatiguePenalty, 0.0, 100.0)

        // Garantía de frescura
        if (accumulatedFatigue <= 0.1 && (wellbeing?.doms ?: 1) <= 2) battery = 100.0

        // 5. Override por DOMS
        val domsCap = when (wellbeing?.doms ?: 1) {
            5    -> 20.0
            4    -> 50.0
            3    -> 85.0
            else -> 100.0
        }
        battery = min(battery, domsCap)

        // 6. Estado
        val status = when {
            battery >= 95 -> RecoveryStatus.FRESH
            battery >= 85 -> RecoveryStatus.OPTIMAL
            battery >= 40 -> RecoveryStatus.RECOVERING
            else          -> RecoveryStatus.EXHAUSTED
        }

        // 7. Horas para llegar al 90%
        var hoursToRecovery = 0
        if (battery < 90 && accumulatedFatigue > 0) {
            val k = 2.9957 / realRecoveryTime
            val targetFatigue = (100 - 90) * capacity / 100.0
            if (accumulatedFatigue > targetFatigue) {
                hoursToRecovery = max(0.0, -ln(targetFatigue / accumulatedFatigue) / k).toInt()
            }
        }

        return MuscleRecoveryStatus(
            muscleName             = muscleName,
            recoveryScore          = battery.toInt(),
            hoursToRecovery        = hoursToRecovery,
            hoursSinceLastSession  = if (lastSessionDate > 0) ((now - lastSessionDate) / 3_600_000).toInt() else -1,
            effectiveSets          = effectiveSetsCount,
            status                 = status,
        )
    }

    // ─── 2. BATERÍA SNC / SISTÉMICA ───────────────────────────────────────────

    fun calculateSystemicFatigue(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
    ): Triple<Int, Int, Int> { // Triple(cnsBattery, gymLoad, lifeLoad)
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val last7Days = now - 7L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > last7Days }

        var cnsLoad = 0.0
        recentLogs.forEach { log ->
            val daysAgo = (now - logDateMs(log)) / (24.0 * 3_600_000)
            val recency = max(0.1, exp(-0.4 * daysAgo))
            val muscleVolumeMap = mutableMapOf<String, Int>()
            var sessionCns = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
                val primaryMuscle = dbInfo?.involvedMuscles?.find { it.role == MuscleRole.PRIMARY }?.muscle ?: "Core"
                var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
                    val drain = calculateSetBatteryDrain(s, metrics, tanks, accumulated, ex.restTime)
                    sessionCns += drain.cnsDrainPct
                }
                muscleVolumeMap[primaryMuscle] = accumulated
            }

            // Duración prolongada libera cortisol
            val durationMin = log.durationMinutes
            if (durationMin > 90) sessionCns *= 1.15
            else if (durationMin > 75) sessionCns *= 1.08

            cnsLoad += sessionCns * recency
        }

        val normalizedGymFatigue = clamp(cnsLoad, 0.0, 100.0)

        // Sleep penalty — promedio ponderado 3 noches (sleep banking incluido)
        // Equivalente a recoveryService.ts líneas 307-321
        var sleepPenalty = 0.0
        if (settings.algorithmSettings.augeEnableSleepTracking) {
            val wSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
            sleepPenalty = when {
                wSleep < 4.5  ->  40.0  // Crítico
                wSleep < 5.5  ->  25.0  // Malo
                wSleep < 6.5  ->  15.0  // Subóptimo
                wSleep >= 8.5 -> -15.0  // Sleep banking: limpia SNC
                wSleep > 7.5  ->  -5.0  // Bonus estándar
                else          ->   0.0
            }
        }

        // Life stress
        var lifeStress = 0.0
        wellbeing?.let { w ->
            if (w.stressLevel >= 4)  lifeStress += 15.0
            else if (w.stressLevel == 3) lifeStress += 5.0
            if (w.workIntensity == IntensityLevel.HIGH || w.studyIntensity == IntensityLevel.HIGH) lifeStress += 10.0
        }

        val total = normalizedGymFatigue + sleepPenalty + lifeStress
        val cnsBattery = clamp(100.0 - total, 0.0, 100.0).toInt()

        return Triple(cnsBattery, normalizedGymFatigue.toInt(), (sleepPenalty + lifeStress).toInt())
    }

    // ─── 3. BATERÍA ESPINAL ───────────────────────────────────────────────────

    fun calculateSpinalBattery(
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Int {
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val last7Days = now - 7L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > last7Days }

        var spinalLoad = 0.0
        recentLogs.forEach { log ->
            val daysAgo = (now - logDateMs(log)) / (24.0 * 3_600_000)
            val recency = max(0.1, exp(-0.3 * daysAgo))

            log.completedExercises.forEach { ex ->
                val dbInfo = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    val drain = calculateSetBatteryDrain(s, metrics, tanks, 0, ex.restTime)
                    spinalLoad += drain.spinalDrainPct * recency
                }
            }
        }

        return clamp(100.0 - spinalLoad, 0.0, 100.0).toInt()
    }

    // ─── 4. BATERÍAS GLOBALES ─────────────────────────────────────────────────

    fun calculateGlobalBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
    ): GlobalBatteries {
        val pillarBatteries = PILLAR_MUSCLES.map { muscle ->
            calculateMuscleBattery(muscle, history, wellbeing, settings, exerciseDb, sleepLogs = sleepLogs).recoveryScore
        }
        val muscularAvg = if (pillarBatteries.isEmpty()) 100 else pillarBatteries.average().toInt()

        val (cncBattery, _, _) = calculateSystemicFatigue(history, wellbeing, settings, exerciseDb, sleepLogs)
        val spinalBattery = calculateSpinalBattery(history, settings, exerciseDb)

        return GlobalBatteries(
            muscular = muscularAvg.coerceIn(0, 100),
            cnc      = cncBattery.coerceIn(0, 100),
            spinal   = spinalBattery.coerceIn(0, 100),
        )
    }

    // ─── 5. PER-MUSCLE BATTERIES (todos los pilares) ──────────────────────────

    fun getPerMuscleBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
    ): Map<String, MuscleRecoveryStatus> {
        return PILLAR_MUSCLES.associateWith { muscle ->
            calculateMuscleBattery(muscle, history, wellbeing, settings, exerciseDb, sleepLogs = sleepLogs)
        }
    }

    // ─── 6. READINESS DIARIA ─────────────────────────────────────────────────

    fun calculateDailyReadiness(
        cnsBattery: Int,
        wellbeing: DailyWellbeingLog?,
    ): AugeReadinessVerdict {
        val sleepScore = when {
            wellbeing == null -> 70
            wellbeing.sleepHours >= 8.5 -> 100
            wellbeing.sleepHours >= 7.5 -> 90
            wellbeing.sleepHours >= 6.5 -> 70
            wellbeing.sleepHours >= 5.5 -> 50
            else -> 25
        }

        val stressScore = when (wellbeing?.stressLevel ?: 3) {
            1 -> 100; 2 -> 85; 3 -> 70; 4 -> 45
            else -> 20
        }

        val domsScore = when (wellbeing?.doms ?: 1) {
            1 -> 100; 2 -> 85; 3 -> 65; 4 -> 35
            else -> 10
        }

        val score = (cnsBattery * 0.4 + sleepScore * 0.3 + stressScore * 0.2 + domsScore * 0.1).toInt()
            .coerceIn(0, 100)

        val (label, color) = when {
            score >= 80 -> "Óptimo para entrenar" to ReadinessColor.GREEN
            score >= 70 -> "Buen estado" to ReadinessColor.GREEN
            score >= 55 -> "Moderado" to ReadinessColor.YELLOW
            score >= 40 -> "Cargado" to ReadinessColor.YELLOW
            else        -> "Descanso recomendado" to ReadinessColor.RED
        }

        val details = buildList {
            if (wellbeing != null) {
                if (wellbeing.sleepHours < 6) add("Sueño insuficiente (${wellbeing.sleepHours}h)")
                if (wellbeing.stressLevel >= 4) add("Estrés elevado")
                if (wellbeing.doms >= 4) add("DOMS severo reportado")
            }
            if (cnsBattery < 50) add("SNC bajo (${cnsBattery}%)")
        }

        return AugeReadinessVerdict(score = score, label = label, color = color, details = details)
    }

    // ─── 7. ENCUESTAS PENDIENTES ─────────────────────────────────────────────

    fun checkPendingSurveys(
        history: List<WorkoutLog>,
        feedbacks: List<PostSessionFeedback>,
    ): PendingQuestionnaire? {
        val now = nowMs()
        val twoHours  = 2L  * 3600 * 1000
        val fortyEightHours = 48L * 3600 * 1000

        return history.firstOrNull { log ->
            val timeSince = now - logDateMs(log)
            timeSince > twoHours &&
            timeSince < fortyEightHours &&
            feedbacks.none { it.logId == log.id }
        }?.let { log ->
            PendingQuestionnaire(
                logId         = log.id,
                sessionName   = log.sessionName,
                muscleGroups  = emptyList(), // Se llena en la UI con los músculos del log
                scheduledTimeMs = logDateMs(log) + 24 * 3600 * 1000,
            )
        }
    }

    // ─── 8. RECOMENDACIÓN DE SUEÑO ───────────────────────────────────────────

    fun calculateSleepRecommendations(
        settings: Settings,
        wellbeing: DailyWellbeingLog?,
        todayLog: WorkoutLog?,
    ): SleepRecommendation {
        val base = settings.sleepTargetHours
        var extra = 0.0
        val reasons = mutableListOf<String>()

        todayLog?.let { log ->
            val volume = log.completedExercises.size * 3
            if (volume > 15 || (log.sessionStressScore ?: 0.0) > 50) {
                extra += 0.75; reasons.add("Alta carga neural")
            } else if (volume > 10) {
                extra += 0.5; reasons.add("Volumen moderado")
            }
        }

        wellbeing?.let { w ->
            if (w.workIntensity == IntensityLevel.HIGH || w.studyIntensity == IntensityLevel.HIGH) {
                extra += 0.5; reasons.add("Carga cognitiva alta")
            }
            if (w.stressLevel >= 4) {
                extra += 0.25; reasons.add("Estrés elevado")
            }
        }

        return SleepRecommendation(
            targetHours = (base + extra).coerceIn(6.0, 10.0),
            reasons = reasons,
        )
    }

    // ─── 9. MULTIPLICADOR NUTRICIONAL ────────────────────────────────────────
    // Sin logs de nutrición activos, usa el objetivo calórico configurado.
    // Equivalente al fallback de computeNutritionRecoveryMultiplier() en nutritionRecovery.ts.
    // Cuando se migre el módulo de nutrición, pasar NutritionLog[] y activar el cálculo completo.

    fun getNutritionMultiplier(settings: Settings): Double {
        if (!settings.algorithmSettings.augeEnableNutritionTracking) return 1.0
        // Fallback: inferir multiplicador del objetivo calórico en settings
        return when (settings.calorieGoalObjective) {
            CalorieGoalObjective.DEFICIT     -> 1.25  // Déficit: recuperación 25% más lenta
            CalorieGoalObjective.SURPLUS     -> 0.95  // Superávit: leve aceleración
            CalorieGoalObjective.MAINTENANCE -> 1.0   // Mantenimiento: sin ajuste
        }
    }

    // Expose PILLAR_MUSCLES para la UI
    val pillarMuscles: List<String> get() = PILLAR_MUSCLES
}
