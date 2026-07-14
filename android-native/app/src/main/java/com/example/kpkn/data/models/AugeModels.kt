package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

// ─── Wellbeing & Logging ──────────────────────────────────────────────────────

@Serializable
data class DailyWellbeingLog(
    val id: String,
    val date: String, // "YYYY-MM-DD"
    val sleepQuality: Int = 3,   // 1-5
    val stressLevel: Int = 3,    // 1-5
    val doms: Int = 1,           // 1-5
    val motivation: Int = 3,     // 1-5
    val sleepHours: Double = 7.5,
    val moodState: MoodState? = null,
    val workIntensity: IntensityLevel? = null,
    val studyIntensity: IntensityLevel? = null,
    // Global display override for the Home/Readiness RING. Per-muscle values
    // remain the source for local muscle detail.
    val manualMuscularBattery: Int? = null,
    val manualNeuralBattery: Int? = null,
    val manualSpinalBattery: Int? = null,
    val manualMuscleBatteries: Map<String, Int> = emptyMap(),
    val manualBatteryAnchorMs: Long? = null,
    val notes: String? = null,
    val preWorkoutDiscomforts: List<String> = emptyList(),
)

@Serializable
data class SleepLog(
    val id: String,
    val date: String,     // "YYYY-MM-DD"
    val endTime: String,  // ISO-8601
    val duration: Double, // hours
)

// ─── Post-Session Feedback ────────────────────────────────────────────────────

@Serializable
data class PostSessionFeedback(
    val logId: String,
    val date: String,
    val cnsRecovery: Int = 7,          // 1-10
    val muscleFeedback: Map<String, MuscleFeedbackEntry> = emptyMap(),
    val unresolvedDiscomfortIds: List<String> = emptyList(),
)

@Serializable
data class MuscleFeedbackEntry(
    val doms: Int = 1,                 // 1-5
    val jointPain: Boolean = false,
    val strengthCapacity: Int = 7,     // 1-10
    val notes: String = "",
)

@Serializable
data class PendingQuestionnaire(
    val logId: String,
    val sessionName: String,
    val muscleGroups: List<String> = emptyList(),
    val stillPresentDiscomfortIds: List<String> = emptyList(),
    val scheduledTimeMs: Long,
)

// ─── AUGE Battery Results ─────────────────────────────────────────────────────

data class GlobalBatteries(
    val muscular: Int,  // 0-100
    val cnc: Int,       // 0-100 (Central Nervous System)
    val spinal: Int,    // 0-100
) {
    val system: Int get() = cnc
    val structure: Int get() = spinal
}

data class MuscleRecoveryStatus(
    val muscleName: String,
    val recoveryScore: Int,           // 0-100
    val hoursToRecovery: Int,
    val hoursSinceLastSession: Int,
    val effectiveSets: Int,
    val status: RecoveryStatus,
)

enum class RecoveryStatus { FRESH, OPTIMAL, RECOVERING, EXHAUSTED }

data class AugeReadinessVerdict(
    val score: Int,           // 0-100
    val label: String,
    val color: ReadinessColor,
    val details: List<String> = emptyList(),
    val action: String = "",
    val confidenceLabel: String = "Media",
)

enum class ReadinessColor { GREEN, YELLOW, RED }

enum class RecoveryChannelId { MUSCULAR, SYSTEM, STRUCTURE }

enum class RecoveryBand { HIGH, NORMAL, MODERATE, LOW, CRITICAL }

data class RecoveryChannelSnapshot(
    val id: RecoveryChannelId,
    val title: String,
    val shortTitle: String,
    val score: Int,
    val band: RecoveryBand,
    val description: String,
    val action: String,
    val causes: List<String> = emptyList(),
    val confidence: Int = 50,
    val editable: Boolean = true,
)

data class RecoveryDashboard(
    val overallScore: Int,
    val headline: String,
    val summary: String,
    val recommendation: String,
    val confidenceLabel: String,
    val channels: List<RecoveryChannelSnapshot> = emptyList(),
)

fun RecoveryDashboard.channelScore(id: RecoveryChannelId, fallback: Int = 100): Int =
    (channels.firstOrNull { it.id == id }?.score ?: fallback).coerceIn(0, 100)

fun AugeSnapshot.ringScore(id: RecoveryChannelId): Int = when (id) {
    RecoveryChannelId.MUSCULAR -> dashboard.channelScore(id, batteries.muscular)
    RecoveryChannelId.SYSTEM -> dashboard.channelScore(id, batteries.cnc)
    RecoveryChannelId.STRUCTURE -> dashboard.channelScore(id, batteries.spinal)
}

data class AugeSnapshot(
    val batteries: GlobalBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
    val perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    val readiness: AugeReadinessVerdict? = null,
    val dashboard: RecoveryDashboard,
    val articular: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
    val shouldSuggestAutoDeload: Boolean = false,
    val cumulativeFatigue: Double = 0.0,
    val autoDeloadMessage: String? = null,
    val isLoading: Boolean = true,
)

// ─── AUGE Metrics (per-exercise) ─────────────────────────────────────────────

data class AugeMetrics(
    val efc: Double = 2.5,  // Metabolic fatigue cost 1-5
    val ssc: Double = 0.5,  // Structural/Spinal cost 0-2
    val cnc: Double = 2.5,  // Central Nervous Cost 1-5
) {
    /** SNC (Sistema Nervioso Central) — alias semántico de cnc para consistencia de UI. */
    val snc: Double get() = cnc
}

data class BatteryTanks(
    val cns: Double,
    val muscular: Double,
    val spinal: Double,
)

data class PhysiologicalFloor(
    val muscular: Int,
    val cns: Int,
    val spinal: Int,
)

data class SetDrain(
    val muscularDrainPct: Double,
    val cnsDrainPct: Double,
    val spinalDrainPct: Double,
)

data class PredictedDrain(
    val cns: Int,     // 0-100
    val muscular: Int,
    val spinal: Int,
)

data class SleepRecommendation(
    val targetHours: Double,
    val reasons: List<String>,
)

// ─── TTC / Articular Battery ──────────────────────────────────────────────────

enum class ArticularBattery { SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL, LUMBAR }

enum class ArticularStatus { OPTIMAL, RECOVERING, EXHAUSTED }

data class ArticularBatteryState(
    val recoveryScore: Int = 100,         // 0-100
    val estimatedHoursToRecovery: Int = 0,
    val status: ArticularStatus = ArticularStatus.OPTIMAL,
    val accumulatedStress: Double = 0.0,
)

data class StructuralReadinessBreakdown(
    val muscleName: String,
    val muscleBattery: Int,
    val articularBattery: Int,
    val combinedBattery: Int,
    val limitingBattery: Int,
    val relatedArticular: List<ArticularBattery>,
)

data class TendonImbalanceAlert(
    val type: AlertSeverity,
    val muscleLabel: String,
    val articularLabel: String,
    val muscleBattery: Int,
    val articularBattery: Int,
    val gap: Int,
    val message: String,
)

enum class AlertSeverity { WARNING, DANGER }

data class TendonCompensationSuggestion(
    val type: SuggestionType,
    val title: String,
    val message: String,
)

enum class SuggestionType { BIOMECHANICAL, NUTRITION }

// ─── Enums ────────────────────────────────────────────────────────────────────

@Suppress("unused")
enum class MoodState { HAPPY, NEUTRAL, SAD, ANXIOUS, ENERGETIC }
enum class IntensityLevel { LOW, MEDIUM, HIGH }

// ─── Mesocycle Stress EMA ──────────────────────────────────────────────────────

data class MesocycleStressEMA(
    val programId: String,
    val mesoIndex: Int,
    val emaValue: Double,
    val sessionCount: Int,
    val latestStressScore: Double?,
    val stressTrend: StressTrend,
    val computedAtMs: Long,
)

enum class StressTrend { RISING, STABLE, FALLING }

// ─── Mis RINGS: Rankings ──────────────────────────────────────────────────────

/** Ranking de sesiones completadas ordenadas por cuánto drenan los RINGS. */
data class SessionDrainRanking(
    val logId: String,
    val sessionName: String,
    val date: String,
    val totalDrain: Double,
    val cnsDrain: Double,
    val muscularDrain: Double,
    val spinalDrain: Double,
)

/** Ranking de ejercicios agregado por drenaje promedio a lo largo del historial. */
data class ExerciseDrainRanking(
    val exerciseName: String,
    val exerciseDbId: String?,
    val overallDrain: Double,
    val muscularDrain: Double,
    val cnsDrain: Double,
    val spinalDrain: Double,
    val sessionCount: Int,
)

// ─── Mis RINGS: Recuperación personal ─────────────────────────────────────────

/** Estadísticas personales de recuperación del último mes. */
data class PersonalRecoveryStats(
    val avgRecoveryHoursOverall: Double,
    val avgRecoveryHoursMuscular: Double,
    val avgRecoveryHoursCns: Double,
    val avgRecoveryHoursSpinal: Double,
    val fastestRecoverySession: String?,
    val slowestRecoverySession: String?,
    val sampleCount: Int,
)

// ─── Mis RINGS: Interferencia ──────────────────────────────────────────────────

/** Músculo compartido entre dos sesiones con datos de interferencia. */
data class SharedMuscleInterference(
    val muscleName: String,
    val drainFromSessionA: Double,   // % de fatiga residual al comenzar sesión B
    val usageInSessionB: Double,     // intensidad de uso en sesión B (0-1 por role)
    val recoveryDeficit: Double,     // déficit combinado (0-1)
)

/**
 * Par de sesiones con porcentaje de interferencia calculado.
 * [isFromHistory] = true → basado en historial real; false → split planificado.
 */
data class SessionInterference(
    val sessionAId: String,
    val sessionAName: String,
    val sessionBId: String,
    val sessionBName: String,
    val sessionADate: String?,
    val sessionBDate: String?,
    val interferencePercent: Int,     // 0-100
    val sharedMuscles: List<SharedMuscleInterference>,
    val recommendation: String,
    val isFromHistory: Boolean,
    val hoursApart: Double,
)

// ─── Mis RINGS: Sueño extendido ───────────────────────────────────────────────

/** Log de sueño extendido con calidad y despertares para tracking de RINGS. */
@Serializable
data class SleepLogExtended(
    val id: String,
    val date: String,              // "YYYY-MM-DD"
    val bedTime: String,           // "23:30"
    val wakeTime: String,          // "07:00"
    val duration: Double,          // horas calculadas
    val quality: Int = 3,          // 1-5 (1=muy mal, 5=excelente)
    val awakenings: Int = 0,       // despertares nocturnos
    val notes: String? = null,
) {
    /** Convierte al SleepLog básico que usa el motor AUGE. */
    fun toSleepLog(): SleepLog = SleepLog(
        id = id,
        date = date,
        endTime = wakeTime,
        duration = duration,
    )
}

// ─── Readiness por Patrón de Movimiento y Ejercicio ───────────────────────────

/**
 * Readiness agregado para un patrón de movimiento (e.g. "Empuje", "Tirón").
 * Las fuentes de datos son exclusivamente AUGE real (batteries + perMuscle).
 */
data class MovementPatternReadiness(
    val patternId: String,
    val patternLabel: String,
    val overallScore: Int,
    val exerciseCount: Int,
    val totalSets: Int,
    val contributingMuscles: List<String>,
    val averageMuscleRecovery: Int,
)

/**
 * Readiness para un ejercicio específico, derivado de baterías AUGE reales,
 * con pesos dinámicos según el perfil biomecánico del ejercicio.
 */
data class ExerciseReadiness(
    val exerciseId: String,
    val exerciseName: String,
    val overallScore: Int,
    val muscularComponent: Int,
    val cnsComponent: Int,
    val spinalComponent: Int,
    val articularComponent: Int,       // NUEVO
    val structuralComponent: Int,     // NUEVO (limiting)
    val relatedArticular: List<ArticularBattery>,  // NUEVO
    val muscularWeight: Double,
    val cnsWeight: Double,
    val spinalWeight: Double,
    val articularWeight: Double,      // NUEVO
    val setsPenaltyFactor: Double,
    val intensityPenaltyFactor: Double,
    val ermProximityFactor: Double,
    val patternId: String?,
    val involvedMuscleIds: List<String>,
    val limitingFactor: String? = null,   // EXPLICATIVO
    val limitingDetail: String? = null,   // EXPLICATIVO
)
/**
 * Sugerencia de ajuste de carga para una serie,
 * basada en el readiness del ejercicio y la severidad del slider.
 */
data class SetAdjustmentSuggestion(
    val exerciseId: String,
    val setIndex: Int,
    val currentPlannedWeight: Double,
    val readinessScore: Int,
    val severityFactor: Double,
    val reductionPercent: Double,
    val suggestedWeight: Double,
    val averageErm: Double?,
    val reason: String,
    val suggestedLoadMode: LoadModeV2 = LoadModeV2.LOAD,
)
