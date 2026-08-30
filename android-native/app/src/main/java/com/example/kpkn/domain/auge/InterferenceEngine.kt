package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeUtils.parseIsoMs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * InterferenceEngine — Detecta interferencia entre sesiones de entrenamiento.
 *
 * La interferencia ocurre cuando una sesión previa ha fatigado un músculo
 * que también se usa significativamente en la sesión siguiente, antes de
 * que se recupere completamente.
 *
 * Fórmula core por músculo M:
 *   fatigaResidual_M = drainA_M × e^(-ln2/halfLife_M × horasEntre)
 *   usoEnB_M = weightedRoleUsage_M_in_B
 *   interferencia_M = fatigaResidual_M × usoEnB_M
 *   interferenciaPct = Σ(interferencia_M × peso_M) / Σ(peso_M) × 100
 */
object InterferenceEngine {

    // Media-vida de recuperación muscular (horas) — alineado con MUSCLE_PROFILE_MAP
    private val MUSCLE_HALF_LIFE: Map<String, Double> = mapOf(
        // fast: 24h
        "Bíceps" to 24.0, "Tríceps" to 24.0, "Deltoides" to 24.0,
        "Deltoides Anterior" to 24.0, "Deltoides Lateral" to 24.0, "Deltoides Posterior" to 24.0,
        "Pantorrillas" to 24.0, "Abdomen" to 24.0, "Antebrazo" to 24.0,
        // medium: 48h
        "Pectorales" to 48.0, "Dorsales" to 48.0, "Hombros" to 48.0, "Trapecio" to 48.0,
        "Core" to 48.0, "Aductores" to 48.0, "Cuello" to 48.0,
        // slow: 72h
        "Cuádriceps" to 72.0, "Glúteos" to 72.0,
        // heavy: 96h
        "Isquiosurales" to 96.0, "Erectores Espinales" to 96.0,
    )

    private const val MAX_HOURS_APART = 96.0

    // Umbral mínimo de drenaje residual para que un músculo cuente como interferencia
    private const val MIN_RESIDUAL_THRESHOLD = 0.08   // 8% fatiga residual mínima
    private const val MIN_USAGE_THRESHOLD    = 0.25   // rol mínimo de uso en sesión B

    // ─── API pública ─────────────────────────────────────────────────────────

    /**
     * Calcula interferencias a partir del **historial real** de sesiones.
     * Analiza pares consecutivos de WorkoutLogs en los últimos [days] días.
     */
    fun calculateHistoricalInterferences(
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
        days: Int = 30,
    ): List<SessionInterference> {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600_000L
        val recent = history
            .filter { parseIsoMs(it.date) >= cutoff }
            .sortedBy { parseIsoMs(it.date) }

        val results = mutableListOf<SessionInterference>()

        for (i in 0 until recent.size - 1) {
            val logA = recent[i]
            val logB = recent[i + 1]
            val msA  = parseIsoMs(logA.date)
            val msB  = parseIsoMs(logB.date)
            val hoursApart = (msB - msA) / 3_600_000.0

            if (hoursApart <= 0 || hoursApart > MAX_HOURS_APART) continue

            val drainsA = buildMuscleDrainsFromLog(logA, exerciseDb, settings)
            val usagesB = buildMuscleUsagesFromLog(logB, exerciseDb)

            val interference = computeInterference(
                sessionAId   = logA.sessionId,
                sessionAName = logA.sessionName,
                sessionBId   = logB.sessionId,
                sessionBName = logB.sessionName,
                sessionADate = logA.date.take(10),
                sessionBDate = logB.date.take(10),
                drainsA      = drainsA,
                usagesB      = usagesB,
                hoursApart   = hoursApart,
                isFromHistory = true,
            )

            if (interference != null) results.add(interference)
        }

        return results.sortedByDescending { it.interferencePercent }
    }

    /**
     * Calcula interferencias **planificadas** para el programa activo.
     * Analiza pares de sesiones contiguas (por dayOfWeek o posición en la semana).
     */
    fun calculatePlannedInterferences(
        program: Program?,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): List<SessionInterference> {
        if (program == null) return emptyList()

        // Extraer sesiones de la primera semana del primer bloque activo
        val sessions = mutableListOf<Pair<Session, Int>>()  // (session, dayOfWeek 1-7)

        outer@ for (macro in program.macrocycles) {
            for (block in macro.blocks) {
                for (meso in block.mesocycles) {
                    val week = meso.weeks.firstOrNull() ?: continue
                    week.sessions.forEachIndexed { idx, session ->
                        val dow = session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: (idx + 1)
                        sessions.add(Pair(session, dow))
                    }
                    if (sessions.isNotEmpty()) break@outer
                }
            }
        }

        // Si el programa tiene macrocycles vacíos, intentar leer de loops
        if (sessions.isEmpty()) return emptyList()

        // Ordenar por día de semana
        val sorted = sessions.sortedBy { it.second }

        val results = mutableListOf<SessionInterference>()

        for (i in 0 until sorted.size - 1) {
            val (sessionA, dayA) = sorted[i]
            val (sessionB, dayB) = sorted[i + 1]
            val hoursApart = ((dayB - dayA).coerceAtLeast(1)) * 24.0

            if (hoursApart > MAX_HOURS_APART) continue

            val drainsA = buildMuscleDrainsFromSession(sessionA, exerciseDb)
            val usagesB = buildMuscleUsagesFromSession(sessionB, exerciseDb)

            val interference = computeInterference(
                sessionAId   = sessionA.id,
                sessionAName = sessionA.name,
                sessionBId   = sessionB.id,
                sessionBName = sessionB.name,
                sessionADate = null,
                sessionBDate = null,
                drainsA      = drainsA,
                usagesB      = usagesB,
                hoursApart   = hoursApart,
                isFromHistory = false,
            )

            if (interference != null) results.add(interference)
        }

        // También analizar el último día de la semana con el primero (split cíclico)
        if (sorted.size >= 2) {
            val (sessionZ, dayZ) = sorted.last()
            val (sessionFirst, dayFirst) = sorted.first()
            // Asume semana de 7 días: días restantes hasta el lunes siguiente
            val hoursApart = ((7 - dayZ + dayFirst).coerceAtLeast(1)) * 24.0
            if (hoursApart <= MAX_HOURS_APART) {
                val drainsZ  = buildMuscleDrainsFromSession(sessionZ, exerciseDb)
                val usagesF  = buildMuscleUsagesFromSession(sessionFirst, exerciseDb)
                val interference = computeInterference(
                    sessionAId   = sessionZ.id,
                    sessionAName = sessionZ.name,
                    sessionBId   = sessionFirst.id,
                    sessionBName = sessionFirst.name,
                    sessionADate = null,
                    sessionBDate = null,
                    drainsA      = drainsZ,
                    usagesB      = usagesF,
                    hoursApart   = hoursApart,
                    isFromHistory = false,
                )
                if (interference != null) results.add(interference)
            }
        }

        return results.sortedByDescending { it.interferencePercent }
    }

    /**
     * Interferencia de la sesión que se está editando contra historial reciente
     * y otras sesiones de la semana (seguidas o separadas, si el tiempo no alcanza).
     */
    fun analyzeUpcomingSession(
        current: Session,
        weekSessions: List<Session>,
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SessionInterference> {
        val usagesB = buildMuscleUsagesFromSession(current, exerciseDb)
        if (usagesB.isEmpty()) return emptyList()

        val results = mutableListOf<SessionInterference>()
        val cutoff = nowMs - (MAX_HOURS_APART * 3_600_000.0).toLong()

        history.filter { parseIsoMs(it.date) in cutoff until nowMs }.forEach { log ->
            val hoursApart = (nowMs - parseIsoMs(log.date)) / 3_600_000.0
            if (hoursApart <= 0.0 || hoursApart > MAX_HOURS_APART) return@forEach
            val interference = computeInterference(
                sessionAId = log.sessionId,
                sessionAName = log.sessionName,
                sessionBId = current.id,
                sessionBName = current.name,
                sessionADate = log.date.take(10),
                sessionBDate = null,
                drainsA = buildMuscleDrainsFromLog(log, exerciseDb, settings),
                usagesB = usagesB,
                hoursApart = hoursApart,
                isFromHistory = true,
            )
            if (interference != null) results.add(interference)
        }

        val currentDow = current.dayOfWeek ?: current.assignedDays.firstOrNull()
        weekSessions.filter { it.id != current.id }.forEach { other ->
            val hoursApart = hoursBetweenAssignedDays(
                fromDow = other.dayOfWeek ?: other.assignedDays.firstOrNull(),
                toDow = currentDow,
            ) ?: return@forEach
            if (hoursApart <= 0.0 || hoursApart > MAX_HOURS_APART) return@forEach
            val interference = computeInterference(
                sessionAId = other.id,
                sessionAName = other.name,
                sessionBId = current.id,
                sessionBName = current.name,
                sessionADate = null,
                sessionBDate = null,
                drainsA = buildMuscleDrainsFromSession(other, exerciseDb),
                usagesB = usagesB,
                hoursApart = hoursApart,
                isFromHistory = false,
            )
            if (interference != null) results.add(interference)
        }

        return results
            .sortedByDescending { it.interferencePercent }
            .distinctBy { it.sessionAId to it.sessionBId }
    }

    private fun hoursBetweenAssignedDays(fromDow: Int?, toDow: Int?): Double? {
        if (fromDow == null || toDow == null) return null
        val raw = toDow - fromDow
        if (raw == 0) return null
        val days = if (raw > 0) raw else raw + 7
        return days * 24.0
    }

    // ─── Construcción de drains/usages ───────────────────────────────────────

    /**
     * Estima el drenaje por músculo de un WorkoutLog real.
     * Returns Map<muscleName, drainFraction (0-1)>
     */
    private fun buildMuscleDrainsFromLog(
        log: WorkoutLog,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): Map<String, Double> {
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val drains = mutableMapOf<String, Double>()

        log.completedExercises.forEach { ce ->
            ce.cardioDetails?.let { cardio ->
                val duration = ce.sets.sumOf { it.timeSeconds ?: 0 }
                    .takeIf { it > 0 }
                    ?: return@forEach
                val rpe = ce.sets.firstOrNull { (it.timeSeconds ?: 0) > 0 }?.rpe ?: cardio.resolvedRpe()
                val cardioDrain = CardioRingDrainEngine.drain(cardio, duration, rpe, settings)
                cardioDrain.muscleDrains.forEach { (muscleKey, drainPct) ->
                    val muscleDrain = drainPct * 0.01
                    drains[muscleKey] = (drains[muscleKey] ?: 0.0) + muscleDrain
                }
                return@forEach
            }
            val info = resolveExercise(
                catalogConfigurationId = ce.catalogConfigurationId,
                exerciseDbId = ce.exerciseDbId,
                exerciseId = ce.exerciseId,
                exerciseName = ce.exerciseName,
                exerciseDb = exerciseDb,
            ) ?: return@forEach
            val accumulated = mutableMapOf<String, Int>()

            ce.sets.forEach { set ->
                if (!AugeFatigueEngine.isSetEffective(set)) return@forEach
                val metrics = AugeFatigueEngine.getDynamicAugeMetrics(info.name, info.equipment, info) ?: AugeMetrics()
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set             = set,
                    metrics         = metrics,
                    tanks           = tanks,
                    accumulatedSets = accumulated.values.sum(),
                    restTime        = ce.supersetRestBetween ?: ce.restTime,
                    weightUnit      = settings.weightUnit,
                )
                val involvedMuscles = ce.effectiveMuscles?.takeIf { it.isNotEmpty() }
                    ?: info.involvedMuscles
                involvedMuscles.forEach { im ->
                    val roleW = resolveMuscleVolumeContribution(im)
                    if (roleW > 0.0) {
                        val muscleKey = getAugeMusclePillarId(im.muscle, im.emphasis)
                        val muscleDrain = drain.muscularDrainPct * roleW * 0.01
                        drains[muscleKey] = (drains[muscleKey] ?: 0.0) + muscleDrain
                    }
                }
                accumulated[ce.exerciseId] = (accumulated[ce.exerciseId] ?: 0) + 1
            }
        }

        return drains.mapValues { (_, v) -> v.coerceIn(0.0, 1.0) }
    }

    /**
     * Estima el uso relativo de músculos en un WorkoutLog (para el lado B).
     * Returns Map<muscleName, usageFraction (0-1)>
     */
    private fun buildMuscleUsagesFromLog(
        log: WorkoutLog,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val usages = mutableMapOf<String, Double>()
        log.completedExercises.forEach { ce ->
            val info = resolveExercise(
                catalogConfigurationId = ce.catalogConfigurationId,
                exerciseDbId = ce.exerciseDbId,
                exerciseId = ce.exerciseId,
                exerciseName = ce.exerciseName,
                exerciseDb = exerciseDb,
            ) ?: return@forEach
            val involvedMuscles = ce.effectiveMuscles?.takeIf { it.isNotEmpty() }
                ?: info.involvedMuscles
            involvedMuscles.forEach { im ->
                val roleW = resolveMuscleVolumeContribution(im)
                if (roleW > 0.0) {
                    val muscleKey = getAugeMusclePillarId(im.muscle, im.emphasis)
                    usages[muscleKey] = maxOf(usages[muscleKey] ?: 0.0, roleW)
                }
            }
        }
        // Normalizar a 0-1
        val maxUsage = usages.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        return usages.mapValues { (_, v) -> v / maxUsage }
    }

    /**
     * Estima el drenaje potencial por músculo de una Session planificada.
     * Sin datos de sets reales, usa el recuento de ejercicios y el EFC.
     */
    private fun buildMuscleDrainsFromSession(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val drains = mutableMapOf<String, Double>()
        val allExercises = session.exercises + session.parts.flatMap { it.exercises }

        allExercises.forEach { ex ->
            val info = resolveExercise(
                catalogConfigurationId = ex.catalogConfigurationId,
                exerciseDbId = ex.exerciseDbId,
                exerciseId = ex.exerciseId ?: ex.id,
                exerciseName = ex.name,
                exerciseDb = exerciseDb,
            ) ?: return@forEach
            val metrics = AugeFatigueEngine.getDynamicAugeMetrics(info.name, info.equipment, info) ?: AugeMetrics()
            val workingSets = ex.sets.count { !it.isIneffective }.coerceAtLeast(1)
            val estimatedDrain = ((metrics.efc / 5.0) * 0.18 * workingSets).coerceIn(0.08, 0.85)

            val involvedMuscles = if (!ex.effectiveMuscles.isNullOrEmpty()) {
                ex.effectiveMuscles!!
            } else {
                info.involvedMuscles
            }

            involvedMuscles.forEach { im ->
                val roleW = resolveMuscleVolumeContribution(im)
                if (roleW > 0.0) {
                    val muscleKey = getAugeMusclePillarId(im.muscle, im.emphasis)
                    val muscleDrain = estimatedDrain * roleW
                    drains[muscleKey] = maxOf(drains[muscleKey] ?: 0.0, muscleDrain)
                }
            }
        }

        return drains.mapValues { (_, v) -> v.coerceIn(0.0, 1.0) }
    }

    private fun buildMuscleUsagesFromSession(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val usages = mutableMapOf<String, Double>()
        val allExercises = session.exercises + session.parts.flatMap { it.exercises }

        allExercises.forEach { ex ->
            val info = resolveExercise(
                catalogConfigurationId = ex.catalogConfigurationId,
                exerciseDbId = ex.exerciseDbId,
                exerciseId = ex.exerciseId,
                exerciseName = ex.name,
                exerciseDb = exerciseDb,
            ) ?: return@forEach
            val involvedMuscles = if (!ex.effectiveMuscles.isNullOrEmpty()) {
                ex.effectiveMuscles!!
            } else {
                info.involvedMuscles
            }
            involvedMuscles.forEach { im ->
                val roleW = resolveMuscleVolumeContribution(im)
                if (roleW > 0.0) {
                    // Drains and usages must share the AUGE pillar key. Raw
                    // chip names such as "Glúteo Mayor" otherwise miss
                    // planned/logged "Glúteos" in the intersection.
                    val muscleKey = getAugeMusclePillarId(im.muscle, im.emphasis)
                    usages[muscleKey] = maxOf(usages[muscleKey] ?: 0.0, roleW)
                }
            }
        }

        val maxUsage = usages.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        return usages.mapValues { (_, v) -> v / maxUsage }
    }

    // ─── Cálculo de interferencia ─────────────────────────────────────────────

    private fun computeInterference(
        sessionAId: String,
        sessionAName: String,
        sessionBId: String,
        sessionBName: String,
        sessionADate: String?,
        sessionBDate: String?,
        drainsA: Map<String, Double>,
        usagesB: Map<String, Double>,
        hoursApart: Double,
        isFromHistory: Boolean,
    ): SessionInterference? {
        val sharedMuscles = mutableListOf<SharedMuscleInterference>()
        var weightedInterferenceSum = 0.0
        var totalWeight = 0.0

        // Músculos que tienen drenaje en A Y uso en B
        val commonMuscles = drainsA.keys.intersect(usagesB.keys)

        commonMuscles.forEach { muscle ->
            val drainA  = drainsA[muscle] ?: return@forEach
            val usageB  = usagesB[muscle] ?: return@forEach

            if (drainA < MIN_RESIDUAL_THRESHOLD || usageB < MIN_USAGE_THRESHOLD) return@forEach

            val halfLife = MUSCLE_HALF_LIFE[muscle] ?: 48.0
            // Fatiga residual al momento de la sesión B: decay exponencial
            val residualFatigue = drainA * exp(-ln(2.0) / halfLife * hoursApart)

            if (residualFatigue < 0.03) return@forEach   // prácticamente recuperado

            val recoveryDeficit = residualFatigue * usageB
            val muscleWeight    = usageB   // los músculos más usados en B pesan más

            sharedMuscles.add(
                SharedMuscleInterference(
                    muscleName       = muscle,
                    drainFromSessionA = residualFatigue,
                    usageInSessionB  = usageB,
                    recoveryDeficit  = recoveryDeficit,
                )
            )

            weightedInterferenceSum += recoveryDeficit * muscleWeight
            totalWeight += muscleWeight
        }

        if (sharedMuscles.isEmpty()) return null
        if (totalWeight <= 0.0) return null

        val interferenceRatio = (weightedInterferenceSum / totalWeight).coerceIn(0.0, 1.0)
        val interferencePercent = (interferenceRatio * 100).toInt().coerceIn(1, 100)

        // Recomendación basada en nivel de interferencia
        val recommendation = buildRecommendation(interferencePercent, sharedMuscles)

        return SessionInterference(
            sessionAId        = sessionAId,
            sessionAName      = sessionAName,
            sessionBId        = sessionBId,
            sessionBName      = sessionBName,
            sessionADate      = sessionADate,
            sessionBDate      = sessionBDate,
            interferencePercent = interferencePercent,
            sharedMuscles     = sharedMuscles.sortedByDescending { it.recoveryDeficit },
            recommendation    = recommendation,
            isFromHistory     = isFromHistory,
            hoursApart        = hoursApart,
        )
    }

    private fun buildRecommendation(
        pct: Int,
        muscles: List<SharedMuscleInterference>,
    ): String {
        val topMuscle = muscles.maxByOrNull { it.recoveryDeficit }?.muscleName ?: "músculos"
        return when {
            pct >= 70 -> "Alta interferencia en $topMuscle. Considera mover un día de separación o cambiar ejercicios compuestos por aislamiento."
            pct >= 45 -> "Interferencia moderada en $topMuscle. Reduce el volumen o la intensidad en los ejercicios que involucren este grupo."
            pct >= 25 -> "Interferencia leve en $topMuscle. El rendimiento puede verse ligeramente afectado. Considera calentar bien ese músculo."
            else       -> "Interferencia mínima. El split está bien organizado para estos dos días."
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun resolveExercise(
        catalogConfigurationId: String?,
        exerciseDbId: String?,
        exerciseId: String?,
        exerciseName: String?,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
        index = exerciseDb,
        catalogConfigurationId = catalogConfigurationId,
        exerciseDbId = exerciseDbId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
    )
}
