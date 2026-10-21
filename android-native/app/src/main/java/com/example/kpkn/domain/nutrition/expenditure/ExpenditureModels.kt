package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate

const val EXPENDITURE_MODEL_VERSION = "expenditure-v1"

/** Declaración de la persona; la ausencia de marca equivale a NONE. */
enum class DayMark { NONE, PARTIAL, COMPLETE_CONFIRMED, FASTING }

data class DayObservation(
    val date: LocalDate,
    val intakeKcal: Double?,
    val mark: DayMark = DayMark.NONE,
)

data class WeightObservation(val date: LocalDate, val kg: Double)

enum class ExclusionReason { NO_LOG, PARTIAL, UNREVIEWED_LOW, IMPLAUSIBLE, FUTURE }

data class WeightTrendPoint(
    val date: LocalDate,
    val trendKg: Double,
    val slopeKgPerDay: Double,
    val slopeStdErrKgPerDay: Double,
    val transientKg: Double,
    val observedKg: Double?,
    val wasOutlier: Boolean,
    val isProvisional: Boolean = false,
)

enum class ExpenditureState { STARTING, LEARNING, STABLE, PAUSED }
enum class PauseReason { INSUFFICIENT_LOGGING, INSUFFICIENT_WEIGHTS }

data class EstimateQuality(
    val windowDays: Int,
    val usedDays: Int,
    val excluded: Map<ExclusionReason, Int>,
    val excludedDates: Map<LocalDate, ExclusionReason>,
    val weighIns: Int,
)

data class ExpenditureEstimate(
    val date: LocalDate,
    val kcal: Double,
    val lowKcal: Double,
    val highKcal: Double,
    val state: ExpenditureState,
    val pauseReason: PauseReason?,
    val quality: EstimateQuality,
    val trend: WeightTrendPoint?,
    val modelVersion: String = EXPENDITURE_MODEL_VERSION,
)

data class ExpenditureInput(
    val initialKcal: Double,
    val floorKcal: Double,
    val days: List<DayObservation>,
    val weights: List<WeightObservation>,
    val through: LocalDate,
    val goalChangeDates: List<LocalDate> = emptyList(),
)

data class TargetProposal(
    val targetKcal: Int,
    val deltaKcal: Int,
    val clampedBy: TargetClamp?,
    val changed: Boolean,
)

enum class TargetClamp { FLOOR, MAX_STEP, DEAD_BAND }
