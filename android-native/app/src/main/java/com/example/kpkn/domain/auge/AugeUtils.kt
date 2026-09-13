package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.PhysiologicalFloor
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Utilidades compartidas para el sistema AUGE (baterías, recuperación, predicción de fatiga).
 */
internal object AugeUtils {

    /** Conservation applied to per-set drain when aggregating a session. */
    const val SESSION_CONSERVATION_FACTOR = 0.85

    /** Diminishing-returns k for session aggregation: 1/(1 + k * acc/100). */
    const val SESSION_DECAY_K = 0.65

    /** Official stress blend: CNS / muscular / spinal. */
    const val STRESS_WEIGHT_CNS = 0.45
    const val STRESS_WEIGHT_MUSCULAR = 0.25
    const val STRESS_WEIGHT_SPINAL = 0.30

    /** ln(20): hours to ~95 % recovery when remaining = exp(-k t) with k = TAU_K_95 / τ. */
    const val TAU_K_95 = 2.9957

    /** V2 muscular stress is drainPct × 10. Capacity must live in the same units. */
    const val STRESS_UNITS_PER_DRAIN_PCT = 10.0
    const val CAPACITY_MIN_STRESS_UNITS = 1200.0
    const val CAPACITY_MAX_STRESS_UNITS = 35000.0
    /** Snapshots saved before the ×10 capacity remap (~260). */
    const val LEGACY_CAPACITY_THRESHOLD = 800.0

    fun recoveryDecay(effectiveHours: Double, tauHours: Double): Double {
        val tau = tauHours.coerceAtLeast(1.0)
        val k = TAU_K_95 / tau
        return safeExp(-k * effectiveHours.coerceAtLeast(0.0))
    }

    fun clampWorkCapacity(capacity: Double): Double =
        capacity.coerceIn(CAPACITY_MIN_STRESS_UNITS, CAPACITY_MAX_STRESS_UNITS)

    fun normalizeStoredCapacity(stored: Double, current: Double): Double {
        val cap = stored.takeIf { it.isFinite() && it > 0.0 } ?: current
        return if (cap < LEGACY_CAPACITY_THRESHOLD && current >= LEGACY_CAPACITY_THRESHOLD * 2.0) {
            cap * STRESS_UNITS_PER_DRAIN_PCT
        } else {
            cap
        }
    }

    /**
     * Canonical fallback when predicted-drain calculation fails but the session
     * has work. Single source for Session Editor / Assistant / callers.
     */
    val DEFAULT_FALLBACK_PREDICTED_DRAIN = PredictedDrain(cns = 10, muscular = 14, spinal = 8)

    /**
     * Devuelve el piso fisiológico mínimo para las baterías de acuerdo al tipo de atleta.
     * Estos valores representan el límite máximo de fatiga "segura" que puede tolerar el atleta.
     */
    fun physiologicalFloor(settings: Settings): PhysiologicalFloor = when (settings.athleteType) {
        AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> PhysiologicalFloor(muscular = 15, cns = 20, spinal = 12)
        AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> PhysiologicalFloor(muscular = 18, cns = 22, spinal = 14)
        AthleteType.CALISTHENICS -> PhysiologicalFloor(muscular = 20, cns = 24, spinal = 16)
        AthleteType.HYBRID, AthleteType.ZERCHER_LIFTER -> PhysiologicalFloor(muscular = 20, cns = 25, spinal = 18)
        AthleteType.ENTHUSIAST -> PhysiologicalFloor(muscular = 22, cns = 26, spinal = 18)
    }

    /**
     * Convierte la fecha del WorkoutLog (generalmente ISO-8601 o yyyy-MM-dd) a milisegundos desde epoch.
     */
    fun logDateMs(log: WorkoutLog): Long = try {
        java.time.OffsetDateTime.parse(log.date).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(log.date).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.ZonedDateTime.parse(log.date).toInstant().toEpochMilli()
            } catch (e3: Exception) {
                try {
                    java.time.LocalDate.parse(log.date.take(10))
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (e4: Exception) { 0L }
            }
        }
    }

    fun parseIsoMs(dateString: String): Long = try {
        java.time.OffsetDateTime.parse(dateString).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.ZonedDateTime.parse(dateString).toInstant().toEpochMilli()
            } catch (e3: Exception) {
                try {
                    java.time.LocalDate.parse(dateString.take(10))
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (e4: Exception) { 0L }
            }
        }
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))

    fun safeExp(v: Double): Double {
        val r = exp(v)
        return if (r.isNaN() || r.isInfinite()) 0.0 else r
    }

    /**
     * Curva de desaceleración para baterías por debajo de 30%.
     * Suaviza el drenaje de los RINGS evitando que marquen 0% artificialmente.
     * B_final = 30 * sqrt(B_raw / 30)  cuando B_raw < 30
     */
    fun decelerateBattery(battery: Double): Double {
        val b = battery.coerceIn(0.0, 100.0)
        return if (b < 30.0) 30.0 * kotlin.math.sqrt(b / 30.0) else b
    }

    /**
     * Curva de recuperación muscular: primeras 24h cuentan ~50% del tiempo lineal
     * (antes 15% — overnight se sentía "pegado"). Tras 24h se acelera.
     * A 10h → 5.0h efectivas; a 24h → 12.0h; luego +1.25× por hora.
     */
    fun getSigmoidalHours(hoursSince: Double): Double {
        val h = hoursSince.coerceAtLeast(0.0)
        return if (h < 24.0) {
            h * 0.50
        } else {
            12.0 + (h - 24.0) * 1.25
        }
    }

    /**
     * Tiempo efectivo para Columna. El +18 h extra se incorpora de forma
     * continua entre 12 h y 24 h (antes saltaba de 12 a 30 en un tick).
     */
    fun getSpinalRecoveryHours(hoursSince: Double): Double {
        val h = hoursSince.coerceAtLeast(0.0)
        val extra = when {
            h < 12.0 -> 0.0
            h >= 24.0 -> 18.0
            else -> 18.0 * (h - 12.0) / 12.0
        }
        return h + extra
    }

    /**
     * Soft-cap compartido para drenaje de sesión (PredictedDrain y RecoveryEngine).
     * Damping crece con la fatiga acumulada respecto al techo fisiológico.
     */
    fun applySessionSoftCap(drain: Double, accumulated: Double, cap: Double): Double {
        if (drain <= 0.0 || cap <= 0.0) return 0.0
        val p = (accumulated / cap).coerceIn(0.0, 1.0)
        val damping = when {
            p <= 0.40 -> 1.0 - p * 0.5
            p <= 0.70 -> 0.80 * exp(-3.2 * (p - 0.40))
            else -> 0.30 * exp(-5.5 * (p - 0.70))
        }
        return (drain * damping).coerceAtLeast(0.0)
    }
}
