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
    // Legacy field kept only for backward compatibility with stored logs.
    // AUGE 3.0 never reads or exposes a globally editable muscular battery.
    val manualMuscularBattery: Int? = null,
    val manualNeuralBattery: Int? = null,
    val manualSpinalBattery: Int? = null,
    val manualMuscleBatteries: Map<String, Int> = emptyMap(),
    val notes: String? = null,
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

// ─── AUGE Metrics (per-exercise) ─────────────────────────────────────────────

data class AugeMetrics(
    val efc: Double = 2.5,  // Metabolic fatigue cost 1-5
    val ssc: Double = 0.5,  // Structural/Spinal cost 0-2
    val cnc: Double = 2.5,  // Central Nervous Cost 1-5
)

data class BatteryTanks(
    val cns: Double,
    val muscular: Double,
    val spinal: Double,
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

enum class ArticularBattery { SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL }

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

enum class MoodState { HAPPY, NEUTRAL, SAD, ANXIOUS, ENERGETIC }
enum class IntensityLevel { LOW, MEDIUM, HIGH }
