package com.example.kpkn.domain.auge

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

    // Peso de impacto por rol muscular (FATIGUE_ROLE_MULTIPLIERS)
    private val ROLE_DRAIN_WEIGHT = mapOf(
        MuscleRole.PRIMARY    to 1.0,
        MuscleRole.SECONDARY  to 0.6,
        MuscleRole.STABILIZER to 0.3,
        MuscleRole.NEUTRALIZER to 0.15,
    )

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

            // Solo analizar si B ocurre dentro de 72h de A
            if (hoursApart <= 0 || hoursApart > 72.0) continue

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

            if (hoursApart > 72.0) continue

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
            if (hoursApart <= 72.0) {
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
            val info = resolveExercise(ce.exerciseDbId, ce.exerciseName, exerciseDb) ?: return@forEach
            val accumulated = mutableMapOf<String, Int>()

            ce.sets.forEach { set ->
                if (!AugeFatigueEngine.isSetEffective(set)) return@forEach
                val metrics = AugeFatigueEngine.getDynamicAugeMetrics(info.name, info.equipment) ?: AugeMetrics()
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set             = set,
                    metrics         = metrics,
                    tanks           = tanks,
                    accumulatedSets = accumulated.values.sum(),
                    restTime        = ce.restTime,
                )
                info.involvedMuscles.forEach { im ->
                    val roleW = ROLE_DRAIN_WEIGHT[im.role] ?: 0.0
                    if (roleW > 0.0) {
                        val muscleDrain = drain.muscularDrainPct * roleW * 0.01
                        drains[im.muscle] = (drains[im.muscle] ?: 0.0) + muscleDrain
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
            val info = resolveExercise(ce.exerciseDbId, ce.exerciseName, exerciseDb) ?: return@forEach
            info.involvedMuscles.forEach { im ->
                val roleW = ROLE_DRAIN_WEIGHT[im.role] ?: 0.0
                if (roleW > 0.0) {
                    usages[im.muscle] = maxOf(usages[im.muscle] ?: 0.0, roleW)
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
            val info = resolveExercise(ex.exerciseDbId, ex.name, exerciseDb) ?: return@forEach
            val metrics = AugeFatigueEngine.getDynamicAugeMetrics(info.name, info.equipment) ?: AugeMetrics()
            // Estimar drenaje basado en EFC normalizado (sin sets reales)
            val estimatedDrain = (metrics.efc / 5.0) * 0.4   // 40% max drain estimado por ejercicio

            val involvedMuscles = if (!ex.effectiveMuscles.isNullOrEmpty()) {
                ex.effectiveMuscles!!
            } else {
                info.involvedMuscles
            }

            involvedMuscles.forEach { im ->
                val roleW = ROLE_DRAIN_WEIGHT[im.role] ?: 0.0
                if (roleW > 0.0) {
                    val muscleDrain = estimatedDrain * roleW
                    drains[im.muscle] = maxOf(drains[im.muscle] ?: 0.0, muscleDrain)
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
            val info = resolveExercise(ex.exerciseDbId, ex.name, exerciseDb) ?: return@forEach
            val involvedMuscles = if (!ex.effectiveMuscles.isNullOrEmpty()) {
                ex.effectiveMuscles!!
            } else {
                info.involvedMuscles
            }
            involvedMuscles.forEach { im ->
                val roleW = ROLE_DRAIN_WEIGHT[im.role] ?: 0.0
                if (roleW > 0.0) {
                    usages[im.muscle] = maxOf(usages[im.muscle] ?: 0.0, roleW)
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
        dbId: String?,
        name: String,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        if (!dbId.isNullOrBlank()) return exerciseDb[dbId]
        // Fallback: match por nombre normalizado
        val normName = name.lowercase().trim()
        return exerciseDb.values.firstOrNull {
            it.name.lowercase().trim() == normName
                || it.alias?.lowercase()?.trim() == normName
        }
    }
}
