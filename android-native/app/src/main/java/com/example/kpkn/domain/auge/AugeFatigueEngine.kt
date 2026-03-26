package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import kotlin.math.max
import kotlin.math.min

/**
 * AugeFatigueEngine — Motor de Fatiga AUGE v3.0 para Kotlin.
 * Funciones puras, sin estado. Equivalente a @kpkn/shared-domain fatigue.ts
 */
object AugeFatigueEngine {

    const val WEEKLY_CNS_FATIGUE_REFERENCE = 4000.0

    // ─── Capacidades base por tipo de atleta ─────────────────────────────────

    private val ATHLETE_CAPACITY: Map<AthleteType, Double> = mapOf(
        AthleteType.ENTHUSIAST    to 500.0,
        AthleteType.HYBRID        to 650.0,
        AthleteType.CALISTHENICS  to 600.0,
        AthleteType.BODYBUILDER   to 1000.0,
        AthleteType.POWERBUILDER  to 1100.0,
        AthleteType.POWERLIFTER   to 1200.0,
        AthleteType.WEIGHTLIFTER  to 1000.0,
    )

    fun getAthleteCapacity(settings: Settings): Double =
        ATHLETE_CAPACITY[settings.athleteType] ?: 500.0

    // ─── Tanques de batería personalizados ───────────────────────────────────

    fun calculatePersonalizedBatteryTanks(settings: Settings): BatteryTanks {
        val base = getAthleteCapacity(settings)
        return BatteryTanks(
            cns      = base * 1.2,
            muscular = base,
            spinal   = base * 0.8,
        )
    }

    // ─── Métricas dinámicas AUGE por nombre de ejercicio ─────────────────────

    fun getDynamicAugeMetrics(exerciseName: String, equipment: String? = null): AugeMetrics {
        val name = exerciseName.lowercase()

        var efc = 2.5; var ssc = 0.5; var cnc = 2.5

        // 1. Diccionario de patrones fundamentales
        when {
            name.contains("peso muerto") || name.contains("deadlift") -> {
                efc = 5.0; ssc = 2.0; cnc = 5.0
                if (name.contains("rumano") || name.contains("rdl"))  { efc = 4.2; ssc = 1.8; cnc = 4.0 }
                if (name.contains("sumo"))                             { efc = 4.8; ssc = 1.6; cnc = 4.8 }
            }
            name.contains("sentadilla") || name.contains("squat") -> {
                efc = 4.5; ssc = 1.5; cnc = 4.5
                if (name.contains("frontal") || name.contains("front"))           { efc = 4.2; ssc = 1.2; cnc = 4.5 }
                if (name.contains("búlgara") || name.contains("bulgarian"))       { efc = 3.8; ssc = 0.8; cnc = 3.5 }
                if (name.contains("hack"))                                         { efc = 3.5; ssc = 0.4; cnc = 3.0 }
            }
            name.contains("press militar") || name.contains("ohp") -> { efc = 4.0; ssc = 1.5; cnc = 4.2 }
            name.contains("press banca") || name.contains("bench press") -> { efc = 3.8; ssc = 0.3; cnc = 3.8 }
            name.contains("dominada") || name.contains("pull-up") || name.contains("pullup") -> { efc = 4.0; ssc = 0.2; cnc = 4.0 }
            name.contains("remo") || name.contains("row") -> {
                efc = 4.2; ssc = 1.6; cnc = 4.0
                if (name.contains("seal") || name.contains("pecho apoyado")) { efc = 3.2; ssc = 0.1; cnc = 2.5 }
            }
            name.contains("hip thrust") || name.contains("puente") -> { efc = 3.5; ssc = 0.5; cnc = 3.0 }
            name.contains("clean") || name.contains("snatch") -> { efc = 4.8; ssc = 1.8; cnc = 5.0 }
            name.contains("zancada") || name.contains("lunge") -> { efc = 3.5; ssc = 0.6; cnc = 3.2 }
            name.contains("press inclinado") -> { efc = 3.5; ssc = 0.2; cnc = 3.5 }
            name.contains("extensión") && (name.contains("cuádriceps") || name.contains("pierna")) -> { efc = 2.5; ssc = 0.1; cnc = 2.0 }
            name.contains("curl") -> { efc = 2.0; ssc = 0.1; cnc = 2.0 }
        }

        // 2. Modificadores de herramienta
        val eq = equipment?.lowercase() ?: ""
        if (name.contains("mancuerna") || eq == "mancuerna") {
            cnc = min(5.0, cnc + 0.2); ssc = max(0.0, ssc - 0.2)
        } else if (name.contains("smith") || name.contains("multipower")) {
            cnc = max(1.0, cnc - 0.5); efc = max(1.0, efc - 0.2)
        } else if (name.contains("polea") || name.contains("cable") || eq == "polea") {
            cnc = max(1.0, cnc - 0.3); efc = min(5.0, efc + 0.2)
        }

        // 3. Modificadores de técnica
        if (name.contains("pausa") || name.contains("paused")) { cnc = min(5.0, cnc + 0.3); efc = min(5.0, efc + 0.5) }
        if (name.contains("déficit") || name.contains("deficit")) { ssc = min(2.0, ssc + 0.2); efc = min(5.0, efc + 0.3) }
        if (name.contains("parcial") || name.contains("rack pull")) { ssc = min(2.0, ssc + 0.2); efc = max(1.0, efc - 0.2) }

        return AugeMetrics(efc = efc, ssc = ssc, cnc = cnc)
    }

    // ─── RPE efectivo (traduce RPE / RIR / failure) ──────────────────────────

    fun getEffectiveRPE(set: CompletedSet): Double {
        var baseRpe = 7.0

        baseRpe = when {
            set.rpe != null         -> set.rpe
            set.rir != null         -> (10 - set.rir).toDouble()
            else                    -> 7.0
        }

        if (set.isFailure) baseRpe = max(baseRpe, 11.0)
        return baseRpe.coerceIn(1.0, 11.0)
    }

    // ─── ¿El set cuenta para fatiga? ─────────────────────────────────────────

    fun isSetEffective(set: CompletedSet): Boolean {
        if (set.reps <= 0 && set.weight <= 0.0) return false
        val rpe = getEffectiveRPE(set)
        return rpe >= 5.0
    }

    // ─── Drain por set ───────────────────────────────────────────────────────

    fun calculateSetBatteryDrain(
        set: CompletedSet,
        metrics: AugeMetrics,
        tanks: BatteryTanks,
        accumulatedSets: Int = 0,
        restTime: Int = 90,
    ): SetDrain {
        val rpe = getEffectiveRPE(set)
        val rpeRatio = rpe / 10.0

        // Factor de acumulación: más sets → más fatiga por set (exponencial suave)
        val volumeFactor = 1.0 + (accumulatedSets * 0.04)

        // Factor de densidad por descanso corto
        val densityFactor = when {
            restTime < 60  -> 1.3
            restTime < 90  -> 1.15
            restTime < 120 -> 1.0
            else           -> 0.9
        }

        // EFC es cuán intenso es metabólicamente (1-5 → 0.2–1.0)
        val efcRatio = metrics.efc / 5.0
        val cncRatio = metrics.cnc / 5.0
        val sscRatio = metrics.ssc / 2.0

        val muscularDrain = efcRatio * rpeRatio * volumeFactor * densityFactor * 3.5
        val cnsDrain      = cncRatio  * rpeRatio * volumeFactor * densityFactor * 3.0
        val spinalDrain   = sscRatio  * rpeRatio * volumeFactor * 4.0

        return SetDrain(
            muscularDrainPct = (muscularDrain / tanks.muscular * 100).coerceIn(0.0, 15.0),
            cnsDrainPct      = (cnsDrain      / tanks.cns      * 100).coerceIn(0.0, 15.0),
            spinalDrainPct   = (spinalDrain   / tanks.spinal   * 100).coerceIn(0.0, 15.0),
        )
    }

    // ─── Estrés total de sesión completada (para historial) ──────────────────

    fun calculateCompletedSessionStress(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Double {
        val tanks = BatteryTanks(cns = 600.0, muscular = 500.0, spinal = 400.0)
        var totalCns = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()

        completedExercises.forEach { ex ->
            val dbInfo = exerciseDb[ex.exerciseDbId ?: ex.exerciseId]
            val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
            val primaryMuscle = dbInfo?.involvedMuscles?.find { it.role == MuscleRole.PRIMARY }?.muscle ?: "Core"
            var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

            ex.sets.forEach { s ->
                if (!isSetEffective(s)) return@forEach
                accumulated++
                val drain = calculateSetBatteryDrain(s, metrics, tanks, accumulated, ex.restTime)
                totalCns += drain.cnsDrainPct
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }
        return totalCns
    }

    // ─── Costo estimado de sesión futura ─────────────────────────────────────

    fun calculatePredictedSessionDrain(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        var totalCns = 0.0; var totalMuscular = 0.0; var totalSpinal = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()

        val exercises = if (session.parts.isNotEmpty())
            session.parts.flatMap { it.exercises }
        else session.exercises

        exercises.forEach { ex ->
            val dbInfo = exerciseDb[ex.exerciseDbId] ?: exerciseDb.values.find {
                it.name.equals(ex.name, ignoreCase = true)
            }
            val metrics = getDynamicAugeMetrics(ex.name, dbInfo?.equipment)
            val primaryMuscle = dbInfo?.involvedMuscles?.find { it.role == MuscleRole.PRIMARY }?.muscle ?: "Core"
            var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

            ex.sets.forEach { s ->
                if (s.isIneffective) return@forEach
                accumulated++
                // Use a synthetic completed set from the planned set
                val syntheticSet = CompletedSet(
                    id = "",
                    weight = s.weight ?: 60.0,
                    reps = s.targetReps ?: 8,
                    rpe = s.targetRPE,
                    rir = s.targetRIR,
                )
                val drain = calculateSetBatteryDrain(syntheticSet, metrics, tanks, accumulated, ex.restTime ?: 90)
                totalCns      += drain.cnsDrainPct
                totalMuscular += drain.muscularDrainPct
                totalSpinal   += drain.spinalDrainPct
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        return PredictedDrain(
            cns      = totalCns.coerceAtMost(100.0).toInt(),
            muscular = totalMuscular.coerceAtMost(100.0).toInt(),
            spinal   = totalSpinal.coerceAtMost(100.0).toInt(),
        )
    }
}
