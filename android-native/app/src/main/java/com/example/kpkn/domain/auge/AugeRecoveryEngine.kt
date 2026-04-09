package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
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

    private fun recoveryBand(score: Int): RecoveryBand = when {
        score >= 85 -> RecoveryBand.HIGH
        score >= 70 -> RecoveryBand.NORMAL
        score >= 50 -> RecoveryBand.MODERATE
        score >= 35 -> RecoveryBand.LOW
        else -> RecoveryBand.CRITICAL
    }

    private fun confidenceLabel(score: Int): String = when {
        score >= 80 -> "Alta"
        score >= 60 -> "Media"
        else -> "Baja"
    }

    private fun actionForChannel(id: RecoveryChannelId, score: Int): String = when (id) {
        RecoveryChannelId.MUSCULAR -> when {
            score >= 85 -> "Puedes meter volumen alto si la sesión lo pide."
            score >= 70 -> "Volumen normal y buena ejecución."
            score >= 50 -> "Modera series duras en el músculo más cargado."
            score >= 35 -> "Prioriza técnica y recorta volumen local."
            else -> "No fuerces volumen local hoy."
        }
        RecoveryChannelId.SYSTEM -> when {
            score >= 85 -> "Buen día para intensidad y coordinación."
            score >= 70 -> "Empuja normal, sin necesidad de ir al límite."
            score >= 50 -> "Mejor dejar alguna repetición en reserva."
            score >= 35 -> "Evita sets al fallo y compuestos muy demandantes."
            else -> "Haz una sesión ligera o técnica."
        }
        RecoveryChannelId.STRUCTURE -> when {
            score >= 85 -> "Toleras bien carga axial y tensión conectiva."
            score >= 70 -> "Carga estructural normal con buena técnica."
            score >= 50 -> "Conviene moderar impacto axial y variantes agresivas."
            score >= 35 -> "Usa variantes estables o menos compresivas."
            else -> "Evita carga axial o trabajo explosivo hoy."
        }
    }

    private fun logDateMs(log: WorkoutLog): Long = try {
        Instant.parse(log.date).toEpochMilli()
    } catch (e: Exception) {
        try {
            val ld = LocalDate.parse(log.date.take(10))
            ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e2: Exception) { 0L }
    }

    private fun muscleMatchesCategory(specificMuscle: String, category: String): Boolean {
        return matchesAugeMuscleTarget(specificMuscle, category)
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

    private fun systemicRecoveryMultiplier(
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog>,
    ): Double {
        var multiplier = 1.0
        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        when {
            weightedSleep < 5.5 -> multiplier *= 1.45
            weightedSleep < 6.5 -> multiplier *= 1.18
            weightedSleep >= 8.5 -> multiplier *= 0.88
            weightedSleep >= 7.5 -> multiplier *= 0.94
        }
        wellbeing?.let { w ->
            when (w.stressLevel) {
                5 -> multiplier *= 1.25
                4 -> multiplier *= 1.15
                2 -> multiplier *= 0.98
                1 -> multiplier *= 0.95
            }
            if (w.workIntensity == IntensityLevel.HIGH || w.studyIntensity == IntensityLevel.HIGH) {
                multiplier *= 1.08
            }
        }
        return multiplier
    }

    private fun resolveDbInfo(
        ex: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        withNameFallback: Boolean = false,
    ): ExerciseMuscleInfo? {
        val lookupId = (ex.exerciseDbId ?: ex.exerciseId)?.lowercase()
        val byId = lookupId?.let { exerciseDb[it] }
        return if (byId != null || !withNameFallback) byId
        else exerciseDb.values.find { it.name.equals(ex.exerciseName, ignoreCase = true) }
    }

    private fun calculateMuscleDiscomfortPenaltyPct(
        muscleName: String,
        history: List<WorkoutLog>,
        now: Long,
    ): Double {
        var penalty = 0.0
        history.forEach { log ->
            val logTime = logDateMs(log)
            if (logTime <= 0L) return@forEach
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)
            val timeDecay = safeExp(-(hoursSince / 84.0))
            log.postExerciseReports.forEach { report ->
                report.discomfortIds
                    .asSequence()
                    .filter { it != "none" }
                    .distinct()
                    .forEach { discomfortId ->
                        val entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] ?: return@forEach
                        val matchesMuscle = entry.relatedMuscles.any { related ->
                            muscleMatchesCategory(related, muscleName) ||
                                muscleMatchesCategory(muscleName, related)
                        }
                        if (!matchesMuscle) return@forEach

                        val quality = report.technicalQuality.coerceIn(1, 10)
                        val qualityMult = when {
                            quality <= 4 -> 1.25
                            quality <= 6 -> 1.10
                            quality >= 9 -> 0.85
                            else -> 1.0
                        }
                        penalty += 8.0 * qualityMult * timeDecay
                    }
            }
        }
        return clamp(penalty, 0.0, 30.0)
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
                val dbInfo = resolveDbInfo(ex, exerciseDb, withNameFallback = true)

                val involvement = dbInfo?.involvedMuscles?.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                } ?: return@forEach

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(ex.supersetId, ex.restTime)

                var accumulated = 0
                val setStress = ex.sets.sumOf { s ->
                    if (!isSetEffective(s)) return@sumOf 0.0
                    accumulated += 1
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
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
                val dbInfo = resolveDbInfo(ex, exerciseDb, withNameFallback = true)

                val involvement = dbInfo?.involvedMuscles?.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                } ?: return@forEach

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo.equipment)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(ex.supersetId, ex.restTime)

                var accumulated = 0
                val rawStress = ex.sets.sumOf { s ->
                    if (!isSetEffective(s)) return@sumOf 0.0
                    accumulated += 1
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
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
        val discomfortPenaltyPct = calculateMuscleDiscomfortPenaltyPct(
            muscleName = muscleName,
            history = relevantHistory,
            now = now,
        )
        val totalFatigue = accumulatedFatigue + (capacity * (discomfortPenaltyPct / 100.0))

        val rawFatiguePct = (totalFatigue / capacity) * 100.0
        val fatiguePenalty = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / 30.0)), 0.0, 100.0)
        var battery = clamp(100.0 - fatiguePenalty, 0.0, 100.0)

        // Garantía de frescura
        if (totalFatigue <= 0.1 && (wellbeing?.doms ?: 1) <= 2) battery = 100.0

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
        if (battery < 90 && totalFatigue > 0) {
            val k = 2.9957 / realRecoveryTime
            val targetFatigue = (100 - 90) * capacity / 100.0
            if (totalFatigue > targetFatigue) {
                hoursToRecovery = max(0.0, -ln(targetFatigue / totalFatigue) / k).toInt()
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
        val tauHours = 36.0 * systemicRecoveryMultiplier(wellbeing, sleepLogs)
        val last10Days = now - 10L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > last10Days }

        var accumulatedGymLoad = 0.0
        recentLogs.forEach { log ->
            val muscleVolumeMap = mutableMapOf<String, Int>()
            var sessionCns = 0.0
            val hoursSince = max(0.0, (now - logDateMs(log)) / 3_600_000.0)

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val primaryMuscle = dbInfo?.involvedMuscles?.find { it.role == MuscleRole.PRIMARY }?.muscle ?: "Core"
                var accumulated = muscleVolumeMap[primaryMuscle] ?: 0
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(ex.supersetId, ex.restTime)

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
                    sessionCns += drain.cnsDrainPct
                    if (s.weight > 0.0 && s.reps <= 3 && AugeFatigueEngine.getEffectiveRPE(s) >= 9.5) {
                        sessionCns += drain.cnsDrainPct * 0.15
                    }
                }
                muscleVolumeMap[primaryMuscle] = accumulated
            }

            val durationMin = log.durationMinutes
            if (durationMin > 90) sessionCns *= 1.15
            else if (durationMin > 75) sessionCns *= 1.08

            accumulatedGymLoad += sessionCns * safeExp(-(hoursSince / tauHours))
        }

        val capacity = max(80.0, tanks.cns * 1.15)
        val rawGymPct = (accumulatedGymLoad / capacity) * 100.0
        val normalizedGymFatigue = clamp(100.0 * (1.0 - safeExp(-rawGymPct / 28.0)), 0.0, 100.0)

        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        var sleepPenalty = 0.0
        if (settings.algorithmSettings.augeEnableSleepTracking) {
            sleepPenalty = when {
                weightedSleep < 4.5  -> 30.0
                weightedSleep < 5.5  -> 18.0
                weightedSleep < 6.5  -> 10.0
                weightedSleep >= 8.5 -> -10.0
                weightedSleep >= 7.5 -> -4.0
                else -> 0.0
            }
        }

        var lifeStress = 0.0
        wellbeing?.let { w ->
            if (w.stressLevel >= 4)  lifeStress += 14.0
            else if (w.stressLevel == 3) lifeStress += 6.0
            if (w.workIntensity == IntensityLevel.HIGH || w.studyIntensity == IntensityLevel.HIGH) lifeStress += 10.0
            if (w.motivation <= 2) lifeStress += 4.0
        }

        val total = normalizedGymFatigue + sleepPenalty + lifeStress
        val cnsBattery = clamp(100.0 - total, 0.0, 100.0).toInt()

        return Triple(cnsBattery, normalizedGymFatigue.toInt(), (sleepPenalty + lifeStress).toInt())
    }

    // ─── 3. BATERÍA ESPINAL ───────────────────────────────────────────────────

    fun calculateSpinalBattery(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
    ): Int {
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val tauHours = 52.0 * systemicRecoveryMultiplier(wellbeing, sleepLogs)
        val last10Days = now - 10L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > last10Days }

        var accumulatedSpinalLoad = 0.0
        recentLogs.forEach { log ->
            val hoursSince = max(0.0, (now - logDateMs(log)) / 3_600_000.0)
            var sessionSpinalLoad = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(ex.supersetId, ex.restTime)
                var accumulated = 0

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated += 1
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
                    sessionSpinalLoad += drain.spinalDrainPct
                }
            }

            if (log.durationMinutes > 90) sessionSpinalLoad *= 1.08
            accumulatedSpinalLoad += sessionSpinalLoad * safeExp(-(hoursSince / tauHours))
        }

        val capacity = max(70.0, tanks.spinal * 0.02)
        val rawPct = (accumulatedSpinalLoad / capacity) * 100.0
        val normalizedLoad = clamp(100.0 * (1.0 - safeExp(-rawPct / 24.0)), 0.0, 100.0)
        return clamp(100.0 - normalizedLoad, 0.0, 100.0).toInt()
    }

    // ─── 4. BATERÍAS GLOBALES ─────────────────────────────────────────────────

    fun calculateGlobalBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
    ): GlobalBatteries {
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel)

        val pillarBatteries = PILLAR_MUSCLES.map { muscle ->
            calculateMuscleBattery(muscle, history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs).recoveryScore
        }
        val muscularAvg = if (pillarBatteries.isEmpty()) {
            100
        } else {
            val overallAvg = pillarBatteries.average()
            val sortedAsc = pillarBatteries.sorted()
            val bottomCount = maxOf(1, (sortedAsc.size * 0.25).toInt())
            val bottomQuartileAvg = sortedAsc.take(bottomCount).average()
            (overallAvg * 0.5 + bottomQuartileAvg * 0.5).toInt()
        }

        val (cncBattery, _, _) = calculateSystemicFatigue(history, wellbeing, settings, exerciseDb, sleepLogs)
        val spinalBattery = calculateSpinalBattery(history, wellbeing, settings, exerciseDb, sleepLogs)

        val manualNeural = wellbeing?.manualNeuralBattery
        val manualSpinal = wellbeing?.manualSpinalBattery

        return GlobalBatteries(
            muscular = muscularAvg.coerceIn(0, 100),
            cnc      = (manualNeural ?: cncBattery).coerceIn(0, 100),
            spinal   = (manualSpinal ?: spinalBattery).coerceIn(0, 100),
        )
    }

    // ─── 5. PER-MUSCLE BATTERIES (todos los pilares) ──────────────────────────

    fun getPerMuscleBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
    ): Map<String, MuscleRecoveryStatus> {
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel)
        return PILLAR_MUSCLES.associateWith { muscle ->
            val computed = calculateMuscleBattery(muscle, history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs)
            val manual = wellbeing?.manualMuscleBatteries?.get(muscle)
            if (manual != null) {
                computed.copy(recoveryScore = manual.coerceIn(0, 100))
            } else computed
        }
    }

    // ─── 6. DASHBOARD Y READINESS DIARIA ─────────────────────────────────────

    fun calculateRecoveryDashboard(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog> = emptyList(),
        recentSessionCount: Int = 0,
    ): RecoveryDashboard {
        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        val lowestMuscles = perMuscle.values.sortedBy { it.recoveryScore }.take(2)
        val weakestArticular = articularBatteries.entries.sortedBy { it.value.recoveryScore }.take(2)
        val articularFloor = weakestArticular
            .takeIf { it.isNotEmpty() }
            ?.map { it.value.recoveryScore.toDouble() }
            ?.average()
            ?.toInt()
            ?: 100
        val structureScore = min(
            batteries.spinal,
            ((batteries.spinal * 0.6) + (articularFloor * 0.4)).toInt(),
        ).coerceIn(0, 100)

        val baseConfidence = when {
            recentSessionCount >= 16 -> 82
            recentSessionCount >= 8 -> 70
            recentSessionCount >= 4 -> 58
            else -> 44
        }
        val muscularConfidence = (baseConfidence + if (perMuscle.isNotEmpty()) 8 else 0).coerceIn(35, 95)
        val systemConfidence = (
            baseConfidence +
                (if (sleepLogs.size >= 3) 12 else 0) +
                (if (wellbeing != null) 8 else 0)
            ).coerceIn(35, 95)
        val structureConfidence = (baseConfidence + if (articularBatteries.isNotEmpty()) 12 else 0).coerceIn(35, 95)

        val muscularCauses = buildList {
            if (lowestMuscles.isNotEmpty() && lowestMuscles.first().recoveryScore < 80) {
                add(lowestMuscles.joinToString(" y ") { "${it.muscleName} ${it.recoveryScore}%" })
            } else {
                add("Promedio de grupos pilar estable")
            }
            if ((wellbeing?.doms ?: 1) >= 4) add("Agujetas altas hoy")
            if (weightedSleep < 6.5) add("Sueño reciente por debajo de lo ideal")
        }
        val systemCauses = buildList {
            when {
                weightedSleep < 5.5 -> add("Poco sueño en las últimas noches")
                weightedSleep < 6.5 -> add("Sueño subóptimo reciente")
                weightedSleep >= 8.5 -> add("Buen colchón de sueño")
            }
            if ((wellbeing?.stressLevel ?: 3) >= 4) add("Estrés alto fuera del entrenamiento")
            if (batteries.cnc < 70) add("Carga neural reciente acumulada")
        }
        val structureCauses = buildList {
            if (batteries.spinal < 75) add("Carga axial reciente elevada")
            if (weakestArticular.isNotEmpty()) {
                add(weakestArticular.joinToString(" y ") { "${AugeTtcEngine.articularLabel(it.key)} ${it.value.recoveryScore}%" })
            } else {
                add("Sin cuello de botella estructural claro")
            }
            if ((wellbeing?.doms ?: 1) >= 4) add("Tejidos aún sensibles hoy")
        }

        val channels = listOf(
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.MUSCULAR,
                title = "Músculos",
                shortTitle = "Músc.",
                score = batteries.muscular,
                band = recoveryBand(batteries.muscular),
                description = "Cuánto volumen local toleran hoy tus músculos.",
                action = actionForChannel(RecoveryChannelId.MUSCULAR, batteries.muscular),
                causes = muscularCauses.take(3),
                confidence = muscularConfidence,
                editable = false,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.SYSTEM,
                title = "Sistema",
                shortTitle = "Sist.",
                score = batteries.cnc,
                band = recoveryBand(batteries.cnc),
                description = "Qué tanta intensidad, coordinación y producción de fuerza toleras hoy.",
                action = actionForChannel(RecoveryChannelId.SYSTEM, batteries.cnc),
                causes = systemCauses.take(3),
                confidence = systemConfidence,
                editable = true,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.STRUCTURE,
                title = "Estructura",
                shortTitle = "Estr.",
                score = structureScore,
                band = recoveryBand(structureScore),
                description = "Cómo llega hoy tu columna, tus tendones y tus articulaciones a la carga.",
                action = actionForChannel(RecoveryChannelId.STRUCTURE, structureScore),
                causes = structureCauses.take(3),
                confidence = structureConfidence,
                editable = true,
            ),
        )

        val overallScore = (
            channels.first { it.id == RecoveryChannelId.SYSTEM }.score * 0.40 +
                channels.first { it.id == RecoveryChannelId.MUSCULAR }.score * 0.35 +
                channels.first { it.id == RecoveryChannelId.STRUCTURE }.score * 0.25
            ).toInt().coerceIn(0, 100)
        val headline = when (recoveryBand(overallScore)) {
            RecoveryBand.HIGH -> "Listo para empujar"
            RecoveryBand.NORMAL -> "Buen estado para entrenar"
            RecoveryBand.MODERATE -> "Día para moderar"
            RecoveryBand.LOW -> "Llega cargado"
            RecoveryBand.CRITICAL -> "Prioriza recuperación"
        }
        val recommendation = when (recoveryBand(overallScore)) {
            RecoveryBand.HIGH -> "Hoy puedes entrenar normal o fuerte si la sesión lo pide."
            RecoveryBand.NORMAL -> "Hoy conviene entrenar normal, dejando algo en reserva."
            RecoveryBand.MODERATE -> "Hoy conviene moderar volumen o intensidad según el ring más bajo."
            RecoveryBand.LOW -> "Hoy conviene priorizar técnica, variantes estables y menos carga."
            RecoveryBand.CRITICAL -> "Hoy conviene descargar o hacer solo trabajo liviano."
        }
        val confidenceAverage = channels.map { it.confidence }.average().toInt()
        val limitingChannel = channels.minByOrNull { it.score }
        val summary = limitingChannel?.let {
            "${it.title} es hoy el factor limitante (${it.score}%)."
        } ?: "Tu estado está equilibrado."

        return RecoveryDashboard(
            overallScore = overallScore,
            headline = headline,
            summary = summary,
            recommendation = recommendation,
            confidenceLabel = confidenceLabel(confidenceAverage),
            channels = channels,
        )
    }

    fun calculateDailyReadiness(
        dashboard: RecoveryDashboard,
        wellbeing: DailyWellbeingLog?,
    ): AugeReadinessVerdict {
        val score = dashboard.overallScore.coerceIn(0, 100)
        val (label, color) = when (recoveryBand(score)) {
            RecoveryBand.HIGH -> "Óptimo para entrenar" to ReadinessColor.GREEN
            RecoveryBand.NORMAL -> "Buen estado" to ReadinessColor.GREEN
            RecoveryBand.MODERATE -> "Moderado" to ReadinessColor.YELLOW
            RecoveryBand.LOW -> "Cargado" to ReadinessColor.YELLOW
            RecoveryBand.CRITICAL -> "Descanso recomendado" to ReadinessColor.RED
        }
        val details = buildList {
            dashboard.channels.flatMap { it.causes }.take(4).forEach { add(it) }
            if ((wellbeing?.doms ?: 1) >= 4) add("Agujetas altas reportadas")
        }.distinct()

        return AugeReadinessVerdict(
            score = score,
            label = label,
            color = color,
            details = details,
            action = dashboard.recommendation,
            confidenceLabel = dashboard.confidenceLabel,
        )
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

    fun getNutritionMultiplier(
        settings: Settings,
        nutritionLogs: List<NutritionLog> = emptyList(),
        stressLevel: Int = 3,
    ): Double {
        if (!settings.algorithmSettings.augeEnableNutritionTracking) return 1.0
        val activePlan = runCatching { NutritionRepository.getInstance().activeNutritionPlan }.getOrNull()
        return NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = nutritionLogs,
            settings = settings,
            activePlan = activePlan,
            stressLevel = stressLevel,
        ).recoveryTimeMultiplier
    }

    // Expose PILLAR_MUSCLES para la UI
    val pillarMuscles: List<String> get() = PILLAR_MUSCLES
}
