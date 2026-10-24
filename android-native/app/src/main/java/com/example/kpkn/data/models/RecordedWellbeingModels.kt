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
    val sleepHours: Double? = null,
    val moodState: MoodState? = null,
    val workIntensity: IntensityLevel? = null,
    val studyIntensity: IntensityLevel? = null,
    val notes: String? = null,
    val preWorkoutDiscomforts: List<String> = emptyList(),
    val source: WellbeingSource = WellbeingSource.DAILY_CHECK_IN,
    val capturedFields: Set<String> = emptySet(),
)

@Serializable
enum class WellbeingSource { DAILY_CHECK_IN, ONBOARDING_INITIAL, POST_SESSION, IMPORTED }

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

enum class ArticularBattery { SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL, LUMBAR }
enum class MoodState { HAPPY, NEUTRAL, SAD, ANXIOUS, ENERGETIC }
enum class IntensityLevel { LOW, MEDIUM, HIGH }

// Sueño registrado

/** Log de sueño extendido con calidad y despertares como declaración original. */
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
    /**
     * Conserva el registro de sueño y normaliza su fecha.
     * [SleepLog.endTime] must be ISO-8601; [wakeTime] alone ("07:00") is not valid.
     */
    fun toSleepLog(): SleepLog = SleepLog(
        id = id,
        date = date,
        endTime = wakeTimeToIso8601(date, wakeTime),
        duration = duration,
    )

    companion object {
        fun wakeTimeToIso8601(date: String, wakeTime: String): String {
            return try {
                val localDate = java.time.LocalDate.parse(date.take(10))
                val parts = wakeTime.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                localDate.atTime(hour, minute)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toString()
            } catch (_: Exception) {
                // Fallback: assemble a parseable ISO local datetime string
                val time = if (wakeTime.length == 5) "$wakeTime:00" else wakeTime
                "${date.take(10)}T$time"
            }
        }
    }
}

